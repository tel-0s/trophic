package com.trophic.simulation;

import com.trophic.Trophic;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * Manages the seasonal cycle for ecosystem simulation.
 * 
 * The year is divided into four seasons:
 * - Spring (0.00 - 0.25): Breeding season, migration return
 * - Summer (0.25 - 0.50): Peak activity, territorial behavior
 * - Autumn (0.50 - 0.75): Migration departure, food storage
 * - Winter (0.75 - 1.00): Reduced spawning, hibernation
 */
public class SeasonManager {
    /**
     * Length of a full year in ticks (default: 8 Minecraft days = 160,000 ticks)
     * This can be configured to adjust season length.
     */
    public static final long YEAR_LENGTH_TICKS = 160000L;
    
    /**
     * Length of a single season in ticks.
     */
    public static final long SEASON_LENGTH_TICKS = YEAR_LENGTH_TICKS / 4;
    
    private long worldTime = 0;
    
    public SeasonManager() {
    }

    /**
     * Called every server tick to update time tracking.
     */
    public void tick(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        if (overworld != null) {
            worldTime = overworld.getTime();
        }
    }

    /**
     * Gets the current progress through the year (0.0 to 1.0).
     */
    public double getYearProgress() {
        return (worldTime % YEAR_LENGTH_TICKS) / (double) YEAR_LENGTH_TICKS;
    }

    /**
     * Gets the current season.
     */
    public Season getCurrentSeason() {
        double progress = getYearProgress();
        if (progress < 0.25) return Season.SPRING;
        if (progress < 0.50) return Season.SUMMER;
        if (progress < 0.75) return Season.AUTUMN;
        return Season.WINTER;
    }

    /**
     * Gets the progress within the current season (0.0 to 1.0).
     */
    public double getSeasonProgress() {
        double yearProgress = getYearProgress();
        return (yearProgress % 0.25) / 0.25;
    }

    /**
     * Checks if it's currently breeding season for a species with given parameters.
     */
    public boolean isBreedingSeason(double seasonStart, double seasonEnd) {
        double progress = getYearProgress();
        
        if (seasonStart <= seasonEnd) {
            return progress >= seasonStart && progress <= seasonEnd;
        } else {
            // Breeding season spans year boundary
            return progress >= seasonStart || progress <= seasonEnd;
        }
    }

    /**
     * Gets the temperature modifier based on current season.
     * Returns a value from 0.0 (coldest) to 1.0 (warmest).
     */
    public double getTemperatureModifier() {
        Season season = getCurrentSeason();
        double seasonProgress = getSeasonProgress();
        
        return switch (season) {
            case SPRING -> 0.3 + seasonProgress * 0.4; // 0.3 -> 0.7
            case SUMMER -> 0.7 + seasonProgress * 0.3; // 0.7 -> 1.0
            case AUTUMN -> 1.0 - seasonProgress * 0.5; // 1.0 -> 0.5
            case WINTER -> 0.5 - seasonProgress * 0.5; // 0.5 -> 0.0
        };
    }

    /**
     * Gets the day length modifier (affects activity patterns).
     * Returns a value from 0.5 (short days) to 1.0 (long days).
     */
    public double getDayLengthModifier() {
        Season season = getCurrentSeason();
        double seasonProgress = getSeasonProgress();
        
        return switch (season) {
            case SPRING -> 0.6 + seasonProgress * 0.4; // 0.6 -> 1.0
            case SUMMER -> 1.0; // Long days
            case AUTUMN -> 1.0 - seasonProgress * 0.4; // 1.0 -> 0.6
            case WINTER -> 0.5 + seasonProgress * 0.1; // 0.5 -> 0.6
        };
    }

    /**
     * Gets a spawn rate modifier based on current season.
     */
    public double getSpawnRateModifier() {
        Season season = getCurrentSeason();
        
        return switch (season) {
            case SPRING -> 1.5; // Peak breeding
            case SUMMER -> 1.0; // Normal
            case AUTUMN -> 0.7; // Reduced
            case WINTER -> 0.3; // Minimal
        };
    }

    /**
     * Gets a migration trigger value.
     * Higher values indicate stronger migration pressure.
     */
    public double getMigrationPressure() {
        Season season = getCurrentSeason();
        double seasonProgress = getSeasonProgress();
        
        return switch (season) {
            case SPRING -> seasonProgress < 0.3 ? 0.8 - seasonProgress * 2 : 0.0; // Return migration early spring
            case SUMMER -> 0.0; // No migration
            case AUTUMN -> seasonProgress > 0.5 ? (seasonProgress - 0.5) * 2 : 0.0; // Departure late autumn
            case WINTER -> seasonProgress < 0.2 ? 0.5 - seasonProgress * 2 : 0.0; // Late departures early winter
        };
    }

    /**
     * @return the current world time in ticks
     */
    public long getWorldTime() {
        return worldTime;
    }

    /**
     * Represents the four seasons.
     */
    public enum Season {
        SPRING("Spring"),
        SUMMER("Summer"),
        AUTUMN("Autumn"),
        WINTER("Winter");
        
        private final String displayName;
        
        Season(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}
