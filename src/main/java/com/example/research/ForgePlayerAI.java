package com.example.research;


import forge.ai.LobbyPlayerAi;
import forge.game.Game;
import forge.game.player.Player;

/**
 * Simple wrapper around Forge's LobbyPlayerAi that uses the default AI system
 * without any custom modifications. This ensures we're using Forge's AI
 * exactly as it comes out of the box.
 */
public class ForgePlayerAI extends LobbyPlayerAi {
    public ForgePlayerAI(String name) {
        super(name, null);  // null for default AI options
        // Or pass AIOption.USE_SIMULATION for simulation-based AI
    }

    // For now, just use the default implementation
    @Override
    public Player createIngamePlayer(Game game, int id) {
        return super.createIngamePlayer(game, id);
    }
}