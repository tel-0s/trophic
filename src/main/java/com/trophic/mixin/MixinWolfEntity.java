package com.trophic.mixin;

import com.trophic.behavior.goals.HuntPreyGoal;
import com.trophic.behavior.goals.PackBehaviorGoal;
import com.trophic.behavior.goals.SeasonalBreedGoal;
import com.trophic.behavior.goals.TerritoryPatrolGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.*;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Mixin to add ecological behavior to wolves.
 * 
 * Also disables vanilla automatic hunting so wolves only hunt when hungry
 * through the Trophic system.
 */
@Mixin(WolfEntity.class)
public abstract class MixinWolfEntity extends TameableEntity {
    
    // Entity types that vanilla wolves target but Trophic should control
    private static final Set<Class<?>> TROPHIC_CONTROLLED_PREY = Set.of(
            RabbitEntity.class,
            SheepEntity.class,
            FoxEntity.class
    );

    protected MixinWolfEntity(EntityType<? extends TameableEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void trophic_onInit(CallbackInfo ci) {
        WolfEntity self = (WolfEntity)(Object)this;
        
        // Remove vanilla prey targeting goals - Trophic controls hunting now
        removeVanillaPreyTargeting();
        
        // Only add ecological goals when not tamed
        if (!self.isTamed()) {
            // Hunting behavior (priority 3 - after combat, highest ecological priority)
            this.goalSelector.add(3, new HuntPreyGoal(self, 1.2, 0.8, 32.0));
            
            // Seasonal breeding (priority 5)
            this.goalSelector.add(5, new SeasonalBreedGoal(self, 1.0));
            
            // Territory patrol (priority 6)
            this.goalSelector.add(6, new TerritoryPatrolGoal(self, 0.8));
            
            // Pack behavior (priority 7 - lowest, only when well-fed and far from pack)
            this.goalSelector.add(7, new PackBehaviorGoal(self, 1.0));
        }
    }
    
    /**
     * Removes vanilla targeting goals for prey animals.
     * This allows Trophic's hunger-based hunting to control when wolves hunt.
     * 
     * We remove all ActiveTargetGoal instances that target passive animals (AnimalEntity subclasses).
     * Hostile mob targeting (like skeletons) is preserved.
     */
    private void removeVanillaPreyTargeting() {
        // Collect goals to remove (can't modify while iterating)
        List<Goal> toRemove = new ArrayList<>();
        
        this.targetSelector.getGoals().forEach(prioritizedGoal -> {
            Goal goal = prioritizedGoal.getGoal();
            if (goal instanceof ActiveTargetGoal<?> targetGoal) {
                try {
                    // Access the targetClass field via reflection
                    var field = ActiveTargetGoal.class.getDeclaredField("targetClass");
                    field.setAccessible(true);
                    Class<?> targetClass = (Class<?>) field.get(targetGoal);
                    
                    // Remove if targeting passive animals (Trophic controls this)
                    if (AnimalEntity.class.isAssignableFrom(targetClass)) {
                        toRemove.add(goal);
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    // Field might have different name in this version - try fallback
                    // Check common prey types by goal class name
                    String className = goal.getClass().getName();
                    if (className.contains("Rabbit") || 
                        className.contains("Sheep") || 
                        className.contains("Fox")) {
                        toRemove.add(goal);
                    }
                }
            }
        });
        
        // Remove the collected goals
        toRemove.forEach(this.targetSelector::remove);
    }
}
