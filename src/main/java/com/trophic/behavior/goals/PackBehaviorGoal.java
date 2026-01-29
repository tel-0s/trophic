package com.trophic.behavior.goals;

import com.trophic.Trophic;
import com.trophic.behavior.EcologicalEntity;
import com.trophic.behavior.ai.PackCoordinator;
import com.trophic.config.TrophicConfig;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * AI goal for pack/herd animals to stay together and follow their leader.
 * 
 * Pack behavior is low priority - hunting and fleeing always take precedence.
 * Only activates when an animal has strayed very far from the pack.
 */
public class PackBehaviorGoal extends Goal {
    private final AnimalEntity animal;
    private final double followSpeed;
    private final double maxDistanceFromPack;
    
    private AnimalEntity packLeader;
    private int updateTimer;

    public PackBehaviorGoal(AnimalEntity animal, double followSpeed) {
        this(animal, followSpeed, TrophicConfig.get().pack.maxDistanceFromPack);
    }

    public PackBehaviorGoal(AnimalEntity animal, double followSpeed, double maxDistanceFromPack) {
        this.animal = animal;
        this.followSpeed = followSpeed;
        this.maxDistanceFromPack = maxDistanceFromPack;
        
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // Don't regroup if hungry - hunting/foraging takes priority
        if (animal instanceof EcologicalEntity eco) {
            if (eco.trophic_isHungry()) {
                return false;
            }
        }
        
        // Check if this is a social species
        Identifier entityId = Registries.ENTITY_TYPE.getId(animal.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(entityId).orElse(null);
        
        if (species == null || species.getSocial() == null || !species.getSocial().isSocial()) {
            return false;
        }
        
        // Get pack assignment
        PackCoordinator.getOrAssignPack(animal);
        
        // If we're the leader, don't follow
        if (PackCoordinator.isPackLeader(animal)) {
            return false;
        }
        
        // Get leader and check distance
        packLeader = PackCoordinator.getPackLeader(animal);
        if (packLeader == null || packLeader == animal) {
            return false;
        }
        
        TrophicConfig.PackConfig packConfig = TrophicConfig.get().pack;
        double distanceSq = animal.squaredDistanceTo(packLeader);
        return distanceSq > packConfig.followStartDistance * packConfig.followStartDistance;
    }

    @Override
    public boolean shouldContinue() {
        if (packLeader == null || !packLeader.isAlive()) {
            return false;
        }
        
        // Stop following if close enough
        TrophicConfig.PackConfig packConfig = TrophicConfig.get().pack;
        double distanceSq = animal.squaredDistanceTo(packLeader);
        return distanceSq > packConfig.followStopDistance * packConfig.followStopDistance;
    }

    @Override
    public void start() {
        updateTimer = 0;
    }

    @Override
    public void stop() {
        packLeader = null;
        animal.getNavigation().stop();
    }

    @Override
    public void tick() {
        updateTimer++;
        
        if (packLeader == null) {
            return;
        }
        
        // Look at leader
        animal.getLookControl().lookAt(packLeader, 10.0F, animal.getMaxLookPitchChange());
        
        // Recalculate path periodically
        if (updateTimer % TrophicConfig.get().pack.updateInterval == 0) {
            // Calculate target position - move toward pack center, not directly to leader
            Vec3d packCenter = PackCoordinator.getPackCenter(animal);
            
            // Add some randomization to avoid all animals bunching up
            double spreadOffset = TrophicConfig.get().pack.spreadOffset;
            double offsetX = (animal.getRandom().nextDouble() - 0.5) * spreadOffset;
            double offsetZ = (animal.getRandom().nextDouble() - 0.5) * spreadOffset;
            
            animal.getNavigation().startMovingTo(
                    packCenter.x + offsetX,
                    packCenter.y,
                    packCenter.z + offsetZ,
                    followSpeed
            );
        }
    }
}
