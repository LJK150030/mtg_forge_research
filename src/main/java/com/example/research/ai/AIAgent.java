package com.example.research.ai;

import forge.LobbyPlayer;
import forge.game.Game;
import forge.game.player.Player;
import forge.item.PaperCard;
import forge.deck.Deck;

import java.util.List;

/**
 * AIAgent - Base interface for all AI implementations
 *
 * Defines the contract for AI agents in the MTG Forge system.
 * Provides abstraction over Forge's AI mechanisms.
 */
public interface AIAgent {

    /**
     * Get the agent's name
     */
    String getName();

    /**
     * Get the agent's type/strategy
     */
    AIStrategy getStrategy();

    /**
     * Get the underlying LobbyPlayer for Forge integration
     */
    LobbyPlayer getLobbyPlayer();

    /**
     * Initialize the AI agent
     */
    void initialize();

    /**
     * Shutdown and cleanup resources
     */
    void shutdown();

    /**
     * Configure the AI for a specific game
     */
    void configureForGame(Game game);

    /**
     * Get the AI's preferred deck archetype
     */
    DeckArchetype getPreferredArchetype();

    /**
     * Evaluate a card's value for the AI's strategy
     */
    int evaluateCard(PaperCard card);

    /**
     * Choose cards during draft or selection
     */
    PaperCard selectCard(List<PaperCard> options);

    /**
     * Build or modify a deck based on available cards
     */
    Deck buildDeck(List<PaperCard> cardPool);

    /**
     * Get AI configuration parameters
     */
    AIConfig getConfig();

    /**
     * Update AI configuration
     */
    void setConfig(AIConfig config);

    /**
     * AI Strategy enumeration
     */
    enum AIStrategy {
        AGGRESSIVE("Aggressive", "Focuses on early damage and tempo"),
        CONTROL("Control", "Focuses on card advantage and late game"),
        MIDRANGE("Midrange", "Balanced approach between aggro and control"),
        COMBO("Combo", "Focuses on specific card interactions"),
        RAMP("Ramp", "Focuses on mana acceleration"),
        RANDOM("Random", "Makes random decisions");

        private final String name;
        private final String description;

        AIStrategy(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    /**
     * Deck archetype preferences
     */
    enum DeckArchetype {
        AGGRO_WHITE("White Weenie", new String[]{"W"}),
        AGGRO_RED("Red Deck Wins", new String[]{"R"}),
        CONTROL_BLUE("Blue Control", new String[]{"U"}),
        CONTROL_BLACK("Black Control", new String[]{"B"}),
        MIDRANGE_GREEN("Green Stompy", new String[]{"G"}),
        CATS_TRIBAL("Cats Tribal", new String[]{"W"}),
        VAMPIRES_TRIBAL("Vampires Tribal", new String[]{"B"}),
        MULTICOLOR("Multicolor", new String[]{"W", "U", "B", "R", "G"});

        private final String name;
        private final String[] colors;

        DeckArchetype(String name, String[] colors) {
            this.name = name;
            this.colors = colors;
        }

        public String getName() { return name; }
        public String[] getColors() { return colors; }
    }

    /**
     * AI Configuration parameters
     */
    class AIConfig {
        // Decision-making parameters
        public boolean useSimulation = false;
        public boolean allowCheatShuffle = false;
        public int maxThinkTime = 5000; // milliseconds
        public int simulationDepth = 3;

        // Behavioral parameters
        public float aggressionLevel = 0.5f; // 0.0 to 1.0
        public float riskTolerance = 0.5f;   // 0.0 to 1.0
        public boolean preferCreatures = true;
        public boolean preferRemoval = true;

        // Draft parameters
        public int targetCreatureCount = 16;
        public int targetLandCount = 17;
        public int maxColors = 2;

        // Performance parameters
        public boolean enableLogging = false;
        public boolean verboseMode = false;

        /**
         * Create default configuration
         */
        public static AIConfig createDefault() {
            return new AIConfig();
        }

        /**
         * Create aggressive configuration
         */
        public static AIConfig createAggressive() {
            AIConfig config = new AIConfig();
            config.aggressionLevel = 0.9f;
            config.riskTolerance = 0.8f;
            config.preferCreatures = true;
            config.targetCreatureCount = 20;
            return config;
        }

        /**
         * Create control configuration
         */
        public static AIConfig createControl() {
            AIConfig config = new AIConfig();
            config.aggressionLevel = 0.2f;
            config.riskTolerance = 0.3f;
            config.preferRemoval = true;
            config.targetCreatureCount = 12;
            return config;
        }
    }
}