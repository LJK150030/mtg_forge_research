package com.example.research.core;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import java.util.*;

/**
 * GameState - Tracks and manages the current state of the MTG game
 *
 * Provides utilities for state queries, history tracking, and state transitions
 */
public class GameState {

    // State tracking
    private StateSnapshot currentState;
    private List<StateSnapshot> stateHistory;
    private Map<String, Object> metadata;

    // Performance metrics
    private long lastUpdateTime;
    private int updateCount;
    private float averageUpdateTime;

    // State change tracking
    private List<StateChange> recentChanges;
    private static final int MAX_RECENT_CHANGES = 50;

    public GameState() {
        this.stateHistory = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.recentChanges = new LinkedList<>();
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Update game state from Forge game instance
     */
    public void update(Game game, float deltaTime) {
        if (game == null) return;

        long startTime = System.currentTimeMillis();

        // Create new state snapshot
        StateSnapshot newState = createSnapshot(game);

        // Detect changes
        if (currentState != null) {
            detectChanges(currentState, newState);
        }

        // Update state
        currentState = newState;
        stateHistory.add(newState);

        // Limit history size
        if (stateHistory.size() > 100) {
            stateHistory.remove(0);
        }

        // Update metrics
        updateCount++;
        long updateTime = System.currentTimeMillis() - startTime;
        averageUpdateTime = (averageUpdateTime * (updateCount - 1) + updateTime) / updateCount;
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Create a snapshot of the current game state
     */
    private StateSnapshot createSnapshot(Game game) {
        StateSnapshot snapshot = new StateSnapshot();

        snapshot.timestamp = System.currentTimeMillis();
        snapshot.turn = game.getPhaseHandler().getTurn();
        snapshot.phase = game.getPhaseHandler().getPhase();
        snapshot.activePlayer = game.getPhaseHandler().getPlayerTurn().getName();
        snapshot.priority = game.getPhaseHandler().getPriorityPlayer().getName();

        // Capture player states
        for (Player player : game.getPlayers()) {
            PlayerState ps = new PlayerState();
            ps.name = player.getName();
            ps.life = player.getLife();
            ps.poison = player.getPoisonCounters();
            ps.handSize = player.getCardsIn(ZoneType.Hand).size();
            ps.librarySize = player.getCardsIn(ZoneType.Library).size();
            ps.graveyardSize = player.getCardsIn(ZoneType.Graveyard).size();
            ps.permanentCount = player.getCardsIn(ZoneType.Battlefield).size();
            ps.manaPool = player.getManaPool().totalMana();

            // Capture key permanents
            ps.creatures = countCreatures(player);
            ps.lands = countLands(player);
            ps.artifacts = countArtifacts(player);
            ps.enchantments = countEnchantments(player);

            snapshot.playerStates.put(player.getName(), ps);
        }

        // Capture stack state
        snapshot.stackSize = game.getStack().size();
        snapshot.isStackEmpty = game.getStack().isEmpty();

        return snapshot;
    }

    /**
     * Detect changes between two state snapshots
     */
    private void detectChanges(StateSnapshot oldState, StateSnapshot newState) {
        // Phase changes
        if (oldState.phase != newState.phase) {
            addChange(new StateChange(
                    StateChange.Type.PHASE_CHANGE,
                    "Phase: " + oldState.phase + " → " + newState.phase
            ));
        }

        // Turn changes
        if (oldState.turn < newState.turn) {
            addChange(new StateChange(
                    StateChange.Type.TURN_CHANGE,
                    "Turn " + newState.turn + " - " + newState.activePlayer
            ));
        }

        // Player state changes
        for (Map.Entry<String, PlayerState> entry : newState.playerStates.entrySet()) {
            String playerName = entry.getKey();
            PlayerState newPS = entry.getValue();
            PlayerState oldPS = oldState.playerStates.get(playerName);

            if (oldPS != null) {
                detectPlayerChanges(playerName, oldPS, newPS);
            }
        }

        // Stack changes
        if (oldState.stackSize != newState.stackSize) {
            addChange(new StateChange(
                    StateChange.Type.STACK_CHANGE,
                    "Stack: " + oldState.stackSize + " → " + newState.stackSize
            ));
        }
    }

    /**
     * Detect changes in player state
     */
    private void detectPlayerChanges(String playerName, PlayerState oldPS, PlayerState newPS) {
        // Life changes
        if (oldPS.life != newPS.life) {
            int diff = newPS.life - oldPS.life;
            addChange(new StateChange(
                    StateChange.Type.LIFE_CHANGE,
                    playerName + ": " + (diff > 0 ? "+" : "") + diff + " life (" + newPS.life + " total)"
            ));
        }

        // Card draw/discard
        if (oldPS.handSize != newPS.handSize) {
            int diff = newPS.handSize - oldPS.handSize;
            addChange(new StateChange(
                    StateChange.Type.CARD_DRAW,
                    playerName + ": " + (diff > 0 ? "drew " + diff : "discarded " + (-diff)) + " card(s)"
            ));
        }

        // Permanents entering/leaving
        if (oldPS.permanentCount != newPS.permanentCount) {
            int diff = newPS.permanentCount - oldPS.permanentCount;
            addChange(new StateChange(
                    StateChange.Type.PERMANENT_CHANGE,
                    playerName + ": " + (diff > 0 ? "+" : "") + diff + " permanent(s)"
            ));
        }

        // Creature changes
        if (oldPS.creatures != newPS.creatures) {
            int diff = newPS.creatures - oldPS.creatures;
            if (diff != 0) {
                addChange(new StateChange(
                        StateChange.Type.CREATURE_CHANGE,
                        playerName + ": " + (diff > 0 ? "+" : "") + diff + " creature(s)"
                ));
            }
        }
    }

    /**
     * Add a state change to recent history
     */
    private void addChange(StateChange change) {
        recentChanges.add(change);
        if (recentChanges.size() > MAX_RECENT_CHANGES) {
            recentChanges.remove(0);
        }
    }

    // Utility counting methods

    private int countCreatures(Player player) {
        return (int) player.getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.isCreature())
                .count();
    }

    private int countLands(Player player) {
        return (int) player.getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.isLand())
                .count();
    }

    private int countArtifacts(Player player) {
        return (int) player.getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.isArtifact())
                .count();
    }

    private int countEnchantments(Player player) {
        return (int) player.getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.isEnchantment())
                .count();
    }

    // Query methods

    public boolean hasStateChanged(String aspect) {
        if (stateHistory.size() < 2) return false;

        StateSnapshot current = stateHistory.get(stateHistory.size() - 1);
        StateSnapshot previous = stateHistory.get(stateHistory.size() - 2);

        switch (aspect.toLowerCase()) {
            case "phase":
                return current.phase != previous.phase;
            case "turn":
                return current.turn != previous.turn;
            case "life":
                return !current.playerStates.entrySet().stream()
                        .allMatch(e -> e.getValue().life ==
                                previous.playerStates.get(e.getKey()).life);
            default:
                return false;
        }
    }

    public List<StateChange> getRecentChanges(int count) {
        int start = Math.max(0, recentChanges.size() - count);
        return new ArrayList<>(recentChanges.subList(start, recentChanges.size()));
    }

    public PlayerState getPlayerState(String playerName) {
        return currentState != null ? currentState.playerStates.get(playerName) : null;
    }

    public PhaseType getCurrentPhase() {
        return currentState != null ? currentState.phase : null;
    }

    public int getCurrentTurn() {
        return currentState != null ? currentState.turn : 0;
    }

    // Performance getters
    public float getAverageUpdateTime() { return averageUpdateTime; }
    public int getUpdateCount() { return updateCount; }

    /**
     * State snapshot at a point in time
     */
    private static class StateSnapshot {
        long timestamp;
        int turn;
        PhaseType phase;
        String activePlayer;
        String priority;
        Map<String, PlayerState> playerStates = new HashMap<>();
        int stackSize;
        boolean isStackEmpty;
    }

    /**
     * Player state within a snapshot
     */
    public static class PlayerState {
        public String name;
        public int life;
        public int poison;
        public int handSize;
        public int librarySize;
        public int graveyardSize;
        public int permanentCount;
        public int manaPool;
        public int creatures;
        public int lands;
        public int artifacts;
        public int enchantments;
    }

    /**
     * Represents a state change event
     */
    public static class StateChange {
        public enum Type {
            PHASE_CHANGE,
            TURN_CHANGE,
            LIFE_CHANGE,
            CARD_DRAW,
            PERMANENT_CHANGE,
            CREATURE_CHANGE,
            STACK_CHANGE,
            MANA_CHANGE,
            COMBAT_CHANGE
        }

        public final Type type;
        public final String description;
        public final long timestamp;

        public StateChange(Type type, String description) {
            this.type = type;
            this.description = description;
            this.timestamp = System.currentTimeMillis();
        }
    }
}