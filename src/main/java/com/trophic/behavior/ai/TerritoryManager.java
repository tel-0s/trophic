package com.trophic.behavior.ai;

import com.trophic.Trophic;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages territory claims and conflicts between animals.
 */
public class TerritoryManager {
    
    /**
     * Territory claim data structure.
     */
    public record Territory(
            UUID ownerId,
            Identifier speciesId,
            BlockPos center,
            int radius,
            long claimTime
    ) {
        public boolean contains(BlockPos pos) {
            return center.isWithinDistance(pos, radius);
        }
        
        public boolean contains(Vec3d pos) {
            return center.isWithinDistance(pos, radius);
        }
        
        public boolean overlaps(Territory other) {
            double distance = Math.sqrt(center.getSquaredDistance(other.center));
            return distance < (radius + other.radius);
        }
    }
    
    // Territory claims per entity
    private static final Map<UUID, Territory> territories = new HashMap<>();
    
    // Spatial index for quick lookups (simplified - per chunk)
    private static final Map<Long, Map<UUID, Territory>> spatialIndex = new HashMap<>();
    
    /**
     * Claims a territory for an animal.
     */
    public static Territory claimTerritory(AnimalEntity animal) {
        Identifier speciesId = Registries.ENTITY_TYPE.getId(animal.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(speciesId).orElse(null);
        
        if (species == null || species.getSocial() == null) {
            return null;
        }
        
        int radius = species.getSocial().territoryRadius();
        BlockPos center = animal.getBlockPos();
        
        Territory territory = new Territory(
                animal.getUuid(),
                speciesId,
                center,
                radius,
                animal.getEntityWorld().getTime()
        );
        
        // Remove old territory if exists
        releaseTerritory(animal.getUuid());
        
        // Register new territory
        territories.put(animal.getUuid(), territory);
        updateSpatialIndex(territory, true);
        
        return territory;
    }
    
    /**
     * Gets the territory claimed by an animal.
     */
    public static Optional<Territory> getTerritory(UUID animalId) {
        return Optional.ofNullable(territories.get(animalId));
    }
    
    /**
     * Releases an animal's territory claim.
     */
    public static void releaseTerritory(UUID animalId) {
        Territory old = territories.remove(animalId);
        if (old != null) {
            updateSpatialIndex(old, false);
        }
    }
    
    /**
     * Updates the spatial index for quick territory lookups.
     */
    private static void updateSpatialIndex(Territory territory, boolean add) {
        // Calculate affected chunk positions
        int minCX = (territory.center.getX() - territory.radius) >> 4;
        int maxCX = (territory.center.getX() + territory.radius) >> 4;
        int minCZ = (territory.center.getZ() - territory.radius) >> 4;
        int maxCZ = (territory.center.getZ() + territory.radius) >> 4;
        
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                long key = chunkKey(cx, cz);
                if (add) {
                    spatialIndex.computeIfAbsent(key, k -> new HashMap<>())
                            .put(territory.ownerId, territory);
                } else {
                    Map<UUID, Territory> chunk = spatialIndex.get(key);
                    if (chunk != null) {
                        chunk.remove(territory.ownerId);
                        if (chunk.isEmpty()) {
                            spatialIndex.remove(key);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Finds territories that contain a given position.
     */
    public static Optional<Territory> findTerritoryAt(BlockPos pos) {
        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        Map<UUID, Territory> chunk = spatialIndex.get(key);
        
        if (chunk == null) {
            return Optional.empty();
        }
        
        return chunk.values().stream()
                .filter(t -> t.contains(pos))
                .findFirst();
    }
    
    /**
     * Checks if a position is inside another animal's territory (intruding).
     */
    public static boolean isIntruding(AnimalEntity animal, BlockPos pos) {
        Optional<Territory> territoryAt = findTerritoryAt(pos);
        if (territoryAt.isEmpty()) {
            return false;
        }
        
        Territory territory = territoryAt.get();
        
        // Not intruding if it's our own territory
        if (territory.ownerId.equals(animal.getUuid())) {
            return false;
        }
        
        // Check if same species (only same species have territorial conflicts)
        Identifier animalSpecies = Registries.ENTITY_TYPE.getId(animal.getType());
        return animalSpecies.equals(territory.speciesId);
    }
    
    /**
     * Finds the territory owner if this position is intruding.
     */
    public static Optional<UUID> findTerritoryOwner(BlockPos pos, Identifier speciesId) {
        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        Map<UUID, Territory> chunk = spatialIndex.get(key);
        
        if (chunk == null) {
            return Optional.empty();
        }
        
        return chunk.values().stream()
                .filter(t -> t.contains(pos) && t.speciesId.equals(speciesId))
                .map(Territory::ownerId)
                .findFirst();
    }
    
    /**
     * Checks if an animal should defend its territory against an intruder.
     */
    public static boolean shouldDefend(AnimalEntity owner, AnimalEntity intruder) {
        Optional<Territory> territory = getTerritory(owner.getUuid());
        if (territory.isEmpty()) {
            return false;
        }
        
        // Check if intruder is in our territory
        if (!territory.get().contains(new Vec3d(intruder.getX(), intruder.getY(), intruder.getZ()))) {
            return false;
        }
        
        // Only defend against same species
        return owner.getType() == intruder.getType();
    }
    
    /**
     * Gets the distance from a position to the nearest territory boundary.
     * Negative values indicate inside a territory.
     */
    public static double getDistanceToTerritoryBoundary(BlockPos pos, UUID excludeOwner) {
        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        Map<UUID, Territory> chunk = spatialIndex.get(key);
        
        if (chunk == null) {
            return Double.MAX_VALUE;
        }
        
        double minDistance = Double.MAX_VALUE;
        
        for (Territory territory : chunk.values()) {
            if (territory.ownerId.equals(excludeOwner)) {
                continue;
            }
            
            double distToCenter = Math.sqrt(territory.center.getSquaredDistance(pos));
            double distToBoundary = distToCenter - territory.radius;
            
            if (distToBoundary < minDistance) {
                minDistance = distToBoundary;
            }
        }
        
        return minDistance;
    }
    
    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
    
    /**
     * Clean up territories for removed entities.
     */
    public static void onEntityRemoved(UUID entityId) {
        releaseTerritory(entityId);
    }
}
