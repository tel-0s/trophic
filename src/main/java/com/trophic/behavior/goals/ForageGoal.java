package com.trophic.behavior.goals;

import com.trophic.Trophic;
import com.trophic.behavior.EcologicalEntity;
import com.trophic.ecosystem.EcosystemManager;
import com.trophic.ecosystem.RegionEcosystem;
import com.trophic.registry.DietType;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.Set;

/**
 * AI goal for herbivores and omnivores to forage for food.
 * 
 * Herbivores will:
 * - Search for grass blocks
 * - Eat grass (converting to dirt)
 * - Gain nutrition
 * 
 * This integrates with the hunger system to drive foraging behavior.
 */
public class ForageGoal extends Goal {
    // Grass plants are preferred - they regrow and don't destroy grass blocks
    private static final Set<Block> PREFERRED_FOOD = Set.of(
            Blocks.TALL_GRASS,
            Blocks.SHORT_GRASS,
            Blocks.FERN,
            Blocks.LARGE_FERN
    );
    
    // Grass blocks are a fallback - eating them converts to dirt
    private static final Set<Block> FALLBACK_FOOD = Set.of(
            Blocks.GRASS_BLOCK
    );
    
    private static final Set<Block> EDIBLE_BLOCKS = Set.of(
            Blocks.GRASS_BLOCK,
            Blocks.TALL_GRASS,
            Blocks.SHORT_GRASS,
            Blocks.FERN,
            Blocks.LARGE_FERN
    );
    
    private static final int SEARCH_RANGE = 10;
    private static final int FORAGE_TIME = 40; // 2 seconds to eat
    private static final int NUTRITION_VALUE = 25;
    
    private final PathAwareEntity entity;
    private final double speed;
    
    private BlockPos targetPos;
    private int forageTimer;
    private int searchCooldown;

    public ForageGoal(PathAwareEntity entity, double speed) {
        this.entity = entity;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (searchCooldown > 0) {
            searchCooldown--;
            return false;
        }
        
        // Check if entity can forage (herbivore or omnivore)
        Identifier entityId = Registries.ENTITY_TYPE.getId(entity.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(entityId).orElse(null);
        
        if (species != null && species.getDiet() != null) {
            if (!species.getDiet().type().canForage()) {
                return false;
            }
        }
        
        // Only forage when hungry
        if (entity instanceof EcologicalEntity eco) {
            if (!eco.trophic_isHungry()) {
                return false;
            }
        }
        
        // Find food source
        targetPos = findFoodSource();
        if (targetPos == null) {
            searchCooldown = 100; // Wait 5 seconds before searching again
            return false;
        }
        
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (targetPos == null) {
            return false;
        }
        
        // Check if still hungry
        if (entity instanceof EcologicalEntity eco) {
            if (!eco.trophic_isHungry()) {
                return false;
            }
        }
        
        // Check if target is still valid
        BlockState state = entity.getEntityWorld().getBlockState(targetPos);
        return EDIBLE_BLOCKS.contains(state.getBlock());
    }

    @Override
    public void start() {
        forageTimer = 0;
    }

    @Override
    public void stop() {
        targetPos = null;
        forageTimer = 0;
    }

    @Override
    public void tick() {
        if (targetPos == null) {
            return;
        }
        
        double distanceSq = entity.squaredDistanceTo(
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5
        );
        
        if (distanceSq > 4.0) {
            // Move toward food
            entity.getNavigation().startMovingTo(
                    targetPos.getX() + 0.5,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5,
                    speed
            );
        } else {
            // Close enough - start eating
            entity.getNavigation().stop();
            entity.getLookControl().lookAt(
                    targetPos.getX() + 0.5,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5
            );
            
            forageTimer++;
            
            if (forageTimer >= FORAGE_TIME) {
                consumeFood();
            }
        }
    }

    private BlockPos findFoodSource() {
        World world = entity.getEntityWorld();
        BlockPos entityPos = entity.getBlockPos();
        
        // First pass: look for preferred grass plants
        BlockPos preferredPos = findFoodOfType(world, entityPos, PREFERRED_FOOD);
        if (preferredPos != null) {
            return preferredPos;
        }
        
        // Second pass: fall back to grass blocks if no plants found
        return findFoodOfType(world, entityPos, FALLBACK_FOOD);
    }
    
    private BlockPos findFoodOfType(World world, BlockPos entityPos, Set<Block> validBlocks) {
        BlockPos bestPos = null;
        double bestDistanceSq = Double.MAX_VALUE;
        
        for (int x = -SEARCH_RANGE; x <= SEARCH_RANGE; x++) {
            for (int z = -SEARCH_RANGE; z <= SEARCH_RANGE; z++) {
                for (int y = -3; y <= 3; y++) {
                    BlockPos pos = entityPos.add(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    
                    if (validBlocks.contains(state.getBlock())) {
                        double distanceSq = entityPos.getSquaredDistance(pos);
                        if (distanceSq < bestDistanceSq) {
                            // Check if we can path there
                            if (entity.getNavigation().findPathTo(pos, 1) != null) {
                                bestDistanceSq = distanceSq;
                                bestPos = pos;
                            }
                        }
                    }
                }
            }
        }
        
        return bestPos;
    }

    private void consumeFood() {
        if (targetPos == null) {
            return;
        }
        
        World world = entity.getEntityWorld();
        BlockState state = world.getBlockState(targetPos);
        
        if (!EDIBLE_BLOCKS.contains(state.getBlock())) {
            targetPos = null;
            return;
        }
        
        // Consume the food
        if (state.isOf(Blocks.GRASS_BLOCK)) {
            world.setBlockState(targetPos, Blocks.DIRT.getDefaultState());
        } else {
            world.breakBlock(targetPos, false);
        }
        
        // Feed the entity
        if (entity instanceof EcologicalEntity eco) {
            eco.trophic_feed(NUTRITION_VALUE);
        }
        
        // Track grazing in ecosystem
        if (world instanceof ServerWorld serverWorld) {
            EcosystemManager ecosystemManager = Trophic.getInstance().getEcosystemManager();
            ChunkPos chunkPos = new ChunkPos(targetPos);
            RegionEcosystem region = ecosystemManager.getOrCreateRegion(serverWorld, chunkPos);
            
            Identifier entityId = Registries.ENTITY_TYPE.getId(entity.getType());
            region.recordGrazing(entityId);
        }
        
        Trophic.LOGGER.debug("{} foraged at {}", 
                Registries.ENTITY_TYPE.getId(entity.getType()), targetPos);
        
        // Look for more food
        targetPos = null;
        forageTimer = 0;
    }
}
