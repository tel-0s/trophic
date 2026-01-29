package com.trophic.mixin;

import com.trophic.Trophic;
import com.trophic.behavior.EcologicalEntity;
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
    }

    @Unique
    private void trophic_updateHunger() {
        Identifier entityId = Registries.ENTITY_TYPE.getId(this.getType());
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        SpeciesDefinition species = registry.getSpecies(entityId).orElse(null);
        
        if (species == null) {
            return;
        }
        
        // Decrease hunger over time (metabolic cost)
        // Base rate: lose 0.0008 hunger per second (20 ticks)
        // This means ~8-10 real minutes to get hungry after eating
        // Animals should need to eat 2-3 times per in-game day
        double hungerDecayRate = 0.0008 / 20.0;
        
        // Minimal trophic scaling - predators shouldn't starve faster
        hungerDecayRate *= 1.0 + (species.getTrophicLevel() - 1) * 0.02;
        
        trophic_hunger = Math.max(0.0, trophic_hunger - hungerDecayRate);
        
        // Apply starvation damage if hunger is critically low
        // Only at 5% hunger, and damage every 10 seconds to give time to find food
        if (trophic_hunger <= 0.05 && this.getEntityWorld().getTime() % 200 == 0) {
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
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void trophic_readData(ReadView view, CallbackInfo ci) {
        view.getOptionalReadView("trophic").ifPresent(trophicView -> {
            trophic_hunger = trophicView.getDouble("hunger", 1.0);
            trophic_lastMealTick = trophicView.getLong("lastMeal", 0L);
            trophic_huntCooldown = trophicView.getInt("huntCooldown", 0);
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
        // Hungry below 60% - triggers hunting/foraging behavior
        return trophic_hunger < 0.6;
    }

    @Override
    public boolean trophic_isStarving() {
        return trophic_hunger < 0.2;
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
}
