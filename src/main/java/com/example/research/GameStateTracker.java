package com.example.research;

import forge.card.MagicColor;
import forge.card.mana.ManaAtom;
import forge.game.*;
import forge.game.card.*;
import forge.game.combat.*;
import forge.game.event.*;
import forge.game.phase.*;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.*;
import forge.game.mana.*;

import com.google.common.eventbus.Subscribe;
import org.neo4j.driver.TransactionContext;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.text.SimpleDateFormat;

/**
 * Game State Tracker that logs state transitions in the format:
 * 1. Event that triggered the change
 * 2. List of variables that changed
 * 3. Full state after the event
 *
 * Now with support for controlling hidden state logging per player
 */
public class GameStateTracker {

    private final Game game;
    private final PrintStream output;
    private final boolean verboseLogging;
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    private StateInfo.GameState currentState;
    private int eventCounter = 0;

    private final PrintWriter fileWriter;

    // Map of player name to whether their hidden states should be logged
    private final Map<String, Boolean> hiddenStateLogging;

    private final Neo4jService neo4j;
    private String gameNodeId;
    private String previousStateNodeId;
    private String currentStateNodeId;


    public GameStateTracker(Game game, PrintStream output, boolean verboseLogging) {
        this(game, output, verboseLogging, false, null, new HashMap<>());
    }

    /**
     * Constructor with hidden state logging control
     */
    public GameStateTracker(Game game, PrintStream output, boolean verboseLogging,
                            boolean logToFile, String logFilePath,
                            Map<String, Boolean> hiddenStateLogging) {
        this.game = game;
        this.output = output;
        this.verboseLogging = verboseLogging;
        this.hiddenStateLogging = hiddenStateLogging != null ? hiddenStateLogging : new HashMap<>();

        // Output configuration
        if (logToFile && logFilePath != null) {
            try {
                this.fileWriter = new PrintWriter(new FileWriter(logFilePath));
            } catch (IOException e) {
                throw new RuntimeException("Failed to create log file: " + logFilePath, e);
            }
        } else {
            this.fileWriter = null;
        }

        // Create and register event listener
        GameEventListener eventListener = new GameEventListener();
        game.subscribeToEvents(eventListener);

        this.neo4j = Neo4jService.getInstance();
        initializeGameInNeo4j();

        // Capture initial state
        currentState = new StateInfo.GameState(game);

        logEvent("GAME_START", "Initial game state", new StateInfo.StateDelta());
    }


    /**
     * Event listener class that subscribes to game events
     */
    private class GameEventListener {

        @Subscribe
        public void onGameStarted(GameEventGameStarted event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventGameStarted", desc.toString());
        }

        @Subscribe
        public void onTurnBegan(GameEventTurnBegan event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventTurnBegan", desc.toString());
        }

        @Subscribe
        public void onTurnEnded(GameEventTurnEnded event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventTurnEnded", desc.toString());
        }

        @Subscribe
        public void onPhaseChanged(GameEventTurnPhase event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventTurnPhase", desc.toString());
        }

        @Subscribe
        public void onCardChangeZone(GameEventCardChangeZone event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCardChangeZone", desc.toString());
        }

        @Subscribe
        public void onCardTapped(GameEventCardTapped event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCardTapped", desc.toString());
        }

        @Subscribe
        public void onSpellCast(GameEventSpellAbilityCast event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventSpellAbilityCast", desc.toString());
        }

        @Subscribe
        public void onSpellResolved(GameEventSpellResolved event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventSpellResolved", desc.toString());
        }

        @Subscribe
        public void onAttackersDeclared(GameEventAttackersDeclared event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventAttackersDeclared", desc.toString());
        }

        @Subscribe
        public void onBlockersDeclared(GameEventBlockersDeclared event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventBlockersDeclared", desc.toString());
        }

        @Subscribe
        public void onCombatEnded(GameEventCombatEnded event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCombatEnded", desc.toString());
        }

        @Subscribe
        public void onPlayerLivesChanged(GameEventPlayerLivesChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventPlayerLivesChanged", desc.toString());
        }

        @Subscribe
        public void onCardDamaged(GameEventCardDamaged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCardDamaged", desc.toString());
        }

        @Subscribe
        public void onCardDestroyed(GameEventCardDestroyed event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCardDestroyed", desc.toString());
        }

        @Subscribe
        public void onLandPlayed(GameEventLandPlayed event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventLandPlayed", desc.toString());
        }

        @Subscribe
        public void onManaPool(GameEventManaPool event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventManaPool", desc.toString());
        }

        @Subscribe
        public void onCardCounters(GameEventCardCounters event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCardCounters", desc.toString());
        }

        @Subscribe
        public void onPlayerCounters(GameEventPlayerCounters event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventPlayerCounters", desc.toString());
        }

        @Subscribe
        public void onGameFinished(GameEventGameFinished event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventGameFinished", desc.toString());
        }

        @Subscribe
        public void onGameOutcome(GameEventGameOutcome event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventGameOutcome", desc.toString());
        }

        @Subscribe
        public void onAnteCardsSelected(GameEventAnteCardsSelected event) {
            // Ante is an old Magic: The Gathering mechanic where players bet cards from their deck before the game starts.
            // The winner of the game gets all the ante cards.

//            StringBuilder desc = new StringBuilder();
//
//            PhaseHandler ph = game.getPhaseHandler();
//            desc.append(" | Phase: ").append(ph.getPhase());
//            desc.append(" | Turn: ").append(ph.getTurn());
//
//            handleEvent("GameEventAnteCardsSelected", desc.toString());
        }

        @Subscribe
        public void onCardAttachment(GameEventCardAttachment event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCardAttachment", desc.toString());
        }

        @Subscribe
        public void onCardForetold(GameEventCardForetold event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCardForetold", desc.toString());
        }

        @Subscribe
        public void onCardModeChosen(GameEventCardModeChosen event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCardModeChosen", desc.toString());
        }

        @Subscribe
        public void onCardPhased(GameEventCardPhased event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCardPhased", desc.toString());
        }

        @Subscribe
        public void onCardPlotted(GameEventCardPlotted event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCardPlotted", desc.toString());
        }

        @Subscribe
        public void onCardRegenerated(GameEventCardRegenerated event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCardRegenerated", desc.toString());
        }

        @Subscribe
        public void onCardSacrificed(GameEventCardSacrificed event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCardSacrificed", desc.toString());
        }

        @Subscribe
        public void onCardStatsChanged(GameEventCardStatsChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCardStatsChanged", desc.toString());
        }

        @Subscribe
        public void onCombatChanged(GameEventCombatChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCombatChanged", desc.toString());
        }

        @Subscribe
        public void onCombatUpdate(GameEventCombatUpdate event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventCombatUpdate", desc.toString());
        }

        @Subscribe
        public void onDayTimeChanged(GameEventDayTimeChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventDayTimeChanged", desc.toString());
        }

        @Subscribe
        public void onDoorChanged(GameEventDoorChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventDoorChanged", desc.toString());
        }

        @Subscribe
        public void onFlipCoin(GameEventFlipCoin event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventFlipCoin", desc.toString());
        }

        @Subscribe
        public void onGameRestarted(GameEventGameRestarted event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventGameRestarted", desc.toString());
        }

        @Subscribe
        public void onManaBurn(GameEventManaBurn event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventManaBurn", desc.toString());
        }

        @Subscribe
        public void onMulligan(GameEventMulligan event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventMulligan", desc.toString());
        }

        @Subscribe
        public void onPlayerControl(GameEventPlayerControl event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventPlayerControl", desc.toString());
        }

        @Subscribe
        public void onPlayerDamaged(GameEventPlayerDamaged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventPlayerDamaged", desc.toString());
        }

        @Subscribe
        public void onPlayerPoisoned(GameEventPlayerPoisoned event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventPlayerPoisoned", desc.toString());
        }

        @Subscribe
        public void onPlayerPriority(GameEventPlayerPriority event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventPlayerPriority", desc.toString());
        }

        @Subscribe
        public void onPlayerRadiation(GameEventPlayerRadiation event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventPlayerRadiation", desc.toString());
        }

        @Subscribe
        public void onPlayerShardsChanged(GameEventPlayerShardsChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventPlayerShardsChanged", desc.toString());
        }

        @Subscribe
        public void onPlayerStatsChanged(GameEventPlayerStatsChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventPlayerStatsChanged", desc.toString());
        }

        @Subscribe
        public void onRandomLog(GameEventRandomLog event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventRandomLog", desc.toString());
        }

        @Subscribe
        public void onRollDie(GameEventRollDie event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventRollDie", desc.toString());
        }

        @Subscribe
        public void onShuffle(GameEventShuffle event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventShuffle", desc.toString());
        }

        @Subscribe
        public void onSpeedChanged(GameEventSpeedChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventSpeedChanged", desc.toString());
        }

        @Subscribe
        public void onScry(GameEventScry event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventScry", desc.toString());
        }

        @Subscribe
        public void onSpellRemovedFromStack(GameEventSpellRemovedFromStack event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventSpellRemovedFromStack", desc.toString());
        }

        @Subscribe
        public void onSprocketUpdate(GameEventSprocketUpdate event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventSprocketUpdate", desc.toString());
        }

        @Subscribe
        public void onSubgameEnd(GameEventSubgameEnd event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventSubgameEnd", desc.toString());
        }

        @Subscribe
        public void onSubgameStart(GameEventSubgameStart event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventSubgameStart", desc.toString());
        }

        @Subscribe
        public void onSurveil(GameEventSurveil event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventSurveil", desc.toString());
        }

        @Subscribe
        public void onTokenCreated(GameEventTokenCreated event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventTokenCreated", desc.toString());
        }

        @Subscribe
        public void onZone(GameEventZone event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            handleEvent("GameEventZone", desc.toString());
        }



//        // Generic handler for any event not specifically handled
//        @Subscribe
//        public void onGenericEvent(GameEvent event) {
//            StringBuilder desc = new StringBuilder();
//
//            PhaseHandler ph = game.getPhaseHandler();
//            desc.append(" | Phase: ").append(ph.getPhase());
//            desc.append(" | Turn: ").append(ph.getTurn());
//
//            handleEvent("onGenericEvent", desc.toString());
//        }
    }

    // Helper method to get defender name (reused from combat state)
    private String getDefenderName(GameEntity defender) {
        if (defender instanceof Player) {
            Player p = (Player) defender;
            return p.getName() + " (Player, Life: " + p.getLife() + ")";
        } else if (defender instanceof Card) {
            Card c = (Card) defender;
            String name = c.getName() + " [" + c.getId() + "]";
            if (c.isPlaneswalker()) {
                name += " (PW, Loyalty: " + c.getCounters(CounterType.get(CounterEnumType.LOYALTY)) + ")";
            }
            return name;
        }
        return defender.toString();
    }



    /**
     * Track zone changes that have state-based implications
     */
    private void trackZoneChangeEffects(Card card, ZoneType from, ZoneType to,
                                        Player fromPlayer, Player toPlayer) {
        // Track graveyard entries for "died this turn" effects
        if (to == ZoneType.Graveyard && from == ZoneType.Battlefield) {
            // This is a "dies" event - useful for tracking death triggers
            if (toPlayer != null) {
                // You could maintain a list of cards that died this turn
                // cardsAddedThisTurn is already tracked in Zone class
            }
        }

        // Track exile movements for processors, adventures, etc.
        if (to == ZoneType.Exile) {
            // Track face-up/face-down exile status if needed
            // card.isFaceDown() can be checked here
        }

        // Track command zone movements
        if (from == ZoneType.Command || to == ZoneType.Command) {
            // Important for commander damage, companion rules, etc.
        }

        // Track stack movements for storm count, cast triggers
        if (to == ZoneType.Stack) {
            // This indicates a spell being cast
        }
    }

    /**
     * Determine if card details should be revealed based on zones and players
     */
    private boolean shouldRevealCardDetails(Card card, ZoneType from, ZoneType to,
                                            Player fromPlayer, Player toPlayer) {
        // Always reveal public zone changes
        if (!isHiddenZone(from) && !isHiddenZone(to)) {
            return true;
        }

        // Check player-specific hidden state logging settings
        if (fromPlayer != null && shouldShowHiddenInfo(fromPlayer.getName())) {
            return true;
        }
        if (toPlayer != null && shouldShowHiddenInfo(toPlayer.getName())) {
            return true;
        }

        // Special cases where hidden info becomes public
        if (from == ZoneType.Library && to != ZoneType.Hand) {
            // Library to anywhere except hand is usually revealed
            return true;
        }

        // Cards revealed from hand
        if (from == ZoneType.Hand && (to == ZoneType.Stack || to == ZoneType.Battlefield)) {
            return true;
        }

        return false;
    }

    /**
     * Create sanitized description for hidden zone changes
     */
    private String createSanitizedDescription(ZoneType from, ZoneType to,
                                              Player fromPlayer, Player toPlayer) {
        StringBuilder desc = new StringBuilder("A card moved from ");

        if (from != null) {
            desc.append(from);
            if (fromPlayer != null) {
                desc.append(" (").append(fromPlayer.getName()).append(")");
            }
        } else {
            desc.append("outside the game");
        }

        desc.append(" to ");

        if (to != null) {
            desc.append(to);
            if (toPlayer != null) {
                desc.append(" (").append(toPlayer.getName()).append(")");
            }
        } else {
            desc.append("outside the game");
        }

        return desc.toString();
    }


    /**
     * Check if we should show card details for a player's hidden zones
     */
    private boolean shouldShowHiddenInfo(String playerName) {
        return hiddenStateLogging.getOrDefault(playerName, false);
    }

    /**
     * Check if a zone is hidden information
     */
    private boolean isHiddenZone(ZoneType zone) {
        return zone == ZoneType.Hand || zone == ZoneType.Library;
    }


    private void initializeGameInNeo4j() {
        Map<String, Object> result = neo4j.writeTransaction(tx -> {
            String query = """
            CREATE (g:Game {
                id: $gameId,
                timestamp: $timestamp,
                players: $players
            })
            RETURN g.id as gameId
            """;

            Map<String, Object> params = Map.of(
                    "gameId", String.valueOf(game.getId()), // Convert to String here
                    "timestamp", System.currentTimeMillis(),
                    "players", game.getPlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList())
            );

            return tx.run(query, params).single().asMap();
        });

        this.gameNodeId = (String) result.get("gameId"); // Now this will work

        // Create player nodes
        createPlayerNodes();
    }

    private void createPlayerNodes() {
        neo4j.writeTransaction(tx -> {
            for (Player p : game.getPlayers()) {
                String query = """
                MATCH (g:Game {id: $gameId})
                CREATE (p:Player {
                    name: $name,
                    gameId: $gameId,
                    startingLife: $startingLife,
                    isAI: $isAI
                })
                CREATE (g)-[:HAS_PLAYER]->(p)
                """;

                Map<String, Object> params = Map.of(
                        "gameId", gameNodeId, // This is already a String now
                        "name", p.getName(),
                        "startingLife", p.getStartingLife(),
                        "isAI", p.getController().isAI()
                );

                tx.run(query, params);
            }
            return null;
        });
    }


    /**
     * Check if we should show card details for a zone change
     */
    private boolean shouldShowCardDetails(String playerName, ZoneType from, ZoneType to) {
        // Always show public zone changes
        if (!isHiddenZone(from) && !isHiddenZone(to)) {
            return true;
        }

        // Check if we should show this player's hidden info
        return shouldShowHiddenInfo(playerName);
    }

    /**
     * Handle an event by capturing state changes
     */
    private void handleEvent(String eventType, String eventDescription) {
        // Save previous state
        StateInfo.GameState previousState = currentState;
        String prevStateId = currentStateNodeId;

        // Capture new state
        currentState = new StateInfo.GameState(game);

        // Compute changes
        StateInfo.StateDelta delta = computeDelta(previousState, currentState);

        // Save to Neo4j
        saveEventAndStateToNeo4j(eventType, eventDescription, delta, prevStateId);

        // Log the event (existing functionality)
        logEvent(eventType, eventDescription, delta);
    }

    private void saveEventAndStateToNeo4j(String eventType, String eventDescription,
                                          StateInfo.StateDelta delta, String prevStateId) {
        neo4j.writeTransaction(tx -> {
            // Create GameState node
            String stateNodeId = createGameStateNode(tx);

            // Create GameEvent node
            String eventNodeId = createGameEventNode(tx, eventType, eventDescription, delta);

            // Create relationships
            createStateTransitionRelationships(tx, prevStateId, stateNodeId, eventNodeId);

            // Update game entities based on delta
            updateGameEntities(tx, delta);

            // Store current state ID for next transition
            currentStateNodeId = stateNodeId;

            return null;
        });
    }

    /**
     * Log an event with state transition
     */
    private void logEvent(String eventType, String eventDescription, StateInfo.StateDelta delta) {
        eventCounter++;

        StringBuilder log = new StringBuilder();
        log.append("\n");
        log.append("════════════════════════════════════════════════════════════════════\n");
        log.append("EVENT #").append(eventCounter).append(" - ").append(timestampFormat.format(new Date())).append("\n");
        log.append("════════════════════════════════════════════════════════════════════\n");

        // Section 1: Event that triggered the change
        log.append("\n1. EVENT TRIGGERED:\n");
        log.append("   Type: ").append(eventType).append("\n");
        log.append("   Description: ").append(eventDescription).append("\n");

//        // Section 2: Variables that changed
//        log.append("\n2. CHANGES:\n");
//        if (delta.hasChanges()) {
//            for (String change : delta.changes) {
//                log.append("   • ").append(change).append("\n");
//            }
//        } else {
//            log.append("   • No state changes\n");
//        }
//
//        // Section 3: Full state after event (only if verbose or significant changes)
//        if (verboseLogging || delta.changes.size() > 3) {
//            log.append("\n3. GAME STATE AFTER EVENT:\n");
//            log.append(formatGameState(currentState));
//        }

        log.append("\n────────────────────────────────────────────────────────────────────\n");

        // Output to console
        output.print(log.toString());

        // Output to file if configured
        if (fileWriter != null) {
            fileWriter.print(log.toString());
            fileWriter.flush();
        }
    }

    /**
     * Close resources
     */
    public void close() {
        if (fileWriter != null) {
            fileWriter.close();
        }
    }

    private StateInfo.StateDelta computeDelta(StateInfo.GameState previousState, StateInfo.GameState currentState) {
        StateInfo.StateDelta delta = new StateInfo.StateDelta();

        // Game level changes
        if (previousState.turnNumber != currentState.turnNumber) {
            delta.addChange("TURN", "Turn changed from " + previousState.turnNumber + " to " + currentState.turnNumber);
        }

        // Fix: Handle null phase values properly
        if (previousState.phase != currentState.phase) {
            // Check if both are non-null and different, or if one is null and the other isn't
            if ((previousState.phase == null && currentState.phase != null) ||
                    (previousState.phase != null && currentState.phase == null) ||
                    (previousState.phase != null && currentState.phase != null && !previousState.phase.equals(currentState.phase))) {

                String prevPhaseStr = previousState.phase != null ? previousState.phase.toString() : "Not Started";
                String currPhaseStr = currentState.phase != null ? currentState.phase.toString() : "Not Started";
                delta.addChange("PHASE", "Phase changed from " + prevPhaseStr + " to " + currPhaseStr);
            }
        }

        if (!previousState.activePlayer.equals(currentState.activePlayer)) {
            delta.addChange("ACTIVE_PLAYER", "Active player changed from " + previousState.activePlayer + " to " + currentState.activePlayer);
        }
        if (!previousState.priorityPlayer.equals(currentState.priorityPlayer)) {
            delta.addChange("PRIORITY", "Priority changed from " + previousState.priorityPlayer + " to " + currentState.priorityPlayer);
        }

        // Player states
        for (String playerName : currentState.players.keySet()) {
            StateInfo.PlayerState prevPlayer = previousState.players.get(playerName);
            StateInfo.PlayerState currPlayer = currentState.players.get(playerName);

            if (prevPlayer != null && currPlayer != null) {
                if (prevPlayer.life != currPlayer.life) {
                    delta.addChange("LIFE", playerName + " life changed from " + prevPlayer.life + " to " + currPlayer.life);
                }
                if (prevPlayer.poisonCounters != currPlayer.poisonCounters) {
                    delta.addChange("POISON", playerName + " poison changed from " + prevPlayer.poisonCounters + " to " + currPlayer.poisonCounters);
                }
                // Add more player state comparisons as needed
            }
        }

        // Zone changes
        for (String playerName : currentState.zones.keySet()) {
            Map<ZoneType, StateInfo.ZoneState> prevZones = previousState.zones.get(playerName);
            Map<ZoneType, StateInfo.ZoneState> currZones = currentState.zones.get(playerName);

            if (prevZones != null && currZones != null) {
                for (ZoneType zoneType : ZoneType.values()) {
                    StateInfo.ZoneState prevZone = prevZones.get(zoneType);
                    StateInfo.ZoneState currZone = currZones.get(zoneType);

                    if (prevZone != null && currZone != null && prevZone.size != currZone.size) {
                        delta.addChange("ZONE", playerName + "'s " + zoneType + " changed from " +
                                prevZone.size + " to " + currZone.size + " cards");
                    }
                }
            }
        }

        // Stack changes
        if (previousState.stack.size() != currentState.stack.size()) {
            delta.addChange("STACK", "Stack size changed from " + previousState.stack.size() +
                    " to " + currentState.stack.size());
        }

        // Combat changes
        boolean prevCombat = previousState.combat != null && !previousState.combat.attackersByDefender.isEmpty();
        boolean currCombat = currentState.combat != null && !currentState.combat.attackersByDefender.isEmpty();

        if (prevCombat != currCombat) {
            if (currCombat) {
                delta.addChange("COMBAT", "Combat started with " + currentState.combat.totalAttackers + " attackers");
            } else {
                delta.addChange("COMBAT", "Combat ended");
            }
        }

        return delta;
    }
    // Replace the existing formatGameState method with this corrected version:
    private String formatGameState(StateInfo.GameState state) {
        StringBuilder sb = new StringBuilder();

        // Game info
        sb.append("   GAME INFO:\n");
        sb.append("     • Turn: ").append(state.turnNumber).append("\n");
        sb.append("     • Phase: ").append(state.phase).append("\n");
        sb.append("     • Active Player: ").append(state.activePlayer).append("\n");
        sb.append("     • Priority: ").append(state.priorityPlayer).append("\n");
        if (state.monarch != null && !"None".equals(state.monarch)) {
            sb.append("     • Monarch: ").append(state.monarch).append("\n");
        }
        if (state.initiative != null && !"None".equals(state.initiative)) {
            sb.append("     • Initiative: ").append(state.initiative).append("\n");
        }

        // Player states
        sb.append("\n   PLAYERS:\n");
        for (StateInfo.PlayerState p : state.players.values()) {
            sb.append("     ").append(p.name).append(":\n");
            sb.append("       • Life: ").append(p.life).append("\n");
            if (p.poisonCounters > 0) {
                sb.append("       • Poison: ").append(p.poisonCounters).append("\n");
            }
            if (!p.counters.isEmpty()) {
                sb.append("       • Counters: ");
                for (Map.Entry<CounterType, Integer> e : p.counters.entrySet()) {
                    sb.append(e.getKey().getName()).append("=").append(e.getValue()).append(" ");
                }
                sb.append("\n");
            }
            sb.append("       • Mana Pool: ").append(p.manaPool.toManaString()).append("\n");
            sb.append("       • Lands Played: ").append(p.turnState.landsPlayedThisTurn).append("\n");

            // Use zoneCounts from PlayerState instead of accessing zones directly
            sb.append("       • Zones: Hand=").append(p.zoneCounts.get(ZoneType.Hand))
                    .append(", Library=").append(p.zoneCounts.get(ZoneType.Library))
                    .append(", Graveyard=").append(p.zoneCounts.get(ZoneType.Graveyard))
                    .append(", Battlefield=").append(p.zoneCounts.get(ZoneType.Battlefield)).append("\n");
        }

        // Stack
        if (!state.stack.isEmpty()) {
            sb.append("\n   STACK:\n");
            for (int i = 0; i < state.stack.size(); i++) {
                StateInfo.StackItem item = state.stack.get(i);
                sb.append("     ").append(i + 1).append(". ").append(item.description)
                        .append(" (").append(item.controller).append(")\n");
            }
        }

        // Combat
        if (state.combat != null && !state.combat.attackersByDefender.isEmpty()) {
            sb.append("\n   COMBAT:\n");
            sb.append("     Attacking Player: ").append(state.combat.attackingPlayer).append("\n");
            sb.append("     Total Attackers: ").append(state.combat.totalAttackers).append("\n");
            sb.append("     Total Power on Board: ").append(state.combat.totalDamageOnBoard).append("\n");

            if (state.combat.hasFirstStrike || state.combat.hasDoubleStrike) {
                sb.append("     Combat Phases: ");
                if (state.combat.hasFirstStrike) sb.append("First Strike ");
                if (state.combat.hasDoubleStrike) sb.append("Double Strike ");
                sb.append("\n");
            }

            // Attackers by defender
            sb.append("     Attack Assignments:\n");
            for (Map.Entry<String, List<StateInfo.CombatState.AttackerInfo>> entry :
                    state.combat.attackersByDefender.entrySet()) {
                sb.append("       → ").append(entry.getKey()).append(":\n");
                for (StateInfo.CombatState.AttackerInfo attacker : entry.getValue()) {
                    sb.append("         • ").append(attacker.toString()).append("\n");
                }
            }

            // Blockers if any
            if (!state.combat.blockersByAttacker.isEmpty()) {
                sb.append("     Block Assignments:\n");
                for (Map.Entry<Integer, List<StateInfo.CombatState.BlockerInfo>> entry :
                        state.combat.blockersByAttacker.entrySet()) {
                    sb.append("       Attacker [").append(entry.getKey()).append("] blocked by:\n");
                    for (StateInfo.CombatState.BlockerInfo blocker : entry.getValue()) {
                        sb.append("         • ").append(blocker.toString()).append("\n");
                    }
                }
            }

            // Combat-relevant abilities
            if (!state.combat.combatRelevantAbilities.isEmpty()) {
                sb.append("     Special Abilities:\n");
                for (String ability : state.combat.combatRelevantAbilities) {
                    sb.append("       • ").append(ability).append("\n");
                }
            }
        }

        // Zones for each player
        for (Map.Entry<String, Map<ZoneType, StateInfo.ZoneState>> playerZones : state.zones.entrySet()) {
            String playerName = playerZones.getKey();
            boolean showHidden = shouldShowHiddenInfo(playerName);

            // Battlefield (always public)
            StateInfo.ZoneState battlefield = playerZones.getValue().get(ZoneType.Battlefield);
            if (battlefield != null && !battlefield.cards.isEmpty()) {
                sb.append("\n   ").append(playerName).append("'s BATTLEFIELD:\n");
                for (StateInfo.CardState card : battlefield.cards) {
                    sb.append("     • ").append(formatCardState(card)).append("\n");
                }
            }

            // Hand (hidden unless explicitly allowed)
            StateInfo.ZoneState hand = playerZones.getValue().get(ZoneType.Hand);
            if (hand != null && !hand.cards.isEmpty()) {
                if (showHidden) {
                    sb.append("\n   ").append(playerName).append("'s HAND:\n");
                    for (StateInfo.CardState card : hand.cards) {
                        sb.append("     • ").append(card.name).append(" [").append(card.id).append("]\n");
                    }
                } else if (verboseLogging) {
                    sb.append("\n   ").append(playerName).append("'s HAND: ")
                            .append(hand.cards.size()).append(" cards (hidden)\n");
                }
            }

            // Graveyard (always public)
            StateInfo.ZoneState graveyard = playerZones.getValue().get(ZoneType.Graveyard);
            if (graveyard != null && !graveyard.cards.isEmpty()) {
                sb.append("\n   ").append(playerName).append("'s GRAVEYARD:\n");
                for (StateInfo.CardState card : graveyard.cards) {
                    sb.append("     • ").append(card.name).append(" [").append(card.id).append("]\n");
                }
            }

            // Exile (always public)
            StateInfo.ZoneState exile = playerZones.getValue().get(ZoneType.Exile);
            if (exile != null && !exile.cards.isEmpty()) {
                sb.append("\n   ").append(playerName).append("'s EXILE:\n");
                for (StateInfo.CardState card : exile.cards) {
                    sb.append("     • ").append(card.name).append(" [").append(card.id).append("]\n");
                }
            }

            // Library (hidden unless explicitly allowed)
            if (verboseLogging) {
                StateInfo.ZoneState library = playerZones.getValue().get(ZoneType.Library);
                if (showHidden && library != null && !library.cards.isEmpty()) {
                    sb.append("\n   ").append(playerName).append("'s LIBRARY TOP CARDS:\n");
                    // Show only top few cards to avoid massive logs
                    int cardsToShow = Math.min(5, library.cards.size());
                    for (int i = 0; i < cardsToShow; i++) {
                        StateInfo.CardState card = library.cards.get(i);
                        sb.append("     • ").append(i + 1).append(": ")
                                .append(card.name).append(" [").append(card.id).append("]\n");
                    }
                    if (library.cards.size() > cardsToShow) {
                        sb.append("     • ... and ").append(library.cards.size() - cardsToShow)
                                .append(" more cards\n");
                    }
                }
            }
        }

        return sb.toString();
    }

    // Helper method to format a card state on the battlefield
    private String formatCardState(StateInfo.CardState card) {
        StringBuilder sb = new StringBuilder();
        sb.append(card.name).append(" [").append(card.id).append("]");

        if (card.type.isCreature()) {
            sb.append(" (").append(card.power).append("/").append(card.toughness);
            if (card.damage > 0) {
                sb.append(", ").append(card.damage).append(" damage");
            }
            sb.append(")");
        } else if (card.type.isPlaneswalker()) {
            sb.append(" (Loyalty: ").append(card.loyaltyCounters).append(")");
        }

        if (card.tapped) {
            sb.append(" [Tapped]");
        }

        if (!card.keywords.isEmpty()) {
            sb.append(" - ").append(String.join(", ", card.keywords));
        }

        if (!card.counters.isEmpty()) {
            sb.append(" {");
            boolean first = true;
            for (Map.Entry<CounterType, Integer> counter : card.counters.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(counter.getKey().getName()).append(": ").append(counter.getValue());
                first = false;
            }
            sb.append("}");
        }

        return sb.toString();
    }

    private String createGameStateNode(TransactionContext tx) {
        String stateId = UUID.randomUUID().toString();

        String query = """
        CREATE (s:GameState {
            id: $stateId,
            gameId: $gameId,
            timestamp: $timestamp,
            eventNumber: $eventNumber,
            turn: $turn,
            phase: $phase,
            activePlayer: $activePlayer,
            priorityPlayer: $priorityPlayer,
            stackSize: $stackSize
        })
        RETURN s.id as stateId
        """;

        Map<String, Object> params = new HashMap<>();
        params.put("stateId", stateId);
        params.put("gameId", gameNodeId); // gameNodeId is already a String
        params.put("timestamp", System.currentTimeMillis());
        params.put("eventNumber", eventCounter);
        params.put("turn", currentState.turnNumber);
        params.put("phase", currentState.phase != null ? currentState.phase.toString() : "NOT_STARTED");
        params.put("activePlayer", currentState.activePlayer);
        params.put("priorityPlayer", currentState.priorityPlayer);
        params.put("stackSize", currentState.stack.size());

        tx.run(query, params);

        // Create detailed state nodes
        createPlayerStateNodes(tx, stateId);
        createZoneStateNodes(tx, stateId);

        if (currentState.combat != null) {
            createCombatStateNode(tx, stateId);
        }

        return stateId;
    }

    private void createPlayerStateNodes(TransactionContext tx, String stateId) {
        for (StateInfo.PlayerState playerState : currentState.players.values()) {
            String query = """
                MATCH (s:GameState {id: $stateId})
                MATCH (p:Player {name: $playerName, gameId: $gameId})
                CREATE (ps:PlayerState {
                    stateId: $stateId,
                    playerName: $playerName,
                    life: $life,
                    poisonCounters: $poison,
                    manaPool: $manaPool,
                    cardsInHand: $handSize,
                    landsPlayed: $landsPlayed
                })
                CREATE (s)-[:HAS_PLAYER_STATE]->(ps)
                CREATE (ps)-[:STATE_OF]->(p)
                """;

            Map<String, Object> params = new HashMap<>();
            params.put("stateId", stateId);
            params.put("gameId", gameNodeId);
            params.put("playerName", playerState.name);
            params.put("life", playerState.life);
            params.put("poison", playerState.poisonCounters);
            params.put("manaPool", playerState.manaPool.toManaString());
            params.put("handSize", playerState.zoneCounts.get(ZoneType.Hand));
            params.put("landsPlayed", playerState.turnState.landsPlayedThisTurn);

            tx.run(query, params);
        }
    }

    private void createZoneStateNodes(TransactionContext tx, String stateId) {
        for (Map.Entry<String, Map<ZoneType, StateInfo.ZoneState>> playerZones :
                currentState.zones.entrySet()) {

            String playerName = playerZones.getKey();
            boolean showHidden = shouldShowHiddenInfo(playerName);

            for (Map.Entry<ZoneType, StateInfo.ZoneState> zoneEntry :
                    playerZones.getValue().entrySet()) {

                ZoneType zoneType = zoneEntry.getKey();
                StateInfo.ZoneState zone = zoneEntry.getValue();

                // Only store public zones or zones we're allowed to see
                if (!isHiddenZone(zoneType) || showHidden) {
                    createZoneNode(tx, stateId, playerName, zone);

                    // Create card nodes for visible cards
                    if (zoneType == ZoneType.Battlefield ||
                            zoneType == ZoneType.Graveyard ||
                            zoneType == ZoneType.Exile ||
                            (showHidden && !zone.cards.isEmpty())) {

                        createCardNodes(tx, stateId, zone);
                    }
                }
            }
        }
    }

    private void createZoneNode(TransactionContext tx, String stateId,
                                String playerName, StateInfo.ZoneState zone) {
        String query = """
            MATCH (s:GameState {id: $stateId})
            CREATE (z:Zone {
                stateId: $stateId,
                type: $zoneType,
                owner: $owner,
                cardCount: $cardCount
            })
            CREATE (s)-[:HAS_ZONE]->(z)
            """;

        Map<String, Object> params = Map.of(
                "stateId", stateId,
                "zoneType", zone.type.toString(),
                "owner", playerName,
                "cardCount", zone.size
        );

        tx.run(query, params);
    }

    private void createCardNodes(TransactionContext tx, String stateId,
                                 StateInfo.ZoneState zone) {
        for (StateInfo.CardState card : zone.cards) {
            String query = """
            CREATE (c:Card {
                stateId: $stateId,
                cardId: $cardId,
                name: $name,
                zone: $zone,
                controller: $controller,
                owner: $owner,
                power: $power,
                toughness: $toughness,
                tapped: $tapped,
                damage: $damage,
                types: $types,
                keywords: $keywords
            })
            WITH c
            MATCH (z:Zone {stateId: $stateId, type: $zone, owner: $owner})
            CREATE (c)-[:IN_ZONE]->(z)
            """;

            Map<String, Object> params = new HashMap<>();
            params.put("stateId", stateId);
            params.put("cardId", card.id);
            params.put("name", card.name);
            params.put("zone", zone.type.toString());
            params.put("controller", card.controller);
            params.put("owner", card.owner);
            params.put("power", card.power);
            params.put("toughness", card.toughness);
            params.put("tapped", card.tapped);
            params.put("damage", card.damage);

            // Convert CoreType objects to strings
            List<String> typeStrings = new ArrayList<>();
            if (card.type != null && card.type.getCoreTypes() != null) {
                for (Object coreType : card.type.getCoreTypes()) {
                    typeStrings.add(coreType.toString());
                }
            }
            params.put("types", typeStrings);

            params.put("keywords", new ArrayList<>(card.keywords));

            tx.run(query, params);

            // Create relationships for attachments, blocks, etc.
            if (card.attacking) {
                createAttackingRelationship(tx, stateId, card);
            }
            if (card.blocking) {
                createBlockingRelationships(tx, stateId, card);
            }
        }
    }

    private void createCombatStateNode(TransactionContext tx, String stateId) {
        StateInfo.CombatState combat = currentState.combat;

        String query = """
            MATCH (s:GameState {id: $stateId})
            CREATE (c:Combat {
                stateId: $stateId,
                attackingPlayer: $attackingPlayer,
                totalAttackers: $totalAttackers,
                totalDamage: $totalDamage,
                turn: $turn,
                combatNumber: $combatNumber
            })
            CREATE (s)-[:HAS_COMBAT]->(c)
            """;

        Map<String, Object> params = Map.of(
                "stateId", stateId,
                "attackingPlayer", combat.attackingPlayer,
                "totalAttackers", combat.totalAttackers,
                "totalDamage", combat.totalDamageOnBoard,
                "turn", combat.turn,
                "combatNumber", combat.combatNumber
        );

        tx.run(query, params);
    }

    private String createGameEventNode(TransactionContext tx, String eventType,
                                       String eventDescription, StateInfo.StateDelta delta) {
        String eventId = UUID.randomUUID().toString();

        String query = """
            CREATE (e:GameEvent {
                id: $eventId,
                gameId: $gameId,
                type: $eventType,
                description: $description,
                timestamp: $timestamp,
                eventNumber: $eventNumber,
                changes: $changes
            })
            RETURN e.id as eventId
            """;

        Map<String, Object> params = Map.of(
                "eventId", eventId,
                "gameId", gameNodeId,
                "eventType", eventType,
                "description", eventDescription,
                "timestamp", System.currentTimeMillis(),
                "eventNumber", eventCounter,
                "changes", delta.changes
        );

        tx.run(query, params);
        return eventId;
    }

    private void createStateTransitionRelationships(TransactionContext tx,
                                                    String prevStateId,
                                                    String newStateId,
                                                    String eventId) {
        if (prevStateId != null) {
            // Link states
            String linkStatesQuery = """
                MATCH (prev:GameState {id: $prevStateId})
                MATCH (next:GameState {id: $nextStateId})
                CREATE (prev)-[:NEXT_STATE]->(next)
                """;

            tx.run(linkStatesQuery, Map.of(
                    "prevStateId", prevStateId,
                    "nextStateId", newStateId
            ));
        }

        // Link event to states
        String linkEventQuery = """
            MATCH (e:GameEvent {id: $eventId})
            MATCH (s:GameState {id: $stateId})
            CREATE (e)-[:RESULTED_IN]->(s)
            CREATE (s)-[:TRIGGERED_BY]->(e)
            """;

        tx.run(linkEventQuery, Map.of(
                "eventId", eventId,
                "stateId", newStateId
        ));

        if (prevStateId != null) {
            String linkEventToPrevQuery = """
                MATCH (e:GameEvent {id: $eventId})
                MATCH (prev:GameState {id: $prevStateId})
                CREATE (prev)-[:CAUSED]->(e)
                """;

            tx.run(linkEventToPrevQuery, Map.of(
                    "eventId", eventId,
                    "prevStateId", prevStateId
            ));
        }
    }

    private void updateGameEntities(TransactionContext tx, StateInfo.StateDelta delta) {
        // Update based on specific change categories
        for (Map.Entry<String, List<String>> categoryChanges :
                delta.categorizedChanges.entrySet()) {

            String category = categoryChanges.getKey();
            List<String> changes = categoryChanges.getValue();

            switch (category) {
                case "LIFE":
                    updateLifeTotals(tx, changes);
                    break;
                case "ZONE":
                    updateZoneChanges(tx, changes);
                    break;
                case "COMBAT":
                    updateCombatInfo(tx, changes);
                    break;
                // Add more categories as needed
            }
        }
    }

    private void createAttackingRelationship(TransactionContext tx, String stateId,
                                             StateInfo.CardState attacker) {
        if (attacker.attackingTarget != null) {
            String query = """
                MATCH (c:Card {stateId: $stateId, cardId: $cardId})
                MATCH (s:GameState {id: $stateId})
                CREATE (c)-[:ATTACKING {target: $target}]->(s)
                """;

            tx.run(query, Map.of(
                    "stateId", stateId,
                    "cardId", attacker.id,
                    "target", attacker.attackingTarget
            ));
        }
    }

    private void createBlockingRelationships(TransactionContext tx, String stateId,
                                             StateInfo.CardState blocker) {
        for (Integer blockedId : blocker.blockingCards) {
            String query = """
                MATCH (blocker:Card {stateId: $stateId, cardId: $blockerId})
                MATCH (attacker:Card {stateId: $stateId, cardId: $attackerId})
                CREATE (blocker)-[:BLOCKING]->(attacker)
                """;

            tx.run(query, Map.of(
                    "stateId", stateId,
                    "blockerId", blocker.id,
                    "attackerId", blockedId
            ));
        }
    }

    // Implement update methods for specific change types
    private void updateLifeTotals(TransactionContext tx, List<String> changes) {
        // Parse life changes and update player nodes
        for (String change : changes) {
            // Parse: "PlayerName life changed from X to Y"
            // Update the Player node with new life total
        }
    }

    private void updateZoneChanges(TransactionContext tx, List<String> changes) {
        // Handle card movements between zones
        for (String change : changes) {
            // Parse zone changes and create movement relationships
        }
    }

    private void updateCombatInfo(TransactionContext tx, List<String> changes) {
        // Update combat-related information
    }
}