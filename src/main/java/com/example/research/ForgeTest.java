package com.example.research;

import forge.deck.*;
import forge.game.*;
import forge.game.player.*;
import forge.game.zone.ZoneType;

/**
 * Forge Test with Integrated AI Implementation
 *
 * This implementation uses the proper LobbyPlayerAi pattern from Forge
 * with self-contained draft AI logic integrated directly.
 */
public class ForgeTest {

    public static void main(String[] args) {
        System.out.println("🎮 Starting Forge Match Test with Enhanced Simulation");

        try {
            // Step 1: Initialize Forge environment
            System.out.println("🔧 Initializing Forge environment...");
            ForgeApp app = new ForgeApp();

            // Step 2: Create tutorial decks
            System.out.println("🏗️  Creating tutorial decks...");
            ForgeDeck deck = new ForgeDeck();
            Deck catsDeck = deck.createCatsDeck();
            Deck vampiresDeck = deck.createVampiresDeck();

            // Step 3: Parse command line arguments for simulation mode
            ForgeMatch match = new ForgeMatch(args, catsDeck, vampiresDeck);

        } catch (Exception e) {
            System.err.println("❌ Error setting up match: " + e.getMessage());
            e.printStackTrace();
        }
    }
}