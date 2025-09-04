package APF;

import forge.game.Game;
import forge.game.event.*;
import forge.game.player.Player;
import forge.game.card.Card;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * KnowledgeBase manages all NounDefinitions and NounInstances
 * Implements Visitor pattern to process game events
 */
public class KnowledgeBase implements IGameEventVisitor<Void> {
    private static final Logger LOGGER = Logger.getLogger(KnowledgeBase.class.getName());

    // Singleton instance
    private static KnowledgeBase instance;
    private ForgeFactory.CardInstanceFactory cardFactory;
    private ForgeFactory.PlayerInstanceFactory playerFactory;

    // Storage
    private final Map<String, NounDefinition> nounDefinitions;
    private final Map<String, NounInstance> nounInstances;
    private final Map<String, List<NounInstance>> instancesByClass;

    private final Map<String, VerbDefinition> verbDefinitions;


    private final List<VerbInstance> verbHistory;

    private final ForgeBuilders.Neo4jDefinitionBuilder neo4jDefinitionBuilder;
    private final ForgeFactory.Neo4jInstanceBuilder neo4jInstanceBuilder;

    // Event processing - custom processors for extensibility
    private Game subscribedGame;
    private KBEventListener eventListener;

    private KnowledgeBase() {
        this.nounDefinitions = new ConcurrentHashMap<>();
        this.nounInstances = new ConcurrentHashMap<>();
        this.instancesByClass = new ConcurrentHashMap<>();

        this.verbDefinitions = new ConcurrentHashMap<>();

        this.verbHistory = Collections.synchronizedList(new ArrayList<>());

        String cardsfolderPath = "res/cardsfolder"; // Adjust path as needed
        ForgeBuilders.CardDefinitionBuilder cardDefinitionBuilder = new ForgeBuilders.CardDefinitionBuilder(cardsfolderPath, this);
        cardDefinitionBuilder.buildAllCardDefinitions();
        ForgeBuilders.ZoneDefinitionBuilder zoneDefinitionBuilder = new ForgeBuilders.ZoneDefinitionBuilder(this);
        zoneDefinitionBuilder.buildAllZoneDefinitions();
        ForgeBuilders.PlayerDefinitionBuilder playerDefinitionBuilder = new ForgeBuilders.PlayerDefinitionBuilder(this);
        playerDefinitionBuilder.buildAllPlayerDefinitions();


        neo4jDefinitionBuilder = new ForgeBuilders.Neo4jDefinitionBuilder(this);
        neo4jInstanceBuilder = new ForgeFactory.Neo4jInstanceBuilder(this);

        neo4jDefinitionBuilder.clearAllDefinitions();
        neo4jDefinitionBuilder.createIndexes();
        neo4jDefinitionBuilder.buildAllNounDefinitions();
    }

    /**
     * Get singleton instance
     */
    public static KnowledgeBase getInstance() {
        if (instance == null) {
            synchronized (KnowledgeBase.class) {
                if (instance == null) {
                    instance = new KnowledgeBase();
                }
            }
        }
        return instance;
    }

    public void setCardFactory(ForgeFactory.CardInstanceFactory f) {
        this.cardFactory = f;
    }

    public void setPlayerFactory(ForgeFactory.PlayerInstanceFactory f) {
        this.playerFactory = f;
    }


    public ForgeFactory.CardInstanceFactory getCardFactory() {
        return cardFactory;
    }

    public ForgeFactory.PlayerInstanceFactory getPlayerFactory() {
        return playerFactory;
    }

    public VerbDefinition getVerb(String name) {
        return verbDefinitions.get(name);
    }

    public List<VerbInstance> getVerbHistory() {
        return verbHistory;
    }


    public void exportAllInstancesToNeo4j() {
        neo4jInstanceBuilder.createIndexes();
        neo4jInstanceBuilder.buildAllNounInstances();
    }

    /**
     * Register a NounDefinition
     */
    public void registerDefinition(NounDefinition definition) {
        nounDefinitions.put(definition.getClassName(), definition);
        instancesByClass.putIfAbsent(definition.getClassName(), new ArrayList<>());
        LOGGER.info("Registered definition: " + definition.getClassName());
    }

    private void registerCommonVerbs() {
        VerbDefinition tap = ForgeCommonVerbs.tapVerb();
        verbDefinitions.put(tap.name(), tap);
    }

    // inside class KnowledgeBase
    public NounInstance createNounInstance(String className, String objectId, Map<String, Object> initialValues) {
        NounDefinition definition = nounDefinitions.get(className);
        if (definition == null) {
            throw new IllegalArgumentException("No definition found for class: " + className);
        }
        NounInstance instance = definition.createInstance(objectId);
        if (initialValues != null && !initialValues.isEmpty()) {
            instance.updateProperties(initialValues);
        }
        nounInstances.put(objectId, instance);
        instancesByClass.get(className).add(instance);
        LOGGER.info("Created instance: " + objectId + " of class: " + className);
        return instance;
    }


    public NounInstance createNounInstance(String className, String objectId) {
        return createNounInstance(className, objectId, Collections.emptyMap());
    }

    public Map<String, NounDefinition> getNounDefinitions() {
        return nounDefinitions;
    }

    /**
     * Get an instance by ID
     */
    public NounInstance getInstance(String objectId) {
        return nounInstances.get(objectId);
    }

    /**
     * Get all instances of a class
     */
    public List<NounInstance> getInstancesByClass(String className) {
        return new ArrayList<>(instancesByClass.getOrDefault(className, Collections.emptyList()));
    }

    /**
     * Query instances with conditions
     */
    public List<NounInstance> query(String className, NounInstance.QueryCondition... conditions) {
        List<NounInstance> results = getInstancesByClass(className);

        for (NounInstance.QueryCondition condition : conditions) {
            results = results.stream()
                    .filter(instance -> instance.matches(condition))
                    .collect(Collectors.toList());
        }

        return results;
    }


    private NounInstance findPlayerByForgeName(String name) {
        for (List<NounInstance> list : instancesByClass.values()) {
            for (NounInstance ni : list) {
                Object n = ni.getProperty("name");
                if (n != null && n.toString().equals(name)) return ni;
            }
        }
        return null;
    }

    public synchronized void subscribeToGame(Game game) {
        if (game == null) return;
        // Already subscribed? Do nothing.
        if (this.subscribedGame == game && this.eventListener != null) return;

        // No explicit unsubscription—just avoid double registration.
        this.eventListener = new KBEventListener(this);
        game.subscribeToEvents(this.eventListener);
        this.subscribedGame = game;
        LOGGER.info("KnowledgeBase subscribed to Forge game events.");
    }

    public NounDefinition getDefinition(String className) {
        return nounDefinitions.get(className);
    }

    // ========== Visitor Pattern Implementation ==========
    // The visitor pattern handles dispatch - we implement logic directly in visit methods

    @Override
    public Void visit(GameEventCardChangeZone event) {
        // TODO: Get card ID from event
        // String cardId = event.getCard().getId();
        // NounInstance card = getInstance(cardId);
        // if (card != null) {
        //     String oldZone = (String) card.getProperty("zone");
        //     String newZone = event.getNewZone();
        //     card.setProperty("zone", newZone);
        //
        //     // Reset states when changing zones
        //     if (newZone.equals("Battlefield") && !oldZone.equals("Battlefield")) {
        //         card.setProperty("summoned", true);
        //         card.setProperty("tapped", false);
        //     } else if (!newZone.equals("Battlefield")) {
        //         card.setProperty("tapped", false);
        //         card.setProperty("attacking", false);
        //         card.setProperty("blocking", false);
        //         card.setProperty("damageMarked", 0);
        //     }
        //
        //     LOGGER.info("Card " + cardId + " moved from " + oldZone + " to " + newZone);
        // }
        return null;
    }

    @Override
    public Void visit(GameEventCardTapped event) {
        try {
            Card forgeCard = event.card;
            if (forgeCard == null || cardFactory == null) return null;

            // Locate or create the card’s NounInstance
            NounInstance cardNI = cardFactory.getInstance(forgeCard);
            if (cardNI == null) {
                // Fallback: if not yet present in KB (rare mid-game), create it
                cardNI = cardFactory.createCardInstance(forgeCard);
            }
            if (cardNI == null) return null;

            // Sync the simple state so your KB stays truthful to Forge
            boolean tappedNow;
            try {
                tappedNow = event.tapped;     // preferred if available
            } catch (Throwable t) {
                tappedNow = forgeCard.isTapped(); // fallback
            }
            cardNI.setProperty("tapped", tappedNow);

            // Try to attribute the action to a player (controller at event time is usually correct)
            Player forgeActor = forgeCard.getController();
            NounInstance actorNI = null;
            if (playerFactory != null && forgeActor != null) {
                // If you seeded via PlayerInstanceFactory, you can keep a reverse map.
                String id = playerFactory.resolvePlayerObjectId(forgeActor);
                if (id != null) actorNI = getInstance(id);
            }
            if (actorNI == null && forgeActor != null) {
                actorNI = findPlayerByForgeName(forgeActor.getName());
            }
            if (actorNI == null) {
                // As a last resort, attribute to the permanent itself (useful for analytics over “what got tapped”).
                actorNI = cardNI;
            }

            // Build/bind a *Tap* VerbInstance for analytics/AI memory
            VerbDefinition tap = ForgeCommonVerbs.tapVerb();
            VerbInstance vi = tap.bind(actorNI, List.of(cardNI), this);

            // (Optional) Record your own structured event alongside storing the VerbInstance
            Map<String, Object> payload = new HashMap<>();
            payload.put("verb", tap.name());
            payload.put("actorId", actorNI.getObjectId());
            payload.put("targetId", cardNI.getObjectId());
            payload.put("tapped", tappedNow);
            // If Forge ever exposes “asCost/effect/sourceSA”, attach here too.
            recordEvent("Tap", payload);

            verbHistory.add(vi);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "visit(GameEventCardTapped) failed: " + ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public Void visit(GameEventCardCounters event) {
        // TODO: Update card counters based on event data
        // String cardId = event.getCard().getId();
        // NounInstance card = getInstance(cardId);
        // if (card != null) {
        //     String counterType = event.getCounterType();
        //     int amount = event.getAmount();
        //
        //     if (counterType.equals("+1/+1")) {
        //         int current = (int) card.getProperty("plus1plus1Counters");
        //         card.setProperty("plus1plus1Counters", current + amount);
        //         updatePowerToughness(card);
        //     } else if (counterType.equals("-1/-1")) {
        //         int current = (int) card.getProperty("minus1minus1Counters");
        //         card.setProperty("minus1minus1Counters", current + amount);
        //         updatePowerToughness(card);
        //     }
        //     // Handle other counter types...
        // }
        return null;
    }

    @Override
    public Void visit(GameEventCardStatsChanged event) {
        // TODO: Update card power/toughness
        // String cardId = event.getCard().getId();
        // NounInstance card = getInstance(cardId);
        // if (card != null) {
        //     card.setProperty("currentPower", event.getNewPower());
        //     card.setProperty("currentToughness", event.getNewToughness());
        // }
        return null;
    }

    @Override
    public Void visit(GameEventCardDamaged event) {
        // TODO: Track damage on creatures
        // String cardId = event.getCard().getId();
        // NounInstance card = getInstance(cardId);
        // if (card != null) {
        //     int damage = event.getDamage();
        //     int currentDamage = (int) card.getProperty("damageMarked");
        //     card.setProperty("damageMarked", currentDamage + damage);
        //     card.setProperty("damagedThisTurn", true);
        // }
        return null;
    }

    @Override
    public Void visit(GameEventPlayerLivesChanged event) {
        // TODO: Update player life total
        // String playerId = event.getPlayer().getId();
        // NounInstance player = getInstance(playerId);
        // if (player != null) {
        //     player.setProperty("life", event.getNewLife());
        //     LOGGER.info("Player " + playerId + " life changed to " + event.getNewLife());
        // }
        return null;
    }

    @Override
    public Void visit(GameEventPlayerCounters event) {
        // TODO: Update player counters (poison, energy, etc.)
        // String playerId = event.getPlayer().getId();
        // NounInstance player = getInstance(playerId);
        // if (player != null) {
        //     String counterType = event.getCounterType();
        //     int amount = event.getAmount();
        //     player.setProperty(counterType + "Counters", amount);
        // }
        return null;
    }

    @Override
    public Void visit(GameEventAttackersDeclared event) {
        // TODO: Mark attackers
        // for (Card attacker : event.getAttackers()) {
        //     String cardId = attacker.getId();
        //     NounInstance card = getInstance(cardId);
        //     if (card != null) {
        //         card.setProperty("attacking", true);
        //         card.setProperty("tapped", true);
        //     }
        // }
        return null;
    }

    @Override
    public Void visit(GameEventBlockersDeclared event) {
        // TODO: Mark blockers and their targets
        // for (Map.Entry<Card, Card> entry : event.getBlockers().entrySet()) {
        //     String blockerId = entry.getKey().getId();
        //     String attackerId = entry.getValue().getId();
        //     NounInstance blocker = getInstance(blockerId);
        //     if (blocker != null) {
        //         blocker.setProperty("blocking", true);
        //         blocker.setProperty("blockingTarget", attackerId);
        //     }
        // }
        return null;
    }

    @Override
    public Void visit(GameEventTokenCreated event) {
        // TODO: Create a new token instance
        // Token token = event.getToken();
        // String tokenId = "token_" + System.currentTimeMillis();
        // NounInstance tokenInstance = createInstance("Token", tokenId);
        // tokenInstance.setProperty("name", token.getName());
        // tokenInstance.setProperty("controller", token.getController().getId());
        // tokenInstance.setProperty("zone", "Battlefield");
        // // Set other token properties...
        return null;
    }

    @Override
    public Void visit(GameEventGameStarted event) {
        LOGGER.info("Game started - initializing knowledge base");
        // Could initialize game state, players, etc.
        return null;
    }

    @Override
    public Void visit(GameEventTurnBegan event) {
        // TODO: Update turn information
        // String activePlayer = event.getPlayer().getId();
        // NounInstance gameState = getInstance("game_state");
        // if (gameState != null) {
        //     int turn = (int) gameState.getProperty("turnNumber");
        //     gameState.setProperty("turnNumber", turn + 1);
        //     gameState.setProperty("activePlayer", activePlayer);
        // }
        return null;
    }

    @Override
    public Void visit(GameEventTurnPhase event) {
        // TODO: Update phase/step information
        // NounInstance gameState = getInstance("game_state");
        // if (gameState != null) {
        //     gameState.setProperty("phase", event.getPhase());
        //     gameState.setProperty("step", event.getStep());
        // }
        return null;
    }

    // Default no-op implementations for events we don't process yet
    @Override
    public Void visit(GameEventAnteCardsSelected event) { return null; }
    @Override
    public Void visit(GameEventCardDestroyed event) { return null; }
    @Override
    public Void visit(GameEventCardAttachment event) { return null; }
    @Override
    public Void visit(GameEventCardModeChosen event) { return null; }
    @Override
    public Void visit(GameEventCardRegenerated event) { return null; }
    @Override
    public Void visit(GameEventCardSacrificed event) { return null; }
    @Override
    public Void visit(GameEventCardPhased event) { return null; }
    @Override
    public Void visit(GameEventCombatChanged event) { return null; }
    @Override
    public Void visit(GameEventCombatEnded event) { return null; }
    @Override
    public Void visit(GameEventCombatUpdate event) { return null; }
    @Override
    public Void visit(GameEventGameFinished event) { return null; }
    @Override
    public Void visit(GameEventGameOutcome event) { return null; }
    @Override
    public Void visit(GameEventFlipCoin event) { return null; }
    @Override
    public Void visit(GameEventGameRestarted event) { return null; }
    @Override
    public Void visit(GameEventLandPlayed event) { return null; }
    @Override
    public Void visit(GameEventManaPool event) { return null; }
    @Override
    public Void visit(GameEventManaBurn event) { return null; }
    @Override
    public Void visit(GameEventMulligan event) { return null; }
    @Override
    public Void visit(GameEventPlayerControl event) { return null; }
    @Override
    public Void visit(GameEventPlayerDamaged event) { return null; }
    @Override
    public Void visit(GameEventPlayerPoisoned event) { return null; }
    @Override
    public Void visit(GameEventPlayerRadiation event) { return null; }
    @Override
    public Void visit(GameEventPlayerPriority event) { return null; }
    @Override
    public Void visit(GameEventPlayerShardsChanged event) { return null; }
    @Override
    public Void visit(GameEventPlayerStatsChanged event) { return null; }
    @Override
    public Void visit(GameEventRandomLog event) { return null; }
    @Override
    public Void visit(GameEventRollDie event) { return null; }
    @Override
    public Void visit(GameEventScry event) { return null; }
    @Override
    public Void visit(GameEventShuffle event) { return null; }
    @Override
    public Void visit(GameEventSpeedChanged event) { return null; }
    @Override
    public Void visit(GameEventSpellAbilityCast event) { return null; }
    @Override
    public Void visit(GameEventSpellResolved event) { return null; }
    @Override
    public Void visit(GameEventSpellRemovedFromStack event) { return null; }
    @Override
    public Void visit(GameEventSprocketUpdate event) { return null; }
    @Override
    public Void visit(GameEventSubgameStart event) { return null; }
    @Override
    public Void visit(GameEventSubgameEnd event) { return null; }
    @Override
    public Void visit(GameEventSurveil event) { return null; }
    @Override
    public Void visit(GameEventTurnEnded event) { return null; }
    @Override
    public Void visit(GameEventZone event) { return null; }
    @Override
    public Void visit(GameEventCardForetold event) { return null; }
    @Override
    public Void visit(GameEventCardPlotted event) { return null; }
    @Override
    public Void visit(GameEventDayTimeChanged event) { return null; }
    @Override
    public Void visit(GameEventDoorChanged event) { return null; }

    public void recordEvent(String type, Map<String,Object> payload) { /* your impl */ }
}