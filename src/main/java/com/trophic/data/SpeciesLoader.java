package com.trophic.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.trophic.Trophic;
import com.trophic.registry.DietType;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads species definitions from JSON files.
 */
public class SpeciesLoader {
    private static final Gson GSON = new GsonBuilder().create();
    
    // Default species files bundled with the mod
    private static final String[] DEFAULT_SPECIES = {
        "wolf", "fox", "rabbit", "sheep", "cow", "pig", "chicken", "goat", 
        "ocelot", "polar_bear"
    };

    /**
     * Loads the default species definitions bundled with the mod.
     */
    public static void loadDefaultSpecies(SpeciesRegistry registry) {
        for (String speciesName : DEFAULT_SPECIES) {
            try {
                String path = "/data/trophic/species/" + speciesName + ".json";
                InputStream stream = SpeciesLoader.class.getResourceAsStream(path);
                
                if (stream != null) {
                    SpeciesDefinition definition = loadFromStream(stream);
                    if (definition != null) {
                        registry.register(definition);
                    }
                } else {
                    Trophic.LOGGER.warn("Default species file not found: {}", path);
                }
            } catch (Exception e) {
                Trophic.LOGGER.error("Failed to load species {}: {}", speciesName, e.getMessage());
            }
        }
    }

    /**
     * Loads a species definition from an input stream.
     */
    public static SpeciesDefinition loadFromStream(InputStream stream) {
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            return parseSpeciesDefinition(json);
        } catch (Exception e) {
            Trophic.LOGGER.error("Failed to parse species JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parses a species definition from a JSON object.
     */
    public static SpeciesDefinition parseSpeciesDefinition(JsonObject json) {
        Identifier entityId = Identifier.of(json.get("entity").getAsString());
        int trophicLevel = json.get("trophic_level").getAsInt();

        // Parse diet
        SpeciesDefinition.Diet diet = null;
        if (json.has("diet")) {
            JsonObject dietJson = json.getAsJsonObject("diet");
            DietType dietType = DietType.fromString(dietJson.get("type").getAsString());
            
            Map<Identifier, SpeciesDefinition.PreyInfo> preyMap = new HashMap<>();
            if (dietJson.has("prey")) {
                JsonObject preyJson = dietJson.getAsJsonObject("prey");
                for (Map.Entry<String, JsonElement> entry : preyJson.entrySet()) {
                    Identifier preyId = Identifier.of(entry.getKey());
                    JsonObject preyInfo = entry.getValue().getAsJsonObject();
                    preyMap.put(preyId, new SpeciesDefinition.PreyInfo(
                            preyInfo.get("preference").getAsDouble(),
                            preyInfo.get("nutritional_value").getAsInt()
                    ));
                }
            }
            
            int huntCooldown = dietJson.has("hunt_cooldown") ? 
                    dietJson.get("hunt_cooldown").getAsInt() : 6000;
            
            diet = new SpeciesDefinition.Diet(dietType, preyMap, huntCooldown);
        }

        // Parse habitat
        SpeciesDefinition.Habitat habitat = null;
        if (json.has("habitat")) {
            JsonObject habitatJson = json.getAsJsonObject("habitat");
            
            List<Identifier> preferredBiomes = new ArrayList<>();
            if (habitatJson.has("preferred_biomes")) {
                for (JsonElement element : habitatJson.getAsJsonArray("preferred_biomes")) {
                    preferredBiomes.add(Identifier.of(element.getAsString()));
                }
            }
            
            double minTemp = -20, maxTemp = 40;
            if (habitatJson.has("temperature_tolerance")) {
                JsonObject tempJson = habitatJson.getAsJsonObject("temperature_tolerance");
                minTemp = tempJson.get("min").getAsDouble();
                maxTemp = tempJson.get("max").getAsDouble();
            }
            
            List<Identifier> avoidsBiomes = new ArrayList<>();
            if (habitatJson.has("avoids")) {
                for (JsonElement element : habitatJson.getAsJsonArray("avoids")) {
                    avoidsBiomes.add(Identifier.of(element.getAsString()));
                }
            }
            
            habitat = new SpeciesDefinition.Habitat(preferredBiomes, minTemp, maxTemp, avoidsBiomes);
        }

        // Parse reproduction
        SpeciesDefinition.Reproduction reproduction = null;
        if (json.has("reproduction")) {
            JsonObject repJson = json.getAsJsonObject("reproduction");
            
            double seasonStart = 0.0, seasonEnd = 1.0;
            if (repJson.has("breeding_season")) {
                JsonObject seasonJson = repJson.getAsJsonObject("breeding_season");
                seasonStart = seasonJson.get("start").getAsDouble();
                seasonEnd = seasonJson.get("end").getAsDouble();
            }
            
            int minLitter = 1, maxLitter = 1;
            if (repJson.has("litter_size")) {
                JsonObject litterJson = repJson.getAsJsonObject("litter_size");
                minLitter = litterJson.get("min").getAsInt();
                maxLitter = litterJson.get("max").getAsInt();
            }
            
            int gestation = repJson.has("gestation_ticks") ? repJson.get("gestation_ticks").getAsInt() : 24000;
            int maturity = repJson.has("maturity_age_ticks") ? repJson.get("maturity_age_ticks").getAsInt() : 48000;
            double foodThreshold = repJson.has("food_threshold") ? repJson.get("food_threshold").getAsDouble() : 0.5;
            
            reproduction = new SpeciesDefinition.Reproduction(
                    seasonStart, seasonEnd, minLitter, maxLitter, gestation, maturity, foodThreshold
            );
        }

        // Parse social
        SpeciesDefinition.Social social = null;
        if (json.has("social")) {
            JsonObject socialJson = json.getAsJsonObject("social");
            
            boolean packAnimal = socialJson.has("pack_animal") && socialJson.get("pack_animal").getAsBoolean();
            
            int minPack = 1, maxPack = 1;
            if (socialJson.has("pack_size")) {
                JsonObject packJson = socialJson.getAsJsonObject("pack_size");
                minPack = packJson.get("min").getAsInt();
                maxPack = packJson.get("max").getAsInt();
            }
            
            int territoryRadius = socialJson.has("territory_radius") ? socialJson.get("territory_radius").getAsInt() : 32;
            double aggression = socialJson.has("aggression") ? socialJson.get("aggression").getAsDouble() : 0.5;
            
            social = new SpeciesDefinition.Social(packAnimal, minPack, maxPack, territoryRadius, aggression);
        }

        // Parse population
        SpeciesDefinition.Population population = null;
        if (json.has("population")) {
            JsonObject popJson = json.getAsJsonObject("population");
            
            double baseDensity = popJson.has("base_density") ? popJson.get("base_density").getAsDouble() : 1.0;
            double carryingCapacity = popJson.has("carrying_capacity_per_chunk") ? 
                    popJson.get("carrying_capacity_per_chunk").getAsDouble() : 2.0;
            double migrationTendency = popJson.has("migration_tendency") ? 
                    popJson.get("migration_tendency").getAsDouble() : 0.1;
            
            population = new SpeciesDefinition.Population(baseDensity, carryingCapacity, migrationTendency);
        }

        return new SpeciesDefinition(entityId, trophicLevel, diet, habitat, reproduction, social, population);
    }
}
