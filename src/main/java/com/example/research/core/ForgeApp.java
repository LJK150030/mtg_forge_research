package com.example.research.core;

import com.example.research.mtg_commons;
import com.example.research.util.ForgeInitializer;
import com.example.research.render.ConsoleRenderer;
import com.example.research.simulation.SimulationEngine;
import com.example.research.simulation.SimulationConfig;

import forge.*;
import java.io.File;

/**
 * ForgeApp - Main application controller for MTG Forge environment
 *
 * Responsibilities:
 * - Initialize and manage Forge environment
 * - Manage application lifecycle
 * - Control game instance and simulation
 */
public class ForgeApp {

    // Core components
    private MTGGame game;
    private SimulationEngine simulator;
    private ConsoleRenderer renderer;
    private ForgeInitializer forgeInit;

    // Application state
    private boolean initialized = false;
    private boolean running = false;
    private long lastUpdateTime;

    // Configuration
    private final String cardDataPath;
    private final String resourcePath;

    public ForgeApp() {
        this(mtg_commons.DEFAULT_CARD_DATA_PATH, mtg_commons.DEFAULT_RESOURCE_PATH);
    }

    public ForgeApp(String cardDataPath, String resourcePath) {
        this.cardDataPath = cardDataPath;
        this.resourcePath = resourcePath;
        this.forgeInit = new ForgeInitializer(cardDataPath, resourcePath);
        this.renderer = new ConsoleRenderer();
    }

    /**
     * Initialize the Forge application environment
     */
    public void initialize() {
        if (initialized) {
            System.out.println("⚠️  ForgeApp already initialized");
            return;
        }

        System.out.println("🎮 Initializing Forge Application...");

        try {
            // Phase 1: Initialize Forge environment
            System.out.println("🔧 Phase 1: Setting up Forge environment...");
            forgeInit.initializeEnvironment();

            // Phase 2: Create game instance
            System.out.println("🎯 Phase 2: Creating game instance...");
            game = new MTGGame(this);

            // Phase 3: Initialize simulation engine
            System.out.println("🔬 Phase 3: Setting up simulation engine...");
            SimulationConfig defaultConfig = new SimulationConfig();
            defaultConfig.verboseLogging = mtg_commons.DEFAULT_VERBOSE_LOGGING;
            simulator = new SimulationEngine(defaultConfig);

            initialized = true;
            lastUpdateTime = System.currentTimeMillis();

            System.out.println("✅ ForgeApp initialized successfully!");

        } catch (Exception e) {
            System.err.println("❌ Failed to initialize ForgeApp: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("ForgeApp initialization failed", e);
        }
    }

    /**
     * Deinitialize and clean up resources
     */
    public void deinitialize() {
        if (!initialized) {
            return;
        }

        System.out.println("🛑 Shutting down Forge Application...");

        try {
            // Stop any running games
            if (game != null && game.isActive()) {
                game.deinitialize();
            }

            // Clean up simulation engine
            if (simulator != null) {
                simulator.shutdown();
            }

            // Clean up Forge resources
            forgeInit.cleanup();

            initialized = false;
            running = false;

            System.out.println("✅ ForgeApp shut down successfully");

        } catch (Exception e) {
            System.err.println("⚠️  Error during shutdown: " + e.getMessage());
        }
    }

    /**
     * Main update loop
     */
    public void update(float deltaTime) {
        if (!initialized || !running) {
            return;
        }

        try {
            // Update game logic
            if (game != null && game.isActive()) {
                game.update(deltaTime);

                // Update simulation if enabled
                if (simulator != null && simulator.isEnabled()) {
                    simulator.update(game.getForgeGame(), deltaTime);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error in update loop: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Render current state
     */
    public void render() {
        if (!initialized) {
            return;
        }

        try {
            // Clear console (platform-specific)
            renderer.clear();

            // Render application header
            renderer.renderHeader("MTG Forge - " + (running ? "Running" : "Idle"));

            // Render game state
            if (game != null && game.isActive()) {
                game.render(renderer);
            } else {
                renderer.renderMessage("No active game");
            }

            // Render simulation info if active
            if (simulator != null && simulator.isEnabled()) {
                renderer.renderSimulationInfo(simulator.getStats());
            }

        } catch (Exception e) {
            System.err.println("❌ Error in render: " + e.getMessage());
        }
    }

    /**
     * Start a new game with the given configuration
     */
    public void startGame(GameConfig config) {
        if (!initialized) {
            throw new IllegalStateException("ForgeApp not initialized");
        }

        System.out.println("🎮 Starting new game...");

        try {
            // Initialize game with config
            game.initialize(config);

            // Configure simulation if requested
            if (config.getSimulationMode() != SimulationConfig.Mode.NONE) {
                simulator.configure(config.getSimulationConfig());
                simulator.enable();
            }

            running = true;

            System.out.println("✅ Game started successfully!");

        } catch (Exception e) {
            System.err.println("❌ Failed to start game: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Game start failed", e);
        }
    }

    /**
     * Main application loop
     */
    public void run() {
        if (!initialized) {
            throw new IllegalStateException("ForgeApp not initialized");
        }

        System.out.println("🎮 Starting main loop...");
        running = true;

        while (running && !game.isGameOver()) {
            long currentTime = System.currentTimeMillis();
            float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
            lastUpdateTime = currentTime;

            // Update game state
            update(deltaTime);

            // Render current state
            render();

            // Frame rate limiting
            try {
                Thread.sleep(mtg_commons.FRAME_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("🏁 Main loop ended");
    }

    /**
     * Stop the application
     */
    public void stop() {
        running = false;
    }

    // Getters
    public MTGGame getGame() { return game; }
    public SimulationEngine getSimulator() { return simulator; }
    public ConsoleRenderer getRenderer() { return renderer; }
    public boolean isInitialized() { return initialized; }
    public boolean isRunning() { return running; }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        ForgeApp app = new ForgeApp();

        try {
            // Initialize application
            app.initialize();

            // Create game configuration
            GameConfig config = GameConfig.parseArgs(args);

            // Start game
            app.startGame(config);

            // Run main loop
            app.run();

        } catch (Exception e) {
            System.err.println("❌ Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up
            app.deinitialize();
        }
    }
}