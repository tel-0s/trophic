package com.trophic.mixin;

import com.trophic.behavior.goals.HuntPreyGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add ecological hunting behavior to foxes.
 */
@Mixin(FoxEntity.class)
public abstract class MixinFoxEntity extends AnimalEntity {

    protected MixinFoxEntity(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void trophic_onInit(CallbackInfo ci) {
        FoxEntity self = (FoxEntity)(Object)this;
        
        // Add hunting goal - foxes are solo hunters
        this.goalSelector.add(4, new HuntPreyGoal(self, 1.25, 0.9, 24.0));
    }
}
