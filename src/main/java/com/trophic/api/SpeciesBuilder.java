package com.trophic.api;

import com.trophic.registry.DietType;
import com.trophic.registry.SpeciesDefinition;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for creating species definitions programmatically.
 * 
 * Example usage:
 * <pre>
 * TrophicAPI.species("mymod:my_wolf")
 *     .trophicLevel(3)
 *     .carnivore()
 *     .prey("minecraft:rabbit", 0.5, 30)
 *     .prey("minecraft:sheep", 0.3, 80)
 *     .prefersBiomes("minecraft:forest", "minecraft:taiga")
 *     .packAnimal(3, 8)
 *     .territoryRadius(64)
 *     .breedingSeason(0.15, 0.35)
 *     .register();
 * </pre>
 */
public class SpeciesBuilder {
    private final Identifier entityId;
    private int trophicLevel = 2;
    
    // Diet
    private DietType dietType = DietType.HERBIVORE;
    private final Map<Identifier, SpeciesDefinition.PreyInfo> prey = new HashMap<>();
    private int huntCooldown = 6000;
    
    // Habitat
    private final List<Identifier> preferredBiomes = new ArrayList<>();
    private double minTemperature = -20;
    private double maxTemperature = 40;
    private final List<Identifier> avoidsBiomes = new ArrayList<>();
    
    // Reproduction
    private double breedingSeasonStart = 0.0;
    private double breedingSeasonEnd = 1.0;
    private int minLitterSize = 1;
    private int maxLitterSize = 1;
    private int gestationTicks = 24000;
    private int maturityAgeTicks = 48000;
    private double foodThreshold = 0.5;
    
    // Social
    private boolean packAnimal = false;
    private int minPackSize = 1;
    private int maxPackSize = 1;
    private int territoryRadius = 32;
    private double aggression = 0.5;
    
    // Population
    private double baseDensity = 1.0;
    private double carryingCapacityPerChunk = 2.0;
    private double migrationTendency = 0.1;

    public SpeciesBuilder(Identifier entityId) {
        this.entityId = entityId;
    }

    // ========== Basic Properties ==========
    
    public SpeciesBuilder trophicLevel(int level) {
        this.trophicLevel = level;
        return this;
    }

    // ========== Diet ==========
    
    public SpeciesBuilder herbivore() {
        this.dietType = DietType.HERBIVORE;
        return this;
    }

    public SpeciesBuilder carnivore() {
        this.dietType = DietType.CARNIVORE;
        return this;
    }

    public SpeciesBuilder omnivore() {
        this.dietType = DietType.OMNIVORE;
        return this;
    }

    public SpeciesBuilder prey(String entityId, double preference, int nutritionalValue) {
        return prey(Identifier.of(entityId), preference, nutritionalValue);
    }

    public SpeciesBuilder prey(Identifier entityId, double preference, int nutritionalValue) {
        this.prey.put(entityId, new SpeciesDefinition.PreyInfo(preference, nutritionalValue));
        return this;
    }

    public SpeciesBuilder huntCooldown(int ticks) {
        this.huntCooldown = ticks;
        return this;
    }

    // ========== Habitat ==========
    
    public SpeciesBuilder prefersBiomes(String... biomes) {
        for (String biome : biomes) {
            this.preferredBiomes.add(Identifier.of(biome));
        }
        return this;
    }

    public SpeciesBuilder avoidsBiomes(String... biomes) {
        for (String biome : biomes) {
            this.avoidsBiomes.add(Identifier.of(biome));
        }
        return this;
    }

    public SpeciesBuilder temperatureRange(double min, double max) {
        this.minTemperature = min;
        this.maxTemperature = max;
        return this;
    }

    // ========== Reproduction ==========
    
    public SpeciesBuilder breedingSeason(double start, double end) {
        this.breedingSeasonStart = start;
        this.breedingSeasonEnd = end;
        return this;
    }

    public SpeciesBuilder litterSize(int min, int max) {
        this.minLitterSize = min;
        this.maxLitterSize = max;
        return this;
    }

    public SpeciesBuilder gestationPeriod(int ticks) {
        this.gestationTicks = ticks;
        return this;
    }

    public SpeciesBuilder maturityAge(int ticks) {
        this.maturityAgeTicks = ticks;
        return this;
    }

    public SpeciesBuilder foodThresholdForBreeding(double threshold) {
        this.foodThreshold = threshold;
        return this;
    }

    // ========== Social ==========
    
    public SpeciesBuilder packAnimal(int minSize, int maxSize) {
        this.packAnimal = true;
        this.minPackSize = minSize;
        this.maxPackSize = maxSize;
        return this;
    }

    public SpeciesBuilder solitary() {
        this.packAnimal = false;
        this.minPackSize = 1;
        this.maxPackSize = 1;
        return this;
    }

    public SpeciesBuilder territoryRadius(int blocks) {
        this.territoryRadius = blocks;
        return this;
    }

    public SpeciesBuilder aggression(double level) {
        this.aggression = level;
        return this;
    }

    // ========== Population ==========
    
    public SpeciesBuilder baseDensity(double density) {
        this.baseDensity = density;
        return this;
    }

    public SpeciesBuilder carryingCapacity(double perChunk) {
        this.carryingCapacityPerChunk = perChunk;
        return this;
    }

    public SpeciesBuilder migrationTendency(double tendency) {
        this.migrationTendency = tendency;
        return this;
    }

    // ========== Build & Register ==========
    
    /**
     * Builds the species definition.
     */
    public SpeciesDefinition build() {
        SpeciesDefinition.Diet diet = new SpeciesDefinition.Diet(
                dietType, prey, huntCooldown
        );
        
        SpeciesDefinition.Habitat habitat = new SpeciesDefinition.Habitat(
                preferredBiomes, minTemperature, maxTemperature, avoidsBiomes
        );
        
        SpeciesDefinition.Reproduction reproduction = new SpeciesDefinition.Reproduction(
                breedingSeasonStart, breedingSeasonEnd,
                minLitterSize, maxLitterSize,
                gestationTicks, maturityAgeTicks, foodThreshold
        );
        
        SpeciesDefinition.Social social = new SpeciesDefinition.Social(
                packAnimal, minPackSize, maxPackSize, territoryRadius, aggression
        );
        
        SpeciesDefinition.Population population = new SpeciesDefinition.Population(
                baseDensity, carryingCapacityPerChunk, migrationTendency
        );
        
        return new SpeciesDefinition(
                entityId, trophicLevel, diet, habitat, reproduction, social, population
        );
    }

    /**
     * Builds and registers the species definition.
     */
    public void register() {
        TrophicAPI.registerSpecies(build());
    }
}
