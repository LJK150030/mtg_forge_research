package APF;

import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.game.card.Card;
import forge.game.mana.ManaPool;
import forge.card.mana.ManaAtom;

import java.util.*;
import java.util.logging.Logger;

/**
 * Seed NounInstances for one Forge match: players, cards, zones.
 *
 * Usage:
 *   KnowledgeBase kb = KnowledgeBase.getInstance();
 *   ForgeBuilders.CardInstanceFactory cardFactory =
 *       new ForgeBuilders.CardInstanceFactory(
 *           new ForgeBuilders.CardDefinitionBuilder("res/cardsfolder", kb)
 *       );
 *   String matchId = "M-" + game.getTimestamp();  // or any id you prefer
 *   new MatchInstanceSeeder(kb, cardFactory).seedAll(game, matchId);
 */
public final class MatchInstanceSeeder {
    private static final Logger LOG = Logger.getLogger(MatchInstanceSeeder.class.getName());

    private final KnowledgeBase kb;
    private final ForgeFactory.CardInstanceFactory cardFactory;

    public MatchInstanceSeeder(KnowledgeBase kb, ForgeFactory.CardInstanceFactory cardFactory) {
        this.kb = kb;
        this.cardFactory = cardFactory;
    }

    /** Entry point: create player, card, and zone instances for a Game. */
    public void seedAll(Game game, String matchId) {
        LOG.info("Seeding KnowledgeBase with instances for match " + matchId);

        Map<Player, NounInstance> players = seedPlayers(game, matchId);
        Map<Card, NounInstance> cards    = seedCards(game, matchId); // via CardInstanceFactory
        seedZones(game, matchId, players, cards);                     // uses created card ids

        LOG.info(String.format("Seeding complete: %d players, %d cards.", players.size(), cards.size()));
    }

    /* =========================== Players ============================ */

    private Map<Player, NounInstance> seedPlayers(Game game, String matchId) {
        Map<Player, NounInstance> map = new LinkedHashMap<>();
        int idx = 1;
        for (Player p : game.getPlayers()) {
            String playerLabel = "Player_" + idx++;
            String objectId    = matchScoped(matchId, playerLabel);
            String className   = findExistingClass("Player"); // your Player NounDefinition

            try {
                Map<String,Object> overrides = new HashMap<>();
                overrides.put("name", p.getName());
                overrides.put("life", p.getLife());
                overrides.put("startingLife", p.getStartingLife());
                overrides.put("poisonCounters", p.getPoisonCounters());
                overrides.put("isAI", p.getController() != null && p.getController().isAI());
                overrides.put("maxHandSize", p.getMaxHandSize());
                Map<String,Integer> counters = new LinkedHashMap<>();
                p.getCounters().forEach((ct, amt) -> counters.put(ct.toString(), amt));
                overrides.put("counters", counters);
                Map<String,Integer> mana = snapshotMana(p.getManaPool());
                if (!mana.isEmpty()) overrides.put("manaPool", mana);

                NounInstance inst =  kb.createInstance(className, objectId);
                inst.updateProperties(overrides);

                inst.getMetadata().put("matchId", matchId);
                inst.getMetadata().put("forgePlayerName", p.getName());

                map.put(p, inst);
            } catch (Exception ex) {
                LOG.warning("Failed to create Player instance for " + p.getName() + ": " + ex.getMessage());
            }
        }
        return map;
    }

    private Map<String,Integer> snapshotMana(ManaPool pool) {
        Map<String,Integer> mana = new LinkedHashMap<>();
        if (pool == null) return mana;
        for (byte c : ManaAtom.MANATYPES) {
            int amount = pool.getAmountOfColor(c);
            if (amount <= 0) continue;
            String key;
            switch (c) {
                case ManaAtom.WHITE: key = "W"; break;
                case ManaAtom.BLUE: key = "U"; break;
                case ManaAtom.BLACK: key = "B"; break;
                case ManaAtom.RED: key = "R"; break;
                case ManaAtom.GREEN: key = "G"; break;
                case ManaAtom.COLORLESS: default: key = "C"; break;
            }
            mana.put(key, amount);
        }
        return mana;
    }

    /* ============================ Cards ============================ */

    private Map<Card, NounInstance> seedCards(Game game, String matchId) {
        Map<Card, NounInstance> created = new LinkedHashMap<>();

        // Gather all cards from per-player zones
        Set<Card> allCards = new LinkedHashSet<>();
        for (Player p : game.getPlayers()) {
            for (ZoneType zt : perPlayerZones()) {
                Zone z = p.getZone(zt);
                if (z != null) for (Card c : z) allCards.add(c);
            }
        }
        // Plus battlefield (global)
        try {
            for (Card c : game.getCardsIn(ZoneType.Battlefield)) allCards.add(c);
        } catch (Throwable ignored) {}

        // Create/reuse instances via CardInstanceFactory
        for (Card c : allCards) {
            try {
                NounInstance inst = cardFactory.getInstance(c);
                if (inst == null) inst = cardFactory.createCardInstance(c);
                if (inst != null) {
                    inst.getMetadata().put("matchId", matchId);
                    created.put(c, inst);
                }
            } catch (Exception ex) {
                LOG.warning("Failed to create card instance for " + c.getName() + ": " + ex.getMessage());
            }
        }
        return created;
    }

    /* ============================= Zones ============================ */

    private void seedZones(Game game, String matchId,
                           Map<Player, NounInstance> players,
                           Map<Card, NounInstance> cardMap) {

        // Per-player zones (Hand/Library/Graveyard/Exile/Sideboard)
        for (Map.Entry<Player, NounInstance> e : players.entrySet()) {
            Player p = e.getKey();
            String ownerEnum = ownerEnumFor(game, p); // "Player_1" / "Player_2"
            for (ZoneType zt : perPlayerZones()) {
                Zone zone = p.getZone(zt);
                if (zone == null) continue;

                String className = findExistingClass(zoneClass(zt)); // e.g., "Zone_Hand"
                String objectId  = matchScoped(matchId, zoneClass(zt) + "@" + ownerEnum);

                List<String> contents = new ArrayList<>();
                for (Card c : zone) {
                    NounInstance ci = cardMap.get(c);
                    if (ci != null) contents.add(ci.getObjectId());
                }
                Map<String,Object> overrides = new HashMap<>();
                overrides.put("owner", ownerEnum);
                overrides.put("contents", contents);

                NounInstance zi =  kb.createInstance(className, objectId);
                zi.updateProperties(overrides);
                zi.getMetadata().put("matchId", matchId);
            }
        }

        // Global battlefield (owner = "NULL")
        try {
            String className = findExistingClass(zoneClass(ZoneType.Battlefield));
            String objectId  = matchScoped(matchId, zoneClass(ZoneType.Battlefield));
            List<String> contents = new ArrayList<>();
            for (Card c : game.getCardsIn(ZoneType.Battlefield)) {
                NounInstance ci = cardMap.get(c);
                if (ci != null) contents.add(ci.getObjectId());
            }
            Map<String,Object> overrides = new HashMap<>();
            overrides.put("owner", "NULL");
            overrides.put("contents", contents);
            NounInstance zi  = kb.createInstance(className, objectId);
            zi.updateProperties(overrides);

            zi.getMetadata().put("matchId", matchId);
        } catch (Throwable ignored) {}
    }

    /* ============================ Helpers ============================ */

    private static String matchScoped(String matchId, String plainId) {
        return matchId + "::" + plainId;
    }

    private static Set<ZoneType> perPlayerZones() {
        return EnumSet.of(ZoneType.Library, ZoneType.Hand, ZoneType.Graveyard, ZoneType.Exile, ZoneType.Sideboard);
    }

    private static String zoneClass(ZoneType zt) {
        // "Zone_Hand", "Zone_Library", ...
        String name = zt.name().substring(0,1).toUpperCase() + zt.name().substring(1).toLowerCase();
        return "Zone_" + name;
    }

    private String findExistingClass(String preferred) {
        if (kb.getNounDefinitions().containsKey(preferred)) return preferred;
        for (String k : kb.getNounDefinitions().keySet()) {
            if (k.equalsIgnoreCase(preferred) || k.startsWith(preferred)) return k;
        }
        throw new IllegalStateException("No NounDefinition registered for class: " + preferred);
    }

    private String ownerEnumFor(Game game, Player p) {
        int idx = game.getPlayers().indexOf(p) + 1;
        return "Player_" + Math.max(idx, 1);
    }

    private static void safeSet(NounInstance inst, String prop, Object value) {
        try {
            inst.setProperty(prop, value);
        } catch (IllegalArgumentException ignored) {
            // Property not defined on this definition — skip
        }
    }
}

