package com.example.research;

import forge.LobbyPlayer;
import forge.ai.AiProfileUtil;
import forge.ai.LobbyPlayerAi;
import forge.ai.PlayerControllerAi;
import forge.ai.AiController;
import forge.game.Game;
import forge.game.player.Player;

/**
 * Enhanced ForgePlayerAI that properly utilizes Forge's default AI system
 * with configurable AI profiles and behavior settings
 */
public class ForgePlayerAI extends LobbyPlayerAi {

    // AI configuration options
    public enum AIPersonality {
        DEFAULT(""),           // Default Forge AI
        AGGRESSIVE("Aggressive"),
        CONTROL("Control"),
        MIDRANGE("Midrange"),
        COMBO("Combo"),
        RANDOM_MATCH("Random (Every Match)"),
        RANDOM_GAME("Random (Every Game)");

        private final String profileName;

        AIPersonality(String profileName) {
            this.profileName = profileName;
        }

        public String getProfileName() {
            return profileName;
        }
    }

    // AI behavior configuration
    public static class AIConfig {
        public AIPersonality personality = AIPersonality.DEFAULT;
        public boolean allowCheatShuffle = false;
        public boolean useSimulation = true;
        public boolean rotateProfile = false;
        public boolean verboseLogging = false;

        public static AIConfig aggressive() {
            AIConfig config = new AIConfig();
            config.personality = AIPersonality.AGGRESSIVE;
            config.useSimulation = true;
            config.allowCheatShuffle = false;
            return config;
        }

        public static AIConfig control() {
            AIConfig config = new AIConfig();
            config.personality = AIPersonality.CONTROL;
            config.useSimulation = true;
            config.allowCheatShuffle = false;
            return config;
        }

        public static AIConfig defaultConfig() {
            AIConfig config = new AIConfig();
            config.personality = AIPersonality.DEFAULT;
            config.useSimulation = true;
            config.allowCheatShuffle = false;
            return config;
        }

        public static AIConfig random() {
            AIConfig config = new AIConfig();
            config.personality = AIPersonality.RANDOM_GAME;
            config.useSimulation = true;
            config.allowCheatShuffle = false;
            return config;
        }
    }

    private final AIConfig aiConfig;

    /**
     * Constructor with AI configuration
     */
    public ForgePlayerAI(String name, AIConfig config) {
        super(name, null);
        this.aiConfig = config != null ? config : AIConfig.defaultConfig();

        configureAI();
    }

    /**
     * Simple constructor for backward compatibility
     */
    public ForgePlayerAI(String name, boolean aggressive) {
        this(name, aggressive ? AIConfig.aggressive() : AIConfig.control());
    }

    /**
     * Constructor with personality
     */
    public ForgePlayerAI(String name, AIPersonality personality) {
        super(name, null);
        this.aiConfig = new AIConfig();
        this.aiConfig.personality = personality;

        configureAI();
    }

    /**
     * Configure the AI with Forge's built-in settings
     */
    private void configureAI() {
        // Set AI profile (this is the key to using Forge's default AI personalities)
        setAiProfile(aiConfig.personality.getProfileName());

        // Configure basic AI properties
        setRotateProfileEachGame(aiConfig.rotateProfile);
        setAllowCheatShuffle(aiConfig.allowCheatShuffle);

        if (aiConfig.verboseLogging) {
            System.out.println("🤖 Configured AI: " + getName());
            System.out.println("   Profile: " + (aiConfig.personality.getProfileName().isEmpty() ? "Default" : aiConfig.personality.getProfileName()));
            System.out.println("   Cheat Shuffle: " + aiConfig.allowCheatShuffle);
            System.out.println("   Use Simulation: " + aiConfig.useSimulation);
            System.out.println("   Rotate Profile: " + aiConfig.rotateProfile);
        }
    }

    @Override
    public void hear(LobbyPlayer player, String message) {
        if (aiConfig.verboseLogging) {
            System.out.println("[" + getName() + "] Heard: " + message);
        }
    }

    /**
     * Create the in-game player with proper AI controller configuration
     */
    @Override
    public Player createIngamePlayer(Game game, int id) {
        Player player = super.createIngamePlayer(game, id);

        // Configure the PlayerControllerAi that Forge creates
        if (player.getController() instanceof PlayerControllerAi aiController) {
            configurePlayerController(aiController);
        }

        return player;
    }

    /**
     * Configure the PlayerControllerAi with our settings
     */
    private void configurePlayerController(PlayerControllerAi aiController) {
        // Allow/disallow cheat shuffling
        aiController.allowCheatShuffle(aiConfig.allowCheatShuffle);

        // Configure simulation usage
        aiController.setUseSimulation(aiConfig.useSimulation);

        // Access the underlying AiController for more detailed configuration
        AiController ai = aiController.getAi();
        if (ai != null) {
            configureAiController(ai);
        }

        if (aiConfig.verboseLogging) {
            System.out.println("✓ Configured PlayerControllerAi for " + getName());
        }
    }

    /**
     * Configure the underlying AiController with personality-specific settings
     */
    private void configureAiController(AiController ai) {
        // The AiController will automatically read from the AI profile we set
        // But we can override specific behaviors here if needed

        switch (aiConfig.personality) {
            case AGGRESSIVE:
                // Aggressive AI might be more willing to attack and take risks
                // These are handled by the AI profile, but we could override here
                break;

            case CONTROL:
                // Control AI might be more conservative and defensive
                // These are handled by the AI profile
                break;

            case COMBO:
                // Combo AI might prioritize setting up combinations
                break;

            default:
                // Use default Forge AI behavior
                break;
        }

        if (aiConfig.verboseLogging) {
            System.out.println("✓ Configured AiController for " + getName());
        }
    }

    /**
     * Get available AI profiles from Forge
     */
    public static java.util.List<String> getAvailableProfiles() {
        return AiProfileUtil.getAvailableProfiles();
    }

    /**
     * Create an AI player with a specific Forge AI profile
     */
    public static ForgePlayerAI withProfile(String name, String profileName) {
        ForgePlayerAI ai = new ForgePlayerAI(name, AIConfig.defaultConfig());
        ai.setAiProfile(profileName);
        return ai;
    }

    /**
     * Create AI players for tutorial scenarios
     */
    public static class TutorialAI {

        /**
         * Create an AI suitable for playing the Cats deck (aggressive)
         */
        public static ForgePlayerAI createCatsAI(String name) {
            AIConfig config = AIConfig.aggressive();
            config.verboseLogging = true;
            return new ForgePlayerAI(name, config);
        }

        /**
         * Create an AI suitable for playing the Vampires deck (midrange/control)
         */
        public static ForgePlayerAI createVampiresAI(String name) {
            AIConfig config = AIConfig.control();
            config.verboseLogging = true;
            return new ForgePlayerAI(name, config);
        }

        /**
         * Create a random AI for variety
         */
        public static ForgePlayerAI createRandomAI(String name) {
            AIConfig config = AIConfig.random();
            config.verboseLogging = true;
            return new ForgePlayerAI(name, config);
        }

        /**
         * Create matched AIs for balanced gameplay
         */
        public static java.util.List<ForgePlayerAI> createMatchedPair(String name1, String name2) {
            java.util.List<ForgePlayerAI> ais = new java.util.ArrayList<>();

            // Create complementary AI personalities
            ais.add(createCatsAI(name1));
            ais.add(createVampiresAI(name2));

            return ais;
        }
    }

    /**
     * Factory methods for common AI configurations
     */
    public static class Factory {

        public static ForgePlayerAI createDefault(String name) {
            return new ForgePlayerAI(name, AIConfig.defaultConfig());
        }

        public static ForgePlayerAI createAggressive(String name) {
            return new ForgePlayerAI(name, AIConfig.aggressive());
        }

        public static ForgePlayerAI createControl(String name) {
            return new ForgePlayerAI(name, AIConfig.control());
        }

        public static ForgePlayerAI createRandom(String name) {
            return new ForgePlayerAI(name, AIConfig.random());
        }

        public static ForgePlayerAI createWithProfile(String name, String profileName) {
            return withProfile(name, profileName);
        }

        public static ForgePlayerAI createVerbose(String name, AIPersonality personality) {
            AIConfig config = new AIConfig();
            config.personality = personality;
            config.verboseLogging = true;
            return new ForgePlayerAI(name, config);
        }
    }

    /**
     * Get current AI configuration
     */
    public AIConfig getConfig() {
        return aiConfig;
    }

    /**
     * Get a description of this AI's configuration
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("AI Player: ").append(getName()).append("\n");
        sb.append("  Profile: ").append(aiConfig.personality.getProfileName().isEmpty() ? "Default" : aiConfig.personality.getProfileName()).append("\n");
        sb.append("  Personality: ").append(aiConfig.personality).append("\n");
        sb.append("  Simulation: ").append(aiConfig.useSimulation ? "Enabled" : "Disabled").append("\n");
        sb.append("  Cheat Shuffle: ").append(aiConfig.allowCheatShuffle ? "Allowed" : "Disabled");
        return sb.toString();
    }

    /**
     * Print AI configuration details
     */
    public void printConfiguration() {
        System.out.println(getDescription());
    }
}