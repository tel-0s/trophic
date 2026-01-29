package com.trophic.behavior.goals;

import com.trophic.Trophic;
import com.trophic.simulation.MigrationPlanner;
import com.trophic.simulation.MigrationPlanner.MigrationTarget;
import com.trophic.simulation.SeasonalEffects;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.Optional;

/**
 * AI goal for animals to migrate to more suitable habitats.
 * 
 * Migration is a long-distance movement triggered by:
 * - Seasonal changes
 * - Food scarcity
 * - Population pressure
 */
public class MigrationGoal extends Goal {
    private final AnimalEntity animal;
    private final double speed;
    
    private MigrationTarget target;
    private int migrationTimer;
    private int stuckTimer;
    private Vec3d lastPosition;
    
    private static final int MAX_MIGRATION_TIME = 24000; // 20 minutes
    private static final int STUCK_THRESHOLD = 200; // 10 seconds
    private static final int CHECK_INTERVAL = 100; // Check conditions every 5 seconds

    public MigrationGoal(AnimalEntity animal, double speed) {
        this.animal = animal;
        this.speed = speed;
        
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (!(animal.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }
        
        // Check migration urge
        Identifier speciesId = Registries.ENTITY_TYPE.getId(animal.getType());
        double migrationUrge = SeasonalEffects.getMigrationUrge(speciesId);
        
        // Only migrate if urge is high and random chance
        if (migrationUrge < 0.3 || animal.getRandom().nextFloat() > 0.01) {
            return false;
        }
        
        // Plan migration
        Optional<MigrationTarget> plannedTarget = MigrationPlanner.planMigration(
                serverWorld, speciesId, animal.getBlockPos()
        );
        
        if (plannedTarget.isEmpty()) {
            return false;
        }
        
        target = plannedTarget.get();
        return target.suitabilityScore() > 0.5;
    }

    @Override
    public boolean shouldContinue() {
        if (target == null) {
            return false;
        }
        
        if (migrationTimer > MAX_MIGRATION_TIME) {
            return false; // Give up after too long
        }
        
        // Check if stuck
        if (stuckTimer > STUCK_THRESHOLD) {
            return false;
        }
        
        // Check if arrived
        double distanceSq = animal.squaredDistanceTo(
                target.destination().getX() + 0.5,
                target.destination().getY(),
                target.destination().getZ() + 0.5
        );
        
        return distanceSq > 64; // More than 8 blocks away
    }

    @Override
    public void start() {
        migrationTimer = 0;
        stuckTimer = 0;
        lastPosition = new Vec3d(animal.getX(), animal.getY(), animal.getZ());
        
        Trophic.LOGGER.info("{} starting migration to {} (reason: {})",
                Registries.ENTITY_TYPE.getId(animal.getType()),
                target.destination(),
                target.reason());
    }

    @Override
    public void stop() {
        if (target != null) {
            double distanceSq = animal.squaredDistanceTo(
                    target.destination().getX() + 0.5,
                    target.destination().getY(),
                    target.destination().getZ() + 0.5
            );
            
            if (distanceSq < 64) {
                Trophic.LOGGER.info("{} completed migration successfully",
                        Registries.ENTITY_TYPE.getId(animal.getType()));
            } else {
                Trophic.LOGGER.info("{} abandoned migration",
                        Registries.ENTITY_TYPE.getId(animal.getType()));
            }
        }
        
        target = null;
        animal.getNavigation().stop();
    }

    @Override
    public void tick() {
        migrationTimer++;
        
        if (target == null) {
            return;
        }
        
        // Navigate toward target
        animal.getNavigation().startMovingTo(
                target.destination().getX() + 0.5,
                target.destination().getY(),
                target.destination().getZ() + 0.5,
                speed
        );
        
        // Check if stuck
        Vec3d currentPos = new Vec3d(animal.getX(), animal.getY(), animal.getZ());
        if (currentPos.squaredDistanceTo(lastPosition) < 1.0) {
            stuckTimer++;
        } else {
            stuckTimer = 0;
        }
        lastPosition = currentPos;
        
        // Periodically recalculate path
        if (migrationTimer % CHECK_INTERVAL == 0) {
            // Recalculate if path is blocked
            if (!animal.getNavigation().isFollowingPath()) {
                // Try to find intermediate waypoint
                BlockPos intermediate = findIntermediateWaypoint();
                if (intermediate != null) {
                    animal.getNavigation().startMovingTo(
                            intermediate.getX() + 0.5,
                            intermediate.getY(),
                            intermediate.getZ() + 0.5,
                            speed
                    );
                }
            }
        }
    }

    /**
     * Finds an intermediate waypoint toward the migration target.
     */
    private BlockPos findIntermediateWaypoint() {
        if (target == null) {
            return null;
        }
        
        // Calculate direction to target
        double dx = target.destination().getX() - animal.getX();
        double dz = target.destination().getZ() - animal.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        if (distance < 16) {
            return target.destination();
        }
        
        // Try to find a reachable point in the general direction
        dx = dx / distance * 32; // 32 blocks toward target
        dz = dz / distance * 32;
        
        BlockPos candidate = new BlockPos(
                (int)(animal.getX() + dx),
                (int)animal.getY(),
                (int)(animal.getZ() + dz)
        );
        
        // Find valid ground position
        if (animal.getEntityWorld() instanceof ServerWorld world) {
            for (int dy = 5; dy >= -5; dy--) {
                BlockPos checkPos = candidate.add(0, dy, 0);
                if (world.getBlockState(checkPos).isAir() &&
                    !world.getBlockState(checkPos.down()).isAir()) {
                    return checkPos;
                }
            }
        }
        
        return null;
    }
}
