package com.example.research.simulation;

import com.example.research.mtg_commons;
import com.example.research.core.GameState;
import forge.game.*;
import forge.game.combat.Combat;
import forge.game.phase.*;
import forge.game.player.Player;
import forge.game.card.Card;
import forge.game.zone.ZoneType;
import forge.game.event.*;
import forge.game.spellability.SpellAbility;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * SimulationEngine - Enhanced simulation framework for MTG Forge
 *
 * Provides comprehensive game simulation capabilities including:
 * - Turn-based progression
 * - Event tracking and logging
 * - Performance monitoring
 * - Replay generation
 */
public class SimulationEngine {

    // Core components
    private SimulationConfig config;
    private SimulationState state;
    private SimulationStats stats;
    private EventLogger eventLogger;
    private StateRecorder stateRecorder;

    // Output handling
    private PrintStream output;
    private SimpleDateFormat timestampFormat;

    // Callbacks and listeners
    private BiConsumer<Game, String> preActionCallback;
    private BiConsumer<Game, String> postActionCallback;
    private List<SimulationListener> listeners;

    // Control flags
    private volatile boolean enabled = false;
    private volatile boolean paused = false;
    private volatile boolean stopRequested = false;

    // Thread management
    private ExecutorService executor;
    private Future<?> simulationTask;

    /**
     * Constructor with default output
     */
    public SimulationEngine(SimulationConfig config) {
        this(config, System.out);
    }

    /**
     * Constructor with custom output
     */
    public SimulationEngine(SimulationConfig config, PrintStream output) {
        this.config = config;
        this.output = output;
        this.state = new SimulationState();
        this.stats = new SimulationStats();
        this.eventLogger = new EventLogger();
        this.stateRecorder = new StateRecorder();
        this.listeners = new ArrayList<>();
        this.timestampFormat = new SimpleDateFormat(config.timestampFormat);
        this.executor = Executors.newSingleThreadExecutor();

        config.validate();
    }

    /**
     * Configure simulation with new settings
     */
    public void configure(SimulationConfig newConfig) {
        this.config = newConfig.copy();
        config.validate();
        this.timestampFormat = new SimpleDateFormat(config.timestampFormat);
    }

    /**
     * Enable simulation
     */
    public void enable() {
        enabled = true;
        log("🔬 Simulation enabled");
    }

    /**
     * Disable simulation
     */
    public void disable() {
        enabled = false;
        stopRequested = true;
        log("🔬 Simulation disabled");
    }

    /**
     * Update simulation state
     */
    public void update(Game game, float deltaTime) {
        if (!enabled || game == null) return;

        state.update(game, deltaTime);

        // Check if we should start simulation
        if (shouldStartSimulation(game)) {
            startSimulation(game);
        }
    }

    /**
     * Start simulation based on configuration
     */
    private void startSimulation(Game game) {
        if (simulationTask != null && !simulationTask.isDone()) {
            return; // Already running
        }

        simulationTask = executor.submit(() -> runSimulation(game));
    }

    /**
     * Main simulation execution
     */
    private void runSimulation(Game game) {
        log("\n" + mtg_commons.CONSOLE_SEPARATOR);
        log("🎮 Starting Simulation - Mode: " + config.mode);
        log(mtg_commons.CONSOLE_SEPARATOR);

        stats.reset();
        stats.startTime = System.currentTimeMillis();

        try {
            switch (config.mode) {
                case TURNS:
                    simulateTurns(game, config.turnsToSimulate);
                    break;

                case FULL_MATCH:
                    simulateFullMatch(game);
                    break;

                case UNTIL_CONDITION:
                    simulateUntilCondition(game);
                    break;

                case INTERACTIVE:
                    simulateInteractive(game);
                    break;

                default:
                    log("⚠️  No simulation mode selected");
            }

        } catch (Exception e) {
            logError("Simulation error", e);
        } finally {
            stats.endTime = System.currentTimeMillis();
            logSimulationComplete();
        }
    }

    /**
     * Simulate specific number of turns
     */
    private void simulateTurns(Game game, int numTurns) {
        log("📋 Simulating " + numTurns + " turns...");

        int startTurn = game.getPhaseHandler().getTurn();
        int targetTurn = startTurn + numTurns;

        while (!stopRequested && !game.isGameOver() &&
                game.getPhaseHandler().getTurn() < targetTurn) {

            simulateSingleTurn(game);
            stats.turnsSimulated++;

            if (config.pauseBetweenPhases) {
                pause(config.pauseDurationMs);
            }
        }
    }

    /**
     * Simulate until game completion
     */
    private void simulateFullMatch(Game game) {
        log("🏁 Simulating full match...");

        int turnCount = 0;
        while (!stopRequested && !game.isGameOver() &&
                turnCount < config.maxTurnsBeforeTimeout) {

            simulateSingleTurn(game);
            stats.turnsSimulated++;
            turnCount++;

            if (config.pauseBetweenPhases) {
                pause(config.pauseDurationMs);
            }
        }

        if (turnCount >= config.maxTurnsBeforeTimeout) {
            log("⏱️  Simulation timeout after " + turnCount + " turns");
        }
    }

    /**
     * Simulate until stop condition is met
     */
    private void simulateUntilCondition(Game game) {
        if (config.stopCondition == null) {
            log("⚠️  No stop condition defined");
            return;
        }

        log("🎯 Simulating until: " + config.stopCondition.getDescription());

        while (!stopRequested && !game.isGameOver() &&
                !config.stopCondition.shouldStop(game)) {

            simulateSingleTurn(game);
            stats.turnsSimulated++;

            if (config.pauseBetweenPhases) {
                pause(config.pauseDurationMs);
            }
        }
    }

    /**
     * Interactive simulation with pauses
     */
    private void simulateInteractive(Game game) {
        log("🎮 Interactive simulation started (press Enter to advance)");

        Scanner scanner = new Scanner(System.in);

        while (!stopRequested && !game.isGameOver()) {
            simulateSinglePhase(game);

            log("\nPress Enter to continue, 'q' to quit...");
            String input = scanner.nextLine();

            if ("q".equalsIgnoreCase(input)) {
                break;
            }
        }
    }

    /**
     * Simulate a single turn
     */
    private void simulateSingleTurn(Game game) {
        int currentTurn = game.getPhaseHandler().getTurn();
        Player activePlayer = game.getPhaseHandler().getPlayerTurn();

        if (config.logPhaseChanges) {
            log("\n" + mtg_commons.CONSOLE_SUBSEPARATOR);
            log(String.format("TURN %d - %s", currentTurn, activePlayer.getName()));
            log(mtg_commons.CONSOLE_SUBSEPARATOR);
        }

        // Simulate all phases of the turn
        PhaseHandler phaseHandler = game.getPhaseHandler();
        PhaseType startPhase = phaseHandler.getPhase();

        do {
            simulateSinglePhase(game);

            // Safety check for infinite loops
            if (stats.actionsThisTurn > config.maxActionsPerTurn) {
                log("⚠️  Max actions per turn exceeded, advancing phase");
                phaseHandler.endTurnByEffect();
            }

        } while (phaseHandler.getTurn() == currentTurn && !stopRequested);

        stats.actionsThisTurn = 0;
    }

    /**
     * Simulate a single phase
     */
    private void simulateSinglePhase(Game game) {
        PhaseHandler phaseHandler = game.getPhaseHandler();
        PhaseType phase = phaseHandler.getPhase();
        Player activePlayer = phaseHandler.getPlayerTurn();

        if (config.logPhaseChanges) {
            logPhaseChange(phase, activePlayer);
        }

        // Process phase-specific actions
        switch (phase) {
            case UNTAP:
                processUntapPhase(game);
                break;

            case UPKEEP:
                processUpkeepPhase(game);
                break;

            case DRAW:
                processDrawPhase(game);
                break;

            case MAIN1:
            case MAIN2:
                processMainPhase(game);
                break;

            case COMBAT_BEGIN:
            case COMBAT_DECLARE_ATTACKERS:
            case COMBAT_DECLARE_BLOCKERS:
            case COMBAT_FIRST_STRIKE_DAMAGE:
            case COMBAT_DAMAGE:
            case COMBAT_END:
                processCombatPhase(game, phase);
                break;

            case END_OF_TURN:
                processEndPhase(game);
                break;

            case CLEANUP:
                processCleanupPhase(game);
                break;
        }

        // Record state if needed
        if (config.saveGameStates && shouldSaveState(game)) {
            stateRecorder.recordState(game);
        }
    }

    // Phase processing methods

    private void processUntapPhase(Game game) {
        if (config.verboseLogging) {
            log("  ♻️  Untapping permanents...");
        }
    }

    private void processUpkeepPhase(Game game) {
        if (config.verboseLogging) {
            log("  ⏰ Processing upkeep triggers...");
        }
    }

    private void processDrawPhase(Game game) {
        if (config.logCardDraws) {
            Player activePlayer = game.getPhaseHandler().getPlayerTurn();
            log("  🎴 " + activePlayer.getName() + " draws a card");
        }
    }

    private void processMainPhase(Game game) {
        if (config.verboseLogging) {
            log("  🎯 Main phase - processing actions...");
        }

        // Let AI make decisions
        Player activePlayer = game.getPhaseHandler().getPlayerTurn();
        if (activePlayer.getController().isAI()) {
            processAIActions(game, activePlayer);
        }
    }

    private void processCombatPhase(Game game, PhaseType phase) {
        if (!config.logCombat) return;

        switch (phase) {
            case COMBAT_DECLARE_ATTACKERS:
                logAttackers(game);
                break;

            case COMBAT_DECLARE_BLOCKERS:
                logBlockers(game);
                break;

            case COMBAT_DAMAGE:
                logCombatDamage(game);
                break;
        }
    }

    private void processEndPhase(Game game) {
        if (config.verboseLogging) {
            log("  🔚 End of turn triggers...");
        }
    }

    private void processCleanupPhase(Game game) {
        if (config.verboseLogging) {
            log("  🧹 Cleanup phase...");
        }
    }

    /**
     * Process AI actions during main phase
     */
    private void processAIActions(Game game, Player aiPlayer) {
        if (config.logAIDecisions) {
            log("  🤖 " + aiPlayer.getName() + " is thinking...");
        }

        // This is where the AI controller would make decisions
        // The actual AI logic is handled by Forge's PlayerControllerAi

        stats.aiDecisions++;
    }

    // Logging methods

    private void log(String message) {
        if (config.includeTimestamps) {
            output.println("[" + timestampFormat.format(new Date()) + "] " + message);
        } else {
            output.println(message);
        }

        // Notify listeners
        for (SimulationListener listener : listeners) {
            listener.onLogMessage(message);
        }
    }

    private void logError(String message, Exception e) {
        log("❌ " + message + ": " + e.getMessage());
        if (config.includeStackTrace) {
            e.printStackTrace(output);
        }
    }

    private void logPhaseChange(PhaseType phase, Player activePlayer) {
        String phaseIcon = getPhaseIcon(phase);
        log(String.format("  %s %s - %s", phaseIcon, phase, activePlayer.getName()));
    }

    private void logAttackers(Game game) {
        Combat combat = game.getCombat();
        if (combat != null && combat.getAttackers() != null) {
            log("  ⚔️  Attackers declared:");
            for (Card attacker : combat.getAttackers()) {
                log("     - " + attacker.getName());
            }
        }
    }

    private void logBlockers(Game game) {
        Combat combat = game.getCombat();
        if (combat != null && combat.getAllBlockers() != null) {
            log("  🛡️  Blockers declared:");
            for (Card blocker : combat.getAllBlockers()) {
                Card blocked = combat.getAttackersBlockedBy(blocker).get(0);
                log("     - " + blocker.getName() + " blocks " + blocked.getName());
            }
        }
    }

    private void logCombatDamage(Game game) {
        log("  💥 Combat damage dealt");
    }

    private void logSimulationComplete() {
        long duration = stats.endTime - stats.startTime;

        log("\n" + mtg_commons.CONSOLE_SEPARATOR);
        log("📊 Simulation Complete");
        log(mtg_commons.CONSOLE_SEPARATOR);
        log("  Duration: " + (duration / 1000.0) + " seconds");
        log("  Turns simulated: " + stats.turnsSimulated);
        log("  Total actions: " + stats.totalActions);
        log("  AI decisions: " + stats.aiDecisions);

        if (config.trackStatistics) {
            log("\n📈 Additional Statistics:");
            log("  Average actions/turn: " +
                    (stats.turnsSimulated > 0 ? stats.totalActions / stats.turnsSimulated : 0));
            log("  Simulation speed: " +
                    (duration > 0 ? (stats.turnsSimulated * 1000.0 / duration) : 0) + " turns/sec");
        }
    }

    private String getPhaseIcon(PhaseType phase) {
        switch (phase) {
            case UNTAP: return "♻️";
            case UPKEEP: return "⏰";
            case DRAW: return "🎴";
            case MAIN1:
            case MAIN2: return "🎯";
            case COMBAT_BEGIN:
            case COMBAT_DECLARE_ATTACKERS:
            case COMBAT_DECLARE_BLOCKERS:
            case COMBAT_FIRST_STRIKE_DAMAGE:
            case COMBAT_DAMAGE:
            case COMBAT_END: return "⚔️";
            case END_OF_TURN: return "🔚";
            case CLEANUP: return "🧹";
            default: return "📍";
        }
    }

    // Utility methods

    private boolean shouldStartSimulation(Game game) {
        return config.mode != SimulationConfig.Mode.NONE &&
                !state.simulationStarted &&
                game.getPhaseHandler() != null;
    }

    private boolean shouldSaveState(Game game) {
        int turn = game.getPhaseHandler().getTurn();
        return turn % config.stateSnapshotFrequency == 0;
    }

    private void pause(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Stop simulation
     */
    public void stop() {
        stopRequested = true;
        if (simulationTask != null) {
            simulationTask.cancel(true);
        }
    }

    /**
     * Shutdown simulation engine
     */
    public void shutdown() {
        stop();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    // Getters and setters

    public boolean isEnabled() { return enabled; }
    public boolean isPaused() { return paused; }
    public SimulationStats getStats() { return stats; }
    public SimulationConfig getConfig() { return config; }

    public void setPreActionCallback(BiConsumer<Game, String> callback) {
        this.preActionCallback = callback;
    }

    public void setPostActionCallback(BiConsumer<Game, String> callback) {
        this.postActionCallback = callback;
    }

    public void addListener(SimulationListener listener) {
        listeners.add(listener);
    }

    public void removeListener(SimulationListener listener) {
        listeners.remove(listener);
    }

    /**
     * Simulation state tracking
     */
    private static class SimulationState {
        boolean simulationStarted = false;
        int lastProcessedTurn = 0;
        PhaseType lastProcessedPhase = null;

        void update(Game game, float deltaTime) {
            if (game.getPhaseHandler() != null) {
                int currentTurn = game.getPhaseHandler().getTurn();
                if (currentTurn > lastProcessedTurn) {
                    lastProcessedTurn = currentTurn;
                    simulationStarted = true;
                }
            }
        }
    }

    /**
     * Simulation statistics
     */
    public static class SimulationStats {
        public long startTime;
        public long endTime;
        public int turnsSimulated;
        public int totalActions;
        public int actionsThisTurn;
        public int aiDecisions;
        public int spellsCast;
        public int combatsResolved;
        public int stateChanges;

        void reset() {
            startTime = 0;
            endTime = 0;
            turnsSimulated = 0;
            totalActions = 0;
            actionsThisTurn = 0;
            aiDecisions = 0;
            spellsCast = 0;
            combatsResolved = 0;
            stateChanges = 0;
        }
    }

    /**
     * Event logger for detailed tracking
     */
    private class EventLogger {
        private final List<SimulationEvent> events = new ArrayList<>();

        void logEvent(String type, String description, Game game) {
            if (!config.logEvents) return;

            SimulationEvent event = new SimulationEvent();
            event.timestamp = System.currentTimeMillis();
            event.turn = game.getPhaseHandler().getTurn();
            event.phase = game.getPhaseHandler().getPhase();
            event.type = type;
            event.description = description;

            events.add(event);
            stats.totalActions++;
            stats.actionsThisTurn++;
        }
    }

    /**
     * State recorder for replay generation
     */
    private class StateRecorder {
        private final List<GameStateSnapshot> snapshots = new ArrayList<>();

        void recordState(Game game) {
            if (!config.saveGameStates) return;

            GameStateSnapshot snapshot = new GameStateSnapshot();
            snapshot.timestamp = System.currentTimeMillis();
            snapshot.turn = game.getPhaseHandler().getTurn();
            snapshot.phase = game.getPhaseHandler().getPhase();

            // Record player states
            for (Player player : game.getPlayers()) {
                PlayerSnapshot ps = new PlayerSnapshot();
                ps.name = player.getName();
                ps.life = player.getLife();
                ps.handSize = player.getCardsIn(ZoneType.Hand).size();
                ps.permanents = player.getCardsIn(ZoneType.Battlefield).size();
                snapshot.playerSnapshots.add(ps);
            }

            snapshots.add(snapshot);
        }

        List<GameStateSnapshot> getSnapshots() {
            return Collections.unmodifiableList(snapshots);
        }
    }

    /**
     * Simulation event
     */
    private static class SimulationEvent {
        long timestamp;
        int turn;
        PhaseType phase;
        String type;
        String description;
    }

    /**
     * Game state snapshot
     */
    private static class GameStateSnapshot {
        long timestamp;
        int turn;
        PhaseType phase;
        List<PlayerSnapshot> playerSnapshots = new ArrayList<>();
    }

    /**
     * Player snapshot
     */
    private static class PlayerSnapshot {
        String name;
        int life;
        int handSize;
        int permanents;
    }

    /**
     * Simulation listener interface
     */
    public interface SimulationListener {
        void onLogMessage(String message);
        void onPhaseChange(PhaseType phase, Player activePlayer);
        void onActionExecuted(String action);
        void onSimulationComplete(SimulationStats stats);
    }
}