package com.trophic.behavior.goals;

import com.trophic.Trophic;
import com.trophic.behavior.ai.PredatorAwareness;
import com.trophic.registry.SpeciesRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.Set;

/**
 * AI goal for prey animals to detect and flee from predators.
 * 
 * Features:
 * - Scans for predators based on species registry
 * - Flees in the opposite direction
 * - Higher priority when predator is close
 * - Integrates with herd behavior (future)
 */
public class FleePredatorGoal extends Goal {
    private final AnimalEntity prey;
    private final double detectionRange;
    private final double fleeSpeed;
    
    private LivingEntity predator;
    private Vec3d fleeTarget;
    private int fleeTimer;
    
    private static final int MAX_FLEE_TIME = 200; // 10 seconds max flee
    private static final int RECALCULATE_INTERVAL = 10;

    public FleePredatorGoal(AnimalEntity prey, double fleeSpeed) {
        this(prey, fleeSpeed, 16.0);
    }

    public FleePredatorGoal(AnimalEntity prey, double fleeSpeed, double detectionRange) {
        this.prey = prey;
        this.fleeSpeed = fleeSpeed;
        this.detectionRange = detectionRange;
        
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        // Check if this species has predators
        Identifier preyId = Registries.ENTITY_TYPE.getId(prey.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        Set<Identifier> predatorIds = registry.getPredatorsOf(preyId);
        
        if (predatorIds.isEmpty()) {
            return false;
        }
        
        // Look for predators
        predator = PredatorAwareness.findNearestPredator(prey, detectionRange);
        if (predator == null) {
            return false;
        }
        
        // Calculate flee direction
        fleeTarget = calculateFleeTarget();
        return fleeTarget != null;
    }

    @Override
    public boolean shouldContinue() {
        if (predator == null || !predator.isAlive()) {
            return false;
        }
        
        if (fleeTimer > MAX_FLEE_TIME) {
            return false;
        }
        
        // Stop fleeing if predator is far enough away
        double safeDistance = PredatorAwareness.getFleeDistance(
                Registries.ENTITY_TYPE.getId(predator.getType())
        );
        
        return prey.squaredDistanceTo(predator) < safeDistance * safeDistance * 1.5;
    }

    @Override
    public void start() {
        fleeTimer = 0;
        
        Trophic.LOGGER.debug("{} started fleeing from {}", 
                Registries.ENTITY_TYPE.getId(prey.getType()),
                Registries.ENTITY_TYPE.getId(predator.getType()));
    }

    @Override
    public void stop() {
        predator = null;
        fleeTarget = null;
        prey.getNavigation().stop();
    }

    @Override
    public void tick() {
        fleeTimer++;
        
        // Recalculate flee direction periodically
        if (fleeTimer % RECALCULATE_INTERVAL == 0) {
            fleeTarget = calculateFleeTarget();
        }
        
        if (fleeTarget != null) {
            prey.getNavigation().startMovingTo(
                    fleeTarget.x, fleeTarget.y, fleeTarget.z, fleeSpeed
            );
        }
    }

    /**
     * Calculates the best flee target - away from the predator.
     */
    private Vec3d calculateFleeTarget() {
        if (predator == null) {
            return null;
        }
        
        // Calculate direction away from predator
        double dx = prey.getX() - predator.getX();
        double dz = prey.getZ() - predator.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        
        if (length < 0.01) {
            // Predator is on top of us, flee in a random direction
            double angle = prey.getRandom().nextDouble() * Math.PI * 2;
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            length = 1.0;
        }
        
        // Normalize and scale
        dx = dx / length * 16.0; // Flee 16 blocks away
        dz = dz / length * 16.0;
        
        // Find a valid position in that direction
        Vec3d fleeDirection = new Vec3d(
                prey.getX() + dx,
                prey.getY(),
                prey.getZ() + dz
        );
        
        // Try to find a path to the flee target
        Path path = prey.getNavigation().findPathTo(
                new BlockPos((int) fleeDirection.x, (int) fleeDirection.y, (int) fleeDirection.z),
                0
        );
        
        if (path != null) {
            return fleeDirection;
        }
        
        // Try alternative directions if direct path is blocked
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double rotatedDx = dx * Math.cos(angle) - dz * Math.sin(angle);
            double rotatedDz = dx * Math.sin(angle) + dz * Math.cos(angle);
            
            Vec3d altTarget = new Vec3d(
                    prey.getX() + rotatedDx,
                    prey.getY(),
                    prey.getZ() + rotatedDz
            );
            
            path = prey.getNavigation().findPathTo(
                    new BlockPos((int) altTarget.x, (int) altTarget.y, (int) altTarget.z),
                    0
            );
            
            if (path != null) {
                return altTarget;
            }
        }
        
        return null;
    }

    /**
     * @return the current predator threat, or null
     */
    public LivingEntity getPredator() {
        return predator;
    }
}
