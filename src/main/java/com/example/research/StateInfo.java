package com.example.research;

import forge.card.*;
import forge.game.GameObject;
import forge.game.replacement.ReplacementEffect;
import forge.game.trigger.TriggerType;
import forge.game.ability.ApiType;
import forge.card.mana.ManaAtom;
import forge.card.mana.ManaCost;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordInterface;
import forge.game.mana.ManaPool;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.spellability.TargetChoices;
import forge.game.staticability.StaticAbility;
import forge.game.trigger.Trigger;
import forge.game.zone.ZoneType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced StateInfo implementation aligned with Forge's architecture
 * Captures comprehensive game state for analysis and AI decision making
 */
public class StateInfo {

    /**
     * Complete game state snapshot
     */
    public static class GameState {
        // === Game-level State ===
        public final int gameId;
        public final int turnNumber;
        public final PhaseType phase;
        public final String step; // For more granular phase tracking
        public final String activePlayer;
        public final String priorityPlayer;
        public final boolean stackResolving;

        // === Special Game States ===
        public final String monarch;
        public final String initiative;
        public final boolean dayTime; // Day/Night mechanic
        public final int stormCount;

        // === Player States ===
        public final Map<String, PlayerState> players = new HashMap<>();

        // === Zone States with Enhanced Tracking ===
        public final Map<String, Map<ZoneType, ZoneState>> zones = new HashMap<>();

        // === Stack State ===
        public final List<StackItem> stack = new ArrayList<>();
        public final Map<String, Integer> stackTargets = new HashMap<>(); // Track targeting

        // === Combat State ===
        public final CombatState combat;

        // === Global Effects ===
        public final List<ContinuousEffect> continuousEffects = new ArrayList<>();
        public final List<DelayedTrigger> delayedTriggers = new ArrayList<>();

        // === Timestamps ===
        public final long timestamp;
        public final long gameTimestamp; // Forge's internal timestamp for layers

        public GameState(Game game) {
            this.timestamp = System.currentTimeMillis();
            this.gameId = game.getId();

            PhaseHandler ph = game.getPhaseHandler();

            PhaseType currentPhase = ph.getPhase();
            if (currentPhase != null) {
                this.phase = currentPhase;
                this.step = getDetailedStep(ph);
            } else {
                this.phase = null;
                this.step = "Not Started";
            }

            this.turnNumber = ph.getTurn();
            this.activePlayer = ph.getPlayerTurn() != null ? ph.getPlayerTurn().getName() : "None";
            this.priorityPlayer = ph.getPriorityPlayer() != null ? ph.getPriorityPlayer().getName() : "None";
            this.stackResolving = game.getStack().isResolving();
            this.gameTimestamp = game.getTimestamp();

            // Special states
            this.monarch = game.getMonarch() != null ? game.getMonarch().getName() : null;
            this.initiative = game.getHasInitiative() != null ? game.getHasInitiative().getName() : null;
            this.dayTime = game.isDay();
            this.stormCount = game.getStack().size(); // Approximate storm count

            // Player states
            for (Player p : game.getPlayers()) {
                players.put(p.getName(), new PlayerState(p, game));

                // Enhanced zone states
                Map<ZoneType, ZoneState> playerZones = new HashMap<>();
                for (ZoneType zt : ZoneType.values()) {
                    playerZones.put(zt, new ZoneState(p, zt));
                }
                zones.put(p.getName(), playerZones);
            }

            // Stack state with enhanced tracking
            for (SpellAbilityStackInstance si : game.getStack()) {
                StackItem item = new StackItem(si);
                stack.add(item);

                // Track targeting relationships
                TargetChoices tc = si.getTargetChoices();
                if (tc != null && !tc.isEmpty()) {
                    // Store target info differently since getTargets() doesn't exist
                    stackTargets.put(tc.toString(), item.id);
                }
            }

            // Combat state
            this.combat = ph.getCombat() != null ?
                    new CombatState(ph.getCombat(), ph) : null;

            // Continuous effects tracking
            trackContinuousEffects(game);
        }

        private String getDetailedStep(PhaseHandler ph) {
            PhaseType currentPhase = ph.getPhase();
            if (currentPhase == null) {
                return "Not Started";
            }

            // More granular phase tracking for complex interactions
            if (currentPhase == PhaseType.COMBAT_DECLARE_BLOCKERS) {
                if (ph.inCombat()) {
                    return "Blockers Declared";
                }
                return "Declare Blockers";
            }
            // Add more detailed step tracking as needed
            return currentPhase.toString();
        }

        private void trackContinuousEffects(Game game) {
            // Track active continuous effects that modify game state
            for (Card c : game.getCardsIn(ZoneType.Battlefield)) {
                for (StaticAbility sa : c.getStaticAbilities()) {
                    continuousEffects.add(new ContinuousEffect(c, sa));
                }
            }
        }
    }

    /**
     * Enhanced zone state with visibility tracking
     */
    public static class ZoneState {
        public final ZoneType type;
        public final String owner;
        public final List<CardState> cards = new ArrayList<>();
        public final int size;
        public final boolean ordered; // Library, graveyard order matters in some formats
        public final Map<Integer, CardVisibility> visibility = new HashMap<>();

        public ZoneState(Player p, ZoneType zt) {
            this.type = zt;
            this.owner = p.getName();
            this.ordered = (zt == ZoneType.Library || zt == ZoneType.Graveyard);

            CardCollectionView cardsInZone = p.getCardsIn(zt);
            this.size = cardsInZone.size();

            int position = 0;
            for (Card c : cardsInZone) {
                CardState cs = new CardState(c, zt, position++);
                cards.add(cs);

                // Track visibility
                visibility.put(c.getId(), determineVisibility(c, zt, p));
            }
        }

        private CardVisibility determineVisibility(Card c, ZoneType zt, Player owner) {
            if (zt == ZoneType.Battlefield || zt == ZoneType.Graveyard ||
                    zt == ZoneType.Exile || zt == ZoneType.Command) {
                return c.isFaceDown() ? CardVisibility.FACE_DOWN : CardVisibility.PUBLIC;
            }
            if (zt == ZoneType.Hand) {
                // Cards in hand are hidden unless revealed
                return CardVisibility.HIDDEN;
            }
            return CardVisibility.HIDDEN;
        }
    }

    public enum CardVisibility {
        PUBLIC,      // Everyone can see
        HIDDEN,      // Only owner can see
        FACE_DOWN,   // Card exists but details hidden
        REVEALED     // Temporarily shown
    }

    /**
     * Enhanced player state with comprehensive tracking
     */
    public static class PlayerState {
        // === Identity ===
        public final String name;
        public final int playerNumber;
        public final boolean isAI;

        // === Life & Loss Conditions ===
        public final int life;
        public final int startingLife;
        public final int poisonCounters;
        public final Map<String, Integer> commanderDamage;
        public final boolean hasLost;
        public final String lostReason;

        // === Counters ===
        public final Map<CounterType, Integer> counters;
        public final int energyCounters;
        public final int experienceCounters;
        public final int radCounters;

        // === Resources ===
        public final ManaPoolState manaPool;
        public final int maxHandSize;
        public final boolean unlimitedHandSize;

        // === Turn Tracking ===
        public final TurnState turnState;

        // === Zone Summary ===
        public final Map<ZoneType, Integer> zoneCounts;

        // === Game State Flags ===
        public final Set<PlayerFlag> flags = new HashSet<>();

        // === Restrictions & Permissions ===
        public final List<PlayerRestriction> restrictions = new ArrayList<>();
        public final List<PlayerPermission> permissions = new ArrayList<>();

        // === Emblems & Dungeons ===
        public final List<EmblemInfo> emblems = new ArrayList<>();
        public final String currentDungeon; // Simplified dungeon tracking

        // === Keywords affecting player ===
        public final Set<String> playerKeywords = new HashSet<>();

        public PlayerState(Player p, Game game) {
            this.name = p.getName();
            this.playerNumber = game.getPlayers().indexOf(p);
            this.isAI = p.getController().isAI();

            // Life tracking
            this.life = p.getLife();
            this.startingLife = p.getStartingLife();
            this.poisonCounters = p.getPoisonCounters();
            this.hasLost = p.hasLost();
            this.lostReason = p.getOutcome() != null ? p.getOutcome().toString() : null;

            // Commander damage - simplified tracking
            this.commanderDamage = new HashMap<>();
            for (Player opponent : game.getPlayers()) {
                if (opponent != p) {
                    for (Card cmd : opponent.getCardsIn(ZoneType.Command)) {
                        if (cmd.isCommander()) {
                            int damage = p.getCommanderDamage(cmd);
                            if (damage > 0) {
                                commanderDamage.put(cmd.getName() + " (" + opponent.getName() + ")", damage);
                            }
                        }
                    }
                }
            }

            // Counters
            this.counters = new HashMap<>(p.getCounters());
            this.energyCounters = getCounterAmount(p, CounterEnumType.ENERGY);
            this.experienceCounters = getCounterAmount(p, CounterEnumType.EXPERIENCE);
            this.radCounters = getCounterAmount(p, CounterEnumType.RAD);

            // Resources
            this.manaPool = new ManaPoolState(p.getManaPool());
            this.maxHandSize = p.getMaxHandSize();
            this.unlimitedHandSize = p.isUnlimitedHandSize();

            // Turn state
            this.turnState = new TurnState(p, game);

            // Zone counts
            this.zoneCounts = new HashMap<>();
            for (ZoneType zt : ZoneType.values()) {
                zoneCounts.put(zt, p.getCardsIn(zt).size());
            }

            // Flags
            if (game.getPhaseHandler().getPlayerTurn() == p) flags.add(PlayerFlag.ACTIVE_PLAYER);
            if (game.getPhaseHandler().getPriorityPlayer() == p) flags.add(PlayerFlag.HAS_PRIORITY);
            if (game.getMonarch() == p) flags.add(PlayerFlag.IS_MONARCH);
            if (p.hasBlessing()) flags.add(PlayerFlag.CITY_BLESSING);
            if (p.hasKeyword("Hexproof")) flags.add(PlayerFlag.HEXPROOF);
            if (p.hasKeyword("You can't lose the game")) flags.add(PlayerFlag.CANT_LOSE);

            // Track restrictions and permissions
            analyzeStaticAbilities(p, game);

            // Emblems
            for (Card emblem : p.getCardsIn(ZoneType.Command)) {
                if (emblem.getType().hasStringType("Emblem")) {
                    emblems.add(new EmblemInfo(emblem));
                }
            }

            // Simplified dungeon tracking
            this.currentDungeon = null; // Would need custom implementation
        }

        private void analyzeStaticAbilities(Player p, Game game) {
            // Analyze static abilities affecting this player
            for (Card c : game.getCardsInGame()) {
                for (StaticAbility sa : c.getStaticAbilities()) {
                    if (affectsPlayer(sa, p)) {
                        String desc = sa.toString();
                        if (desc.contains("can't") || desc.contains("cannot")) {
                            restrictions.add(new PlayerRestriction(desc, c));
                        } else if (desc.contains("may")) {
                            permissions.add(new PlayerPermission(desc, c));
                        }
                    }
                }
            }
        }

        private boolean affectsPlayer(StaticAbility sa, Player p) {
            // Simplified check - would need full implementation
            String affected = sa.getParam("Affected");
            return affected != null && affected.contains("Player");
        }

        private static int getCounterAmount(Player p, CounterEnumType type) {
            CounterType ct = CounterType.get(type);
            Integer amount = p.getCounters().get(ct);
            return amount != null ? amount : 0;
        }
    }

    public enum PlayerFlag {
        ACTIVE_PLAYER,
        HAS_PRIORITY,
        IS_MONARCH,
        HAS_INITIATIVE,
        CITY_BLESSING,
        HEXPROOF,
        CANT_LOSE,
        TAKING_EXTRA_TURN
    }

    /**
     * Turn-specific state tracking
     */
    public static class TurnState {
        public final int landsPlayedThisTurn;
        public final int landsAllowedThisTurn;
        public final int spellsCastThisTurn;
        public final int creaturesCastThisTurn;
        public final int nonCreaturesCastThisTurn;
        public final int cardsDrawnThisTurn;
        public final boolean declaredAttackersThisTurn;
        public final boolean declaredBlockersThisTurn;
        public final int combatDamageDealtThisTurn;
        public final List<String> spellsCastThisTurnNames;

        public TurnState(Player p, Game game) {
            this.landsPlayedThisTurn = p.getLandsPlayedThisTurn();
            this.landsAllowedThisTurn = p.getMaxLandPlays();
            this.spellsCastThisTurn = p.getSpellsCastThisTurn();
            this.cardsDrawnThisTurn = p.getNumDrawnThisTurn();

            // These would need custom tracking in Forge
            this.creaturesCastThisTurn = 0; // Would need to track
            this.nonCreaturesCastThisTurn = 0; // Would need to track
            this.declaredAttackersThisTurn = false; // Would need to track
            this.declaredBlockersThisTurn = false; // Would need to track
            this.combatDamageDealtThisTurn = 0; // Would need to track
            this.spellsCastThisTurnNames = new ArrayList<>(); // For storm tracking
        }
    }

    /**
     * Enhanced mana pool state with restrictions tracking
     */
    public static class ManaPoolState {
        public final Map<Byte, Integer> manaByColor;
        public final int totalMana;
        public final List<ManaRestriction> restrictions;
        public final Map<String, Integer> conditionalMana; // "Spend only on creatures", etc

        public ManaPoolState(ManaPool pool) {
            this.manaByColor = new HashMap<>();
            this.restrictions = new ArrayList<>();
            this.conditionalMana = new HashMap<>();

            int total = 0;
            for (byte color : ManaAtom.MANATYPES) {
                int amount = pool.getAmountOfColor(color);
                if (amount > 0) {
                    manaByColor.put(color, amount);
                    total += amount;
                }
            }
            this.totalMana = total;

            // Would need to track mana restrictions
            // pool.getManaRestrictions() or similar
        }

        public String toManaString() {
            if (totalMana == 0) return "Empty";

            StringBuilder sb = new StringBuilder();
            appendColoredMana(sb, (byte)ManaAtom.WHITE, "W");
            appendColoredMana(sb, (byte)ManaAtom.BLUE, "U");
            appendColoredMana(sb, (byte)ManaAtom.BLACK, "B");
            appendColoredMana(sb, (byte)ManaAtom.RED, "R");
            appendColoredMana(sb, (byte)ManaAtom.GREEN, "G");
            appendColoredMana(sb, (byte)ManaAtom.COLORLESS, "C");
            return sb.toString();
        }

        private void appendColoredMana(StringBuilder sb, byte color, String symbol) {
            Integer amount = manaByColor.get(color);
            if (amount != null && amount > 0) {
                for (int i = 0; i < amount; i++) {
                    sb.append("{").append(symbol).append("}");
                }
            }
        }
    }

    public static class ManaRestriction {
        public final String restriction;
        public final String source;
        public final int amount;

        public ManaRestriction(String restriction, String source, int amount) {
            this.restriction = restriction;
            this.source = source;
            this.amount = amount;
        }
    }

    /**
     * Comprehensive card state aligned with Forge's CardState
     */
    public static class CardState {
        // === Core Properties ===
        public final int id;
        public final String name;
        public final String displayName; // May differ due to effects
        public final CardTypeView type;
        public final CardTypeView originalType;
        public final Set<String> subtypes;
        public final Set<String> supertypes;
        public final ManaCost manaCost;
        public final int cmc; // Converted mana cost
        public final byte color;
        public final byte originalColor;

        // === Ownership & Control ===
        public final String controller;
        public final String owner;
        public final boolean token;

        // === Zone-specific State ===
        public final ZoneType zone;
        public final int zonePosition; // Order in zone
        public final boolean tapped;
        public final boolean flipped;
        public final boolean faceDown;
        public final boolean phased;
        public final CardStateName currentState; // Original, Transformed, Adventure, etc

        // === Combat State ===
        public final boolean attacking;
        public final boolean blocking;
        public final String attackingTarget; // Player or planeswalker
        public final List<Integer> blockedBy;
        public final List<Integer> blockingCards; // Renamed from 'blocking' to avoid conflict
        public final boolean mustAttack;
        public final boolean mustBlock;

        // === P/T and Damage ===
        public final int basePower;
        public final int baseToughness;
        public final int power; // Current power
        public final int toughness; // Current toughness
        public final int damage;
        public final int combatDamage;
        public final int nonCombatDamage;

        // === Counters ===
        public final Map<CounterType, Integer> counters;
        public final int loyaltyCounters; // For planeswalkers

        // === Keywords & Abilities ===
        public final Set<String> keywords;
        public final Set<String> intrinsicKeywords;
        public final List<AbilityInfo> abilities;
        public final List<TriggerInfo> triggers;
        public final List<ReplacementInfo> replacements;
        public final List<StaticAbilityInfo> staticAbilities;

        // === Attachments & Relationships ===
        public final Integer attachedTo;
        public final List<Integer> attachments;
        public final List<Integer> enchantedBy;
        public final Integer pairedWith;
        public final List<Integer> mergedCards; // For mutate

        // === History & Tracking ===
        public final boolean summoningSick;
        public final int turnInPlay;
        public final boolean etbThisTurn;
        public final int timesCast;
        public final List<String> chosenColors;
        public final List<String> chosenTypes;
        public final String chosenName;
        public final Map<String, Object> remembered; // For cards that remember things
        public final List<Integer> exiledWith; // Cards exiled by this

        // === Special Properties ===
        public final boolean legendary;
        public final boolean historic;
        public final boolean commander;
        public final boolean mdfcBackSide;
        public final Integer castX; // X value if cast with X

        public CardState(Card c, ZoneType zone, int position) {
            // Core properties
            this.id = c.getId();
            this.name = c.getName();
            this.displayName = c.getName(); // Simplified - no getDisplayName()
            this.type = c.getType();
            this.originalType = c.getType(); // Simplified - no separate original type tracking
            this.subtypes = new HashSet<>((Collection) c.getType().getSubtypes());
            this.supertypes = new HashSet<>();
            for (CardType.Supertype st : c.getType().getSupertypes()) {
                this.supertypes.add(st.name());
            }
            this.manaCost = c.getManaCost();
            this.cmc = c.getCMC();
            this.color = c.getColor().getColor();
            this.originalColor = c.getColor().getColor(); // Simplified

            // Ownership
            this.controller = c.getController().getName();
            this.owner = c.getOwner().getName();
            this.token = c.isToken();

            // Zone state
            this.zone = zone;
            this.zonePosition = position;
            this.tapped = c.isTapped();
            this.flipped = c.isFlipped();
            this.faceDown = c.isFaceDown();
            this.phased = c.isPhasedOut();
            this.currentState = c.getCurrentStateName();

            // Combat
            Combat combat = c.getGame().getCombat();
            this.attacking = combat != null && c.isAttacking();
            this.blocking = combat != null && c.getBlockedThisTurn() != null;
            this.attackingTarget = getAttackingTarget(c, combat);
            this.blockedBy = getBlockers(c, combat);
            this.blockingCards = getBlocking(c, combat);
            this.mustAttack = false; // Simplified
            this.mustBlock = checkMustBlock(c);

            // P/T
            if (c.isCreature()) {
                this.basePower = c.getBasePower();
                this.baseToughness = c.getBaseToughness();
                this.power = c.getNetPower();
                this.toughness = c.getNetToughness();
            } else {
                this.basePower = -1;
                this.baseToughness = -1;
                this.power = -1;
                this.toughness = -1;
            }

            // Damage
            this.damage = c.getDamage();
            this.combatDamage = c.getDamage(); // Simplified - no separate combat damage tracking
            this.nonCombatDamage = 0;

            // Counters
            this.counters = new HashMap<>(c.getCounters());
            this.loyaltyCounters = c.isPlaneswalker() ?
                    c.getCounters(CounterType.get(CounterEnumType.LOYALTY)) : 0;

            // Keywords
            this.keywords = c.getKeywords().stream()
                    .map(KeywordInterface::getOriginal)
                    .collect(Collectors.toSet());
            this.intrinsicKeywords = new HashSet<>(); // Simplified

            // Abilities - simplified for now
            this.abilities = extractAbilities(c);
            this.triggers = extractTriggers(c);
            this.replacements = extractReplacements(c);
            this.staticAbilities = extractStaticAbilities(c);

            // Relationships
            this.attachedTo = c.getAttachedTo() != null ? c.getAttachedTo().getId() : null;
            this.attachments = c.getAttachedCards().stream()
                    .map(Card::getId)
                    .collect(Collectors.toList());
            this.enchantedBy = c.getEnchantedBy().stream()
                    .map(Card::getId)
                    .collect(Collectors.toList());
            this.pairedWith = c.getPairedWith() != null ? c.getPairedWith().getId() : null;
            this.mergedCards = c.getMergedCards() != null ?
                    c.getMergedCards().stream().map(Card::getId).collect(Collectors.toList()) :
                    new ArrayList<>();

            // History
            this.summoningSick = c.hasSickness();
            this.turnInPlay = c.getTurnInZone();
            this.etbThisTurn = c.getTurnInZone() == c.getGame().getPhaseHandler().getTurn();
            this.timesCast = 0; // Simplified - no getTimesCast()

            // Choices and memory
            this.chosenColors = c.getChosenColors() != null ?
                    new ArrayList<>((Collection) c.getChosenColors()) : new ArrayList<>();
            this.chosenTypes = c.getChosenType() != null ?
                    Arrays.asList(c.getChosenType().split(" ")) : new ArrayList<>();
            this.chosenName = c.getNamedCard();
            this.remembered = new HashMap<>(); // Would need to extract from c.getRemembered()
            this.exiledWith = new ArrayList<>(); // Would need tracking

            // Special properties
            this.legendary = c.getType().isLegendary();
            this.historic = c.isHistoric();
            this.commander = c.isCommander();
            this.mdfcBackSide = false; // Simplified
            this.castX = c.getXManaCostPaid();
        }

        private String getAttackingTarget(Card c, Combat combat) {
            if (combat == null || !c.isAttacking()) return null;
            GameEntity defender = combat.getDefenderByAttacker(c);
            if (defender instanceof Player) {
                return ((Player) defender).getName() + " (Player)";
            } else if (defender instanceof Card) {
                return ((Card) defender).getName() + " [" + ((Card) defender).getId() + "]";
            }
            return null;
        }

        private List<Integer> getBlockers(Card c, Combat combat) {
            if (combat == null) return new ArrayList<>();
            return combat.getBlockers(c).stream()
                    .map(Card::getId)
                    .collect(Collectors.toList());
        }

        private List<Integer> getBlocking(Card c, Combat combat) {
            if (combat == null || c.getBlockedThisTurn() == null) return new ArrayList<>();
            return combat.getAttackersBlockedBy(c).stream()
                    .map(Card::getId)
                    .collect(Collectors.toList());
        }

        private boolean checkMustBlock(Card c) {
            // Simplified - would need full implementation
            return c.hasKeyword("CARDNAME blocks each combat if able");
        }

        private List<AbilityInfo> extractAbilities(Card c) {
            List<AbilityInfo> result = new ArrayList<>();
            for (SpellAbility sa : c.getSpellAbilities()) {
                result.add(new AbilityInfo(sa));
            }
            return result;
        }

        private List<TriggerInfo> extractTriggers(Card c) {
            List<TriggerInfo> result = new ArrayList<>();
            for (Trigger t : c.getTriggers()) {
                result.add(new TriggerInfo(t));
            }
            return result;
        }

        private List<ReplacementInfo> extractReplacements(Card c) {
            List<ReplacementInfo> result = new ArrayList<>();
            for (ReplacementEffect re : c.getReplacementEffects()) {
                result.add(new ReplacementInfo(re));
            }
            return result;
        }

        private List<StaticAbilityInfo> extractStaticAbilities(Card c) {
            List<StaticAbilityInfo> result = new ArrayList<>();
            for (StaticAbility sa : c.getStaticAbilities()) {
                result.add(new StaticAbilityInfo(sa));
            }
            return result;
        }
    }

    /**
     * Ability information
     */
    public static class AbilityInfo {
        public final String description;
        public final ApiType api;
        public final boolean isManaAbility;
        public final boolean isActivated;
        public final ManaCost cost;
        public final boolean canPlay;

        public AbilityInfo(SpellAbility sa) {
            this.description = sa.getDescription();
            this.api = sa.getApi();
            this.isManaAbility = sa.isManaAbility();
            this.isActivated = sa.isActivatedAbility();
            this.cost = sa.getPayCosts() != null ? sa.getPayCosts().getTotalMana() : null;
            this.canPlay = sa.canPlay();
        }
    }

    /**
     * Trigger information
     */
    public static class TriggerInfo {
        public final String description;
        public final TriggerType type;
        public final boolean mandatory;

        public TriggerInfo(Trigger t) {
            this.description = t.toString();
            this.type = t.getMode();
            this.mandatory = true; // Simplified - isOptional() doesn't exist
        }
    }

    /**
     * Replacement effect information
     */
    public static class ReplacementInfo {
        public final String description;
        public final String layer;

        public ReplacementInfo(ReplacementEffect re) {
            this.description = re.getDescription();
            this.layer = re.getLayer() != null ? re.getLayer().toString() : "None";
        }
    }

    /**
     * Static ability information
     */
    public static class StaticAbilityInfo {
        public final String description;
        public final Map<String, String> params;
        public final boolean affecting;

        public StaticAbilityInfo(StaticAbility sa) {
            this.description = sa.toString();
            this.params = sa.getMapParams();
            this.affecting = true; // Simplified - isRelevant() doesn't exist
        }
    }

    /**
     * Enhanced stack item with mode tracking
     */
    public static class StackItem {
        public final int id;
        public final String description;
        public final String source;
        public final int sourceId;
        public final String controller;
        public final List<String> targets;
        public final Map<String, String> choices; // Modes, X values, etc
        public final boolean isCopy;
        public final Integer copyOf;

        private static int nextId = 1;

        public StackItem(SpellAbilityStackInstance si) {
            this.id = nextId++;
            SpellAbility sa = si.getSpellAbility();
            this.description = sa.getStackDescription();
            this.source = sa.getHostCard() != null ? sa.getHostCard().getName() : "Unknown";
            this.sourceId = sa.getHostCard() != null ? sa.getHostCard().getId() : -1;
            this.controller = si.getActivatingPlayer().getName();

            // Extract targets
            this.targets = new ArrayList<>();
            TargetChoices tc = si.getTargetChoices();
            if (tc != null && !tc.isEmpty()) {
                // Store target description since we can't get individual targets
                targets.add(tc.toString());
            }

            // Extract choices
            this.choices = new HashMap<>();
            if (sa.getParam("Mode") != null) {
                choices.put("Mode", sa.getParam("Mode"));
            }
            if (sa.getXManaCostPaid() != null) {
                choices.put("X", sa.getXManaCostPaid().toString());
            }

            this.isCopy = false; // Simplified - isCopy() doesn't exist
            this.copyOf = null;
        }
    }

    /**
     * Enhanced combat state - keeping the existing implementation
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
        public final int combatNumber;
        public final Map<String, String> manaPools;
        public final Map<String, Integer> lifeTotals;
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
                        totalDamage += attacker.getNetPower();
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

        public static class AttackerInfo {
            public final int id;
            public final String name;
            public final int power;
            public final int toughness;
            public final List<String> relevantKeywords;
            public final boolean tapped;
            public final int damage;
            public final boolean isBlocked;
            public final List<Integer> blockerIds;

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

        public static class BlockerInfo {
            public final int id;
            public final String name;
            public final int power;
            public final int toughness;
            public final boolean canBlockAdditional;
            public final int damage;
            public final List<Integer> blockingAttackerIds;
            public final boolean hasRegenerationShield;
            public final boolean hasReach;

            public BlockerInfo(Card blocker) {
                this.id = blocker.getId();
                this.name = blocker.getName();
                this.power = blocker.getNetPower();
                this.toughness = blocker.getNetToughness();
                this.damage = blocker.getDamage();

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
     * Player restriction tracking
     */
    public static class PlayerRestriction {
        public final String description;
        public final String source;

        public PlayerRestriction(String desc, Card source) {
            this.description = desc;
            this.source = source.getName() + " [" + source.getId() + "]";
        }
    }

    /**
     * Player permission tracking
     */
    public static class PlayerPermission {
        public final String description;
        public final String source;

        public PlayerPermission(String desc, Card source) {
            this.description = desc;
            this.source = source.getName() + " [" + source.getId() + "]";
        }
    }

    /**
     * Emblem information
     */
    public static class EmblemInfo {
        public final String name;
        public final String controller;
        public final String text;
        public final String source; // Planeswalker that created it

        public EmblemInfo(Card emblem) {
            this.name = emblem.getName();
            this.controller = emblem.getController().getName();
            this.text = emblem.getRules() != null ? emblem.getRules().getOracleText() : "";
            this.source = extractSource(emblem);
        }

        private String extractSource(Card emblem) {
            // Extract planeswalker name from emblem name
            // "Teferi Emblem" -> "Teferi"
            String name = emblem.getName();
            if (name.endsWith(" Emblem")) {
                return name.substring(0, name.length() - 7);
            }
            return "Unknown";
        }
    }

    /**
     * Continuous effect tracking
     */
    public static class ContinuousEffect {
        public final String source;
        public final int sourceId;
        public final String effect;
        public final String affected;
        public final long timestamp;

        public ContinuousEffect(Card source, StaticAbility sa) {
            this.source = source.getName();
            this.sourceId = source.getId();
            this.effect = sa.toString();
            this.affected = sa.getParam("Affected");
            this.timestamp = source.getGame().getTimestamp();
        }
    }

    /**
     * Delayed trigger tracking
     */
    public static class DelayedTrigger {
        public final String description;
        public final String expires;
        public final String source;

        public DelayedTrigger(String desc, String expires, String source) {
            this.description = desc;
            this.expires = expires;
            this.source = source;
        }
    }

    /**
     * State delta computation remains the same but enhanced
     */
    public static class StateDelta {
        public final List<String> changes = new ArrayList<>();
        public final Map<String, List<String>> categorizedChanges = new HashMap<>();

        public void addChange(String category, String description) {
            changes.add("[" + category + "] " + description);
            categorizedChanges.computeIfAbsent(category, k -> new ArrayList<>()).add(description);
        }

        public boolean hasChanges() {
            return !changes.isEmpty();
        }

        public boolean hasCategory(String category) {
            return categorizedChanges.containsKey(category);
        }
    }
}