package com.trophic.mixin;

import com.trophic.behavior.goals.FleePredatorGoal;
import com.trophic.behavior.goals.ForageGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add ecological behavior to pigs.
 */
@Mixin(PigEntity.class)
public abstract class MixinPigEntity extends AnimalEntity {

    protected MixinPigEntity(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void trophic_onInit(CallbackInfo ci) {
        PigEntity self = (PigEntity)(Object)this;
        
        // Add flee behavior
        this.goalSelector.add(2, new FleePredatorGoal(self, 1.0, 10.0));
        
        // Add foraging behavior - pigs are omnivores
        this.goalSelector.add(5, new ForageGoal(self, 1.0));
    }
}
