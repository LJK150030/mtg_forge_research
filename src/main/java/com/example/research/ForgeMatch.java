package com.example.research;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

public class ForgeMatch {

    // Simulation mode enum
    private enum SimulationMode {
        QUICK, DETAILED, FULL, CUSTOM, DEFAULT
    }

    public ForgeMatch(String[] arg, Deck deck1, Deck deck2) {
        SimulationMode mode = parseArgs(arg);

        switch (mode) {
            case QUICK:
                runQuickMatch(deck1, deck2);
                break;
            case DETAILED:
                runDetailedMatch(deck1, deck2);
                break;
            case FULL:
                runFullMatch(deck1, deck2);
                break;
            case CUSTOM:
                runCustomMatch(deck1, deck2, arg);
                break;
            default:
                runMatch(deck1, deck2); // Default behavior
        }
    }

    // Parse command line arguments
    private static SimulationMode parseArgs(String[] arg) {
        return switch (arg[0].toLowerCase()) {
            case "quick" -> SimulationMode.QUICK;
            case "detailed" -> SimulationMode.DETAILED;
            case "full" -> SimulationMode.FULL;
            case "custom" -> SimulationMode.CUSTOM;
            default -> SimulationMode.DEFAULT;
        };
    }


    // Different match configurations
    private static void runQuickMatch(Deck deck1, Deck deck2) {
        System.out.println("\n🚀 Running Quick Match (3 turns, minimal logging)");
        // Implementation here
    }

    private static void runDetailedMatch(Deck deck1, Deck deck2) {
        System.out.println("\n🔍 Running Detailed Match (10 turns, full logging)");
        // Implementation here
    }

    private static void runFullMatch(Deck deck1, Deck deck2) {
        System.out.println("\n🏁 Running Full Match (until game over)");
        // Implementation here
    }

    private static void runCustomMatch(Deck deck1, Deck deck2, String[] arg) {
        System.out.println("\n⚙️  Running Custom Match");
        // Parse custom configuration from args
        // Implementation here
    }

    private static void runMatch(Deck deck1, Deck deck2) {
        try {
            System.out.println("👥 Creating AI players using LobbyPlayerAi pattern...");

            // Use TutorialAI which extends LobbyPlayerAi
            ForgePlayerAI aiPlayer1 = new ForgePlayerAI("Cats AI", false);  // control style
            ForgePlayerAI aiPlayer2 = new ForgePlayerAI("Vampires AI", true); // aggro style

            System.out.println("✓ Created AI players");

            RegisteredPlayer regPlayer1 = new RegisteredPlayer(deck1);
            RegisteredPlayer regPlayer2 = new RegisteredPlayer(deck2);

            regPlayer1.setPlayer(aiPlayer1);
            regPlayer2.setPlayer(aiPlayer2);

            List<RegisteredPlayer> players = Arrays.asList(regPlayer1, regPlayer2);
            System.out.println("✓ Created registered players");

            GameRules gameRules = new GameRules(GameType.Constructed);
            gameRules.setGamesPerMatch(1);
            System.out.println("✓ Created game rules");

            Match match = new Match(gameRules, players, "Tutorial Match");
            System.out.println("✓ Created match");

            Game game = match.createGame();
            System.out.println("✓ Created game");

            try {
                match.startGame(game, null);
                System.out.println("🎉 AI game started successfully!");

                // Run a few turns for demonstration
                runEnhancedSimulation(game, 100);

            } catch (Exception e) {
                System.err.println("⚠️  Game start encountered issues: " + e.getMessage());
                System.out.println("🎉 AI game created successfully");
            }

            displayGameState(game);

        } catch (Exception e) {
            System.err.println("❌ Error during AI match: " + e.getMessage());
            e.printStackTrace();
        }
    }



    private static void runEnhancedSimulation(Game game, int i) {
        // Example 1: Simulate 5 turns with full verbose logging
//        System.out.println("\n🎮 Running Enhanced Simulation - Example 1: 5 Turns with Full Logging");
//        ForgeEnhancedSimulator.SimulationConfig config1 = new ForgeEnhancedSimulator.SimulationConfig();
//        config1.mode = ForgeEnhancedSimulator.SimulationConfig.Mode.TURNS;
//        config1.turnsToSimulate = i;
//        config1.verboseLogging = true;
//        config1.logEvents = true;
//        config1.logPhaseChanges = true;
//        config1.logCombat = true;
//        config1.showHiddenZones = true;
//        config1.focusPlayerIndex = -1; // Show all players
//
//        ForgeEnhancedSimulator.MatchSimulator simulator1 =
//                new ForgeEnhancedSimulator.MatchSimulator(config1);
//        simulator1.simulate(game);

//        // Example 2: Full match with focus on player 1 (Vampires)
//        ForgeEnhancedSimulator.SimulationConfig config2 = new ForgeEnhancedSimulator.SimulationConfig();
//        config2.mode = ForgeEnhancedSimulator.SimulationConfig.Mode.FULL_MATCH;
//        config2.verboseLogging = false; // Less verbose
//        config2.logEvents = true;
//        config2.logPhaseChanges = false; // Don't log every phase
//        config2.logCombat = true;
//        config2.showHiddenZones = true;
//        config2.focusPlayerIndex = 1; // Focus on player 2 (Vampires)
//        config2.maxTurnsBeforeTimeout = 50;
//
//        ForgeEnhancedSimulator.MatchSimulator simulator2 =
//                new ForgeEnhancedSimulator.MatchSimulator(config2);
//        simulator2.simulate(game);
//
//
        // Example 3: Quick 3-turn analysis with custom output
//        System.out.println("\n🎮 Running Enhanced Simulation - Example 3: Quick Analysis");
//
//        ForgeEnhancedSimulator.SimulationConfig config3 = new ForgeEnhancedSimulator.SimulationConfig();
//        config3.mode = ForgeEnhancedSimulator.SimulationConfig.Mode.TURNS;
//        config3.turnsToSimulate = 3;
//        config3.verboseLogging = true;
//        config3.logEvents = false; // No event logging
//        config3.logPhaseChanges = true;
//        config3.showHiddenZones = false; // Don't show hands
//        config3.pauseBetweenPhases = true; // Slow down for observation
//
//        // Custom output stream for file logging
//        try (PrintStream fileOutput = new PrintStream(new FileOutputStream("match_log.txt"))) {
//            ForgeEnhancedSimulator.MatchSimulator simulator3 =
//                    new ForgeEnhancedSimulator.MatchSimulator(config3, fileOutput);
//            simulator3.simulate(game);
//            System.out.println("✓ Match log saved to match_log.txt");
//        } catch (IOException e) {
//            System.err.println("Error writing to file: " + e.getMessage());
//        }
//
        // Example 4: Verbose turn-by-turn explanation for EVERY action
        System.out.println("\n🎮 Running Enhanced Simulation - Example 4: Complete Turn-by-Turn Analysis");

        ForgeEnhancedSimulator.SimulationConfig config4 = new ForgeEnhancedSimulator.SimulationConfig();
        config4.mode = ForgeEnhancedSimulator.SimulationConfig.Mode.FULL_MATCH;
        config4.verboseLogging = true;
        config4.logEvents = true;
        config4.logPhaseChanges = true;
        config4.logCombat = true;
        config4.logManaUsage = true;
        config4.logStackOperations = true;
        config4.showHiddenZones = true;
        config4.focusPlayerIndex = -1; // Show ALL players
        config4.maxTurnsBeforeTimeout = 30;

        // Enable maximum verbosity for complete action tracking
        config4.logCardDraws = true;
        config4.logLandPlays = true;
        config4.logSpellsCast = true;
        config4.logAbilityActivations = true;
        config4.logTriggers = true;
        config4.logStateBasedActions = true;
        config4.logPriorityPasses = true;
        config4.logAIDecisions = true;
        config4.pauseBetweenActions = true;

        // Create detailed formatter for comprehensive output
        config4.detailedActionFormatter = true;
        config4.includeTimestamps = true;
        config4.includeStackTrace = true;

        // Custom output handler for ultra-verbose logging
        try (PrintStream detailedLog = new PrintStream(new FileOutputStream("detailed_match_log.txt"))) {
            ForgeEnhancedSimulator.MatchSimulator simulator4 =
                    new ForgeEnhancedSimulator.MatchSimulator(config4, detailedLog);

            // Set up event listeners for maximum detail
            simulator4.setPreActionCallback((g, action) -> {
                System.out.println("\n🔷 PRE-ACTION: " + action);
                System.out.println("   Stack Size: " + game.getStack().size());
                System.out.println("   Active Player: " + game.getPhaseHandler().getPlayerTurn().getName());
            });

            simulator4.setPostActionCallback((g, action, result) -> {
                System.out.println("🔶 POST-ACTION: " + action + " -> " + result);
                System.out.println("   Game State Changed: " + result.isStateChanged());
            });

            simulator4.simulate(game);
            System.out.println("✓ Detailed match log saved to detailed_match_log.txt");
        } catch (IOException e) {
            System.err.println("Error writing detailed log: " + e.getMessage());
        }
    }

    private static void displayGameState(Game game) {
        try {
            System.out.println("\n📊 Final Game State:");
            System.out.println("   - Players: " + game.getRegisteredPlayers().size());

            if (game.getPhaseHandler() != null) {
                System.out.println("   - Current Turn: " + game.getPhaseHandler().getTurn());
                System.out.println("   - Current Phase: " + game.getPhaseHandler().getPhase());
            }

            for (Player player : game.getPlayers()) {
                System.out.println("\n   Player: " + player.getName());
                System.out.println("   - Life: " + player.getLife());
                System.out.println("   - Hand: " + player.getCardsIn(ZoneType.Hand).size() + " cards");
                System.out.println("   - Library: " + player.getCardsIn(ZoneType.Library).size() + " cards");
                System.out.println("   - Battlefield: " + player.getCardsIn(ZoneType.Battlefield).size() + " permanents");
                System.out.println("   - Graveyard: " + player.getCardsIn(ZoneType.Graveyard).size() + " cards");
            }
        } catch (Exception e) {
            System.err.println("❌ Error displaying game state: " + e.getMessage());
        }
    }


}
