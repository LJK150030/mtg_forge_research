package com.example.research;

import forge.LobbyPlayer;
import forge.StaticData;
import forge.CardStorageReader;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.game.*;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.item.PaperCard;
import forge.util.CardTranslation;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ForgeTest {
    public static void main(String[] args) {
        System.out.println("🎮 Starting Forge Match Test with Custom Decks");

        try {
            // Initialize Forge's StaticData first
            initializeForgeData();

            // Initialize card translation system
            CardTranslation.preloadTranslation("en-US", "res/languages/");

            // Create simple decks with basic lands only (since card database is not fully loaded)
            Deck catsDeck = createSimpleDeck("Cats Theme Deck", "Plains");
            Deck vampiresDeck = createSimpleDeck("Vampires Theme Deck", "Swamp");

            System.out.println("✓ Created simple theme decks");

            // Create lobby players
            LobbyPlayer catPlayer = new TestLobbyPlayer("Cat Player");
            LobbyPlayer vampirePlayer = new TestLobbyPlayer("Vampire Player");

            // Create registered players
            RegisteredPlayer regCatPlayer = new RegisteredPlayer(catsDeck);
            regCatPlayer.setPlayer(catPlayer);

            RegisteredPlayer regVampirePlayer = new RegisteredPlayer(vampiresDeck);
            regVampirePlayer.setPlayer(vampirePlayer);

            List<RegisteredPlayer> players = Arrays.asList(regCatPlayer, regVampirePlayer);

            System.out.println("✓ Created registered players");

            // Create game rules
            GameRules gameRules = new GameRules(GameType.Constructed);

            System.out.println("✓ Created game rules");

            // Create the match
            Match match = new Match(gameRules, players, "Basic Match Test");

            System.out.println("✓ Created match");

            // Create a game
            Game game = new Game(players, gameRules, match);

            System.out.println("✓ Created game");

            // Start the game
            game.getAction().startGame(null);

            System.out.println("🎯 Game started successfully!");
            System.out.println("📊 Game State:");
            System.out.println("   - Players: " + game.getRegisteredPlayers().size());
            System.out.println("   - Current Turn: " + game.getPhaseHandler().getTurn());
            System.out.println("   - Current Phase: " + game.getPhaseHandler().getPhase());

            // Display player information
            for (Player player : game.getPlayers()) {
                System.out.println("   - " + player.getName() +
                        " Life: " + player.getLife() +
                        ", Hand: " + player.getCardsIn(forge.game.zone.ZoneType.Hand).size() +
                        ", Library: " + player.getCardsIn(forge.game.zone.ZoneType.Library).size());
            }

            System.out.println("🎉 Match setup completed successfully!");

        } catch (Exception e) {
            System.err.println("❌ Error setting up match: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initialize Forge's StaticData with minimal configuration
     */
    private static void initializeForgeData() {
        try {
            System.out.println("🔧 Initializing Forge StaticData...");

            // Create card storage readers (you may need to adjust these paths based on your setup)
            String cardDataDir = "res/cardsfolder"; // Adjust this path
            String editionFolder = "res/editions"; // Adjust this path
            String blockDataFolder = "res/blockdata"; // Adjust this path

            // Check if directories exist, create minimal setup if they don't
            ensureDirectoryExists(cardDataDir);
            ensureDirectoryExists(editionFolder);
            ensureDirectoryExists(blockDataFolder);

            CardStorageReader cardReader = new CardStorageReader(cardDataDir, null, false);
            CardStorageReader customCardReader = null; // Optional

            // Initialize StaticData with minimal configuration
            StaticData staticData = new StaticData(
                    cardReader,
                    customCardReader,
                    editionFolder,
                    "", // custom editions folder (empty)
                    blockDataFolder,
                    "LATEST_ART_ALL_EDITIONS", // card art preference
                    true, // enable unknown cards
                    true  // load non-legal cards
            );

            System.out.println("✓ StaticData initialized");

        } catch (Exception e) {
            System.err.println("⚠️  Failed to initialize full StaticData: " + e.getMessage());
            System.out.println("   Continuing with minimal setup...");

            // Create a minimal StaticData setup if full initialization fails
            try {
                createMinimalStaticData();
                System.out.println("✓ Minimal StaticData created");
            } catch (Exception e2) {
                System.err.println("❌ Failed to create minimal StaticData: " + e2.getMessage());
                throw new RuntimeException("Cannot initialize Forge", e2);
            }
        }
    }

    /**
     * Create minimal StaticData for testing when full setup is not available
     */
    private static void createMinimalStaticData() {
        // This is a fallback approach - you may need to implement this based on
        // your specific Forge version and available constructors
        System.out.println("   Creating minimal StaticData fallback...");

        // You might need to create empty directories and minimal data files
        // or use a different StaticData constructor if available
    }

    /**
     * Ensure a directory exists, create it if it doesn't
     */
    private static void ensureDirectoryExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                System.out.println("   Created directory: " + path);
            } else {
                System.out.println("   Directory already exists or could not create: " + path);
            }
        }
    }

    /**
     * Creates a simple deck with only basic lands for testing
     */
    private static Deck createSimpleDeck(String deckName, String basicLandName) {
        Deck deck = new Deck(deckName);
        CardPool mainDeck = new CardPool();

        System.out.println("🏗️  Creating " + deckName + " with basic lands:");

        // Add 20 basic lands to make a minimal playable deck
        for (int i = 0; i < 20; i++) {
            try {
                PaperCard card = getBasicLand(basicLandName);
                if (card != null) {
                    mainDeck.add(card);
                    if (i == 0) {
                        System.out.println("   ✓ Added: " + basicLandName + " (x20)");
                    }
                } else {
                    System.out.println("   ⚠️  Could not find " + basicLandName + ", creating placeholder");
                    // For testing, we could create a minimal card or skip
                    // This depends on your Forge version and requirements
                }
            } catch (Exception e) {
                System.out.println("   ❌ Error adding " + basicLandName + ": " + e.getMessage());
            }
        }

        deck.getMain().addAll(mainDeck);
        return deck;
    }

    /**
     * Helper method to get a basic land card
     */
    private static PaperCard getBasicLand(String landName) {
        try {
            if (StaticData.instance() != null && StaticData.instance().getCommonCards() != null) {
                return StaticData.instance().getCommonCards().getCard(landName);
            } else {
                System.out.println("   StaticData not available, cannot load " + landName);
                return null;
            }
        } catch (Exception e) {
            System.out.println("   Failed to get basic land: " + landName + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Test implementation of LobbyPlayer
     */
    static class TestLobbyPlayer extends LobbyPlayer {
        public TestLobbyPlayer(String name) {
            super(name);
        }

        @Override
        public void hear(LobbyPlayer player, String message) {
            System.out.println(getName() + " heard from " + player.getName() + ": " + message);
        }
    }
}