package com.trophic.api;

import com.trophic.Trophic;
import com.trophic.ecosystem.EcosystemManager;
import com.trophic.population.PopulationTracker;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import com.trophic.simulation.SeasonManager;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Public API for other mods to interact with Trophic.
 * 
 * Use this class to:
 * - Register custom species
 * - Query ecosystem state
 * - Access population data
 * - Get seasonal information
 */
public final class TrophicAPI {
    
    private TrophicAPI() {
        // Static API class
    }
    
    // ========== Species Registration ==========
    
    /**
     * Registers a new species definition.
     * 
     * @param definition the species definition
     */
    public static void registerSpecies(SpeciesDefinition definition) {
        getSpeciesRegistry().register(definition);
    }
    
    /**
     * Creates a new species builder for the given entity.
     * 
     * @param entityId the entity identifier (e.g., "mymod:my_animal")
     * @return a builder for creating the species definition
     */
    public static SpeciesBuilder species(String entityId) {
        return new SpeciesBuilder(Identifier.of(entityId));
    }
    
    /**
     * Creates a new species builder for the given entity.
     * 
     * @param entityId the entity identifier
     * @return a builder for creating the species definition
     */
    public static SpeciesBuilder species(Identifier entityId) {
        return new SpeciesBuilder(entityId);
    }
    
    /**
     * Gets a registered species definition.
     * 
     * @param entityId the entity identifier
     * @return the species definition, or empty if not registered
     */
    public static Optional<SpeciesDefinition> getSpecies(Identifier entityId) {
        return getSpeciesRegistry().getSpecies(entityId);
    }
    
    /**
     * Checks if a species is registered.
     * 
     * @param entityId the entity identifier
     * @return true if registered
     */
    public static boolean isSpeciesRegistered(Identifier entityId) {
        return getSpeciesRegistry().isRegistered(entityId);
    }
    
    // ========== Ecosystem Access ==========
    
    /**
     * Gets the current season.
     * 
     * @return the current season
     */
    public static SeasonManager.Season getCurrentSeason() {
        return getSeasonManager().getCurrentSeason();
    }
    
    /**
     * Gets the current progress through the year (0.0 to 1.0).
     * 
     * @return year progress
     */
    public static double getYearProgress() {
        return getSeasonManager().getYearProgress();
    }
    
    /**
     * Checks if it's currently breeding season for given parameters.
     * 
     * @param seasonStart start of breeding season (0.0 to 1.0)
     * @param seasonEnd end of breeding season (0.0 to 1.0)
     * @return true if in breeding season
     */
    public static boolean isBreedingSeason(double seasonStart, double seasonEnd) {
        return getSeasonManager().isBreedingSeason(seasonStart, seasonEnd);
    }
    
    // ========== Population Access ==========
    
    /**
     * Gets the global population count for a species.
     * 
     * @param entityId the entity identifier
     * @return population count
     */
    public static int getGlobalPopulation(Identifier entityId) {
        return getPopulationTracker().getGlobalPopulation(entityId);
    }
    
    // ========== Internal Access ==========
    
    private static SpeciesRegistry getSpeciesRegistry() {
        return Trophic.getInstance().getSpeciesRegistry();
    }
    
    private static SeasonManager getSeasonManager() {
        return Trophic.getInstance().getSeasonManager();
    }
    
    private static PopulationTracker getPopulationTracker() {
        return Trophic.getInstance().getPopulationTracker();
    }
    
    private static EcosystemManager getEcosystemManager() {
        return Trophic.getInstance().getEcosystemManager();
    }
}
