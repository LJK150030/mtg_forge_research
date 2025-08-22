package APF;

import com.example.research.Neo4jService;
import forge.game.card.Card;
import forge.game.card.CounterType;
import forge.game.keyword.KeywordInterface;
import org.neo4j.driver.Result;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static APF.magic_commons.*;


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

        public CardDefinitionBuilder(String cardsfolderPath, KnowledgeBase knowledgeBase) {
            this.cardsfolderPath = Paths.get(cardsfolderPath);
            this.cardDefinitions = new HashMap<>();
            this.knowledgeBase = knowledgeBase;  // Use the passed instance
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
            builder.addStringProperty(
                    "name",
                    cardName,
                    1,
                    200,
                    NON_EMPTY
            ).addRequiredProperty("name");

            // Mana Cost - Convert to standard format
            if (cardData.containsKey("ManaCost")) {
                String manaCost = cardData.get("ManaCost");
                if (!manaCost.contains("no cost")) {
                    String formattedManaCost = formatManaCost(manaCost);
                    builder.addStringProperty(
                            "manaCost",
                            formattedManaCost,
                            0,
                            100,
                            FULL_BRACED
                    ).addRequiredProperty("manaCost");

                    builder.addIntProperty(
                            "convertedManaCost",
                            calculateCMC(manaCost),
                            0,
                            9999
                    ).addRequiredProperty("convertedManaCost");
                }
                else
                {
                    builder.addStringProperty(
                            "manaCost",
                            "{0}",
                            0,
                            100,
                            FULL_BRACED
                    ).addRequiredProperty("manaCost");

                    builder.addIntProperty(
                            "convertedManaCost",
                            0,
                            0,
                            9999
                    ).addRequiredProperty("convertedManaCost");
                }
            }

            String formattedManaCost = cardData.containsKey("ManaCost") && !cardData.get("ManaCost").contains("no cost")
                    ? formatManaCost(cardData.get("ManaCost"))
                    : "";

            List<String> colorId = calculateColorIdentity(formattedManaCost, cardData.get("Oracle"));

            builder.addListProperty(
                    "colorIdentity",
                    (colorId == null ? java.util.Collections.emptyList() : colorId),
                    String.class,
                    CARD_COLOR_ID,
                    0, 5,
                    false
            ).addRequiredProperty("colorIdentity");

            // Parse Types and Subtypes separately
            ParsedTypes parsed = parseTypesAndSubtypes(typesLine);

            // Store supertypes and types as lists (no special validation needed)
            if(!parsed.supertypes.isEmpty()) {
                builder.addListProperty(
                        "types.superTypes",
                        parsed.supertypes,
                        String.class,
                        CARD_SUPERTYPES,
                        0,
                        16,
                        false
                );
            }

            if(!parsed.types.isEmpty()) {
                builder.addListProperty(
                        "types.cardTypes",
                        parsed.types,
                        String.class,
                        CARD_TYPES,
                        0,
                        16,
                        false
                ).addRequiredProperty("types.cardTypes");
            }


            // Determine valid subtypes based on the card's types
            if(!parsed.subtypes.isEmpty()) {
                Set<String> validSubtypes = getValidSubtypesForTypes(parsed.types);

                builder.addListProperty(
                        "types.subTypes",
                        parsed.subtypes,
                        String.class,
                        validSubtypes,
                        0,
                        16,
                        false
                );
            }

            // Creature-specific properties
            if (parsed.types.contains("Creature") && cardData.containsKey("PT")) {
                String pt = cardData.get("PT");
                String[] parts = pt.split("/");
                if (parts.length == 2) {
                    builder.addStringProperty(
                            "power",
                            parts[0].trim(),
                            0,
                            16,
                            DIGIT_ONLY
                            ).addStringProperty(
                                "toughness",
                                parts[1].trim(),
                                0,
                                16,
                                DIGIT_ONLY
                    );
                }
            }

            if (parsed.types.contains("Planeswalker") && cardData.containsKey("Loyalty")) {
                String loyalty = cardData.get("Loyalty");
                builder.addIntProperty(
                     "loyalty",
                        Integer.parseInt(loyalty),
                        0,
                        999
                );
            }

            if (parsed.types.contains("Battle") && cardData.containsKey("Defense")) {
                String defense = cardData.get("Defense");
                builder.addIntProperty(
                        "Defense",
                        Integer.parseInt(defense),
                        0,
                        999
                );
            }

            // Keywords - now properly extracts from K: lines
            List<String> keywords = extractKeywords(cardData); // may be empty
            builder.addListProperty(
                    ( "keywords" ),
                    (keywords == null ? java.util.Collections.emptyList() : keywords),
                    String.class,
                    KEYWORD_ABILITIES,
                    0, 16,
                    false
            );

            // Abilities - from A:, T:, S: lines
            List<String> abilities = extractAbilities(cardData);
            builder.addProperty("abilities", abilities);

            // Oracle text
            String oracleText = cardData.getOrDefault("Oracle", "");
            builder.addStringProperty(
                    "oracleText",
                    oracleText,
                    0,
                    5000,
                    MATCH_ANYTHING
            ).addRequiredProperty("oracleText");



            ///  Stateful infromation

            // Zones
            builder.addListProperty(
                    "zone",
                    List.of("Library"),
                    String.class,
                    ZONE_TYPES,
                    1,
                    1,
                    false
            );


            builder.addListProperty(
                    "owner",
                    List.of("NULL"),
                    String.class,
                    PLAYERS,
                    1,
                    1,
                    false
            );

            builder.addListProperty(
                    "controller",
                    List.of("NULL"),
                    String.class,
                    PLAYERS,
                    1,
                    1,
                    false
            );

            builder.addBooleanProperty(
                "tapped",
                false
            );

            builder.addBooleanProperty(
                    "summoningSick",
                    false
            );

            builder.addMapProperty(
                    "counters",
                    new java.util.LinkedHashMap<String, Integer>(),
                    String.class, Integer.class,
                    new Domain.EnumDomain<>(String.class, CARD_COUNTER_TYPES),
                    new Domain.IntDomain(0, 999),
                    0, 128
            );

            return builder.build();
        }

        /**
         * Helper class to hold parsed types and subtypes
         */
        private static class ParsedTypes {
            List<String> supertypes;
            List<String> types;
            List<String> subtypes;

            ParsedTypes(List<String> supertypes, List<String> types, List<String> subtypes) {
                this.supertypes = supertypes;
                this.types = types;
                this.subtypes = subtypes;
            }
        }


        private ParsedTypes parseTypesAndSubtypes(String typesLine) {
            List<String> supertypes = new ArrayList<>();
            List<String> types = new ArrayList<>();
            List<String> subtypes = new ArrayList<>();

            if (typesLine == null || typesLine.isEmpty()) {
                return new ParsedTypes(supertypes, types, subtypes);
            }

            String[] parts = typesLine.split("\\s+");
            boolean foundMainType = false;

            for (String part : parts) {
                if (!foundMainType && CARD_SUPERTYPES_SET.contains(part)) {
                    // Supertypes come before main types
                    supertypes.add(part);
                } else if (CARD_TYPES_SET.contains(part)) {
                    // Main card type
                    types.add(part);
                    foundMainType = true;
                } else if (foundMainType) {
                    // After we've found a main type, check if this is a valid subtype
                    if (isValidSubtype(part, types)) {
                        subtypes.add(part);
                    }
                }
            }

            return new ParsedTypes(supertypes, types, subtypes);
        }

        private boolean isValidSubtype(String subtype, List<String> cardTypes) {
            // Check if the subtype is valid for any of the card's types
            for (String cardType : cardTypes) {
                Set<String> validSubtypes = TYPE_TO_SUBTYPES.get(cardType);
                if (validSubtypes != null && validSubtypes.contains(subtype)) {
                    return true;
                }
            }
            return false;
        }


        private Set<String> getValidSubtypesForTypes(List<String> cardTypes) {
            Set<String> validSubtypes = new HashSet<>();

            for (String cardType : cardTypes) {
                Set<String> typesForThisCardType = TYPE_TO_SUBTYPES.get(cardType);
                if (typesForThisCardType != null) {
                    validSubtypes.addAll(typesForThisCardType);
                }
            }

            // Special case: Kindred shares creature types
            if (cardTypes.contains("Kindred")) {
                Set<String> creatureTypes = TYPE_TO_SUBTYPES.get("Creature");
                if (creatureTypes != null) {
                    validSubtypes.addAll(creatureTypes);
                }
            }

            return validSubtypes;
        }

        /**
         * Format mana cost from Forge format to standard MTG format
         * Examples:
         * "1 B" -> "{1}{B}"
         * "2 W W" -> "{2}{W}{W}"
         * "W U B R G" -> "{W}{U}{B}{R}{G}"
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
                    if (!numBuilder.isEmpty()) {
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
                            formatted.append(last, 0, 2).append("/P}");
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

        private List<String> extractKeywords(Map<String, String> cardData) {
            return extractKeywordMetadata(cardData).canonical;
        }

        private Map<String, List<AltKeyword>> extractAltKeywords(Map<String, String> cardData) {
            return extractKeywordMetadata(cardData).alt;
        }

        private static KeywordParseResult extractKeywordMetadata(Map<String, String> cardData) {
            // Resolve SVar actions for linking
            Map<String, String> svars = parseSVarActions(cardData);

            // Dedup canonical, keep first-seen order
            java.util.Set<String> canonicalOrdered = new java.util.LinkedHashSet<>();
            // Group alt keywords by head
            Map<String, List<AltKeyword>> altByHead = new LinkedHashMap<>();

            for (Map.Entry<String, String> e : cardData.entrySet()) {
                String key = e.getKey();
                if (!"K".equals(key) && !key.startsWith("K:")) continue;
                String raw = e.getValue();
                if (raw == null || raw.isEmpty()) continue;

                for (String piece : SPLIT_COMBINED.split(raw)) {
                    String p = piece.trim();
                    if (p.isEmpty()) continue;

                    // Case-insensitive canonical check (exact match)
                    String lower = p.toLowerCase(Locale.ROOT);
                    if (KEYWORD_ABILITY_SET.contains(lower)) {
                        // Add the canonical, preserving original casing if you like:
                        canonicalOrdered.add(capitalizeAsKnown(p));
                        continue;
                    }

                    // Otherwise treat as alternative/macro: split on ':'
                    String[] parts = p.split(":", -1);
                    if (parts.length >= 1) {
                        String head = parts[0].trim();
                        List<String> args = new ArrayList<>();
                        for (int i = 1; i < parts.length; i++) args.add(parts[i].trim());

                        // Heuristic: last arg may be an SVar name; link if present
                        String refSVar = null, refAction = null;
                        if (!args.isEmpty()) {
                            String last = args.get(args.size()-1);
                            if (svars.containsKey(last)) {
                                refSVar = last;
                                refAction = svars.get(last);  // e.g., "ChooseColor"
                            }
                        }

                        AltKeyword ak = new AltKeyword(head, args, refSVar, refAction);
                        altByHead.computeIfAbsent(head, h -> new ArrayList<>()).add(ak);
                    }
                }
            }

            return new KeywordParseResult(new ArrayList<>(canonicalOrdered), altByHead);
        }

        // Optional: map back to canonical casing if desired
        private static String capitalizeAsKnown(String s) {
            // If you want perfect casing, build a Map lower->canonical from KEYWORD_ABILITIES.
            return KEYWORD_ABILITIES.stream()
                    .filter(k -> k.equalsIgnoreCase(s))
                    .findFirst().orElse(s);
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
        public static List<String> calculateColorIdentity(String formattedManaCost, String oracleText) {
            Set<String> colors = new HashSet<>();
            extractColors(formattedManaCost != null ? formattedManaCost : "", colors);
            extractColors(oracleText != null ? oracleText : "", colors);
            return CARD_COLOR_ID.stream().filter(colors::contains).toList();
        }

        private static void extractColors(String text, Set<String> out) {
            if (text == null || text.isEmpty()) return;

            Matcher m = ELEMENT_BASED_BRACED.matcher(text);
            while (m.find()) {
                String token = m.group(1);          // e.g. "U", "2/W", "B/G", "W/P", "10"
                for (int i = 0; i < token.length(); i++) {
                    String name = COLOR_BY_SYMBOL.get(token.charAt(i));
                    if (name != null) out.add(name); // picks up W/U/B/R/G inside hybrids, phyrexian, etc.
                }
            }
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

    /**
     * ZoneDefinitionBuilder defines MTG zones as NounDefinitions and
     * registers them with the KnowledgeBase. Mirrors CardDefinitionBuilder's pattern.
     *
     * Each definition is a prototype (schema + defaults). Create per-game instances
     * later with NounDefinition#createInstance(...).
     */
    public static final class ZoneDefinitionBuilder {
        private static final Logger LOGGER = Logger.getLogger(ZoneDefinitionBuilder.class.getName());

        private final Map<String, NounDefinition> zoneDefinitions = new HashMap<>();
        private final KnowledgeBase knowledgeBase;

        public ZoneDefinitionBuilder(KnowledgeBase knowledgeBase) {
            this.knowledgeBase = knowledgeBase;
        }

        /** Build and register all canonical zones (shared + per-player archetypes). */
        public void buildAllZoneDefinitions() {
            register(buildBattlefield());
            //register(buildCommand());
            register(buildExile());
            register(buildStack());

            // Per-player archetypes (owner will be set on instance)
            register(buildLibrary());
            register(buildHand());
            register(buildGraveyard());

            LOGGER.info("Successfully built " + zoneDefinitions.size() + " zone definitions");
        }

        private void register(NounDefinition def) {
            zoneDefinitions.put(def.getClassName(), def);
            knowledgeBase.registerDefinition(def);
            LOGGER.fine("Registered zone definition: " + def.getClassName());
        }

        // ====== Zone prototypes ===================================================

        /** Battlefield — public zone, unordered (arrangement free), permanents only. */
        private NounDefinition buildBattlefield() {
            NounDefinition.Builder b = new NounDefinition.Builder("Zone_Battlefield")
                    .description("Battlefield zone (public, permanents only)");

            b.addEnumProperty(
                    "zoneType",
                    "Battlefield",
                    ZONE_TYPES
            ).addRequiredProperty("zoneType");

            b.addEnumProperty(
                    "visibility",
                    "Public",
                    Arrays.asList("Public", "Hidden")
            ).addRequiredProperty("visibility");

            // Shared zone (owner not applicable). Keep single owner slot with NULL default.
            b.addEnumProperty(
                    "owner",
                    "NULL",
                    PLAYERS
            );

            b.addListProperty(
                    "allowedCardTypes",
                    List.of("Artifact","Creature","Enchantment","Land","Planeswalker","Battle","Kindred"),
                    String.class,
                    CARD_TYPES_SET,
                    0,
                    1000,
                    true
            );

            b.addProperty(
                    "contents",
                    new ArrayList<String>(),
                    contentsDomain("^(?:card|token)_(?:0|[1-9]\\d*)_\\d{13}$", 0, 10000)
            );

            // Shared zone marker
            b.addBooleanProperty("shared", true);

            return b.build();
        }

        /** Command — public, shared, special object types (Conspiracy/Plane/Phenomenon/Scheme/Vanguard), emblems allowed. */
        private NounDefinition buildCommand() {
            NounDefinition.Builder b = new NounDefinition.Builder("Zone_Command")
                    .description("Command zone (public, shared; special objects live here)");

            b.addEnumProperty(
                    "zoneType",
                    "Command",
                    ZONE_TYPES
            ).addRequiredProperty("zoneType");

            b.addEnumProperty(
                    "visibility",
                    "Public",
                    Arrays.asList("Public", "Hidden")
            ).addRequiredProperty("visibility");

            b.addBooleanProperty("ordered", false);

            b.addListProperty(
                    "allowedCardTypes",
                    List.of("Creature","Planeswalker","Enchantment","Artifact"),
                    String.class,
                    CARD_TYPES_SET,
                    0,
                    1000,
                    true
            );


            b.addProperty(
                    "contents",
                    new ArrayList<String>(),
                    contentsDomain("^(?:card|token)_(?:0|[1-9]\\d*)_\\d{13}$", 0, 10000)
            );

            return b.build();
        }

        /** Exile — public, ordered status is “no” (CR 400.5 doesn’t constrain Exile order), any card type. */
        private NounDefinition buildExile() {
            NounDefinition.Builder b = new NounDefinition.Builder("Zone_Exile")
                    .description("Exile zone (public; any card type; can include face-down exiled objects)");

            b.addEnumProperty(
                    "zoneType",
                    "Exile",
                    ZONE_TYPES
            ).addRequiredProperty("zoneType");

            b.addEnumProperty(
                    "visibility",
                    "Public",
                    Arrays.asList("Public", "Hidden")
            ).addRequiredProperty("visibility");

            b.addEnumProperty(
                    "owner",
                    "NULL",
                    PLAYERS
            );


            b.addListProperty(
                    "allowedCardTypes",
                    List.of("Artifact","Creature","Enchantment","Instant","Land","Planeswalker","Sorcery","Battle","Kindred"),
                    String.class,
                    CARD_TYPES_SET,
                    0,
                    1000,
                    true
            );

            b.addProperty(
                    "contents",
                    new ArrayList<String>(),
                    contentsDomain("^(?:card|token)_(?:0|[1-9]\\d*)_\\d{13}$", 0, 10000)
            );

            return b.build();
        }

        /** Stack — public, ordered (LIFO), contains spells and abilities (instants/sorceries + other spells-as-objects). */
        private NounDefinition buildStack() {
            NounDefinition.Builder b = new NounDefinition.Builder("Zone_Stack")
                    .description("Stack (public; ordered; spells/abilities)");

            b.addEnumProperty(
                    "zoneType",
                    "Stack",
                    ZONE_TYPES
            ).addRequiredProperty("zoneType");

            b.addEnumProperty(
                    "visibility",
                    "Public",
                    Arrays.asList("Public", "Hidden")
            ).addRequiredProperty("visibility");

            b.addEnumProperty(
                    "owner",
                    "NULL",
                    PLAYERS
            );


            b.addListProperty(
                    "allowedCardTypes",
                    List.of("Artifact","Creature","Enchantment","Planeswalker","Sorcery","Instant","Battle","Kindred"),
                    String.class,
                    CARD_TYPES_SET,
                    0,
                    1000,
                    true
            );

            b.addProperty(
                    "contents",
                    new ArrayList<String>(),
                    contentsDomain("^(?:card|token)_(?:0|[1-9]\\d*)_\\d{13}$", 0, 10000)
            );

            return b.build();
        }

        /** Library — hidden, ordered (top/bottom), per-player. */
        private NounDefinition buildLibrary() {
            NounDefinition.Builder b = new NounDefinition.Builder("Zone_Library")
                    .description("Library (hidden; ordered; one per player)");

            b.addEnumProperty(
                    "zoneType",
                    "Library",
                    ZONE_TYPES
            ).addRequiredProperty("zoneType");

            b.addEnumProperty(
                    "visibility",
                    "Hidden",
                    Arrays.asList("Public", "Hidden")
            ).addRequiredProperty("visibility");

            b.addEnumProperty(
                    "owner",
                    "NULL",
                    PLAYERS
            ).addRequiredProperty("owner");

            b.addListProperty(
                    "allowedCardTypes",
                    List.of("Artifact","Creature","Enchantment","Planeswalker","Sorcery","Instant","Battle","Kindred"),
                    String.class,
                    CARD_TYPES_SET,
                    0,
                    1000,
                    true
            );

            // Useful state flags for gameplay bookkeeping
            b.addBooleanProperty(
                    "topRevealed",
                    false
            );

            b.addProperty(
                    "contents",
                    new ArrayList<String>(),
                    contentsDomain("^(?:card|token)_(?:0|[1-9]\\d*)_\\d{13}$", 0, 100000)
            );

            return b.build();
        }

        /** Hand — hidden, unordered, per-player; default max size 7 per CR 402.2. */
        private NounDefinition buildHand() {
            NounDefinition.Builder b = new NounDefinition.Builder("Zone_Hand")
                    .description("Hand (hidden; unordered; one per player)");

            b.addEnumProperty(
                    "zoneType",
                    "Hand",
                    ZONE_TYPES
            ).addRequiredProperty("zoneType");

            b.addEnumProperty(
                    "visibility",
                    "Hidden",
                    Arrays.asList("Public", "Hidden")
            ).addRequiredProperty("visibility");

            b.addEnumProperty(
                    "owner",
                    "NULL",
                    PLAYERS
            ).addRequiredProperty("owner");


            b.addListProperty(
                    "allowedCardTypes",
                    List.of("Artifact","Creature","Enchantment","Instant","Land","Planeswalker","Sorcery","Battle","Kindred"),
                    String.class,
                    CARD_TYPES_SET,
                    0,
                    1000,
                    true
            );

            b.addIntProperty(
                    "maxSize",
                    7,
                    0,
                    99
            );

            b.addProperty(
                    "contents",
                    new ArrayList<String>(),
                    contentsDomain("^(?:card|token)_(?:0|[1-9]\\d*)_\\d{13}$", 0, 64)
            );

            return b.build();
        }

        /** Graveyard — public, ordered (single face-up pile), per-player. */
        private NounDefinition buildGraveyard() {
            NounDefinition.Builder b = new NounDefinition.Builder("Zone_Graveyard")
                    .description("Graveyard (public; ordered; one per player)");

            b.addEnumProperty(
                    "zoneType",
                    "Graveyard",
                    ZONE_TYPES
            ).addRequiredProperty("zoneType");

            b.addEnumProperty(
                    "visibility",
                    "Public",
                    Arrays.asList("Public", "Hidden")
            ).addRequiredProperty("visibility");

            b.addEnumProperty(
                    "owner",
                    "NULL",
                    PLAYERS
            ).addRequiredProperty("owner");


            b.addListProperty(
                    "allowedCardTypes",
                    List.of( "Artifact","Creature","Enchantment","Instant","Land","Planeswalker","Sorcery","Battle","Kindred"),
                    String.class,
                    CARD_TYPES_SET,
                    0,
                    1000,
                    true
            );

            b.addProperty(
                    "contents",
                    new ArrayList<String>(),
                    contentsDomain("^(?:card|token)_(?:0|[1-9]\\d*)_\\d{13}$", 0, 2000)
            );

            return b.build();
        }

        // ====== Accessors =========================================================

        private Domain<List<String>> contentsDomain(String idRegex, int min, int max) {
            Domain<String> idRef = new Domain.KBRefDomain(knowledgeBase, idRegex);
            return new Domain.KBRefListDomain(idRef, min, max);
        }

        public Map<String, NounDefinition> getZoneDefinitions() {
            return Collections.unmodifiableMap(zoneDefinitions);
        }

        public NounDefinition getZoneDefinition(String className) {
            return zoneDefinitions.get(className);
        }
    }


    public static final class PlayerDefinitionBuilder {
        private static final Logger LOGGER = Logger.getLogger(PlayerDefinitionBuilder.class.getName());
        private static final List<String> FALLBACK_MANA_KEYS =
                java.util.Arrays.asList("W","U","B","R","G","C");

        private final KnowledgeBase knowledgeBase;
        private final Map<String, NounDefinition> playerDefinitions = new HashMap<>();

        public PlayerDefinitionBuilder(KnowledgeBase knowledgeBase) {
            this.knowledgeBase = knowledgeBase;
        }

        /** Build and register the canonical Player definition. */
        public void buildAllPlayerDefinitions() {
            register(buildPlayer());
            LOGGER.info("Successfully built " + playerDefinitions.size() + " player definitions");
        }

        private void register(NounDefinition def) {
            playerDefinitions.put(def.getClassName(), def);
            knowledgeBase.registerDefinition(def);
            LOGGER.fine("Registered player definition: " + def.getClassName());
        }

        /** Player — life, hand/deck params, mana pool, and player-scoped counters. */
        private NounDefinition buildPlayer() {
            NounDefinition.Builder b = new NounDefinition.Builder("Player")
                    .description("Player entity: identity, life/hand, mana pool, and player counters.");

            b.addStringProperty(
                    "displayName",
                    "",
                    0, 60,
                    MATCH_ANYTHING
            ).addRequiredProperty("displayName");

            // Life totals — CR 103.4, 119.1 (defaults to 20; formats may override at setup)
            b.addIntProperty("startingLife", 20, 0, 200)
                    .addIntProperty("life",         20, -1000, 1000);

            // Hand size — CR 103.5 (starting hand size normally 7); max hand size — CR 402.2 (normally 7)
            b.addIntProperty("startingHandSize", 7, 0, 14);
            b.addIntProperty("maxHandSize",      7, 0, 99);

            // Deck construction knobs (set per format at match bootstrap; defaults here are Constructed-like)
            b.addIntProperty("deckMinSize", 60, 0, 500);

            // Mana pool — keys strictly from your shared default; empties each step/phase (CR 106.4)
            // Use a defensive copy so the prototype carries its own map instance.
            final List<String> manaKeys = resolveManaKeys();               // <- see helper below
            final Map<String,Integer> defaultMana = zeroedManaPool(manaKeys);

            b.addMapProperty(
                    "manaPool",
                    defaultMana,
                    String.class, Integer.class,
                    new Domain.EnumDomain<>(String.class, manaKeys),           // only W/U/B/R/G/C (or your custom set)
                    new Domain.IntDomain(0, 999),
                    0,
                    manaKeys.size()
            );

            // Helper toggle (lets your state tracker know to drain per step/phase)
            b.addBooleanProperty("emptyManaPoolAtEndOfStepOrPhase", true);

            // Player counters: open-ended by design (poison/energy/experience/etc.)
            b.addMapProperty(
                    "counters",
                    new java.util.LinkedHashMap<String, Integer>(),
                    String.class, Integer.class,
                    new Domain.EnumDomain<>(String.class, PLAYER_COUNTER_TYPES),
                    new Domain.IntDomain(0, 999),
                    0, 128
            );

            // (Optional but convenient) table seating / meta
            b.addIntProperty("seatIndex", 0, 0, 63);

            return b.build();
        }

        /** Prefer keys published by magic_commons (if you add them); otherwise use WUBRGC. */
        private List<String> resolveManaKeys() {
            try {
                // If you later add magic_commons.MANA_KEYS, this will pick it up.
                @SuppressWarnings("unchecked")
                List<String> keys = (List<String>)magic_commons.class.getField("MANA_KEYS").get(null);
                if (keys != null && !keys.isEmpty()) return new ArrayList<>(keys);
            } catch (NoSuchFieldException | IllegalAccessException ignored) { }
            return FALLBACK_MANA_KEYS;
        }

        private Map<String,Integer> zeroedManaPool(List<String> keys) {
            Map<String,Integer> m = new java.util.LinkedHashMap<>();
            for (String k : keys) m.put(k, 0);
            return m;
        }

        public Map<String, NounDefinition> getPlayerDefinitions() {
            return java.util.Collections.unmodifiableMap(playerDefinitions);
        }

        public NounDefinition getPlayerDefinition(String className) {
            return playerDefinitions.get(className);
        }
    }



    public static final class Neo4jDefinitionBuilder {
        private static final Logger LOGGER = Logger.getLogger(Neo4jDefinitionBuilder.class.getName());

        private final Neo4jService neo4jService;
        private int nounDefinitionsCreated = 0;
        private KnowledgeBase kb;

        public Neo4jDefinitionBuilder(KnowledgeBase knowledgeBase) {
            this.neo4jService = Neo4jService.getInstance();
            this.kb = knowledgeBase;
        }

        /**
         * Build Neo4j nodes for all NounDefinitions in the KnowledgeBase
         */
        public void buildAllNounDefinitions() {
            Map<String, NounDefinition> nounDefinitions = kb.getNounDefinitions();

            LOGGER.info("Starting to build Neo4j nodes for " + nounDefinitions.size() + " NounDefinitions");

            for (Map.Entry<String, NounDefinition> entry : nounDefinitions.entrySet()) {
                buildNounDefinition(entry.getValue());
            }

            LOGGER.info("Successfully created " + nounDefinitionsCreated + " NounDefinition nodes in Neo4j");
        }

        /**
         * Build Neo4j node for a single NounDefinition
         */
        public void buildNounDefinition(NounDefinition definition) {
            try {
                neo4jService.writeTransaction(tx -> {
                    // Convert all properties to a storable format
                    Map<String, Map<String, Object>> propertiesData = new HashMap<>();
                    Map<String, NounProperty> properties = definition.getPropertyPrototypes();

                    for (Map.Entry<String, NounProperty> propEntry : properties.entrySet()) {
                        String propName = propEntry.getKey();
                        NounProperty property = propEntry.getValue();

                        // Create a map for each property containing its metadata
                        Map<String, Object> propertyInfo = new HashMap<>();
                        propertyInfo.put("name", propName);
                        propertyInfo.put("defaultValue", convertValueToString(property.getValue()));

                        // Add domain information
                        Map<String, Object> domainInfo = extractDomainInfo(property.getDomain());
                        propertyInfo.put("domainType", domainInfo.get("type"));
                        propertyInfo.put("domainConstraints", domainInfo.get("constraints"));

                        propertiesData.put(propName, propertyInfo);
                    }

                    // Create the NounDefinition node with embedded properties
                    String createDefinitionQuery =
                            "MERGE (def:NounDefinition {className: $className}) " +
                                    "SET def.description = $description, " +
                                    "    def.requiredProperties = $requiredProperties, " +
                                    "    def.properties = $properties, " +
                                    "    def.propertyNames = $propertyNames, " +
                                    "    def.propertyCount = $propertyCount, " +
                                    "    def.lastUpdated = datetime() " +
                                    "RETURN def";

                    Map<String, Object> defParams = Map.of(
                            "className", definition.getClassName(),
                            "description", definition.getDescription(),
                            "requiredProperties", new ArrayList<>(definition.getRequiredProperties()),
                            "properties", convertPropertiesToJson(propertiesData),
                            "propertyNames", new ArrayList<>(properties.keySet()),
                            "propertyCount", properties.size()
                    );

                    tx.run(createDefinitionQuery, defParams);

                    nounDefinitionsCreated++;
                    LOGGER.fine("Created Neo4j node for NounDefinition: " + definition.getClassName() +
                            " with " + properties.size() + " properties");
                    return null;
                });
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to create Neo4j node for definition: " +
                        definition.getClassName(), e);
            }
        }

        /**
         * Convert properties map to JSON string for storage in Neo4j
         * Neo4j can store this as a single property value
         */
        private String convertPropertiesToJson(Map<String, Map<String, Object>> propertiesData) {
            // Create a simplified JSON-like string representation
            // In production, you might want to use a JSON library like Jackson
            StringBuilder json = new StringBuilder("{");
            boolean first = true;

            for (Map.Entry<String, Map<String, Object>> entry : propertiesData.entrySet()) {
                if (!first) json.append(", ");
                first = false;

                json.append("\"").append(entry.getKey()).append("\": {");

                Map<String, Object> propInfo = entry.getValue();
                json.append("\"defaultValue\": \"").append(propInfo.get("defaultValue")).append("\", ");
                json.append("\"domainType\": \"").append(propInfo.get("domainType")).append("\", ");
                json.append("\"domainConstraints\": ").append(convertMapToJson(propInfo.get("domainConstraints")));
                json.append("}");
            }

            json.append("}");
            return json.toString();
        }

        /**
         * Convert a map to JSON string representation
         */
        private String convertMapToJson(Object obj) {
            if (obj == null) return "{}";
            if (!(obj instanceof Map)) return "\"" + obj.toString() + "\"";

            Map<?, ?> map = (Map<?, ?>) obj;
            if (map.isEmpty()) return "{}";

            StringBuilder json = new StringBuilder("{");
            boolean first = true;

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) json.append(", ");
                first = false;

                json.append("\"").append(entry.getKey()).append("\": ");

                Object value = entry.getValue();
                if (value instanceof String) {
                    json.append("\"").append(value).append("\"");
                } else if (value instanceof Collection) {
                    json.append("[");
                    boolean firstItem = true;
                    for (Object item : (Collection<?>) value) {
                        if (!firstItem) json.append(", ");
                        firstItem = false;
                        json.append("\"").append(item).append("\"");
                    }
                    json.append("]");
                } else {
                    json.append(value);
                }
            }

            json.append("}");
            return json.toString();
        }

        /**
         * Extract domain information into a map for Neo4j storage
         */
        private Map<String, Object> extractDomainInfo(Domain<?> domain) {
            Map<String, Object> info = new HashMap<>();

            if (domain == null) {
                info.put("type", "ANY");
                info.put("constraints", Map.of());
                return info;
            }

            String domainClassName = domain.getClass().getSimpleName();
            Map<String, Object> constraints = new HashMap<>();

            // Extract constraints based on domain type
            if (domain instanceof Domain.IntDomain) {
                Domain.IntDomain intDomain = (Domain.IntDomain) domain;
                info.put("type", "INTEGER");
                if (intDomain.getMin() != null) constraints.put("min", intDomain.getMin());
                if (intDomain.getMax() != null) constraints.put("max", intDomain.getMax());

            } else if (domain instanceof Domain.DoubleDomain) {
                Domain.DoubleDomain doubleDomain = (Domain.DoubleDomain) domain;
                info.put("type", "DOUBLE");
                if (doubleDomain.getMin() != null) constraints.put("min", doubleDomain.getMin());
                if (doubleDomain.getMax() != null) constraints.put("max", doubleDomain.getMax());

            } else if (domain instanceof Domain.StringDomain) {
                Domain.StringDomain stringDomain = (Domain.StringDomain) domain;
                info.put("type", "STRING");
                if (stringDomain.getMinLength() != null) constraints.put("minLength", stringDomain.getMinLength());
                if (stringDomain.getMaxLength() != null) constraints.put("maxLength", stringDomain.getMaxLength());
                if (stringDomain.getPattern() != null) {
                    constraints.put("pattern", stringDomain.getPattern().pattern()); // Use .pattern() to get string
                }

            } else if (domain instanceof Domain.BooleanDomain) {
                info.put("type", "BOOLEAN");

            } else if (domain instanceof Domain.EnumDomain) {
                Domain.EnumDomain<?> enumDomain = (Domain.EnumDomain<?>) domain;
                info.put("type", "ENUM");
                constraints.put("validValues", enumDomain.getValidValues().stream()
                        .map(Object::toString)
                        .collect(Collectors.toList()));

            }  else if (domain instanceof Domain.ListDomain) {
                Domain.ListDomain<?> listDomain = (Domain.ListDomain<?>) domain;
                info.put("type", "LIST");
                if (listDomain.getMinSize() != null) constraints.put("minSize", listDomain.getMinSize());
                if (listDomain.getMaxSize() != null) constraints.put("maxSize", listDomain.getMaxSize());

                constraints.put("elementType", listDomain.getElementType().getSimpleName());

                if (listDomain.getAllowedValues() != null && !listDomain.getAllowedValues().isEmpty()) {
                    constraints.put("allowedValues", listDomain.getAllowedValues().stream()
                            .map(Object::toString)
                            .collect(Collectors.toList()));
                }
            }
            else {
                info.put("type", domainClassName.replace("Domain", "").toUpperCase());
            }

            info.put("constraints", constraints);
            return info;
        }

        /**
         * Convert a value to string representation for Neo4j storage
         */
        private String convertValueToString(Object value) {
            if (value == null) {
                return "null";
            } else if (value instanceof Collection) {
                return ((Collection<?>) value).stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(",", "[", "]"));
            } else if (value instanceof Map) {
                return value.toString();
            } else {
                return value.toString();
            }
        }

        /**
         * Clear all NounDefinition nodes from Neo4j (useful for rebuilding)
         */
        public void clearAllDefinitions() {
            neo4jService.writeTransaction(tx -> {
                tx.run("MATCH (def:NounDefinition) DETACH DELETE def");
                LOGGER.info("Cleared all NounDefinition nodes from Neo4j");
                return null;
            });
            nounDefinitionsCreated = 0;
        }

        /**
         * Query for a specific NounDefinition in Neo4j
         */
        public Map<String, Object> queryDefinition(String className) {
            return neo4jService.readTransaction(tx -> {
                String query =
                        "MATCH (def:NounDefinition {className: $className}) " +
                                "RETURN def";

                Result result = tx.run(query, Map.of("className", className));

                if (result.hasNext()) {
                    var record = result.next();
                    return record.get("def").asMap();
                }
                return null;
            });
        }

        /**
         * Query definitions by property characteristics
         */
        public List<String> queryDefinitionsByPropertyType(String propertyName, String domainType) {
            return neo4jService.readTransaction(tx -> {
                // This query looks for definitions that have a specific property name
                String query =
                        "MATCH (def:NounDefinition) " +
                                "WHERE $propertyName IN def.propertyNames " +
                                "RETURN def.className as className";

                Result result = tx.run(query, Map.of("propertyName", propertyName));

                List<String> classNames = new ArrayList<>();
                while (result.hasNext()) {
                    classNames.add(result.next().get("className").asString());
                }
                return classNames;
            });
        }

        /**
         * Create an index on className for better query performance
         */
        public void createIndexes() {
            neo4jService.writeTransaction(tx -> {
                tx.run("CREATE INDEX noun_def_class IF NOT EXISTS FOR (n:NounDefinition) ON (n.className)");
                tx.run("CREATE INDEX noun_def_props IF NOT EXISTS FOR (n:NounDefinition) ON (n.propertyNames)");
                LOGGER.info("Created indexes for NounDefinition nodes");
                return null;
            });
        }

        /**
         * Get statistics about stored definitions
         */
        public Map<String, Object> getStatistics() {
            return neo4jService.readTransaction(tx -> {
                Map<String, Object> stats = new HashMap<>();

                // Count NounDefinitions
                Result defCountResult = tx.run("MATCH (n:NounDefinition) RETURN count(n) as count");
                stats.put("totalDefinitions", defCountResult.single().get("count").asLong());

                // Get average property count
                Result avgResult = tx.run(
                        "MATCH (def:NounDefinition) " +
                                "RETURN avg(def.propertyCount) as avgProperties, " +
                                "       max(def.propertyCount) as maxProperties, " +
                                "       min(def.propertyCount) as minProperties"
                );
                var record = avgResult.single();
                stats.put("avgPropertiesPerDefinition", record.get("avgProperties").asDouble());
                stats.put("maxProperties", record.get("maxProperties").asLong());
                stats.put("minProperties", record.get("minProperties").asLong());

                // Count total unique property names across all definitions
                Result uniquePropsResult = tx.run(
                        "MATCH (def:NounDefinition) " +
                                "UNWIND def.propertyNames as propName " +
                                "RETURN count(DISTINCT propName) as uniquePropertyNames"
                );
                stats.put("uniquePropertyNames", uniquePropsResult.single().get("uniquePropertyNames").asLong());

                return stats;
            });
        }
    }
}