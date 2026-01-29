package com.trophic.behavior.goals;

import com.trophic.Trophic;
import com.trophic.behavior.EcologicalEntity;
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
 * - Respects home range to prevent emergent migration
 * - Higher priority when predator is close
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
     * Calculates the best flee target - away from the predator but within home range.
     */
    private Vec3d calculateFleeTarget() {
        if (predator == null) {
            return null;
        }
        
        // Get home position and range
        BlockPos homePos = null;
        double homeRange = 48.0;
        if (prey instanceof EcologicalEntity eco) {
            homePos = eco.trophic_getHomePos();
            homeRange = eco.trophic_getHomeRange();
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
        
        // Try different angles, preferring directions that stay within home range
        Vec3d bestTarget = null;
        double bestScore = Double.MAX_VALUE;
        
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double rotatedDx = dx * Math.cos(angle) - dz * Math.sin(angle);
            double rotatedDz = dx * Math.sin(angle) + dz * Math.cos(angle);
            
            Vec3d target = new Vec3d(
                    prey.getX() + rotatedDx,
                    prey.getY(),
                    prey.getZ() + rotatedDz
            );
            
            // Check if we can path there
            Path path = prey.getNavigation().findPathTo(
                    new BlockPos((int) target.x, (int) target.y, (int) target.z),
                    0
            );
            
            if (path == null) {
                continue;
            }
            
            // Score this target - lower is better
            // Primary: distance from predator (want to maximize)
            // Secondary: distance from home (want to minimize)
            double distFromPredator = target.squaredDistanceTo(predator.getX(), predator.getY(), predator.getZ());
            double distFromHome = 0;
            
            if (homePos != null) {
                distFromHome = target.squaredDistanceTo(homePos.getX(), homePos.getY(), homePos.getZ());
                
                // If this would take us outside home range, heavily penalize
                if (Math.sqrt(distFromHome) > homeRange) {
                    distFromHome *= 10; // Strong penalty for leaving home range
                }
            }
            
            // Score: want high distance from predator, low distance from home
            double score = distFromHome - distFromPredator * 0.5;
            
            if (score < bestScore) {
                bestScore = score;
                bestTarget = target;
            }
        }
        
        return bestTarget;
    }

    /**
     * @return the current predator threat, or null
     */
    public LivingEntity getPredator() {
        return predator;
    }
}
