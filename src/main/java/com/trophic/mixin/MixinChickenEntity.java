package com.trophic.mixin;

import com.trophic.behavior.goals.FleePredatorGoal;
import com.trophic.behavior.goals.ForageGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add ecological behavior to chickens.
 */
@Mixin(ChickenEntity.class)
public abstract class MixinChickenEntity extends AnimalEntity {

    protected MixinChickenEntity(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void trophic_onInit(CallbackInfo ci) {
        ChickenEntity self = (ChickenEntity)(Object)this;
        
        // Add flee behavior - chickens are prey for foxes and ocelots
        this.goalSelector.add(1, new FleePredatorGoal(self, 1.0, 10.0));
        
        // Add foraging behavior
        this.goalSelector.add(5, new ForageGoal(self, 1.0));
    }
}
