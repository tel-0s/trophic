package com.trophic.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.trophic.Trophic;
import com.trophic.behavior.EcologicalEntity;
import com.trophic.config.TrophicConfig;
import com.trophic.registry.SpeciesDefinition;
import com.trophic.registry.SpeciesRegistry;
import com.trophic.simulation.SeasonManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Debug commands for the Trophic mod.
 * 
 * Commands:
 * - /trophic reload - Reload config from disk
 * - /trophic list [radius] - List animal populations in area
 * - /trophic info <entity> - Get detailed info about an animal
 */
public class TrophicCommands {
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }
    
    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("trophic")
                .requires(ServerCommandSource::isExecutedByPlayer) // Any player can use (or use isOp() for ops only)
                .then(literal("reload")
                    .executes(TrophicCommands::executeReload))
                .then(literal("list")
                    .executes(context -> executeList(context, 64)) // Default radius
                    .then(argument("radius", IntegerArgumentType.integer(1, 256))
                        .executes(context -> executeList(context, IntegerArgumentType.getInteger(context, "radius")))))
                .then(literal("info")
                    .then(argument("target", EntityArgumentType.entity())
                        .executes(TrophicCommands::executeInfo)))
                .then(literal("season")
                    .executes(TrophicCommands::executeSeason))
                .then(literal("feed")
                    .then(argument("target", EntityArgumentType.entity())
                        .executes(context -> executeFeed(context, 100))
                        .then(argument("amount", IntegerArgumentType.integer(1, 100))
                            .executes(context -> executeFeed(context, IntegerArgumentType.getInteger(context, "amount"))))))
                .then(literal("starve")
                    .then(argument("target", EntityArgumentType.entity())
                        .executes(TrophicCommands::executeStarve)))
        );
    }
    
    /**
     * /trophic reload - Reload configuration from disk
     */
    private static int executeReload(CommandContext<ServerCommandSource> context) {
        try {
            TrophicConfig.reload();
            context.getSource().sendFeedback(
                () -> Text.literal("[Trophic] Configuration reloaded successfully!")
                    .formatted(Formatting.GREEN),
                true
            );
            Trophic.LOGGER.info("Configuration reloaded by {}", 
                context.getSource().getName());
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(
                Text.literal("[Trophic] Failed to reload config: " + e.getMessage())
                    .formatted(Formatting.RED)
            );
            Trophic.LOGGER.error("Failed to reload config", e);
            return 0;
        }
    }
    
    /**
     * /trophic list [radius] - List animal populations in area
     */
    private static int executeList(CommandContext<ServerCommandSource> context, int radius) {
        ServerCommandSource source = context.getSource();
        
        if (source.getEntity() == null) {
            source.sendError(Text.literal("[Trophic] This command must be run by a player or entity"));
            return 0;
        }
        
        Entity executor = source.getEntity();
        Box searchBox = executor.getBoundingBox().expand(radius);
        
        // Count animals by type
        Map<Identifier, List<AnimalEntity>> animalsByType = new HashMap<>();
        
        source.getWorld().getEntitiesByClass(AnimalEntity.class, searchBox, entity -> true)
            .forEach(animal -> {
                Identifier id = Registries.ENTITY_TYPE.getId(animal.getType());
                animalsByType.computeIfAbsent(id, k -> new ArrayList<>()).add(animal);
            });
        
        if (animalsByType.isEmpty()) {
            source.sendFeedback(
                () -> Text.literal("[Trophic] No animals found within " + radius + " blocks")
                    .formatted(Formatting.YELLOW),
                false
            );
            return 1;
        }
        
        // Build report
        source.sendFeedback(
            () -> Text.literal("=== Animal Population (" + radius + " block radius) ===")
                .formatted(Formatting.GOLD, Formatting.BOLD),
            false
        );
        
        int totalCount = 0;
        SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
        
        // Sort by count (descending)
        List<Map.Entry<Identifier, List<AnimalEntity>>> sorted = new ArrayList<>(animalsByType.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));
        
        for (Map.Entry<Identifier, List<AnimalEntity>> entry : sorted) {
            Identifier id = entry.getKey();
            List<AnimalEntity> animals = entry.getValue();
            int count = animals.size();
            totalCount += count;
            
            // Calculate average hunger
            double avgHunger = animals.stream()
                .filter(a -> a instanceof EcologicalEntity)
                .mapToDouble(a -> ((EcologicalEntity) a).trophic_getHunger())
                .average()
                .orElse(1.0);
            
            // Count hungry/starving
            long hungryCount = animals.stream()
                .filter(a -> a instanceof EcologicalEntity)
                .filter(a -> ((EcologicalEntity) a).trophic_isHungry())
                .count();
            
            long starvingCount = animals.stream()
                .filter(a -> a instanceof EcologicalEntity)
                .filter(a -> ((EcologicalEntity) a).trophic_isStarving())
                .count();
            
            // Get species info
            SpeciesDefinition species = registry.getSpecies(id).orElse(null);
            String dietType = species != null && species.getDiet() != null 
                ? species.getDiet().type().name().toLowerCase() 
                : "unknown";
            
            // Format color based on diet
            Formatting color = switch (dietType) {
                case "carnivore" -> Formatting.RED;
                case "herbivore" -> Formatting.GREEN;
                case "omnivore" -> Formatting.GOLD;
                default -> Formatting.WHITE;
            };
            
            String hungerStatus = "";
            if (starvingCount > 0) {
                hungerStatus = String.format(" [%d starving!]", starvingCount);
            } else if (hungryCount > 0) {
                hungerStatus = String.format(" [%d hungry]", hungryCount);
            }
            
            final String statusFinal = hungerStatus;
            final int countFinal = count;
            final double avgHungerFinal = avgHunger;
            
            source.sendFeedback(
                () -> Text.literal("  " + id.getPath() + ": ")
                    .formatted(color)
                    .append(Text.literal(String.valueOf(countFinal)).formatted(Formatting.WHITE))
                    .append(Text.literal(String.format(" (avg hunger: %.0f%%)", avgHungerFinal * 100))
                        .formatted(Formatting.GRAY))
                    .append(Text.literal(statusFinal)
                        .formatted(starvingCount > 0 ? Formatting.RED : Formatting.YELLOW)),
                false
            );
        }
        
        final int total = totalCount;
        source.sendFeedback(
            () -> Text.literal("Total: " + total + " animals")
                .formatted(Formatting.AQUA),
            false
        );
        
        return 1;
    }
    
    /**
     * /trophic info <entity> - Get detailed info about an animal
     */
    private static int executeInfo(CommandContext<ServerCommandSource> context) {
        try {
            Entity target = EntityArgumentType.getEntity(context, "target");
            
            if (!(target instanceof AnimalEntity animal)) {
                context.getSource().sendError(
                    Text.literal("[Trophic] Target must be an animal")
                );
                return 0;
            }
            
            Identifier id = Registries.ENTITY_TYPE.getId(animal.getType());
            SpeciesRegistry registry = Trophic.getInstance().getSpeciesRegistry();
            SpeciesDefinition species = registry.getSpecies(id).orElse(null);
            
            // Header
            context.getSource().sendFeedback(
                () -> Text.literal("=== " + id.getPath().toUpperCase() + " Info ===")
                    .formatted(Formatting.GOLD, Formatting.BOLD),
                false
            );
            
            // Position
            context.getSource().sendFeedback(
                () -> Text.literal("Position: ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(String.format("%.1f, %.1f, %.1f", 
                        animal.getX(), animal.getY(), animal.getZ()))
                        .formatted(Formatting.WHITE)),
                false
            );
            
            // Health
            context.getSource().sendFeedback(
                () -> Text.literal("Health: ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(String.format("%.1f / %.1f", 
                        animal.getHealth(), animal.getMaxHealth()))
                        .formatted(Formatting.RED)),
                false
            );
            
            // Age
            String ageStatus = animal.isBaby() ? "Baby" : "Adult";
            int breedingAge = animal.getBreedingAge();
            String breedingStatus = breedingAge > 0 
                ? String.format(" (breeding cooldown: %ds)", breedingAge / 20)
                : breedingAge < 0 
                    ? String.format(" (grows up in: %ds)", -breedingAge / 20)
                    : " (can breed)";
            
            context.getSource().sendFeedback(
                () -> Text.literal("Age: ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(ageStatus + breedingStatus)
                        .formatted(Formatting.WHITE)),
                false
            );
            
            // Ecological data
            if (animal instanceof EcologicalEntity eco) {
                double hunger = eco.trophic_getHunger();
                Formatting hungerColor = hunger > 0.6 ? Formatting.GREEN 
                    : hunger > 0.2 ? Formatting.YELLOW 
                    : Formatting.RED;
                
                context.getSource().sendFeedback(
                    () -> Text.literal("Hunger: ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(String.format("%.0f%%", hunger * 100))
                            .formatted(hungerColor))
                        .append(Text.literal(eco.trophic_isStarving() ? " [STARVING]" 
                            : eco.trophic_isHungry() ? " [hungry]" : "")
                            .formatted(eco.trophic_isStarving() ? Formatting.RED : Formatting.YELLOW)),
                    false
                );
                
                context.getSource().sendFeedback(
                    () -> Text.literal("Can Hunt: ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(String.valueOf(eco.trophic_canHunt()))
                            .formatted(eco.trophic_canHunt() ? Formatting.GREEN : Formatting.RED)),
                    false
                );
                
                // Home position
                if (eco.trophic_getHomePos() != null) {
                    var homePos = eco.trophic_getHomePos();
                    double distFromHome = Math.sqrt(animal.squaredDistanceTo(
                        homePos.getX(), homePos.getY(), homePos.getZ()));
                    
                    context.getSource().sendFeedback(
                        () -> Text.literal("Home: ")
                            .formatted(Formatting.GRAY)
                            .append(Text.literal(String.format("%d, %d, %d (%.1f blocks away, range: %.0f)",
                                homePos.getX(), homePos.getY(), homePos.getZ(),
                                distFromHome, eco.trophic_getHomeRange()))
                                .formatted(distFromHome > eco.trophic_getHomeRange() 
                                    ? Formatting.RED : Formatting.WHITE)),
                        false
                    );
                }
            }
            
            // Species data
            if (species != null) {
                context.getSource().sendFeedback(
                    () -> Text.literal("--- Species Data ---")
                        .formatted(Formatting.AQUA),
                    false
                );
                
                if (species.getDiet() != null) {
                    context.getSource().sendFeedback(
                        () -> Text.literal("Diet: ")
                            .formatted(Formatting.GRAY)
                            .append(Text.literal(species.getDiet().type().name())
                                .formatted(Formatting.WHITE)),
                        false
                    );
                }
                
                context.getSource().sendFeedback(
                    () -> Text.literal("Trophic Level: ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(String.valueOf(species.getTrophicLevel()))
                            .formatted(Formatting.WHITE)),
                    false
                );
                
                if (species.getSocial() != null) {
                    context.getSource().sendFeedback(
                        () -> Text.literal("Social: ")
                            .formatted(Formatting.GRAY)
                            .append(Text.literal(species.getSocial().isSocial() ? "Yes" : "No")
                                .formatted(Formatting.WHITE))
                            .append(species.getSocial().territoryRadius() > 0 
                                ? Text.literal(" (territory: " + species.getSocial().territoryRadius() + " blocks)")
                                    .formatted(Formatting.GRAY)
                                : Text.empty()),
                        false
                    );
                }
                
                // Predators and prey
                Set<Identifier> predators = registry.getPredatorsOf(id);
                Set<Identifier> prey = registry.getPreyOf(id);
                
                if (!predators.isEmpty()) {
                    String predatorList = String.join(", ", 
                        predators.stream().map(Identifier::getPath).toList());
                    context.getSource().sendFeedback(
                        () -> Text.literal("Predators: ")
                            .formatted(Formatting.GRAY)
                            .append(Text.literal(predatorList)
                                .formatted(Formatting.RED)),
                        false
                    );
                }
                
                if (!prey.isEmpty()) {
                    String preyList = String.join(", ", 
                        prey.stream().map(Identifier::getPath).toList());
                    context.getSource().sendFeedback(
                        () -> Text.literal("Prey: ")
                            .formatted(Formatting.GRAY)
                            .append(Text.literal(preyList)
                                .formatted(Formatting.GREEN)),
                        false
                    );
                }
            }
            
            return 1;
            
        } catch (Exception e) {
            context.getSource().sendError(
                Text.literal("[Trophic] Error: " + e.getMessage())
            );
            return 0;
        }
    }
    
    /**
     * /trophic season - Show current season info
     */
    private static int executeSeason(CommandContext<ServerCommandSource> context) {
        SeasonManager seasonManager = Trophic.getInstance().getSeasonManager();
        
        context.getSource().sendFeedback(
            () -> Text.literal("=== Season Info ===")
                .formatted(Formatting.GOLD, Formatting.BOLD),
            false
        );
        
        context.getSource().sendFeedback(
            () -> Text.literal("Current Season: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(seasonManager.getCurrentSeason().getDisplayName())
                    .formatted(Formatting.WHITE)),
            false
        );
        
        // Calculate day of year (0-7 for 8-day year)
        long worldTime = seasonManager.getWorldTime();
        int dayOfYear = (int) ((worldTime % SeasonManager.YEAR_LENGTH_TICKS) / 24000L);
        
        context.getSource().sendFeedback(
            () -> Text.literal("Day of Year: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(String.valueOf(dayOfYear + 1) + "/8")
                    .formatted(Formatting.WHITE)),
            false
        );
        
        context.getSource().sendFeedback(
            () -> Text.literal("Year Progress: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(String.format("%.1f%%", seasonManager.getYearProgress() * 100))
                    .formatted(Formatting.WHITE)),
            false
        );
        
        context.getSource().sendFeedback(
            () -> Text.literal("Spawn Rate Modifier: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(String.format("%.0f%%", seasonManager.getSpawnRateModifier() * 100))
                    .formatted(Formatting.WHITE)),
            false
        );
        
        context.getSource().sendFeedback(
            () -> Text.literal("Temperature: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(String.format("%.0f%%", seasonManager.getTemperatureModifier() * 100))
                    .formatted(Formatting.WHITE)),
            false
        );
        
        context.getSource().sendFeedback(
            () -> Text.literal("Migration Pressure: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(String.format("%.0f%%", seasonManager.getMigrationPressure() * 100))
                    .formatted(Formatting.WHITE)),
            false
        );
        
        return 1;
    }
    
    /**
     * /trophic feed <entity> [amount] - Feed an animal
     */
    private static int executeFeed(CommandContext<ServerCommandSource> context, int amount) {
        try {
            Entity target = EntityArgumentType.getEntity(context, "target");
            
            if (!(target instanceof EcologicalEntity eco)) {
                context.getSource().sendError(
                    Text.literal("[Trophic] Target must be a Trophic-enabled animal")
                );
                return 0;
            }
            
            eco.trophic_feed(amount);
            
            Identifier id = Registries.ENTITY_TYPE.getId(target.getType());
            context.getSource().sendFeedback(
                () -> Text.literal("[Trophic] Fed " + id.getPath() + " +" + amount + " nutrition (now at " + 
                    String.format("%.0f%%", eco.trophic_getHunger() * 100) + ")")
                    .formatted(Formatting.GREEN),
                true
            );
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("[Trophic] Error: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * /trophic starve <entity> - Set animal hunger to 0
     */
    private static int executeStarve(CommandContext<ServerCommandSource> context) {
        try {
            Entity target = EntityArgumentType.getEntity(context, "target");
            
            if (!(target instanceof EcologicalEntity eco)) {
                context.getSource().sendError(
                    Text.literal("[Trophic] Target must be a Trophic-enabled animal")
                );
                return 0;
            }
            
            eco.trophic_setHunger(0.0);
            
            Identifier id = Registries.ENTITY_TYPE.getId(target.getType());
            context.getSource().sendFeedback(
                () -> Text.literal("[Trophic] Set " + id.getPath() + " hunger to 0%")
                    .formatted(Formatting.RED),
                true
            );
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("[Trophic] Error: " + e.getMessage()));
            return 0;
        }
    }
}
