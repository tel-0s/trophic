package com.trophic.ecosystem;

import com.trophic.Trophic;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages ecosystem state across all dimensions.
 * Coordinates regional ecosystems and handles cross-region interactions.
 */
public class EcosystemManager {
    // Region size in chunks (16x16 chunks = 256x256 blocks)
    public static final int REGION_SIZE = 16;
    
    // Tick interval for ecosystem updates (every 5 seconds)
    private static final int UPDATE_INTERVAL = 100;
    
    private final Map<World, Map<Long, RegionEcosystem>> worldEcosystems = new HashMap<>();
    private MinecraftServer server;
    private int tickCounter = 0;

    public EcosystemManager() {
    }

    /**
     * Initializes the ecosystem manager for a server.
     */
    public void initializeForServer(MinecraftServer server) {
        this.server = server;
        worldEcosystems.clear();
        
        for (ServerWorld world : server.getWorlds()) {
            worldEcosystems.put(world, new HashMap<>());
        }
        
        Trophic.LOGGER.info("EcosystemManager initialized for {} dimensions", worldEcosystems.size());
    }

    /**
     * Called every server tick to update ecosystems.
     */
    public void tick(MinecraftServer server) {
        tickCounter++;
        
        if (tickCounter >= UPDATE_INTERVAL) {
            tickCounter = 0;
            updateAllEcosystems();
        }
    }

    /**
     * Updates all active ecosystems.
     */
    private void updateAllEcosystems() {
        for (Map.Entry<World, Map<Long, RegionEcosystem>> worldEntry : worldEcosystems.entrySet()) {
            for (RegionEcosystem region : worldEntry.getValue().values()) {
                region.tick();
            }
        }
    }

    /**
     * Gets or creates a region ecosystem for the given chunk position.
     */
    public RegionEcosystem getOrCreateRegion(World world, ChunkPos chunkPos) {
        Map<Long, RegionEcosystem> worldRegions = worldEcosystems.computeIfAbsent(world, k -> new HashMap<>());
        
        long regionKey = getRegionKey(chunkPos);
        return worldRegions.computeIfAbsent(regionKey, k -> {
            int regionX = Math.floorDiv(chunkPos.x, REGION_SIZE);
            int regionZ = Math.floorDiv(chunkPos.z, REGION_SIZE);
            return new RegionEcosystem(world, regionX, regionZ);
        });
    }

    /**
     * Gets the region ecosystem for a chunk, if it exists.
     */
    public RegionEcosystem getRegion(World world, ChunkPos chunkPos) {
        Map<Long, RegionEcosystem> worldRegions = worldEcosystems.get(world);
        if (worldRegions == null) {
            return null;
        }
        return worldRegions.get(getRegionKey(chunkPos));
    }

    /**
     * Converts a chunk position to a region key.
     */
    private long getRegionKey(ChunkPos chunkPos) {
        int regionX = Math.floorDiv(chunkPos.x, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkPos.z, REGION_SIZE);
        return ChunkPos.toLong(regionX, regionZ);
    }

    /**
     * Saves all ecosystem data.
     */
    public void saveAll() {
        int regionCount = 0;
        for (Map<Long, RegionEcosystem> worldRegions : worldEcosystems.values()) {
            for (RegionEcosystem region : worldRegions.values()) {
                region.save();
                regionCount++;
            }
        }
        Trophic.LOGGER.info("Saved {} ecosystem regions", regionCount);
    }

    /**
     * @return the total number of active regions
     */
    public int getActiveRegionCount() {
        return worldEcosystems.values().stream()
                .mapToInt(Map::size)
                .sum();
    }
}
