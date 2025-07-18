package com.example.research.deck;

import com.example.research.mtg_commons;
import forge.deck.*;
import forge.deck.io.*;
import forge.item.PaperCard;
import forge.properties.ForgeConstants;
import forge.util.storage.IStorage;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DeckManager - Manages deck storage, loading, and organization
 *
 * Provides functionality for:
 * - Loading decks from files
 * - Saving decks to storage
 * - Managing deck collections
 * - Creating tutorial/precon decks
 * - Import/export operations
 */
public class DeckManager {

    // Deck storage paths
    private final String userDecksPath;
    private final String preconDecksPath;
    private final String tutorialDecksPath;

    // Deck collections
    private Map<String, Deck> loadedDecks;
    private Map<String, DeckTemplate> tutorialTemplates;
    private Map<String, DeckTemplate> preconTemplates;

    // Deck storage
    private IStorage<Deck> deckStorage;

    /**
     * Default constructor
     */
    public DeckManager() {
        this(mtg_commons.DEFAULT_DECK_PATH);
    }

    /**
     * Constructor with custom deck path
     */
    public DeckManager(String basePath) {
        this.userDecksPath = basePath + "/user";
        this.preconDecksPath = basePath + "/precon";
        this.tutorialDecksPath = basePath + "/tutorial";

        this.loadedDecks = new HashMap<>();
        this.tutorialTemplates = new HashMap<>();
        this.preconTemplates = new HashMap<>();

        initializeDirectories();
        initializeTutorialTemplates();
    }

    /**
     * Initialize deck directories
     */
    private void initializeDirectories() {
        try {
            Files.createDirectories(Paths.get(userDecksPath));
            Files.createDirectories(Paths.get(preconDecksPath));
            Files.createDirectories(Paths.get(tutorialDecksPath));
        } catch (IOException e) {
            System.err.println("❌ Failed to create deck directories: " + e.getMessage());
        }
    }

    /**
     * Initialize tutorial deck templates
     */
    private void initializeTutorialTemplates() {
        // Cats Tutorial Deck
        DeckTemplate catsDeck = (DeckTemplate) new DeckTemplate("Cats Tutorial Deck")
                // Lands (8 total)
                .addLand("Plains", 7)
                .addLand("Uncharted Haven", 1)
                // Creatures (9 total)
                .addCreature("Savannah Lions", 1)
                .addCreature("Leonin Skyhunter", 1)
                .addCreature("Prideful Parent", 1)
                .addCreature("Felidar Savior", 1)
                .addCreature("Jazal Goldmane", 1)
                .addCreature("Ingenious Leonin", 1)
                .addCreature("Helpful Hunter", 1)
                .addCreature("Leonin Vanguard", 1)
                .addCreature("Elspeth's Smite", 1)
                // Spells (3 total)
                .addSpell("Angelic Edict", 1)
                .addSpell("Pacifism", 1)
                .addSpell("Moment of Triumph", 1);

        tutorialTemplates.put("Cats", catsDeck);

        // Vampires Tutorial Deck
        DeckTemplate vampiresDeck = (DeckTemplate) new DeckTemplate("Vampires Tutorial Deck")
                // Lands (8 total)
                .addLand("Swamp", 7)
                .addLand("Uncharted Haven", 1)
                // Creatures (10 total)
                .addCreature("Vampire Interloper", 1)
                .addCreature("Vampire Spawn", 1)
                .addCreature("Highborn Vampire", 1)
                .addCreature("Bloodtithe Collector", 1)
                .addCreature("Crossway Troublemakers", 1)
                .addCreature("Vengeful Bloodwitch", 1)
                .addCreature("Vampire Neonate", 1)
                .addCreature("Stromkirk Bloodthief", 1)
                .addCreature("Offer Immortality", 1)
                .addCreature("Untamed Hunger", 1)
                // Spells (2 total)
                .addSpell("Moment of Craving", 1)
                .addSpell("Hero's Downfall", 1);

        tutorialTemplates.put("Vampires", vampiresDeck);
    }

    /**
     * Load a deck from storage
     */
    public Deck loadDeck(String deckName) {
        // Check if already loaded
        if (loadedDecks.containsKey(deckName)) {
            return loadedDecks.get(deckName);
        }

        // Try to load from different locations
        Deck deck = null;

        // Try user decks first
        deck = loadDeckFromPath(userDecksPath, deckName);
        if (deck != null) {
            loadedDecks.put(deckName, deck);
            return deck;
        }

        // Try precon decks
        deck = loadDeckFromPath(preconDecksPath, deckName);
        if (deck != null) {
            loadedDecks.put(deckName, deck);
            return deck;
        }

        // Try tutorial decks
        deck = loadDeckFromPath(tutorialDecksPath, deckName);
        if (deck != null) {
            loadedDecks.put(deckName, deck);
            return deck;
        }

        System.out.println("⚠️  Deck not found: " + deckName);
        return null;
    }

    /**
     * Load deck from specific path
     */
    private Deck loadDeckFromPath(String path, String deckName) {
        File deckFile = new File(path, deckName + ".dck");
        if (!deckFile.exists()) {
            return null;
        }

        try {
            return DeckSerializer.fromFile(deckFile);
        } catch (Exception e) {
            System.err.println("❌ Error loading deck from " + deckFile + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Save a deck to storage
     */
    public boolean saveDeck(Deck deck) {
        return saveDeck(deck, userDecksPath);
    }

    /**
     * Save a deck to specific path
     */
    public boolean saveDeck(Deck deck, String path) {
        try {
            File deckFile = new File(path, deck.getName() + ".dck");
            DeckSerializer.writeDeck(deck, deckFile);
            loadedDecks.put(deck.getName(), deck);
            System.out.println("✅ Saved deck: " + deck.getName());
            return true;
        } catch (Exception e) {
            System.err.println("❌ Error saving deck: " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete a deck
     */
    public boolean deleteDeck(String deckName) {
        // Remove from loaded decks
        loadedDecks.remove(deckName);

        // Try to delete from all locations
        boolean deleted = false;
        deleted |= deleteDeckFromPath(userDecksPath, deckName);
        deleted |= deleteDeckFromPath(preconDecksPath, deckName);
        deleted |= deleteDeckFromPath(tutorialDecksPath, deckName);

        return deleted;
    }

    /**
     * Delete deck from specific path
     */
    private boolean deleteDeckFromPath(String path, String deckName) {
        File deckFile = new File(path, deckName + ".dck");
        if (deckFile.exists()) {
            return deckFile.delete();
        }
        return false;
    }

    /**
     * List all available decks
     */
    public List<DeckInfo> listAllDecks() {
        List<DeckInfo> allDecks = new ArrayList<>();

        // List user decks
        allDecks.addAll(listDecksFromPath(userDecksPath, DeckType.USER));

        // List precon decks
        allDecks.addAll(listDecksFromPath(preconDecksPath, DeckType.PRECON));

        // List tutorial decks
        allDecks.addAll(listDecksFromPath(tutorialDecksPath, DeckType.TUTORIAL));

        return allDecks;
    }

    /**
     * List decks from specific path
     */
    private List<DeckInfo> listDecksFromPath(String path, DeckType type) {
        List<DeckInfo> decks = new ArrayList<>();
        File dir = new File(path);

        if (!dir.exists() || !dir.isDirectory()) {
            return decks;
        }

        File[] files = dir.listFiles((f) -> f.getName().endsWith(".dck"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName().replace(".dck", "");
                decks.add(new DeckInfo(name, type, file.getAbsolutePath()));
            }
        }

        return decks;
    }

    /**
     * Create a tutorial deck
     */
    public Deck createTutorialDeck(String templateName) {
        DeckTemplate template = tutorialTemplates.get(templateName);
        if (template == null) {
            System.err.println("❌ Tutorial template not found: " + templateName);
            return null;
        }

        Deck deck = DeckBuilder.buildFromTemplate(template);
        saveDeck(deck, tutorialDecksPath);
        return deck;
    }

    /**
     * Import deck from external format
     */
    public Deck importDeck(String deckData, DeckImportFormat format) {
        switch (format) {
            case FORGE:
                return importForgeDeck(deckData);
            case MTGO:
                return importMTGODeck(deckData);
            case ARENA:
                return importArenaDeck(deckData);
            default:
                System.err.println("❌ Unsupported import format: " + format);
                return null;
        }
    }

    /**
     * Import Forge format deck
     */
    private Deck importForgeDeck(String deckData) {
        try {
            return DeckSerializer.fromString(deckData);
        } catch (Exception e) {
            System.err.println("❌ Error importing Forge deck: " + e.getMessage());
            return null;
        }
    }

    /**
     * Import MTGO format deck
     */
    private Deck importMTGODeck(String deckData) {
        Deck deck = new Deck("Imported MTGO Deck");
        String[] lines = deckData.split("\n");
        boolean inSideboard = false;

        for (String line : lines) {
            line = line.trim();

            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }

            if (line.toLowerCase().contains("sideboard")) {
                inSideboard = true;
                continue;
            }

            // Parse "4 Lightning Bolt" format
            String[] parts = line.split(" ", 2);
            if (parts.length == 2) {
                try {
                    int count = Integer.parseInt(parts[0]);
                    String cardName = parts[1];

                    DeckSection section = inSideboard ? DeckSection.Sideboard : DeckSection.Main;
                    DeckBuilder.addCardToDeck(deck, cardName, count, section);
                } catch (NumberFormatException e) {
                    System.err.println("⚠️  Skipping invalid line: " + line);
                }
            }
        }

        return deck;
    }

    /**
     * Import Arena format deck
     */
    private Deck importArenaDeck(String deckData) {
        Deck deck = new Deck("Imported Arena Deck");
        String[] lines = deckData.split("\n");
        boolean inSideboard = false;

        for (String line : lines) {
            line = line.trim();

            if (line.isEmpty()) {
                inSideboard = true;  // Arena uses blank line to separate sideboard
                continue;
            }

            // Parse "4 Lightning Bolt (SET) 123" format
            String[] parts = line.split(" ");
            if (parts.length >= 2) {
                try {
                    int count = Integer.parseInt(parts[0]);

                    // Find card name (everything before set code in parentheses)
                    StringBuilder cardName = new StringBuilder();
                    for (int i = 1; i < parts.length; i++) {
                        if (parts[i].startsWith("(")) {
                            break;
                        }
                        if (cardName.length() > 0) {
                            cardName.append(" ");
                        }
                        cardName.append(parts[i]);
                    }

                    DeckSection section = inSideboard ? DeckSection.Sideboard : DeckSection.Main;
                    DeckBuilder.addCardToDeck(deck, cardName.toString(), count, section);
                } catch (NumberFormatException e) {
                    System.err.println("⚠️  Skipping invalid line: " + line);
                }
            }
        }

        return deck;
    }

    /**
     * Export deck to format
     */
    public String exportDeck(Deck deck, DeckExportFormat format) {
        switch (format) {
            case FORGE:
                return exportForgeDeck(deck);
            case MTGO:
                return exportMTGODeck(deck);
            case ARENA:
                return exportArenaDeck(deck);
            case TEXT:
                return exportTextDeck(deck);
            default:
                return "";
        }
    }

    /**
     * Export to Forge format
     */
    private String exportForgeDeck(Deck deck) {
        return DeckSerializer.serializeDeck(deck);
    }

    /**
     * Export to MTGO format
     */
    private String exportMTGODeck(Deck deck) {
        StringBuilder sb = new StringBuilder();

        // Main deck
        Map<String, Integer> mainCards = new HashMap<>();
        for (Map.Entry<PaperCard, Integer> card : deck.getMain()) {
            mainCards.put(card.getKey().getName(), mainCards.getOrDefault(card.getKey().getName(), 0) + 1);
        }

        for (Map.Entry<String, Integer> entry : mainCards.entrySet()) {
            sb.append(entry.getValue()).append(" ").append(entry.getKey()).append("\n");
        }

        // Sideboard
        if (deck.has(DeckSection.Sideboard)) {
            sb.append("\nSideboard\n");
            Map<String, Integer> sideCards = new HashMap<>();
            for (Map.Entry<PaperCard, Integer> card : deck.get(DeckSection.Sideboard)) {
                sideCards.put(card.getKey().getName(), sideCards.getOrDefault(card.getKey().getName(), 0) + 1);
            }

            for (Map.Entry<String, Integer> entry : sideCards.entrySet()) {
                sb.append(entry.getValue()).append(" ").append(entry.getKey()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Export to Arena format
     */
    private String exportArenaDeck(Deck deck) {
        StringBuilder sb = new StringBuilder();

        // Main deck
        Map<String, Integer> mainCards = new HashMap<>();
        for (Map.Entry<PaperCard, Integer> card : deck.getMain()) {
            mainCards.put(card.getKey().getName(), mainCards.getOrDefault(card.getKey().getName(), 0) + 1);
        }

        for (Map.Entry<String, Integer> entry : mainCards.entrySet()) {
            sb.append(entry.getValue()).append(" ").append(entry.getKey()).append("\n");
        }

        // Sideboard (separated by blank line)
        if (deck.has(DeckSection.Sideboard)) {
            sb.append("\n");
            Map<String, Integer> sideCards = new HashMap<>();
            for (Map.Entry<PaperCard, Integer> card : deck.get(DeckSection.Sideboard)) {
                sideCards.put(card.getKey().getName(), sideCards.getOrDefault(card.getKey().getName(), 0) + 1);
            }

            for (Map.Entry<String, Integer> entry : sideCards.entrySet()) {
                sb.append(entry.getValue()).append(" ").append(entry.getKey()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Export to readable text format
     */
    private String exportTextDeck(Deck deck) {
        StringBuilder sb = new StringBuilder();
        sb.append("Deck: ").append(deck.getName()).append("\n");
        sb.append("=" .repeat(50)).append("\n\n");

        // Group cards by type
        Map<String, List<PaperCard>> cardsByType = new HashMap<>();
        cardsByType.put("Lands", new ArrayList<>());
        cardsByType.put("Creatures", new ArrayList<>());
        cardsByType.put("Instants", new ArrayList<>());
        cardsByType.put("Sorceries", new ArrayList<>());
        cardsByType.put("Enchantments", new ArrayList<>());
        cardsByType.put("Artifacts", new ArrayList<>());
        cardsByType.put("Planeswalkers", new ArrayList<>());
        cardsByType.put("Other", new ArrayList<>());

        // Sort cards into categories
        for (Map.Entry<PaperCard, Integer> card : deck.getMain()) {
            if (card.getKey().getRules().getType().isLand()) {
                cardsByType.get("Lands").add((PaperCard) card);
            } else if (card.getKey().getRules().getType().isCreature()) {
                cardsByType.get("Creatures").add((PaperCard) card);
            } else if (card.getKey().getRules().getType().isInstant()) {
                cardsByType.get("Instants").add((PaperCard) card);
            } else if (card.getKey().getRules().getType().isSorcery()) {
                cardsByType.get("Sorceries").add((PaperCard) card);
            } else if (card.getKey().getRules().getType().isEnchantment()) {
                cardsByType.get("Enchantments").add((PaperCard) card);
            } else if (card.getKey().getRules().getType().isArtifact()) {
                cardsByType.get("Artifacts").add((PaperCard) card);
            } else if (card.getKey().getRules().getType().isPlaneswalker()) {
                cardsByType.get("Planeswalkers").add((PaperCard) card);
            } else {
                cardsByType.get("Other").add((PaperCard) card);
            }
        }

        // Print each category
        for (String category : Arrays.asList("Lands", "Creatures", "Instants", "Sorceries",
                "Enchantments", "Artifacts", "Planeswalkers", "Other")) {
            List<PaperCard> cards = cardsByType.get(category);
            if (!cards.isEmpty()) {
                sb.append(category).append(" (").append(cards.size()).append("):\n");

                // Count duplicates
                Map<String, Integer> cardCounts = new HashMap<>();
                for (PaperCard card : cards) {
                    cardCounts.put(card.getName(), cardCounts.getOrDefault(card.getName(), 0) + 1);
                }

                // Print sorted
                List<String> sortedNames = new ArrayList<>(cardCounts.keySet());
                Collections.sort(sortedNames);

                for (String name : sortedNames) {
                    int count = cardCounts.get(name);
                    sb.append("  ").append(count).append("x ").append(name).append("\n");
                }
                sb.append("\n");
            }
        }

        // Sideboard
        if (deck.has(DeckSection.Sideboard)) {
            sb.append("Sideboard (").append(deck.get(DeckSection.Sideboard).countAll()).append("):\n");
            Map<String, Integer> sideCards = new HashMap<>();
            for (Map.Entry<PaperCard, Integer> card : deck.get(DeckSection.Sideboard)) {
                sideCards.put(card.getKey().getName(), sideCards.getOrDefault(card.getKey().getName(), 0) + 1);
            }

            List<String> sortedSide = new ArrayList<>(sideCards.keySet());
            Collections.sort(sortedSide);

            for (String name : sortedSide) {
                int count = sideCards.get(name);
                sb.append("  ").append(count).append("x ").append(name).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Validate all loaded decks
     */
    public Map<String, DeckBuilder.DeckValidation> validateAllDecks(DeckBuilder.DeckFormat format) {
        Map<String, DeckBuilder.DeckValidation> results = new HashMap<>();

        for (Map.Entry<String, Deck> entry : loadedDecks.entrySet()) {
            results.put(entry.getKey(), DeckBuilder.validateDeck(entry.getValue(), format));
        }

        return results;
    }

    /**
     * Get statistics for all decks
     */
    public void printDeckStatistics() {
        System.out.println("\n📊 Deck Collection Statistics:");
        System.out.println("  Total Decks Loaded: " + loadedDecks.size());

        // Count by format
        int limited = 0, standard = 0, commander = 0, other = 0;

        for (Deck deck : loadedDecks.values()) {
            int size = deck.getMain().countAll();
            if (size == 40) limited++;
            else if (size == 60) standard++;
            else if (size == 100) commander++;
            else other++;
        }

        System.out.println("  Limited (40): " + limited);
        System.out.println("  Standard (60): " + standard);
        System.out.println("  Commander (100): " + commander);
        System.out.println("  Other: " + other);
    }

    // Getters
    public Map<String, Deck> getLoadedDecks() { return loadedDecks; }
    public Map<String, DeckTemplate> getTutorialTemplates() { return tutorialTemplates; }

    /**
     * Deck information
     */
    public static class DeckInfo {
        public final String name;
        public final DeckType type;
        public final String path;

        public DeckInfo(String name, DeckType type, String path) {
            this.name = name;
            this.type = type;
            this.path = path;
        }
    }

    /**
     * Deck type enumeration
     */
    public enum DeckType {
        USER("User Deck"),
        PRECON("Preconstructed"),
        TUTORIAL("Tutorial"),
        DRAFT("Draft Deck"),
        SEALED("Sealed Deck");

        private final String displayName;

        DeckType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    /**
     * Import format enumeration
     */
    public enum DeckImportFormat {
        FORGE, MTGO, ARENA, TXT
    }

    /**
     * Export format enumeration
     */
    public enum DeckExportFormat {
        FORGE, MTGO, ARENA, TEXT
    }

    /**
     * Deck template for building decks
     */
    public static class DeckTemplate extends DeckBuilder.DeckTemplate {
        public DeckTemplate(String name) {
            super(name);
        }
    }
}