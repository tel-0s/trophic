package com.trophic.mixin;

import com.trophic.behavior.goals.FleePredatorGoal;
import com.trophic.behavior.goals.ForageGoal;
import com.trophic.behavior.goals.MigrationGoal;
import com.trophic.behavior.goals.PackBehaviorGoal;
import com.trophic.behavior.goals.SeasonalBreedGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add ecological behavior to sheep.
 */
@Mixin(SheepEntity.class)
public abstract class MixinSheepEntity extends AnimalEntity {

    protected MixinSheepEntity(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void trophic_onInit(CallbackInfo ci) {
        SheepEntity self = (SheepEntity)(Object)this;
        
        // Flee behavior (highest priority)
        this.goalSelector.add(1, new FleePredatorGoal(self, 1.05, 12.0));
        
        // Seasonal breeding
        this.goalSelector.add(4, new SeasonalBreedGoal(self, 1.0));
        
        // Foraging behavior
        this.goalSelector.add(5, new ForageGoal(self, 1.0));
        
        // Herd behavior (lower priority - only when safe and well-fed)
        this.goalSelector.add(6, new PackBehaviorGoal(self, 1.0));
        
        // Migration behavior (lowest priority, seasonal)
        this.goalSelector.add(7, new MigrationGoal(self, 1.0));
    }
}
