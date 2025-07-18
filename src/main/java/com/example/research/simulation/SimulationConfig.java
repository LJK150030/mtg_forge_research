package com.example.research.simulation;

import com.example.research.mtg_commons;

/**
 * SimulationConfig - Configuration for MTG game simulation
 *
 * Provides comprehensive configuration options for controlling
 * simulation behavior, logging, and analysis features.
 */
public class SimulationConfig {

    /**
     * Simulation execution modes
     */
    public enum Mode {
        NONE("No simulation"),
        TURNS("Simulate specific number of turns"),
        FULL_MATCH("Simulate until game completion"),
        UNTIL_CONDITION("Simulate until condition met"),
        INTERACTIVE("Step-by-step with pauses");

        private final String description;

        Mode(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    // Core simulation settings
    public Mode mode = Mode.NONE;
    public int turnsToSimulate = 10;
    public int maxTurnsBeforeTimeout = 100;
    public boolean pauseBetweenPhases = false;
    public boolean pauseBetweenActions = false;
    public int pauseDurationMs = 1000;

    // Logging configuration
    public boolean verboseLogging = false;
    public boolean logEvents = true;
    public boolean logPhaseChanges = true;
    public boolean logCombat = true;
    public boolean logManaUsage = true;
    public boolean logStackOperations = true;
    public boolean logCardDraws = true;
    public boolean logLandPlays = true;
    public boolean logSpellsCast = true;
    public boolean logAbilityActivations = true;
    public boolean logTriggers = true;
    public boolean logStateBasedActions = true;
    public boolean logPriorityPasses = true;
    public boolean logAIDecisions = true;

    // Display configuration
    public boolean showHiddenZones = false;
    public int focusPlayerIndex = -1; // -1 for all players
    public boolean showManaPool = true;
    public boolean showLifeTotals = true;
    public boolean showHandCounts = true;
    public boolean showLibraryCounts = true;
    public boolean showGraveyardCounts = true;

    // Output formatting
    public boolean detailedActionFormatter = false;
    public boolean includeTimestamps = true;
    public boolean includeStackTrace = false;
    public boolean colorizeOutput = true;
    public String timestampFormat = "HH:mm:ss.SSS";

    // Performance settings
    public int actionDelayMs = 0;
    public int maxActionsPerTurn = 1000;
    public boolean skipRedundantLogging = true;

    // Analysis features
    public boolean trackStatistics = true;
    public boolean generateReplay = false;
    public boolean saveGameStates = false;
    public int stateSnapshotFrequency = 1; // Every N turns

    // Condition-based simulation
    public StopCondition stopCondition = null;

    /**
     * Create default configuration
     */
    public static SimulationConfig createDefault() {
        return new SimulationConfig();
    }

    /**
     * Create quick simulation config (minimal logging)
     */
    public static SimulationConfig createQuick() {
        SimulationConfig config = new SimulationConfig();
        config.mode = Mode.TURNS;
        config.turnsToSimulate = 5;
        config.verboseLogging = false;
        config.logEvents = false;
        config.logPhaseChanges = false;
        config.logCombat = true;
        config.skipRedundantLogging = true;
        return config;
    }

    /**
     * Create detailed simulation config (full logging)
     */
    public static SimulationConfig createDetailed() {
        SimulationConfig config = new SimulationConfig();
        config.mode = Mode.FULL_MATCH;
        config.verboseLogging = true;
        config.logEvents = true;
        config.logPhaseChanges = true;
        config.logCombat = true;
        config.logManaUsage = true;
        config.logStackOperations = true;
        config.showHiddenZones = true;
        config.detailedActionFormatter = true;
        return config;
    }

    /**
     * Create interactive simulation config
     */
    public static SimulationConfig createInteractive() {
        SimulationConfig config = new SimulationConfig();
        config.mode = Mode.INTERACTIVE;
        config.pauseBetweenPhases = true;
        config.pauseBetweenActions = true;
        config.pauseDurationMs = 2000;
        config.verboseLogging = true;
        config.showHiddenZones = true;
        return config;
    }

    /**
     * Create analysis-focused config
     */
    public static SimulationConfig createAnalysis() {
        SimulationConfig config = new SimulationConfig();
        config.mode = Mode.FULL_MATCH;
        config.trackStatistics = true;
        config.generateReplay = true;
        config.saveGameStates = true;
        config.stateSnapshotFrequency = 1;
        config.logAIDecisions = true;
        return config;
    }

    /**
     * Builder pattern for fluent configuration
     */
    public SimulationConfig withMode(Mode mode) {
        this.mode = mode;
        return this;
    }

    public SimulationConfig withTurns(int turns) {
        this.turnsToSimulate = turns;
        return this;
    }

    public SimulationConfig withVerboseLogging(boolean verbose) {
        this.verboseLogging = verbose;
        return this;
    }

    public SimulationConfig withFocusPlayer(int playerIndex) {
        this.focusPlayerIndex = playerIndex;
        return this;
    }

    public SimulationConfig withStopCondition(StopCondition condition) {
        this.stopCondition = condition;
        return this;
    }

    /**
     * Enable all logging options
     */
    public SimulationConfig enableAllLogging() {
        this.verboseLogging = true;
        this.logEvents = true;
        this.logPhaseChanges = true;
        this.logCombat = true;
        this.logManaUsage = true;
        this.logStackOperations = true;
        this.logCardDraws = true;
        this.logLandPlays = true;
        this.logSpellsCast = true;
        this.logAbilityActivations = true;
        this.logTriggers = true;
        this.logStateBasedActions = true;
        this.logPriorityPasses = true;
        this.logAIDecisions = true;
        return this;
    }

    /**
     * Disable all logging options
     */
    public SimulationConfig disableAllLogging() {
        this.verboseLogging = false;
        this.logEvents = false;
        this.logPhaseChanges = false;
        this.logCombat = false;
        this.logManaUsage = false;
        this.logStackOperations = false;
        this.logCardDraws = false;
        this.logLandPlays = false;
        this.logSpellsCast = false;
        this.logAbilityActivations = false;
        this.logTriggers = false;
        this.logStateBasedActions = false;
        this.logPriorityPasses = false;
        this.logAIDecisions = false;
        return this;
    }

    /**
     * Validate configuration
     */
    public void validate() {
        if (mode == Mode.TURNS && turnsToSimulate <= 0) {
            throw new IllegalStateException("TURNS mode requires positive turnsToSimulate");
        }

        if (maxTurnsBeforeTimeout <= 0) {
            maxTurnsBeforeTimeout = mtg_commons.MAX_SIMULATION_TURNS;
        }

        if (focusPlayerIndex < -1) {
            focusPlayerIndex = -1;
        }
    }

    /**
     * Clone configuration
     */
    public SimulationConfig copy() {
        SimulationConfig copy = new SimulationConfig();

        // Copy all fields
        copy.mode = this.mode;
        copy.turnsToSimulate = this.turnsToSimulate;
        copy.maxTurnsBeforeTimeout = this.maxTurnsBeforeTimeout;
        copy.pauseBetweenPhases = this.pauseBetweenPhases;
        copy.pauseBetweenActions = this.pauseBetweenActions;
        copy.pauseDurationMs = this.pauseDurationMs;

        copy.verboseLogging = this.verboseLogging;
        copy.logEvents = this.logEvents;
        copy.logPhaseChanges = this.logPhaseChanges;
        copy.logCombat = this.logCombat;
        copy.logManaUsage = this.logManaUsage;
        copy.logStackOperations = this.logStackOperations;
        copy.logCardDraws = this.logCardDraws;
        copy.logLandPlays = this.logLandPlays;
        copy.logSpellsCast = this.logSpellsCast;
        copy.logAbilityActivations = this.logAbilityActivations;
        copy.logTriggers = this.logTriggers;
        copy.logStateBasedActions = this.logStateBasedActions;
        copy.logPriorityPasses = this.logPriorityPasses;
        copy.logAIDecisions = this.logAIDecisions;

        copy.showHiddenZones = this.showHiddenZones;
        copy.focusPlayerIndex = this.focusPlayerIndex;
        copy.showManaPool = this.showManaPool;
        copy.showLifeTotals = this.showLifeTotals;
        copy.showHandCounts = this.showHandCounts;
        copy.showLibraryCounts = this.showLibraryCounts;
        copy.showGraveyardCounts = this.showGraveyardCounts;

        copy.detailedActionFormatter = this.detailedActionFormatter;
        copy.includeTimestamps = this.includeTimestamps;
        copy.includeStackTrace = this.includeStackTrace;
        copy.colorizeOutput = this.colorizeOutput;
        copy.timestampFormat = this.timestampFormat;

        copy.actionDelayMs = this.actionDelayMs;
        copy.maxActionsPerTurn = this.maxActionsPerTurn;
        copy.skipRedundantLogging = this.skipRedundantLogging;

        copy.trackStatistics = this.trackStatistics;
        copy.generateReplay = this.generateReplay;
        copy.saveGameStates = this.saveGameStates;
        copy.stateSnapshotFrequency = this.stateSnapshotFrequency;

        copy.stopCondition = this.stopCondition;

        return copy;
    }

    @Override
    public String toString() {
        return "SimulationConfig{" +
                "mode=" + mode +
                ", turns=" + turnsToSimulate +
                ", verbose=" + verboseLogging +
                ", focus=" + (focusPlayerIndex == -1 ? "all" : "player" + focusPlayerIndex) +
                '}';
    }

    /**
     * Stop condition interface for conditional simulation
     */
    public interface StopCondition {
        boolean shouldStop(forge.game.Game game);
        String getDescription();
    }

    /**
     * Common stop conditions
     */
    public static class StopConditions {

        public static StopCondition lifeThreshold(final int threshold) {
            return new StopCondition() {
                @Override
                public boolean shouldStop(forge.game.Game game) {
                    return game.getPlayers().stream()
                            .anyMatch(p -> p.getLife() <= threshold);
                }

                @Override
                public String getDescription() {
                    return "Stop when any player's life <= " + threshold;
                }
            };
        }

        public static StopCondition turnCount(final int maxTurns) {
            return new StopCondition() {
                @Override
                public boolean shouldStop(forge.game.Game game) {
                    return game.getPhaseHandler().getTurn() >= maxTurns;
                }

                @Override
                public String getDescription() {
                    return "Stop after turn " + maxTurns;
                }
            };
        }

        public static StopCondition creatureCount(final int count) {
            return new StopCondition() {
                @Override
                public boolean shouldStop(forge.game.Game game) {
                    return game.getPlayers().stream()
                            .anyMatch(p -> p.getCreaturesInPlay().size() >= count);
                }

                @Override
                public String getDescription() {
                    return "Stop when any player has " + count + "+ creatures";
                }
            };
        }

        public static StopCondition gameOver() {
            return new StopCondition() {
                @Override
                public boolean shouldStop(forge.game.Game game) {
                    return game.isGameOver();
                }

                @Override
                public String getDescription() {
                    return "Stop when game ends";
                }
            };
        }
    }
}