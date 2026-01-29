package com.trophic.mixin;

import com.trophic.behavior.goals.HuntPreyGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.OcelotEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add ecological hunting behavior to ocelots.
 */
@Mixin(OcelotEntity.class)
public abstract class MixinOcelotEntity extends AnimalEntity {

    protected MixinOcelotEntity(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void trophic_onInit(CallbackInfo ci) {
        OcelotEntity self = (OcelotEntity)(Object)this;
        
        // Add hunting goal - ocelots are ambush predators
        this.goalSelector.add(3, new HuntPreyGoal(self, 1.3, 0.6, 20.0));
    }
}
