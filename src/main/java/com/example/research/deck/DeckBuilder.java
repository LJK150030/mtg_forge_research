package com.example.research.deck;

import forge.deck.*;
import forge.item.PaperCard;
import forge.StaticData;
import forge.card.ColorSet;

import java.util.*;

/**
 * DeckBuilder - Utilities for constructing and validating MTG decks
 *
 * Provides functionality for:
 * - Building decks from templates
 * - Validating deck legality
 * - Analyzing deck composition
 * - Generating deck variations
 */
public class DeckBuilder {

    // Deck construction constants
    private static final int MIN_DECK_SIZE = 40;  // Limited
    private static final int STANDARD_DECK_SIZE = 60;  // Constructed
    private static final int LIMITED_DECK_SIZE = 40;
    private static final int COMMANDER_DECK_SIZE = 100;

    // Common deck ratios
    private static final double DEFAULT_LAND_RATIO = 0.4;  // 40% lands
    private static final double DEFAULT_CREATURE_RATIO = 0.35;  // 35% creatures
    private static final double DEFAULT_SPELL_RATIO = 0.25;  // 25% other spells

    /**
     * Create a new empty deck with the given name
     */
    public static Deck createEmptyDeck(String name) {
        return new Deck(name);
    }

    /**
     * Build a basic deck from a list of cards
     */
    public static Deck buildDeck(String name, Map<String, Integer> cardList) {
        Deck deck = new Deck(name);
        CardPool main = deck.getMain();

        for (Map.Entry<String, Integer> entry : cardList.entrySet()) {
            String cardName = entry.getKey();
            int count = entry.getValue();

            PaperCard card = getCard(cardName);
            if (card != null) {
                for (int i = 0; i < count; i++) {
                    main.add(card);
                }
            } else {
                System.err.println("⚠️  Card not found: " + cardName);
            }
        }

        return deck;
    }

    /**
     * Add cards to a deck section
     */
    public static void addCardToDeck(Deck deck, String cardName, int count) {
        addCardToDeck(deck, cardName, count, DeckSection.Main);
    }

    /**
     * Add cards to a specific deck section
     */
    public static void addCardToDeck(Deck deck, String cardName, int count, DeckSection section) {
        try {
            PaperCard card = getCard(cardName);
            if (card != null) {
                CardPool deckSection = deck.get(section);
                for (int i = 0; i < count; i++) {
                    deckSection.add(card);
                }
                System.out.println("  ✓ Added " + count + "x " + cardName + " to " + deck.getName());
            } else {
                System.err.println("  ❌ Could not find card: " + cardName);
            }
        } catch (Exception e) {
            System.err.println("  ❌ Error adding " + cardName + " to deck: " + e.getMessage());
        }
    }

    /**
     * Build a deck from a template
     */
    public static Deck buildFromTemplate(DeckTemplate template) {
        Deck deck = new Deck(template.getName());

        // Add lands
        for (Map.Entry<String, Integer> entry : template.getLands().entrySet()) {
            addCardToDeck(deck, entry.getKey(), entry.getValue());
        }

        // Add creatures
        for (Map.Entry<String, Integer> entry : template.getCreatures().entrySet()) {
            addCardToDeck(deck, entry.getKey(), entry.getValue());
        }

        // Add spells
        for (Map.Entry<String, Integer> entry : template.getSpells().entrySet()) {
            addCardToDeck(deck, entry.getKey(), entry.getValue());
        }

        // Add sideboard if present
        if (template.hasSideboard()) {
            for (Map.Entry<String, Integer> entry : template.getSideboard().entrySet()) {
                addCardToDeck(deck, entry.getKey(), entry.getValue(), DeckSection.Sideboard);
            }
        }

        return deck;
    }

    /**
     * Validate deck legality
     */
    public static DeckValidation validateDeck(Deck deck, DeckFormat format) {
        DeckValidation validation = new DeckValidation();
        CardPool main = deck.getMain();

        // Check deck size
        int deckSize = main.countAll();
        int minSize = format.getMinDeckSize();

        if (deckSize < minSize) {
            validation.addError("Deck has " + deckSize + " cards, minimum is " + minSize);
        }

        // Check card legality
        for (Map.Entry<PaperCard, Integer> card : main) {
            if (!format.isCardLegal((PaperCard) card)) {
                validation.addError(((PaperCard) card).getName() + " is not legal in " + format.getName());
            }
        }

        // Check card count limits
        Map<String, Integer> cardCounts = new HashMap<>();
        for (Map.Entry<PaperCard, Integer> card : main) {
            String name = card.getKey().getName();
            cardCounts.put(name, cardCounts.getOrDefault(name, 0) + 1);
        }

        for (Map.Entry<String, Integer> entry : cardCounts.entrySet()) {
            String cardName = entry.getKey();
            int count = entry.getValue();
            int maxAllowed = format.getMaxCopies(cardName);

            if (count > maxAllowed) {
                validation.addError("Too many copies of " + cardName + " (" + count + "/" + maxAllowed + ")");
            }
        }

        // Format-specific validation
        if (format == DeckFormat.COMMANDER) {
            validateCommanderDeck(deck, validation);
        }

        return validation;
    }

    /**
     * Validate commander-specific rules
     */
    private static void validateCommanderDeck(Deck deck, DeckValidation validation) {
        // Check for exactly 100 cards
        if (deck.getMain().countAll() != COMMANDER_DECK_SIZE) {
            validation.addError("Commander decks must have exactly 100 cards");
        }

        // Check for commander
        CardPool commanders = deck.get(DeckSection.Commander);
        if (commanders == null || commanders.isEmpty()) {
            validation.addError("Commander deck must have a commander");
        }

        // Check singleton rule (except basic lands)
        Map<String, Integer> cardCounts = new HashMap<>();
        for (Map.Entry<PaperCard, Integer> card : deck.getMain()) {
            if (!card.getKey().getRules().getType().isBasicLand()) {
                String name = card.getKey().getName();
                cardCounts.put(name, cardCounts.getOrDefault(name, 0) + 1);
                if (cardCounts.get(name) > 1) {
                    validation.addError("Multiple copies of " + name + " (singleton format)");
                }
            }
        }
    }

    /**
     * Analyze deck composition
     */
    public static DeckAnalysis analyzeDeck(Deck deck) {
        DeckAnalysis analysis = new DeckAnalysis();
        CardPool main = deck.getMain();

        // Basic counts
        analysis.totalCards = main.countAll();

        // Count by type
        for (Map.Entry<PaperCard, Integer> card : main) {
            if (card.getKey().getRules().getType().isLand()) {
                analysis.landCount++;
            } else if (card.getKey().getRules().getType().isCreature()) {
                analysis.creatureCount++;
            } else if (card.getKey().getRules().getType().isInstant()) {
                analysis.instantCount++;
            } else if (card.getKey().getRules().getType().isSorcery()) {
                analysis.sorceryCount++;
            } else if (card.getKey().getRules().getType().isEnchantment()) {
                analysis.enchantmentCount++;
            } else if (card.getKey().getRules().getType().isArtifact()) {
                analysis.artifactCount++;
            } else if (card.getKey().getRules().getType().isPlaneswalker()) {
                analysis.planeswalkerCount++;
            }
        }

        // Mana curve
        for (Map.Entry<PaperCard, Integer> card : main) {
            if (!card.getKey().getRules().getType().isLand()) {
                int cmc = card.getKey().getRules().getManaCost().getCMC();
                analysis.manaCurve.put(cmc, analysis.manaCurve.getOrDefault(cmc, 0) + 1);
            }
        }

        // Color distribution
        analysis.colorDistribution = analyzeColorDistribution(main);

        // Average CMC
        int totalCmc = 0;
        int nonLandCount = 0;
        for (Map.Entry<PaperCard, Integer> card : main) {
            if (!card.getKey().getRules().getType().isLand()) {
                totalCmc += card.getKey().getRules().getManaCost().getCMC();
                nonLandCount++;
            }
        }
        analysis.averageCmc = nonLandCount > 0 ? (double) totalCmc / nonLandCount : 0.0;

        return analysis;
    }

    /**
     * Analyze color distribution in deck
     */
    private static Map<String, Integer> analyzeColorDistribution(CardPool deck) {
        Map<String, Integer> colors = new HashMap<>();
        colors.put("W", 0);
        colors.put("U", 0);
        colors.put("B", 0);
        colors.put("R", 0);
        colors.put("G", 0);
        colors.put("Colorless", 0);
        colors.put("Multicolor", 0);

        for (Map.Entry<PaperCard, Integer> card : deck) {
            ColorSet cardColors = card.getKey().getRules().getColor();

            if (cardColors.isColorless()) {
                colors.put("Colorless", colors.get("Colorless") + 1);
            } else if (cardColors.isMonoColor()) {
                if (cardColors.hasWhite()) colors.put("W", colors.get("W") + 1);
                else if (cardColors.hasBlue()) colors.put("U", colors.get("U") + 1);
                else if (cardColors.hasBlack()) colors.put("B", colors.get("B") + 1);
                else if (cardColors.hasRed()) colors.put("R", colors.get("R") + 1);
                else if (cardColors.hasGreen()) colors.put("G", colors.get("G") + 1);
            } else {
                colors.put("Multicolor", colors.get("Multicolor") + 1);
            }
        }

        return colors;
    }

    /**
     * Generate a random deck based on colors
     */
    public static Deck generateRandomDeck(String name, ColorSet colors, int size) {
        Deck deck = new Deck(name);

        // Calculate card distribution
        int landCount = (int) (size * DEFAULT_LAND_RATIO);
        int creatureCount = (int) (size * DEFAULT_CREATURE_RATIO);
        int spellCount = size - landCount - creatureCount;

        // Add lands
        addRandomLands(deck, colors, landCount);

        // Add creatures
        addRandomCreatures(deck, colors, creatureCount);

        // Add spells
        addRandomSpells(deck, colors, spellCount);

        return deck;
    }

    /**
     * Add random lands to deck
     */
    private static void addRandomLands(Deck deck, ColorSet colors, int count) {
        List<String> basicLands = new ArrayList<>();

        if (colors.hasWhite()) basicLands.add("Plains");
        if (colors.hasBlue()) basicLands.add("Island");
        if (colors.hasBlack()) basicLands.add("Swamp");
        if (colors.hasRed()) basicLands.add("Mountain");
        if (colors.hasGreen()) basicLands.add("Forest");

        if (basicLands.isEmpty()) {
            basicLands.add("Wastes");  // Colorless
        }

        // Distribute lands evenly among colors
        int landsPerColor = count / basicLands.size();
        int remainder = count % basicLands.size();

        for (int i = 0; i < basicLands.size(); i++) {
            int landsToAdd = landsPerColor + (i < remainder ? 1 : 0);
            addCardToDeck(deck, basicLands.get(i), landsToAdd);
        }
    }

    /**
     * Add random creatures to deck
     */
    private static void addRandomCreatures(Deck deck, ColorSet colors, int count) {
        // This would need access to a creature database
        // For now, using placeholder logic
        System.out.println("  ℹ️  Random creature generation not fully implemented");
    }

    /**
     * Add random spells to deck
     */
    private static void addRandomSpells(Deck deck, ColorSet colors, int count) {
        // This would need access to a spell database
        // For now, using placeholder logic
        System.out.println("  ℹ️  Random spell generation not fully implemented");
    }

    /**
     * Clone a deck with modifications
     */
    public static Deck cloneDeck(Deck original, String newName) {
        return new Deck(original, newName);
    }

    /**
     * Merge multiple decks into one
     */
    public static Deck mergeDecks(String name, Deck... decks) {
        Deck merged = new Deck(name);

        for (Deck deck : decks) {
            for (Map.Entry<PaperCard, Integer> card : deck.getMain()) {
                merged.getMain().add(card.getKey());
            }
        }

        return merged;
    }

    /**
     * Get card from StaticData
     */
    private static PaperCard getCard(String cardName) {
        if (StaticData.instance() != null && StaticData.instance().getCommonCards() != null) {
            return StaticData.instance().getCommonCards().getCard(cardName);
        }
        return null;
    }

    /**
     * Deck template for building decks
     */
    public static class DeckTemplate {
        private String name;
        private Map<String, Integer> lands = new LinkedHashMap<>();
        private Map<String, Integer> creatures = new LinkedHashMap<>();
        private Map<String, Integer> spells = new LinkedHashMap<>();
        private Map<String, Integer> sideboard = new LinkedHashMap<>();

        public DeckTemplate(String name) {
            this.name = name;
        }

        public DeckTemplate addLand(String card, int count) {
            lands.put(card, count);
            return this;
        }

        public DeckTemplate addCreature(String card, int count) {
            creatures.put(card, count);
            return this;
        }

        public DeckTemplate addSpell(String card, int count) {
            spells.put(card, count);
            return this;
        }

        public DeckTemplate addSideboard(String card, int count) {
            sideboard.put(card, count);
            return this;
        }

        // Getters
        public String getName() { return name; }
        public Map<String, Integer> getLands() { return lands; }
        public Map<String, Integer> getCreatures() { return creatures; }
        public Map<String, Integer> getSpells() { return spells; }
        public Map<String, Integer> getSideboard() { return sideboard; }
        public boolean hasSideboard() { return !sideboard.isEmpty(); }
    }

    /**
     * Deck validation result
     */
    public static class DeckValidation {
        private boolean valid = true;
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();

        public void addError(String error) {
            errors.add(error);
            valid = false;
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
    }

    /**
     * Deck analysis results
     */
    public static class DeckAnalysis {
        public int totalCards;
        public int landCount;
        public int creatureCount;
        public int instantCount;
        public int sorceryCount;
        public int enchantmentCount;
        public int artifactCount;
        public int planeswalkerCount;
        public double averageCmc;
        public Map<Integer, Integer> manaCurve = new TreeMap<>();
        public Map<String, Integer> colorDistribution;

        public void print() {
            System.out.println("\n📊 Deck Analysis:");
            System.out.println("  Total Cards: " + totalCards);
            System.out.println("  Lands: " + landCount + " (" + getPercentage(landCount) + "%)");
            System.out.println("  Creatures: " + creatureCount + " (" + getPercentage(creatureCount) + "%)");
            System.out.println("  Instants: " + instantCount);
            System.out.println("  Sorceries: " + sorceryCount);
            System.out.println("  Enchantments: " + enchantmentCount);
            System.out.println("  Artifacts: " + artifactCount);
            System.out.println("  Planeswalkers: " + planeswalkerCount);
            System.out.println("  Average CMC: " + String.format("%.2f", averageCmc));

            System.out.println("\n  Mana Curve:");
            for (Map.Entry<Integer, Integer> entry : manaCurve.entrySet()) {
                System.out.println("    " + entry.getKey() + " CMC: " + entry.getValue() + " cards");
            }

            System.out.println("\n  Color Distribution:");
            for (Map.Entry<String, Integer> entry : colorDistribution.entrySet()) {
                if (entry.getValue() > 0) {
                    System.out.println("    " + entry.getKey() + ": " + entry.getValue() + " cards");
                }
            }
        }

        private int getPercentage(int count) {
            return totalCards > 0 ? (count * 100) / totalCards : 0;
        }
    }

    /**
     * Deck format definitions
     */
    public enum DeckFormat {
        LIMITED("Limited", 40),
        STANDARD("Standard", 60),
        MODERN("Modern", 60),
        LEGACY("Legacy", 60),
        VINTAGE("Vintage", 60),
        COMMANDER("Commander", 100),
        BRAWL("Brawl", 60),
        PAUPER("Pauper", 60);

        private final String name;
        private final int minDeckSize;

        DeckFormat(String name, int minDeckSize) {
            this.name = name;
            this.minDeckSize = minDeckSize;
        }

        public String getName() { return name; }
        public int getMinDeckSize() { return minDeckSize; }

        public boolean isCardLegal(PaperCard card) {
            // Simplified - would need full format legality implementation
            return true;
        }

        public int getMaxCopies(String cardName) {
            if (this == COMMANDER || this == BRAWL) {
                return cardName.equals("Plains") || cardName.equals("Island") ||
                        cardName.equals("Swamp") || cardName.equals("Mountain") ||
                        cardName.equals("Forest") || cardName.equals("Wastes") ? 99 : 1;
            }
            return 4;  // Standard limit
        }
    }
}