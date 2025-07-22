package com.example.research;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import forge.StaticData;

import java.util.*;

/**
 * ForgeDeck implementation that builds tutorial decks using existing cards from forge-gui/res/cardsfolder
 */
public class ForgeDeck {

    private final boolean verboseLogging;

    // Define the cards needed for each deck
    private static final Map<String, Integer> CATS_DECK = new LinkedHashMap<>();
    private static final Map<String, Integer> VAMPIRES_DECK = new LinkedHashMap<>();

    static {
        // Cats deck - 20 cards total
        CATS_DECK.put("Plains", 7);
        CATS_DECK.put("Savannah Lions", 1);
        CATS_DECK.put("Leonin Skyhunter", 1);
        CATS_DECK.put("Prideful Parent", 1);
        CATS_DECK.put("Felidar Savior", 1);
        CATS_DECK.put("Angelic Edict", 1);
        CATS_DECK.put("Jazal Goldmane", 1);
        CATS_DECK.put("Pacifism", 1);
        CATS_DECK.put("Ingenious Leonin", 1);
        CATS_DECK.put("Helpful Hunter", 1);
        CATS_DECK.put("Leonin Vanguard", 1);
        CATS_DECK.put("Moment of Triumph", 1);
        CATS_DECK.put("Elspeth's Smite", 1);
        CATS_DECK.put("Uncharted Haven", 1);

        // Vampires deck - 20 cards total
        VAMPIRES_DECK.put("Swamp", 7);
        VAMPIRES_DECK.put("Vampire Interloper", 1);
        VAMPIRES_DECK.put("Vampire Spawn", 1);
        VAMPIRES_DECK.put("Moment of Craving", 1);
        VAMPIRES_DECK.put("Highborn Vampire", 1);
        VAMPIRES_DECK.put("Untamed Hunger", 1);
        VAMPIRES_DECK.put("Bloodtithe Collector", 1);
        VAMPIRES_DECK.put("Crossway Troublemakers", 1);
        VAMPIRES_DECK.put("Vengeful Bloodwitch", 1);
        VAMPIRES_DECK.put("Hero's Downfall", 1);
        VAMPIRES_DECK.put("Vampire Neonate", 1);
        VAMPIRES_DECK.put("Offer Immortality", 1);
        VAMPIRES_DECK.put("Stromkirk Bloodthief", 1);
        VAMPIRES_DECK.put("Uncharted Haven", 1);
    }

    public ForgeDeck() {
        this(true);
    }

    public ForgeDeck(boolean verbose) {
        this.verboseLogging = verbose;
    }

    /**
     * Create the Cats tutorial deck
     * @return Deck configured with cat-themed cards
     */
    public Deck createCatsDeck() {
        log("🐱 Creating Cats tutorial deck...");
        Deck deck = new Deck("Cats Tutorial Deck");

        // Add cards to main deck
        int totalCards = addCardsToDeck(deck, CATS_DECK, DeckSection.Main);

        log("✓ Cats deck created with " + totalCards + " cards");
        return deck;
    }

    /**
     * Create the Vampires tutorial deck
     * @return Deck configured with vampire-themed cards
     */
    public Deck createVampiresDeck() {
        log("🧛 Creating Vampires tutorial deck...");
        Deck deck = new Deck("Vampires Tutorial Deck");

        // Add cards to main deck
        int totalCards = addCardsToDeck(deck, VAMPIRES_DECK, DeckSection.Main);

        log("✓ Vampires deck created with " + totalCards + " cards");
        return deck;
    }

    /**
     * Add cards to a deck section
     * @param deck The deck to add cards to
     * @param cardList Map of card names to quantities
     * @param section The deck section (Main, Sideboard, etc.)
     * @return Total number of cards added
     */
    private int addCardsToDeck(Deck deck, Map<String, Integer> cardList, DeckSection section) {
        int totalAdded = 0;

        for (Map.Entry<String, Integer> entry : cardList.entrySet()) {
            String cardName = entry.getKey();
            int quantity = entry.getValue();

            int added = addCardToDeck(deck, cardName, quantity, section);
            totalAdded += added;
        }

        return totalAdded;
    }

    /**
     * Add a specific card to the deck
     * @param deck The deck to add the card to
     * @param cardName The name of the card
     * @param count How many copies to add
     * @param section Which section of the deck to add to
     * @return Number of cards actually added
     */
    private int addCardToDeck(Deck deck, String cardName, int count, DeckSection section) {
        try {
            if (StaticData.instance() != null && StaticData.instance().getCommonCards() != null) {
                PaperCard card = StaticData.instance().getCommonCards().getCard(cardName);
                if (card != null) {
                    for (int i = 0; i < count; i++) {
                        deck.get(section).add(card);
                    }
                    log("  ✓ Added " + count + "x " + cardName);
                    return count;
                } else {
                    log("  ❌ Could not find card: " + cardName);
                }
            } else {
                log("  ❌ StaticData not available");
            }
        } catch (Exception e) {
            log("  ❌ Error adding " + cardName + ": " + e.getMessage());
        }
        return 0;
    }

    /**
     * Validate that all required cards exist in the Forge card database
     * @return true if all cards are available
     */
    public boolean validateCardAvailability() {
        log("🔍 Validating card availability...");

        Set<String> allRequiredCards = new HashSet<>();
        allRequiredCards.addAll(CATS_DECK.keySet());
        allRequiredCards.addAll(VAMPIRES_DECK.keySet());

        List<String> missingCards = new ArrayList<>();

        if (StaticData.instance() != null && StaticData.instance().getCommonCards() != null) {
            for (String cardName : allRequiredCards) {
                PaperCard card = StaticData.instance().getCommonCards().getCard(cardName);
                if (card == null) {
                    missingCards.add(cardName);
                }
            }
        } else {
            log("❌ StaticData not initialized - cannot validate cards");
            return false;
        }

        if (missingCards.isEmpty()) {
            log("✅ All " + allRequiredCards.size() + " required cards are available!");
            return true;
        } else {
            log("❌ Missing " + missingCards.size() + " cards:");
            for (String missing : missingCards) {
                log("  • " + missing);
            }
            return false;
        }
    }

    /**
     * Get a list of all cards used in the tutorial decks
     * @return Set of card names
     */
    public Set<String> getAllTutorialCards() {
        Set<String> allCards = new HashSet<>();
        allCards.addAll(CATS_DECK.keySet());
        allCards.addAll(VAMPIRES_DECK.keySet());
        return allCards;
    }

    /**
     * Print deck contents for debugging
     * @param deck The deck to print
     */
    public void printDeckContents(Deck deck) {
        System.out.println("\n📋 Deck: " + deck.getName());
        System.out.println("Total cards: " + deck.getMain().countAll());

        Map<String, Integer> cardCounts = new TreeMap<>();
        for (Map.Entry<PaperCard, Integer> entry : deck.getMain()) {
            cardCounts.put(entry.getKey().getName(), entry.getValue());
        }

        for (Map.Entry<String, Integer> entry : cardCounts.entrySet()) {
            System.out.println("  " + entry.getValue() + "x " + entry.getKey());
        }
    }

    /**
     * Get deck statistics
     * @param deck The deck to analyze
     * @return Map of statistics
     */
    public Map<String, Object> getDeckStats(Deck deck) {
        Map<String, Object> stats = new HashMap<>();

        int totalCards = deck.getMain().countAll();
        int uniqueCards = deck.getMain().toFlatList().size();

        // Count card types
        int creatures = 0;
        int lands = 0;
        int spells = 0;

        for (PaperCard card : deck.getMain().toFlatList()) {
            if (card.getRules().getType().isCreature()) {
                creatures++;
            } else if (card.getRules().getType().isLand()) {
                lands++;
            } else {
                spells++;
            }
        }

        stats.put("totalCards", totalCards);
        stats.put("uniqueCards", uniqueCards);
        stats.put("creatures", creatures);
        stats.put("lands", lands);
        stats.put("spells", spells);

        return stats;
    }

    private void log(String message) {
        if (verboseLogging) {
            System.out.println(message);
        }
    }
}