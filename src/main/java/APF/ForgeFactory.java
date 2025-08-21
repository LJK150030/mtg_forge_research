package APF;

import forge.game.card.Card;
import forge.game.card.CounterType;
import forge.game.keyword.KeywordInterface;
import forge.game.player.Player;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;

import java.util.*;
import java.util.logging.Logger;

import static APF.magic_commons.KEYWORD_ABILITY_SET;

/**
 * === Instance Factories for Zone and Player ===
 * Add this to the same file where CardInstanceFactory lives (e.g., ForgeBuilders.java).
 */
public final class ForgeFactory {
    private ForgeFactory() {}

    /* ----------------------------- Common helpers ----------------------------- */

    private static void safeSet(NounInstance inst, String prop, Object value, Logger log) {
        try {
            inst.setProperty(prop, value);
        } catch (IllegalArgumentException ex) {
            // Property not present on this definition (or domain mismatch) — ignore but log once
            if (log != null) log.fine("Skipping set of '" + prop + "' on " + inst.getObjectId() + ": " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T safeGet(NounInstance inst, String prop, Class<T> type) {
        try {
            return inst.getPropertyAs(prop, type);
        } catch (Exception ignored) {
            return null;
        }
    }


    public static class CardInstanceFactory {
        private static final Logger LOGGER = Logger.getLogger(CardInstanceFactory.class.getName());
        private final KnowledgeBase knowledgeBase;
        private final Map<Card, NounInstance> cardToInstance = new HashMap<>();
        private final Map<NounInstance, Card> instanceToCard = new HashMap<>();

        public CardInstanceFactory() {
            this.knowledgeBase = KnowledgeBase.getInstance();
        }

        private NounDefinition resolveDefinitionFor(Card card) {
            String cardName = card.getName();
            // same sanitization used by CardDefinitionBuilder
            String className = "Card_" + cardName.replaceAll("[^a-zA-Z0-9]", "_");
            NounDefinition def = knowledgeBase.getNounDefinitions().get(className);
            if (def == null) {
                LOGGER.warning("No definition found for class: " + className + " (from card: " + cardName + ")");
            }
            return def;
        }

        public NounInstance createCardInstance(Card card) {
            NounDefinition definition = resolveDefinitionFor(card);
            if (definition == null) return null;

            String instanceId = generateInstanceId(card);
            Map<String, Object> initialValues = extractCardProperties(card);

            NounInstance instance = knowledgeBase.createInstance(definition.getClassName(), instanceId);
            instance.updateProperties(initialValues);

            cardToInstance.put(card, instance);
            instanceToCard.put(instance, card);
            return instance;
        }
        /**
         * Extract properties from a Card object to match schema in CardDefinitionBuilder
         */
        private Map<String, Object> extractCardProperties(Card card) {
            Map<String, Object> properties = new HashMap<>();

            // Identity
            properties.put("name", card.getName());

            // Mana cost and CMC
            if (card.getManaCost() != null) {
                String mc = String.valueOf(card.getManaCost());
                if (mc != null && !mc.isBlank()) {
                    properties.put("manaCost", mc);
                    properties.put("convertedManaCost", card.getCMC());
                }
            }


            // Creature-specific
            if (card.isCreature()) {
                properties.put("power", String.valueOf(card.getNetPower()));
                properties.put("toughness", String.valueOf(card.getNetToughness()));
            }

            // Keywords
            List<String> keywords = new ArrayList<>();
            for (KeywordInterface kw : card.getKeywords()) {
                String canon = canonicalizeKeyword(kw.toString());
                if (canon != null) keywords.add(canon);
            }
            properties.put("keywords", keywords);
            // Oracle text
            properties.put("oracleText", card.getOracleText());

            // Color identity (use same helper as CardDefinitionBuilder)
            properties.put(
                    "colorIdentity",
                    ForgeBuilders.CardDefinitionBuilder.calculateColorIdentity(
                            card.getManaCost() != null ? card.getManaCost().toString() : "",
                            card.getOracleText()
                    )
            );

            // Game state properties
            if (card.getGame() != null) {
                properties.put("zone", List.of(card.getZone() != null ? card.getZone().getZoneType().name() : "Library"));
                properties.put("controller", List.of(playerEnum(card.getController())));
                properties.put("owner",      List.of(playerEnum(card.getOwner())));
                properties.put("tapped", card.isTapped());
                properties.put("summoningSick", card.hasSickness());

                // Counters
                Map<String, Integer> counters = new LinkedHashMap<>();
                for (Map.Entry<CounterType, Integer> entry : card.getCounters().entrySet()) {
                    counters.put(entry.getKey().toString(), entry.getValue());
                }
                properties.put("counters", counters);
            }

            return properties;
        }

        private static String playerEnum(Player p) {
            if (p == null) return "NULL";
            int idx = p.getGame().getPlayers().indexOf(p) + 1;
            return "Player_" + Math.max(1, idx);
        }

        private String generateInstanceId(Card card) {
            if (card.getId() > 0) {
                return "card_" + card.getId() + "_" + System.currentTimeMillis();
            } else {
                return "card_" + card.getName().replaceAll("[^a-zA-Z0-9]", "_") +
                        "_" + UUID.randomUUID().toString().substring(0, 8);
            }
        }

        /**
         * Update a NounInstance when a Card's state changes
         */
        public void updateCardInstance(Card card) {
            NounInstance instance = cardToInstance.get(card);
            if (instance == null) {
                LOGGER.warning("No instance found for card: " + card.getName());
                return;
            }

            Map<String, Object> updatedProperties = extractCardProperties(card);
            instance.updateProperties(updatedProperties);

            LOGGER.fine("Updated NounInstance for card: " + card.getName());
        }

        /**
         * Get NounInstance for a Card
         */
        public NounInstance getInstance(Card card) {
            return cardToInstance.get(card);
        }

        /**
         * Get Card for a NounInstance
         */
        public Card getCard(NounInstance instance) {
            return instanceToCard.get(instance);
        }

        /**
         * Remove instance when card is removed from game
         */
        public void removeCardInstance(Card card) {
            NounInstance instance = cardToInstance.remove(card);
            if (instance != null) {
                instanceToCard.remove(instance);
                LOGGER.fine("Removed NounInstance for card: " + card.getName());
            }
        }

        /**
         * Get all card instances
         */
        public Collection<NounInstance> getAllInstances() {
            return Collections.unmodifiableCollection(cardToInstance.values());
        }

        /**
         * Query instances by property
         */
        public List<NounInstance> queryInstances(String propertyName, Object value) {
            List<NounInstance> results = new ArrayList<>();

            for (NounInstance instance : cardToInstance.values()) {
                Object propValue = instance.getProperty(propertyName);
                if (propValue != null && propValue.equals(value)) {
                    results.add(instance);
                }
            }

            return results;
        }

        /**
         * Query instances by type
         */
        public List<NounInstance> queryByType(String cardType) {
            List<NounInstance> results = new ArrayList<>();
            for (NounInstance instance : cardToInstance.values()) {
                List<String> types = instance.getPropertyAs("types.cardTypes", List.class);
                if (types != null && types.contains(cardType)) {
                    results.add(instance);
                }
            }
            return results;
        }

        /**
         * Query creatures with specific power/toughness
         */
        public List<NounInstance> queryCreatures(int minPower, int minToughness) {
            List<NounInstance> results = new ArrayList<>();

            for (NounInstance instance : cardToInstance.values()) {
                Boolean isCreature = instance.getPropertyAs("isCreature", Boolean.class);
                if (Boolean.TRUE.equals(isCreature)) {
                    try {
                        String powerStr = instance.getPropertyAs("power", String.class);
                        String toughnessStr = instance.getPropertyAs("toughness", String.class);

                        if (powerStr != null && toughnessStr != null) {
                            int power = Integer.parseInt(powerStr);
                            int toughness = Integer.parseInt(toughnessStr);

                            if (power >= minPower && toughness >= minToughness) {
                                results.add(instance);
                            }
                        }
                    } catch (NumberFormatException e) {
                        // Handle special cases like * or X
                        continue;
                    }
                }
            }

            return results;
        }

        private static String canonicalizeKeyword(String raw) {
            if (raw == null) return null;
            String s = raw.trim();

            // strip bracket noise like "[...]" if present
            if (s.startsWith("[") && s.endsWith("]") && s.length() >= 2) {
                s = s.substring(1, s.length() - 1).trim();
            }

            String u = s.toUpperCase(java.util.Locale.ROOT);

            // not a keyword: drop common static/ability phrases
            if (u.contains("ENTERS THE BATTLEFIELD TAPPED")) return null;

            // normalize "Enchant:creature", "Enchant creature", etc.
            if (u.startsWith("ENCHANT")) return "Enchant";

            // keep only canonical keywords your domain allows
            return KEYWORD_ABILITY_SET.contains(s) ? s : null;
        }
    }





    /* ----------------------------- Zone Instances ----------------------------- */

    public class ZoneInstanceFactory {
        private static final Logger LOGGER = Logger.getLogger(ZoneInstanceFactory.class.getName());
        private final KnowledgeBase kb;

        public ZoneInstanceFactory() {
            this.kb = KnowledgeBase.getInstance();
        }

        private static String defNameFor(ZoneType type) {
            // Match your ZoneDefinitionBuilder className convention, e.g., "Zone_Hand", "Zone_Battlefield", etc.
            return "Zone_" + type.name();
        }

        /** Create (or mirror) a zone instance; override only what differs from defaults. */
        public NounInstance createZoneInstance(String zoneClassName, String ownerId) {
            Map<String, NounDefinition> defs = kb.getNounDefinitions();
            NounDefinition def = defs.get(zoneClassName);
            if (def == null) {
                throw new IllegalArgumentException("No zone definition found for class: " + zoneClassName);
            }

            boolean hasOwner = ownerId != null && !"NULL".equals(ownerId);
            String instanceId = hasOwner ? zoneClassName + "_" + ownerId : zoneClassName;

            // NEW: create with overrides in one shot
            Map<String, Object> overrides = new HashMap<>();
            overrides.put("owner", hasOwner ? ownerId : "NULL");  // will no-op if property doesn't exist

            NounInstance inst = kb.createInstance(zoneClassName, instanceId, overrides);

            // Ensure contents list exists if that property is part of the definition
            @SuppressWarnings("unchecked")
            List<String> contents = inst.getPropertyAs("contents", List.class);
            if (contents == null) {
                inst.setProperty("contents", new ArrayList<String>());
            }
            return inst;
        }
    }

    /** Makes NounInstances for Players based on Player_* definitions. */
    public class PlayerInstanceFactory {
        private static final Logger LOGGER = Logger.getLogger(PlayerInstanceFactory.class.getName());
        private final KnowledgeBase kb;
        private final Map<forge.game.player.Player, NounInstance> playerToInstance = new HashMap<>();
        private final Map<NounInstance, forge.game.player.Player> instanceToPlayer = new HashMap<>();

        public PlayerInstanceFactory() {
            this.kb = KnowledgeBase.getInstance();
        }

        /** Pick a player definition; adjust to your actual className(s). */
        private NounDefinition pickPlayerDefinition() {
            // Prefer a specific Player_* if present; otherwise fall back to "Player"
            for (String k : kb.getNounDefinitions().keySet()) {
                if (k.startsWith("Player_")) return kb.getDefinition(k);
            }
            NounDefinition def = kb.getDefinition("Player");
            if (def == null) throw new IllegalStateException("No Player definition registered");
            return def;
        }

        public NounInstance createPlayerInstance(String playerClassName, String playerId,
                                                 Integer startingLife,
                                                 Map<String,Integer> startingMana,
                                                 Map<String,Integer> startingCounters,
                                                 forge.game.player.Player forgePlayerRef) {
            // prepare overrides (life, mana, counters) as you already do...
            Map<String,Object> overrides = new HashMap<>();
            if (startingLife != null) overrides.put("life", startingLife);
            if (startingMana != null) overrides.put("manaPool", startingMana);
            if (startingCounters != null) overrides.put("counters", startingCounters);

            NounInstance inst = kb.createInstance(playerClassName, playerId, overrides);

            if (forgePlayerRef != null) {
                playerToInstance.put(forgePlayerRef, inst);
                instanceToPlayer.put(inst, forgePlayerRef);
            }
            return inst;
        }

        // convenient lookup for Zone factory
        public String resolvePlayerObjectId(forge.game.player.Player p) {
            NounInstance ni = playerToInstance.get(p);
            return ni != null ? ni.getObjectId() : null;
        }
    }

}
