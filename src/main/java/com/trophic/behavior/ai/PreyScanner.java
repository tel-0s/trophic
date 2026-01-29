package com.trophic.behavior.ai;

import com.trophic.Trophic;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.*;
import java.util.function.Predicate;

/**
 * Utility class for predators to scan for valid prey targets.
 */
public class PreyScanner {
    
    /**
     * Finds the best prey target for a predator within range.
     * 
     * @param predator the hunting predator
     * @param range the maximum search range
     * @return the best prey target, or null if none found
     */
    public static LivingEntity findBestPrey(MobEntity predator, double range) {
        Identifier predatorId = Registries.ENTITY_TYPE.getId(predator.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition predatorSpecies = registry.getSpecies(predatorId).orElse(null);
        
        if (predatorSpecies == null || predatorSpecies.getDiet() == null) {
            return null;
        }
        
        SpeciesDefinition.Diet diet = predatorSpecies.getDiet();
        if (!diet.type().canHunt() || diet.prey().isEmpty()) {
            return null;
        }
        
        // Get all potential prey in range
        Box searchBox = predator.getBoundingBox().expand(range);
        List<LivingEntity> potentialPrey = predator.getEntityWorld().getEntitiesByClass(
                LivingEntity.class,
                searchBox,
                entity -> isValidPrey(entity, predator, diet)
        );
        
        if (potentialPrey.isEmpty()) {
            return null;
        }
        
        // Score and sort prey by preference and distance
        return potentialPrey.stream()
                .min(Comparator.comparingDouble(prey -> scorePreyTarget(predator, prey, diet)))
                .orElse(null);
    }
    
    /**
     * Checks if an entity is valid prey for the predator.
     */
    public static boolean isValidPrey(LivingEntity target, MobEntity predator, SpeciesDefinition.Diet diet) {
        if (target == predator || !target.isAlive()) {
            return false;
        }
        
        // Check if target is a recognized prey species
        Identifier targetId = Registries.ENTITY_TYPE.getId(target.getType());
        if (!diet.canHunt(targetId)) {
            return false;
        }
        
        // Don't hunt babies (optional ethical consideration)
        if (target instanceof AnimalEntity animal && animal.isBaby()) {
            return false;
        }
        
        // Check line of sight
        if (!predator.canSee(target)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Scores a prey target (lower is better).
     * Takes into account preference and distance.
     * Distance is weighted heavily to prevent predators chasing distant targets.
     */
    public static double scorePreyTarget(MobEntity predator, LivingEntity prey, SpeciesDefinition.Diet diet) {
        Identifier preyId = Registries.ENTITY_TYPE.getId(prey.getType());
        SpeciesDefinition.PreyInfo preyInfo = diet.getPreyInfo(preyId).orElse(null);
        
        double distanceSq = predator.squaredDistanceTo(prey);
        double preference = preyInfo != null ? preyInfo.preference() : 0.1;
        
        // Lower score = better target
        // Distance is primary factor - nearby prey strongly preferred
        // Preference only provides a moderate adjustment (up to 50% bonus)
        double distanceScore = distanceSq;
        double preferenceMultiplier = 1.0 - (preference * 0.5); // 0.5 to 1.0
        
        return distanceScore * preferenceMultiplier;
    }
    
    /**
     * Gets the nutritional value for eating the given prey.
     */
    public static int getNutritionalValue(MobEntity predator, LivingEntity prey) {
        Identifier predatorId = Registries.ENTITY_TYPE.getId(predator.getType());
        Identifier preyId = Registries.ENTITY_TYPE.getId(prey.getType());
        
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition predatorSpecies = registry.getSpecies(predatorId).orElse(null);
        
        if (predatorSpecies == null || predatorSpecies.getDiet() == null) {
            return 50; // Default value
        }
        
        SpeciesDefinition.PreyInfo preyInfo = predatorSpecies.getDiet().getPreyInfo(preyId).orElse(null);
        return preyInfo != null ? preyInfo.nutritionalValue() : 50;
    }
    
    /**
     * Gets the hunt cooldown for a predator species.
     */
    public static int getHuntCooldown(MobEntity predator) {
        Identifier predatorId = Registries.ENTITY_TYPE.getId(predator.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition predatorSpecies = registry.getSpecies(predatorId).orElse(null);
        
        if (predatorSpecies == null || predatorSpecies.getDiet() == null) {
            return 6000; // Default 5 minutes
        }
        
        return predatorSpecies.getDiet().huntCooldownTicks();
    }
    
    /**
     * Creates a predicate for filtering prey entities.
     */
    public static Predicate<LivingEntity> createPreyPredicate(MobEntity predator) {
        Identifier predatorId = Registries.ENTITY_TYPE.getId(predator.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition predatorSpecies = registry.getSpecies(predatorId).orElse(null);
        
        if (predatorSpecies == null || predatorSpecies.getDiet() == null) {
            return entity -> false;
        }
        
        SpeciesDefinition.Diet diet = predatorSpecies.getDiet();
        return entity -> isValidPrey(entity, predator, diet);
    }
}
