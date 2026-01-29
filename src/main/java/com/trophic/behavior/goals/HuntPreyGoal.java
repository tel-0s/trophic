package com.trophic.behavior.goals;

import com.trophic.Trophic;
import com.trophic.behavior.EcologicalEntity;
import com.trophic.behavior.ai.PreyScanner;
import com.trophic.ecosystem.EcosystemManager;
import com.trophic.ecosystem.RegionEcosystem;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

import java.util.EnumSet;

import com.trophic.config.TrophicConfig;
import net.minecraft.util.math.Vec3d;

/**
 * AI goal for predators to hunt prey based on hunger and prey availability.
 * 
 * The hunt has three phases:
 * 1. Searching - Looking for prey within detection range
 * 2. Stalking - Moving closer while prey is unaware (optional)
 * 3. Chasing - Pursuing prey at full speed
 * 
 * Includes target commitment to prevent oscillation between prey pockets.
 */
public class HuntPreyGoal extends Goal {
    private final PathAwareEntity predator;
    private final double searchRange;
    private final double chaseSpeed;
    private final double stalkSpeed;
    
    private LivingEntity targetPrey;
    private int huntTimer;
    private HuntPhase phase;
    
    // Target commitment - prevents oscillating between different prey groups
    private Vec3d committedDirection;
    private int commitmentTimer;
    
    public enum HuntPhase {
        SEARCHING,
        STALKING,
        CHASING
    }

    public HuntPreyGoal(PathAwareEntity predator, double chaseSpeed) {
        this(predator, chaseSpeed, 
             chaseSpeed * TrophicConfig.get().hunt.stalkSpeedMultiplier, 
             TrophicConfig.get().hunt.defaultSearchRange);
    }

    public HuntPreyGoal(PathAwareEntity predator, double chaseSpeed, double stalkSpeed, double searchRange) {
        this.predator = predator;
        this.chaseSpeed = chaseSpeed;
        this.stalkSpeed = stalkSpeed;
        this.searchRange = searchRange;
        this.phase = HuntPhase.SEARCHING;
        
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // Decrement commitment timer
        if (commitmentTimer > 0) {
            commitmentTimer--;
        } else {
            committedDirection = null;
        }
        
        // Check if predator is hungry enough to hunt
        if (predator instanceof EcologicalEntity eco) {
            if (!eco.trophic_canHunt()) {
                return false;
            }
        }
        
        // Check if this entity is a registered predator
        Identifier predatorId = Registries.ENTITY_TYPE.getId(predator.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(predatorId).orElse(null);
        
        if (species == null || species.getDiet() == null || !species.getDiet().type().canHunt()) {
            return false;
        }
        
        // Try to find prey, preferring targets in committed direction
        targetPrey = findPreyWithCommitment(species.getDiet());
        return targetPrey != null;
    }
    
    /**
     * Finds prey while considering directional commitment to prevent oscillation.
     */
    private LivingEntity findPreyWithCommitment(SpeciesDefinition.Diet diet) {
        // Get all potential prey
        var searchBox = predator.getBoundingBox().expand(searchRange);
        var potentialPrey = predator.getEntityWorld().getEntitiesByClass(
                LivingEntity.class,
                searchBox,
                entity -> PreyScanner.isValidPrey(entity, predator, diet)
        );
        
        if (potentialPrey.isEmpty()) {
            return null;
        }
        
        Vec3d predatorPos = new Vec3d(predator.getX(), predator.getY(), predator.getZ());
        
        // Score and select best prey
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        
        for (LivingEntity prey : potentialPrey) {
            double baseScore = PreyScanner.scorePreyTarget(predator, prey, diet);
            
            // Apply directional preference if we have a commitment
            if (committedDirection != null) {
                Vec3d toTarget = new Vec3d(
                        prey.getX() - predator.getX(),
                        0,
                        prey.getZ() - predator.getZ()
                ).normalize();
                
                // Dot product: 1.0 = same direction, -1.0 = opposite
                double alignment = committedDirection.dotProduct(toTarget);
                
                // Penalize targets in opposite direction
                TrophicConfig.HuntConfig huntConfig = TrophicConfig.get().hunt;
                if (alignment < 0) {
                    baseScore *= (1.0 + Math.abs(alignment) * huntConfig.oppositeDirectionPenalty);
                } else {
                    baseScore *= (1.0 - alignment * huntConfig.directionPreference);
                }
            }
            
            if (baseScore < bestScore) {
                bestScore = baseScore;
                best = prey;
            }
        }
        
        return best;
    }

    @Override
    public boolean shouldContinue() {
        if (targetPrey == null || !targetPrey.isAlive()) {
            return false;
        }
        
        if (huntTimer > TrophicConfig.get().hunt.maxHuntTime) {
            return false; // Give up after too long
        }
        
        // Continue if prey is still in range (extended during chase)
        double maxRange = phase == HuntPhase.CHASING 
                ? searchRange * TrophicConfig.get().hunt.chaseRangeMultiplier 
                : searchRange;
        return predator.squaredDistanceTo(targetPrey) < maxRange * maxRange;
    }

    @Override
    public void start() {
        huntTimer = 0;
        phase = HuntPhase.STALKING;
        
        // Set directional commitment toward this prey
        if (targetPrey != null) {
            committedDirection = new Vec3d(
                    targetPrey.getX() - predator.getX(),
                    0,
                    targetPrey.getZ() - predator.getZ()
            ).normalize();
            commitmentTimer = TrophicConfig.get().hunt.commitmentDuration;
        }
        
        Trophic.LOGGER.debug("{} started hunting {}", 
                Registries.ENTITY_TYPE.getId(predator.getType()),
                Registries.ENTITY_TYPE.getId(targetPrey.getType()));
    }

    @Override
    public void stop() {
        // Keep the directional commitment even when hunt ends
        // This prevents oscillating back to a different prey group
        targetPrey = null;
        phase = HuntPhase.SEARCHING;
        predator.getNavigation().stop();
    }

    @Override
    public void tick() {
        huntTimer++;
        
        if (targetPrey == null || !targetPrey.isAlive()) {
            return;
        }
        
        // Always look at prey
        predator.getLookControl().lookAt(targetPrey, 30.0F, 30.0F);
        
        double distanceSq = predator.squaredDistanceTo(targetPrey);
        
        switch (phase) {
            case STALKING -> tickStalking(distanceSq);
            case CHASING -> tickChasing(distanceSq);
            default -> {}
        }
    }

    private void tickStalking(double distanceSq) {
        // Move slowly toward prey
        if (huntTimer % TrophicConfig.get().hunt.recalculatePathInterval == 0) {
            predator.getNavigation().startMovingTo(targetPrey, stalkSpeed);
        }
        
        // Transition to chasing if prey notices us or we're close enough
        if (distanceSq < TrophicConfig.get().hunt.stalkToChaseDistanceSq) {
            phase = HuntPhase.CHASING;
            Trophic.LOGGER.debug("{} transitioning to chase phase", 
                    Registries.ENTITY_TYPE.getId(predator.getType()));
        }
        
        // Also transition if prey starts running (they detected us)
        if (targetPrey instanceof PathAwareEntity pathAware) {
            if (pathAware.getNavigation().isFollowingPath()) {
                phase = HuntPhase.CHASING;
            }
        }
    }

    private void tickChasing(double distanceSq) {
        // Chase at full speed
        if (huntTimer % (TrophicConfig.get().hunt.recalculatePathInterval / 2) == 0) {
            predator.getNavigation().startMovingTo(targetPrey, chaseSpeed);
        }
        
        // Check for successful catch
        if (distanceSq < TrophicConfig.get().hunt.attackDistanceSq) {
            attemptKill();
        }
    }

    private void attemptKill() {
        if (targetPrey == null || !targetPrey.isAlive()) {
            return;
        }
        
        // Attack the prey
        if (predator.getEntityWorld() instanceof ServerWorld serverWorld) {
            predator.tryAttack(serverWorld, targetPrey);
        }
        
        // If prey died, consume it
        if (!targetPrey.isAlive()) {
            onSuccessfulKill();
        }
    }

    private void onSuccessfulKill() {
        // Feed the predator
        int nutrition = PreyScanner.getNutritionalValue(predator, targetPrey);
        if (predator instanceof EcologicalEntity eco) {
            eco.trophic_feed(nutrition);
            eco.trophic_setHuntCooldown(PreyScanner.getHuntCooldown(predator));
        }
        
        // Track in ecosystem
        if (predator.getEntityWorld() instanceof ServerWorld serverWorld) {
            EcosystemManager ecosystemManager = Trophic.getInstance().getEcosystemManager();
            ChunkPos chunkPos = new ChunkPos(targetPrey.getBlockPos());
            RegionEcosystem region = ecosystemManager.getOrCreateRegion(serverWorld, chunkPos);
            
            Identifier predatorId = Registries.ENTITY_TYPE.getId(predator.getType());
            Identifier preyId = Registries.ENTITY_TYPE.getId(targetPrey.getType());
            region.recordKill(predatorId, preyId);
        }
        
        Trophic.LOGGER.debug("{} successfully killed and consumed {}", 
                Registries.ENTITY_TYPE.getId(predator.getType()),
                Registries.ENTITY_TYPE.getId(targetPrey.getType()));
        
        targetPrey = null;
    }
    
    /**
     * @return the current prey target, or null
     */
    public LivingEntity getTargetPrey() {
        return targetPrey;
    }
    
    /**
     * @return the current hunt phase
     */
    public HuntPhase getPhase() {
        return phase;
    }
}
