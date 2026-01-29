package com.trophic.mixin;

import com.trophic.Trophic;
import com.trophic.ecosystem.EcosystemManager;
import com.trophic.ecosystem.RegionEcosystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to track entity deaths for ecosystem dynamics.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void trophic_onDeath(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        
        if (self.getEntityWorld().isClient()) {
            return;
        }
        
        if (!(self instanceof AnimalEntity animal)) {
            return;
        }
        
        // Track death in population system
        Trophic.getInstance().getPopulationTracker().onEntityDeath(animal);
        
        // Track predation in ecosystem
        if (damageSource.getAttacker() instanceof AnimalEntity predator) {
            trackPredation(predator, animal);
        }
    }

    private void trackPredation(AnimalEntity predator, AnimalEntity prey) {
        if (!(prey.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        
        Identifier predatorId = Registries.ENTITY_TYPE.getId(predator.getType());
        Identifier preyId = Registries.ENTITY_TYPE.getId(prey.getType());
        
        // Record kill in the ecosystem
        EcosystemManager ecosystemManager = Trophic.getInstance().getEcosystemManager();
        ChunkPos chunkPos = new ChunkPos(prey.getBlockPos());
        RegionEcosystem region = ecosystemManager.getOrCreateRegion(serverWorld, chunkPos);
        
        region.recordKill(predatorId, preyId);
        
        Trophic.LOGGER.debug("{} killed {} at region ({}, {})", 
                predatorId, preyId, region.getRegionX(), region.getRegionZ());
    }
}
