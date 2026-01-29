package com.trophic.simulation;

import com.trophic.Trophic;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;

import java.util.*;

/**
 * Plans and coordinates animal migration between regions.
 * 
 * Migration is triggered by:
 * - Seasonal changes
 * - Food scarcity
 * - Population pressure
 * - Habitat unsuitability
 */
public class MigrationPlanner {
    
    /**
     * Represents a migration destination with suitability score.
     */
    public record MigrationTarget(
            BlockPos destination,
            double suitabilityScore,
            MigrationReason reason
    ) {}
    
    /**
     * Reasons for migration.
     */
    public enum MigrationReason {
        SEASONAL,       // Following seasonal patterns
        FOOD_SCARCITY,  // Not enough food in current area
        POPULATION,     // Too crowded
        HABITAT         // Current habitat becoming unsuitable
    }

    /**
     * Calculates a migration target for a species from a given location.
     */
    public static Optional<MigrationTarget> planMigration(
            ServerWorld world,
            Identifier speciesId,
            BlockPos currentPos
    ) {
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(speciesId).orElse(null);
        
        if (species == null) {
            return Optional.empty();
        }
        
        // Determine migration reason
        MigrationReason reason = determineMigrationReason(world, speciesId, currentPos);
        if (reason == null) {
            return Optional.empty();
        }
        
        // Search for suitable destination
        BlockPos destination = findSuitableDestination(world, species, currentPos, reason);
        if (destination == null) {
            return Optional.empty();
        }
        
        double suitability = calculateHabitatSuitability(world, species, destination);
        
        return Optional.of(new MigrationTarget(destination, suitability, reason));
    }

    /**
     * Determines why an animal might want to migrate.
     */
    private static MigrationReason determineMigrationReason(
            ServerWorld world,
            Identifier speciesId,
            BlockPos currentPos
    ) {
        SeasonManager seasonManager = Trophic.getInstance().getSeasonManager();
        
        // Check seasonal migration pressure
        double migrationUrge = SeasonalEffects.getMigrationUrge(speciesId);
        if (migrationUrge > 0.5) {
            return MigrationReason.SEASONAL;
        }
        
        // Check food availability (via vegetation level)
        // In a full implementation, this would check the region ecosystem
        double forageModifier = SeasonalEffects.getForagingSuccessModifier();
        if (forageModifier < 0.5) {
            return MigrationReason.FOOD_SCARCITY;
        }
        
        // Check population pressure
        // This would check regional population vs carrying capacity
        
        return null;
    }

    /**
     * Finds a suitable destination for migration.
     */
    private static BlockPos findSuitableDestination(
            ServerWorld world,
            SpeciesDefinition species,
            BlockPos currentPos,
            MigrationReason reason
    ) {
        Random random = new Random();
        int searchRadius = 256; // Search up to 256 blocks away
        
        // For seasonal migration, move toward warmer/colder regions
        Vec3d migrationDirection = calculateMigrationDirection(reason);
        
        // Search in the general migration direction
        for (int attempt = 0; attempt < 10; attempt++) {
            // Add some randomness to the direction
            double angle = Math.atan2(migrationDirection.z, migrationDirection.x);
            angle += (random.nextDouble() - 0.5) * Math.PI / 2; // +/- 45 degrees
            
            double distance = 128 + random.nextDouble() * 128;
            
            int targetX = currentPos.getX() + (int)(Math.cos(angle) * distance);
            int targetZ = currentPos.getZ() + (int)(Math.sin(angle) * distance);
            
            // Find ground level
            BlockPos target = findGroundLevel(world, new BlockPos(targetX, currentPos.getY(), targetZ));
            
            if (target != null) {
                double suitability = calculateHabitatSuitability(world, species, target);
                if (suitability > 0.5) {
                    return target;
                }
            }
        }
        
        return null;
    }

    /**
     * Calculates the general direction for migration based on reason.
     */
    private static Vec3d calculateMigrationDirection(MigrationReason reason) {
        SeasonManager seasonManager = Trophic.getInstance().getSeasonManager();
        SeasonManager.Season season = seasonManager.getCurrentSeason();
        
        return switch (reason) {
            case SEASONAL -> {
                // In autumn, migrate "south" (negative Z in Minecraft)
                // In spring, migrate "north" (positive Z)
                if (season == SeasonManager.Season.AUTUMN || season == SeasonManager.Season.WINTER) {
                    yield new Vec3d(0, 0, -1); // South
                } else {
                    yield new Vec3d(0, 0, 1); // North
                }
            }
            case FOOD_SCARCITY, POPULATION, HABITAT -> {
                // Random direction for resource-based migration
                double angle = Math.random() * Math.PI * 2;
                yield new Vec3d(Math.cos(angle), 0, Math.sin(angle));
            }
        };
    }

    /**
     * Calculates habitat suitability score for a species at a location.
     */
    public static double calculateHabitatSuitability(
            ServerWorld world,
            SpeciesDefinition species,
            BlockPos pos
    ) {
        if (species.getHabitat() == null) {
            return 0.5; // Neutral if no habitat preferences
        }
        
        // Get biome at position
        var biomeEntry = world.getBiome(pos);
        Identifier biomeId = biomeEntry.getKey()
                .map(RegistryKey::getValue)
                .orElse(Identifier.of("minecraft:plains"));
        
        // Check biome suitability
        if (species.getHabitat().avoidsBiomes().contains(biomeId)) {
            return 0.0;
        }
        
        double biomeFactor = 0.5;
        if (species.getHabitat().preferredBiomes().isEmpty() ||
            species.getHabitat().preferredBiomes().contains(biomeId)) {
            biomeFactor = 1.0;
        }
        
        // Check temperature (using biome temperature as proxy)
        double temperature = biomeEntry.value().getTemperature();
        double tempFactor = 1.0;
        
        // Map Minecraft biome temperature (-0.5 to 2.0) to our range
        double normalizedTemp = (temperature + 0.5) / 2.5 * 50 - 10; // -10 to 40
        
        if (normalizedTemp < species.getHabitat().minTemperature()) {
            tempFactor = Math.max(0, 1.0 - (species.getHabitat().minTemperature() - normalizedTemp) / 20.0);
        } else if (normalizedTemp > species.getHabitat().maxTemperature()) {
            tempFactor = Math.max(0, 1.0 - (normalizedTemp - species.getHabitat().maxTemperature()) / 20.0);
        }
        
        return biomeFactor * tempFactor;
    }

    /**
     * Finds ground level at a position.
     */
    private static BlockPos findGroundLevel(ServerWorld world, BlockPos pos) {
        // Use world height limit
        int y = world.getTopYInclusive();
        
        // Search down for solid ground
        for (int dy = 0; dy < 128; dy++) {
            BlockPos checkPos = new BlockPos(pos.getX(), y - dy, pos.getZ());
            if (!world.getBlockState(checkPos).isAir() &&
                world.getBlockState(checkPos.up()).isAir()) {
                return checkPos.up();
            }
        }
        
        return null;
    }

    /**
     * Gets all active migrations for a world.
     */
    public static Map<UUID, MigrationTarget> getActiveMigrations() {
        // In a full implementation, this would track active migrations
        return new HashMap<>();
    }
}
