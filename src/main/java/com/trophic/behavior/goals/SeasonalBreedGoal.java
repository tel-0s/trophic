package com.trophic.behavior.goals;

import com.trophic.Trophic;
import com.trophic.behavior.EcologicalEntity;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import com.trophic.simulation.SeasonManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * AI goal for seasonal breeding based on ecological conditions.
 * 
 * Animals will only breed when:
 * - It's their breeding season
 * - They have sufficient food (hunger > threshold)
 * - A compatible mate is nearby
 * - Population isn't at carrying capacity
 */
public class SeasonalBreedGoal extends Goal {
    private final AnimalEntity animal;
    private final double speed;
    
    private AnimalEntity mate;
    private int breedTimer;
    
    private static final int BREED_DURATION = 60;
    private static final int COOLDOWN_AFTER_BREEDING = 24000; // 1 in-game day (20 real minutes)

    public SeasonalBreedGoal(AnimalEntity animal, double speed) {
        this.animal = animal;
        this.speed = speed;
        
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // Basic checks
        if (animal.isBaby()) {
            return false;
        }
        
        if (animal.getBreedingAge() != 0) {
            return false; // On cooldown from vanilla breeding
        }
        
        // Check species-specific breeding conditions
        Identifier entityId = Registries.ENTITY_TYPE.getId(animal.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(entityId).orElse(null);
        
        if (species == null || species.getReproduction() == null) {
            return false;
        }
        
        SpeciesDefinition.Reproduction reproduction = species.getReproduction();
        
        // Check if breeding season
        SeasonManager seasonManager = Trophic.getInstance().getSeasonManager();
        if (!seasonManager.isBreedingSeason(
                reproduction.breedingSeasonStart(), 
                reproduction.breedingSeasonEnd())) {
            return false;
        }
        
        // Check food threshold
        if (animal instanceof EcologicalEntity eco) {
            if (eco.trophic_getHunger() < reproduction.foodThreshold()) {
                return false;
            }
        }
        
        // Check carrying capacity - prevent overpopulation
        if (!isUnderCarryingCapacity(species)) {
            return false;
        }
        
        // For carnivores/omnivores: check prey availability
        // Predators should only breed when prey is plentiful
        if (species.getDiet() != null && species.getDiet().type().canHunt()) {
            if (!hasAdequatePrey(species, registry)) {
                return false;
            }
        }
        
        // Find a mate
        mate = findMate();
        return mate != null;
    }

    @Override
    public boolean shouldContinue() {
        if (mate == null || !mate.isAlive() || mate.isBaby()) {
            return false;
        }
        
        return breedTimer < BREED_DURATION;
    }

    @Override
    public void start() {
        breedTimer = 0;
    }

    @Override
    public void stop() {
        mate = null;
    }

    @Override
    public void tick() {
        breedTimer++;
        
        if (mate == null) {
            return;
        }
        
        // Look at mate
        animal.getLookControl().lookAt(mate, 10.0F, animal.getMaxLookPitchChange());
        
        // Move toward mate
        double distanceSq = animal.squaredDistanceTo(mate);
        if (distanceSq > 9.0) {
            animal.getNavigation().startMovingTo(mate, speed);
        } else {
            animal.getNavigation().stop();
            
            // Close enough - breed
            if (breedTimer >= BREED_DURATION) {
                breed();
            }
        }
    }

    /**
     * Finds a suitable mate nearby.
     */
    private AnimalEntity findMate() {
        Box searchBox = animal.getBoundingBox().expand(16.0);
        List<AnimalEntity> candidates = animal.getEntityWorld().getEntitiesByClass(
                AnimalEntity.class,
                searchBox,
                other -> isValidMate(other)
        );
        
        // Return nearest valid mate
        return candidates.stream()
                .min(Comparator.comparingDouble(animal::squaredDistanceTo))
                .orElse(null);
    }

    /**
     * Checks if another animal is a valid mate.
     */
    private boolean isValidMate(AnimalEntity other) {
        if (other == animal) {
            return false;
        }
        
        if (other.getType() != animal.getType()) {
            return false;
        }
        
        if (other.isBaby() || other.getBreedingAge() != 0) {
            return false;
        }
        
        // Check other's food threshold too
        Identifier entityId = Registries.ENTITY_TYPE.getId(animal.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(entityId).orElse(null);
        
        if (species != null && species.getReproduction() != null) {
            if (other instanceof EcologicalEntity eco) {
                if (eco.trophic_getHunger() < species.getReproduction().foodThreshold()) {
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * Checks if there's adequate prey in the area for predators to breed.
     * Predators need a healthy prey population to sustain offspring.
     */
    private boolean hasAdequatePrey(SpeciesDefinition species, SpeciesRegistry registry) {
        // Get the prey species for this predator
        Identifier predatorId = Registries.ENTITY_TYPE.getId(animal.getType());
        Set<Identifier> preyIds = registry.getPreyOf(predatorId);
        
        if (preyIds.isEmpty()) {
            return true; // No prey defined, allow breeding
        }
        
        // Count prey in a large area
        Box searchBox = animal.getBoundingBox().expand(48.0);
        int preyCount = 0;
        
        for (Identifier preyId : preyIds) {
            EntityType<?> preyType = Registries.ENTITY_TYPE.get(preyId);
            if (preyType != null) {
                preyCount += animal.getEntityWorld().getEntitiesByType(
                        (EntityType<? extends net.minecraft.entity.Entity>) preyType,
                        searchBox,
                        entity -> entity.isAlive()
                ).size();
            }
        }
        
        // Count predators of the same type in the area
        int predatorCount = animal.getEntityWorld().getEntitiesByClass(
                animal.getClass(),
                searchBox,
                other -> other.isAlive() && other.getType() == animal.getType()
        ).size();
        
        // Require at least 4 prey per predator to breed
        // This prevents predator overpopulation
        int requiredPreyRatio = 4;
        int minPreyForBreeding = predatorCount * requiredPreyRatio;
        
        if (preyCount < minPreyForBreeding) {
            Trophic.LOGGER.debug("{} cannot breed: only {} prey for {} predators (need {})",
                    predatorId, preyCount, predatorCount, minPreyForBreeding);
            return false;
        }
        
        return true;
    }

    /**
     * Checks if the local population is under carrying capacity.
     * Animals won't breed if the area is already at capacity.
     */
    private boolean isUnderCarryingCapacity(SpeciesDefinition species) {
        if (species.getPopulation() == null) {
            return true; // No population limits defined
        }
        
        int capacityPerChunk = (int) species.getPopulation().carryingCapacityPerChunk();
        if (capacityPerChunk <= 0) {
            return true;
        }
        
        // Check a 3x3 chunk area (48 blocks radius roughly)
        // This gives us 9 chunks worth of capacity
        int areaCapacity = capacityPerChunk * 9;
        
        Box searchBox = animal.getBoundingBox().expand(48.0);
        int currentPopulation = animal.getEntityWorld().getEntitiesByClass(
                animal.getClass(),
                searchBox,
                other -> other.isAlive() && other.getType() == animal.getType()
        ).size();
        
        if (currentPopulation >= areaCapacity) {
            Trophic.LOGGER.debug("{} cannot breed: population {} at capacity {} in area",
                    Registries.ENTITY_TYPE.getId(animal.getType()), currentPopulation, areaCapacity);
            return false;
        }
        
        return true;
    }

    /**
     * Performs the breeding action.
     */
    private void breed() {
        if (!(animal.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        
        // Get litter size from species definition
        Identifier entityId = Registries.ENTITY_TYPE.getId(animal.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(entityId).orElse(null);
        
        int litterSize = 1;
        if (species != null && species.getReproduction() != null) {
            int min = species.getReproduction().minLitterSize();
            int max = species.getReproduction().maxLitterSize();
            litterSize = min + animal.getRandom().nextInt(max - min + 1);
        }
        
        // Spawn offspring
        for (int i = 0; i < litterSize; i++) {
            PassiveEntity offspring = animal.createChild(serverWorld, mate);
            if (offspring != null) {
                offspring.setBaby(true);
                offspring.refreshPositionAndAngles(
                        animal.getX() + (animal.getRandom().nextDouble() - 0.5) * 2,
                        animal.getY(),
                        animal.getZ() + (animal.getRandom().nextDouble() - 0.5) * 2,
                        0.0F, 0.0F
                );
                serverWorld.spawnEntity(offspring);
            }
        }
        
        // Set breeding cooldown (longer for predators)
        int cooldown = COOLDOWN_AFTER_BREEDING;
        if (species != null && species.getDiet() != null && species.getDiet().type().canHunt()) {
            cooldown = COOLDOWN_AFTER_BREEDING * 3; // 15 minutes for predators
        }
        animal.setBreedingAge(cooldown);
        mate.setBreedingAge(cooldown);
        
        // Reduce hunger from breeding effort (more for predators)
        double hungerCost = 0.3;
        if (species != null && species.getDiet() != null && species.getDiet().type().canHunt()) {
            hungerCost = 0.5; // Breeding is more taxing for predators
        }
        if (animal instanceof EcologicalEntity eco) {
            eco.trophic_setHunger(eco.trophic_getHunger() - hungerCost);
        }
        if (mate instanceof EcologicalEntity eco) {
            eco.trophic_setHunger(eco.trophic_getHunger() - hungerCost);
        }
        
        Trophic.LOGGER.debug("{} bred with mate, producing {} offspring", 
                entityId, litterSize);
    }
}
