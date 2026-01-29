package com.trophic.mixin;

import com.trophic.behavior.goals.FleePredatorGoal;
import com.trophic.behavior.goals.ForageGoal;
import com.trophic.behavior.goals.SeasonalBreedGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add ecological behavior to rabbits.
 */
@Mixin(RabbitEntity.class)
public abstract class MixinRabbitEntity extends AnimalEntity {

    protected MixinRabbitEntity(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void trophic_onInit(CallbackInfo ci) {
        RabbitEntity self = (RabbitEntity)(Object)this;
        
        // Flee behavior (high priority - rabbits are skittish)
        // Rabbits are the fastest prey but wolves can still catch up
        this.goalSelector.add(1, new FleePredatorGoal(self, 1.15, 14.0));
        
        // Seasonal breeding (rabbits breed prolifically)
        this.goalSelector.add(4, new SeasonalBreedGoal(self, 1.2));
        
        // Foraging behavior
        this.goalSelector.add(5, new ForageGoal(self, 1.0));
    }
}
