package com.trophic.mixin;

import com.trophic.behavior.goals.FleePredatorGoal;
import com.trophic.behavior.goals.ForageGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add ecological behavior to cows.
 */
@Mixin(CowEntity.class)
public abstract class MixinCowEntity extends AnimalEntity {

    protected MixinCowEntity(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void trophic_onInit(CallbackInfo ci) {
        CowEntity self = (CowEntity)(Object)this;
        
        // Add flee behavior - cows are slow, easy prey for wolves
        this.goalSelector.add(2, new FleePredatorGoal(self, 0.95, 10.0));
        
        // Add foraging behavior
        this.goalSelector.add(6, new ForageGoal(self, 1.0));
    }
}
