package com.trophic.mixin;

import com.trophic.behavior.goals.HuntPreyGoal;
import com.trophic.behavior.goals.PackBehaviorGoal;
import com.trophic.behavior.goals.SeasonalBreedGoal;
import com.trophic.behavior.goals.TerritoryPatrolGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add ecological behavior to wolves.
 */
@Mixin(WolfEntity.class)
public abstract class MixinWolfEntity extends TameableEntity {

    protected MixinWolfEntity(EntityType<? extends TameableEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void trophic_onInit(CallbackInfo ci) {
        WolfEntity self = (WolfEntity)(Object)this;
        
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
}
