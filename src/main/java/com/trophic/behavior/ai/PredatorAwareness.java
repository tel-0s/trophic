package com.trophic.behavior.ai;

import com.trophic.Trophic;
import com.trophic.registry.SpeciesRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Utility class for prey animals to detect nearby predators.
 */
public class PredatorAwareness {
    
    /**
     * Scans for predators that hunt this entity type.
     * 
     * @param prey the potential prey entity
     * @param range the detection range
     * @return the nearest threatening predator, or null
     */
    public static LivingEntity findNearestPredator(AnimalEntity prey, double range) {
        Identifier preyId = Registries.ENTITY_TYPE.getId(prey.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        
        // Get all predators of this species
        Set<Identifier> predatorIds = registry.getPredatorsOf(preyId);
        if (predatorIds.isEmpty()) {
            return null;
        }
        
        // Search for predators in range
        Box searchBox = prey.getBoundingBox().expand(range);
        List<LivingEntity> nearbyPredators = prey.getEntityWorld().getEntitiesByClass(
                LivingEntity.class,
                searchBox,
                entity -> isPredatorThreat(entity, prey, predatorIds)
        );
        
        if (nearbyPredators.isEmpty()) {
            return null;
        }
        
        // Return the nearest predator
        return nearbyPredators.stream()
                .min(Comparator.comparingDouble(prey::squaredDistanceTo))
                .orElse(null);
    }
    
    /**
     * Checks if an entity is a threatening predator.
     */
    private static boolean isPredatorThreat(LivingEntity entity, AnimalEntity prey, Set<Identifier> predatorIds) {
        if (entity == prey || !entity.isAlive()) {
            return false;
        }
        
        Identifier entityId = Registries.ENTITY_TYPE.getId(entity.getType());
        if (!predatorIds.contains(entityId)) {
            return false;
        }
        
        // Check if predator can see us (more threatening)
        if (entity instanceof MobEntity mob) {
            // Predator is more threatening if it's looking at us
            return mob.canSee(prey);
        }
        
        return true;
    }
    
    /**
     * Calculates the threat level of a predator (0.0 to 1.0).
     * Higher threat = predator is closer, can see prey, and is actively hunting.
     */
    public static double calculateThreatLevel(AnimalEntity prey, LivingEntity predator, double maxRange) {
        double distance = prey.distanceTo(predator);
        double distanceFactor = 1.0 - (distance / maxRange);
        
        double visibilityFactor = 0.5;
        if (predator instanceof MobEntity mob && mob.canSee(prey)) {
            visibilityFactor = 1.0;
        }
        
        // Check if predator is moving toward us
        double approachFactor = 0.5;
        if (predator instanceof MobEntity mob) {
            if (mob.getNavigation().isFollowingPath()) {
                // Check if path is roughly toward us
                double dx = prey.getX() - predator.getX();
                double dz = prey.getZ() - predator.getZ();
                double vx = predator.getVelocity().x;
                double vz = predator.getVelocity().z;
                
                // Dot product indicates if moving toward prey
                if (dx * vx + dz * vz > 0) {
                    approachFactor = 1.0;
                }
            }
        }
        
        return distanceFactor * visibilityFactor * approachFactor;
    }
    
    /**
     * Gets the recommended flee distance based on predator type.
     */
    public static double getFleeDistance(Identifier predatorId) {
        // Different predators warrant different flee distances
        String path = predatorId.getPath();
        
        return switch (path) {
            case "wolf" -> 24.0; // Pack hunters, flee far
            case "polar_bear" -> 20.0;
            case "fox", "ocelot" -> 16.0;
            default -> 20.0;
        };
    }
    
    /**
     * Checks if the prey should be alert (predators nearby but not immediate threat).
     */
    public static boolean shouldBeAlert(AnimalEntity prey, double alertRange) {
        Identifier preyId = Registries.ENTITY_TYPE.getId(prey.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        Set<Identifier> predatorIds = registry.getPredatorsOf(preyId);
        
        if (predatorIds.isEmpty()) {
            return false;
        }
        
        Box searchBox = prey.getBoundingBox().expand(alertRange);
        return prey.getEntityWorld().getEntitiesByClass(
                LivingEntity.class,
                searchBox,
                entity -> {
                    Identifier entityId = Registries.ENTITY_TYPE.getId(entity.getType());
                    return predatorIds.contains(entityId) && entity.isAlive();
                }
        ).size() > 0;
    }
}
