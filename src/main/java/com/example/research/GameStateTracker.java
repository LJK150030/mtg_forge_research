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
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionContext;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.text.SimpleDateFormat;
import java.util.UUID;

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


    private Map<String, Integer> phaseCountByTurn = new HashMap<>();
    private String currentTurnNodeId;
    private String currentPhaseNodeId;
    private String currentPriorityNodeId;
    private int priorityPassCount = 0;


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
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());


            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Game_Started", String.valueOf(desc), null);
            //handleEvent("GameEventGameStarted", desc.toString());
        }

        @Subscribe
        public void onTurnBegan(GameEventTurnBegan event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            //handleEvent("GameEventTurnBegan", desc.toString());

            // Create Turn node
            currentTurnNodeId = createTurnNode(event.turnNumber, event.turnOwner.getName());

            // Reset counters
            phaseCountByTurn.put(currentTurnNodeId, 0);

            // Log the event
            desc.append(String.format(" | Turn %d began | Turn Owner: %s",
                    event.turnNumber, event.turnOwner.getName()));

            handleEvent("Turn_Began", String.valueOf(desc));
        }

        @Subscribe
        public void onTurnEnded(GameEventTurnEnded event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            // Update turn node with final statistics
            updateTurnNodeOnEnd(currentTurnNodeId);

            desc.append(String.format(" | Turn %d ended | Active Player: %s",
                    ph.getTurn(), ph.getPlayerTurn().getName()));

            handleEvent("Turn_Ended", String.valueOf(desc));
        }

        @Subscribe
        public void onPhaseChanged(GameEventTurnPhase event) {
            try {
                StringBuilder desc = new StringBuilder();

                PhaseHandler ph = game.getPhaseHandler();
                desc.append(" | Phase: ").append(ph.getPhase());
                desc.append(" | Turn: ").append(ph.getTurn());

                // Check if we need to create the turn node first
                if (currentTurnNodeId == null && ph.getTurn() > 0) {
                    // This phase event fired before turn began event
                    String activePlayer = event.playerTurn != null ? event.playerTurn.getName() : ph.getPlayerTurn().getName();
                    currentTurnNodeId = createTurnNode(ph.getTurn(), activePlayer);
                    phaseCountByTurn.put(currentTurnNodeId, 0);
                }

                // Skip if we still don't have a turn (e.g., during game setup)
                if (currentTurnNodeId == null) {
                    System.err.println("Phase event before game properly started: " + event.phase);
                    return;
                }

                // Increment phase count for this turn
                int phaseNum = phaseCountByTurn.getOrDefault(currentTurnNodeId, 0) + 1;
                phaseCountByTurn.put(currentTurnNodeId, phaseNum);

                // Create Phase node
                currentPhaseNodeId = createPhaseNode(
                        event.phase.toString(),
                        event.phase.nameForUi,
                        event.phaseDesc,
                        game.getPhaseHandler().getTurn(),
                        phaseNum,
                        event.playerTurn.getName()
                );

                // Reset priority pass count for new phase
                priorityPassCount = 0;

                desc.append(String.format(" | Phase changed to %s (%s) | Player: %s",
                        event.phase.nameForUi, event.phaseDesc, event.playerTurn.getName()));

                handleEvent("Turn_Phase", String.valueOf(desc));

            } catch (Exception e) {
                System.err.println("Error handling phase change: " + e.getMessage());
                e.printStackTrace();
            }
        }

        @Subscribe
        public void onCardChangeZone(GameEventCardChangeZone event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Card_Change_Zone", String.valueOf(desc), null);
            //handleEvent("GameEventCardChangeZone", desc.toString());
        }

        @Subscribe
        public void onCardTapped(GameEventCardTapped event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Card_Tapped", String.valueOf(desc), null);
            //handleEvent("GameEventCardTapped", desc.toString());
        }

        @Subscribe
        public void onSpellCast(GameEventSpellAbilityCast event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            // Record the action at the priority level
            Map<String, Object> actionDetails = new HashMap<>();
            actionDetails.put("spellName", event.sa.getHostCard().getName());
            actionDetails.put("stackIndex", event.stackIndex);
            actionDetails.put("isAbility", event.sa.isAbility());
            actionDetails.put("description", event.sa.getDescription());

            // Get mana cost if available
            if (event.sa.getPayCosts() != null && event.sa.getPayCosts().getTotalMana() != null) {
                actionDetails.put("manaCost", event.sa.getPayCosts().getTotalMana().toString());
            }

            // Get controller
            if (event.sa.getActivatingPlayer() != null) {
                actionDetails.put("controller", event.sa.getActivatingPlayer().getName());
            }

            // Get host card ID if available
            if (event.sa.getHostCard() != null) {
                actionDetails.put("cardId", event.sa.getHostCard().getId());
                actionDetails.put("cardName", event.sa.getHostCard().getName());
            }

            // Get stack instance details if available
            if (event.si != null) {
                actionDetails.put("stackInstanceId", event.si.getId());
                if (event.si.getSpellAbility() != null) {
                    actionDetails.put("stackDescription", event.si.getStackDescription());
                }
            }

            recordPriorityAction("cast_spell", actionDetails);

            // Create an Action node for this spell cast
            createActionNode("SPELL_CAST", actionDetails);

            // Also handle as a regular event
            if (event.sa.getActivatingPlayer() != null) {
                desc.append(event.sa.getActivatingPlayer().getName());
            } else {
                desc.append(" |  Unknown player");
            }
            desc.append("  | cast ");
            desc.append(event.sa.getHostCard().getName() != null ? event.sa.getHostCard().getName() : "spell");
            desc.append("  | (stack position: ").append(event.stackIndex).append(")");

            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            handleEvent("SPELL_CAST", desc.toString());
        }

        @Subscribe
        public void onSpellResolved(GameEventSpellResolved event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Spell_Resolved", String.valueOf(desc), null);
            //handleEvent("GameEventSpellResolved", desc.toString());
        }

        @Subscribe
        public void onAttackersDeclared(GameEventAttackersDeclared event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Attackers_Declared", String.valueOf(desc), null);
            //handleEvent("GameEventAttackersDeclared", desc.toString());
        }

        @Subscribe
        public void onBlockersDeclared(GameEventBlockersDeclared event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Blockers_Declared", String.valueOf(desc), null);
            //handleEvent("GameEventBlockersDeclared", desc.toString());
        }

        @Subscribe
        public void onCombatEnded(GameEventCombatEnded event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Combat_Ended", String.valueOf(desc), null);
            //handleEvent("GameEventCombatEnded", desc.toString());
        }

        @Subscribe
        public void onPlayerLivesChanged(GameEventPlayerLivesChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Player_Lives_Changed", String.valueOf(desc), null);
            //handleEvent("GameEventPlayerLivesChanged", desc.toString());
        }

        @Subscribe
        public void onCardDamaged(GameEventCardDamaged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Card_Damaged", String.valueOf(desc), null);
            //handleEvent("GameEventCardDamaged", desc.toString());
        }

        @Subscribe
        public void onCardDestroyed(GameEventCardDestroyed event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Card_Destroyed", String.valueOf(desc), null);
            //handleEvent("GameEventCardDestroyed", desc.toString());
        }

        @Subscribe
        public void onManaPool(GameEventManaPool event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Mana_Pool", String.valueOf(desc), null);
            //handleEvent("GameEventManaPool", desc.toString());
        }

        @Subscribe
        public void onCardCounters(GameEventCardCounters event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Card_Counters", String.valueOf(desc), null);
            //handleEvent("GameEventCardCounters", desc.toString());
        }

        @Subscribe
        public void onPlayerCounters(GameEventPlayerCounters event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Player_Counters", String.valueOf(desc), null);
            //handleEvent("GameEventPlayerCounters", desc.toString());
        }

        @Subscribe
        public void onGameFinished(GameEventGameFinished event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Game_Finished", String.valueOf(desc), null);
            //handleEvent("GameEventGameFinished", desc.toString());
        }

        @Subscribe
        public void onGameOutcome(GameEventGameOutcome event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Game_Outcome", String.valueOf(desc), null);
            //handleEvent("GameEventGameOutcome", desc.toString());
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
//            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
//            logEvent("Ante_Cards_Selected", String.valueOf(desc), null);
//            //handleEvent("GameEventAnteCardsSelected", desc.toString());
        }

        @Subscribe
        public void onCardAttachment(GameEventCardAttachment event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Card_Attachment", String.valueOf(desc), null);
            //handleEvent("GameEventCardAttachment", desc.toString());
        }

        @Subscribe
        public void onCardForetold(GameEventCardForetold event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Card_Foretold", String.valueOf(desc), null);
            //handleEvent("GameEventCardForetold", desc.toString());
        }

        @Subscribe
        public void onCardModeChosen(GameEventCardModeChosen event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Card_Mode_Chosen", String.valueOf(desc), null);
            //handleEvent("GameEventCardModeChosen", desc.toString());
        }

        @Subscribe
        public void onCardPhased(GameEventCardPhased event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Card_Phased", String.valueOf(desc), null);
            //handleEvent("GameEventCardPhased", desc.toString());
        }

        @Subscribe
        public void onCardPlotted(GameEventCardPlotted event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Card_Plotted", String.valueOf(desc), null);
            //handleEvent("GameEventCardPlotted", desc.toString());
        }

        @Subscribe
        public void onCardRegenerated(GameEventCardRegenerated event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Card_Regenerated", String.valueOf(desc), null);
            //handleEvent("GameEventCardRegenerated", desc.toString());
        }

        @Subscribe
        public void onCardSacrificed(GameEventCardSacrificed event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Card_Sacrificed", String.valueOf(desc), null);
            //handleEvent("GameEventCardSacrificed", desc.toString());
        }

        @Subscribe
        public void onCardStatsChanged(GameEventCardStatsChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Card_Stats_Changed", String.valueOf(desc), null);
            //handleEvent("GameEventCardStatsChanged", desc.toString());
        }

        @Subscribe
        public void onCombatChanged(GameEventCombatChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Combat_Changed", String.valueOf(desc), null);
            //handleEvent("GameEventCombatChanged", desc.toString());
        }

        @Subscribe
        public void onCombatUpdate(GameEventCombatUpdate event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Combat_Update", String.valueOf(desc), null);
            //handleEvent("GameEventCombatUpdate", desc.toString());
        }

        @Subscribe
        public void onDayTimeChanged(GameEventDayTimeChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Time_Changed", String.valueOf(desc), null);
            //handleEvent("GameEventDayTimeChanged", desc.toString());
        }

        @Subscribe
        public void onDoorChanged(GameEventDoorChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Door_Changed", String.valueOf(desc), null);
            //handleEvent("GameEventDoorChanged", desc.toString());
        }

        @Subscribe
        public void onFlipCoin(GameEventFlipCoin event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Flip_Coin", String.valueOf(desc), null);
            //handleEvent("GameEventFlipCoin", desc.toString());
        }

        @Subscribe
        public void onGameRestarted(GameEventGameRestarted event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Game_Restarted", String.valueOf(desc), null);
            //handleEvent("GameEventGameRestarted", desc.toString());
        }

        @Subscribe
        public void onManaBurn(GameEventManaBurn event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Mana_Burn", String.valueOf(desc), null);
            //handleEvent("GameEventManaBurn", desc.toString());
        }

        @Subscribe
        public void onMulligan(GameEventMulligan event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Mulligan", String.valueOf(desc), null);
            //handleEvent("GameEventMulligan", desc.toString());
        }

        @Subscribe
        public void onPlayerControl(GameEventPlayerControl event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Player_Control", String.valueOf(desc), null);
            //handleEvent("GameEventPlayerControl", desc.toString());
        }

        @Subscribe
        public void onPlayerDamaged(GameEventPlayerDamaged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Player_Damaged", String.valueOf(desc), null);
            //handleEvent("GameEventPlayerDamaged", desc.toString());
        }

        @Subscribe
        public void onPlayerPoisoned(GameEventPlayerPoisoned event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Player_Poisoned", String.valueOf(desc), null);
            //handleEvent("GameEventPlayerPoisoned", desc.toString());
        }

        @Subscribe
        public void onPlayerPriority(GameEventPlayerPriority event) {
            try {
                StringBuilder desc = new StringBuilder();

                PhaseHandler ph = game.getPhaseHandler();
                desc.append(" | Phase: ").append(ph.getPhase());
                desc.append(" | Turn: ").append(ph.getTurn());

                // Check if we need to create turn/phase nodes first
                if (currentTurnNodeId == null && ph.getTurn() > 0) {
                    String activePlayer = event.turn != null ? event.turn.getName() : ph.getPlayerTurn().getName();
                    currentTurnNodeId = createTurnNode(ph.getTurn(), activePlayer);
                    phaseCountByTurn.put(currentTurnNodeId, 0);
                }

                if (currentPhaseNodeId == null && currentTurnNodeId != null) {
                    // Create a phase node for this priority event
                    int phaseNum = phaseCountByTurn.getOrDefault(currentTurnNodeId, 0) + 1;
                    phaseCountByTurn.put(currentTurnNodeId, phaseNum);

                    currentPhaseNodeId = createPhaseNode(
                            event.phase.toString(),
                            event.phase.nameForUi,
                            event.phase.toString(), // Use phase toString as description
                            ph.getTurn(),
                            phaseNum,
                            event.turn.getName()
                    );
                    priorityPassCount = 0;
                }

                // Skip if we still don't have proper context
                if (currentTurnNodeId == null || currentPhaseNodeId == null) {
                    System.err.println("Priority event without proper game context");
                    return;
                }

                Player turn_holder = event.turn;
                Player priority_holder = event.priority;
                priorityPassCount++;

                // Create Priority node
                String newPriorityNodeId = createPriorityNode(
                        priority_holder.getName(),  // Use event.priority.getName()
                        event.turn.getName(),      // The player whose turn it is
                        event.phase.toString(),
                        event.phase.nameForUi,
                        game.getPhaseHandler().getTurn(),
                        priorityPassCount,
                        game.getStack().size()
                );

                // Link to previous priority if exists
                if (currentPriorityNodeId != null) {
                    linkPriorityNodes(currentPriorityNodeId, newPriorityNodeId);
                }

                currentPriorityNodeId = newPriorityNodeId;

                desc.append(String.format(" | Priority: %s | Turn Player: %s | Phase: %s | Stack: %d",
                        priority_holder.getName(), ph.getPlayerTurn().getName(),
                        event.phase.nameForUi, game.getStack().size()));

                handleEvent("PRIORITY_CHANGED", String.valueOf(desc));

            } catch (Exception e) {
                System.err.println("Error handling priority: " + e.getMessage());
                e.printStackTrace();
            }
        }

        @Subscribe
        public void onPlayerRadiation(GameEventPlayerRadiation event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Player_Radiation", String.valueOf(desc), null);
            //handleEvent("GameEventPlayerRadiation", desc.toString());
        }

        @Subscribe
        public void onPlayerShardsChanged(GameEventPlayerShardsChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Player_Shards_Changed", String.valueOf(desc), null);
            //handleEvent("GameEventPlayerShardsChanged", desc.toString());
        }

        @Subscribe
        public void onPlayerStatsChanged(GameEventPlayerStatsChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Player_Stats_Changed", String.valueOf(desc), null);
            //handleEvent("GameEventPlayerStatsChanged", desc.toString());
        }

        @Subscribe
        public void onRandomLog(GameEventRandomLog event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Random_Log", String.valueOf(desc), null);
            //handleEvent("GameEventRandomLog", desc.toString());
        }

        @Subscribe
        public void onRollDie(GameEventRollDie event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Roll_Die", String.valueOf(desc), null);
            //handleEvent("GameEventRollDie", desc.toString());
        }

        @Subscribe
        public void onShuffle(GameEventShuffle event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Shuffle", String.valueOf(desc), null);
            //handleEvent("GameEventShuffle", desc.toString());
        }

        @Subscribe
        public void onSpeedChanged(GameEventSpeedChanged event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Speed_Changed", String.valueOf(desc), null);
            //handleEvent("GameEventSpeedChanged", desc.toString());
        }

        @Subscribe
        public void onScry(GameEventScry event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Scry", String.valueOf(desc), null);
            //handleEvent("GameEventScry", desc.toString());
        }

        @Subscribe
        public void onSpellRemovedFromStack(GameEventSpellRemovedFromStack event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Spell_Removed_From_Stack", String.valueOf(desc), null);
            //handleEvent("GameEventSpellRemovedFromStack", desc.toString());
        }

        @Subscribe
        public void onSprocketUpdate(GameEventSprocketUpdate event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Sprocket_Update", String.valueOf(desc), null);
            //handleEvent("GameEventSprocketUpdate", desc.toString());
        }

        @Subscribe
        public void onSubgameEnd(GameEventSubgameEnd event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Subgame_End", String.valueOf(desc), null);
            //handleEvent("GameEventSubgameEnd", desc.toString());
        }

        @Subscribe
        public void onSubgameStart(GameEventSubgameStart event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Subgame_Start", String.valueOf(desc), null);
            //handleEvent("GameEventSubgameStart", desc.toString());
        }

        @Subscribe
        public void onSurveil(GameEventSurveil event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Surveil", String.valueOf(desc), null);
            //handleEvent("GameEventSurveil", desc.toString());
        }

        @Subscribe
        public void onTokenCreated(GameEventTokenCreated event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Token_Created", String.valueOf(desc), null);
            //handleEvent("GameEventTokenCreated", desc.toString());
        }

        @Subscribe
        public void onZone(GameEventZone event) {
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            desc.append(" | \u001B[31mTODO: implement ").append(event.getClass().getSimpleName()).append("\u001B[0m");
            logEvent("Zone", String.valueOf(desc), null);
            //handleEvent("GameEventZone", desc.toString());
        }

        // Updated onLandPlayed to create Action node
        @Subscribe
        public void onLandPlayed(GameEventLandPlayed event) {
            Map<String, Object> actionDetails = new HashMap<>();
            actionDetails.put("landName", event.land.getName());
            actionDetails.put("controller", event.player.getName());
            actionDetails.put("cardId", event.land.getId());

            // Add land type information if available
            if (event.land.isBasicLand()) {
                actionDetails.put("isBasicLand", true);
            }
            if (event.land.getType() != null) {
                actionDetails.put("landTypes", event.land.getType().toString());
            }

            recordPriorityAction("play_land", actionDetails);

            // Create an Action node for this land play
            createActionNode("play_land", actionDetails);

            // Also handle as a regular event
            StringBuilder desc = new StringBuilder();
            desc.append(event.player.getName());
            desc.append(" played ");
            desc.append(event.land.getName());
            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            handleEvent("LAND_PLAYED", desc.toString());
        }

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
                    "gameId", String.valueOf(game.getId()),
                    "timestamp", System.currentTimeMillis(),
                    "players", game.getPlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList())
            );

            return tx.run(query, params).single().asMap();
        });

        this.gameNodeId = (String) result.get("gameId");
        createPlayerNodes();
    }

    private void createPlayerNodes() {
        neo4j.writeTransaction(tx -> {
            for (Player p : game.getPlayers()) {
                // First MERGE the player node (global across games)
                String mergePlayerQuery = """
                MERGE (p:Player {name: $name})
                ON CREATE SET 
                    p.isAI = $isAI,
                    p.firstSeen = $firstSeen,
                    p.gamesPlayed = 1
                ON MATCH SET 
                    p.gamesPlayed = COALESCE(p.gamesPlayed, 0) + 1
                """;

                tx.run(mergePlayerQuery, Map.of(
                        "name", p.getName(),
                        "isAI", p.getController().isAI(),
                        "firstSeen", System.currentTimeMillis()
                ));

                // Create relationship between Game and Player with game-specific data
                String linkQuery = """
                MATCH (g:Game {id: $gameId})
                MATCH (p:Player {name: $name})
                CREATE (g)-[:HAS_PLAYER {
                    startingLife: $startingLife,
                    playerIndex: $playerIndex
                }]->(p)
                """;

                tx.run(linkQuery, Map.of(
                        "gameId", gameNodeId,
                        "name", p.getName(),
                        "startingLife", p.getStartingLife(),
                        "playerIndex", game.getPlayers().indexOf(p)
                ));
            }
            return null;
        });
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
        //saveEventAndStateToNeo4j(eventType, eventDescription, delta, prevStateId);

        //Linking turn, phase, priority
        linkGameStateToHierarchy();

        // Log the event (existing functionality)
        logEvent(eventType, eventDescription, delta);
    }

    private void saveEventAndStateToNeo4j(String eventType, String eventDescription,
                                          StateInfo.StateDelta delta, String prevStateId) {
        neo4j.writeTransaction(tx -> {
            // Create GameState node
            //String stateNodeId = createGameStateNode(tx);

            // Create GameEvent node
            String eventNodeId = createGameEventNode(tx, eventType, eventDescription, delta);

            // Create relationships
            //createStateTransitionRelationships(tx, prevStateId, stateNodeId, eventNodeId);

            // Update game entities based on delta
            updateGameEntities(tx, delta);

            // Store current state ID for next transition
            //currentStateNodeId = stateNodeId;

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
        params.put("gameId", gameNodeId);
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
        MATCH (g:Game {id: $gameId})
        MATCH (g)-[:HAS_PLAYER]->(p:Player {name: $playerName})
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

            // Create relationships for combat
            if (card.attacking) {
                createAttackingRelationship(tx, stateId, card);
            }
            if (card.blocking) {
                createBlockingRelationships(tx, stateId, card);
            }
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

    // GameEvent Node Functions
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

    private void linkGameStateToHierarchy() {
        if (currentStateNodeId == null) return;

        neo4j.writeTransaction(tx -> {
            // Link to Turn
            if (currentTurnNodeId != null) {
                tx.run("""
            MATCH (s:GameState {id: $stateId})
            MATCH (t:Turn {id: $turnId})
            CREATE (s)-[:DURING_TURN]->(t)
            """, Map.of("stateId", currentStateNodeId, "turnId", currentTurnNodeId));
            }

            // Link to Phase
            if (currentPhaseNodeId != null) {
                tx.run("""
            MATCH (s:GameState {id: $stateId})
            MATCH (p:Phase {id: $phaseId})
            CREATE (s)-[:DURING_PHASE]->(p)
            """, Map.of("stateId", currentStateNodeId, "phaseId", currentPhaseNodeId));
            }

            // Link to Priority
            if (currentPriorityNodeId != null) {
                tx.run("""
            MATCH (s:GameState {id: $stateId})
            MATCH (pr:Priority {id: $priorityId})
            CREATE (s)-[:AT_PRIORITY]->(pr)
            """, Map.of("stateId", currentStateNodeId, "priorityId", currentPriorityNodeId));
            }

            return null;
        });
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

    private String createTurnNode(int turnNumber, String activePlayer) {
        return neo4j.writeTransaction(tx -> {
            String turnId = String.format("%s-turn-%d", gameNodeId, turnNumber);

            String query = """
            MATCH (g:Game {id: $gameId})
            CREATE (t:Turn {
                id: $turnId,
                gameId: $gameId,
                turnNumber: $turnNumber,
                activePlayer: $activePlayer,
                startTime: $startTime,
                phaseCount: 0,
                priorityPassCount: 0,
                spellsCast: 0,
                damageDealt: 0
            })
            CREATE (g)-[:HAS_TURN]->(t)
            WITH t, g
            OPTIONAL MATCH (g)-[:HAS_TURN]->(prevTurn:Turn)
            WHERE prevTurn.turnNumber = $turnNumber - 1
              AND prevTurn.gameId = $gameId  // IMPORTANT: Scope to current game
            WITH t, prevTurn
            ORDER BY prevTurn.turnNumber DESC
            LIMIT 1
            FOREACH (pt IN CASE WHEN prevTurn IS NOT NULL THEN [prevTurn] ELSE [] END |
                CREATE (pt)-[:NEXT_TURN]->(t)
            )
            RETURN t.id as turnId
            """;

            Map<String, Object> params = Map.of(
                    "gameId", gameNodeId,
                    "turnId", turnId,
                    "turnNumber", turnNumber,
                    "activePlayer", activePlayer,
                    "startTime", System.currentTimeMillis()
            );

            return tx.run(query, params).single().get("turnId").asString();
        });
    }

    private String createPhaseNode(String phaseType, String phaseName, String description,
                                   int turnNumber, int phaseNumber, String activePlayer) {
        return neo4j.writeTransaction(tx -> {
            String normalizedPhaseType = phaseType.toLowerCase().replace(" ", "_");
            String phaseId = String.format("%s-turn-%d-phase-%s-%d",
                    gameNodeId, turnNumber, normalizedPhaseType, phaseNumber);

            String query = """
            MATCH (t:Turn {id: $turnId})
            WHERE t.gameId = $gameId  // Ensure we're in the right game
            CREATE (p:Phase {
                id: $phaseId,
                gameId: $gameId,
                turnNumber: $turnNumber,
                phaseNumber: $phaseNumber,
                phaseType: $phaseType,
                phaseName: $phaseName,
                phaseDescription: $phaseDesc,
                activePlayer: $activePlayer,
                startTime: $startTime,
                priorityExchanges: 0,
                actionsPerformed: 0
            })
            CREATE (t)-[:HAS_PHASE]->(p)
            WITH p, t
            OPTIONAL MATCH (t)-[:HAS_PHASE]->(prevPhase:Phase)
            WHERE prevPhase.phaseNumber = $phaseNumber - 1
              AND prevPhase.gameId = $gameId  // IMPORTANT: Scope to current game
            WITH p, t, prevPhase
            ORDER BY prevPhase.phaseNumber DESC
            LIMIT 1
            FOREACH (pp IN CASE WHEN prevPhase IS NOT NULL THEN [prevPhase] ELSE [] END |
                CREATE (pp)-[:NEXT_PHASE]->(p)
            )
            SET t.phaseCount = t.phaseCount + 1
            RETURN p.id as phaseId
            """;

            Map<String, Object> params = new HashMap<>();
            params.put("gameId", gameNodeId);
            params.put("turnId", currentTurnNodeId);
            params.put("phaseId", phaseId);
            params.put("phaseType", phaseType);
            params.put("phaseName", phaseName);
            params.put("phaseDesc", description);
            params.put("turnNumber", turnNumber);
            params.put("phaseNumber", phaseNumber);
            params.put("activePlayer", activePlayer);
            params.put("startTime", System.currentTimeMillis());

            var result = tx.run(query, params).single();
            return result.get("phaseId").asString();
        });
    }

    private String createPriorityNode(String priorityHolder, String turnPlayer,
                                      String phaseType, String phaseNameUI,
                                      int turnNumber, int priorityNumber, int stackSize) {
        return neo4j.writeTransaction(tx -> {
            String normalizedPhaseType = phaseType.toLowerCase().replace(" ", "_");
            String priorityId = String.format("%s-turn-%d-phase-%s-priority-%d",
                    gameNodeId, turnNumber, normalizedPhaseType, priorityNumber);

            String query = """
            MATCH (p:Phase {id: $phaseId})
            WHERE p.gameId = $gameId  // Ensure we're in the right game
            CREATE (pr:Priority {
                id: $priorityId,
                gameId: $gameId,
                priorityHolder: $priorityHolder,
                turnPlayer: $turnPlayer,
                phaseType: $phaseType,
                phaseName: $phaseName,
                priorityNumber: $priorityNumber,
                stackSize: $stackSize,
                timestamp: $timestamp,
                actionTaken: false,
                passedPriority: true
            })
            CREATE (p)-[:HAS_PRIORITY_PASS]->(pr)
            SET p.priorityExchanges = p.priorityExchanges + 1
            WITH pr
            MATCH (t:Turn {id: $turnId})
            WHERE t.gameId = $gameId  // Ensure we're in the right game
            SET t.priorityPassCount = t.priorityPassCount + 1
            RETURN pr.id as priorityId
            """;

            Map<String, Object> params = new HashMap<>();
            params.put("gameId", gameNodeId);
            params.put("phaseId", currentPhaseNodeId);
            params.put("turnId", currentTurnNodeId);
            params.put("priorityId", priorityId);
            params.put("priorityHolder", priorityHolder);
            params.put("turnPlayer", turnPlayer);
            params.put("phaseType", phaseType);
            params.put("phaseName", phaseNameUI);
            params.put("priorityNumber", priorityNumber);
            params.put("stackSize", stackSize);
            params.put("timestamp", System.currentTimeMillis());

            var result = tx.run(query, params).single();
            return result.get("priorityId").asString();
        });
    }

    private void updatePriorityNodeWithAction(org.neo4j.driver.TransactionContext tx, String priorityId, String action, Map<String, Object> actionDetails) {
        // Add null check
        if (priorityId == null) {
            System.err.println("WARNING: Attempting to update null priorityId with action: " + action);
            return;
        }

        try {
            String actionDetailsJson = mapToJsonString(actionDetails);

            // First check if the Priority node exists
            String checkQuery = "MATCH (pr:Priority {id: $priorityId}) RETURN pr.id as id";
            var checkResult = tx.run(checkQuery, Map.of("priorityId", priorityId));

            if (!checkResult.hasNext()) {
                System.err.println("WARNING: Priority node not found: " + priorityId);
                return;
            }

            // Use OPTIONAL MATCH for the Phase relationship to prevent failure
            String query = """
        MATCH (pr:Priority {id: $priorityId})
        SET pr.actionTaken = true,
            pr.passedPriority = false,
            pr.action = $action,
            pr.actionDetailsJson = $actionDetailsJson
        WITH pr
        OPTIONAL MATCH (p:Phase)-[:HAS_PRIORITY_PASS]->(pr)
        WHERE p IS NOT NULL
        SET p.actionsPerformed = COALESCE(p.actionsPerformed, 0) + 1
        RETURN pr.id as updatedId
        """;

            var result = tx.run(query, Map.of(
                    "priorityId", priorityId,
                    "action", action,
                    "actionDetailsJson", actionDetailsJson
            ));

            if (result.hasNext()) {
                System.out.println("Successfully updated priority node: " + priorityId + " with action: " + action);
            } else {
                System.err.println("Failed to update priority node: " + priorityId);
            }

        } catch (Exception e) {
            System.err.println("Error updating priority node: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void linkPriorityNodes(String fromPriorityId, String toPriorityId) {
        neo4j.writeTransaction(tx -> {
            String query = """
            MATCH (from:Priority {id: $fromId})
            MATCH (to:Priority {id: $toId})
            CREATE (from)-[:PASSED_TO]->(to)
            """;

            tx.run(query, Map.of(
                    "fromId", fromPriorityId,
                    "toId", toPriorityId
            ));

            return null;
        });
    }

    private void updateTurnNodeOnEnd(String turnId) {
        neo4j.writeTransaction(tx -> {
            String query = """
        MATCH (t:Turn {id: $turnId})
        SET t.endTime = $endTime,
            t.duration = $endTime - t.startTime
        WITH t
        OPTIONAL MATCH (t)-[:HAS_PHASE]->(p:Phase)
        WITH t, count(p) as finalPhaseCount
        SET t.phaseCount = finalPhaseCount
        """;

            tx.run(query, Map.of(
                    "turnId", turnId,
                    "endTime", System.currentTimeMillis()
            ));

            return null;
        });
    }

    // Additional helper methods for tracking actions during priority
    public void recordPriorityAction(String action, Map<String, Object> actionDetails) {
        if (currentPriorityNodeId == null) {
            System.err.println("WARNING: No current priority node to record action: " + action);
            // Optionally, you could create a priority node here if needed
            return;
        }

        neo4j.writeTransaction(tx -> {
            try {
                // Check if the phase and turn nodes exist
                if (currentPhaseNodeId == null || currentTurnNodeId == null) {
                    System.err.println("WARNING: Missing phase or turn node for action: " + action);
                    return null;
                }

                // Convert the Map to individual properties or JSON string
                String query = """
                MATCH (pr:Priority {id: $priorityId})
                SET pr.actionTaken = true,
                    pr.passedPriority = false,
                    pr.action = $action,
                    pr.actionDetailsJson = $actionDetailsJson
                WITH pr
                OPTIONAL MATCH (p:Phase {id: $phaseId})
                WHERE p IS NOT NULL
                SET p.actionsPerformed = COALESCE(p.actionsPerformed, 0) + 1
                WITH p
                OPTIONAL MATCH (t:Turn {id: $turnId})
                WHERE t IS NOT NULL AND $action CONTAINS 'cast'
                SET t.spellsCast = COALESCE(t.spellsCast, 0) + 1
                """;

                String actionDetailsJson = mapToJsonString(actionDetails);

                Map<String, Object> params = Map.of(
                        "priorityId", currentPriorityNodeId,
                        "phaseId", currentPhaseNodeId,
                        "turnId", currentTurnNodeId,
                        "action", action,
                        "actionDetailsJson", actionDetailsJson
                );

                tx.run(query, params);

            } catch (Exception e) {
                System.err.println("Error recording priority action: " + e.getMessage());
                e.printStackTrace();
            }

            return null;
        });
    }

    // Helper method to convert Map to JSON string
    private String mapToJsonString(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            first = false;

            json.append("\"").append(entry.getKey()).append("\":");

            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
            }
        }

        json.append("}");
        return json.toString();
    }

    // Track ability activations
    public void recordAbilityActivation(SpellAbility ability) {
        Map<String, Object> actionDetails = new HashMap<>();
        actionDetails.put("abilityDesc", ability.getDescription());
        actionDetails.put("source", ability.getHostCard().getName());
        actionDetails.put("controller", ability.getActivatingPlayer().getName());
        actionDetails.put("manaCost", ability.getPayCosts().getTotalMana().toString());

        recordPriorityAction("activate_ability", actionDetails);
    }

    // Updated createActionNode to track land plays in turn statistics
    private void createActionNode(String actionType, Map<String, Object> details) {
        neo4j.writeTransaction(tx -> {
            String actionId = UUID.randomUUID().toString();
            String detailsJson = mapToJsonString(details);

            String query = """
            MATCH (pr:Priority {id: $priorityId})
            CREATE (a:Action {
                id: $actionId,
                type: $actionType,
                timestamp: $timestamp,
                detailsJson: $detailsJson
            })
            CREATE (pr)-[:PERFORMED]->(a)
            WITH a
            MATCH (p:Phase {id: $phaseId})
            CREATE (a)-[:DURING_PHASE]->(p)
            WITH a
            MATCH (t:Turn {id: $turnId})
            CREATE (a)-[:DURING_TURN]->(t)
            
            // Update turn statistics if relevant
            WITH a, t
            WHERE $actionType IN ['cast_spell', 'activate_ability']
            SET t.spellsCast = t.spellsCast + 1
            
            WITH a, t
            WHERE $actionType = 'deal_damage' AND $damageAmount IS NOT NULL
            SET t.damageDealt = t.damageDealt + $damageAmount
            
            // Track land plays
            WITH a, t
            WHERE $actionType = 'play_land'
            SET t.landsPlayed = COALESCE(t.landsPlayed, 0) + 1
            """;

            Map<String, Object> params = new HashMap<>();
            params.put("actionId", actionId);
            params.put("priorityId", currentPriorityNodeId);
            params.put("phaseId", currentPhaseNodeId);
            params.put("turnId", currentTurnNodeId);
            params.put("actionType", actionType);
            params.put("timestamp", System.currentTimeMillis());
            params.put("detailsJson", detailsJson);
            params.put("damageAmount", details.getOrDefault("damageAmount", 0));

            tx.run(query, params);

            // Pass the transaction to updatePriorityNodeWithAction
            updatePriorityNodeWithAction(tx, currentPriorityNodeId, actionType, details);

            return null;
        });
    }


}