package com.example.research;

import forge.deck.*;
import forge.game.*;
import forge.game.player.*;
import forge.game.zone.ZoneType;

/**
 * Forge Test with Integrated AI Implementation
 *
 * This implementation uses the proper LobbyPlayerAi pattern from Forge
 * with self-contained draft AI logic integrated directly.
 */
public class ForgeTest {

    public static void main(String[] args) {
        System.out.println("🎮 Starting Forge Match Test with Enhanced Simulation");

        try {
            // Step 1: Initialize Forge environment
            System.out.println("🔧 Initializing Forge environment...");
            ForgeApp app = new ForgeApp();

            // Step 2: Create tutorial decks
            System.out.println("🏗️  Creating tutorial decks...");
            ForgeDeck deck = new ForgeDeck();
            Deck catsDeck = deck.createCatsDeck();
            Deck vampiresDeck = deck.createVampiresDeck();

            // Step 3: Parse command line arguments for simulation mode
            ForgeMatch match = new ForgeMatch(args, catsDeck, vampiresDeck);

        } catch (Exception e) {
            System.err.println("❌ Error setting up match: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // Method to run a single turn with detailed logging
    public static void runSingleTurn(Game game, boolean verbose) {
        ForgeEnhancedSimulator.SimulationConfig config = new ForgeEnhancedSimulator.SimulationConfig();
        config.mode = ForgeEnhancedSimulator.SimulationConfig.Mode.TURNS;
        config.turnsToSimulate = 1;
        config.verboseLogging = verbose;
        config.logEvents = true;
        config.logPhaseChanges = true;

        ForgeEnhancedSimulator.MatchSimulator simulator =
                new ForgeEnhancedSimulator.MatchSimulator(config);
        simulator.simulate(game);
    }

    // Method to analyze specific player's hand and board state
    public static void analyzePlayerState(Game game, int playerIndex) {
        ForgeEnhancedSimulator.SimulationConfig config = new ForgeEnhancedSimulator.SimulationConfig();
        config.focusPlayerIndex = playerIndex;
        config.showHiddenZones = true;

        ForgeEnhancedSimulator.GameStatePrinter printer =
                new ForgeEnhancedSimulator.GameStatePrinter(config, System.out);
        printer.printFullGameState(game);
    }

    // Method to track specific events
    public static void trackCombatEvents(Game game, int turns) {
        ForgeEnhancedSimulator.SimulationConfig config = new ForgeEnhancedSimulator.SimulationConfig();
        config.mode = ForgeEnhancedSimulator.SimulationConfig.Mode.TURNS;
        config.turnsToSimulate = turns;
        config.verboseLogging = false;
        config.logEvents = true;
        config.logPhaseChanges = false;
        config.logCombat = true; // Only log combat
        config.logManaUsage = false;
        config.logStackOperations = false;

        ForgeEnhancedSimulator.MatchSimulator simulator =
                new ForgeEnhancedSimulator.MatchSimulator(config);
        simulator.simulate(game);
    }

    private static void simulateTurns(Game game, int numTurns) {
        System.out.println("\n📋 Simulating " + numTurns + " turns...");

        for (int i = 0; i < numTurns && !game.isGameOver(); i++) {
            System.out.println("\n--- Turn " + (i + 1) + " ---");

            try {
                // Advance through phases
                if (game.getPhaseHandler() != null) {
                    // This would need actual phase advancement logic
                    System.out.println("Current phase: " + game.getPhaseHandler().getPhase());
                }

                // Display basic game state each turn
                for (Player p : game.getPlayers()) {
                    System.out.println(p.getName() + " - Life: " + p.getLife() +
                            ", Hand: " + p.getCardsIn(ZoneType.Hand).size() +
                            ", Battlefield: " + p.getCardsIn(ZoneType.Battlefield).size());
                }

            } catch (Exception e) {
                System.err.println("Error simulating turn: " + e.getMessage());
                break;
            }
        }
    }
}