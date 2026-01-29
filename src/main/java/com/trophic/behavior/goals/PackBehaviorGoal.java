package com.trophic.behavior.goals;

import com.trophic.Trophic;
import com.trophic.behavior.EcologicalEntity;
import com.trophic.behavior.ai.PackCoordinator;
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
    
    private static final int UPDATE_INTERVAL = 20;
    private static final double FOLLOW_START_DISTANCE = 32.0; // Only regroup when VERY far
    private static final double FOLLOW_STOP_DISTANCE = 8.0;

    public PackBehaviorGoal(AnimalEntity animal, double followSpeed) {
        this(animal, followSpeed, 32.0);
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
        
        double distanceSq = animal.squaredDistanceTo(packLeader);
        return distanceSq > FOLLOW_START_DISTANCE * FOLLOW_START_DISTANCE;
    }

    @Override
    public boolean shouldContinue() {
        if (packLeader == null || !packLeader.isAlive()) {
            return false;
        }
        
        // Stop following if close enough
        double distanceSq = animal.squaredDistanceTo(packLeader);
        return distanceSq > FOLLOW_STOP_DISTANCE * FOLLOW_STOP_DISTANCE;
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
        if (updateTimer % UPDATE_INTERVAL == 0) {
            // Calculate target position - move toward pack center, not directly to leader
            Vec3d packCenter = PackCoordinator.getPackCenter(animal);
            
            // Add some randomization to avoid all animals bunching up
            double offsetX = (animal.getRandom().nextDouble() - 0.5) * 4.0;
            double offsetZ = (animal.getRandom().nextDouble() - 0.5) * 4.0;
            
            animal.getNavigation().startMovingTo(
                    packCenter.x + offsetX,
                    packCenter.y,
                    packCenter.z + offsetZ,
                    followSpeed
            );
        }
    }
}
