package com.trophic.registry;

import net.minecraft.util.Identifier;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Defines the ecological properties of a species.
 * Loaded from JSON datapacks or registered via the API.
 */
public class SpeciesDefinition {
    private final Identifier entityId;
    private final int trophicLevel;
    private final Diet diet;
    private final Habitat habitat;
    private final Reproduction reproduction;
    private final Social social;
    private final Population population;

    public SpeciesDefinition(
            Identifier entityId,
            int trophicLevel,
            Diet diet,
            Habitat habitat,
            Reproduction reproduction,
            Social social,
            Population population) {
        this.entityId = entityId;
        this.trophicLevel = trophicLevel;
        this.diet = diet;
        this.habitat = habitat;
        this.reproduction = reproduction;
        this.social = social;
        this.population = population;
    }

    public Identifier getEntityId() {
        return entityId;
    }

    public int getTrophicLevel() {
        return trophicLevel;
    }

    public Diet getDiet() {
        return diet;
    }

    public Habitat getHabitat() {
        return habitat;
    }

    public Reproduction getReproduction() {
        return reproduction;
    }

    public Social getSocial() {
        return social;
    }

    public Population getPopulation() {
        return population;
    }

    /**
     * Dietary information for a species.
     */
    public record Diet(
            DietType type,
            Map<Identifier, PreyInfo> prey,
            int huntCooldownTicks
    ) {
        /**
         * Gets the prey preference for a given entity.
         * @param entityId the entity to check
         * @return the prey info if this species hunts the given entity
         */
        public Optional<PreyInfo> getPreyInfo(Identifier entityId) {
            return Optional.ofNullable(prey.get(entityId));
        }

        /**
         * @return true if this species can hunt the given entity
         */
        public boolean canHunt(Identifier entityId) {
            return prey.containsKey(entityId);
        }
    }

    /**
     * Information about a prey species.
     */
    public record PreyInfo(
            double preference,
            int nutritionalValue
    ) {}

    /**
     * Habitat preferences for a species.
     */
    public record Habitat(
            List<Identifier> preferredBiomes,
            double minTemperature,
            double maxTemperature,
            List<Identifier> avoidsBiomes
    ) {
        /**
         * Checks if a biome is suitable for this species.
         * @param biomeId the biome to check
         * @return true if the biome is preferred or neutral
         */
        public boolean isSuitableBiome(Identifier biomeId) {
            if (avoidsBiomes.contains(biomeId)) {
                return false;
            }
            return preferredBiomes.isEmpty() || preferredBiomes.contains(biomeId);
        }

        /**
         * Calculates habitat suitability score (0.0 to 1.0).
         * @param biomeId the biome
         * @param temperature the temperature
         * @return suitability score
         */
        public double calculateSuitability(Identifier biomeId, double temperature) {
            if (avoidsBiomes.contains(biomeId)) {
                return 0.0;
            }

            double biomeFactor = preferredBiomes.isEmpty() ? 0.5 :
                    preferredBiomes.contains(biomeId) ? 1.0 : 0.3;

            double tempFactor;
            if (temperature < minTemperature) {
                tempFactor = Math.max(0, 1.0 - (minTemperature - temperature) / 20.0);
            } else if (temperature > maxTemperature) {
                tempFactor = Math.max(0, 1.0 - (temperature - maxTemperature) / 20.0);
            } else {
                tempFactor = 1.0;
            }

            return biomeFactor * tempFactor;
        }
    }

    /**
     * Reproduction parameters for a species.
     */
    public record Reproduction(
            double breedingSeasonStart,
            double breedingSeasonEnd,
            int minLitterSize,
            int maxLitterSize,
            int gestationTicks,
            int maturityAgeTicks,
            double foodThreshold
    ) {
        /**
         * Checks if it's currently breeding season.
         * @param yearProgress the current progress through the year (0.0 to 1.0)
         * @return true if within breeding season
         */
        public boolean isBreedingSeason(double yearProgress) {
            if (breedingSeasonStart <= breedingSeasonEnd) {
                return yearProgress >= breedingSeasonStart && yearProgress <= breedingSeasonEnd;
            } else {
                // Breeding season spans year boundary
                return yearProgress >= breedingSeasonStart || yearProgress <= breedingSeasonEnd;
            }
        }
    }

    /**
     * Social behavior parameters.
     */
    public record Social(
            boolean packAnimal,
            int minPackSize,
            int maxPackSize,
            int territoryRadius,
            double aggression
    ) {
        /**
         * @return true if this species forms social groups
         */
        public boolean isSocial() {
            return packAnimal && maxPackSize > 1;
        }
    }

    /**
     * Population dynamics parameters.
     */
    public record Population(
            double baseDensity,
            double carryingCapacityPerChunk,
            double migrationTendency
    ) {}

    /**
     * Builder for creating SpeciesDefinition instances.
     */
    public static class Builder {
        private Identifier entityId;
        private int trophicLevel = 2;
        private Diet diet;
        private Habitat habitat;
        private Reproduction reproduction;
        private Social social;
        private Population population;

        public Builder(Identifier entityId) {
            this.entityId = entityId;
        }

        public Builder trophicLevel(int level) {
            this.trophicLevel = level;
            return this;
        }

        public Builder diet(Diet diet) {
            this.diet = diet;
            return this;
        }

        public Builder habitat(Habitat habitat) {
            this.habitat = habitat;
            return this;
        }

        public Builder reproduction(Reproduction reproduction) {
            this.reproduction = reproduction;
            return this;
        }

        public Builder social(Social social) {
            this.social = social;
            return this;
        }

        public Builder population(Population population) {
            this.population = population;
            return this;
        }

        public SpeciesDefinition build() {
            if (entityId == null) {
                throw new IllegalStateException("Entity ID is required");
            }
            return new SpeciesDefinition(
                    entityId,
                    trophicLevel,
                    diet,
                    habitat,
                    reproduction,
                    social,
                    population
            );
        }
    }
}
