package com.example.research;

import APF.ForgeBuilders;
import APF.ForgeFactory;
import APF.KnowledgeBase;
import APF.MatchInstanceSeeder;
import APF.magic_commons.*;
import forge.ai.PlayerControllerAi;
import forge.deck.*;
import forge.game.*;
import forge.game.phase.*;
import forge.game.player.*;
import forge.game.zone.ZoneType;

import java.io.*;
import java.util.*;

import static APF.magic_commons.PLAYERS;

/**
 * Updated ForgeMatch Implementation with GameStateTracker integration
 * This shows the key components needed to track and display each phase
 */
public class ForgeMatch {

    // Configuration for simulation
    public static class SimulationConfig {
        public int turnsToSimulate = 100;
        public boolean verboseLogging = true;
        public boolean logPhaseChanges = true;
        public boolean pauseBetweenPhases = false;
        public boolean trackGameState = true;  // New option for state tracking
        public boolean logStateToFile = true;  // Log state transitions to file
        public String stateLogPath = "res/log/game_state_log.txt";

        // Hidden state logging control
        public boolean logPlayer1HiddenStates = true;
        public boolean logPlayer2HiddenStates = false;
    }

    private final SimulationConfig config;
    private final PrintStream output;
    private Game game;
    private GameStateTracker stateTracker;  // State tracker instance
    private boolean simulationRunning = true;
    private KnowledgeBase knowledgeBase;

    public ForgeMatch(SimulationConfig config, PrintStream output) {
        this.config = config;
        this.output = output;

        knowledgeBase = KnowledgeBase.getInstance();
    }

    /**
     * Initialize and run the match
     */
    public void runMatch(Deck deck1, Deck deck2) {
        try {
            // Step 1: Create AI Players
            output.println("👥 Creating AI players...");

            ForgePlayerAI  aiPlayer1 = new ForgePlayerAI(PLAYERS.get(1));
            aiPlayer1.setAiProfile("Default");

            ForgePlayerAI  aiPlayer2 = new ForgePlayerAI(PLAYERS.get(2));
            aiPlayer2.setAiProfile("Default");

            aiPlayer1.setAllowCheatShuffle(false);
            aiPlayer2.setAllowCheatShuffle(false);

            // Step 2: Create RegisteredPlayers with decks
            RegisteredPlayer regPlayer1 = new RegisteredPlayer(deck1);
            RegisteredPlayer regPlayer2 = new RegisteredPlayer(deck2);

            regPlayer1.setPlayer(aiPlayer1);
            regPlayer2.setPlayer(aiPlayer2);

            List<RegisteredPlayer> players = Arrays.asList(regPlayer1, regPlayer2);

            // Step 3: Create Game Rules and Match
            GameRules gameRules = new GameRules(GameType.Constructed);
            gameRules.setGamesPerMatch(1);

            Match match = new Match(gameRules, players, "Simulated Match");

            // Step 4: Create Game
            game = match.createGame();
            output.println("✓ Game created successfully");

            // Step 5: Initialize GameStateTracker if enabled
            if (config.trackGameState) {
                // Create a map of player names to their hidden state logging preferences
                Map<String, Boolean> hiddenStateLogging = new HashMap<>();
                hiddenStateLogging.put("Player 1", config.logPlayer1HiddenStates);
                hiddenStateLogging.put("Player 2", config.logPlayer2HiddenStates);

                stateTracker = new GameStateTracker(game, output, config.verboseLogging,
                        config.logStateToFile, config.stateLogPath, hiddenStateLogging);

                output.println("✓ Game state tracking enabled");
                output.println("  • Player 1 hidden states: " +
                        (config.logPlayer1HiddenStates ? "VISIBLE" : "HIDDEN"));
                output.println("  • Player 2 hidden states: " +
                        (config.logPlayer2HiddenStates ? "VISIBLE" : "HIDDEN"));

                if (config.logStateToFile) {
                    output.println("✓ State transitions will be logged to: " + config.stateLogPath);
                }
            }

            knowledgeBase.subscribeToGame(game);

            APF.ForgeFactory.CardInstanceFactory cardFactory = new APF.ForgeFactory.CardInstanceFactory();
            ForgeFactory.PlayerInstanceFactory playerFactory = new APF.ForgeFactory.PlayerInstanceFactory();

            knowledgeBase.setCardFactory(cardFactory);
            knowledgeBase.setPlayerFactory(playerFactory);

            APF.MatchInstanceSeeder seeder = new APF.MatchInstanceSeeder(knowledgeBase, cardFactory);

            String matchId = "M-" + game.getTimestamp();
            match.startGame(game, () -> {
                try {
                    seeder.seedAll(game, matchId);
                    knowledgeBase.exportAllInstancesToNeo4j();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            output.println("✓ Game started");

            // Step 8: Run simulation
            simulateGame();

            // Step 9: Close state tracker if it was created
            if (stateTracker != null) {
                stateTracker.close();
            }

        } catch (Exception e) {
            output.println("❌ Error during match: " + e.getMessage());
            e.printStackTrace(output);
            // Ensure cleanup happens even on error
            if (stateTracker != null) {
                stateTracker.close();
            }
        }
    }

    /**
     * Main simulation loop
     */
    private void simulateGame() {
        output.println("\n🎮 Starting Game Simulation");
        output.println("═══════════════════════════════════════════════════════════════════\n");

        simulationRunning = true;
        int turnCount = 0;
        int actionCount = 0;
        final int MAX_ACTIONS = 10000; // Safety limit

        while (simulationRunning && !game.isGameOver() && actionCount < MAX_ACTIONS) {
            try {
                // Let the game process one action
                boolean actionTaken = advanceGameState();

                if (actionTaken) {
                    actionCount++;
                }

                // Check for turn completion
                PhaseHandler ph = game.getPhaseHandler();
                if (ph.getPhase() == PhaseType.CLEANUP) {
                    turnCount++;
                    if (turnCount >= config.turnsToSimulate) {
                        output.println("\n📊 Reached turn limit (" + config.turnsToSimulate + " turns)");
                        break;
                    }
                }

                // Small delay to prevent CPU spinning
                Thread.sleep(1);

            } catch (Exception e) {
                output.println("❌ Error during simulation: " + e.getMessage());
                e.printStackTrace(output);
                break;
            }
        }

        // Print final results
        printGameResults();
    }

    /**
     * Advance game state by letting AI make decisions
     */
    private boolean advanceGameState() {
        PhaseHandler phaseHandler = game.getPhaseHandler();
        Player priorityPlayer = phaseHandler.getPriorityPlayer();

        if (priorityPlayer != null && priorityPlayer.getController() instanceof PlayerControllerAi) {
            // Let AI controller handle the priority
            PlayerControllerAi aiController = (PlayerControllerAi) priorityPlayer.getController();

            // The AI will automatically make decisions when it has priority
            // This happens through the game's internal mechanics

            // Process state-based actions
            game.getAction().checkStateEffects(true);

            return true;
        }

        return false;
    }

    /**
     * Print final game results
     */
    private void printGameResults() {
        output.println("\n╔════════════════════════════════════════════════════════════════════╗");
        output.println("║                         GAME RESULTS                                ║");
        output.println("╚════════════════════════════════════════════════════════════════════╝");

        // Determine winner
        Player winner = null;
        for (Player p : game.getPlayers()) {
            if (!p.hasLost()) {
                winner = p;
                break;
            }
        }

        if (winner != null) {
            output.println("🏆 Winner: " + winner.getName());
        } else {
            output.println("🤝 Draw!");
        }

        // Player final states
        output.println("\n📊 Final Player States:");
        for (Player p : game.getPlayers()) {
            output.println(String.format("  %s: Life=%d, Hand=%d, Library=%d",
                    p.getName(),
                    p.getLife(),
                    p.getCardsIn(ZoneType.Hand).size(),
                    p.getCardsIn(ZoneType.Library).size()));
        }

        // If state tracking is enabled, show summary
        if (config.trackGameState && stateTracker != null) {
            output.println("\n📊 State Tracking Complete");
            if (config.logStateToFile) {
                output.println("  • Full state transition log saved to: " + config.stateLogPath);
            }
        }


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Shutdown Neo4J connection
            try {
                Neo4jService.shutdown();
                System.out.println("Neo4J connection closed");
            } catch (Exception e) {
                System.err.println("Error closing Neo4J connection: " + e.getMessage());
            }
        }));
    }
}