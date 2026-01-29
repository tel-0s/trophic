package com.trophic.population;

import com.trophic.Trophic;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

/**
 * Controls animal spawning based on population dynamics and carrying capacity.
 */
public class SpawnController {
    private final PopulationTracker populationTracker;
    private final SpeciesRegistry speciesRegistry;

    public SpawnController(PopulationTracker populationTracker, SpeciesRegistry speciesRegistry) {
        this.populationTracker = populationTracker;
        this.speciesRegistry = speciesRegistry;
    }

    /**
     * Registers spawn control event handlers.
     */
    public void register() {
        // Track entity loads (includes spawns)
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof AnimalEntity animal) {
                onAnimalLoad(animal, world);
            }
        });
        
        // Track entity unloads (includes deaths)
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof AnimalEntity animal) {
                onAnimalUnload(animal, world);
            }
        });
        
        Trophic.LOGGER.info("SpawnController registered");
    }

    /**
     * Called when an animal entity is loaded/spawned.
     */
    private void onAnimalLoad(AnimalEntity animal, ServerWorld world) {
        Identifier entityId = Registries.ENTITY_TYPE.getId(animal.getType());
        
        // Check if this is a registered species
        if (speciesRegistry.isRegistered(entityId)) {
            // Initialize ecological data for the entity if needed
            initializeEcologicalData(animal);
        }
    }

    /**
     * Called when an animal entity is unloaded.
     */
    private void onAnimalUnload(AnimalEntity animal, ServerWorld world) {
        // Unload handling - population tracking happens via death events
    }

    /**
     * Checks if a spawn should be allowed at a given location.
     * Called before natural spawns occur.
     */
    public boolean shouldAllowSpawn(ServerWorld world, Identifier entityId, ChunkPos chunkPos, SpawnReason reason) {
        // Always allow spawner, command, or breeding spawns
        if (reason == SpawnReason.SPAWNER || 
            reason == SpawnReason.COMMAND || 
            reason == SpawnReason.BREEDING) {
            return true;
        }
        
        // Check if population allows spawning
        if (!populationTracker.canSpawn(world, chunkPos, entityId)) {
            return false;
        }
        
        // Check habitat suitability
        SpeciesDefinition species = speciesRegistry.getSpecies(entityId).orElse(null);
        if (species != null && species.getHabitat() != null) {
            // TODO: Check biome suitability
            // Identifier biomeId = world.getBiome(pos).getKey().get().getValue();
            // if (!species.getHabitat().isSuitableBiome(biomeId)) {
            //     return false;
            // }
        }
        
        return true;
    }

    /**
     * Initializes ecological NBT data for an animal.
     */
    private void initializeEcologicalData(AnimalEntity animal) {
        // Check if already initialized
        if (animal.getCommandTags().contains("trophic_initialized")) {
            return;
        }
        
        // Mark as initialized
        animal.addCommandTag("trophic_initialized");
        
        // Initialize hunger to a random value
        // This is done via NBT in the mixin
    }

    /**
     * Gets the spawn weight modifier for a species based on ecosystem state.
     */
    public double getSpawnWeightModifier(ServerWorld world, Identifier entityId, ChunkPos chunkPos) {
        SpeciesDefinition species = speciesRegistry.getSpecies(entityId).orElse(null);
        if (species == null || species.getPopulation() == null) {
            return 1.0;
        }
        
        // Reduce spawn weight as population approaches carrying capacity
        if (!populationTracker.canSpawn(world, chunkPos, entityId)) {
            return 0.0;
        }
        
        // Gradual reduction as population increases
        int currentPop = populationTracker.getRegionalPopulation(world, chunkPos, entityId);
        double maxPop = species.getPopulation().carryingCapacityPerChunk() * 256; // Approximate
        
        if (maxPop <= 0) return 1.0;
        
        double ratio = currentPop / maxPop;
        return Math.max(0.1, 1.0 - ratio * 0.9);
    }
}
