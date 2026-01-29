package com.trophic.mixin;

import com.trophic.Trophic;
import com.trophic.behavior.EcologicalEntity;
import com.trophic.config.TrophicConfig;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add ecological behavior to all AnimalEntity instances.
 */
@Mixin(AnimalEntity.class)
public abstract class MixinAnimalEntity extends PassiveEntity implements EcologicalEntity {
    
    // Ecological state
    @Unique
    private double trophic_hunger = 1.0;
    
    @Unique
    private long trophic_lastMealTick = 0;
    
    @Unique
    private int trophic_huntCooldown = 0;
    
    @Unique
    private boolean trophic_initialized = false;
    
    @Unique
    private BlockPos trophic_homePos = null;

    protected MixinAnimalEntity(EntityType<? extends PassiveEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void trophic_tick(CallbackInfo ci) {
        if (this.getEntityWorld().isClient()) {
            return;
        }
        
        // Initialize on first tick
        if (!trophic_initialized) {
            trophic_initialize();
            trophic_initialized = true;
        }
        
        // Update hunger
        trophic_updateHunger();
        
        // Update hunt cooldown
        if (trophic_huntCooldown > 0) {
            trophic_huntCooldown--;
        }
    }

    @Unique
    private void trophic_initialize() {
        // Initialize with random hunger
        trophic_hunger = 0.5 + this.random.nextDouble() * 0.5;
        trophic_lastMealTick = this.getEntityWorld().getTime();
        
        // Set home position to spawn location
        if (trophic_homePos == null) {
            trophic_homePos = this.getBlockPos();
        }
    }

    @Unique
    private void trophic_updateHunger() {
        Identifier entityId = Registries.ENTITY_TYPE.getId(this.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(entityId).orElse(null);
        
        if (species == null) {
            return;
        }
        
        TrophicConfig.HungerConfig config = TrophicConfig.get().hunger;
        
        // Decrease hunger over time (metabolic cost)
        double hungerDecayRate = config.decayRatePerSecond / 20.0;
        
        // Trophic level scaling
        hungerDecayRate *= 1.0 + (species.getTrophicLevel() - 1) * config.trophicLevelScaling;
        
        trophic_hunger = Math.max(0.0, trophic_hunger - hungerDecayRate);
        
        // Apply starvation damage if hunger is critically low
        if (trophic_hunger <= config.starvationDamageThreshold && 
            this.getEntityWorld().getTime() % config.starvationDamageInterval == 0) {
            if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
                ((AnimalEntity)(Object)this).damage(serverWorld, this.getDamageSources().starve(), 1.0f);
            }
        }
    }

    @Inject(method = "writeCustomData", at = @At("TAIL"))
    private void trophic_writeData(WriteView view, CallbackInfo ci) {
        WriteView trophicView = view.get("trophic");
        trophicView.putDouble("hunger", trophic_hunger);
        trophicView.putLong("lastMeal", trophic_lastMealTick);
        trophicView.putInt("huntCooldown", trophic_huntCooldown);
        
        // Save home position
        if (trophic_homePos != null) {
            trophicView.putInt("homeX", trophic_homePos.getX());
            trophicView.putInt("homeY", trophic_homePos.getY());
            trophicView.putInt("homeZ", trophic_homePos.getZ());
        }
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void trophic_readData(ReadView view, CallbackInfo ci) {
        view.getOptionalReadView("trophic").ifPresent(trophicView -> {
            trophic_hunger = trophicView.getDouble("hunger", 1.0);
            trophic_lastMealTick = trophicView.getLong("lastMeal", 0L);
            trophic_huntCooldown = trophicView.getInt("huntCooldown", 0);
            
            // Load home position
            int homeX = trophicView.getInt("homeX", Integer.MIN_VALUE);
            if (homeX != Integer.MIN_VALUE) {
                int homeY = trophicView.getInt("homeY", 64);
                int homeZ = trophicView.getInt("homeZ", 0);
                trophic_homePos = new BlockPos(homeX, homeY, homeZ);
            }
            
            trophic_initialized = true;
        });
    }

    // EcologicalEntity interface implementation
    
    @Override
    public double trophic_getHunger() {
        return trophic_hunger;
    }

    @Override
    public void trophic_setHunger(double hunger) {
        this.trophic_hunger = Math.max(0.0, Math.min(1.0, hunger));
    }

    @Override
    public void trophic_feed(int nutritionalValue) {
        // Convert nutritional value to hunger restoration
        // Base: 100 nutrition = full restoration
        double restoration = nutritionalValue / 100.0;
        trophic_hunger = Math.min(1.0, trophic_hunger + restoration);
        trophic_lastMealTick = this.getEntityWorld().getTime();
    }

    @Override
    public boolean trophic_isHungry() {
        return trophic_hunger < TrophicConfig.get().hunger.hungryThreshold;
    }

    @Override
    public boolean trophic_isStarving() {
        return trophic_hunger < TrophicConfig.get().hunger.starvingThreshold;
    }

    @Override
    public int trophic_getHuntCooldown() {
        return trophic_huntCooldown;
    }

    @Override
    public void trophic_setHuntCooldown(int ticks) {
        this.trophic_huntCooldown = ticks;
    }

    @Override
    public boolean trophic_canHunt() {
        return trophic_huntCooldown <= 0 && trophic_isHungry();
    }

    @Override
    public long trophic_getLastMealTick() {
        return trophic_lastMealTick;
    }
    
    @Override
    public BlockPos trophic_getHomePos() {
        return trophic_homePos;
    }
    
    @Override
    public void trophic_setHomePos(BlockPos pos) {
        this.trophic_homePos = pos;
    }
    
    @Override
    public double trophic_getHomeRange() {
        // Could be species-specific in the future
        return TrophicConfig.get().homeRange.defaultRange;
    }
}
