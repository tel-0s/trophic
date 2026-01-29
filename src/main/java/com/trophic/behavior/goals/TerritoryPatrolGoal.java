package com.trophic.behavior.goals;

import com.trophic.Trophic;
import com.trophic.behavior.ai.TerritoryManager;
import com.trophic.behavior.ai.TerritoryManager.Territory;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.Optional;

/**
 * AI goal for territorial animals to patrol and defend their territory.
 */
public class TerritoryPatrolGoal extends Goal {
    private final AnimalEntity animal;
    private final double patrolSpeed;
    
    private Territory territory;
    private Vec3d patrolTarget;
    private int patrolTimer;
    private int patrolPointIndex;
    
    private static final int PATROL_POINT_COUNT = 8;
    private static final int PATROL_DURATION = 100;

    public TerritoryPatrolGoal(AnimalEntity animal, double patrolSpeed) {
        this.animal = animal;
        this.patrolSpeed = patrolSpeed;
        
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        // Check if territorial species
        Identifier entityId = Registries.ENTITY_TYPE.getId(animal.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(entityId).orElse(null);
        
        if (species == null || species.getSocial() == null) {
            return false;
        }
        
        if (species.getSocial().territoryRadius() <= 0) {
            return false;
        }
        
        // Ensure we have a territory
        Optional<Territory> existing = TerritoryManager.getTerritory(animal.getUuid());
        if (existing.isEmpty()) {
            territory = TerritoryManager.claimTerritory(animal);
        } else {
            territory = existing.get();
        }
        
        return territory != null && animal.getRandom().nextFloat() < 0.05; // 5% chance to start patrol
    }

    @Override
    public boolean shouldContinue() {
        return patrolTimer < PATROL_DURATION * PATROL_POINT_COUNT;
    }

    @Override
    public void start() {
        patrolTimer = 0;
        patrolPointIndex = 0;
        selectNextPatrolPoint();
    }

    @Override
    public void stop() {
        patrolTarget = null;
        animal.getNavigation().stop();
    }

    @Override
    public void tick() {
        patrolTimer++;
        
        if (patrolTarget == null) {
            return;
        }
        
        // Move toward patrol point
        double distanceSq = animal.squaredDistanceTo(patrolTarget);
        
        if (distanceSq < 4.0 || patrolTimer % PATROL_DURATION == 0) {
            // Reached point or time to move on
            patrolPointIndex++;
            selectNextPatrolPoint();
        }
        
        if (patrolTarget != null) {
            animal.getNavigation().startMovingTo(
                    patrolTarget.x, patrolTarget.y, patrolTarget.z, patrolSpeed
            );
        }
    }

    /**
     * Selects the next patrol point around the territory perimeter.
     */
    private void selectNextPatrolPoint() {
        if (territory == null) {
            patrolTarget = null;
            return;
        }
        
        // Calculate point on territory perimeter
        double angle = (2 * Math.PI * patrolPointIndex) / PATROL_POINT_COUNT;
        double radius = territory.radius() * 0.8; // Patrol slightly inside boundary
        
        double x = territory.center().getX() + Math.cos(angle) * radius;
        double z = territory.center().getZ() + Math.sin(angle) * radius;
        
        // Find ground level
        BlockPos groundPos = findGround(new BlockPos((int) x, animal.getBlockY(), (int) z));
        
        if (groundPos != null) {
            patrolTarget = Vec3d.ofCenter(groundPos);
        } else {
            // Can't reach this point, try the center
            patrolTarget = Vec3d.ofCenter(territory.center());
        }
    }

    /**
     * Finds a valid ground position near the given position.
     */
    private BlockPos findGround(BlockPos pos) {
        for (int dy = 5; dy >= -5; dy--) {
            BlockPos checkPos = pos.add(0, dy, 0);
            if (animal.getEntityWorld().getBlockState(checkPos).isAir() &&
                !animal.getEntityWorld().getBlockState(checkPos.down()).isAir()) {
                return checkPos;
            }
        }
        return null;
    }
}
