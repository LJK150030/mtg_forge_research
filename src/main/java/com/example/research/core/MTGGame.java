package com.example.research.core;

import com.example.research.mtg_commons;
import com.example.research.ai.AIAgent;
import com.example.research.ai.TutorialAI;
import com.example.research.deck.DeckManager;
import com.example.research.render.ConsoleRenderer;

import forge.*;
import forge.game.*;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.deck.Deck;

import java.util.*;

/**
 * MTGGame - Core game logic controller
 *
 * Responsibilities:
 * - Manage Forge Game instance
 * - Control match setup and progression
 * - Handle AI players and deck management
 * - Maintain game state
 */
public class MTGGame {

    // Core game components
    private final ForgeApp app;
    private Game forgeGame;
    private Match match;
    private GameState gameState;

    // Players and AI
    private List<AIAgent> aiAgents;
    private List<RegisteredPlayer> registeredPlayers;

    // Decks
    private DeckManager deckManager;
    private Map<String, Deck> loadedDecks;

    // Game configuration
    private GameConfig config;
    private boolean active = false;
    private boolean gameOver = false;

    // Statistics
    private int turnCount = 0;
    private long gameStartTime;
    private List<GameEvent> eventHistory;

    public MTGGame(ForgeApp app) {
        this.app = app;
        this.deckManager = new DeckManager();
        this.loadedDecks = new HashMap<>();
        this.eventHistory = new ArrayList<>();
        this.gameState = new GameState();
    }

    /**
     * Initialize game with configuration
     */
    public void initialize(GameConfig config) {
        System.out.println("🎯 Initializing MTG Game...");

        this.config = config;

        try {
            // Step 1: Load decks
            System.out.println("📚 Loading decks...");
            loadDecks();

            // Step 2: Create AI players
            System.out.println("🤖 Creating AI players...");
            createAIPlayers();

            // Step 3: Setup match
            System.out.println("🎮 Setting up match...");
            setupMatch();

            // Step 4: Start game
            System.out.println("🚀 Starting game...");
            startGame();

            active = true;
            gameStartTime = System.currentTimeMillis();

            System.out.println("✅ Game initialized successfully!");

        } catch (Exception e) {
            System.err.println("❌ Failed to initialize game: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Game initialization failed", e);
        }
    }

    /**
     * Deinitialize and clean up game resources
     */
    public void deinitialize() {
        System.out.println("🛑 Deinitializing game...");

        active = false;

        // Clean up AI agents
        if (aiAgents != null) {
            for (AIAgent ai : aiAgents) {
                ai.shutdown();
            }
            aiAgents.clear();
        }

        // Clear game data
        if (forgeGame != null) {
            forgeGame = null;
        }

        if (match != null) {
            match = null;
        }

        loadedDecks.clear();
        eventHistory.clear();

        System.out.println("✅ Game deinitialized");
    }

    /**
     * Update game logic
     */
    public void update(float deltaTime) {
        if (!active || gameOver) {
            return;
        }

        try {
            // Check game over conditions
            if (forgeGame != null && forgeGame.isGameOver()) {
                handleGameOver();
                return;
            }

            // Update game state
            gameState.update(forgeGame, deltaTime);

            // Process AI decisions if needed
            processAITurn();

            // Update turn counter
            if (forgeGame != null && forgeGame.getPhaseHandler() != null) {
                int currentTurn = forgeGame.getPhaseHandler().getTurn();
                if (currentTurn > turnCount) {
                    turnCount = currentTurn;
                    logEvent("Turn " + turnCount + " started");
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error updating game: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Render game state
     */
    public void render(ConsoleRenderer renderer) {
        if (!active) {
            renderer.renderMessage("Game not active");
            return;
        }

        renderer.renderSeparator();
        renderer.renderTitle("MTG Match Status");
        renderer.renderSeparator();

        // Render match info
        renderer.renderKeyValue("Turn", String.valueOf(turnCount));
        renderer.renderKeyValue("Phase", getCurrentPhase());
        renderer.renderKeyValue("Active Player", getActivePlayerName());

        renderer.renderSeparator();

        // Render player states
        renderPlayerStates(renderer);

        // Render battlefield
        renderer.renderSeparator();
        renderer.renderTitle("Battlefield");
        renderBattlefield(renderer);

        // Render recent events
        if (!eventHistory.isEmpty()) {
            renderer.renderSeparator();
            renderer.renderTitle("Recent Events");
            renderRecentEvents(renderer, 5);
        }

        // Render game over state
        if (gameOver) {
            renderer.renderSeparator();
            renderer.renderGameOver(getWinner());
        }
    }

    /**
     * Load decks based on configuration
     */
    private void loadDecks() {
        // Load deck 1
        String deck1Name = config.getDeck1Name();
        Deck deck1 = deckManager.loadDeck(deck1Name);
        if (deck1 == null) {
            deck1 = deckManager.createTutorialDeck(deck1Name);
        }
        loadedDecks.put("Player1", deck1);
        System.out.println("  ✓ Loaded deck: " + deck1Name);

        // Load deck 2
        String deck2Name = config.getDeck2Name();
        Deck deck2 = deckManager.loadDeck(deck2Name);
        if (deck2 == null) {
            deck2 = deckManager.createTutorialDeck(deck2Name);
        }
        loadedDecks.put("Player2", deck2);
        System.out.println("  ✓ Loaded deck: " + deck2Name);
    }

    /**
     * Create AI players based on configuration
     */
    private void createAIPlayers() {
        aiAgents = new ArrayList<>();

        // Create AI for player 1
        AIAgent ai1 = createAIAgent(config.getPlayer1AI(), "Player1");
        aiAgents.add(ai1);
        System.out.println("  ✓ Created AI: " + ai1.getName());

        // Create AI for player 2
        AIAgent ai2 = createAIAgent(config.getPlayer2AI(), "Player2");
        aiAgents.add(ai2);
        System.out.println("  ✓ Created AI: " + ai2.getName());
    }

    /**
     * Create specific AI agent based on type
     */
    private AIAgent createAIAgent(GameConfig.AIType type, String name) {
        switch (type) {
            case AGGRESSIVE:
                return new TutorialAI(name + " (Aggressive)", true);
            case CONTROL:
                return new TutorialAI(name + " (Control)", false);
            case RANDOM:
                return new TutorialAI(name + " (Random)", false); // Simplified
            default:
                return new TutorialAI(name + " (Default)", false);
        }
    }

    /**
     * Setup match with players and decks
     */
    private void setupMatch() {
        // Create registered players
        registeredPlayers = new ArrayList<>();

        RegisteredPlayer regPlayer1 = new RegisteredPlayer(loadedDecks.get("Player1"));
        regPlayer1.setPlayer(aiAgents.get(0).getLobbyPlayer());
        registeredPlayers.add(regPlayer1);

        RegisteredPlayer regPlayer2 = new RegisteredPlayer(loadedDecks.get("Player2"));
        regPlayer2.setPlayer(aiAgents.get(1).getLobbyPlayer());
        registeredPlayers.add(regPlayer2);

        // Create game rules
        GameRules gameRules = new GameRules(GameType.Constructed);
        gameRules.setGamesPerMatch(config.getGamesPerMatch());
        gameRules.setManaBurn(config.isManaBurnEnabled());

        // Apply any custom rules
        applyCustomRules(gameRules);

        // Create match
        match = new Match(gameRules, registeredPlayers, config.getMatchName());
        System.out.println("  ✓ Match created: " + config.getMatchName());
    }

    /**
     * Apply custom game rules from configuration
     */
    private void applyCustomRules(GameRules rules) {
        if (config.getStartingLife() != mtg_commons.DEFAULT_STARTING_LIFE) {
            rules.setStartingLife(config.getStartingLife());
        }

        if (config.getStartingHandSize() != mtg_commons.DEFAULT_HAND_SIZE) {
            rules.setStartingHandSize(config.getStartingHandSize());
        }
    }

    /**
     * Start the game
     */
    private void startGame() {
        forgeGame = match.createGame();

        if (forgeGame == null) {
            throw new RuntimeException("Failed to create game");
        }

        try {
            match.startGame(forgeGame, null);
            logEvent("Game started");
        } catch (Exception e) {
            System.err.println("⚠️  Game start encountered issues: " + e.getMessage());
        }
    }

    /**
     * Process AI turn if needed
     */
    private void processAITurn() {
        if (forgeGame == null || !forgeGame.getPhaseHandler().isNeedToNextPhase()) {
            return;
        }

        // Let AI process their turn
        Player activePlayer = forgeGame.getPhaseHandler().getPlayerTurn();
        if (activePlayer != null) {
            int playerIndex = forgeGame.getPlayers().indexOf(activePlayer);
            if (playerIndex >= 0 && playerIndex < aiAgents.size()) {
                AIAgent ai = aiAgents.get(playerIndex);
                // AI processing is handled by Forge's PlayerControllerAi
            }
        }
    }

    /**
     * Handle game over state
     */
    private void handleGameOver() {
        gameOver = true;
        active = false;

        long gameTime = System.currentTimeMillis() - gameStartTime;
        String winner = getWinner();

        logEvent("Game Over! Winner: " + winner);
        logEvent("Game Duration: " + (gameTime / 1000) + " seconds");
        logEvent("Total Turns: " + turnCount);

        // Generate game statistics
        generateGameStats();
    }

    /**
     * Generate end-game statistics
     */
    private void generateGameStats() {
        System.out.println("\n📊 Game Statistics:");
        System.out.println("  Duration: " + ((System.currentTimeMillis() - gameStartTime) / 1000) + " seconds");
        System.out.println("  Total Turns: " + turnCount);
        System.out.println("  Winner: " + getWinner());

        for (Player p : forgeGame.getPlayers()) {
            System.out.println("\n  " + p.getName() + ":");
            System.out.println("    Final Life: " + p.getLife());
            System.out.println("    Cards Drawn: " + p.getNumDrawnThisTurn());
            System.out.println("    Lands Played: " + p.getLandsPlayedThisTurn());
        }
    }

    // Rendering helper methods

    private void renderPlayerStates(ConsoleRenderer renderer) {
        if (forgeGame == null) return;

        for (Player player : forgeGame.getPlayers()) {
            renderer.renderPlayerState(
                    player.getName(),
                    player.getLife(),
                    player.getCardsIn(ZoneType.Hand).size(),
                    player.getCardsIn(ZoneType.Library).size(),
                    player.getManaPool().totalMana()
            );
        }
    }

    private void renderBattlefield(ConsoleRenderer renderer) {
        if (forgeGame == null) return;

        for (Player player : forgeGame.getPlayers()) {
            renderer.renderSubtitle(player.getName() + "'s Board:");

            List<Card> creatures = player.getCreaturesInPlay();
            if (!creatures.isEmpty()) {
                renderer.renderList("Creatures", creatures, Card::getName);
            }

            List<Card> other = player.getCardsIn(ZoneType.Battlefield);
            other.removeAll(creatures);
            if (!other.isEmpty()) {
                renderer.renderList("Other Permanents", other, Card::getName);
            }
        }
    }

    private void renderRecentEvents(ConsoleRenderer renderer, int count) {
        int start = Math.max(0, eventHistory.size() - count);
        for (int i = start; i < eventHistory.size(); i++) {
            GameEvent event = eventHistory.get(i);
            renderer.renderEvent(event.timestamp, event.message);
        }
    }

    // Utility methods

    private String getCurrentPhase() {
        if (forgeGame != null && forgeGame.getPhaseHandler() != null) {
            return forgeGame.getPhaseHandler().getPhase().toString();
        }
        return "Unknown";
    }

    private String getActivePlayerName() {
        if (forgeGame != null && forgeGame.getPhaseHandler() != null) {
            Player active = forgeGame.getPhaseHandler().getPlayerTurn();
            return active != null ? active.getName() : "None";
        }
        return "Unknown";
    }

    private String getWinner() {
        if (forgeGame != null && forgeGame.getOutcome() != null) {
            return forgeGame.getOutcome().getWinningLobbyPlayer().getName();
        }
        return "Unknown";
    }

    private void logEvent(String message) {
        eventHistory.add(new GameEvent(System.currentTimeMillis(), message));
    }

    // Getters

    public Game getForgeGame() { return forgeGame; }
    public boolean isActive() { return active; }
    public boolean isGameOver() { return gameOver; }
    public GameState getGameState() { return gameState; }
    public List<AIAgent> getAIAgents() { return Collections.unmodifiableList(aiAgents); }

    /**
     * Simple game event for history tracking
     */
    private static class GameEvent {
        final long timestamp;
        final String message;

        GameEvent(long timestamp, String message) {
            this.timestamp = timestamp;
            this.message = message;
        }
    }
}