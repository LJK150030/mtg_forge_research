package com.example.research;

import forge.*;
import forge.ai.AiController;
import forge.ai.LobbyPlayerAi;
import forge.ai.PlayerControllerAi;
import forge.card.ColorSet;
import forge.card.ICardFace;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.deck.*;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.ability.effects.RollDiceEffect;
import forge.game.combat.Combat;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostPartMana;
import forge.game.event.*;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordInterface;
import forge.game.mana.Mana;
import forge.game.mana.ManaConversionMatrix;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.phase.*;
import forge.game.player.*;
import forge.game.card.*;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.*;
import forge.game.staticability.StaticAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.MagicStack;
import forge.game.zone.PlayerZone;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.item.*;
import forge.util.*;
import forge.util.collect.FCollectionView;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.function.Predicate;

/**
 * Enhanced Forge Match Simulator with comprehensive event logging and state visualization
 *
 * This version includes:
 * - Extended configuration options for detailed logging
 * - Callback support for pre/post action tracking
 * - Comprehensive event handlers for all game events
 * - Enhanced formatting and timestamp support
 */
public class ForgeEnhancedSimulator {

    // Simulation configuration with extended options
    public static class SimulationConfig {
        public enum Mode { TURNS, FULL_MATCH, UNTIL_CONDITION }

        public Mode mode = Mode.TURNS;
        public int turnsToSimulate = 10;
        public boolean verboseLogging = true;
        public boolean logEvents = true;
        public boolean logPhaseChanges = true;
        public boolean logCombat = true;
        public boolean logManaUsage = true;
        public boolean logStackOperations = true;
        public int focusPlayerIndex = -1; // -1 means show all players
        public boolean showHiddenZones = true; // Show hands, library counts
        public boolean pauseBetweenPhases = false;
        public int maxTurnsBeforeTimeout = 100; // Prevent infinite games

        // Extended detailed logging flags
        public boolean logCardDraws = false;
        public boolean logLandPlays = false;
        public boolean logSpellsCast = false;
        public boolean logAbilityActivations = false;
        public boolean logTriggers = false;
        public boolean logStateBasedActions = false;
        public boolean logPriorityPasses = false;
        public boolean logAIDecisions = false;
        public boolean pauseBetweenActions = false;

        // Output formatting options
        public boolean detailedActionFormatter = false;
        public boolean includeTimestamps = false;
        public boolean includeStackTrace = false;

        // Custom formatting
        public SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    }

    /**
     * Callback interfaces for action tracking
     */
    public interface PreActionCallback {
        void onPreAction(Game game, String action);
    }

    public interface PostActionCallback {
        void onPostAction(Game game, String action, ActionResult result);
    }

    public static class ActionResult {
        private final boolean stateChanged;
        private final String description;

        public ActionResult(boolean stateChanged, String description) {
            this.stateChanged = stateChanged;
            this.description = description;
        }

        public boolean isStateChanged() { return stateChanged; }
        public String getDescription() { return description; }

        @Override
        public String toString() { return description; }
    }

    /**
     * DetailedEventHandler for comprehensive event tracking
     */
    public abstract static class DetailedEventHandler {
        public void onPhaseBegin(Game game, String phase) {}
        public void onActionTaken(Game game, String action, Player actor) {}
        public void onStateCheck(Game game) {}
        public void onCardDrawn(Game game, Player player, Card card) {}
        public void onLandPlayed(Game game, Player player, Card land) {}
        public void onSpellCast(Game game, Player player, SpellAbility spell) {}
        public void onAbilityActivated(Game game, Player player, SpellAbility ability) {}
        public void onTrigger(Game game, Trigger trigger) {}
        public void onPriorityPass(Game game, Player player) {}
        public void onAIDecision(Game game, Player ai, String decision) {}
    }

    // Event logger for capturing game events
    public static class GameEventLogger implements IGameEventVisitor<Void> {
        private final SimulationConfig config;
        private final PrintStream output;

        public GameEventLogger(SimulationConfig config, PrintStream output) {
            this.config = config;
            this.output = output;
        }

        private void log(String message) {
            if (config.verboseLogging) {
                output.println("[EVENT] " + message);
            }
        }

        @Override
        public Void visit(GameEventAnteCardsSelected gameEventAnteCardsSelected) {
            return null;
        }

        @Override
        public Void visit(GameEventAttackersDeclared gameEventAttackersDeclared) {
            return null;
        }

        @Override
        public Void visit(GameEventBlockersDeclared gameEventBlockersDeclared) {
            return null;
        }

        @Override
        public Void visit(GameEventCardDamaged gameEventCardDamaged) {
            return null;
        }

        @Override
        public Void visit(GameEventCardDestroyed gameEventCardDestroyed) {
            return null;
        }

        @Override
        public Void visit(GameEventCardAttachment gameEventCardAttachment) {
            return null;
        }

        @Override
        public Void visit(GameEventCardChangeZone gameEventCardChangeZone) {
            return null;
        }

        @Override
        public Void visit(GameEventCardModeChosen event) {
            log(String.format("Mode chosen for %s: %s",
                    event.cardName, event.mode));
            return null;
        }

        @Override
        public Void visit(GameEventCardRegenerated gameEventCardRegenerated) {
            return null;
        }

        @Override
        public Void visit(GameEventCardSacrificed gameEventCardSacrificed) {
            return null;
        }

        @Override
        public Void visit(GameEventCardPhased gameEventCardPhased) {
            return null;
        }

        @Override
        public Void visit(GameEventCardTapped gameEventCardTapped) {
            return null;
        }

        @Override
        public Void visit(GameEventCardStatsChanged event) {
            Card card = event.cards.iterator().next();
            log(String.format("%s stats changed to %d/%d",
                    card.getName(), card.getNetPower(), card.getNetToughness()));
            return null;
        }

        @Override
        public Void visit(GameEventCardCounters gameEventCardCounters) {
            return null;
        }

        @Override
        public Void visit(GameEventZone event) {
            if (event.player != null) {
                log(String.format("%s moved %s from %s to %s",
                        event.player.getName(),
                        event.card.getName(),
                        event.zoneType != null ? event.zoneType.name() : "null",
                        event.mode != null ? event.mode.name() : "null"));
            }
            return null;
        }

        @Override
        public Void visit(GameEventCardForetold gameEventCardForetold) {
            return null;
        }

        @Override
        public Void visit(GameEventCardPlotted gameEventCardPlotted) {
            return null;
        }

        @Override
        public Void visit(GameEventDayTimeChanged gameEventDayTimeChanged) {
            return null;
        }

        @Override
        public Void visit(GameEventDoorChanged gameEventDoorChanged) {
            return null;
        }

        @Override
        public Void visit(GameEventSpellResolved event) {
            log(String.format("Spell resolved: %s", event.spell.getHostCard().getName()));
            return null;
        }

        @Override
        public Void visit(GameEventSpellRemovedFromStack gameEventSpellRemovedFromStack) {
            return null;
        }

        @Override
        public Void visit(GameEventSprocketUpdate gameEventSprocketUpdate) {
            return null;
        }

        @Override
        public Void visit(GameEventSubgameStart gameEventSubgameStart) {
            return null;
        }

        @Override
        public Void visit(GameEventSubgameEnd gameEventSubgameEnd) {
            return null;
        }

        @Override
        public Void visit(GameEventSurveil gameEventSurveil) {
            return null;
        }

        @Override
        public Void visit(GameEventTokenCreated gameEventTokenCreated) {
            return null;
        }

        @Override
        public Void visit(GameEventTurnBegan gameEventTurnBegan) {
            return null;
        }

        @Override
        public Void visit(GameEventTurnEnded gameEventTurnEnded) {
            return null;
        }

        @Override
        public Void visit(GameEventTurnPhase gameEventTurnPhase) {
            return null;
        }

        @Override
        public Void visit(GameEventCombatChanged event) {
            if (config.logCombat) {
                log("Combat state changed");
            }
            return null;
        }

        @Override
        public Void visit(GameEventCombatEnded gameEventCombatEnded) {
            return null;
        }

        @Override
        public Void visit(GameEventCombatUpdate gameEventCombatUpdate) {
            return null;
        }

        @Override
        public Void visit(GameEventPlayerDamaged event) {
            log(String.format("%s took %d damage from %s",
                    event.target.getName(),
                    event.amount,
                    event.source != null ? event.source.getName() : "unknown source"));
            return null;
        }

        @Override
        public Void visit(GameEventPlayerCounters gameEventPlayerCounters) {
            return null;
        }

        @Override
        public Void visit(GameEventPlayerPoisoned gameEventPlayerPoisoned) {
            return null;
        }

        @Override
        public Void visit(GameEventPlayerRadiation gameEventPlayerRadiation) {
            return null;
        }

        @Override
        public Void visit(GameEventPlayerPriority gameEventPlayerPriority) {
            return null;
        }

        @Override
        public Void visit(GameEventPlayerShardsChanged gameEventPlayerShardsChanged) {
            return null;
        }

        @Override
        public Void visit(GameEventPlayerStatsChanged gameEventPlayerStatsChanged) {
            return null;
        }

        @Override
        public Void visit(GameEventRandomLog gameEventRandomLog) {
            return null;
        }

        @Override
        public Void visit(GameEventRollDie gameEventRollDie) {
            return null;
        }

        @Override
        public Void visit(GameEventScry gameEventScry) {
            return null;
        }

        @Override
        public Void visit(GameEventShuffle gameEventShuffle) {
            return null;
        }

        @Override
        public Void visit(GameEventSpeedChanged gameEventSpeedChanged) {
            return null;
        }

        @Override
        public Void visit(GameEventSpellAbilityCast gameEventSpellAbilityCast) {
            return null;
        }

        @Override
        public Void visit(GameEventPlayerLivesChanged event) {
            log(String.format("%s life changed to %d",
                    event.player.getName(),
                    event.newLives));
            return null;
        }

        @Override
        public Void visit(GameEventManaPool gameEventManaPool) {
            return null;
        }

        @Override
        public Void visit(GameEventManaBurn gameEventManaBurn) {
            return null;
        }

        @Override
        public Void visit(GameEventMulligan gameEventMulligan) {
            return null;
        }

        @Override
        public Void visit(GameEventPlayerControl gameEventPlayerControl) {
            return null;
        }

        @Override
        public Void visit(GameEventGameFinished event) {
            log("=== GAME FINISHED ===");
            return null;
        }

        @Override
        public Void visit(GameEventGameOutcome gameEventGameOutcome) {
            return null;
        }

        @Override
        public Void visit(GameEventFlipCoin gameEventFlipCoin) {
            return null;
        }

        @Override
        public Void visit(GameEventGameStarted gameEventGameStarted) {
            return null;
        }

        @Override
        public Void visit(GameEventGameRestarted gameEventGameRestarted) {
            return null;
        }

        @Override
        public Void visit(GameEventLandPlayed gameEventLandPlayed) {
            return null;
        }
    }

    // Enhanced game state printer
    public static class GameStatePrinter {
        private final SimulationConfig config;
        private final PrintStream output;

        public GameStatePrinter(SimulationConfig config, PrintStream output) {
            this.config = config;
            this.output = output;
        }

        public void printFullGameState(Game game) {
            output.println("\n╔════════════════════════════════════════════════════════════════════╗");
            output.println("║                         GAME STATE                                  ║");
            output.println("╚════════════════════════════════════════════════════════════════════╝");

            // Print turn and phase info
            PhaseHandler phaseHandler = game.getPhaseHandler();
            if (phaseHandler != null) {
                output.println(String.format("Turn %d - %s's %s Phase",
                        phaseHandler.getTurn(),
                        phaseHandler.getPlayerTurn().getName(),
                        phaseHandler.getPhase().nameForUi));
            }

            output.println();

            // Print player states
            List<Player> players = game.getPlayers();
            for (int i = 0; i < players.size(); i++) {
                if (config.focusPlayerIndex == -1 || config.focusPlayerIndex == i) {
                    printPlayerState(players.get(i), i == config.focusPlayerIndex);
                }
            }

            // Print stack if not empty
            if (!game.getStack().isEmpty()) {
                printStack(game);
            }

            // Print combat if ongoing
            Combat combat = game.getCombat();
            if (combat != null && !combat.getAttackers().isEmpty()) {
                printCombat(combat);
            }
        }

        private void printPlayerState(Player player, boolean detailed) {
            output.println("┌─────────────────────────────────────────────────────────────────────┐");
            output.println(String.format("│ %-67s │", player.getName()));
            output.println("├─────────────────────────────────────────────────────────────────────┤");

            // Basic info
            output.println(String.format("│ Life: %-3d  Poison: %-2d  Mana: %-40s │",
                    player.getLife(),
                    player.getPoisonCounters(),
                    formatManaPool(player)));

            // Zone counts
            output.println(String.format("│ Library: %-3d  Hand: %-3d  Graveyard: %-3d  Exile: %-3d           │",
                    player.getCardsIn(ZoneType.Library).size(),
                    player.getCardsIn(ZoneType.Hand).size(),
                    player.getCardsIn(ZoneType.Graveyard).size(),
                    player.getCardsIn(ZoneType.Exile).size()));

            output.println("├─────────────────────────────────────────────────────────────────────┤");

            // Battlefield
            output.println("│ BATTLEFIELD:                                                        │");
            printBattlefield(player);

            // Hand (if detailed view or show hidden zones)
            if (detailed || config.showHiddenZones) {
                output.println("├─────────────────────────────────────────────────────────────────────┤");
                output.println("│ HAND:                                                               │");
                printHand(player);
            }

            // Graveyard (always visible)
            if (!player.getCardsIn(ZoneType.Graveyard).isEmpty()) {
                output.println("├─────────────────────────────────────────────────────────────────────┤");
                output.println("│ GRAVEYARD:                                                          │");
                printGraveyard(player);
            }

            output.println("└─────────────────────────────────────────────────────────────────────┘");
            output.println();
        }

        private void printBattlefield(Player player) {
            CardCollectionView battlefield = player.getCardsIn(ZoneType.Battlefield);

            // Group by type
            List<Card> lands = new ArrayList<>();
            List<Card> creatures = new ArrayList<>();
            List<Card> artifacts = new ArrayList<>();
            List<Card> enchantments = new ArrayList<>();
            List<Card> planeswalkers = new ArrayList<>();
            List<Card> others = new ArrayList<>();

            for (Card card : battlefield) {
                if (card.isLand()) lands.add(card);
                else if (card.isCreature()) creatures.add(card);
                else if (card.isArtifact()) artifacts.add(card);
                else if (card.isEnchantment()) enchantments.add(card);
                else if (card.isPlaneswalker()) planeswalkers.add(card);
                else others.add(card);
            }

            // Print each type
            if (!lands.isEmpty()) {
                output.print("│   Lands: ");
                printCardList(lands, 58);
            }

            if (!creatures.isEmpty()) {
                output.print("│   Creatures: ");
                printCreatureList(creatures, 54);
            }

            if (!artifacts.isEmpty()) {
                output.print("│   Artifacts: ");
                printCardList(artifacts, 54);
            }

            if (!enchantments.isEmpty()) {
                output.print("│   Enchantments: ");
                printCardList(enchantments, 51);
            }

            if (!planeswalkers.isEmpty()) {
                output.print("│   Planeswalkers: ");
                printCardList(planeswalkers, 50);
            }

            if (!others.isEmpty()) {
                output.print("│   Other: ");
                printCardList(others, 58);
            }

            if (battlefield.isEmpty()) {
                output.println("│   (empty)                                                           │");
            }
        }

        private void printCreatureList(List<Card> creatures, int maxWidth) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < creatures.size(); i++) {
                Card c = creatures.get(i);
                String creatureStr = String.format("%s (%d/%d%s%s)",
                        c.getName(),
                        c.getNetPower(),
                        c.getNetToughness(),
                        c.isTapped() ? " T" : "",
                        c.isSick() ? " S" : "");

                if (i > 0) sb.append(", ");
                sb.append(creatureStr);
            }

            // Word wrap
            printWrapped(sb.toString(), maxWidth);
        }

        private void printCardList(List<Card> cards, int maxWidth) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cards.size(); i++) {
                Card c = cards.get(i);
                String cardStr = c.getName() + (c.isTapped() ? " (T)" : "");
                if (i > 0) sb.append(", ");
                sb.append(cardStr);
            }
            printWrapped(sb.toString(), maxWidth);
        }

        private void printWrapped(String text, int maxWidth) {
            if (text.length() <= maxWidth) {
                output.println(String.format("%-" + maxWidth + "s │", text));
            } else {
                // Simple word wrapping
                String[] words = text.split(" ");
                StringBuilder line = new StringBuilder();
                for (String word : words) {
                    if (line.length() + word.length() + 1 > maxWidth) {
                        output.println(String.format("%-" + maxWidth + "s │", line.toString()));
                        output.print("│              ");
                        line = new StringBuilder(word);
                    } else {
                        if (line.length() > 0) line.append(" ");
                        line.append(word);
                    }
                }
                if (line.length() > 0) {
                    output.println(String.format("%-" + maxWidth + "s │", line.toString()));
                }
            }
        }

        private void printHand(Player player) {
            CardCollectionView hand = player.getCardsIn(ZoneType.Hand);
            if (hand.isEmpty()) {
                output.println("│   (empty)                                                           │");
                return;
            }

            for (Card card : hand) {
                String cardInfo = String.format("   %s [%s]",
                        card.getName(),
                        card.getManaCost());
                output.println(String.format("│%-69s│", cardInfo));
            }
        }

        private void printGraveyard(Player player) {
            CardCollectionView graveyard = player.getCardsIn(ZoneType.Graveyard);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < graveyard.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(graveyard.get(i).getName());
            }
            output.print("│   ");
            printWrapped(sb.toString(), 65);
        }

        private void printStack(Game game) {
            output.println("┌─────────────────────────────────────────────────────────────────────┐");
            output.println("│ THE STACK:                                                          │");
            output.println("├─────────────────────────────────────────────────────────────────────┤");

            MagicStack stack = game.getStack();
            int index = 1;
            for (SpellAbilityStackInstance sa : stack) {
                String stackEntry = String.format("%d. %s (%s)",
                        index++,
                        sa.getSpellAbility().getHostCard().getName(),
                        sa.getSpellAbility().getControlledByPlayer().getValue());
                output.println(String.format("│ %-67s │", stackEntry));
            }

            output.println("└─────────────────────────────────────────────────────────────────────┘");
            output.println();
        }

        private void printCombat(Combat combat) {
            output.println("┌─────────────────────────────────────────────────────────────────────┐");
            output.println("│ COMBAT:                                                             │");
            output.println("├─────────────────────────────────────────────────────────────────────┤");

            for (Card attacker : combat.getAttackers()) {
                Card blocker = combat.getBlockers(attacker).isEmpty() ? null : combat.getBlockers(attacker).get(0);
                String combatInfo = String.format("%s (%d/%d) -> %s",
                        attacker.getName(),
                        attacker.getNetPower(),
                        attacker.getNetToughness(),
                        blocker != null ? blocker.getName() + " (" + blocker.getNetPower() + "/" + blocker.getNetToughness() + ")" : "unblocked");
                output.println(String.format("│ %-67s │", combatInfo));
            }

            output.println("└─────────────────────────────────────────────────────────────────────┘");
            output.println();
        }

        private String formatManaPool(Player player) {
            // Format mana pool display
            // This would need actual mana pool access from the player
            return ""; // Placeholder
        }
    }

    // Main simulator class with enhanced callbacks and event handling
    public static class MatchSimulator {
        private final SimulationConfig config;
        private final GameEventLogger eventLogger;
        private final GameStatePrinter statePrinter;
        private final PrintStream output;

        // Callback support
        private PreActionCallback preActionCallback;
        private PostActionCallback postActionCallback;
        private DetailedEventHandler eventHandler;

        private int actionCount = 0;
        private long simulationStartTime;

        public MatchSimulator(SimulationConfig config) {
            this(config, System.out);
        }

        public MatchSimulator(SimulationConfig config, PrintStream output) {
            this.config = config;
            this.output = output;
            this.eventLogger = new GameEventLogger(config, output);
            this.statePrinter = new GameStatePrinter(config, output);
            this.simulationStartTime = System.currentTimeMillis();
        }

        public void setPreActionCallback(PreActionCallback callback) {
            this.preActionCallback = callback;
        }

        public void setPostActionCallback(PostActionCallback callback) {
            this.postActionCallback = callback;
        }

        public void setEventHandler(DetailedEventHandler handler) {
            this.eventHandler = handler;
        }

        public void simulate(Game game) {
            output.println("\n🎮 Starting Match Simulation");
            output.println("Mode: " + config.mode);
            if (config.mode == SimulationConfig.Mode.TURNS) {
                output.println("Turns to simulate: " + config.turnsToSimulate);
            }
            output.println("Verbose logging: " + config.verboseLogging);
            output.println();

            // Register event listener
            if (config.logEvents) {
                game.subscribeToEvents(eventLogger);
            }

            // Set up enhanced event listeners if needed
            setupEnhancedEventListeners(game);

            // Initial state
            statePrinter.printFullGameState(game);

            // Simulation loop
            int turnCount = 0;
            boolean gameOver = false;

            while (!gameOver) {
                try {
                    // Check simulation limits
                    if (config.mode == SimulationConfig.Mode.TURNS && turnCount >= config.turnsToSimulate) {
                        output.println("\n📊 Simulation complete: Reached turn limit");
                        break;
                    }

                    if (turnCount >= config.maxTurnsBeforeTimeout) {
                        output.println("\n⏰ Simulation timeout: Maximum turns reached");
                        break;
                    }

                    // Check game over
                    if (game.isGameOver()) {
                        gameOver = true;
                        output.println("\n🏁 Game Over!");
                        printGameResults(game);
                        break;
                    }

                    // Advance game state with callbacks
                    advanceGameStateWithCallbacks(game);

                    // Check for new turn
                    PhaseHandler ph = game.getPhaseHandler();
                    if (ph != null && ph.getPhase() == PhaseType.CLEANUP) {
                        turnCount++;

                        // Print state at end of each turn
                        if (config.verboseLogging) {
                            output.println("\n═══════════════════════════════════════════════════════════════════");
                            output.println("End of Turn " + turnCount);
                            output.println("═══════════════════════════════════════════════════════════════════");
                            statePrinter.printFullGameState(game);
                        }
                    }

                    // Pause between phases if configured
                    if (config.pauseBetweenPhases) {
                        Thread.sleep(500);
                    }

                    // Pause between actions if configured
                    if (config.pauseBetweenActions && actionCount % 5 == 0) {
                        Thread.sleep(100);
                    }

                } catch (Exception e) {
                    output.println("\n❌ Error during simulation: " + e.getMessage());
                    if (config.includeStackTrace) {
                        e.printStackTrace(output);
                    }
                    break;
                }
            }

            // Final state
            output.println("\n📊 Final Game State:");
            statePrinter.printFullGameState(game);
        }

        private void setupEnhancedEventListeners(Game game) {
            // Phase change listener
            if (config.logPhaseChanges && eventHandler != null) {
                game.subscribeToEvents(new IGameEventVisitor<Void>() {
                    @Override
                    public Void visit(GameEventTurnPhase event) {
                        String phase = event.phaseDesc;
                        logWithTimestamp("📍 Phase Changed: " + phase);
                        eventHandler.onPhaseBegin(game, phase);
                        return null;
                    }

                    // Implement other visit methods as needed
                    @Override public Void visit(GameEventAnteCardsSelected e) { return null; }
                    @Override public Void visit(GameEventAttackersDeclared e) { return null; }
                    @Override public Void visit(GameEventBlockersDeclared e) { return null; }
                    @Override public Void visit(GameEventCardDamaged e) { return null; }
                    @Override public Void visit(GameEventCardDestroyed e) { return null; }
                    @Override public Void visit(GameEventCardAttachment e) { return null; }
                    @Override public Void visit(GameEventCardChangeZone e) { return null; }
                    @Override public Void visit(GameEventCardModeChosen e) { return null; }
                    @Override public Void visit(GameEventCardRegenerated e) { return null; }
                    @Override public Void visit(GameEventCardSacrificed e) { return null; }
                    @Override public Void visit(GameEventCardPhased e) { return null; }
                    @Override public Void visit(GameEventCardTapped e) { return null; }
                    @Override public Void visit(GameEventCardStatsChanged e) { return null; }
                    @Override public Void visit(GameEventCardCounters e) { return null; }
                    @Override public Void visit(GameEventZone e) { return null; }
                    @Override public Void visit(GameEventCardForetold e) { return null; }
                    @Override public Void visit(GameEventCardPlotted e) { return null; }
                    @Override public Void visit(GameEventDayTimeChanged e) { return null; }
                    @Override public Void visit(GameEventDoorChanged e) { return null; }
                    @Override public Void visit(GameEventSpellResolved e) { return null; }
                    @Override public Void visit(GameEventSpellRemovedFromStack e) { return null; }
                    @Override public Void visit(GameEventSprocketUpdate e) { return null; }
                    @Override public Void visit(GameEventSubgameStart e) { return null; }
                    @Override public Void visit(GameEventSubgameEnd e) { return null; }
                    @Override public Void visit(GameEventSurveil e) { return null; }
                    @Override public Void visit(GameEventTokenCreated e) { return null; }
                    @Override public Void visit(GameEventTurnBegan e) { return null; }
                    @Override public Void visit(GameEventTurnEnded e) { return null; }
                    @Override public Void visit(GameEventCombatChanged e) { return null; }
                    @Override public Void visit(GameEventCombatEnded e) { return null; }
                    @Override public Void visit(GameEventCombatUpdate e) { return null; }
                    @Override public Void visit(GameEventPlayerDamaged e) { return null; }
                    @Override public Void visit(GameEventPlayerCounters e) { return null; }
                    @Override public Void visit(GameEventPlayerPoisoned e) { return null; }
                    @Override public Void visit(GameEventPlayerRadiation e) { return null; }
                    @Override public Void visit(GameEventPlayerPriority e) { return null; }
                    @Override public Void visit(GameEventPlayerShardsChanged e) { return null; }
                    @Override public Void visit(GameEventPlayerStatsChanged e) { return null; }
                    @Override public Void visit(GameEventRandomLog e) { return null; }
                    @Override public Void visit(GameEventRollDie e) { return null; }
                    @Override public Void visit(GameEventScry e) { return null; }
                    @Override public Void visit(GameEventShuffle e) { return null; }
                    @Override public Void visit(GameEventSpeedChanged e) { return null; }
                    @Override public Void visit(GameEventSpellAbilityCast e) { return null; }
                    @Override public Void visit(GameEventPlayerLivesChanged e) { return null; }
                    @Override public Void visit(GameEventManaPool e) { return null; }
                    @Override public Void visit(GameEventManaBurn e) { return null; }
                    @Override public Void visit(GameEventMulligan e) { return null; }
                    @Override public Void visit(GameEventPlayerControl e) { return null; }
                    @Override public Void visit(GameEventGameFinished e) { return null; }
                    @Override public Void visit(GameEventGameOutcome e) { return null; }
                    @Override public Void visit(GameEventFlipCoin e) { return null; }
                    @Override public Void visit(GameEventGameStarted e) { return null; }
                    @Override public Void visit(GameEventGameRestarted e) { return null; }
                    @Override public Void visit(GameEventLandPlayed e) { return null; }
                });
            }

            // Additional event listeners for detailed logging
            if (config.logCardDraws || config.logLandPlays || config.logSpellsCast) {
                game.subscribeToEvents(new EnhancedEventListener(config, eventHandler));
            }
        }

        private void advanceGameState(Game game) {
            PhaseHandler phaseHandler = game.getPhaseHandler();
            if (phaseHandler == null) return;

            try {
                // Let the AI controllers handle their actions
                Player activePlayer = phaseHandler.getPlayerTurn();
                if (activePlayer != null && activePlayer.getController() instanceof PlayerControllerAi) {
                    // The AI should handle its own actions during the phase
                    // In a real implementation, we'd give the AI time to act
                    phaseHandler.getPhase();
                }
            } catch (Exception e) {
                output.println("Error advancing game state: " + e.getMessage());
            }
        }

        private void advanceGameStateWithCallbacks(Game game) {
            actionCount++;
            String actionDescription = "Action #" + actionCount;

            // Pre-action callback
            if (preActionCallback != null) {
                preActionCallback.onPreAction(game, actionDescription);
            }

            // Log AI decision if enabled
            if (config.logAIDecisions && game.getPhaseHandler().getPriorityPlayer().getController().isAI()) {
                Player ai = game.getPhaseHandler().getPriorityPlayer();
                String decision = "Evaluating board state";
                logWithTimestamp("🤖 " + ai.getName() + " AI: " + decision);
                if (eventHandler != null) {
                    eventHandler.onAIDecision(game, ai, decision);
                }
            }

            // Original advance logic
            advanceGameState(game);

            // Post-action callback
            if (postActionCallback != null) {
                ActionResult result = new ActionResult(true, "Game state advanced");
                postActionCallback.onPostAction(game, actionDescription, result);
            }
        }

        private void logWithTimestamp(String message) {
            if (config.includeTimestamps) {
                String timestamp = config.timestampFormat.format(new Date());
                output.println("[" + timestamp + "] " + message);
            } else {
                output.println(message);
            }
        }

        private void printGameResults(Game game) {
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

            output.println("\nFinal Stats:");
            for (Player p : game.getPlayers()) {
                output.println(String.format("  %s: Life=%d, Cards in Library=%d, Lost=%s",
                        p.getName(),
                        p.getLife(),
                        p.getCardsIn(ZoneType.Library).size(),
                        p.hasLost()));
            }

            // Additional timing information
            long duration = System.currentTimeMillis() - simulationStartTime;
            output.println("\nSimulation Duration: " + duration + "ms");
            output.println("Total Actions: " + actionCount);
        }
    }

    // Enhanced event listener for detailed event tracking
    public static class EnhancedEventListener implements IGameEventVisitor<Void> {
        private final SimulationConfig config;
        private final DetailedEventHandler handler;

        public EnhancedEventListener(SimulationConfig config, DetailedEventHandler handler) {
            this.config = config;
            this.handler = handler;
        }

        @Override
        public Void visit(GameEventCardChangeZone event) {
            if (config != null && handler != null) {
                // Card draw events
                if (config.logCardDraws &&
                        event.to.is(ZoneType.Hand)  &&
                        event.from.is(ZoneType.Library)) {
                    handler.onCardDrawn(null, event.card.getController(), event.card);
                }
            }
            return null;
        }

        @Override
        public Void visit(GameEventLandPlayed event) {
            if (config != null && config.logLandPlays && handler != null) {
                handler.onLandPlayed(null, event.player, event.land);
            }
            return null;
        }

        @Override
        public Void visit(GameEventSpellAbilityCast event) {
            if (config != null && config.logSpellsCast && handler != null) {
                handler.onSpellCast(null, event.si.getActivatingPlayer(), event.sa);
            }
            return null;
        }

        // Implement all other required visitor methods
        @Override public Void visit(GameEventAnteCardsSelected e) { return null; }
        @Override public Void visit(GameEventAttackersDeclared e) { return null; }
        @Override public Void visit(GameEventBlockersDeclared e) { return null; }
        @Override public Void visit(GameEventCardDamaged e) { return null; }
        @Override public Void visit(GameEventCardDestroyed e) { return null; }
        @Override public Void visit(GameEventCardAttachment e) { return null; }
        @Override public Void visit(GameEventCardModeChosen e) { return null; }
        @Override public Void visit(GameEventCardRegenerated e) { return null; }
        @Override public Void visit(GameEventCardSacrificed e) { return null; }
        @Override public Void visit(GameEventCardPhased e) { return null; }
        @Override public Void visit(GameEventCardTapped e) { return null; }
        @Override public Void visit(GameEventCardStatsChanged e) { return null; }
        @Override public Void visit(GameEventCardCounters e) { return null; }
        @Override public Void visit(GameEventZone e) { return null; }
        @Override public Void visit(GameEventCardForetold e) { return null; }
        @Override public Void visit(GameEventCardPlotted e) { return null; }
        @Override public Void visit(GameEventDayTimeChanged e) { return null; }
        @Override public Void visit(GameEventDoorChanged e) { return null; }
        @Override public Void visit(GameEventSpellResolved e) { return null; }
        @Override public Void visit(GameEventSpellRemovedFromStack e) { return null; }
        @Override public Void visit(GameEventSprocketUpdate e) { return null; }
        @Override public Void visit(GameEventSubgameStart e) { return null; }
        @Override public Void visit(GameEventSubgameEnd e) { return null; }
        @Override public Void visit(GameEventSurveil e) { return null; }
        @Override public Void visit(GameEventTokenCreated e) { return null; }
        @Override public Void visit(GameEventTurnBegan e) { return null; }
        @Override public Void visit(GameEventTurnEnded e) { return null; }
        @Override public Void visit(GameEventTurnPhase e) { return null; }
        @Override public Void visit(GameEventCombatChanged e) { return null; }
        @Override public Void visit(GameEventCombatEnded e) { return null; }
        @Override public Void visit(GameEventCombatUpdate e) { return null; }
        @Override public Void visit(GameEventPlayerDamaged e) { return null; }
        @Override public Void visit(GameEventPlayerCounters e) { return null; }
        @Override public Void visit(GameEventPlayerPoisoned e) { return null; }
        @Override public Void visit(GameEventPlayerRadiation e) { return null; }
        @Override public Void visit(GameEventPlayerPriority e) { return null; }
        @Override public Void visit(GameEventPlayerShardsChanged e) { return null; }
        @Override public Void visit(GameEventPlayerStatsChanged e) { return null; }
        @Override public Void visit(GameEventRandomLog e) { return null; }
        @Override public Void visit(GameEventRollDie e) { return null; }
        @Override public Void visit(GameEventScry e) { return null; }
        @Override public Void visit(GameEventShuffle e) { return null; }
        @Override public Void visit(GameEventSpeedChanged e) { return null; }
        @Override public Void visit(GameEventPlayerLivesChanged e) { return null; }
        @Override public Void visit(GameEventManaPool e) { return null; }
        @Override public Void visit(GameEventManaBurn e) { return null; }
        @Override public Void visit(GameEventMulligan e) { return null; }
        @Override public Void visit(GameEventPlayerControl e) { return null; }
        @Override public Void visit(GameEventGameFinished e) { return null; }
        @Override public Void visit(GameEventGameOutcome e) { return null; }
        @Override public Void visit(GameEventFlipCoin e) { return null; }
        @Override public Void visit(GameEventGameStarted e) { return null; }
        @Override public Void visit(GameEventGameRestarted e) { return null; }
    }
}