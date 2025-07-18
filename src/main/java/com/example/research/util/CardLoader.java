package com.example.research.util;

import com.example.research.mtg_commons;
import forge.*;
import forge.item.PaperCard;
import forge.item.IPaperCard;
import forge.card.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CardLoader - Comprehensive card data management utilities
 *
 * Core Responsibilities:
 * - Card file creation and management
 * - Batch card loading operations
 * - Edition file management
 * - Card data validation
 */
public class CardLoader {

    // File paths
    private final String cardDataPath;
    private final String editionsPath;

    // Card templates
    private final Map<String, CardTemplate> cardTemplates;

    // Statistics
    private int cardsCreated = 0;
    private int cardsLoaded = 0;
    private List<String> errors = new ArrayList<>();

    /**
     * Default constructor
     */
    public CardLoader() {
        this(mtg_commons.DEFAULT_CARD_DATA_PATH);
    }

    /**
     * Constructor with custom path
     */
    public CardLoader(String cardDataPath) {
        this.cardDataPath = cardDataPath;
        this.editionsPath = mtg_commons.DEFAULT_RESOURCE_PATH + "/editions";
        this.cardTemplates = new HashMap<>();

        // Initialize with basic templates
        initializeBasicTemplates();
    }

    /**
     * Create a single card file
     */
    public void createCard(CardDefinition definition) throws IOException {
        File cardFile = new File(cardDataPath, definition.getFileName() + ".txt");

        try (PrintWriter writer = new PrintWriter(cardFile)) {
            // Write card data
            writer.println("Name:" + definition.name);
            writer.println("ManaCost:" + definition.manaCost);
            writer.println("Types:" + definition.types);

            // Write abilities if present
            if (definition.abilities != null && !definition.abilities.isEmpty()) {
                writer.println(definition.abilities);
            }

            // Write oracle text
            writer.println("Oracle:" + definition.oracleText);

            // Additional fields
            if (definition.power != null && definition.toughness != null) {
                writer.println("PT:" + definition.power + "/" + definition.toughness);
            }

            if (definition.keywords != null && !definition.keywords.isEmpty()) {
                writer.println("K:" + String.join(",", definition.keywords));
            }

            if (definition.colors != null && !definition.colors.isEmpty()) {
                writer.println("Colors:" + definition.colors);
            }

            if (definition.rarity != null) {
                writer.println("Rarity:" + definition.rarity);
            }

            cardsCreated++;
            System.out.println("  ✓ Created card: " + definition.name);

        } catch (IOException e) {
            errors.add("Failed to create card " + definition.name + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * Create multiple cards from definitions
     */
    public void createCards(List<CardDefinition> definitions) throws IOException {
        System.out.println("📝 Creating " + definitions.size() + " card files...");

        for (CardDefinition def : definitions) {
            createCard(def);
        }

        System.out.println("✅ Created " + cardsCreated + " cards");
    }

    /**
     * Load basic land cards
     */
    public void createBasicLands() throws IOException {
        System.out.println("🏞️  Creating basic land cards...");

        List<CardDefinition> lands = Arrays.asList(
                new CardDefinition("Plains", "0", "Basic Land Plains")
                        .withAbilities("A:AB$ Mana | Cost$ T | Produced$ W | SpellDescription$ Add {W}.")
                        .withOracle("({T}: Add {W}.)"),

                new CardDefinition("Island", "0", "Basic Land Island")
                        .withAbilities("A:AB$ Mana | Cost$ T | Produced$ U | SpellDescription$ Add {U}.")
                        .withOracle("({T}: Add {U}.)"),

                new CardDefinition("Swamp", "0", "Basic Land Swamp")
                        .withAbilities("A:AB$ Mana | Cost$ T | Produced$ B | SpellDescription$ Add {B}.")
                        .withOracle("({T}: Add {B}.)"),

                new CardDefinition("Mountain", "0", "Basic Land Mountain")
                        .withAbilities("A:AB$ Mana | Cost$ T | Produced$ R | SpellDescription$ Add {R}.")
                        .withOracle("({T}: Add {R}.)"),

                new CardDefinition("Forest", "0", "Basic Land Forest")
                        .withAbilities("A:AB$ Mana | Cost$ T | Produced$ G | SpellDescription$ Add {G}.")
                        .withOracle("({T}: Add {G}.)"),

                new CardDefinition("Wastes", "0", "Basic Land")
                        .withAbilities("A:AB$ Mana | Cost$ T | Produced$ C | SpellDescription$ Add {C}.")
                        .withOracle("({T}: Add {C}.)")
        );

        createCards(lands);
    }

    /**
     * Create tutorial set cards
     */
    public void createTutorialCards() throws IOException {
        System.out.println("🎓 Creating tutorial cards...");

        // Create from templates
        List<CardDefinition> tutorialCards = new ArrayList<>();

        // Add cards from templates
        tutorialCards.addAll(createCatsDeckCards());
        tutorialCards.addAll(createVampiresDeckCards());

        createCards(tutorialCards);
    }

    /**
     * Create cats deck cards
     */
    private List<CardDefinition> createCatsDeckCards() {
        return Arrays.asList(
                new CardDefinition("Savannah Lions", "W", "Creature Cat")
                        .withPT(2, 1)
                        .withOracle("Savannah Lions"),

                new CardDefinition("Leonin Skyhunter", "WW", "Creature Cat Knight")
                        .withPT(2, 2)
                        .withKeywords("Flying")
                        .withOracle("Flying"),

                new CardDefinition("Prideful Parent", "2W", "Creature Cat")
                        .withPT(2, 2)
                        .withKeywords("Vigilance")
                        .withOracle("Vigilance"),

                new CardDefinition("Felidar Savior", "3W", "Creature Cat Beast")
                        .withPT(2, 3)
                        .withKeywords("Lifelink")
                        .withOracle("Lifelink"),

                new CardDefinition("Jazal Goldmane", "2WW", "Legendary Creature Cat Warrior")
                        .withPT(4, 4)
                        .withKeywords("First Strike")
                        .withOracle("First strike"),

                new CardDefinition("Angelic Edict", "4W", "Sorcery")
                        .withAbilities("A:SP$ Destroy | Cost$ 4 W | ValidTgts$ Creature,Enchantment | TgtPrompt$ Select target creature or enchantment | SpellDescription$ Destroy target creature or enchantment.")
                        .withOracle("Destroy target creature or enchantment."),

                new CardDefinition("Pacifism", "1W", "Enchantment Aura")
                        .withKeywords("Enchant:creature")
                        .withOracle("Enchant creature\\nEnchanted creature can't attack or block."),

                new CardDefinition("Moment of Triumph", "W", "Instant")
                        .withAbilities("A:SP$ Pump | Cost$ W | ValidTgts$ Creature | TgtPrompt$ Select target creature | NumAtt$ +2 | NumDef$ +2 | SpellDescription$ Target creature gets +2/+2 until end of turn.")
                        .withOracle("Target creature gets +2/+2 until end of turn. You gain 2 life."),

                new CardDefinition("Elspeth's Smite", "W", "Instant")
                        .withAbilities("A:SP$ DealDamage | Cost$ W | ValidTgts$ Creature.attacking,Creature.blocking | TgtPrompt$ Select target attacking or blocking creature | NumDmg$ 3 | SpellDescription$ CARDNAME deals 3 damage to target attacking or blocking creature.")
                        .withOracle("Elspeth's Smite deals 3 damage to target attacking or blocking creature.")
        );
    }

    /**
     * Create vampires deck cards
     */
    private List<CardDefinition> createVampiresDeckCards() {
        return Arrays.asList(
                new CardDefinition("Vampire Interloper", "1B", "Creature Vampire Scout")
                        .withPT(2, 1)
                        .withKeywords("Flying")
                        .withOracle("Flying\\nThis creature can't block."),

                new CardDefinition("Vampire Spawn", "2B", "Creature Vampire")
                        .withPT(2, 3)
                        .withOracle("When this creature enters, each opponent loses 2 life and you gain 2 life."),

                new CardDefinition("Moment of Craving", "1B", "Instant")
                        .withAbilities("A:SP$ Pump | Cost$ 1 B | ValidTgts$ Creature | TgtPrompt$ Select target creature | NumAtt$ -2 | NumDef$ -2 | SpellDescription$ Target creature gets -2/-2 until end of turn.")
                        .withOracle("Target creature gets -2/-2 until end of turn. You gain 2 life."),

                new CardDefinition("Highborn Vampire", "3B", "Creature Vampire Warrior")
                        .withPT(4, 3)
                        .withOracle("Highborn Vampire"),

                new CardDefinition("Untamed Hunger", "2B", "Enchantment Aura")
                        .withKeywords("Enchant:creature")
                        .withOracle("Enchant creature\\nEnchanted creature gets +2/+1 and has menace."),

                new CardDefinition("Bloodtithe Collector", "4B", "Creature Vampire Noble")
                        .withPT(3, 4)
                        .withKeywords("Flying")
                        .withOracle("Flying"),

                new CardDefinition("Hero's Downfall", "1BB", "Instant")
                        .withAbilities("A:SP$ Destroy | Cost$ 1 B B | ValidTgts$ Creature,Planeswalker | TgtPrompt$ Select target creature or planeswalker | SpellDescription$ Destroy target creature or planeswalker.")
                        .withOracle("Destroy target creature or planeswalker.")
        );
    }

    /**
     * Create edition file with card entries
     */
    public void createEditionFile(String editionCode, String editionName, List<EditionEntry> entries) throws IOException {
        File editionFile = new File(editionsPath, editionCode + ".txt");

        try (PrintWriter writer = new PrintWriter(editionFile)) {
            // Write metadata
            writer.println("[metadata]");
            writer.println("Code=" + editionCode);
            writer.println("Date=" + new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            writer.println("Name=" + editionName);
            writer.println("Type=Other");
            writer.println();

            // Write card entries
            writer.println("[cards]");
            for (EditionEntry entry : entries) {
                writer.println(entry.toString());
            }

            System.out.println("✅ Created edition file: " + editionFile.getName());
        }
    }

    /**
     * Create default tutorial edition
     */
    public void createTutorialEdition() throws IOException {
        List<EditionEntry> entries = Arrays.asList(
                // Basic lands
                new EditionEntry(1, "C", "Plains"),
                new EditionEntry(2, "C", "Island"),
                new EditionEntry(3, "C", "Swamp"),
                new EditionEntry(4, "C", "Mountain"),
                new EditionEntry(5, "C", "Forest"),

                // Cats cards
                new EditionEntry(10, "C", "Savannah Lions"),
                new EditionEntry(11, "C", "Leonin Skyhunter"),
                new EditionEntry(12, "C", "Prideful Parent"),
                new EditionEntry(13, "U", "Felidar Savior"),
                new EditionEntry(14, "R", "Jazal Goldmane"),
                new EditionEntry(15, "C", "Angelic Edict"),
                new EditionEntry(16, "C", "Pacifism"),
                new EditionEntry(17, "C", "Moment of Triumph"),
                new EditionEntry(18, "C", "Elspeth's Smite"),

                // Vampire cards
                new EditionEntry(20, "C", "Vampire Interloper"),
                new EditionEntry(21, "C", "Vampire Spawn"),
                new EditionEntry(22, "C", "Moment of Craving"),
                new EditionEntry(23, "U", "Highborn Vampire"),
                new EditionEntry(24, "C", "Untamed Hunger"),
                new EditionEntry(25, "U", "Bloodtithe Collector"),
                new EditionEntry(26, "R", "Hero's Downfall")
        );

        createEditionFile("TUTORIAL", "Tutorial Set", entries);
    }

    /**
     * Load cards from file system
     */
    public List<PaperCard> loadCardsFromDirectory() {
        List<PaperCard> loadedCards = new ArrayList<>();

        try {
            Path cardsPath = Paths.get(cardDataPath);
            if (!Files.exists(cardsPath)) {
                System.err.println("❌ Card directory does not exist: " + cardDataPath);
                return loadedCards;
            }

            Files.walk(cardsPath)
                    .filter(path -> path.toString().endsWith(".txt"))
                    .forEach(path -> {
                        try {
                            // Load card using StaticData if available
                            String cardName = path.getFileName().toString().replace(".txt", "");
                            if (StaticData.instance() != null) {
                                PaperCard card = StaticData.instance().getCommonCards().getCard(cardName);
                                if (card != null) {
                                    loadedCards.add(card);
                                    cardsLoaded++;
                                }
                            }
                        } catch (Exception e) {
                            errors.add("Failed to load card from " + path + ": " + e.getMessage());
                        }
                    });

        } catch (IOException e) {
            System.err.println("❌ Error loading cards: " + e.getMessage());
        }

        System.out.println("✅ Loaded " + cardsLoaded + " cards");
        return loadedCards;
    }

    /**
     * Validate card files
     */
    public ValidationResult validateCards() {
        ValidationResult result = new ValidationResult();

        try {
            Path cardsPath = Paths.get(cardDataPath);
            if (!Files.exists(cardsPath)) {
                result.addError("Card directory does not exist: " + cardDataPath);
                return result;
            }

            Files.walk(cardsPath)
                    .filter(path -> path.toString().endsWith(".txt"))
                    .forEach(path -> validateCardFile(path, result));

        } catch (IOException e) {
            result.addError("Error validating cards: " + e.getMessage());
        }

        return result;
    }

    /**
     * Validate single card file
     */
    private void validateCardFile(Path path, ValidationResult result) {
        try {
            List<String> lines = Files.readAllLines(path);
            String fileName = path.getFileName().toString();

            // Check required fields
            boolean hasName = false;
            boolean hasTypes = false;
            boolean hasOracle = false;

            for (String line : lines) {
                if (line.startsWith("Name:")) hasName = true;
                if (line.startsWith("Types:")) hasTypes = true;
                if (line.startsWith("Oracle:")) hasOracle = true;
            }

            if (!hasName) result.addError(fileName + ": Missing Name field");
            if (!hasTypes) result.addError(fileName + ": Missing Types field");
            if (!hasOracle) result.addWarning(fileName + ": Missing Oracle field");

            result.filesChecked++;

        } catch (IOException e) {
            result.addError("Cannot read " + path + ": " + e.getMessage());
        }
    }

    /**
     * Initialize basic card templates
     */
    private void initializeBasicTemplates() {
        // Basic creature template
        cardTemplates.put("BasicCreature", new CardTemplate()
                .withTypes("Creature")
                .withPT(2, 2));

        // Basic spell template
        cardTemplates.put("BasicSpell", new CardTemplate()
                .withTypes("Instant"));

        // Basic enchantment template
        cardTemplates.put("BasicEnchantment", new CardTemplate()
                .withTypes("Enchantment"));
    }

    // Getters
    public int getCardsCreated() { return cardsCreated; }
    public int getCardsLoaded() { return cardsLoaded; }
    public List<String> getErrors() { return new ArrayList<>(errors); }

    /**
     * Card definition class
     */
    public static class CardDefinition {
        public final String name;
        public final String manaCost;
        public final String types;

        public String abilities;
        public String oracleText;
        public Integer power;
        public Integer toughness;
        public List<String> keywords;
        public String colors;
        public String rarity = "C";

        public CardDefinition(String name, String manaCost, String types) {
            this.name = name;
            this.manaCost = manaCost;
            this.types = types;
            this.oracleText = name; // Default oracle text
        }

        public CardDefinition withAbilities(String abilities) {
            this.abilities = abilities;
            return this;
        }

        public CardDefinition withOracle(String oracle) {
            this.oracleText = oracle;
            return this;
        }

        public CardDefinition withPT(int power, int toughness) {
            this.power = power;
            this.toughness = toughness;
            return this;
        }

        public CardDefinition withKeywords(String... keywords) {
            this.keywords = Arrays.asList(keywords);
            return this;
        }

        public CardDefinition withColors(String colors) {
            this.colors = colors;
            return this;
        }

        public CardDefinition withRarity(String rarity) {
            this.rarity = rarity;
            return this;
        }

        public String getFileName() {
            return name.toLowerCase().replace(" ", "_").replace(",", "").replace("'", "");
        }
    }

    /**
     * Card template for common patterns
     */
    public static class CardTemplate {
        private Map<String, String> defaults = new HashMap<>();

        public CardTemplate withTypes(String types) {
            defaults.put("types", types);
            return this;
        }

        public CardTemplate withPT(int power, int toughness) {
            defaults.put("power", String.valueOf(power));
            defaults.put("toughness", String.valueOf(toughness));
            return this;
        }

        public CardDefinition create(String name, String manaCost) {
            CardDefinition def = new CardDefinition(name, manaCost, defaults.get("types"));

            if (defaults.containsKey("power") && defaults.containsKey("toughness")) {
                def.withPT(
                        Integer.parseInt(defaults.get("power")),
                        Integer.parseInt(defaults.get("toughness"))
                );
            }

            return def;
        }
    }

    /**
     * Edition entry for edition files
     */
    public static class EditionEntry {
        public final int number;
        public final String rarity;
        public final String cardName;

        public EditionEntry(int number, String rarity, String cardName) {
            this.number = number;
            this.rarity = rarity;
            this.cardName = cardName;
        }

        @Override
        public String toString() {
            return number + " " + rarity + " " + cardName;
        }
    }

    /**
     * Validation result container
     */
    public static class ValidationResult {
        public int filesChecked = 0;
        public List<String> errors = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public void print() {
            System.out.println("\n📋 Validation Results:");
            System.out.println("  Files checked: " + filesChecked);

            if (errors.isEmpty()) {
                System.out.println("  ✅ No errors found");
            } else {
                System.out.println("  ❌ Errors (" + errors.size() + "):");
                errors.forEach(e -> System.out.println("    - " + e));
            }

            if (!warnings.isEmpty()) {
                System.out.println("  ⚠️  Warnings (" + warnings.size() + "):");
                warnings.forEach(w -> System.out.println("    - " + w));
            }
        }
    }
}