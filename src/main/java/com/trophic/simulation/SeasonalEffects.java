package com.trophic.simulation;

import com.trophic.Trophic;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import net.minecraft.util.Identifier;

/**
 * Calculates seasonal effects on various ecological behaviors.
 */
public class SeasonalEffects {

    /**
     * Gets the activity level modifier for a species based on season.
     * Some animals are more/less active in certain seasons.
     */
    public static double getActivityModifier(Identifier speciesId) {
        SeasonManager seasonManager = Trophic.getInstance().getSeasonManager();
        SeasonManager.Season season = seasonManager.getCurrentSeason();
        
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(speciesId).orElse(null);
        
        if (species == null) {
            return 1.0;
        }
        
        // Cold-adapted species are more active in winter
        // Warm-adapted species are more active in summer
        double tempModifier = 1.0;
        if (species.getHabitat() != null) {
            double optimalTemp = (species.getHabitat().minTemperature() + 
                                  species.getHabitat().maxTemperature()) / 2;
            
            double currentTemp = seasonManager.getTemperatureModifier() * 40 - 10; // -10 to 30
            double tempDiff = Math.abs(currentTemp - optimalTemp);
            tempModifier = Math.max(0.3, 1.0 - tempDiff / 40.0);
        }
        
        return tempModifier;
    }

    /**
     * Gets the hunting success modifier based on season.
     * Hunting is generally easier when prey is more active.
     */
    public static double getHuntingSuccessModifier() {
        SeasonManager seasonManager = Trophic.getInstance().getSeasonManager();
        SeasonManager.Season season = seasonManager.getCurrentSeason();
        
        return switch (season) {
            case SPRING -> 1.0; // Normal
            case SUMMER -> 1.1; // Active prey, easier to find
            case AUTUMN -> 1.2; // Prey fattening up, less cautious
            case WINTER -> 0.8; // Prey scarce, hiding
        };
    }

    /**
     * Gets the foraging success modifier based on season.
     */
    public static double getForagingSuccessModifier() {
        SeasonManager seasonManager = Trophic.getInstance().getSeasonManager();
        SeasonManager.Season season = seasonManager.getCurrentSeason();
        
        return switch (season) {
            case SPRING -> 0.9; // Growing season starting
            case SUMMER -> 1.2; // Abundant vegetation
            case AUTUMN -> 1.0; // Still good
            case WINTER -> 0.4; // Scarce vegetation
        };
    }

    /**
     * Gets the metabolism modifier based on season.
     * Animals burn more energy in cold weather.
     */
    public static double getMetabolismModifier(Identifier speciesId) {
        SeasonManager seasonManager = Trophic.getInstance().getSeasonManager();
        double temp = seasonManager.getTemperatureModifier();
        
        // Colder = higher metabolism (need to stay warm)
        return 1.0 + (1.0 - temp) * 0.3;
    }

    /**
     * Gets the breeding probability modifier based on season.
     */
    public static double getBreedingModifier(Identifier speciesId) {
        SeasonManager seasonManager = Trophic.getInstance().getSeasonManager();
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(speciesId).orElse(null);
        
        if (species == null || species.getReproduction() == null) {
            return seasonManager.getSpawnRateModifier();
        }
        
        // Check if in breeding season
        if (seasonManager.isBreedingSeason(
                species.getReproduction().breedingSeasonStart(),
                species.getReproduction().breedingSeasonEnd())) {
            return 1.5; // Peak breeding time
        }
        
        return 0.1; // Can still breed, but rarely
    }

    /**
     * Gets the migration urge based on season and species.
     */
    public static double getMigrationUrge(Identifier speciesId) {
        SeasonManager seasonManager = Trophic.getInstance().getSeasonManager();
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(speciesId).orElse(null);
        
        if (species == null || species.getPopulation() == null) {
            return 0.0;
        }
        
        double baseTendency = species.getPopulation().migrationTendency();
        double seasonalPressure = seasonManager.getMigrationPressure();
        
        return baseTendency * seasonalPressure;
    }

    /**
     * Checks if an animal should consider hibernating.
     */
    public static boolean shouldConsiderHibernation(Identifier speciesId) {
        SeasonManager seasonManager = Trophic.getInstance().getSeasonManager();
        
        // Only consider hibernation in late autumn/winter
        SeasonManager.Season season = seasonManager.getCurrentSeason();
        if (season != SeasonManager.Season.AUTUMN && season != SeasonManager.Season.WINTER) {
            return false;
        }
        
        // Check if species hibernates (for now, only polar bears)
        String path = speciesId.getPath();
        return path.equals("polar_bear");
    }
}
