package com.trophic;

import com.trophic.command.TrophicCommands;
import com.trophic.config.TrophicConfig;
import com.trophic.ecosystem.EcosystemManager;
import com.trophic.population.PopulationTracker;
import com.trophic.population.SpawnController;
import com.trophic.registry.SpeciesRegistry;
import com.trophic.simulation.FoodChainSimulator;
import com.trophic.simulation.SeasonManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Trophic mod.
 * 
 * Trophic overhauls animal behaviour and interactions, introducing a complex
 * ecological system with predator-prey relationships, food chains, and
 * ecosystem dynamics.
 */
public class Trophic implements ModInitializer {
    public static final String MOD_ID = "trophic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Trophic instance;

    private SpeciesRegistry speciesRegistry;
    private EcosystemManager ecosystemManager;
    private PopulationTracker populationTracker;
    private SeasonManager seasonManager;
    private SpawnController spawnController;
    private FoodChainSimulator foodChainSimulator;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("Initializing Trophic - Ecological Overhaul");

        // Load configuration
        TrophicConfig.load();
        LOGGER.info("Loaded Trophic configuration");

        // Initialize core systems
        speciesRegistry = new SpeciesRegistry();
        ecosystemManager = new EcosystemManager();
        populationTracker = new PopulationTracker();
        seasonManager = new SeasonManager();
        foodChainSimulator = new FoodChainSimulator();
        spawnController = new SpawnController(populationTracker, speciesRegistry);

        // Load species definitions from datapacks
        speciesRegistry.loadDefaultSpecies();

        // Register commands
        TrophicCommands.register();

        // Register event handlers
        registerEvents();

        LOGGER.info("Trophic initialized successfully with {} species definitions", 
                    speciesRegistry.getSpeciesCount());
    }

    private void registerEvents() {
        // Server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Trophic: Server started, initializing ecosystem state");
            ecosystemManager.initializeForServer(server);
            populationTracker.initializeForServer(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Trophic: Server stopping, saving ecosystem state");
            ecosystemManager.saveAll();
            populationTracker.saveAll();
        });

        // Server tick events for simulation
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            seasonManager.tick(server);
            ecosystemManager.tick(server);
            populationTracker.tick(server);
            foodChainSimulator.tick(server);
        });

        // Register spawn control
        spawnController.register();
    }

    public static Trophic getInstance() {
        return instance;
    }

    public SpeciesRegistry getSpeciesRegistry() {
        return speciesRegistry;
    }

    public EcosystemManager getEcosystemManager() {
        return ecosystemManager;
    }

    public PopulationTracker getPopulationTracker() {
        return populationTracker;
    }

    public SeasonManager getSeasonManager() {
        return seasonManager;
    }

    public SpawnController getSpawnController() {
        return spawnController;
    }

    public FoodChainSimulator getFoodChainSimulator() {
        return foodChainSimulator;
    }
}
