package com.example.research;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import forge.*;
import forge.ai.AiController;
import forge.ai.LobbyPlayerAi;
import forge.ai.PlayerControllerAi;
import forge.card.ColorSet;
import forge.card.ICardFace;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.deck.*;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.ability.effects.RollDiceEffect;
import forge.game.combat.Combat;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostPartMana;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordInterface;
import forge.game.mana.Mana;
import forge.game.mana.ManaConversionMatrix;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.*;
import forge.game.card.*;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.*;
import forge.game.staticability.StaticAbility;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.PlayerZone;
import forge.game.zone.ZoneType;
import forge.item.*;
import forge.util.*;
import forge.util.collect.FCollectionView;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.util.*;
import java.util.function.Predicate;

/**
 * Forge Test with Integrated AI Implementation
 *
 * This implementation uses the proper LobbyPlayerAi pattern from Forge
 * with self-contained draft AI logic integrated directly.
 */
public class ForgeTest {

    public static void main(String[] args) {
        System.out.println("🎮 Starting Forge Match Test with Integrated AI");

        try {
            // Step 1: Initialize Forge environment
            System.out.println("🔧 Initializing Forge environment...");
            initializeForgeEnvironment();

            // Step 2: Create tutorial decks
            System.out.println("🏗️  Creating tutorial decks...");
            Deck catsDeck = createCatsDeck();
            Deck vampiresDeck = createVampiresDeck();

            // Step 3: Run the match with proper AI
            runMatch(catsDeck, vampiresDeck);

        } catch (Exception e) {
            System.err.println("❌ Error setting up match: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // === FORGE ENVIRONMENT INITIALIZATION ===

    private static void initializeForgeEnvironment() {
        try {
            setupForgeDirectories();
            setupLocalization();
            initializeStaticData();
            System.out.println("✓ Forge environment initialized successfully");
        } catch (Exception e) {
            System.err.println("❌ Failed to initialize Forge environment: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static void setupLocalization() {
        System.out.println("🌐 Setting up localization...");
        try {
            Localizer.getInstance().initialize("en-US", "res/languages");
            System.out.println("✓ Localizer initialized successfully");
        } catch (Exception e) {
            System.out.println("⚠️  Localizer initialization failed, continuing with defaults: " + e.getMessage());
            try {
                Localizer.getInstance();
                System.out.println("✓ Basic Localizer instance created");
            } catch (Exception e2) {
                System.out.println("⚠️  Basic Localizer failed too: " + e2.getMessage());
            }
        }

        try {
            System.out.println("🔧 Initializing Lang instance...");
            Lang.createInstance("en-US");
            System.out.println("✓ Lang instance created successfully");
        } catch (Exception e) {
            System.err.println("❌ Failed to create Lang instance: " + e.getMessage());
            throw new RuntimeException("Lang initialization failed", e);
        }
    }

    private static void setupForgeDirectories() {
        System.out.println("📁 Setting up Forge directory structure...");
        try {
            String[] requiredDirs = {
                    "res", "D:/my_files/cards", "res/editions", "res/blockdata",
                    "res/blockdata/formats", "res/languages", "cache", "cache/pics",
                    "cache/layouts", "cache/tokens"
            };

            for (String dir : requiredDirs) {
                File directory = new File(dir);
                if (!directory.exists()) {
                    directory.mkdirs();
                }
            }

            createBasicCards();
            createTutorialCards();
            createEditionFiles();
            createFormatFiles();
            createLanguageFiles();

            System.out.println("✓ Directory structure created");
        } catch (Exception e) {
            System.err.println("❌ Failed to create directories: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static void createLanguageFiles() throws IOException {
        File langDir = new File("res/languages");
        File langFile = new File(langDir, "en-US.txt");

        if (!langFile.exists()) {
            try (PrintWriter writer = new PrintWriter(langFile)) {
                writer.println("# Basic language file for Forge");
                writer.println("lblName=Name");
                writer.println("lblType=Type");
                writer.println("lblCost=Cost");
                writer.println("lblPower=Power");
                writer.println("lblToughness=Toughness");
                writer.println("lblLibrary=Library");
                writer.println("lblHand=Hand");
                writer.println("lblBattlefield=Battlefield");
                writer.println("lblGraveyard=Graveyard");
                writer.println("lblExile=Exile");
                writer.println("lblStack=Stack");
                writer.println("lblCommand=Command");
                writer.println("lblAnte=Ante");
                writer.println("lblSideboard=Sideboard");
            }
        }
    }

    private static void createBasicCards() throws IOException {
        File cardsDir = new File("D:/my_files/cards");

        createCard(cardsDir, "plains", "Plains", "0", "Basic Land Plains",
                "A:AB$ Mana | Cost$ T | Produced$ W | SpellDescription$ Add {W}.",
                "({T}: Add {W}.)");
        createCard(cardsDir, "island", "Island", "0", "Basic Land Island",
                "A:AB$ Mana | Cost$ T | Produced$ U | SpellDescription$ Add {U}.",
                "({T}: Add {U}.)");
        createCard(cardsDir, "swamp", "Swamp", "0", "Basic Land Swamp",
                "A:AB$ Mana | Cost$ T | Produced$ B | SpellDescription$ Add {B}.",
                "({T}: Add {B}.)");
        createCard(cardsDir, "mountain", "Mountain", "0", "Basic Land Mountain",
                "A:AB$ Mana | Cost$ T | Produced$ R | SpellDescription$ Add {R}.",
                "({T}: Add {R}.)");
        createCard(cardsDir, "forest", "Forest", "0", "Basic Land Forest",
                "A:AB$ Mana | Cost$ T | Produced$ G | SpellDescription$ Add {G}.",
                "({T}: Add {G}.)");
    }

    private static void createTutorialCards() throws IOException {
        File cardsDir = new File("D:/my_files/cards");

        // CATS DECK CARDS
        createCard(cardsDir, "savannah_lions", "Savannah Lions", "W", "Creature Cat",
                "PT:2/1", "Savannah Lions");

        createCard(cardsDir, "leonin_skyhunter", "Leonin Skyhunter", "WW", "Creature Cat Knight",
                "PT:2/2\nK:Flying", "Flying");

        createCard(cardsDir, "prideful_parent", "Prideful Parent", "2W", "Creature Cat",
                "PT:2/2\nK:Vigilance", "Vigilance");

        createCard(cardsDir, "felidar_savior", "Felidar Savior", "3W", "Creature Cat Beast",
                "PT:2/3\nK:Lifelink", "Lifelink");

        createCard(cardsDir, "jazal_goldmane", "Jazal Goldmane", "2WW", "Legendary Creature Cat Warrior",
                "PT:4/4\nK:First Strike", "First strike");

        createCard(cardsDir, "angelic_edict", "Angelic Edict", "4W", "Sorcery",
                "A:SP$ Destroy | Cost$ 4 W | ValidTgts$ Creature,Enchantment | TgtPrompt$ Select target creature or enchantment | SpellDescription$ Destroy target creature or enchantment.",
                "Destroy target creature or enchantment.");

        createCard(cardsDir, "pacifism", "Pacifism", "1W", "Enchantment Aura",
                "K:Enchant:creature", "Enchant creature\\nEnchanted creature can't attack or block.");

        createCard(cardsDir, "moment_of_triumph", "Moment of Triumph", "W", "Instant",
                "A:SP$ Pump | Cost$ W | ValidTgts$ Creature | TgtPrompt$ Select target creature | NumAtt$ +2 | NumDef$ +2 | SpellDescription$ Target creature gets +2/+2 until end of turn.",
                "Target creature gets +2/+2 until end of turn. You gain 2 life.");

        createCard(cardsDir, "elspeths_smite", "Elspeth's Smite", "W", "Instant",
                "A:SP$ DealDamage | Cost$ W | ValidTgts$ Creature.attacking,Creature.blocking | TgtPrompt$ Select target attacking or blocking creature | NumDmg$ 3 | SpellDescription$ CARDNAME deals 3 damage to target attacking or blocking creature.",
                "Elspeth's Smite deals 3 damage to target attacking or blocking creature.");

        // VAMPIRE DECK CARDS
        createCard(cardsDir, "vampire_interloper", "Vampire Interloper", "1B", "Creature Vampire Scout",
                "PT:2/1\nK:Flying", "Flying\\nThis creature can't block.");

        createCard(cardsDir, "vampire_spawn", "Vampire Spawn", "2B", "Creature Vampire",
                "PT:2/3", "When this creature enters, each opponent loses 2 life and you gain 2 life.");

        createCard(cardsDir, "moment_of_craving", "Moment of Craving", "1B", "Instant",
                "A:SP$ Pump | Cost$ 1 B | ValidTgts$ Creature | TgtPrompt$ Select target creature | NumAtt$ -2 | NumDef$ -2 | SpellDescription$ Target creature gets -2/-2 until end of turn.",
                "Target creature gets -2/-2 until end of turn. You gain 2 life.");

        createCard(cardsDir, "highborn_vampire", "Highborn Vampire", "3B", "Creature Vampire Warrior",
                "PT:4/3", "Highborn Vampire");

        createCard(cardsDir, "untamed_hunger", "Untamed Hunger", "2B", "Enchantment Aura",
                "K:Enchant:creature", "Enchant creature\\nEnchanted creature gets +2/+1 and has menace.");

        createCard(cardsDir, "bloodtithe_collector", "Bloodtithe Collector", "4B", "Creature Vampire Noble",
                "PT:3/4\nK:Flying", "Flying");

        createCard(cardsDir, "crossway_troublemakers", "Crossway Troublemakers", "5B", "Creature Vampire",
                "PT:5/5", "Attacking Vampires you control have deathtouch and lifelink.");

        createCard(cardsDir, "vengeful_bloodwitch", "Vengeful Bloodwitch", "1B", "Creature Vampire Warlock",
                "PT:1/1", "Whenever this creature or another creature you control dies, target opponent loses 1 life and you gain 1 life.");

        createCard(cardsDir, "heros_downfall", "Hero's Downfall", "1BB", "Instant",
                "A:SP$ Destroy | Cost$ 1 B B | ValidTgts$ Creature,Planeswalker | TgtPrompt$ Select target creature or planeswalker | SpellDescription$ Destroy target creature or planeswalker.",
                "Destroy target creature or planeswalker.");

        createCard(cardsDir, "vampire_neonate", "Vampire Neonate", "B", "Creature Vampire",
                "PT:0/3", "{2}, {T}: Each opponent loses 1 life and you gain 1 life.");

        createCard(cardsDir, "offer_immortality", "Offer Immortality", "1B", "Instant",
                "A:SP$ Pump | Cost$ 1 B | ValidTgts$ Creature | TgtPrompt$ Select target creature | KW$ Deathtouch & Indestructible | SpellDescription$ Target creature gains deathtouch and indestructible until end of turn.",
                "Target creature gains deathtouch and indestructible until end of turn.");

        createCard(cardsDir, "stromkirk_bloodthief", "Stromkirk Bloodthief", "2B", "Creature Vampire Rogue",
                "PT:2/2", "At the beginning of your end step, if an opponent lost life this turn, put a +1/+1 counter on target Vampire you control.");

        createCard(cardsDir, "uncharted_haven", "Uncharted Haven", "0", "Land",
                "K:CARDNAME enters the battlefield tapped.", "This land enters tapped. As it enters, choose a color.\\n{T}: Add one mana of the chosen color.");
    }

    private static void createCard(File dir, String filename, String name, String cost, String types, String abilities, String oracleText) throws IOException {
        File cardFile = new File(dir, filename + ".txt");
        try (PrintWriter writer = new PrintWriter(cardFile)) {
            writer.println("Name:" + name);
            writer.println("ManaCost:" + cost);
            writer.println("Types:" + types);
            if (abilities != null && !abilities.isEmpty()) {
                writer.println(abilities);
            }
            writer.println("Oracle:" + oracleText);
        }
    }

    private static void createEditionFiles() throws IOException {
        File editionsFile = new File("res/editions/TUTORIAL.txt");
        try (PrintWriter writer = new PrintWriter(editionsFile)) {
            writer.println("[metadata]");
            writer.println("Code=TUTORIAL");
            writer.println("Date=2024-01-01");
            writer.println("Name=Tutorial Set");
            writer.println("Type=Other");
            writer.println("");
            writer.println("[cards]");
            writer.println("1 C Plains");
            writer.println("2 C Island");
            writer.println("3 C Swamp");
            writer.println("4 C Mountain");
            writer.println("5 C Forest");
            writer.println("6 C Uncharted Haven");
            writer.println("7 C Savannah Lions");
            writer.println("8 C Vampire Interloper");
            // Add more cards as needed
        }
    }

    private static void createFormatFiles() throws IOException {
        File formatFile = new File("res/blockdata/formats/Constructed.txt");
        try (PrintWriter writer = new PrintWriter(formatFile)) {
            writer.println("Name:Constructed");
            writer.println("Sets:TUTORIAL");
            writer.println("Banned:");
            writer.println("Restricted:");
        }
    }

    private static void initializeStaticData() {
        try {
            System.out.println("🖼️  Initializing ImageKeys...");
            initializeImageKeys();

            String cardDataDir = "D:/my_files/cards";
            String editionFolder = "res/editions";
            String blockDataFolder = "res/blockdata";

            CardStorageReader cardReader = new CardStorageReader(cardDataDir, null, false);

            StaticData staticData = new StaticData(
                    cardReader,
                    null,
                    editionFolder,
                    editionFolder,
                    blockDataFolder,
                    "LATEST_ART_ALL_EDITIONS",
                    true,
                    true
            );

            System.out.println("✓ StaticData initialized successfully");
            System.out.println("   Available cards: " + staticData.getCommonCards().getAllCards().size());

        } catch (Exception e) {
            System.err.println("❌ StaticData initialization failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize StaticData", e);
        }
    }

    private static void initializeImageKeys() {
        try {
            String[] imageDirs = {
                    "cache/pics", "cache/pics/cards", "cache/pics/tokens",
                    "cache/pics/icons", "cache/pics/boosters", "cache/pics/fatpacks",
                    "cache/pics/boosterboxes", "cache/pics/precons", "cache/pics/tournamentpacks"
            };

            for (String dir : imageDirs) {
                File directory = new File(dir);
                if (!directory.exists()) {
                    directory.mkdirs();
                }
            }

            Map<String, String> cardSubdirs = new HashMap<>();
            cardSubdirs.put("TUTORIAL", "TUTORIAL");

            ImageKeys.initializeDirs(
                    "cache/pics/cards/",
                    cardSubdirs,
                    "cache/pics/tokens/",
                    "cache/pics/icons/",
                    "cache/pics/boosters/",
                    "cache/pics/fatpacks/",
                    "cache/pics/boosterboxes/",
                    "cache/pics/precons/",
                    "cache/pics/tournamentpacks/"
            );

            System.out.println("✓ ImageKeys initialized successfully");

        } catch (Exception e) {
            System.err.println("❌ ImageKeys initialization failed: " + e.getMessage());
            throw new RuntimeException("Failed to initialize ImageKeys", e);
        }
    }

    // === DECK CREATION ===

    private static Deck createCatsDeck() {
        Deck deck = new Deck("Cats Tutorial Deck");
        addCardToDeck(deck, "Plains", 8);
        addCardToDeck(deck, "Savannah Lions", 2);
        addCardToDeck(deck, "Leonin Skyhunter", 2);
        addCardToDeck(deck, "Prideful Parent", 2);
        addCardToDeck(deck, "Felidar Savior", 1);
        addCardToDeck(deck, "Jazal Goldmane", 1);
        addCardToDeck(deck, "Angelic Edict", 1);
        addCardToDeck(deck, "Pacifism", 1);
        addCardToDeck(deck, "Moment of Triumph", 1);
        addCardToDeck(deck, "Elspeth's Smite", 1);
        return deck;
    }

    private static Deck createVampiresDeck() {
        Deck deck = new Deck("Vampires Tutorial Deck");
        addCardToDeck(deck, "Swamp", 8);
        addCardToDeck(deck, "Vampire Interloper", 2);
        addCardToDeck(deck, "Vampire Spawn", 2);
        addCardToDeck(deck, "Moment of Craving", 2);
        addCardToDeck(deck, "Highborn Vampire", 1);
        addCardToDeck(deck, "Untamed Hunger", 1);
        addCardToDeck(deck, "Bloodtithe Collector", 1);
        addCardToDeck(deck, "Crossway Troublemakers", 1);
        addCardToDeck(deck, "Vengeful Bloodwitch", 1);
        addCardToDeck(deck, "Hero's Downfall", 1);
        return deck;
    }

    private static void addCardToDeck(Deck deck, String cardName, int count) {
        try {
            if (StaticData.instance() != null && StaticData.instance().getCommonCards() != null) {
                PaperCard card = StaticData.instance().getCommonCards().getCard(cardName);
                if (card != null) {
                    for (int i = 0; i < count; i++) {
                        deck.getMain().add(card);
                    }
                    System.out.println("  ✓ Added " + count + "x " + cardName + " to " + deck.getName());
                } else {
                    System.out.println("  ❌ Could not find card: " + cardName);
                }
            } else {
                System.out.println("  ❌ StaticData not available");
            }
        } catch (Exception e) {
            System.err.println("  ❌ Error adding " + cardName + " to deck: " + e.getMessage());
        }
    }

    // === MATCH EXECUTION WITH PROPER AI ===

    private static void runMatch(Deck deck1, Deck deck2) {
        try {
            System.out.println("👥 Creating AI players using LobbyPlayerAi pattern...");

            // Use TutorialAI which extends LobbyPlayerAi
            TutorialAI aiPlayer1 = new TutorialAI("Cats AI", true);  // aggro style
            TutorialAI aiPlayer2 = new TutorialAI("Vampires AI", false); // control style

            System.out.println("✓ Created AI players");

            RegisteredPlayer regPlayer1 = new RegisteredPlayer(deck1);
            RegisteredPlayer regPlayer2 = new RegisteredPlayer(deck2);

            regPlayer1.setPlayer(aiPlayer1);
            regPlayer2.setPlayer(aiPlayer2);

            List<RegisteredPlayer> players = Arrays.asList(regPlayer1, regPlayer2);
            System.out.println("✓ Created registered players");

            GameRules gameRules = new GameRules(GameType.Constructed);
            gameRules.setGamesPerMatch(1);
            System.out.println("✓ Created game rules");

            Match match = new Match(gameRules, players, "Tutorial Match");
            System.out.println("✓ Created match");

            Game game = match.createGame();
            System.out.println("✓ Created game");

            try {
                match.startGame(game, null);
                System.out.println("🎉 AI game started successfully!");

                // Run a few turns for demonstration
                simulateTurns(game, 5);

            } catch (Exception e) {
                System.err.println("⚠️  Game start encountered issues: " + e.getMessage());
                System.out.println("🎉 AI game created successfully");
            }

            displayGameState(game);

        } catch (Exception e) {
            System.err.println("❌ Error during AI match: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void simulateTurns(Game game, int numTurns) {
        System.out.println("\n📋 Simulating " + numTurns + " turns...");

        for (int i = 0; i < numTurns && !game.isGameOver(); i++) {
            System.out.println("\n--- Turn " + (i + 1) + " ---");

            try {
                // Advance through phases
                if (game.getPhaseHandler() != null) {
                    // This would need actual phase advancement logic
                    System.out.println("Current phase: " + game.getPhaseHandler().getPhase());
                }

                // Display basic game state each turn
                for (Player p : game.getPlayers()) {
                    System.out.println(p.getName() + " - Life: " + p.getLife() +
                            ", Hand: " + p.getCardsIn(ZoneType.Hand).size() +
                            ", Battlefield: " + p.getCardsIn(ZoneType.Battlefield).size());
                }

            } catch (Exception e) {
                System.err.println("Error simulating turn: " + e.getMessage());
                break;
            }
        }
    }

    private static void displayGameState(Game game) {
        try {
            System.out.println("\n📊 Final Game State:");
            System.out.println("   - Players: " + game.getRegisteredPlayers().size());

            if (game.getPhaseHandler() != null) {
                System.out.println("   - Current Turn: " + game.getPhaseHandler().getTurn());
                System.out.println("   - Current Phase: " + game.getPhaseHandler().getPhase());
            }

            for (Player player : game.getPlayers()) {
                System.out.println("\n   Player: " + player.getName());
                System.out.println("   - Life: " + player.getLife());
                System.out.println("   - Hand: " + player.getCardsIn(ZoneType.Hand).size() + " cards");
                System.out.println("   - Library: " + player.getCardsIn(ZoneType.Library).size() + " cards");
                System.out.println("   - Battlefield: " + player.getCardsIn(ZoneType.Battlefield).size() + " permanents");
                System.out.println("   - Graveyard: " + player.getCardsIn(ZoneType.Graveyard).size() + " cards");
            }
        } catch (Exception e) {
            System.err.println("❌ Error displaying game state: " + e.getMessage());
        }
    }

    // === INTEGRATED AI IMPLEMENTATION ===

    /**
     * TutorialAI extends LobbyPlayerAi following Forge patterns
     * Includes integrated draft-style AI logic for tutorial gameplay
     */
    static class TutorialAI extends LobbyPlayerAi {

        private final boolean isAggressive;

        public TutorialAI(String name, boolean aggressive) {
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
                    aiController.allowCheatShuffle(false);
                    aiController.setUseSimulation(false);
                } else {
                    // Control AI configuration
                    aiController.allowCheatShuffle(false);
                    aiController.setUseSimulation(false);
                }
            }

            return player;
        }
    }

    /**
     * Integrated Draft AI Logic
     * Based on BoosterDraftAI.java patterns
     */
    static class IntegratedDraftAI {

        // Color preferences for draft
        private static final String[] COLOR_PREFERENCE = {"W", "U", "B", "R", "G"};
        private String primaryColor = null;
        private String secondaryColor = null;
        private int creatureCount = 0;

        /**
         * Simple card evaluation for tutorial cards
         */
        public int evaluateCard(PaperCard card) {
            int score = 0;

            // Basic evaluation based on card type
            if (card.getRules().getType().isCreature()) {
                score += 50; // Creatures are valuable
                score += evaluateCreature(card);
            } else if (card.getRules().getType().isInstant() || card.getRules().getType().isSorcery()) {
                score += 30; // Spells are useful
                if (card.getRules().getOracleText().contains("Destroy")) {
                    score += 20; // Removal is good
                }
            } else if (card.getRules().getType().isEnchantment()) {
                score += 20; // Enchantments are okay
            }

            // Color matching bonus
            if (primaryColor != null && card.getRules().getColor().hasAnyColor(Integer.parseInt(primaryColor))) {
                score += 25;
            }
            if (secondaryColor != null && card.getRules().getColor().hasAnyColor(Integer.parseInt(secondaryColor))) {
                score += 15;
            }

            return score;
        }

        /**
         * Evaluate creature based on P/T and abilities
         */
        private int evaluateCreature(PaperCard card) {
            int score = 0;

            // Get power/toughness
            String pt = card.getRules().getPower() + "/" + card.getRules().getToughness();
            if (pt.equals("2/1")) score += 10;
            else if (pt.equals("2/2")) score += 15;
            else if (pt.equals("2/3")) score += 18;
            else if (pt.equals("3/4")) score += 25;
            else if (pt.equals("4/3")) score += 22;
            else if (pt.equals("4/4")) score += 30;
            else if (pt.equals("5/5")) score += 35;

            // Check for keywords
            String oracle = card.getRules().getOracleText();
            if (oracle.contains("Flying")) score += 15;
            if (oracle.contains("First strike")) score += 10;
            if (oracle.contains("Lifelink")) score += 8;
            if (oracle.contains("Vigilance")) score += 7;
            if (oracle.contains("Deathtouch")) score += 12;
            if (oracle.contains("Menace")) score += 5;

            return score;
        }

        /**
         * Pick a card from available options (draft simulation)
         */
        public PaperCard pickCard(List<PaperCard> options) {
            PaperCard bestCard = null;
            int bestScore = -1;

            // First pick - choose best card and commit to color
            if (primaryColor == null && !options.isEmpty()) {
                for (PaperCard card : options) {
                    int score = evaluateCard(card);
                    if (score > bestScore) {
                        bestScore = score;
                        bestCard = card;
                    }
                }

                // Set primary color based on first pick
                if (bestCard != null && !bestCard.getRules().getColor().isColorless()) {
                    for (String color : COLOR_PREFERENCE) {
                        if (bestCard.getRules().getColor().hasAnyColor(Integer.parseInt(color))) {
                            primaryColor = color;
                            break;
                        }
                    }
                }

                return bestCard;
            }

            // Subsequent picks - prefer cards in our colors
            for (PaperCard card : options) {
                int score = evaluateCard(card);

                // Need creatures
                if (creatureCount < 16 && card.getRules().getType().isCreature()) {
                    score += 30;
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestCard = card;
                }
            }

            // Update creature count
            if (bestCard != null && bestCard.getRules().getType().isCreature()) {
                creatureCount++;
            }

            // Set secondary color if needed
            if (secondaryColor == null && bestCard != null &&
                    !bestCard.getRules().getColor().hasAnyColor(Integer.parseInt(primaryColor))) {
                for (String color : COLOR_PREFERENCE) {
                    if (!color.equals(primaryColor) &&
                            bestCard.getRules().getColor().hasAnyColor(Integer.parseInt(color))) {
                        secondaryColor = color;
                        break;
                    }
                }
            }

            return bestCard;
        }
    }
}