package com.example.research;

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

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.text.SimpleDateFormat;

/**
 * Game State Tracker that logs state transitions in the format:
 * 1. Event that triggered the change
 * 2. List of variables that changed
 * 3. Full state after the event
 */
public class GameStateTracker {

    private final Game game;
    private final PrintStream output;
    private final boolean verboseLogging;
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    private GameState currentState;
    private int eventCounter = 0;

    private final PrintWriter fileWriter;

    /**
     * Complete game state at a point in time
     */
    public static class GameState {
        // Game-level state
        public final int turnNumber;
        public final PhaseType phase;
        public final String activePlayer;
        public final String priorityPlayer;
        public final String monarch;
        public final String initiative;

        // Player states
        public final Map<String, PlayerState> players = new HashMap<>();

        // Zone states
        public final Map<String, Map<ZoneType, List<CardState>>> zones = new HashMap<>();

        // Stack state
        public final List<StackItem> stack = new ArrayList<>();

        // Combat state
        public final CombatState combat;

        // Timestamp
        public final long timestamp;

        public GameState(Game game) {
            this.timestamp = System.currentTimeMillis();
            PhaseHandler ph = game.getPhaseHandler();

            // Game-level state
            this.turnNumber = ph.getTurn();
            this.phase = ph.getPhase();
            this.activePlayer = ph.getPlayerTurn() != null ? ph.getPlayerTurn().getName() : "None";
            this.priorityPlayer = ph.getPriorityPlayer() != null ? ph.getPriorityPlayer().getName() : "None";
            this.monarch = game.getMonarch() != null ? game.getMonarch().getName() : "None";
            this.initiative = game.getHasInitiative() != null ? game.getHasInitiative().getName() : "None";

            // Player states
            for (Player p : game.getPlayers()) {
                players.put(p.getName(), new PlayerState(p));

                // Zone states for each player
                Map<ZoneType, List<CardState>> playerZones = new HashMap<>();
                for (ZoneType zt : ZoneType.values()) {
                    List<CardState> cards = new ArrayList<>();
                    for (Card c : p.getCardsIn(zt)) {
                        cards.add(new CardState(c));
                    }
                    playerZones.put(zt, cards);
                }
                zones.put(p.getName(), playerZones);
            }

            // Stack state
            for (SpellAbilityStackInstance si : game.getStack()) {
                stack.add(new StackItem(si));
            }

            // Combat state
            this.combat = ph.getCombat() != null ? new CombatState(ph.getCombat()) : null;
        }
    }

    /**
     * Player state
     */
    public static class PlayerState {
        public final String name;
        public final int life;
        public final int poisonCounters;
        public final Map<CounterType, Integer> counters;
        public final String manaPool;
        public final int landsPlayedThisTurn;
        public final int handSize;
        public final int librarySize;
        public final int graveyardSize;
        public final int battlefieldCount;

        public PlayerState(Player p) {
            this.name = p.getName();
            this.life = p.getLife();
            this.poisonCounters = p.getPoisonCounters();

            // Counters
            this.counters = new HashMap<>();
            for (Map.Entry<CounterType, Integer> entry : p.getCounters().entrySet()) {
                if (entry.getValue() > 0) {
                    counters.put(entry.getKey(), entry.getValue());
                }
            }

            // Mana pool
            ManaPool pool = p.getManaPool();
            StringBuilder mana = new StringBuilder();
            for (Byte color : ManaAtom.MANATYPES) {
                int amount = pool.getAmountOfColor(color);
                if (amount > 0) {
                    if (!mana.isEmpty()) mana.append(", ");
                    mana.append(color.toString()).append(": ").append(amount);
                }
            }
            this.manaPool = mana.length() > 0 ? mana.toString() : "Empty";

            // Other state
            this.landsPlayedThisTurn = p.getLandsPlayedThisTurn();
            this.handSize = p.getCardsIn(ZoneType.Hand).size();
            this.librarySize = p.getCardsIn(ZoneType.Library).size();
            this.graveyardSize = p.getCardsIn(ZoneType.Graveyard).size();
            this.battlefieldCount = p.getCardsIn(ZoneType.Battlefield).size();
        }
    }

    /**
     * Card state
     */
    public static class CardState {
        public final int id;
        public final String name;
        public final String controller;
        public final String owner;
        public final boolean tapped;
        public final boolean attacking;
        public final boolean blocking;
        public final Map<CounterType, Integer> counters;
        public final int damage;
        public final int power;
        public final int toughness;
        public final List<Integer> attachedToIds;
        public final List<Integer> attachmentIds;

        public CardState(Card c) {
            this.id = c.getId();
            this.name = c.getName();
            this.controller = c.getController() != null ? c.getController().getName() : "None";
            this.owner = c.getOwner() != null ? c.getOwner().getName() : "None";
            this.tapped = c.isTapped();
            this.attacking = c.getGame().getCombat() != null && c.isAttacking();
            this.blocking = c.getBlockedThisTurn() != null;

            // Counters
            this.counters = new HashMap<>();
            for (Map.Entry<CounterType, Integer> entry : c.getCounters().entrySet()) {
                if (entry.getValue() > 0) {
                    counters.put(entry.getKey(), entry.getValue());
                }
            }

            this.damage = c.getDamage();
            this.power = c.isCreature() ? c.getNetPower() : -1;
            this.toughness = c.isCreature() ? c.getNetToughness() : -1;

            // Attachments
            this.attachedToIds = new ArrayList<>();
            if (c.getAttachedTo() != null) {
                attachedToIds.add(c.getAttachedTo().getId());
            }

            this.attachmentIds = new ArrayList<>();
            for (Card attachment : c.getAttachedCards()) {
                attachmentIds.add(attachment.getId());
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(" [").append(id).append("]");
            if (tapped) sb.append(" (Tapped)");
            if (attacking) sb.append(" (Attacking)");
            if (blocking) sb.append(" (Blocking)");
            if (power >= 0 && toughness >= 0) {
                sb.append(" ").append(power).append("/").append(toughness);
            }
            if (damage > 0) sb.append(" (").append(damage).append(" damage)");
            if (!counters.isEmpty()) {
                sb.append(" Counters: ");
                for (Map.Entry<CounterType, Integer> e : counters.entrySet()) {
                    sb.append(e.getKey().getName()).append("=").append(e.getValue()).append(" ");
                }
            }
            return sb.toString();
        }
    }

    /**
     * Stack item
     */
    public static class StackItem {
        public final String description;
        public final String source;
        public final String controller;

        public StackItem(SpellAbilityStackInstance si) {
            SpellAbility sa = si.getSpellAbility();
            this.description = sa.getDescription();
            this.source = sa.getHostCard() != null ? sa.getHostCard().getName() : "Unknown";
            this.controller = si.getActivatingPlayer().getName();
        }

        @Override
        public String toString() {
            return controller + ": " + description + " (from " + source + ")";
        }
    }

    /**
     * Combat state
     */
    public static class CombatState {
        public final String attackingPlayer;
        public final Map<String, List<Integer>> attackers;
        public final Map<Integer, List<Integer>> blockers;

        public CombatState(Combat combat) {
            this.attackingPlayer = combat.getAttackingPlayer().getName();
            this.attackers = new HashMap<>();
            this.blockers = new HashMap<>();

            // Attackers by defender
            for (GameEntity defender : combat.getDefenders()) {
                String defenderName = defender instanceof Player ?
                        ((Player)defender).getName() : defender.toString();
                List<Integer> attackerIds = combat.getAttackersOf(defender).stream()
                        .map(Card::getId)
                        .collect(Collectors.toList());
                if (!attackerIds.isEmpty()) {
                    attackers.put(defenderName, attackerIds);
                }
            }

            // Blockers by attacker
            for (Card attacker : combat.getAttackers()) {
                CardCollection blockerCards = combat.getBlockers(attacker);
                if (!blockerCards.isEmpty()) {
                    List<Integer> blockerIds = blockerCards.stream()
                            .map(Card::getId)
                            .collect(Collectors.toList());
                    blockers.put(attacker.getId(), blockerIds);
                }
            }
        }
    }

    /**
     * State change delta
     */
    public static class StateDelta {
        public final List<String> changes = new ArrayList<>();

        public void addChange(String category, String description) {
            changes.add("[" + category + "] " + description);
        }

        public boolean hasChanges() {
            return !changes.isEmpty();
        }
    }

    /**
     * Event listener class that subscribes to game events
     */
    private class GameEventListener {

        @Subscribe
        public void onGameStarted(GameEventGameStarted event) {
            handleEvent("GAME_STARTED", "Game has started");
        }

        @Subscribe
        public void onTurnBegan(GameEventTurnBegan event) {
            String desc = "Turn " + game.getPhaseHandler().getTurn() + " began";
            handleEvent("TURN_BEGAN", desc);
        }

        @Subscribe
        public void onTurnEnded(GameEventTurnEnded event) {
            String desc = "Turn " + game.getPhaseHandler().getTurn() + " ended";
            handleEvent("TURN_ENDED", desc);
        }

        @Subscribe
        public void onPhaseChanged(GameEventTurnPhase event) {
            PhaseHandler ph = game.getPhaseHandler();
            String desc = "Phase changed to " + ph.getPhase() +
                    " (Player: " + (ph.getPlayerTurn() != null ? ph.getPlayerTurn().getName() : "None") + ")";
            handleEvent("PHASE_CHANGED", desc);
        }

        @Subscribe
        public void onCardChangeZone(GameEventCardChangeZone event) {
            Card card = event.card;
            String desc = card.getName() + " moved from " + event.from + " to " + event.to;
            if (event.from != event.to) {
                handleEvent("CARD_ZONE_CHANGE", desc);
            }
        }

        @Subscribe
        public void onCardTapped(GameEventCardTapped event) {
            String desc = event.card.getName() + " " + (event.tapped ? "tapped" : "untapped");
            handleEvent("CARD_TAP_STATE", desc);
        }

        @Subscribe
        public void onSpellCast(GameEventSpellAbilityCast event) {
            SpellAbility sa = event.sa;
            String desc = sa.getActivatingPlayer().getName() + " cast " +
                    (sa.getHostCard() != null ? sa.getHostCard().getName() : sa.getDescription());
            handleEvent("SPELL_CAST", desc);
        }

        @Subscribe
        public void onSpellResolved(GameEventSpellResolved event) {
            SpellAbility sa = event.spell;
            String desc = (sa.getHostCard() != null ? sa.getHostCard().getName() : sa.getDescription()) + " resolved";
            handleEvent("SPELL_RESOLVED", desc);
        }

        @Subscribe
        public void onAttackersDeclared(GameEventAttackersDeclared event) {
            String desc = event.player.getName() + " declared " + event.attackersMap.size() + " attackers";
            handleEvent("ATTACKERS_DECLARED", desc);
        }

        @Subscribe
        public void onBlockersDeclared(GameEventBlockersDeclared event) {
            String desc = "Blockers declared";
            handleEvent("BLOCKERS_DECLARED", desc);
        }

        @Subscribe
        public void onCombatEnded(GameEventCombatEnded event) {
            handleEvent("COMBAT_ENDED", "Combat phase ended");
        }

        @Subscribe
        public void onPlayerLivesChanged(GameEventPlayerLivesChanged event) {
            String desc = event.player.getName() + " life changed: " + event.oldLives + " → " + event.newLives;
            handleEvent("LIFE_CHANGED", desc);
        }

        @Subscribe
        public void onCardDamaged(GameEventCardDamaged event) {
            String desc = event.card.getName() + " took " + event.amount + " damage";
            handleEvent("CARD_DAMAGED", desc);
        }

        @Subscribe
        public void onCardDestroyed(GameEventCardDestroyed event) {
            String desc = "card was destroyed";
            handleEvent("CARD_DESTROYED", desc);
        }

        @Subscribe
        public void onLandPlayed(GameEventLandPlayed event) {
            String desc = event.player.getName() + " played " + event.land.getName();
            handleEvent("LAND_PLAYED", desc);
        }

        @Subscribe
        public void onManaPool(GameEventManaPool event) {
            handleEvent("MANA_POOL_CHANGED", "Mana pool updated");
        }

        @Subscribe
        public void onCardCounters(GameEventCardCounters event) {
            String desc = event.card.getName() + " counters changed";
            handleEvent("CARD_COUNTERS", desc);
        }

        @Subscribe
        public void onPlayerCounters(GameEventPlayerCounters event) {
            String desc = event.receiver.getName() + " counters changed";
            handleEvent("PLAYER_COUNTERS", desc);
        }

        @Subscribe
        public void onGameFinished(GameEventGameFinished event) {
            handleEvent("GAME_FINISHED", "Game has finished");
        }

        @Subscribe
        public void onGameOutcome(GameEventGameOutcome event) {
            String desc = "Game outcome determined";
            handleEvent("GAME_OUTCOME", desc);
        }

        // Generic handler for any event not specifically handled
        @Subscribe
        public void onGenericEvent(GameEvent event) {
            // This will catch any GameEvent subclass not specifically handled above
            if (!isHandledEvent(event)) {
                String eventType = event.getClass().getSimpleName().replace("GameEvent", "");
                handleEvent(eventType.toUpperCase(), "Event: " + eventType);
            }
        }

        private boolean isHandledEvent(GameEvent event) {
            // List of event types we handle specifically
            return event instanceof GameEventGameStarted ||
                    event instanceof GameEventTurnBegan ||
                    event instanceof GameEventTurnEnded ||
                    event instanceof GameEventTurnPhase ||
                    event instanceof GameEventCardChangeZone ||
                    event instanceof GameEventCardTapped ||
                    event instanceof GameEventSpellAbilityCast ||
                    event instanceof GameEventSpellResolved ||
                    event instanceof GameEventAttackersDeclared ||
                    event instanceof GameEventBlockersDeclared ||
                    event instanceof GameEventCombatEnded ||
                    event instanceof GameEventPlayerLivesChanged ||
                    event instanceof GameEventCardDamaged ||
                    event instanceof GameEventCardDestroyed ||
                    event instanceof GameEventLandPlayed ||
                    event instanceof GameEventManaPool ||
                    event instanceof GameEventCardCounters ||
                    event instanceof GameEventPlayerCounters ||
                    event instanceof GameEventGameFinished ||
                    event instanceof GameEventGameOutcome;
        }
    }

    /**
     * Constructor
     */
    public GameStateTracker(Game game, PrintStream output, boolean verboseLogging) {
        this(game, output, verboseLogging, false, null);
    }

    public GameStateTracker(Game game, PrintStream output, boolean verboseLogging,
                            boolean logToFile, String logFilePath) {
        this.game = game;
        this.output = output;
        this.verboseLogging = verboseLogging;
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
        // Event listener
        GameEventListener eventListener = new GameEventListener();
        game.subscribeToEvents(eventListener);

        // Capture initial state
        currentState = new GameState(game);
        logEvent("GAME_START", "Initial game state", new StateDelta());
    }

    /**
     * Handle an event by capturing state changes
     */
    private void handleEvent(String eventType, String eventDescription) {
        // Save previous state
        // State tracking
        GameState previousState = currentState;

        // Capture new state
        currentState = new GameState(game);

        // Compute changes
        StateDelta delta = computeDelta(previousState, currentState);

        // Log the event
        logEvent(eventType, eventDescription, delta);
    }

    /**
     * Log an event with state transition
     */
    private void logEvent(String eventType, String eventDescription, StateDelta delta) {
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

        // Section 2: Variables that changed
        log.append("\n2. CHANGES:\n");
        if (delta.hasChanges()) {
            for (String change : delta.changes) {
                log.append("   • ").append(change).append("\n");
            }
        } else {
            log.append("   • No state changes\n");
        }

        // Section 3: Full state after event (only if verbose or significant changes)
        if (verboseLogging || delta.changes.size() > 3) {
            log.append("\n3. GAME STATE AFTER EVENT:\n");
            log.append(formatGameState(currentState));
        }

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
     * Format the complete game state
     */
    private String formatGameState(GameState state) {
        StringBuilder sb = new StringBuilder();

        // Game info
        sb.append("   GAME INFO:\n");
        sb.append("     • Turn: ").append(state.turnNumber).append("\n");
        sb.append("     • Phase: ").append(state.phase).append("\n");
        sb.append("     • Active Player: ").append(state.activePlayer).append("\n");
        sb.append("     • Priority: ").append(state.priorityPlayer).append("\n");
        if (!"None".equals(state.monarch)) {
            sb.append("     • Monarch: ").append(state.monarch).append("\n");
        }
        if (!"None".equals(state.initiative)) {
            sb.append("     • Initiative: ").append(state.initiative).append("\n");
        }

        // Player states
        sb.append("\n   PLAYERS:\n");
        for (PlayerState p : state.players.values()) {
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
            sb.append("       • Mana Pool: ").append(p.manaPool).append("\n");
            sb.append("       • Lands Played: ").append(p.landsPlayedThisTurn).append("\n");
            sb.append("       • Zones: Hand=").append(p.handSize)
                    .append(", Library=").append(p.librarySize)
                    .append(", Graveyard=").append(p.graveyardSize)
                    .append(", Battlefield=").append(p.battlefieldCount).append("\n");
        }

        // Stack
        if (!state.stack.isEmpty()) {
            sb.append("\n   STACK:\n");
            for (int i = 0; i < state.stack.size(); i++) {
                sb.append("     ").append(i + 1).append(". ").append(state.stack.get(i)).append("\n");
            }
        }

        // Combat
        if (state.combat != null && !state.combat.attackers.isEmpty()) {
            sb.append("\n   COMBAT:\n");
            sb.append("     Attacking Player: ").append(state.combat.attackingPlayer).append("\n");
            for (Map.Entry<String, List<Integer>> e : state.combat.attackers.entrySet()) {
                sb.append("     Attacking ").append(e.getKey()).append(": ");
                sb.append(e.getValue().stream().map(String::valueOf).collect(Collectors.joining(", ")));
                sb.append("\n");
            }
            if (!state.combat.blockers.isEmpty()) {
                sb.append("     Blocks:\n");
                for (Map.Entry<Integer, List<Integer>> e : state.combat.blockers.entrySet()) {
                    sb.append("       ").append(e.getKey()).append(" blocked by: ");
                    sb.append(e.getValue().stream().map(String::valueOf).collect(Collectors.joining(", ")));
                    sb.append("\n");
                }
            }
        }

        // Battlefield (only if verbose or non-empty)
        for (Map.Entry<String, Map<ZoneType, List<CardState>>> playerZones : state.zones.entrySet()) {
            String playerName = playerZones.getKey();
            List<CardState> battlefield = playerZones.getValue().get(ZoneType.Battlefield);

            if (!battlefield.isEmpty()) {
                sb.append("\n   ").append(playerName).append("'s BATTLEFIELD:\n");
                for (CardState card : battlefield) {
                    sb.append("     • ").append(card).append("\n");
                }
            }
        }

        // Other zones (only if verbose)
        if (verboseLogging) {
            for (Map.Entry<String, Map<ZoneType, List<CardState>>> playerZones : state.zones.entrySet()) {
                String playerName = playerZones.getKey();
                for (Map.Entry<ZoneType, List<CardState>> zoneEntry : playerZones.getValue().entrySet()) {
                    ZoneType zone = zoneEntry.getKey();
                    List<CardState> cards = zoneEntry.getValue();

                    if (zone != ZoneType.Battlefield && !cards.isEmpty() &&
                            (zone == ZoneType.Graveyard || zone == ZoneType.Exile)) {
                        sb.append("\n   ").append(playerName).append("'s ").append(zone).append(":\n");
                        for (CardState card : cards) {
                            sb.append("     • ").append(card.name).append(" [").append(card.id).append("]\n");
                        }
                    }
                }
            }
        }

        return sb.toString();
    }

    /**
     * Compute delta between two states
     */
    private StateDelta computeDelta(GameState oldState, GameState newState) {
        StateDelta delta = new StateDelta();

        if (oldState == null) {
            return delta; // No comparison for initial state
        }

        // Game-level changes
        if (oldState.turnNumber != newState.turnNumber) {
            delta.addChange("TURN", "Turn " + oldState.turnNumber + " → " + newState.turnNumber);
        }
        if (oldState.phase != newState.phase) {
            delta.addChange("PHASE", oldState.phase + " → " + newState.phase);
        }
        if (!oldState.activePlayer.equals(newState.activePlayer)) {
            delta.addChange("ACTIVE_PLAYER", oldState.activePlayer + " → " + newState.activePlayer);
        }
        if (!oldState.priorityPlayer.equals(newState.priorityPlayer)) {
            delta.addChange("PRIORITY", oldState.priorityPlayer + " → " + newState.priorityPlayer);
        }

        // Player state changes
        for (String playerName : oldState.players.keySet()) {
            PlayerState oldPlayer = oldState.players.get(playerName);
            PlayerState newPlayer = newState.players.get(playerName);

            if (oldPlayer.life != newPlayer.life) {
                delta.addChange("LIFE", playerName + ": " + oldPlayer.life + " → " + newPlayer.life);
            }
            if (oldPlayer.poisonCounters != newPlayer.poisonCounters) {
                delta.addChange("POISON", playerName + ": " + oldPlayer.poisonCounters + " → " + newPlayer.poisonCounters);
            }
            if (!oldPlayer.manaPool.equals(newPlayer.manaPool)) {
                delta.addChange("MANA", playerName + ": " + oldPlayer.manaPool + " → " + newPlayer.manaPool);
            }
            if (oldPlayer.handSize != newPlayer.handSize) {
                delta.addChange("HAND", playerName + ": " + oldPlayer.handSize + " → " + newPlayer.handSize + " cards");
            }
            if (oldPlayer.librarySize != newPlayer.librarySize) {
                delta.addChange("LIBRARY", playerName + ": " + oldPlayer.librarySize + " → " + newPlayer.librarySize + " cards");
            }
            if (oldPlayer.graveyardSize != newPlayer.graveyardSize) {
                delta.addChange("GRAVEYARD", playerName + ": " + oldPlayer.graveyardSize + " → " + newPlayer.graveyardSize + " cards");
            }
            if (oldPlayer.battlefieldCount != newPlayer.battlefieldCount) {
                delta.addChange("BATTLEFIELD", playerName + ": " + oldPlayer.battlefieldCount + " → " + newPlayer.battlefieldCount + " permanents");
            }
        }

        // Zone changes - track cards that moved
        for (String playerName : oldState.zones.keySet()) {
            Map<ZoneType, List<CardState>> oldZones = oldState.zones.get(playerName);
            Map<ZoneType, List<CardState>> newZones = newState.zones.get(playerName);

            for (ZoneType zone : ZoneType.values()) {
                List<CardState> oldCards = oldZones.get(zone);
                List<CardState> newCards = newZones.get(zone);

                // Cards that left this zone
                Set<Integer> oldIds = oldCards.stream().map(c -> c.id).collect(Collectors.toSet());
                Set<Integer> newIds = newCards.stream().map(c -> c.id).collect(Collectors.toSet());

                for (CardState oldCard : oldCards) {
                    if (!newIds.contains(oldCard.id)) {
                        // Find where it went
                        for (ZoneType targetZone : ZoneType.values()) {
                            if (targetZone != zone) {
                                List<CardState> targetCards = newZones.get(targetZone);
                                if (targetCards.stream().anyMatch(c -> c.id == oldCard.id)) {
                                    delta.addChange("ZONE_CHANGE",
                                            oldCard.name + " moved from " + zone + " to " + targetZone);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card state changes (for battlefield cards)
        for (String playerName : oldState.zones.keySet()) {
            List<CardState> oldBattlefield = oldState.zones.get(playerName).get(ZoneType.Battlefield);
            List<CardState> newBattlefield = newState.zones.get(playerName).get(ZoneType.Battlefield);

            Map<Integer, CardState> oldCardMap = oldBattlefield.stream()
                    .collect(Collectors.toMap(c -> c.id, c -> c));
            Map<Integer, CardState> newCardMap = newBattlefield.stream()
                    .collect(Collectors.toMap(c -> c.id, c -> c));

            for (Integer cardId : oldCardMap.keySet()) {
                if (newCardMap.containsKey(cardId)) {
                    CardState oldCard = oldCardMap.get(cardId);
                    CardState newCard = newCardMap.get(cardId);

                    if (oldCard.tapped != newCard.tapped) {
                        delta.addChange("TAP_STATE", newCard.name + " " +
                                (newCard.tapped ? "became tapped" : "untapped"));
                    }
                    if (oldCard.damage != newCard.damage) {
                        delta.addChange("DAMAGE", newCard.name + " damage: " +
                                oldCard.damage + " → " + newCard.damage);
                    }
                    if (oldCard.power != newCard.power || oldCard.toughness != newCard.toughness) {
                        delta.addChange("P/T", newCard.name + ": " +
                                oldCard.power + "/" + oldCard.toughness + " → " +
                                newCard.power + "/" + newCard.toughness);
                    }
                }
            }
        }

        // Stack changes
        if (oldState.stack.size() != newState.stack.size()) {
            delta.addChange("STACK", "Stack size: " + oldState.stack.size() + " → " + newState.stack.size());
        }

        return delta;
    }

    /**
     * Force a state capture and log (useful for debugging)
     */
    public void captureState(String reason) {
        handleEvent("MANUAL_CAPTURE", reason);
    }

    /**
     * Close resources
     */
    public void close() {
        if (fileWriter != null) {
            fileWriter.close();
        }
    }
}