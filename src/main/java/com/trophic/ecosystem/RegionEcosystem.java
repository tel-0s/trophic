package com.trophic.ecosystem;

import com.trophic.Trophic;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the ecosystem state for a region (16x16 chunks).
 * Tracks population counts, food web state, and environmental conditions.
 */
public class RegionEcosystem {
    private final World world;
    private final int regionX;
    private final int regionZ;
    
    // Population counts per species in this region
    private final Map<Identifier, Integer> populationCounts = new HashMap<>();
    
    // Vegetation/resource level (0.0 to 1.0, affects herbivore carrying capacity)
    private double vegetationLevel = 1.0;
    
    // Accumulated hunting pressure (affects prey populations)
    private double huntingPressure = 0.0;
    
    // Last update tick
    private long lastUpdateTick = 0;

    public RegionEcosystem(World world, int regionX, int regionZ) {
        this.world = world;
        this.regionX = regionX;
        this.regionZ = regionZ;
    }

    /**
     * Called periodically to update the ecosystem state.
     */
    public void tick() {
        long currentTick = world.getTime();
        long tickDelta = currentTick - lastUpdateTick;
        lastUpdateTick = currentTick;
        
        // Regenerate vegetation over time
        regenerateVegetation(tickDelta);
        
        // Decay hunting pressure over time
        decayHuntingPressure(tickDelta);
        
        // Apply population dynamics
        applyPopulationDynamics(tickDelta);
    }

    /**
     * Vegetation regenerates slowly over time.
     */
    private void regenerateVegetation(long tickDelta) {
        // Regenerate 1% per minute (1200 ticks)
        double regenRate = 0.01 / 1200.0;
        vegetationLevel = Math.min(1.0, vegetationLevel + regenRate * tickDelta);
    }

    /**
     * Hunting pressure decays over time.
     */
    private void decayHuntingPressure(long tickDelta) {
        // Decay with half-life of about 10 minutes
        double decayRate = 0.0001;
        huntingPressure = Math.max(0, huntingPressure - decayRate * tickDelta);
    }

    /**
     * Apply population dynamics based on carrying capacity.
     */
    private void applyPopulationDynamics(long tickDelta) {
        // Population dynamics are handled through spawn control and mortality
        // This method can apply additional effects like disease spread, etc.
    }

    /**
     * Records a kill event in this region.
     */
    public void recordKill(Identifier predatorId, Identifier preyId) {
        // Decrease prey population count
        populationCounts.merge(preyId, -1, Integer::sum);
        populationCounts.computeIfPresent(preyId, (k, v) -> v <= 0 ? null : v);
        
        // Increase hunting pressure
        huntingPressure += 0.1;
    }

    /**
     * Records grazing/foraging in this region.
     */
    public void recordGrazing(Identifier herbivoreId) {
        // Decrease vegetation level slightly
        vegetationLevel = Math.max(0, vegetationLevel - 0.001);
    }

    /**
     * Records a spawn event in this region.
     */
    public void recordSpawn(Identifier entityId) {
        populationCounts.merge(entityId, 1, Integer::sum);
    }

    /**
     * Records a death event in this region.
     */
    public void recordDeath(Identifier entityId) {
        populationCounts.merge(entityId, -1, Integer::sum);
        populationCounts.computeIfPresent(entityId, (k, v) -> v <= 0 ? null : v);
    }

    /**
     * Gets the current population of a species in this region.
     */
    public int getPopulation(Identifier entityId) {
        return populationCounts.getOrDefault(entityId, 0);
    }

    /**
     * Sets the population count for a species.
     */
    public void setPopulation(Identifier entityId, int count) {
        if (count <= 0) {
            populationCounts.remove(entityId);
        } else {
            populationCounts.put(entityId, count);
        }
    }

    /**
     * @return the current vegetation level (0.0 to 1.0)
     */
    public double getVegetationLevel() {
        return vegetationLevel;
    }

    /**
     * @return the current hunting pressure
     */
    public double getHuntingPressure() {
        return huntingPressure;
    }

    /**
     * Calculates the effective carrying capacity modifier for this region.
     */
    public double getCarryingCapacityModifier() {
        // Base modifier affected by vegetation and hunting pressure
        return vegetationLevel * (1.0 - huntingPressure * 0.5);
    }

    /**
     * Saves the region ecosystem state.
     */
    public void save() {
        // TODO: Implement persistent storage via PersistentState
    }

    /**
     * Loads the region ecosystem state.
     */
    public void load() {
        // TODO: Implement loading from PersistentState
    }

    public int getRegionX() {
        return regionX;
    }

    public int getRegionZ() {
        return regionZ;
    }

    public World getWorld() {
        return world;
    }

    /**
     * @return total population across all species in this region
     */
    public int getTotalPopulation() {
        return populationCounts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
