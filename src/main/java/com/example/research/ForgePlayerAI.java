package com.example.research;

import forge.ai.LobbyPlayerAi;
import forge.ai.AiProfileUtil;
import forge.ai.AIOption;
import java.util.Set;
import java.util.HashSet;

/**
 * Simple wrapper around Forge's LobbyPlayerAi that uses the default AI system
 * without any custom modifications. This ensures we're using Forge's AI
 * exactly as it comes out of the box.
 */
public class ForgePlayerAI extends LobbyPlayerAi {

    /**
     * Create a default Forge AI player
     */
    public ForgePlayerAI(String name) {
        super(name, null);
    }

    /**
     * Create a Forge AI player with simulation enabled
     */
    public ForgePlayerAI(String name, boolean useSimulation) {
        super(name, useSimulation ? Set.of(AIOption.USE_SIMULATION) : null);
    }

    /**
     * Create a Forge AI player with specific AI profile
     *
     * @param name Player name
     * @param profileName One of Forge's built-in AI profiles (or empty string for default)
     */
    public ForgePlayerAI(String name, String profileName) {
        super(name, null);
        if (profileName != null && !profileName.isEmpty()) {
            setAiProfile(profileName);
        }
    }

    /**
     * Create a Forge AI player with profile and simulation option
     */
    public ForgePlayerAI(String name, String profileName, boolean useSimulation) {
        super(name, useSimulation ? Set.of(AIOption.USE_SIMULATION) : null);
        if (profileName != null && !profileName.isEmpty()) {
            setAiProfile(profileName);
        }
    }

    /**
     * Factory methods for convenience
     */
    public static class Factory {

        /**
         * Create a default AI player
         */
        public static ForgePlayerAI createDefault(String name) {
            return new ForgePlayerAI(name);
        }

        /**
         * Create an AI player with simulation enabled
         */
        public static ForgePlayerAI createWithSimulation(String name) {
            return new ForgePlayerAI(name, true);
        }

        /**
         * Create an AI player with a specific profile
         * Available profiles can be retrieved using getAvailableProfiles()
         */
        public static ForgePlayerAI createWithProfile(String name, String profileName) {
            return new ForgePlayerAI(name, profileName);
        }

        /**
         * Create an AI that randomly selects a profile each game
         */
        public static ForgePlayerAI createRandomProfilePerGame(String name) {
            ForgePlayerAI ai = new ForgePlayerAI(name);
            ai.setRotateProfileEachGame(true);
            return ai;
        }

        /**
         * Create an AI with cheat shuffle enabled (for testing only)
         */
        public static ForgePlayerAI createWithCheatShuffle(String name) {
            ForgePlayerAI ai = new ForgePlayerAI(name);
            ai.setAllowCheatShuffle(true);
            return ai;
        }
    }

    /**
     * Get list of available AI profiles from Forge
     */
    public static java.util.List<String> getAvailableProfiles() {
        return AiProfileUtil.getAvailableProfiles();
    }

    /**
     * Convenience method to get a random profile name
     */
    public static String getRandomProfile() {
        return AiProfileUtil.getRandomProfile();
    }

    /**
     * Get a description of this AI player
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Forge AI Player: ").append(getName()).append("\n");
        sb.append("  Profile: ").append(getAiProfile().isEmpty() ? "Default" : getAiProfile()).append("\n");
        sb.append("  Cheat Shuffle: ").append(isAllowCheatShuffle() ? "Yes" : "No").append("\n");
        return sb.toString();
    }

    /**
     * Print AI configuration
     */
    public void printConfiguration() {
        System.out.println(getDescription());
    }
}