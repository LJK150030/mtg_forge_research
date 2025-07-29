package com.example.research;

import forge.card.MagicColor;
import forge.card.mana.ManaAtom;
import forge.game.*;
import forge.game.card.*;
import forge.game.combat.*;
import forge.game.cost.*;
import forge.game.event.*;
import forge.game.phase.*;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.*;
import forge.game.mana.*;

import com.google.common.eventbus.Subscribe;
import forge.util.maps.MapOfLists;

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

    private GameState currentState;
    private int eventCounter = 0;

    private final PrintWriter fileWriter;

    // Map of player name to whether their hidden states should be logged
    private final Map<String, Boolean> hiddenStateLogging;

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
                players.put(p.getName(), new PlayerState(p, null));

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
            this.combat = ph.getCombat() != null ? new CombatState(ph.getCombat(), ph) : null;
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
        public final boolean isActivePlayer;

        public final int cardsDrawnThisTurn;
        public final int spellsCastThisTurn;

        public PlayerState(Player p, Player activePlayer) {
            this.name = p.getName();
            this.life = p.getLife();
            this.poisonCounters = p.getPoisonCounters();
            this.isActivePlayer = p.equals(activePlayer);

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

            cardsDrawnThisTurn = 0;
            spellsCastThisTurn = 0;
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
        public final Map<String, List<AttackerInfo>> attackersByDefender;
        public final Map<Integer, List<BlockerInfo>> blockersByAttacker;
        public final int totalAttackers;
        public final int totalDamageOnBoard;
        public final boolean hasFirstStrike;
        public final boolean hasDoubleStrike;
        public final List<String> combatRelevantAbilities;
        public final int turn;
        public final String phase;
        public final int combatNumber; // For additional combat phases
        public final Map<String, String> manaPools; // Player -> mana pool string
        public final Map<String, Integer> lifeTotals; // Player -> life total
        public final List<AttackerInfo> unblockedAttackers;

        public CombatState(Combat combat, PhaseHandler phaseHandler) {
            this.attackingPlayer = combat.getAttackingPlayer().getName();
            this.attackersByDefender = new HashMap<>();
            this.blockersByAttacker = new HashMap<>();
            this.combatRelevantAbilities = new ArrayList<>();
            this.turn = phaseHandler.getTurn();
            this.phase = phaseHandler.getPhase().toString();
            this.combatNumber = phaseHandler.getNumCombat();
            this.manaPools = new HashMap<>();
            this.lifeTotals = new HashMap<>();
            this.unblockedAttackers = new ArrayList<>();

            int totalDamage = 0;
            boolean firstStrike = false;
            boolean doubleStrike = false;

            // Process attackers by defender with detailed information
            for (GameEntity defender : combat.getDefenders()) {
                String defenderName = getDefenderName(defender);
                List<AttackerInfo> attackerInfos = new ArrayList<>();

                for (Card attacker : combat.getAttackersOf(defender)) {
                    AttackerInfo info = new AttackerInfo(attacker, combat);
                    attackerInfos.add(info);

                    // Track total damage potential
                    totalDamage += attacker.getNetPower();

                    // Track combat abilities
                    if (attacker.hasKeyword("First Strike")) firstStrike = true;
                    if (attacker.hasKeyword("Double Strike")) {
                        doubleStrike = true;
                        totalDamage += attacker.getNetPower(); // Double strike deals damage twice
                    }

                    // Track other relevant abilities
                    if (attacker.hasKeyword("Lifelink")) {
                        combatRelevantAbilities.add(attacker.getName() + " has Lifelink");
                    }
                    if (attacker.hasKeyword("Deathtouch")) {
                        combatRelevantAbilities.add(attacker.getName() + " has Deathtouch");
                    }
                    if (attacker.hasKeyword("Trample")) {
                        combatRelevantAbilities.add(attacker.getName() + " has Trample");
                    }
                }

                if (!attackerInfos.isEmpty()) {
                    attackersByDefender.put(defenderName, attackerInfos);
                }
            }

            // Process blockers with detailed information
            for (Card attacker : combat.getAttackers()) {
                CardCollection blockers = combat.getBlockers(attacker);
                if (!blockers.isEmpty()) {
                    List<BlockerInfo> blockerInfos = new ArrayList<>();
                    for (Card blocker : blockers) {
                        blockerInfos.add(new BlockerInfo(blocker));
                    }
                    blockersByAttacker.put(attacker.getId(), blockerInfos);
                }
            }

            // Capture player states
            for (Player p : combat.getAttackingPlayer().getGame().getPlayers()) {
                lifeTotals.put(p.getName(), p.getLife());
                if (!p.getManaPool().isEmpty()) {
                    manaPools.put(p.getName(), formatManaPool(p.getManaPool()));
                }
            }

            // Identify unblocked attackers
            for (Card attacker : combat.getAttackers()) {
                if (combat.getBlockers(attacker).isEmpty()) {
                    unblockedAttackers.add(new AttackerInfo(attacker, combat));
                }
            }

            this.totalAttackers = combat.getAttackers().size();
            this.totalDamageOnBoard = totalDamage;
            this.hasFirstStrike = firstStrike;
            this.hasDoubleStrike = doubleStrike;
        }

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
         * Detailed attacker information
         */
        public static class AttackerInfo {
            public final int id;
            public final String name;
            public final int power;
            public final int toughness;
            public final List<String> relevantKeywords;
            public final boolean tapped; // Some cards attack without tapping
            public final int damage; // Existing damage on the creature
            public final boolean isBlocked;
            public final List<Integer> blockerIds; // IDs of creatures blocking this attacker

            public AttackerInfo(Card attacker, Combat combat) {
                this.id = attacker.getId();
                this.name = attacker.getName();
                this.power = attacker.getNetPower();
                this.toughness = attacker.getNetToughness();
                this.tapped = attacker.isTapped();
                this.damage = attacker.getDamage();

                this.relevantKeywords = new ArrayList<>();
                String[] combatKeywords = {
                        "Flying", "First Strike", "Double Strike", "Deathtouch",
                        "Lifelink", "Trample", "Menace", "Vigilance", "Indestructible"
                };

                for (String keyword : combatKeywords) {
                    if (attacker.hasKeyword(keyword)) {
                        relevantKeywords.add(keyword);
                    }
                }

                CardCollection blockers = combat.getBlockers(attacker);
                this.isBlocked = !blockers.isEmpty();
                this.blockerIds = new ArrayList<>();
                for (Card blocker : blockers) {
                    blockerIds.add(blocker.getId());
                }

            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append(name).append(" [").append(id).append("] ");
                sb.append(power).append("/").append(toughness);
                if (damage > 0) {
                    sb.append(" (").append(damage).append(" damage)");
                }
                if (!relevantKeywords.isEmpty()) {
                    sb.append(" - ").append(String.join(", ", relevantKeywords));
                }
                return sb.toString();
            }
        }

        /**
         * Detailed blocker information
         */
        public static class BlockerInfo {
            public final int id;
            public final String name;
            public final int power;
            public final int toughness;
            public final boolean canBlockAdditional;
            public final int damage;
            public final List<Integer> blockingAttackerIds; // Which attackers this blocks
            public final boolean hasRegenerationShield;
            public final boolean hasReach; // Relevant for flying attackers

            public BlockerInfo(Card blocker) {
                this.id = blocker.getId();
                this.name = blocker.getName();
                this.power = blocker.getNetPower();
                this.toughness = blocker.getNetToughness();
                this.damage = blocker.getDamage();


                // Check if can block additional creatures
                this.canBlockAdditional = blocker.canBlockAdditional() > 0;

                this.blockingAttackerIds = new ArrayList<>();
                this.hasRegenerationShield = blocker.getShieldCount() > 0;
                this.hasReach = blocker.hasKeyword("Reach");
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append(name).append(" [").append(id).append("] ");
                sb.append(power).append("/").append(toughness);
                if (damage > 0) {
                    sb.append(" (").append(damage).append(" damage)");
                }
                if (canBlockAdditional) {
                    sb.append(" (can block additional)");
                }
                return sb.toString();
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
            StringBuilder desc = new StringBuilder();
            desc.append(event.gameType.toString()).append(" game started");
            desc.append(" with ").append(event.firstTurn.getName()).append(" going first");

            // List all players
            List<String> playerNames = new ArrayList<>();
            for (Player p : event.players) {
                playerNames.add(p.getName());
            }
            desc.append(" (Players: ").append(String.join(", ", playerNames)).append(")");

            handleEvent("GAME_STARTED", desc.toString());
        }

        @Subscribe
        public void onTurnBegan(GameEventTurnBegan event) {
            // Create a more detailed description
            StringBuilder desc = new StringBuilder();
            desc.append("Turn ").append(event.turnNumber)
                    .append(" began for ").append(event.turnOwner.getName());

            // Add context about game state at turn start
            Player previousActivePlayer = game.getPhaseHandler().getPlayerTurn();
            if (previousActivePlayer != null && !previousActivePlayer.equals(event.turnOwner)) {
                desc.append(" (previous: ").append(previousActivePlayer.getName()).append(")");
            }

            // You could also log turn-start conditions
            desc.append(" | Life: ").append(event.turnOwner.getLife());
            desc.append(" | Cards in hand: ").append(event.turnOwner.getCardsIn(ZoneType.Hand).size());

            handleEvent("TURN_BEGAN", desc.toString());
        }

        @Subscribe
        public void onTurnEnded(GameEventTurnEnded event) {
            // Get current game state information
            PhaseHandler ph = game.getPhaseHandler();
            Player endingPlayer = ph.getPlayerTurn(); // Player whose turn is ending
            int turnNumber = ph.getTurn();

            // Create comprehensive turn-end description
            StringBuilder desc = new StringBuilder();
            desc.append("Turn ").append(turnNumber).append(" ended");
            if (endingPlayer != null) {
                desc.append(" for ").append(endingPlayer.getName());

                // Add turn summary statistics
                desc.append(" | Life: ").append(endingPlayer.getLife());
                desc.append(" | Cards in hand: ").append(endingPlayer.getCardsIn(ZoneType.Hand).size());
                desc.append(" | Lands played: ").append(endingPlayer.getLandsPlayedThisTurn());
            }

            handleEvent("TURN_ENDED", desc.toString());
        }

        @Subscribe
        public void onPhaseChanged(GameEventTurnPhase event) {
            // Use event data directly instead of querying the game
            String desc = String.format("Phase changed to %s (Player: %s) - %s",
                    event.phase.nameForUi,
                    event.playerTurn.getName(),
                    event.phaseDesc);

            handleEvent("PHASE_CHANGED", desc);
        }

        @Subscribe
        public void onCardChangeZone(GameEventCardChangeZone event) {
            Card card = event.card;
            Zone fromZone = event.from;
            Zone toZone = event.to;

            // Skip if zones are the same (no actual movement)
            if (fromZone == toZone) {
                return;
            }

            // Extract comprehensive information from the event
            ZoneType fromType = fromZone != null ? fromZone.getZoneType() : null;
            ZoneType toType = toZone != null ? toZone.getZoneType() : null;

            Player fromPlayer = fromZone != null ? fromZone.getPlayer() : null;
            Player toPlayer = toZone != null ? toZone.getPlayer() : null;

            // Build a detailed description
            StringBuilder desc = new StringBuilder();

            // Basic movement info
            desc.append(card.getName()).append(" [").append(card.getId()).append("] moved from ");

            // From zone details
            if (fromZone != null) {
                desc.append(fromType);
                if (fromPlayer != null) {
                    desc.append(" (").append(fromPlayer.getName()).append(")");
                }
            } else {
                desc.append("null");
            }

            desc.append(" to ");

            // To zone details
            if (toZone != null) {
                desc.append(toType);
                if (toPlayer != null) {
                    desc.append(" (").append(toPlayer.getName()).append(")");
                }
            } else {
                desc.append("null");
            }

            // Add context about the card state
            if (card.getController() != null && card.getOwner() != null &&
                    !card.getController().equals(card.getOwner())) {
                desc.append(" | Control: ").append(card.getController().getName());
                desc.append(" | Owner: ").append(card.getOwner().getName());
            }

            // Track specific zone changes for state-based effects
            trackZoneChangeEffects(card, fromType, toType, fromPlayer, toPlayer);

            // Handle hidden information appropriately
            boolean shouldLogDetails = shouldRevealCardDetails(card, fromType, toType, fromPlayer, toPlayer);

            if (!shouldLogDetails && (isHiddenZone(fromType) || isHiddenZone(toType))) {
                // Create a sanitized description for hidden information
                String sanitizedDesc = createSanitizedDescription(fromType, toType, fromPlayer, toPlayer);
                handleEvent("CARD_ZONE_CHANGE", sanitizedDesc);
            } else {
                handleEvent("CARD_ZONE_CHANGE", desc.toString());
            }
        }

        @Subscribe
        public void onCardTapped(GameEventCardTapped event) {
            Card card = event.card;
            boolean tapped = event.tapped;

            // Extract comprehensive card information
            StringBuilder desc = new StringBuilder();

            // Basic tap information with card ID for precise tracking
            desc.append(card.getName()).append(" [").append(card.getId()).append("] ");
            desc.append(tapped ? "tapped" : "untapped");

            // Add controller information (important for multiplayer)
            if (card.getController() != null) {
                desc.append(" | Controller: ").append(card.getController().getName());
            }

            // Add zone information (should be battlefield, but verify)
            Zone zone = card.getZone();
            if (zone != null) {
                desc.append(" | Zone: ").append(zone.getZoneType());
            }

            // Add game phase context
            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            // Track if this is combat-related
            Combat combat = ph.getCombat();
            if (combat != null && card.isCreature()) {
                if (card.isAttacking()) {
                    desc.append(" | Attacking");
                }
                if (card.getBlockedThisTurn() != null) {
                    desc.append(" | Blocking");
                }
            }

            // Track card types (important for understanding tap effects)
            desc.append(" | Types: ");
            if (card.isLand()) desc.append("Land ");
            if (card.isCreature()) desc.append("Creature ");
            if (card.isArtifact()) desc.append("Artifact ");
            if (card.isEnchantment()) desc.append("Enchantment ");
            if (card.isPlaneswalker()) desc.append("Planeswalker ");

            // Track any counters on the card
            if (!card.getCounters().isEmpty()) {
                desc.append(" | Counters: ");
                for (Map.Entry<CounterType, Integer> entry : card.getCounters().entrySet()) {
                    if (entry.getValue() > 0) {
                        desc.append(entry.getKey().getName()).append("=").append(entry.getValue()).append(" ");
                    }
                }
            }

            // Track damage on creatures
            if (card.isCreature() && card.getDamage() > 0) {
                desc.append(" | Damage: ").append(card.getDamage());
            }

            // Track P/T for creatures
            if (card.isCreature()) {
                desc.append(" | P/T: ").append(card.getNetPower()).append("/").append(card.getNetToughness());
            }

            // Track attachments (equipment, auras)
            if (!card.getAttachedCards().isEmpty()) {
                desc.append(" | Attached: ");
                for (Card attachment : card.getAttachedCards()) {
                    desc.append(attachment.getName()).append(" ");
                }
            }

            // Track if attached to something
            if (card.getAttachedTo() != null) {
                desc.append(" | Attached to: ").append(card.getAttachedTo().getName());
            }

            // Track summoning sickness for creatures
            if (card.isCreature() && card.hasSickness()) {
                desc.append(" | Summoning Sick");
            }

            handleEvent("CARD_TAP_STATE", desc.toString());
        }

        @Subscribe
        public void onSpellCast(GameEventSpellAbilityCast event) {
            SpellAbility sa = event.sa;
            SpellAbilityStackInstance si = event.si;
            int stackIndex = event.stackIndex;

            // Build comprehensive spell cast description
            StringBuilder desc = new StringBuilder();

            // Basic cast information
            Player caster = sa.getActivatingPlayer();
            Card hostCard = sa.getHostCard();

            desc.append(caster.getName()).append(" cast ");

            // Spell/ability details
            if (hostCard != null) {
                desc.append(hostCard.getName()).append(" [").append(hostCard.getId()).append("]");

                // Add zone information (important for flashback, adventures, etc.)
                Zone castFrom = hostCard.getCastFrom();
                if (castFrom != null && castFrom.getZoneType() != ZoneType.Hand) {
                    desc.append(" from ").append(castFrom.getZoneType());
                }
            } else {
                desc.append(sa.getDescription());
            }

            // Spell type information
            desc.append(" | Type: ");
            if (sa.isSpell()) {
                desc.append("Spell");
                if (hostCard != null) {
                    if (hostCard.isInstant()) desc.append(" (Instant)");
                    else if (hostCard.isSorcery()) desc.append(" (Sorcery)");
                    else if (hostCard.isCreature()) desc.append(" (Creature)");
                    else if (hostCard.isEnchantment()) desc.append(" (Enchantment)");
                    else if (hostCard.isArtifact()) desc.append(" (Artifact)");
                    else if (hostCard.isPlaneswalker()) desc.append(" (Planeswalker)");
                }
            } else if (sa.isActivatedAbility()) {
                desc.append("Activated Ability");
            } else if (sa.isTrigger()) {
                desc.append("Triggered Ability");
            } else {
                desc.append("Ability");
            }

            // Stack position (important for storm count and spell ordering)
            desc.append(" | Stack Position: ").append(stackIndex);
            desc.append(" | Stack Size: ").append(game.getStack().size());

            // Mana cost information
            if (sa.getPayCosts() != null) {
                Cost cost = sa.getPayCosts();
                if (cost.hasManaCost()) {
                    desc.append(" | Mana Cost: ").append(cost.getCostMana());
                }

                // Additional costs
                if (cost.hasSpecificCostType(CostTapType.class)) {
                    desc.append(" | Tap Cost");
                }
                if (cost.hasSpecificCostType(CostSacrifice.class)) {
                    desc.append(" | Sacrifice Cost");
                }
                if (cost.hasSpecificCostType(CostDiscard.class)) {
                    desc.append(" | Discard Cost");
                }
                if (cost.hasSpecificCostType(CostPayLife.class)) {
                    desc.append(" | Life Cost");
                }
            }

            // CMC for storm/cascade tracking
            if (hostCard != null) {
                desc.append(" | CMC: ").append(hostCard.getCMC());
            }

            // Color information (devotion, color matters)
            if (hostCard != null) {
                desc.append(" | Colors: ");
                if (hostCard.isColorless()) {
                    desc.append("Colorless");
                } else {
                    List<String> colors = new ArrayList<>();
                    if (hostCard.isWhite()) colors.add("W");
                    if (hostCard.isBlue()) colors.add("U");
                    if (hostCard.isBlack()) colors.add("B");
                    if (hostCard.isRed()) colors.add("R");
                    if (hostCard.isGreen()) colors.add("G");
                    desc.append(String.join("", colors));
                }
            }

            // Target information
            if (sa.usesTargeting() && sa.getTargets() != null) {
                desc.append(" | Targets: ");
                List<String> targetDescriptions = new ArrayList<>();

                for (GameObject target : sa.getTargets()) {
                    if (target instanceof Card) {
                        Card targetCard = (Card) target;
                        targetDescriptions.add(targetCard.getName() + " [" + targetCard.getId() + "]");
                    } else if (target instanceof Player) {
                        Player targetPlayer = (Player) target;
                        targetDescriptions.add(targetPlayer.getName() + " (player)");
                    } else {
                        targetDescriptions.add(target.toString());
                    }
                }
                desc.append(String.join(", ", targetDescriptions));
            }

            // Copy information (storm, fork effects)
            if (sa.isCopied()) {
                desc.append(" | COPY");
            }

            // X value (if applicable)
            if (sa.hasParam("X") || (hostCard != null)) {
                int xValue = sa.getXManaCostPaid() != null ? sa.getXManaCostPaid() : 0;
                desc.append(" | X=").append(xValue);
            }

            // Kicker/additional costs paid
            if (sa.isKicked()) {
                desc.append(" | Kicked");
            }

            // Storm count (spells cast before this one this turn)
            int stormCount = caster.getSpellsCastThisTurn();
            desc.append(" | Storm Count: ").append(stormCount);

            // Check for special casting permissions (cascade, flashback, etc)
            if (sa.hasParam("CastFromSource")) {
                desc.append(" | Cast via: ").append(sa.getParam("CastFromSource"));
            }

            // Track game phase when cast (important for flash, instant speed)
            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            // Track if cast during combat
            if (ph.getCombat() != null) {
                desc.append(" | During Combat");
            }

            handleEvent("SPELL_CAST", desc.toString());
        }

        @Subscribe
        public void onSpellResolved(GameEventSpellResolved event) {
            SpellAbility sa = event.spell;
            boolean fizzled = event.hasFizzled;

            // Build comprehensive spell resolution description
            StringBuilder desc = new StringBuilder();

            // Basic resolution info
            Player controller = sa.getActivatingPlayer();
            Card hostCard = sa.getHostCard();

            if (hostCard != null) {
                desc.append(hostCard.getName()).append(" [").append(hostCard.getId()).append("]");
            } else {
                desc.append(sa.getDescription());
            }

            // CRITICAL: Did it actually resolve or fizzle?
            if (fizzled) {
                desc.append(" FIZZLED");
                desc.append(" | Controller: ").append(controller.getName());
                desc.append(" | Reason: ");

                // Common fizzle reasons
                if (sa.usesTargeting() && sa.getTargets() != null) {
                    boolean hasLegalTargets = sa.getTargets().isTargetingAnyCard();
                    if (!hasLegalTargets) {
                        desc.append("No legal targets");
                    } else {
                        desc.append("Countered or otherwise failed");
                    }
                } else {
                    desc.append("Countered or conditions not met");
                }
            } else {
                desc.append(" RESOLVED successfully");
                desc.append(" | Controller: ").append(controller.getName());

                // Track what the spell did (if we can determine it)
                if (sa.hasParam("NumCards")) {
                    desc.append(" | Cards drawn: ").append(sa.getParam("NumCards"));
                }
                if (sa.hasParam("LifeAmount")) {
                    desc.append(" | Life gained: ").append(sa.getParam("LifeAmount"));
                }
                if (sa.hasParam("Damage")) {
                    desc.append(" | Damage dealt: ").append(sa.getParam("Damage"));
                }
            }

            // Spell type (important for storm count, spell type matters cards)
            desc.append(" | Type: ");
            if (hostCard != null) {
                if (hostCard.isInstant()) desc.append("Instant");
                else if (hostCard.isSorcery()) desc.append("Sorcery");
                else if (hostCard.isCreature()) desc.append("Creature");
                else if (hostCard.isEnchantment()) desc.append("Enchantment");
                else if (hostCard.isArtifact()) desc.append("Artifact");
                else if (hostCard.isPlaneswalker()) desc.append("Planeswalker");
            } else if (sa.isActivatedAbility()) {
                desc.append("Activated Ability");
            } else if (sa.isTrigger()) {
                desc.append("Triggered Ability");
            }

            // Target information (what did it affect?)
            if (!fizzled && sa.usesTargeting() && sa.getTargets() != null) {
                desc.append(" | Targets affected: ");
                List<String> targetDescriptions = new ArrayList<>();

                for (GameObject target : sa.getTargets()) {
                    if (target instanceof Card) {
                        Card targetCard = (Card) target;
                        targetDescriptions.add(targetCard.getName() + " [" + targetCard.getId() + "]");
                    } else if (target instanceof Player) {
                        Player targetPlayer = (Player) target;
                        targetDescriptions.add(targetPlayer.getName() + " (player)");
                    }
                }
                desc.append(String.join(", ", targetDescriptions));
            }

            // Zone the spell goes to after resolution
            if (hostCard != null) {
                Zone destZone = sa.isSpell() ? hostCard.getGame().getZoneOf(hostCard) : null;
                if (destZone != null) {
                    desc.append(" | Goes to: ").append(destZone.getZoneType());
                } else if (sa.isSpell()) {
                    // Most instants/sorceries go to graveyard
                    desc.append(" | Goes to: Graveyard (expected)");
                }
            }

            // Copy information (storm, fork effects)
            if (sa.isCopied()) {
                desc.append(" | Was a COPY");
            }

            // X value resolution
            if (sa.getXManaCostPaid() != null) {
                desc.append(" | X resolved as: ").append(sa.getXManaCostPaid());
            }

            // Stack information
            desc.append(" | Stack size after: ").append(game.getStack().size());

            // Phase/timing information
            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            // Combat relevance
            if (ph.getCombat() != null) {
                desc.append(" | During Combat");
                if (fizzled) {
                    desc.append(" (combat tricks may have failed)");
                }
            }

            // Storm count update (if this was a spell)
            if (!fizzled && sa.isSpell() && controller != null) {
                int stormCount = controller.getSpellsCastThisTurn();
                desc.append(" | Storm count now: ").append(stormCount);
            }

            // Check for cascade/cast triggers that may fire
            if (!fizzled && sa.isSpell()) {
                desc.append(" | May trigger cast/ETB abilities");
            }

            handleEvent(fizzled ? "SPELL_FIZZLED" : "SPELL_RESOLVED", desc.toString());
        }

        @Subscribe
        public void onAttackersDeclared(GameEventAttackersDeclared event) {
            // Build comprehensive attacker declaration description
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            Combat combat = ph.getCombat();

            // Create structured combat state for analysis
            CombatState combatState = null;
            if (combat != null) {
                combatState = new CombatState(combat, ph);
            }

            // Basic attack information
            desc.append(event.player.getName()).append(" declared attackers");

            // Use CombatState for counts if available
            if (combatState != null) {
                desc.append(" | Total: ").append(combatState.totalAttackers).append(" creatures");
                desc.append(" | Defending: ").append(combatState.attackersByDefender.size()).append(" target(s)");
            } else {
                // Fallback to manual counting
                int totalAttackers = 0;
                int defendersCount = event.attackersMap.keySet().size();
                for (GameEntity defender : event.attackersMap.keySet()) {
                    totalAttackers += event.attackersMap.get(defender).size();
                }
                desc.append(" | Total: ").append(totalAttackers).append(" creatures");
                desc.append(" | Defending: ").append(defendersCount).append(" target(s)");
            }

            // Detailed breakdown by defender
            desc.append(" | Details: ");
            List<String> attackDetails = new ArrayList<>();

            // If we have CombatState, we can use its structured data
            if (combatState != null) {
                for (Map.Entry<String, List<CombatState.AttackerInfo>> entry :
                        combatState.attackersByDefender.entrySet()) {
                    String defenderName = entry.getKey();
                    List<CombatState.AttackerInfo> attackers = entry.getValue();

                    StringBuilder defenderDetail = new StringBuilder();
                    defenderDetail.append(defenderName).append(" <- ");

                    List<String> attackerStrings = new ArrayList<>();
                    for (CombatState.AttackerInfo attacker : attackers) {
                        attackerStrings.add(attacker.toString());
                    }

                    defenderDetail.append(String.join(", ", attackerStrings));
                    attackDetails.add(defenderDetail.toString());
                }
            } else {
                // Fallback to original logic
                for (Map.Entry<GameEntity, Collection<Card>> entry : event.attackersMap.asMap().entrySet()) {
                    // ... original detailed breakdown code ...
                }
            }

            desc.append(String.join(" | ", attackDetails));

            // Add game state context - can use CombatState data
            if (combatState != null) {
                desc.append(" | Turn: ").append(combatState.turn);
                desc.append(" | Phase: ").append(combatState.phase);

                if (combatState.combatNumber > 0) {
                    desc.append(" | Additional Combat #").append(combatState.combatNumber);
                }

                // Check for special abilities in structured data
                if (!combatState.combatRelevantAbilities.isEmpty()) {
                    for (String ability : combatState.combatRelevantAbilities) {
                        desc.append(" | ").append(ability);
                    }
                }
            } else {
                desc.append(" | Turn: ").append(ph.getTurn());
                desc.append(" | Phase: ").append(ph.getPhase());
            }

            // Note mana available for combat tricks
            ManaPool attackerPool = event.player.getManaPool();
            if (!attackerPool.isEmpty()) {
                desc.append(" | Attacker has mana: ").append(formatManaPool(attackerPool));
            }

            // Track life totals
            desc.append(" | Attacker life: ").append(event.player.getLife());

            handleEvent("ATTACKERS_DECLARED", desc.toString());
        }

        @Subscribe
        public void onBlockersDeclared(GameEventBlockersDeclared event) {
            // Build comprehensive blocker declaration description
            StringBuilder desc = new StringBuilder();

            PhaseHandler ph = game.getPhaseHandler();
            Combat combat = ph.getCombat();

            // Create CombatState to help with analysis
            CombatState combatState = null;
            if (combat != null) {
                combatState = new CombatState(combat, ph);

                // Update combat state with blocker information from event
                for (Map.Entry<GameEntity, MapOfLists<Card, Card>> defenderEntry : event.blockers.entrySet()) {
                    MapOfLists<Card, Card> blockAssignments = defenderEntry.getValue();

                    for (Map.Entry<Card, Collection<Card>> blockEntry : blockAssignments.entrySet()) {
                        Card attacker = blockEntry.getKey();
                        Collection<Card> blockers = blockEntry.getValue();

                        // Find the attacker in combat state and mark as blocked
                        for (List<CombatState.AttackerInfo> attackerList : combatState.attackersByDefender.values()) {
                            for (CombatState.AttackerInfo attackerInfo : attackerList) {
                                if (attackerInfo.id == attacker.getId()) {
                                    // We can't modify the AttackerInfo directly since fields are final
                                    // But we can track this information separately
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            // Basic blocking information
            desc.append(event.defendingPlayer.getName()).append(" declared blockers");

            // Count total blockers and blocked attackers
            int totalBlockers = 0;
            int blockedAttackers = 0;

            for (Map.Entry<GameEntity, MapOfLists<Card, Card>> defenderEntry : event.blockers.entrySet()) {
                MapOfLists<Card, Card> blockAssignments = defenderEntry.getValue();
                for (Map.Entry<Card, Collection<Card>> blockEntry : blockAssignments.entrySet()) {
                    blockedAttackers++;
                    totalBlockers += blockEntry.getValue().size();
                }
            }

            desc.append(" | Total: ").append(totalBlockers).append(" blockers");
            desc.append(" | Blocking: ").append(blockedAttackers).append(" attackers");

            // Get unblocked count from CombatState if available
            if (combatState != null) {
                int unblockedCount = combatState.totalAttackers - blockedAttackers;
                if (unblockedCount > 0) {
                    desc.append(" | Unblocked: ").append(unblockedCount).append(" attackers");
                }
            } else if (combat != null) {
                int unblockedCount = combat.getAttackers().size() - blockedAttackers;
                if (unblockedCount > 0) {
                    desc.append(" | Unblocked: ").append(unblockedCount).append(" attackers");
                }
            }

            // Detailed breakdown of blocking assignments
            desc.append(" | Assignments: ");
            List<String> blockDetails = new ArrayList<>();

            for (Map.Entry<GameEntity, MapOfLists<Card, Card>> defenderEntry : event.blockers.entrySet()) {
                GameEntity defender = defenderEntry.getKey();
                MapOfLists<Card, Card> blockAssignments = defenderEntry.getValue();

                for (Map.Entry<Card, Collection<Card>> blockEntry : blockAssignments.entrySet()) {
                    Card attacker = blockEntry.getKey();
                    Collection<Card> blockers = blockEntry.getValue();

                    StringBuilder blockDetail = new StringBuilder();

                    // Create AttackerInfo for detailed information
                    CombatState.AttackerInfo attackerInfo = new CombatState.AttackerInfo(attacker, combat);
                    blockDetail.append(attackerInfo.toString());
                    blockDetail.append(" <- ");

                    // List all blockers
                    List<String> blockerStrings = new ArrayList<>();
                    int totalBlockerPower = 0;
                    boolean hasDeathtouch = false;

                    for (Card blocker : blockers) {
                        CombatState.BlockerInfo blockerInfo = new CombatState.BlockerInfo(blocker);
                        blockerStrings.add(blockerInfo.toString());
                        totalBlockerPower += blockerInfo.power;
                        if (blocker.hasKeyword("Deathtouch")) {
                            hasDeathtouch = true;
                        }
                    }

                    blockDetail.append(String.join(" + ", blockerStrings));

                    // Add combat outcome prediction
                    if (blockers.size() > 1) {
                        blockDetail.append(" (Gang block: ");
                        blockDetail.append(totalBlockerPower).append(" total power");
                        if (hasDeathtouch) {
                            blockDetail.append(", DEATHTOUCH");
                        }
                        blockDetail.append(")");
                    } else if (blockers.size() == 1) {
                        Card blocker = blockers.iterator().next();
                        // Chump block detection
                        if (blocker.getNetToughness() <= attacker.getNetPower() &&
                                blocker.getNetPower() < attacker.getNetToughness() &&
                                !blocker.hasKeyword("Deathtouch")) {
                            blockDetail.append(" (Chump block)");
                        }
                        // Trade detection
                        else if (blocker.getNetToughness() <= attacker.getNetPower() &&
                                blocker.getNetPower() >= attacker.getNetToughness()) {
                            blockDetail.append(" (Trade)");
                        }
                        // Favorable block
                        else if (blocker.getNetToughness() > attacker.getNetPower() &&
                                blocker.getNetPower() >= attacker.getNetToughness()) {
                            blockDetail.append(" (Favorable block)");
                        }
                    }

                    blockDetails.add(blockDetail.toString());
                }
            }

            desc.append(String.join(" | ", blockDetails));

            // Check for unblocked attackers with evasion
            if (combat != null) {
                List<String> unblockedThreats = new ArrayList<>();

                // Build a set of blocked attacker IDs for quick lookup
                Set<Integer> blockedAttackerIds = new HashSet<>();
                for (MapOfLists<Card, Card> blockAssignments : event.blockers.values()) {
                    for (Card attacker : blockAssignments.keySet()) {
                        blockedAttackerIds.add(attacker.getId());
                    }
                }

                for (Card attacker : combat.getAttackers()) {
                    if (!blockedAttackerIds.contains(attacker.getId())) {
                        CombatState.AttackerInfo unblockedInfo = new CombatState.AttackerInfo(attacker, combat);
                        StringBuilder threatInfo = new StringBuilder();
                        threatInfo.append(unblockedInfo.name);
                        threatInfo.append(" (").append(unblockedInfo.power).append(" damage");

                        if (!unblockedInfo.relevantKeywords.isEmpty()) {
                            threatInfo.append(", ").append(String.join(", ", unblockedInfo.relevantKeywords));
                        }

                        threatInfo.append(")");
                        unblockedThreats.add(threatInfo.toString());
                    }
                }

                if (!unblockedThreats.isEmpty()) {
                    desc.append(" | Unblocked threats: ").append(String.join(", ", unblockedThreats));
                }
            }

            // Add game state context
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Phase: ").append(ph.getPhase());

            // Track mana and life totals
            ManaPool defenderPool = event.defendingPlayer.getManaPool();
            if (!defenderPool.isEmpty()) {
                desc.append(" | Defender has mana: ").append(formatManaPool(defenderPool));
            }

            desc.append(" | Defender life: ").append(event.defendingPlayer.getLife());
            if (combat != null && combat.getAttackingPlayer() != null) {
                desc.append(" | Attacker life: ").append(combat.getAttackingPlayer().getLife());
            }

            // Track regeneration shields
            int regenerators = 0;
            for (MapOfLists<Card, Card> blockAssignments : event.blockers.values()) {
                for (Collection<Card> blockerList : blockAssignments.values()) {
                    for (Card blocker : blockerList) {
                        if (blocker.getShieldCount() > 0) {
                            regenerators++;
                        }
                    }
                }
            }
            if (regenerators > 0) {
                desc.append(" | Regeneration shields: ").append(regenerators);
            }

            handleEvent("BLOCKERS_DECLARED", desc.toString());
        }

        @Subscribe
        public void onCombatEnded(GameEventCombatEnded event) {
            // Build comprehensive combat summary
            StringBuilder desc = new StringBuilder();
            desc.append("Combat phase ended");

            List<Card> attackers = event.attackers;
            List<Card> blockers = event.blockers;

            desc.append(" | Attackers: ").append(attackers.size());
            desc.append(" | Blockers: ").append(blockers.size());

            PhaseHandler ph = game.getPhaseHandler();

            // Track combat outcomes using structured data
            if (!attackers.isEmpty() || !blockers.isEmpty()) {
                desc.append(" | Combat Summary: ");

                // Create maps to track creature outcomes
                Map<Integer, CreatureOutcome> creatureOutcomes = new HashMap<>();

                // Analyze attacker states
                int attackersSurvived = 0;
                int attackersDied = 0;
                int attackersTapped = 0;
                int totalCombatDamageDealt = 0;
                List<String> notableAttackers = new ArrayList<>();

                for (Card attacker : attackers) {
                    CreatureOutcome outcome = new CreatureOutcome();
                    outcome.id = attacker.getId();
                    outcome.name = attacker.getName();
                    outcome.survived = attacker.isInPlay();

                    if (outcome.survived) {
                        attackersSurvived++;
                        outcome.damageReceived = attacker.getDamage();

                        if (attacker.isTapped() && !attacker.hasKeyword("Vigilance")) {
                            attackersTapped++;
                        }

                        if (outcome.damageReceived > 0) {
                            notableAttackers.add(outcome.name + " has " + outcome.damageReceived + " damage");
                        }

                        // Check if unblocked (simplified check)
                        boolean wasBlocked = false;
                        for (Card blocker : blockers) {
                            if (blocker.getBlockedThisTurn() != null &&
                                    blocker.getBlockedThisTurn().contains(attacker)) {
                                wasBlocked = true;
                                break;
                            }
                        }

                        if (!wasBlocked) {
                            outcome.damageDealt = attacker.getNetPower();
                            totalCombatDamageDealt += outcome.damageDealt;
                            notableAttackers.add(outcome.name + " dealt " + outcome.damageDealt + " damage (unblocked)");
                        } else if (attacker.hasKeyword("Trample")) {
                            notableAttackers.add(outcome.name + " (Trample)");
                        }
                    } else {
                        attackersDied++;
                        notableAttackers.add(outcome.name + " died");
                    }

                    creatureOutcomes.put(outcome.id, outcome);
                }

                desc.append("Attackers: ").append(attackersSurvived).append(" survived");
                if (attackersDied > 0) {
                    desc.append(", ").append(attackersDied).append(" died");
                }
                if (attackersTapped > 0) {
                    desc.append(", ").append(attackersTapped).append(" tapped");
                }

                // Analyze blocker states
                int blockersSurvived = 0;
                int blockersDied = 0;
                List<String> notableBlockers = new ArrayList<>();

                for (Card blocker : blockers) {
                    CreatureOutcome outcome = new CreatureOutcome();
                    outcome.id = blocker.getId();
                    outcome.name = blocker.getName();
                    outcome.survived = blocker.isInPlay();

                    if (outcome.survived) {
                        blockersSurvived++;
                        outcome.damageReceived = blocker.getDamage();

                        if (outcome.damageReceived > 0) {
                            notableBlockers.add(outcome.name + " has " + outcome.damageReceived + " damage");
                        }

                        // Check for deathtouch kills
                        if (blocker.hasKeyword("Deathtouch") && blocker.getBlockedThisTurn() != null) {
                            for (Card blocked : blocker.getBlockedThisTurn()) {
                                if (!blocked.isInPlay()) {
                                    outcome.killedCreatureIds.add(blocked.getId());
                                    notableBlockers.add(outcome.name + " killed " + blocked.getName() + " (Deathtouch)");
                                }
                            }
                        }
                    } else {
                        blockersDied++;
                        notableBlockers.add(outcome.name + " died");
                    }

                    creatureOutcomes.put(outcome.id, outcome);
                }

                desc.append(" | Blockers: ").append(blockersSurvived).append(" survived");
                if (blockersDied > 0) {
                    desc.append(", ").append(blockersDied).append(" died");
                }

                // Add player life totals
                desc.append(" | Life totals: ");
                List<String> lifeTotals = new ArrayList<>();
                for (Player p : game.getPlayers()) {
                    lifeTotals.add(p.getName() + "=" + p.getLife());
                }
                desc.append(String.join(", ", lifeTotals));

                // Add notable events
                if (!notableAttackers.isEmpty() || !notableBlockers.isEmpty()) {
                    desc.append(" | Notable: ");
                    List<String> allNotable = new ArrayList<>();
                    allNotable.addAll(notableAttackers);
                    allNotable.addAll(notableBlockers);
                    desc.append(String.join("; ", allNotable));
                }

                if (totalCombatDamageDealt > 0) {
                    desc.append(" | Unblocked damage: ").append(totalCombatDamageDealt);
                }

                // Track first/double strike
                boolean hadFirstStrike = false;
                boolean hadDoubleStrike = false;
                for (Card creature : attackers) {
                    if (creature.hasKeyword("First Strike")) hadFirstStrike = true;
                    if (creature.hasKeyword("Double Strike")) hadDoubleStrike = true;
                }
                for (Card creature : blockers) {
                    if (creature.hasKeyword("First Strike")) hadFirstStrike = true;
                    if (creature.hasKeyword("Double Strike")) hadDoubleStrike = true;
                }

                if (hadFirstStrike || hadDoubleStrike) {
                    desc.append(" | Combat had: ");
                    if (hadFirstStrike) desc.append("First Strike ");
                    if (hadDoubleStrike) desc.append("Double Strike");
                }

                // Track regeneration
                int regeneratedCount = 0;
                for (Card c : attackers) {
                    if (c.isInPlay() && c.getShieldCount() < c.getRegeneratedThisTurn()) {
                        regeneratedCount++;
                    }
                }
                for (Card c : blockers) {
                    if (c.isInPlay() && c.getShieldCount() < c.getRegeneratedThisTurn()) {
                        regeneratedCount++;
                    }
                }
                if (regeneratedCount > 0) {
                    desc.append(" | Regenerated: ").append(regeneratedCount).append(" creatures");
                }

            } else {
                desc.append(" | No creatures involved in combat");
            }

            // Add turn/phase context
            desc.append(" | Turn: ").append(ph.getTurn());
            desc.append(" | Active player: ").append(ph.getPlayerTurn().getName());

            if (ph.getNumCombat() > 1) {
                desc.append(" | Additional combat #").append(ph.getNumCombat());
            }

            handleEvent("COMBAT_ENDED", desc.toString());
        }

        // Helper class for tracking creature outcomes
        private static class CreatureOutcome {
            int id;
            String name;
            boolean survived;
            int damageDealt = 0;
            int damageReceived = 0;
            List<Integer> killedCreatureIds = new ArrayList<>();
            boolean regenerated = false;
        }

        @Subscribe
        public void onPlayerLivesChanged(GameEventPlayerLivesChanged event) {
            // Extract comprehensive information from the event
            Player player = event.player;
            int oldLife = event.oldLives;
            int newLife = event.newLives;
            int lifeDelta = newLife - oldLife;

            // Build a detailed description
            StringBuilder desc = new StringBuilder();
            desc.append(player.getName()).append(" life changed: ")
                    .append(oldLife).append(" → ").append(newLife);

            // Add delta information
            if (lifeDelta > 0) {
                desc.append(" (+").append(lifeDelta).append(" gained)");
            } else {
                desc.append(" (").append(lifeDelta).append(" lost)");
            }

            // Track critical life thresholds
            List<String> thresholds = new ArrayList<>();
            if (oldLife > 10 && newLife <= 10) {
                thresholds.add("dropped to 10 or below");
            }
            if (oldLife > 5 && newLife <= 5) {
                thresholds.add("dropped to 5 or below");
            }
            if (oldLife > 1 && newLife == 1) {
                thresholds.add("at 1 life!");
            }
            if (oldLife > 0 && newLife <= 0) {
                thresholds.add("dropped to 0 or below (potential loss)");
            }

            if (!thresholds.isEmpty()) {
                desc.append(" | CRITICAL: ").append(String.join(", ", thresholds));
            }

            // Add game context
            PhaseHandler ph = game.getPhaseHandler();
            desc.append(" | Phase: ").append(ph.getPhase());
            desc.append(" | Turn: ").append(ph.getTurn());

            // Check if this happened during combat (likely combat damage)
            // Check if this happened during combat (likely combat damage)
            Combat combat = ph.getCombat();
            if (combat != null && lifeDelta < 0) {
                desc.append(" | During Combat");

                // Try to identify if this was combat damage
                if (ph.getPhase() == PhaseType.COMBAT_DAMAGE ||
                        ph.getPhase() == PhaseType.COMBAT_FIRST_STRIKE_DAMAGE) {
                    desc.append(" (likely combat damage)");

                    // Track all damage sources during combat
                    List<Card> unblockedAttackers = new ArrayList<>();
                    List<Card> blockedAttackers = new ArrayList<>();
                    List<Card> trampleAttackers = new ArrayList<>();
                    int totalUnblockedDamage = 0;
                    int potentialTrampleDamage = 0;

                    // Analyze each attacker
                    for (Card attacker : combat.getAttackers()) {
                        GameEntity defender = combat.getDefenderByAttacker(attacker);

                        // Check if this attacker is targeting the player who lost life
                        if (defender instanceof Player && defender.equals(player)) {
                            CardCollection blockers = combat.getBlockers(attacker);

                            if (blockers.isEmpty()) {
                                // Unblocked attacker
                                unblockedAttackers.add(attacker);
                                totalUnblockedDamage += attacker.getNetPower();
                            } else {
                                // Blocked attacker
                                blockedAttackers.add(attacker);

                                // Check for trample
                                if (attacker.hasKeyword("Trample")) {
                                    trampleAttackers.add(attacker);

                                    // Calculate potential trample damage
                                    int attackerPower = attacker.getNetPower();
                                    int totalBlockerToughness = 0;

                                    for (Card blocker : blockers) {
                                        totalBlockerToughness += blocker.getNetToughness() - blocker.getDamage();
                                    }

                                    int trampleThrough = Math.max(0, attackerPower - totalBlockerToughness);
                                    potentialTrampleDamage += trampleThrough;
                                }
                            }
                        }
                    }

                    // Build damage source analysis
                    if (!unblockedAttackers.isEmpty() || !trampleAttackers.isEmpty()) {
                        List<String> damageBreakdown = new ArrayList<>();

                        // Unblocked damage
                        if (!unblockedAttackers.isEmpty()) {
                            List<String> unblockedNames = new ArrayList<>();
                            for (Card attacker : unblockedAttackers) {
                                unblockedNames.add(attacker.getName() + " [" + attacker.getId() + "] (" +
                                        attacker.getNetPower() + " damage)");
                            }
                            damageBreakdown.add("Unblocked: " + String.join(", ", unblockedNames));
                        }

                        // Trample damage
                        if (!trampleAttackers.isEmpty() && potentialTrampleDamage > 0) {
                            List<String> trampleNames = new ArrayList<>();
                            for (Card attacker : trampleAttackers) {
                                trampleNames.add(attacker.getName() + " [" + attacker.getId() + "]");
                            }
                            damageBreakdown.add("Potential trample: " + potentialTrampleDamage +
                                    " from " + String.join(", ", trampleNames));
                        }

                        desc.append(" | Combat damage sources: ").append(String.join("; ", damageBreakdown));

                        // Check if damage matches life loss
                        int totalPotentialDamage = totalUnblockedDamage + potentialTrampleDamage;
                        if (totalPotentialDamage == Math.abs(lifeDelta)) {
                            desc.append(" | EXACT MATCH");
                        } else if (totalPotentialDamage > Math.abs(lifeDelta)) {
                            desc.append(" | Damage prevented/replaced: ")
                                    .append(totalPotentialDamage - Math.abs(lifeDelta));
                        } else if (totalPotentialDamage < Math.abs(lifeDelta)) {
                            desc.append(" | Additional damage from other sources: ")
                                    .append(Math.abs(lifeDelta) - totalPotentialDamage);
                        }
                    } else if (!blockedAttackers.isEmpty()) {
                        // All attackers were blocked (no trample)
                        desc.append(" | All ").append(blockedAttackers.size())
                                .append(" attackers were blocked (no trample damage)");
                    } else {
                        // No attackers targeting this player
                        desc.append(" | No creatures attacking this player");
                    }

                    // Check for first strike/double strike timing
                    if (ph.getPhase() == PhaseType.COMBAT_FIRST_STRIKE_DAMAGE) {
                        desc.append(" | First strike damage phase");

                        // Count first strikers
                        int firstStrikers = 0;
                        for (Card attacker : combat.getAttackers()) {
                            if ((attacker.hasKeyword("First Strike") || attacker.hasKeyword("Double Strike")) &&
                                    combat.getDefenderByAttacker(attacker) == player) {
                                firstStrikers++;
                            }
                        }
                        if (firstStrikers > 0) {
                            desc.append(" | ").append(firstStrikers).append(" first/double striker(s)");
                        }
                    }
                } else {
                    // Combat is active but not in damage phase
                    desc.append(" | Phase: ").append(ph.getPhase())
                            .append(" (not damage phase - possibly ability/spell)");
                }
            } else if (combat != null && lifeDelta > 0) {
                // Life gain during combat
                desc.append(" | During Combat (life gain)");

                // Check for lifelink
                int lifelinkCreatures = 0;
                for (Card attacker : combat.getAttackers()) {
                    if (attacker.hasKeyword("Lifelink") &&
                            attacker.getController().equals(player)) {
                        lifelinkCreatures++;
                    }
                }

                if (lifelinkCreatures > 0 &&
                        (ph.getPhase() == PhaseType.COMBAT_DAMAGE ||
                                ph.getPhase() == PhaseType.COMBAT_FIRST_STRIKE_DAMAGE)) {
                    desc.append(" | Possible lifelink from ").append(lifelinkCreatures).append(" creature(s)");
                }
            }

            // Check for non-combat life changes
            if (combat == null || (ph.getPhase() != PhaseType.COMBAT_DAMAGE &&
                    ph.getPhase() != PhaseType.COMBAT_FIRST_STRIKE_DAMAGE)) {
                // Check stack for potential sources
                if (!game.getStack().isEmpty()) {
                    desc.append(" | Stack has ").append(game.getStack().size()).append(" items");

                    // Look for life-related effects on stack
                    SpellAbilityStackInstance topStack = game.getStack().peek();
                    if (topStack != null) {
                        SpellAbility sa = topStack.getSpellAbility();
                        String saDesc = sa.getDescription().toLowerCase();

                        if (lifeDelta > 0 && (saDesc.contains("gain") && saDesc.contains("life"))) {
                            desc.append(" | Likely from: ").append(sa.getHostCard().getName());
                        } else if (lifeDelta < 0 && (saDesc.contains("lose") && saDesc.contains("life") ||
                                saDesc.contains("pay") && saDesc.contains("life") ||
                                saDesc.contains("damage"))) {
                            desc.append(" | Likely from: ").append(sa.getHostCard().getName());
                        }
                    }
                }
            }

            // Track opponent life for life differential
            List<String> opponentInfo = new ArrayList<>();
            for (Player opponent : game.getPlayers()) {
                if (opponent != player) {
                    int lifeDiff = newLife - opponent.getLife();
                    opponentInfo.add(opponent.getName() + ": " + opponent.getLife() +
                            " (diff: " + (lifeDiff > 0 ? "+" : "") + lifeDiff + ")");
                }
            }
            desc.append(" | Opponents: ").append(String.join(", ", opponentInfo));

            // Track infect/poison relationship
            if (player.getPoisonCounters() > 0) {
                desc.append(" | Poison counters: ").append(player.getPoisonCounters());
            }

            // Check mana pool (life payment costs)
            if (lifeDelta < 0 && !player.getManaPool().isEmpty()) {
                desc.append(" | Player has mana: ").append(formatManaPool(player.getManaPool()));
                desc.append(" (possible life payment for mana/ability)");
            }

            handleEvent("LIFE_CHANGED", desc.toString());
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
     * Format mana pool for logging
     */
    private static String formatManaPool(ManaPool pool) {
        StringBuilder mana = new StringBuilder();
        for (Byte color : ManaAtom.MANATYPES) {
            int amount = pool.getAmountOfColor(color);
            if (amount > 0) {
                if (mana.length() > 0) mana.append(", ");
                mana.append(MagicColor.toShortString(color)).append(": ").append(amount);
            }
        }
        return mana.toString();
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

    public GameStateTracker(Game game, PrintStream output, boolean verboseLogging) {
        this(game, output, verboseLogging, false, null, new HashMap<>());
    }

    /**
     * Constructor with hidden state logging control
     */
    public GameStateTracker(Game game, PrintStream output, boolean verboseLogging,
                            boolean logToFile, String logFilePath, Map<String, Boolean> hiddenStateLogging) {
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

        // Capture initial state
        currentState = new GameState(game);
        logEvent("GAME_START", "Initial game state", new StateDelta());
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
            for (Map.Entry<String, List<CombatState.AttackerInfo>> entry :
                    state.combat.attackersByDefender.entrySet()) {
                sb.append("       → ").append(entry.getKey()).append(":\n");
                for (CombatState.AttackerInfo attacker : entry.getValue()) {
                    sb.append("         • ").append(attacker.toString()).append("\n");
                }
            }

            // Blockers if any
            if (!state.combat.blockersByAttacker.isEmpty()) {
                sb.append("     Block Assignments:\n");
                for (Map.Entry<Integer, List<CombatState.BlockerInfo>> entry :
                        state.combat.blockersByAttacker.entrySet()) {
                    sb.append("       Attacker [").append(entry.getKey()).append("] blocked by:\n");
                    for (CombatState.BlockerInfo blocker : entry.getValue()) {
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
        for (Map.Entry<String, Map<ZoneType, List<CardState>>> playerZones : state.zones.entrySet()) {
            String playerName = playerZones.getKey();
            boolean showHidden = shouldShowHiddenInfo(playerName);

            // Battlefield (always public)
            List<CardState> battlefield = playerZones.getValue().get(ZoneType.Battlefield);
            if (!battlefield.isEmpty()) {
                sb.append("\n   ").append(playerName).append("'s BATTLEFIELD:\n");
                for (CardState card : battlefield) {
                    sb.append("     • ").append(card).append("\n");
                }
            }

            // Hand (hidden unless explicitly allowed)
            List<CardState> hand = playerZones.getValue().get(ZoneType.Hand);
            if (!hand.isEmpty()) {
                if (showHidden) {
                    sb.append("\n   ").append(playerName).append("'s HAND:\n");
                    for (CardState card : hand) {
                        sb.append("     • ").append(card.name).append(" [").append(card.id).append("]\n");
                    }
                } else if (verboseLogging) {
                    sb.append("\n   ").append(playerName).append("'s HAND: ")
                            .append(hand.size()).append(" cards (hidden)\n");
                }
            }

            // Graveyard (always public)
            List<CardState> graveyard = playerZones.getValue().get(ZoneType.Graveyard);
            if (!graveyard.isEmpty()) {
                sb.append("\n   ").append(playerName).append("'s GRAVEYARD:\n");
                for (CardState card : graveyard) {
                    sb.append("     • ").append(card.name).append(" [").append(card.id).append("]\n");
                }
            }

            // Exile (always public)
            List<CardState> exile = playerZones.getValue().get(ZoneType.Exile);
            if (!exile.isEmpty()) {
                sb.append("\n   ").append(playerName).append("'s EXILE:\n");
                for (CardState card : exile) {
                    sb.append("     • ").append(card.name).append(" [").append(card.id).append("]\n");
                }
            }

            // Library (hidden unless explicitly allowed)
            if (verboseLogging) {
                List<CardState> library = playerZones.getValue().get(ZoneType.Library);
                if (showHidden && !library.isEmpty()) {
                    sb.append("\n   ").append(playerName).append("'s LIBRARY TOP CARDS:\n");
                    // Show only top few cards to avoid massive logs
                    int cardsToShow = Math.min(5, library.size());
                    for (int i = 0; i < cardsToShow; i++) {
                        CardState card = library.get(i);
                        sb.append("     • ").append(i + 1).append(": ")
                                .append(card.name).append(" [").append(card.id).append("]\n");
                    }
                    if (library.size() > cardsToShow) {
                        sb.append("     • ... and ").append(library.size() - cardsToShow)
                                .append(" more cards\n");
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
                String change = playerName + ": " + oldPlayer.handSize + " → " + newPlayer.handSize + " cards";
                if (shouldShowHiddenInfo(playerName)) {
                    // If we can see hidden info, we might add more detail later
                    delta.addChange("HAND", change);
                } else {
                    delta.addChange("HAND", change + " (contents hidden)");
                }
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
            boolean showHidden = shouldShowHiddenInfo(playerName);

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
                                    // Check if we should show card name based on zones and player settings
                                    boolean showCardName = shouldShowCardDetails(playerName, zone, targetZone);

                                    if (showCardName) {
                                        delta.addChange("ZONE_CHANGE",
                                                oldCard.name + " moved from " + zone + " to " + targetZone);
                                    } else {
                                        delta.addChange("ZONE_CHANGE",
                                                "A card moved from " + zone + " to " + targetZone +
                                                        " (Player: " + playerName + ")");
                                    }
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

        // Add turn-specific changes
        if (oldState.turnNumber != newState.turnNumber) {
            delta.addChange("TURN", "Turn " + oldState.turnNumber + " → " + newState.turnNumber);

            // Add turn transition details
            String oldActive = oldState.activePlayer;
            String newActive = newState.activePlayer;
            if (!oldActive.equals(newActive)) {
                delta.addChange("TURN_PLAYER", "Active player: " + oldActive + " → " + newActive);
            }
        }

        return delta;
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