package APF;

import forge.game.card.Card;
import forge.game.card.CounterType;
import forge.game.keyword.KeywordInterface;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;


public final class ForgeBuilders {
    private ForgeBuilders() {
    }

    /**
     * CardDefinitionBuilder reads card definitions from Forge's cardsfolder
     * and creates NounDefinitions for each unique card.
     */
    public static final class CardDefinitionBuilder {
        private static final Logger LOGGER = Logger.getLogger(CardDefinitionBuilder.class.getName());

        private final Path cardsfolderPath;
        private final Map<String, NounDefinition> cardDefinitions;
        private final KnowledgeBase knowledgeBase;

        public CardDefinitionBuilder(String cardsfolderPath) {
            this.cardsfolderPath = Paths.get(cardsfolderPath);
            this.cardDefinitions = new HashMap<>();
            this.knowledgeBase = KnowledgeBase.getInstance();
        }

        /**
         * Build definitions for all cards in the cardsfolder
         */
        public void buildAllCardDefinitions() {
            try {
                // Process all .txt files in the directory
                Files.walk(cardsfolderPath, 1)
                        .filter(path -> path.toString().endsWith(".txt"))
                        .forEach(this::processCardFile);

                // Process .zip file if present
                //Files.walk(cardsfolderPath, 1)
                //        .filter(path -> path.toString().endsWith(".zip"))
                //        .forEach(this::processZipFile);

                LOGGER.info("Successfully built " + cardDefinitions.size() + " card definitions");
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error reading cardsfolder", e);
            }
        }

        /**
         * Process a single card text file
         */
        private void processCardFile(Path filePath) {
            try {
                List<String> lines = Files.readAllLines(filePath);
                Map<String, String> cardData = parseCardFile(lines);

                if (cardData.containsKey("Name")) {
                    NounDefinition cardDef = createCardDefinition(cardData);
                    String cardName = cardData.get("Name");
                    cardDefinitions.put(cardName, cardDef);
                    knowledgeBase.registerDefinition(cardDef);
                    LOGGER.fine("Created definition for card: " + cardName);
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error processing card file: " + filePath, e);
            }
        }

        /**
         * Process cards from a zip file
         */
        private void processZipFile(Path zipPath) {
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().endsWith(".txt")) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                        List<String> lines = reader.lines().collect(Collectors.toList());
                        Map<String, String> cardData = parseCardFile(lines);

                        if (cardData.containsKey("Name")) {
                            NounDefinition cardDef = createCardDefinition(cardData);
                            String cardName = cardData.get("Name");
                            cardDefinitions.put(cardName, cardDef);
                            knowledgeBase.registerDefinition(cardDef);
                            LOGGER.fine("Created definition for card from zip: " + cardName);
                        }
                    }
                    zis.closeEntry();
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error processing zip file: " + zipPath, e);
            }
        }

        /**
         * Parse a card file into a map of properties
         */
        private Map<String, String> parseCardFile(List<String> lines) {
            Map<String, String> cardData = new HashMap<>();
            String currentKey = null;
            StringBuilder currentValue = new StringBuilder();

            for (String line : lines) {
                if (line.isEmpty()) continue;

                // Check if this line starts a new property
                if (line.contains(":")) {
                    // Save previous property if exists
                    if (currentKey != null) {
                        cardData.put(currentKey, currentValue.toString().trim());
                    }

                    // Parse new property
                    int colonIndex = line.indexOf(':');
                    currentKey = line.substring(0, colonIndex);
                    currentValue = new StringBuilder(line.substring(colonIndex + 1));
                } else if (currentKey != null) {
                    // Continue multi-line value
                    currentValue.append(" ").append(line);
                }
            }

            // Don't forget the last property
            if (currentKey != null) {
                cardData.put(currentKey, currentValue.toString().trim());
            }

            return cardData;
        }

        /**
         * Create a NounDefinition for a card based on parsed data
         */
        private NounDefinition createCardDefinition(Map<String, String> cardData) {
            String cardName = cardData.get("Name");
            String typesLine = cardData.getOrDefault("Types", "");

            NounDefinition.Builder builder = new NounDefinition.Builder("Card_" + sanitizeName(cardName))
                    .description("Magic: The Gathering card - " + cardName);

            // Core properties every card has
            builder.addStringProperty("name", cardName, 1, 200)
                    .addRequiredProperty("name");

            // Mana Cost - Convert to standard format
            if (cardData.containsKey("ManaCost")) {
                String manaCost = cardData.get("ManaCost");
                String formattedManaCost = formatManaCost(manaCost);
                builder.addStringProperty("manaCost", formattedManaCost, 0, 50);
                builder.addIntProperty("convertedManaCost", calculateCMC(manaCost), 0, 100);
            } else {
                builder.addStringProperty("manaCost", "", 0, 50);
                builder.addIntProperty("convertedManaCost", 0, 0, 100);
            }

            // Parse Types and Subtypes separately
            ParsedTypes parsed = parseTypesAndSubtypes(typesLine);

            // Store types as list
            builder.addProperty("types", parsed.types);
            builder.addProperty("subtypes", parsed.subtypes);

            // Full types string for backward compatibility
            builder.addStringProperty("typesLine", typesLine, 0, 200)
                    .addRequiredProperty("typesLine");

            // Card type booleans - check against parsed types
            builder.addBooleanProperty("isCreature", parsed.types.contains("Creature"))
                    .addBooleanProperty("isArtifact", parsed.types.contains("Artifact"))
                    .addBooleanProperty("isEnchantment", parsed.types.contains("Enchantment"))
                    .addBooleanProperty("isInstant", parsed.types.contains("Instant"))
                    .addBooleanProperty("isSorcery", parsed.types.contains("Sorcery"))
                    .addBooleanProperty("isPlaneswalker", parsed.types.contains("Planeswalker"))
                    .addBooleanProperty("isLand", parsed.types.contains("Land"))
                    .addBooleanProperty("isBattle", parsed.types.contains("Battle"))
                    .addBooleanProperty("isLegendary", typesLine.contains("Legendary"))
                    .addBooleanProperty("isTribal", parsed.types.contains("Tribal"));

            // Creature-specific properties
            if (parsed.types.contains("Creature") && cardData.containsKey("PT")) {
                String pt = cardData.get("PT");
                String[] parts = pt.split("/");
                if (parts.length == 2) {
                    builder.addStringProperty("power", parts[0].trim(), 0, 10)
                            .addStringProperty("toughness", parts[1].trim(), 0, 10);
                }
            }

            // Keywords - now properly extracts from K: lines
            List<String> keywords = extractKeywords(cardData);
            builder.addProperty("keywords", keywords);

            // Abilities - from A:, T:, S: lines
            List<String> abilities = extractAbilities(cardData);
            builder.addProperty("abilities", abilities);

            // Oracle text
            String oracleText = cardData.getOrDefault("Oracle", "");
            builder.addStringProperty("oracleText", oracleText, 0, 5000);

            // Deck hints and AI properties
            if (cardData.containsKey("DeckHints")) {
                builder.addStringProperty("deckHints", cardData.get("DeckHints"), 0, 500);
            }

            if (cardData.containsKey("DeckHas")) {
                builder.addStringProperty("deckHas", cardData.get("DeckHas"), 0, 500);
            }

            // Rarity (if available)
            if (cardData.containsKey("Rarity")) {
                builder.addStringProperty("rarity", cardData.get("Rarity"), 0, 20);
            }

            // Color identity
            builder.addProperty("colorIdentity", calculateColorIdentity(cardData));

            // Set/Collection info if available
            if (cardData.containsKey("SetInfo")) {
                builder.addStringProperty("setInfo", cardData.get("SetInfo"), 0, 200);
            }

            return builder.build();
        }

        /**
         * Helper class to hold parsed types and subtypes
         */
        private static class ParsedTypes {
            List<String> types;
            List<String> subtypes;

            ParsedTypes(List<String> types, List<String> subtypes) {
                this.types = types;
                this.subtypes = subtypes;
            }
        }

        /**
         * Parse types and subtypes from the Types line
         * Examples:
         * "Creature Vampire Scout" -> types: ["Creature"], subtypes: ["Vampire", "Scout"]
         * "Legendary Creature Angel" -> types: ["Creature"], subtypes: ["Angel"]
         * "Instant" -> types: ["Instant"], subtypes: []
         * "Basic Land Mountain" -> types: ["Land"], subtypes: ["Mountain"]
         */
        private ParsedTypes parseTypesAndSubtypes(String typesLine) {
            List<String> types = new ArrayList<>();
            List<String> subtypes = new ArrayList<>();

            if (typesLine == null || typesLine.isEmpty()) {
                return new ParsedTypes(types, subtypes);
            }

            String[] parts = typesLine.split("\\s+");

            // Known card types (main types)
            Set<String> knownTypes = new HashSet<>(Arrays.asList(
                    "Creature", "Artifact", "Enchantment", "Instant", "Sorcery",
                    "Planeswalker", "Land", "Battle", "Tribal", "Conspiracy",
                    "Phenomenon", "Plane", "Scheme", "Vanguard"
            ));

            // Known supertypes (modifiers that come before main types)
            Set<String> supertypes = new HashSet<>(Arrays.asList(
                    "Basic", "Legendary", "Ongoing", "Snow", "World"
            ));

            boolean foundMainType = false;

            for (String part : parts) {
                if (supertypes.contains(part)) {
                    // Skip supertypes for now (could be stored separately if needed)
                    continue;
                } else if (knownTypes.contains(part)) {
                    types.add(part);
                    foundMainType = true;
                } else if (foundMainType) {
                    // Everything after the main type(s) is a subtype
                    subtypes.add(part);
                }
            }

            return new ParsedTypes(types, subtypes);
        }

        /**
         * Format mana cost from Forge format to standard MTG format
         * Examples:
         * "1B" -> "{1}{B}"
         * "2WW" -> "{2}{W}{W}"
         * "WUBRG" -> "{W}{U}{B}{R}{G}"
         */
        private String formatManaCost(String manaCost) {
            if (manaCost == null || manaCost.isEmpty()) {
                return "";
            }

            StringBuilder formatted = new StringBuilder();
            StringBuilder numBuilder = new StringBuilder();

            for (int i = 0; i < manaCost.length(); i++) {
                char c = manaCost.charAt(i);

                if (Character.isDigit(c)) {
                    numBuilder.append(c);
                } else {
                    // Add any accumulated number
                    if (numBuilder.length() > 0) {
                        formatted.append("{").append(numBuilder).append("}");
                        numBuilder = new StringBuilder();
                    }

                    // Add the mana symbol
                    if (c == 'W' || c == 'U' || c == 'B' || c == 'R' || c == 'G' ||
                            c == 'C' || c == 'X' || c == 'Y' || c == 'Z') {
                        formatted.append("{").append(c).append("}");
                    } else if (c == 'P') {
                        // Phyrexian mana - needs special handling
                        // Usually comes after another symbol like "BP" for Phyrexian black
                        if (i > 0) {
                            // Remove the last added symbol and re-add with P
                            String last = formatted.substring(formatted.length() - 3);
                            formatted.setLength(formatted.length() - 3);
                            formatted.append(last.substring(0, 2)).append("/P}");
                        }
                    } else if (c == '/') {
                        // Hybrid mana handling
                        // Format like "W/U" should become "{W/U}"
                        if (i > 0 && i < manaCost.length() - 1) {
                            char prev = manaCost.charAt(i - 1);
                            char next = manaCost.charAt(i + 1);
                            // Remove the last symbol if it was added
                            if (formatted.length() >= 3) {
                                formatted.setLength(formatted.length() - 3);
                            }
                            formatted.append("{").append(prev).append("/").append(next).append("}");
                            i++; // Skip the next character since we've processed it
                        }
                    }
                }
            }

            // Add any remaining number
            if (numBuilder.length() > 0) {
                formatted.append("{").append(numBuilder).append("}");
            }

            return formatted.toString();
        }

        /**
         * Fixed extractKeywords to properly handle multiple K: lines
         */
        private List<String> extractKeywords(Map<String, String> cardData) {
            List<String> keywords = new ArrayList<>();

            // In the parseCardFile method, multiple K: lines might be combined
            // or stored separately. Let's handle both cases.
            for (Map.Entry<String, String> entry : cardData.entrySet()) {
                if (entry.getKey().equals("K") || entry.getKey().startsWith("K:")) {
                    String keywordLine = entry.getValue();
                    // Handle keywords with parameters (e.g., "Suspend:2:1 R R")
                    if (keywordLine.contains(":")) {
                        // Just take the first part as the keyword name
                        String[] parts = keywordLine.split(":");
                        keywords.add(parts[0]);
                    } else {
                        keywords.add(keywordLine);
                    }
                }
            }

            return keywords;
        }

        /**
         * Extract abilities from card data
         */
        private List<String> extractAbilities(Map<String, String> cardData) {
            List<String> abilities = new ArrayList<>();

            // Look for A: lines (Activated abilities) and T: lines (Triggered abilities)
            for (Map.Entry<String, String> entry : cardData.entrySet()) {
                String key = entry.getKey();
                if (key.equals("A") || key.equals("T") || key.equals("S")) {
                    abilities.add(entry.getValue());
                }
            }

            return abilities;
        }

        /**
         * Calculate converted mana cost from mana cost string
         */
        private int calculateCMC(String manaCost) {
            if (manaCost == null || manaCost.isEmpty()) {
                return 0;
            }

            int cmc = 0;

            // Parse numeric values
            StringBuilder numBuilder = new StringBuilder();
            for (char c : manaCost.toCharArray()) {
                if (Character.isDigit(c)) {
                    numBuilder.append(c);
                } else if (numBuilder.length() > 0) {
                    cmc += Integer.parseInt(numBuilder.toString());
                    numBuilder = new StringBuilder();
                }

                // Count colored mana symbols
                if (c == 'W' || c == 'U' || c == 'B' || c == 'R' || c == 'G') {
                    cmc++;
                }
                // Handle hybrid mana (counts as 1)
                else if (c == '/' && manaCost.indexOf(c) > 0) {
                    // Skip, already counted the first part
                }
                // Handle Phyrexian mana
                else if (c == 'P') {
                    // Phyrexian mana counts as 1
                }
                // Handle X costs
                else if (c == 'X') {
                    // X is typically 0 for CMC calculation
                }
            }

            // Add any remaining numeric value
            if (numBuilder.length() > 0) {
                cmc += Integer.parseInt(numBuilder.toString());
            }

            return cmc;
        }

        /**
         * Calculate color identity from card data
         */
        private Set<String> calculateColorIdentity(Map<String, String> cardData) {
            Set<String> colors = new HashSet<>();
            String manaCost = cardData.getOrDefault("ManaCost", "");
            String oracleText = cardData.getOrDefault("Oracle", "");

            // Check mana cost
            if (manaCost.contains("W")) colors.add("White");
            if (manaCost.contains("U")) colors.add("Blue");
            if (manaCost.contains("B")) colors.add("Black");
            if (manaCost.contains("R")) colors.add("Red");
            if (manaCost.contains("G")) colors.add("Green");

            // Check oracle text for color indicators and mana symbols
            if (oracleText.contains("{W}")) colors.add("White");
            if (oracleText.contains("{U}")) colors.add("Blue");
            if (oracleText.contains("{B}")) colors.add("Black");
            if (oracleText.contains("{R}")) colors.add("Red");
            if (oracleText.contains("{G}")) colors.add("Green");

            return colors;
        }

        /**
         * Sanitize card name for use as a class name
         */
        private String sanitizeName(String name) {
            return name.replaceAll("[^a-zA-Z0-9]", "_");
        }

        /**
         * Get all card definitions
         */
        public Map<String, NounDefinition> getCardDefinitions() {
            return Collections.unmodifiableMap(cardDefinitions);
        }

        /**
         * Get a specific card definition by name
         */
        public NounDefinition getCardDefinition(String cardName) {
            return cardDefinitions.get(cardName);
        }
    }

    public class CardInstanceFactory {
        private static final Logger LOGGER = Logger.getLogger(CardInstanceFactory.class.getName());

        private final CardDefinitionBuilder definitionBuilder;
        private final KnowledgeBase knowledgeBase;
        private final Map<Card, NounInstance> cardToInstance;
        private final Map<NounInstance, Card> instanceToCard;

        public CardInstanceFactory(CardDefinitionBuilder definitionBuilder) {
            this.definitionBuilder = definitionBuilder;
            this.knowledgeBase = KnowledgeBase.getInstance();
            this.cardToInstance = new HashMap<>();
            this.instanceToCard = new HashMap<>();
        }

        /**
         * Create a NounInstance for a Card
         */
        public NounInstance createCardInstance(Card card) {
            String cardName = card.getName();
            NounDefinition definition = definitionBuilder.getCardDefinition(cardName);

            if (definition == null) {
                LOGGER.warning("No definition found for card: " + cardName);
                return null;
            }

            // Create unique ID for this card instance
            String instanceId = generateInstanceId(card);

            // Create initial property values from the Card
            Map<String, Object> initialValues = extractCardProperties(card);

            // Create the NounInstance
            NounInstance instance = knowledgeBase.createInstance(
                    definition.getClassName(),
                    instanceId
            );

            // Update with card-specific values
            instance.updateProperties(initialValues);

            // Store bidirectional mapping
            cardToInstance.put(card, instance);
            instanceToCard.put(instance, card);

            LOGGER.fine("Created NounInstance for card: " + cardName + " with ID: " + instanceId);

            return instance;
        }

        /**
         * Update a NounInstance when a Card's state changes
         */
        public void updateCardInstance(Card card) {
            NounInstance instance = cardToInstance.get(card);
            if (instance == null) {
                LOGGER.warning("No instance found for card: " + card.getName());
                return;
            }

            Map<String, Object> updatedProperties = extractCardProperties(card);
            instance.updateProperties(updatedProperties);

            LOGGER.fine("Updated NounInstance for card: " + card.getName());
        }

        /**
         * Extract properties from a Card object
         */
        private Map<String, Object> extractCardProperties(Card card) {
            Map<String, Object> properties = new HashMap<>();

            // Basic properties
            properties.put("name", card.getName());
            properties.put("types", card.getType().toString());

            // Mana cost
            if (card.getManaCost() != null) {
                properties.put("manaCost", card.getManaCost().toString());
                properties.put("convertedManaCost", card.getCMC());
            }

            // Type booleans
            properties.put("isCreature", card.isCreature());
            properties.put("isArtifact", card.isArtifact());
            properties.put("isEnchantment", card.isEnchantment());
            properties.put("isInstant", card.isInstant());
            properties.put("isSorcery", card.isSorcery());
            properties.put("isPlaneswalker", card.isPlaneswalker());
            properties.put("isLand", card.isLand());
            properties.put("isBattle", card.isBattle());

            // Creature properties
            if (card.isCreature()) {
                properties.put("power", String.valueOf(card.getNetPower()));
                properties.put("toughness", String.valueOf(card.getNetToughness()));
            }

            // Keywords
            List<String> keywords = new ArrayList<>();
            for (KeywordInterface keyword : card.getKeywords()) {
                keywords.add(String.valueOf(keyword));
            }
            properties.put("keywords", keywords);

            // Oracle text
            properties.put("oracleText", card.getOracleText());

            // Color identity
            Set<String> colorIdentity = new HashSet<>();
            if (card.getColor().hasWhite()) colorIdentity.add("White");
            if (card.getColor().hasBlue()) colorIdentity.add("Blue");
            if (card.getColor().hasBlack()) colorIdentity.add("Black");
            if (card.getColor().hasRed()) colorIdentity.add("Red");
            if (card.getColor().hasGreen()) colorIdentity.add("Green");
            properties.put("colorIdentity", colorIdentity);

            // Game state properties
            if (card.getGame() != null) {
                properties.put("zone", card.getZone() != null ? card.getZone().getZoneType().name() : "Unknown");
                properties.put("controller", card.getController() != null ? card.getController().getName() : "None");
                properties.put("owner", card.getOwner() != null ? card.getOwner().getName() : "None");
                properties.put("isTapped", card.isTapped());
                properties.put("damage", card.getDamage());

                // Counters
                Map<String, Integer> counters = new HashMap<>();
                for (Map.Entry<CounterType, Integer> entry : card.getCounters().entrySet()) {
                    counters.put(entry.getKey().toString(), entry.getValue());
                }
                properties.put("counters", counters);
            }

            return properties;
        }

        /**
         * Generate a unique instance ID for a card
         */
        private String generateInstanceId(Card card) {
            // Use card ID if available, otherwise generate one
            if (card.getId() > 0) {
                return "card_" + card.getId() + "_" + System.currentTimeMillis();
            } else {
                return "card_" + card.getName().replaceAll("[^a-zA-Z0-9]", "_") +
                        "_" + UUID.randomUUID().toString().substring(0, 8);
            }
        }

        /**
         * Get NounInstance for a Card
         */
        public NounInstance getInstance(Card card) {
            return cardToInstance.get(card);
        }

        /**
         * Get Card for a NounInstance
         */
        public Card getCard(NounInstance instance) {
            return instanceToCard.get(instance);
        }

        /**
         * Remove instance when card is removed from game
         */
        public void removeCardInstance(Card card) {
            NounInstance instance = cardToInstance.remove(card);
            if (instance != null) {
                instanceToCard.remove(instance);
                LOGGER.fine("Removed NounInstance for card: " + card.getName());
            }
        }

        /**
         * Get all card instances
         */
        public Collection<NounInstance> getAllInstances() {
            return Collections.unmodifiableCollection(cardToInstance.values());
        }

        /**
         * Query instances by property
         */
        public List<NounInstance> queryInstances(String propertyName, Object value) {
            List<NounInstance> results = new ArrayList<>();

            for (NounInstance instance : cardToInstance.values()) {
                Object propValue = instance.getProperty(propertyName);
                if (propValue != null && propValue.equals(value)) {
                    results.add(instance);
                }
            }

            return results;
        }

        /**
         * Query instances by type
         */
        public List<NounInstance> queryByType(String cardType) {
            List<NounInstance> results = new ArrayList<>();

            for (NounInstance instance : cardToInstance.values()) {
                String types = instance.getPropertyAs("types", String.class);
                if (types != null && types.contains(cardType)) {
                    results.add(instance);
                }
            }

            return results;
        }

        /**
         * Query creatures with specific power/toughness
         */
        public List<NounInstance> queryCreatures(int minPower, int minToughness) {
            List<NounInstance> results = new ArrayList<>();

            for (NounInstance instance : cardToInstance.values()) {
                Boolean isCreature = instance.getPropertyAs("isCreature", Boolean.class);
                if (Boolean.TRUE.equals(isCreature)) {
                    try {
                        String powerStr = instance.getPropertyAs("power", String.class);
                        String toughnessStr = instance.getPropertyAs("toughness", String.class);

                        if (powerStr != null && toughnessStr != null) {
                            int power = Integer.parseInt(powerStr);
                            int toughness = Integer.parseInt(toughnessStr);

                            if (power >= minPower && toughness >= minToughness) {
                                results.add(instance);
                            }
                        }
                    } catch (NumberFormatException e) {
                        // Handle special cases like * or X
                        continue;
                    }
                }
            }

            return results;
        }
    }
}