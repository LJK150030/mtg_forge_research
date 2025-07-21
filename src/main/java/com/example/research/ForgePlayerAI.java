package com.example.research;

import forge.LobbyPlayer;
import forge.ai.LobbyPlayerAi;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.player.Player;

public class ForgePlayerAI extends LobbyPlayerAi {
    private final boolean isAggressive;

    public ForgePlayerAI(String name, boolean aggressive) {
            super(name, null);
            this.isAggressive = aggressive;

            // Configure AI properties
            setRotateProfileEachGame(false);
            setAllowCheatShuffle(false);
            setAiProfile("");  // Use default profile
        }

    @Override
    public void hear(LobbyPlayer player, String message) {
        System.out.println("[" + getName() + "] Heard: " + message);
    }


    /**
     * Override to customize the AI controller creation
     */
    @Override
    public Player createIngamePlayer(Game game, int id) {
        Player player = super.createIngamePlayer(game, id);

        // The framework has already created PlayerControllerAi
        // We can access and configure it here if needed
        if (player.getController() instanceof PlayerControllerAi aiController) {

            // Configure AI behavior based on deck type
            if (isAggressive) {
                // Aggressive AI configuration
                aiController.allowCheatShuffle(true);
                aiController.setUseSimulation(true);
            } else {
                // Control AI configuration
                aiController.allowCheatShuffle(false);
                aiController.setUseSimulation(false);
            }
        }

        return player;
    }
}
