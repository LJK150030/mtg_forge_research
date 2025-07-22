package com.example.research;

import forge.ai.PlayerControllerAi;
import forge.deck.*;
import forge.game.*;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.event.*;
import forge.game.phase.*;
import forge.game.player.*;
import forge.game.zone.ZoneType;
import forge.util.maps.MapOfLists;

import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * Updated ForgeMatch Implementation following Forge patterns
 * This shows the key components needed to track and display each phase
 */
public class ForgeMatch {

    // Configuration for simulation
    public static class SimulationConfig {
        public int turnsToSimulate = 5;
        public boolean verboseLogging = true;
        public boolean logPhaseChanges = true;
        public boolean pauseBetweenPhases = false;
        public int pauseDurationMs = 500;
    }

    private final SimulationConfig config;
    private final PrintStream output;
    private Game game;
    private boolean simulationRunning = false;

    public ForgeMatch(SimulationConfig config, PrintStream output) {
        this.config = config;
        this.output = output;
    }

    /**
     * Initialize and run the match
     */
    public void runMatch(Deck deck1, Deck deck2) {
        try {
            // Step 1: Create AI Players
            output.println("👥 Creating AI players...");

            // Use the ForgePlayerAI pattern from your codebase
            ForgePlayerAI aiPlayer1 = new ForgePlayerAI("Player 1", false);
            ForgePlayerAI aiPlayer2 = new ForgePlayerAI("Player 2", true);

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

            // Step 5: Register Event Listeners BEFORE starting
            registerEventListeners();

            // Step 6: Start the game
            match.startGame(game, null);
            output.println("✓ Game started");

            // Step 7: Run simulation
            simulateGame();

        } catch (Exception e) {
            output.println("❌ Error during match: " + e.getMessage());
            e.printStackTrace(output);
        }
    }

    /**
     * Register event listeners to track phases and game events
     */
    private void registerEventListeners() {
        // Phase tracking listener
        game.subscribeToEvents(new PhaseTracker());
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
    }

    /**
     * Phase Tracker - Logs each phase change
     */
    private class PhaseTracker implements IGameEventVisitor<Void> {
        @Override
        public Void visit(GameEventTurnPhase event) {
            if (config.logPhaseChanges) {
                PhaseHandler ph = game.getPhaseHandler();
                output.println(String.format("\n📍 Turn %d - %s's %s",
                        ph.getTurn(),
                        ph.getPlayerTurn().getName(),
                        event.phaseDesc));

                // Print current game state for this phase
                printPhaseState();

                // Pause if configured
                if (config.pauseBetweenPhases) {
                    try {
                        Thread.sleep(config.pauseDurationMs);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
            }
            return null;
        }

        private void printPhaseState() {
            // Print relevant information for the current phase
            PhaseHandler ph = game.getPhaseHandler();
            PhaseType phase = ph.getPhase();
            Player activePlayer = ph.getPlayerTurn();

            // Show different information based on phase
            switch (phase) {
                case UNTAP:
                    output.println("  • Untapping permanents...");
                    int tappedCount = 0;
                    for (Card c : activePlayer.getCardsIn(ZoneType.Battlefield)) {
                        if (c.isTapped()) tappedCount++;
                    }
                    if (tappedCount > 0) {
                        output.println("  • " + tappedCount + " permanents to untap");
                    }
                    break;
                case UPKEEP:
                    output.println("  • Upkeep triggers...");
                    break;
                case DRAW:
                    output.println("  • Drawing card...");
                    output.println("  • Library: " + activePlayer.getCardsIn(ZoneType.Library).size() + " cards");
                    break;
                case MAIN1:
                case MAIN2:
                    output.println("  • Main phase - can play lands and spells");
                    output.println("  • " + activePlayer.getName() + " has " +
                            activePlayer.getCardsIn(ZoneType.Hand).size() + " cards in hand");
                    output.println("  • Lands played: " + activePlayer.getLandsPlayedThisTurn() + "/" + activePlayer.getLandsPlayedLastTurn());
                    break;
                case COMBAT_BEGIN:
                    output.println("  • Beginning combat...");
                    int attackers = 0;
                    for (Card c : activePlayer.getCreaturesInPlay()) {
                        if (c.isAttacking()) attackers++;
                    }
                    if (attackers > 0) {
                        output.println("  • " + attackers + " potential attackers");
                    }
                    break;
                case COMBAT_DECLARE_ATTACKERS:
                    output.println("  • Declaring attackers...");
                    break;
                case COMBAT_DECLARE_BLOCKERS:
                    output.println("  • Declaring blockers...");
                    break;
                case COMBAT_DAMAGE:
                    output.println("  • Dealing combat damage...");
                    break;
                case COMBAT_END:
                    output.println("  • Ending combat...");
                    break;
                case END_OF_TURN:
                    output.println("  • End step...");
                    break;
                case CLEANUP:
                    output.println("  • Cleanup - discarding to hand size...");
                    int handSize = activePlayer.getCardsIn(ZoneType.Hand).size();
                    int maxHand = activePlayer.getMaxHandSize();
                    if (handSize > maxHand) {
                        output.println("  • Must discard " + (handSize - maxHand) + " cards");
                    }
                    break;
            }

            // Show priority player
            Player priority = ph.getPriorityPlayer();
            if (priority != null) {
                output.println("  • Priority: " + priority.getName());
            }
        }

        // Card-related events
        @Override
        public Void visit(GameEventCardChangeZone event) {
            if (config.verboseLogging) {
                String fromZone = event.from != null ? event.from.getZoneType().name() : "Unknown";
                String toZone = event.to != null ? event.to.getZoneType().name() : "Unknown";

                // Special handling for common zone changes
                if (event.to != null && event.to.is(ZoneType.Battlefield)) {
                    output.println("  🎴 " + event.card.getController().getName() +
                            " plays " + event.card.getName());
                } else if (event.to != null && event.to.is(ZoneType.Graveyard) && event.from != null && event.from.is(ZoneType.Battlefield)) {
                    output.println("  💀 " + event.card.getName() + " dies");
                } else if (event.to != null && event.to.is(ZoneType.Exile)) {
                    output.println("  🚫 " + event.card.getName() + " is exiled");
                } else if (event.to != null && event.to.is(ZoneType.Hand) && event.from != null && !event.from.is(ZoneType.Library)) {
                    output.println("  ↩️ " + event.card.getName() + " returns to hand");
                }
            }
            return null;
        }

        @Override
        public Void visit(GameEventCardDamaged event) {
            if (config.verboseLogging) {
                output.println("  💥 " + event.card.getName() + " takes " +
                        event.amount + " damage from " + event.source);
            }
            return null;
        }

        @Override
        public Void visit(GameEventCardDestroyed event) {
            if (config.verboseLogging) {
                output.println("  🔥 card is destroyed");
            }
            return null;
        }

        @Override
        public Void visit(GameEventCardSacrificed event) {
            if (config.verboseLogging) {
                output.println("  ⚰️  card was sacrificed. ");
            }
            return null;
        }

        @Override
        public Void visit(GameEventCardTapped event) {
            if (config.verboseLogging && event.tapped) {
                // Only log when card becomes tapped (not untapped)
                if (!event.card.getType().isLand()) {
                    output.println("  ↻ " + event.card.getName() + " taps");
                }
            }
            return null;
        }

        @Override
        public Void visit(GameEventCardCounters event) {
            if (config.verboseLogging) {
                int diff = event.newValue - event.oldValue;
                if (diff > 0) {
                    output.println("  ➕ " + event.card.getName() + " gets " + diff +
                            " " + event.type.getName() + " counter(s) (" + event.newValue + " total)");
                } else if (diff < 0) {
                    output.println("  ➖ " + event.card.getName() + " loses " + (-diff) +
                            " " + event.type.getName() + " counter(s) (" + event.newValue + " total)");
                }
            }
            return null;
        }

        @Override
        public Void visit(GameEventCardAttachment event) {
            if (config.verboseLogging) {
                if (event.newTarget != null) {
                    output.println("  🔗 " + event.equipment.getName() + " attaches to " +
                            event.newTarget.getName());
                } else {
                    output.println("  🔓 " + event.equipment.getName() + " unattaches");
                }
            }
            return null;
        }

        @Override
        public Void visit(GameEventTokenCreated event) {
            if (config.verboseLogging) {
                output.println("  🎯 Token Created");
            }
            return null;
        }

        // Combat events
        @Override
        public Void visit(GameEventAttackersDeclared event) {
            if (config.verboseLogging && !event.attackersMap.isEmpty()) {
                output.println("  ⚔️ " + event.player.getName() + " declares attackers:");
                for (Map.Entry<GameEntity, Collection<Card>> entry : event.attackersMap.asMap().entrySet()) {
                    GameEntity defender = entry.getKey();
                    String defenderName = defender instanceof Player ?
                            ((Player)defender).getName() : defender.toString();
                    for (Card attacker : entry.getValue()) {
                        output.println("    • " + attacker.getName() + " → " + defenderName);
                    }
                }
            }
            return null;
        }

        @Override
        public Void visit(GameEventBlockersDeclared event) {
            if (config.verboseLogging && !event.blockers.isEmpty()) {
                output.println("  🛡️ " + event.defendingPlayer.getName() + " declares blockers:");
                for (Map.Entry<GameEntity, MapOfLists<Card, Card>> defenderEntry : event.blockers.entrySet()) {
                    MapOfLists<Card, Card> blockerAssignments = defenderEntry.getValue();
                    for (Map.Entry<Card, Collection<Card>> blockEntry : blockerAssignments.entrySet()) {
                        Card attacker = blockEntry.getKey();
                        Collection<Card> blockers = blockEntry.getValue();
                        if (blockers.size() == 1) {
                            Card blocker = blockers.iterator().next();
                            output.println("    • " + blocker.getName() + " blocks " + attacker.getName());
                        } else if (blockers.size() > 1) {
                            output.println("    • " + attacker.getName() + " is gang blocked by:");
                            for (Card blocker : blockers) {
                                output.println("      - " + blocker.getName());
                            }
                        }
                    }
                }
            }
            return null;
        }

        @Override
        public Void visit(GameEventCombatChanged event) {
            // Log significant combat changes
            return null;
        }

        @Override
        public Void visit(GameEventCombatEnded event) {
            if (config.verboseLogging) {
                output.println("  🏁 Combat ends");
            }
            return null;
        }

        // Player events
        @Override
        public Void visit(GameEventPlayerDamaged event) {
            if (config.verboseLogging) {
                output.println("  💔 " + event.target.getName() + " takes " + event.amount +
                        " damage (Life: " + event.target.getLife() + ")");
            }
            return null;
        }

        @Override
        public Void visit(GameEventPlayerLivesChanged event) {
            if (config.verboseLogging) {
                output.println("  ❤️ " + event.player.getName() + "'s life: " +
                        event.oldLives + " → " + event.newLives);
            }
            return null;
        }

        @Override
        public Void visit(GameEventManaPool event) {
            // Could log mana pool changes if needed
            return null;
        }

        @Override
        public Void visit(GameEventLandPlayed event) {
            if (config.verboseLogging) {
                output.println("  🏞️ " + event.player.getName() + " plays " + event.land.getName());
            }
            return null;
        }

        // Spell events
        @Override
        public Void visit(GameEventSpellAbilityCast event) {
            if (config.verboseLogging) {
                String caster = event.si.getActivatingPlayer().getName();
                String spell = event.sa.getHostCard().getName();
                if (event.sa.isSpell()) {
                    output.println("  🎯 " + caster + " casts " + spell);
                } else {
                    output.println("  🎯 " + caster + " activates " + spell + "'s ability");
                }
            }
            return null;
        }

        @Override
        public Void visit(GameEventSpellResolved event) {
            if (config.verboseLogging) {
                output.println("  ✓ " + event.spell.getHostCard().getName() + " resolves");
            }
            return null;
        }

        // Game flow events
        @Override
        public Void visit(GameEventTurnBegan event) {
            if (config.logPhaseChanges) {
                output.println("\n╔════════════════════════════════════════════════════════════════════╗");
                output.println("║ TURN " + event.turnNumber + " - " + event.turnOwner.getName().toUpperCase() +
                        "'S TURN" + " ".repeat(Math.max(0, 60 - event.turnOwner.getName().length() - 9 - String.valueOf(event.turnNumber).length())) + "║");
                output.println("╚════════════════════════════════════════════════════════════════════╝");
            }
            return null;
        }

        @Override
        public Void visit(GameEventTurnEnded event) {
            if (config.logPhaseChanges) {
                output.println("\n✅ End of Turn " + game.getPhaseHandler().getTurn());
            }
            return null;
        }

        @Override
        public Void visit(GameEventMulligan event) {
            output.println("  🔄 " + event.player.getName() + " mulligans");
            return null;
        }

        @Override
        public Void visit(GameEventShuffle event) {
            if (config.verboseLogging) {
                output.println("  🔀 " + event.player.getName() + " shuffles their library");
            }
            return null;
        }

        @Override
        public Void visit(GameEventGameStarted event) {
            output.println("\n🎮 Game Started!");
            output.println("═══════════════════════════════════════════════════════════════════\n");
            return null;
        }

        @Override
        public Void visit(GameEventGameFinished event) {
            output.println("\n🏁 Game Finished!");
            return null;
        }

        @Override
        public Void visit(GameEventGameOutcome event) {
            output.println("\n📊 Game Outcome: " + event.result);
            return null;
        }

        // Other events - only log if very verbose or important
        @Override
        public Void visit(GameEventAnteCardsSelected event) {
            // Ante is rarely used
            return null;
        }

        @Override
        public Void visit(GameEventCardModeChosen event) {
            if (config.verboseLogging) {
                output.println("  📋 Mode selected " + event.cardName + event.mode);
            }
            return null;
        }

        @Override
        public Void visit(GameEventCardRegenerated event) {
            if (config.verboseLogging) {
                for (Card card : event.cards) {
                    output.println("  🔄 " + card + " regenerates");
                }
            }
            return null;
        }


        @Override
        public Void visit(GameEventCardPhased event) {
            if (config.verboseLogging) {
                String phase = event.phaseState ? "phases in" : "phases out";
                output.println("  👻 " + event.card.getName() + " " + phase);
            }
            return null;
        }

        @Override
        public Void visit(GameEventCardStatsChanged event) {
            // Too frequent to log
            return null;
        }

        @Override
        public Void visit(GameEventZone event) {
            // Generic zone event, usually covered by more specific events
            return null;
        }

        @Override
        public Void visit(GameEventCombatUpdate event) {
            // Too frequent during combat
            return null;
        }

        @Override
        public Void visit(GameEventPlayerCounters event) {
            if (config.verboseLogging) {
                output.println("  🎯 " + event.receiver.getName() + " gets " +
                        event.type.toString() + " counter(s)");
            }
            return null;
        }

        @Override
        public Void visit(GameEventPlayerPoisoned event) {
            output.println("  ☠️ " + event.receiver.getName() + " gets " +
                    event.amount + " poison counters (Total: " + event.receiver.getPoisonCounters() + ")");
            return null;
        }

        @Override
        public Void visit(GameEventPlayerRadiation event) {
            if (config.verboseLogging) {
                output.println("  ☢️ " + event.receiver.getName() + " gets " +
                        event.change + " rad counters");
            }
            return null;
        }

        @Override
        public Void visit(GameEventPlayerPriority event) {
            // Too frequent to log
            return null;
        }

        @Override
        public Void visit(GameEventRandomLog event) {
            // Debug logging
            return null;
        }

        @Override
        public Void visit(GameEventScry event) {
            if (config.verboseLogging) {
                output.println("  👁️ " + event.player.getName() + " scrys.");
            }
            return null;
        }

        @Override
        public Void visit(GameEventFlipCoin event) {
            if (config.verboseLogging) {
                output.println("  🪙 " + " flipping coin.");
            }
            return null;
        }

        @Override
        public Void visit(GameEventRollDie event) {
            if (config.verboseLogging) {
                output.println("  🎲  rolling dice.");
            }
            return null;
        }

        // Special card mechanics
        @Override
        public Void visit(GameEventCardForetold event) {
            if (config.verboseLogging) {
                output.println("  📜 " + event.activatingPlayer.getName() + " is foretold");
            }
            return null;
        }

        @Override
        public Void visit(GameEventCardPlotted event) {
            if (config.verboseLogging) {
                output.println("  🎭 " + event.card.getName() + " is plotted");
            }
            return null;
        }

        @Override
        public Void visit(GameEventSurveil event) {
            if (config.verboseLogging) {
                output.println("  👁️ " + event.player.getName() + " surveils.");
            }
            return null;
        }

        // Less common events
        @Override
        public Void visit(GameEventSpellRemovedFromStack event) {
            // Usually countered spells are more interesting
            return null;
        }

        @Override
        public Void visit(GameEventManaBurn event) {
            if (config.verboseLogging) {
                output.println("  🔥 " + event.player.getName() + " takes " +
                        event.amount + " mana burn damage");
            }
            return null;
        }

        @Override
        public Void visit(GameEventPlayerControl event) {
            output.println("  👥 " + event.newController.getPlayer().getName() + " gains control of " +
                    event.player.getName());
            return null;
        }

        @Override
        public Void visit(GameEventGameRestarted event) {
            output.println("\n🔄 Game Restarted!");
            return null;
        }

        // Rarely used mechanics
        @Override
        public Void visit(GameEventPlayerShardsChanged event) { return null; }

        @Override
        public Void visit(GameEventPlayerStatsChanged event) { return null; }

        @Override
        public Void visit(GameEventSpeedChanged event) { return null; }

        @Override
        public Void visit(GameEventSubgameStart event) { return null; }

        @Override
        public Void visit(GameEventSubgameEnd event) { return null; }

        @Override
        public Void visit(GameEventSprocketUpdate event) { return null; }

        @Override
        public Void visit(GameEventDayTimeChanged event) { return null; }

        @Override
        public Void visit(GameEventDoorChanged event) { return null; }
    }
}