package com.trophic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.trophic.Trophic;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration for the Trophic mod.
 * All values are externalized here for easy tweaking.
 */
public class TrophicConfig {
    private static TrophicConfig INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // ===== HUNGER SYSTEM =====
    public HungerConfig hunger = new HungerConfig();
    
    public static class HungerConfig {
        /** Base hunger decay per second (default: 0.0008 = ~8-10 min to get hungry) */
        public double decayRatePerSecond = 0.0008;
        
        /** How much trophic level affects hunger decay (default: 0.02 = minimal) */
        public double trophicLevelScaling = 0.02;
        
        /** Hunger threshold for "hungry" state - triggers hunting/foraging (default: 0.6) */
        public double hungryThreshold = 0.6;
        
        /** Hunger threshold for "starving" state (default: 0.2) */
        public double starvingThreshold = 0.2;
        
        /** Hunger level at which starvation damage starts (default: 0.05) */
        public double starvationDamageThreshold = 0.05;
        
        /** Ticks between starvation damage (default: 200 = 10 seconds) */
        public int starvationDamageInterval = 200;
    }
    
    // ===== HOME RANGE =====
    public HomeRangeConfig homeRange = new HomeRangeConfig();
    
    public static class HomeRangeConfig {
        /** Default home range radius in blocks (default: 48) */
        public double defaultRange = 48.0;
        
        /** Penalty multiplier for flee targets outside home range (default: 10) */
        public double fleeOutOfRangePenalty = 10.0;
    }
    
    // ===== FLEEING BEHAVIOR =====
    public FleeConfig flee = new FleeConfig();
    
    public static class FleeConfig {
        /** Maximum flee duration in ticks (default: 200 = 10 seconds) */
        public int maxFleeTime = 200;
        
        /** How often to recalculate flee direction in ticks (default: 10) */
        public int recalculateInterval = 10;
        
        /** Distance to flee in blocks (default: 16) */
        public double fleeDistance = 16.0;
        
        /** Default detection range for predators in blocks (default: 16) */
        public double detectionRange = 16.0;
        
        /** Multiplier for safe distance check when deciding to stop fleeing (default: 1.5) */
        public double safeDistanceMultiplier = 1.5;
        
        /** Weight for predator distance in flee target scoring (default: 0.5) */
        public double predatorDistanceWeight = 0.5;
    }
    
    // ===== HUNTING BEHAVIOR =====
    public HuntConfig hunt = new HuntConfig();
    
    public static class HuntConfig {
        /** Maximum hunt duration in ticks (default: 600 = 30 seconds) */
        public int maxHuntTime = 600;
        
        /** How often to recalculate path during hunt in ticks (default: 20) */
        public int recalculatePathInterval = 20;
        
        /** Duration of directional commitment after starting a hunt in ticks (default: 400 = 20 seconds) */
        public int commitmentDuration = 400;
        
        /** How much to prefer targets in committed direction (default: 0.7) */
        public double directionPreference = 0.7;
        
        /** Penalty multiplier for targets in opposite direction (default: 2.0) */
        public double oppositeDirectionPenalty = 2.0;
        
        /** Default search range for prey in blocks (default: 32) */
        public double defaultSearchRange = 32.0;
        
        /** Stalk speed as multiplier of chase speed (default: 0.6) */
        public double stalkSpeedMultiplier = 0.6;
        
        /** Multiplier for search range during chase phase (default: 1.5) */
        public double chaseRangeMultiplier = 1.5;
        
        /** Squared distance to transition from stalk to chase (default: 64 = 8 blocks) */
        public double stalkToChaseDistanceSq = 64.0;
        
        /** Squared distance to attempt attack (default: 4 = 2 blocks) */
        public double attackDistanceSq = 4.0;
    }
    
    // ===== PACK BEHAVIOR =====
    public PackConfig pack = new PackConfig();
    
    public static class PackConfig {
        /** Distance at which pack following starts in blocks (default: 32) */
        public double followStartDistance = 32.0;
        
        /** Distance at which pack following stops in blocks (default: 8) */
        public double followStopDistance = 8.0;
        
        /** How often to update pack behavior in ticks (default: 20) */
        public int updateInterval = 20;
        
        /** Default max distance from pack before regrouping in blocks (default: 32) */
        public double maxDistanceFromPack = 32.0;
        
        /** Random spread offset when moving toward pack center in blocks (default: 4) */
        public double spreadOffset = 4.0;
    }
    
    // ===== BREEDING =====
    public BreedingConfig breeding = new BreedingConfig();
    
    public static class BreedingConfig {
        /** Duration of breeding interaction in ticks (default: 60 = 3 seconds) */
        public int breedDuration = 60;
        
        /** Cooldown after breeding for herbivores in ticks (default: 24000 = 1 in-game day) */
        public int cooldownTicks = 24000;
        
        /** Cooldown multiplier for predators (default: 3 = 3 days) */
        public int predatorCooldownMultiplier = 3;
        
        /** Hunger cost for herbivores when breeding (default: 0.3) */
        public double herbivoreHungerCost = 0.3;
        
        /** Hunger cost for predators when breeding (default: 0.5) */
        public double predatorHungerCost = 0.5;
        
        /** Required prey-to-predator ratio for predator breeding (default: 4) */
        public int requiredPreyRatio = 4;
        
        /** Search radius for prey count in blocks (default: 48) */
        public double preySearchRadius = 48.0;
        
        /** Number of chunks to check for carrying capacity (default: 9 = 3x3 area) */
        public int carryingCapacityChunks = 9;
        
        /** Search range for finding a mate in blocks (default: 16) */
        public double mateSearchRange = 16.0;
        
        /** Squared distance for close enough to breed (default: 9 = 3 blocks) */
        public double breedingDistanceSq = 9.0;
    }
    
    // ===== FORAGING =====
    public ForageConfig forage = new ForageConfig();
    
    public static class ForageConfig {
        /** Search range for food in blocks (default: 10) */
        public int searchRange = 10;
        
        /** Time to eat food in ticks (default: 40 = 2 seconds) */
        public int forageTime = 40;
        
        /** Nutrition value from foraging (default: 25) */
        public int nutritionValue = 25;
        
        /** Cooldown before searching again if no food found in ticks (default: 100) */
        public int searchCooldown = 100;
        
        /** Squared distance to start eating food (default: 4 = 2 blocks) */
        public double eatDistanceSq = 4.0;
        
        /** Vertical range to search for food (default: 3) */
        public int verticalSearchRange = 3;
    }
    
    // ===== MIGRATION =====
    public MigrationConfig migration = new MigrationConfig();
    
    public static class MigrationConfig {
        /** Maximum migration duration in ticks (default: 24000 = 20 minutes) */
        public int maxMigrationTime = 24000;
        
        /** Ticks without movement before considered stuck (default: 200 = 10 seconds) */
        public int stuckThreshold = 200;
        
        /** How often to check migration conditions in ticks (default: 100) */
        public int checkInterval = 100;
        
        /** Distance to intermediate waypoints in blocks (default: 32) */
        public int waypointDistance = 32;
        
        /** Minimum migration urge to start migration (default: 0.3) */
        public double migrationUrgeThreshold = 0.3;
        
        /** Random chance to start migration when urge is high (default: 0.01 = 1%) */
        public double migrationChance = 0.01;
        
        /** Minimum suitability score for a migration target (default: 0.5) */
        public double suitabilityThreshold = 0.5;
        
        /** Squared distance to consider arrived at waypoint (default: 64 = 8 blocks) */
        public double arrivedDistanceSq = 64.0;
        
        /** Distance at which to use final destination instead of waypoint (default: 16) */
        public double closeEnoughDistance = 16.0;
        
        /** Vertical range to search for ground (default: 5) */
        public int groundSearchRange = 5;
    }
    
    // ===== TERRITORY PATROL =====
    public TerritoryConfig territory = new TerritoryConfig();
    
    public static class TerritoryConfig {
        /** Number of patrol points around territory perimeter (default: 8) */
        public int patrolPointCount = 8;
        
        /** Ticks at each patrol point (default: 100 = 5 seconds) */
        public int patrolDuration = 100;
        
        /** Random chance to start a patrol each tick (default: 0.05 = 5%) */
        public double patrolStartChance = 0.05;
        
        /** Patrol radius as multiplier of territory radius (default: 0.8 = 80% of boundary) */
        public double patrolRadiusMultiplier = 0.8;
        
        /** Squared distance to consider reached a patrol point (default: 4 = 2 blocks) */
        public double reachedPointDistanceSq = 4.0;
        
        /** Vertical range to search for ground at patrol points (default: 5) */
        public int groundSearchRange = 5;
    }
    
    // ===== CONFIG LOADING/SAVING =====
    
    public static TrophicConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }
    
    public static void load() {
        Path configPath = getConfigPath();
        
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                INSTANCE = GSON.fromJson(json, TrophicConfig.class);
                Trophic.LOGGER.info("Loaded Trophic config from {}", configPath);
            } catch (IOException e) {
                Trophic.LOGGER.error("Failed to load Trophic config, using defaults", e);
                INSTANCE = new TrophicConfig();
                save(); // Save defaults
            }
        } else {
            INSTANCE = new TrophicConfig();
            save(); // Create default config
            Trophic.LOGGER.info("Created default Trophic config at {}", configPath);
        }
    }
    
    public static void save() {
        if (INSTANCE == null) {
            INSTANCE = new TrophicConfig();
        }
        
        Path configPath = getConfigPath();
        
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            Trophic.LOGGER.error("Failed to save Trophic config", e);
        }
    }
    
    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("trophic.json");
    }
    
    /** Reload config from disk (for in-game reloading) */
    public static void reload() {
        INSTANCE = null;
        load();
    }
}
