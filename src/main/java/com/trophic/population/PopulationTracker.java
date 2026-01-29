package com.trophic.population;

import com.trophic.Trophic;
import com.trophic.ecosystem.EcosystemManager;
import com.trophic.ecosystem.RegionEcosystem;
import com.trophic.registry.SpeciesDefinition;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks animal populations across the world and manages population dynamics.
 */
public class PopulationTracker {
    // Tick interval for population scans (every 30 seconds)
    private static final int SCAN_INTERVAL = 600;
    
    private MinecraftServer server;
    private int tickCounter = 0;
    
    // Global population statistics
    private final Map<Identifier, Integer> globalPopulations = new HashMap<>();
    
    public PopulationTracker() {
    }

    /**
     * Initializes the population tracker for a server.
     */
    public void initializeForServer(MinecraftServer server) {
        this.server = server;
        globalPopulations.clear();
        
        // Initial population scan
        scanAllPopulations();
        
        Trophic.LOGGER.info("PopulationTracker initialized");
    }

    /**
     * Called every server tick.
     */
    public void tick(MinecraftServer server) {
        tickCounter++;
        
        if (tickCounter >= SCAN_INTERVAL) {
            tickCounter = 0;
            scanAllPopulations();
        }
    }

    /**
     * Scans all loaded chunks to count populations.
     */
    private void scanAllPopulations() {
        globalPopulations.clear();
        EcosystemManager ecosystemManager = Trophic.getInstance().getEcosystemManager();
        
        for (ServerWorld world : server.getWorlds()) {
            // Iterate through all loaded entities
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof AnimalEntity animal) {
                    Identifier entityId = Registries.ENTITY_TYPE.getId(animal.getType());
                    
                    // Update global count
                    globalPopulations.merge(entityId, 1, Integer::sum);
                    
                    // Update regional count
                    ChunkPos chunkPos = new ChunkPos(animal.getBlockPos());
                    RegionEcosystem region = ecosystemManager.getOrCreateRegion(world, chunkPos);
                    // Regional population is tracked via spawn/death events
                }
            }
        }
        
        Trophic.LOGGER.debug("Population scan complete: {} species tracked", globalPopulations.size());
    }

    /**
     * Gets the global population of a species.
     */
    public int getGlobalPopulation(Identifier entityId) {
        return globalPopulations.getOrDefault(entityId, 0);
    }

    /**
     * Gets population in a specific region.
     */
    public int getRegionalPopulation(ServerWorld world, ChunkPos chunkPos, Identifier entityId) {
        EcosystemManager ecosystemManager = Trophic.getInstance().getEcosystemManager();
        RegionEcosystem region = ecosystemManager.getRegion(world, chunkPos);
        
        if (region != null) {
            return region.getPopulation(entityId);
        }
        return 0;
    }

    /**
     * Checks if spawning should be allowed based on population limits.
     */
    public boolean canSpawn(ServerWorld world, ChunkPos chunkPos, Identifier entityId) {
        SpeciesDefinition species = Trophic.getInstance().getSpeciesRegistry()
                .getSpecies(entityId).orElse(null);
        
        if (species == null || species.getPopulation() == null) {
            return true; // No restrictions for unregistered species
        }
        
        EcosystemManager ecosystemManager = Trophic.getInstance().getEcosystemManager();
        RegionEcosystem region = ecosystemManager.getOrCreateRegion(world, chunkPos);
        
        // Calculate carrying capacity for this region
        double baseCapacity = species.getPopulation().carryingCapacityPerChunk() * 
                              EcosystemManager.REGION_SIZE * EcosystemManager.REGION_SIZE;
        double effectiveCapacity = baseCapacity * region.getCarryingCapacityModifier();
        
        int currentPopulation = region.getPopulation(entityId);
        
        // Allow spawning if below carrying capacity
        return currentPopulation < effectiveCapacity;
    }

    /**
     * Records that an entity was spawned.
     */
    public void onEntitySpawn(AnimalEntity entity) {
        Identifier entityId = Registries.ENTITY_TYPE.getId(entity.getType());
        globalPopulations.merge(entityId, 1, Integer::sum);
        
        if (entity.getEntityWorld() instanceof ServerWorld serverWorld) {
            ChunkPos chunkPos = new ChunkPos(entity.getBlockPos());
            EcosystemManager ecosystemManager = Trophic.getInstance().getEcosystemManager();
            RegionEcosystem region = ecosystemManager.getOrCreateRegion(serverWorld, chunkPos);
            region.recordSpawn(entityId);
        }
    }

    /**
     * Records that an entity died.
     */
    public void onEntityDeath(AnimalEntity entity) {
        Identifier entityId = Registries.ENTITY_TYPE.getId(entity.getType());
        globalPopulations.merge(entityId, -1, Integer::sum);
        globalPopulations.computeIfPresent(entityId, (k, v) -> v <= 0 ? null : v);
        
        if (entity.getEntityWorld() instanceof ServerWorld serverWorld) {
            ChunkPos chunkPos = new ChunkPos(entity.getBlockPos());
            EcosystemManager ecosystemManager = Trophic.getInstance().getEcosystemManager();
            RegionEcosystem region = ecosystemManager.getOrCreateRegion(serverWorld, chunkPos);
            region.recordDeath(entityId);
        }
    }

    /**
     * Saves all population data.
     */
    public void saveAll() {
        // Population data is derived from ecosystem regions
        Trophic.LOGGER.info("Population data saved");
    }

    /**
     * @return a copy of the global population map
     */
    public Map<Identifier, Integer> getGlobalPopulations() {
        return new HashMap<>(globalPopulations);
    }
}
