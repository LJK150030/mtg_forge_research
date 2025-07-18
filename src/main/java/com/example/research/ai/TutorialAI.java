package com.example.research.ai;

import com.example.research.mtg_commons;
import forge.*;
import forge.ai.LobbyPlayerAi;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.player.Player;
import forge.item.PaperCard;
import forge.deck.Deck;
import forge.deck.DeckSection;

import java.util.*;

/**
 * TutorialAI - AI implementation for tutorial/basic gameplay
 *
 * Extends LobbyPlayerAi to integrate with Forge's AI system.
 * Implements AIAgent interface for our architecture.
 */
public class TutorialAI extends LobbyPlayerAi implements AIAgent {

    private final AIStrategy strategy;
    private final DeckArchetype preferredArchetype;
    private AIConfig config;
    private boolean initialized = false;

    // Draft state
    private DraftState draftState;

    /**
     * Constructor for compatibility with existing code
     */
    public TutorialAI(String name, boolean aggressive) {
        this(name, aggressive ? AIStrategy.AGGRESSIVE : AIStrategy.CONTROL);
    }

    /**
     * Main constructor
     */
    public TutorialAI(String name, AIStrategy strategy) {
        super(name, null);
        this.strategy = strategy;
        this.config = createConfigForStrategy(strategy);
        this.preferredArchetype = determineArchetype(strategy);
        this.draftState = new DraftState();

        // Configure LobbyPlayerAi properties
        setRotateProfileEachGame(false);
        setAllowCheatShuffle(config.allowCheatShuffle);

        // Set AI profile based on strategy
        switch (strategy) {
            case AGGRESSIVE:
                setAiProfile("aggressive");  // Use Forge's aggressive AI profile
                break;
            case CONTROL:
                setAiProfile("control");     // Use Forge's control AI profile
                break;
            default:
                setAiProfile("");            // Use default profile
        }
    }

    @Override
    public void initialize() {
        if (initialized) return;

        System.out.println("🤖 Initializing TutorialAI: " + getName());

        // Additional initialization if needed
        initialized = true;
    }

    @Override
    public void shutdown() {
        System.out.println("🛑 Shutting down TutorialAI: " + getName());
        initialized = false;
    }

    @Override
    public void configureForGame(Game game) {
        // Game-specific configuration
        if (config.enableLogging) {
            System.out.println("⚙️ Configuring " + getName() + " for game");
        }
    }

    @Override
    public Player createIngamePlayer(Game game, int id) {
        Player player = super.createIngamePlayer(game, id);

        // Configure PlayerControllerAi based on strategy
        if (player.getController() instanceof PlayerControllerAi aiController) {
            configureAIController(aiController);
        }

        return player;
    }

    /**
     * Configure the AI controller based on strategy
     */
    private void configureAIController(PlayerControllerAi controller) {
        controller.allowCheatShuffle(config.allowCheatShuffle);
        controller.setUseSimulation(config.useSimulation);

        // Strategy-specific configurations are handled through:
        // 1. AI profile selection (setAiProfile)
        // 2. Property-based configuration if available
        // 3. Decision-making logic within the AI's canPlaySa() method

        // Note: Forge doesn't have setAggression() method
        // Aggression is controlled through AI profiles and decision logic
    }

    @Override
    public int evaluateCard(PaperCard card) {
        int baseScore = 0;

        // Type-based evaluation
        if (card.getRules().getType().isCreature()) {
            baseScore += evaluateCreature(card);
        } else if (card.getRules().getType().isInstant() || card.getRules().getType().isSorcery()) {
            baseScore += evaluateSpell(card);
        } else if (card.getRules().getType().isEnchantment()) {
            baseScore += 20;
        } else if (card.getRules().getType().isArtifact()) {
            baseScore += 15;
        } else if (card.getRules().getType().isLand()) {
            baseScore += evaluateLand(card);
        }

        // Strategy adjustments
        baseScore = applyStrategyModifiers(card, baseScore);

        // Color matching bonus
        baseScore += evaluateColorMatch(card);

        return baseScore;
    }

    /**
     * Evaluate creature cards
     */
    private int evaluateCreature(PaperCard card) {
        int score = 50; // Base creature value

        // Power/Toughness evaluation
        try {
            int power = Integer.parseInt(card.getRules().getPower());
            int toughness = Integer.parseInt(card.getRules().getToughness());
            int cmc = card.getRules().getManaCost().getCMC();

            // Efficiency calculation
            float efficiency = (power + toughness) / (float) Math.max(1, cmc);
            score += (int)(efficiency * 10);

            // Size bonuses
            if (power >= 4) score += 10;
            if (toughness >= 4) score += 8;

        } catch (NumberFormatException e) {
            // Variable P/T, use default score
        }

        // Keyword abilities
        String oracle = card.getRules().getOracleText().toLowerCase();
        score += evaluateKeywords(oracle);

        return score;
    }

    /**
     * Evaluate spell cards
     */
    private int evaluateSpell(PaperCard card) {
        int score = 30; // Base spell value
        String oracle = card.getRules().getOracleText().toLowerCase();

        // Removal spells
        if (oracle.contains("destroy") || oracle.contains("exile")) {
            score += 25;
        }
        if (oracle.contains("damage")) {
            score += 20;
        }

        // Card advantage
        if (oracle.contains("draw")) {
            score += 15;
        }

        // Combat tricks
        if (card.getRules().getType().isInstant()) {
            score += 10;
        }

        return score;
    }

    /**
     * Evaluate land cards
     */
    private int evaluateLand(PaperCard card) {
        if (card.getRules().getType().isBasicLand()) {
            return 100; // Always want basics
        }
        return 30; // Non-basics are situational
    }

    /**
     * Evaluate keyword abilities
     */
    private int evaluateKeywords(String oracle) {
        int score = 0;

        if (oracle.contains("flying")) score += 15;
        if (oracle.contains("first strike")) score += 10;
        if (oracle.contains("double strike")) score += 20;
        if (oracle.contains("lifelink")) score += 8;
        if (oracle.contains("vigilance")) score += 7;
        if (oracle.contains("deathtouch")) score += 12;
        if (oracle.contains("menace")) score += 5;
        if (oracle.contains("trample")) score += 8;
        if (oracle.contains("haste")) score += 10;
        if (oracle.contains("hexproof")) score += 10;
        if (oracle.contains("indestructible")) score += 15;

        return score;
    }

    /**
     * Apply strategy-specific modifiers
     */
    private int applyStrategyModifiers(PaperCard card, int baseScore) {
        switch (strategy) {
            case AGGRESSIVE:
                if (card.getRules().getManaCost().getCMC() <= 3) {
                    baseScore *= 1.3;
                }
                if (card.getRules().getType().isCreature()) {
                    baseScore *= 1.2;
                }
                break;

            case CONTROL:
                if (card.getRules().getOracleText().toLowerCase().contains("draw") ||
                        card.getRules().getOracleText().toLowerCase().contains("destroy")) {
                    baseScore *= 1.4;
                }
                if (card.getRules().getManaCost().getCMC() >= 4) {
                    baseScore *= 1.1;
                }
                break;

            case MIDRANGE:
                if (card.getRules().getManaCost().getCMC() >= 3 &&
                        card.getRules().getManaCost().getCMC() <= 5) {
                    baseScore *= 1.2;
                }
                break;
        }

        return baseScore;
    }

    /**
     * Evaluate color matching
     */
    private int evaluateColorMatch(PaperCard card) {
        int score = 0;

        if (draftState.primaryColor != null &&
                card.getRules().getColor().hasAnyColor(Integer.parseInt(draftState.primaryColor))) {
            score += 25;
        }

        if (draftState.secondaryColor != null &&
                card.getRules().getColor().hasAnyColor(Integer.parseInt(draftState.secondaryColor))) {
            score += 15;
        }

        return score;
    }

    @Override
    public PaperCard selectCard(List<PaperCard> options) {
        if (options.isEmpty()) return null;

        PaperCard bestCard = null;
        int bestScore = Integer.MIN_VALUE;

        // Evaluate each option
        for (PaperCard card : options) {
            int score = evaluateCard(card);

            // Apply draft-specific considerations
            if (draftState.needsMoreCreatures() && card.getRules().getType().isCreature()) {
                score += 30;
            }
            if (draftState.needsMoreLands() && card.getRules().getType().isLand()) {
                score += 50;
            }

            if (score > bestScore) {
                bestScore = score;
                bestCard = card;
            }
        }

        // Update draft state
        if (bestCard != null) {
            draftState.cardPicked(bestCard);
        }

        return bestCard;
    }

    @Override
    public Deck buildDeck(List<PaperCard> cardPool) {
        Deck deck = new Deck("AI Generated Deck");

        // Sort cards by evaluation score
        List<PaperCard> sortedCards = new ArrayList<>(cardPool);
        sortedCards.sort((a, b) -> Integer.compare(evaluateCard(b), evaluateCard(a)));

        // Add lands first
        int landCount = 0;
        for (PaperCard card : sortedCards) {
            if (card.getRules().getType().isLand() && landCount < config.targetLandCount) {
                deck.getMain().add(card);
                landCount++;
            }
        }

        // Add non-lands
        int nonLandCount = 0;
        int targetNonLands = 40 - config.targetLandCount; // Assuming 40 card limited deck

        for (PaperCard card : sortedCards) {
            if (!card.getRules().getType().isLand() && nonLandCount < targetNonLands) {
                deck.getMain().add(card);
                nonLandCount++;
            }
        }

        return deck;
    }

    @Override
    public void hear(LobbyPlayer player, String message) {
        if (config.verboseMode) {
            System.out.println("[" + getName() + "] Heard: " + message);
        }
    }

    // Getters

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    public AIStrategy getStrategy() {
        return strategy;
    }

    @Override
    public LobbyPlayer getLobbyPlayer() {
        return this;
    }

    @Override
    public DeckArchetype getPreferredArchetype() {
        return preferredArchetype;
    }

    @Override
    public AIConfig getConfig() {
        return config;
    }

    @Override
    public void setConfig(AIConfig config) {
        this.config = config;
        setAllowCheatShuffle(config.allowCheatShuffle);
    }

    /**
     * Create configuration based on strategy
     */
    private AIConfig createConfigForStrategy(AIStrategy strategy) {
        switch (strategy) {
            case AGGRESSIVE:
                return AIConfig.createAggressive();
            case CONTROL:
                return AIConfig.createControl();
            default:
                return AIConfig.createDefault();
        }
    }

    /**
     * Determine preferred archetype based on strategy
     */
    private DeckArchetype determineArchetype(AIStrategy strategy) {
        switch (strategy) {
            case AGGRESSIVE:
                return DeckArchetype.AGGRO_RED;
            case CONTROL:
                return DeckArchetype.CONTROL_BLUE;
            default:
                return DeckArchetype.MIDRANGE_GREEN;
        }
    }

    /**
     * Draft state tracking
     */
    private class DraftState {
        String primaryColor = null;
        String secondaryColor = null;
        int creatureCount = 0;
        int nonCreatureCount = 0;
        int landCount = 0;

        void cardPicked(PaperCard card) {
            // Update counts
            if (card.getRules().getType().isCreature()) {
                creatureCount++;
            } else if (card.getRules().getType().isLand()) {
                landCount++;
            } else {
                nonCreatureCount++;
            }

            // Update colors
            if (!card.getRules().getColor().isColorless() && primaryColor == null) {
                // Set primary color from first non-colorless pick
                for (String color : new String[]{"W", "U", "B", "R", "G"}) {
                    if (card.getRules().getColor().hasAnyColor(Integer.parseInt(color))) {
                        primaryColor = color;
                        break;
                    }
                }
            }
        }

        boolean needsMoreCreatures() {
            return creatureCount < config.targetCreatureCount;
        }

        boolean needsMoreLands() {
            return landCount < config.targetLandCount;
        }

        void reset() {
            primaryColor = null;
            secondaryColor = null;
            creatureCount = 0;
            nonCreatureCount = 0;
            landCount = 0;
        }
    }
}