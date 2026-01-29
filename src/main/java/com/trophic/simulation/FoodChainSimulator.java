package com.trophic.simulation;

import com.trophic.Trophic;
import com.trophic.ecosystem.EcosystemManager;
import com.trophic.ecosystem.RegionEcosystem;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

/**
 * Simulates food chain energy flow and trophic cascades.
 * 
 * The food chain operates on energy transfer principles:
 * - Primary producers (plants) capture energy from the environment
 * - Each trophic level transfers ~10% of energy to the next level
 * - Population dynamics are affected by energy availability
 */
public class FoodChainSimulator {
    // Energy transfer efficiency between trophic levels
    private static final double TRANSFER_EFFICIENCY = 0.1;
    
    // Base energy production per region (from vegetation)
    private static final double BASE_PRODUCTION = 1000.0;
    
    // Update interval in ticks (every 10 seconds)
    private static final int UPDATE_INTERVAL = 200;
    
    private int tickCounter = 0;

    public FoodChainSimulator() {
    }

    /**
     * Called every server tick to update food chain dynamics.
     */
    public void tick(MinecraftServer server) {
        tickCounter++;
        
        if (tickCounter >= UPDATE_INTERVAL) {
            tickCounter = 0;
            simulateFoodChain(server);
        }
    }

    /**
     * Simulates food chain dynamics across all active regions.
     */
    private void simulateFoodChain(MinecraftServer server) {
        EcosystemManager ecosystemManager = Trophic.getInstance().getEcosystemManager();
        SpeciesRegistry speciesRegistry = Trophic.getInstance().getSpeciesRegistry();
        SeasonManager seasonManager = Trophic.getInstance().getSeasonManager();
        
        // Get seasonal modifier for energy production
        double seasonalModifier = calculateSeasonalProductionModifier(seasonManager);
        
        // Process each active world
        for (ServerWorld world : server.getWorlds()) {
            // For now, we simulate based on global population data
            // In a full implementation, this would process each region
            simulateGlobalFoodChain(speciesRegistry, seasonalModifier);
        }
    }

    /**
     * Calculates seasonal modifier for primary production.
     */
    private double calculateSeasonalProductionModifier(SeasonManager seasonManager) {
        SeasonManager.Season season = seasonManager.getCurrentSeason();
        double progress = seasonManager.getSeasonProgress();
        
        return switch (season) {
            case SPRING -> 0.6 + progress * 0.4; // Growing season
            case SUMMER -> 1.0; // Peak production
            case AUTUMN -> 1.0 - progress * 0.4; // Declining
            case WINTER -> 0.3 + progress * 0.3; // Low but recovering
        };
    }

    /**
     * Simulates food chain at a global level.
     */
    private void simulateGlobalFoodChain(SpeciesRegistry registry, double seasonalModifier) {
        // Get species grouped by trophic level
        Map<Integer, List<SpeciesDefinition>> byLevel = new HashMap<>();
        for (SpeciesDefinition species : registry.getAllSpecies()) {
            byLevel.computeIfAbsent(species.getTrophicLevel(), k -> new ArrayList<>())
                    .add(species);
        }
        
        // Calculate energy available at each level
        double availableEnergy = BASE_PRODUCTION * seasonalModifier;
        
        // Level 1: Primary producers (vegetation) - not tracked as entities
        // Level 2: Herbivores consume vegetation energy
        // Level 3+: Carnivores consume lower levels
        
        for (int level = 2; level <= 4; level++) {
            List<SpeciesDefinition> species = byLevel.get(level);
            if (species == null || species.isEmpty()) {
                continue;
            }
            
            // Energy available at this level
            double levelEnergy = availableEnergy;
            
            // Distribute energy among species at this level based on population
            // This affects carrying capacity and spawn rates
            
            // Pass remaining energy to next level (with efficiency loss)
            availableEnergy = levelEnergy * TRANSFER_EFFICIENCY;
        }
    }

    /**
     * Calculates the trophic cascade effect when a species population changes.
     * 
     * @param speciesId the affected species
     * @param populationChange the change in population (positive or negative)
     * @return map of affected species to their cascade effect magnitude
     */
    public Map<Identifier, Double> calculateCascadeEffect(Identifier speciesId, int populationChange) {
        Map<Identifier, Double> effects = new HashMap<>();
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        
        SpeciesDefinition species = registry.getSpecies(speciesId).orElse(null);
        if (species == null) {
            return effects;
        }
        
        // If predator population decreases, prey populations can increase
        // If prey population decreases, predator populations may decrease
        
        double changeMagnitude = populationChange / 10.0; // Normalize
        
        // Cascade down (affect prey)
        if (species.getDiet() != null && species.getDiet().prey() != null) {
            for (Identifier preyId : species.getDiet().prey().keySet()) {
                // Fewer predators = more prey (inverse relationship)
                effects.put(preyId, -changeMagnitude * 0.5);
            }
        }
        
        // Cascade up (affect predators)
        Set<Identifier> predators = registry.getPredatorsOf(speciesId);
        for (Identifier predatorId : predators) {
            // Less prey = less food for predators (direct relationship)
            effects.put(predatorId, changeMagnitude * 0.3);
        }
        
        return effects;
    }

    /**
     * Gets the energy availability modifier for a species based on food chain position.
     */
    public double getEnergyModifier(Identifier speciesId) {
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(speciesId).orElse(null);
        
        if (species == null) {
            return 1.0;
        }
        
        // Higher trophic levels have less energy available (10% rule)
        int level = species.getTrophicLevel();
        return Math.pow(TRANSFER_EFFICIENCY, level - 1);
    }

    /**
     * Calculates the expected population ratio between predator and prey.
     */
    public double getPredatorPreyRatio(Identifier predatorId, Identifier preyId) {
        // Typically, predators should be ~10% of prey population
        // This can be modified by species-specific factors
        return 0.1;
    }
}
