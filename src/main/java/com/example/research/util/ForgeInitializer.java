package com.example.research.util;

import com.example.research.mtg_commons;
import forge.*;
import forge.util.Localizer;
import forge.util.CardStorageReader;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * ForgeInitializer - Manages Forge environment initialization
 *
 * Core Responsibilities:
 * - Directory structure setup
 * - Localization configuration
 * - StaticData initialization
 * - Resource management
 */
public class ForgeInitializer {

    // Directory paths
    private final String cardDataPath;
    private final String resourcePath;
    private final String editionsPath;
    private final String blockDataPath;
    private final String languagesPath;
    private final String cachePath;

    // Initialization state
    private boolean initialized = false;
    private StaticData staticData;

    // Configuration
    private final InitializationConfig config;

    /**
     * Default constructor
     */
    public ForgeInitializer() {
        this(mtg_commons.DEFAULT_CARD_DATA_PATH, mtg_commons.DEFAULT_RESOURCE_PATH);
    }

    /**
     * Constructor with custom paths
     */
    public ForgeInitializer(String cardDataPath, String resourcePath) {
        this(cardDataPath, resourcePath, new InitializationConfig());
    }

    /**
     * Full constructor
     */
    public ForgeInitializer(String cardDataPath, String resourcePath, InitializationConfig config) {
        this.cardDataPath = cardDataPath;
        this.resourcePath = resourcePath;
        this.config = config;

        // Derive paths
        this.editionsPath = resourcePath + "/editions";
        this.blockDataPath = resourcePath + "/blockdata";
        this.languagesPath = resourcePath + "/languages";
        this.cachePath = "cache";
    }

    /**
     * Initialize complete Forge environment
     */
    public void initializeEnvironment() throws InitializationException {
        if (initialized) {
            System.out.println("⚠️  Forge environment already initialized");
            return;
        }

        System.out.println("🔧 Initializing Forge environment...");

        try {
            // Step 1: Setup directory structure
            setupDirectoryStructure();

            // Step 2: Initialize localization
            initializeLocalization();

            // Step 3: Initialize image keys
            initializeImageKeys();

            // Step 4: Initialize static data
            initializeStaticData();

            initialized = true;
            System.out.println("✅ Forge environment initialized successfully");

        } catch (Exception e) {
            System.err.println("❌ Failed to initialize Forge environment: " + e.getMessage());
            throw new InitializationException("Forge initialization failed", e);
        }
    }

    /**
     * Setup required directory structure
     */
    private void setupDirectoryStructure() throws IOException {
        System.out.println("📁 Setting up directory structure...");

        // Core directories
        List<String> requiredDirs = Arrays.asList(
                resourcePath,
                cardDataPath,
                editionsPath,
                blockDataPath,
                blockDataPath + "/formats",
                languagesPath,
                cachePath,
                cachePath + "/pics",
                cachePath + "/pics/cards",
                cachePath + "/pics/tokens",
                cachePath + "/pics/icons",
                cachePath + "/pics/boosters",
                cachePath + "/pics/fatpacks",
                cachePath + "/pics/boosterboxes",
                cachePath + "/pics/precons",
                cachePath + "/pics/tournamentpacks",
                cachePath + "/layouts",
                cachePath + "/tokens"
        );

        // Create directories
        for (String dir : requiredDirs) {
            Path path = Paths.get(dir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                System.out.println("  ✓ Created directory: " + dir);
            }
        }

        // Initialize basic files if needed
        if (config.createBasicFiles) {
            createBasicFiles();
        }

        System.out.println("✅ Directory structure ready");
    }

    /**
     * Initialize localization system
     */
    private void initializeLocalization() throws InitializationException {
        System.out.println("🌐 Initializing localization...");

        try {
            // Initialize Localizer
            Localizer localizer = Localizer.getInstance();
            localizer.initialize(config.locale, languagesPath);
            System.out.println("  ✓ Localizer initialized with locale: " + config.locale);

        } catch (Exception e) {
            // Try fallback initialization
            System.out.println("  ⚠️  Primary localization failed, trying fallback...");
            try {
                Localizer.getInstance();
                System.out.println("  ✓ Basic Localizer instance created");
            } catch (Exception e2) {
                throw new InitializationException("Localizer initialization failed", e2);
            }
        }

        // Initialize Lang
        try {
            Lang.createInstance(config.locale);
            System.out.println("  ✓ Lang instance created");
        } catch (Exception e) {
            throw new InitializationException("Lang initialization failed", e);
        }

        System.out.println("✅ Localization initialized");
    }

    /**
     * Initialize image key mappings
     */
    private void initializeImageKeys() throws InitializationException {
        System.out.println("🖼️  Initializing ImageKeys...");

        try {
            // Prepare subdirectory mappings
            Map<String, String> cardSubdirs = new HashMap<>();
            if (config.defaultEdition != null) {
                cardSubdirs.put(config.defaultEdition, config.defaultEdition);
            }

            // Initialize ImageKeys with paths
            ImageKeys.initializeDirs(
                    cachePath + "/pics/cards/",
                    cardSubdirs,
                    cachePath + "/pics/tokens/",
                    cachePath + "/pics/icons/",
                    cachePath + "/pics/boosters/",
                    cachePath + "/pics/fatpacks/",
                    cachePath + "/pics/boosterboxes/",
                    cachePath + "/pics/precons/",
                    cachePath + "/pics/tournamentpacks/"
            );

            System.out.println("✅ ImageKeys initialized");

        } catch (Exception e) {
            throw new InitializationException("ImageKeys initialization failed", e);
        }
    }

    /**
     * Initialize StaticData with card database
     */
    private void initializeStaticData() throws InitializationException {
        System.out.println("📚 Initializing StaticData...");

        try {
            // Create card reader
            CardStorageReader cardReader = new CardStorageReader(
                    cardDataPath,
                    config.progressMonitor,
                    config.ignoreInvalidCards
            );

            // Create StaticData instance
            staticData = new StaticData(
                    cardReader,
                    null,  // token reader
                    editionsPath,
                    editionsPath,
                    blockDataPath,
                    config.cardArtPreference,
                    config.enableUnknownCards,
                    config.loadNonLegalCards
            );

            // Verify initialization
            int cardCount = staticData.getCommonCards().getAllCards().size();
            System.out.println("  ✓ Loaded " + cardCount + " cards");

            if (cardCount == 0 && !config.allowEmptyDatabase) {
                throw new InitializationException("No cards loaded - check card data path");
            }

            System.out.println("✅ StaticData initialized");

        } catch (Exception e) {
            throw new InitializationException("StaticData initialization failed", e);
        }
    }

    /**
     * Create basic required files
     */
    private void createBasicFiles() throws IOException {
        // Language file
        createLanguageFile();

        // Format file
        createFormatFile();

        // Edition file
        if (config.defaultEdition != null) {
            createEditionFile(config.defaultEdition);
        }
    }

    /**
     * Create basic language file
     */
    private void createLanguageFile() throws IOException {
        File langFile = new File(languagesPath, config.locale + ".txt");

        if (!langFile.exists()) {
            try (PrintWriter writer = new PrintWriter(langFile)) {
                writer.println("# Basic language file for Forge");
                writer.println("# Generated by ForgeInitializer");
                writer.println();

                // Basic labels
                String[] labels = {
                        "lblName=Name",
                        "lblType=Type",
                        "lblCost=Cost",
                        "lblPower=Power",
                        "lblToughness=Toughness",
                        "lblLibrary=Library",
                        "lblHand=Hand",
                        "lblBattlefield=Battlefield",
                        "lblGraveyard=Graveyard",
                        "lblExile=Exile",
                        "lblStack=Stack",
                        "lblCommand=Command",
                        "lblAnte=Ante",
                        "lblSideboard=Sideboard",
                        "lblDraw=Draw",
                        "lblDiscard=Discard",
                        "lblSacrifice=Sacrifice",
                        "lblDestroy=Destroy",
                        "lblExile=Exile",
                        "lblCounter=Counter",
                        "lblToken=Token",
                        "lblEmblem=Emblem"
                };

                for (String label : labels) {
                    writer.println(label);
                }
            }
            System.out.println("  ✓ Created language file: " + langFile.getName());
        }
    }

    /**
     * Create basic format file
     */
    private void createFormatFile() throws IOException {
        File formatDir = new File(blockDataPath, "formats");
        formatDir.mkdirs();

        File formatFile = new File(formatDir, "Constructed.txt");

        if (!formatFile.exists()) {
            try (PrintWriter writer = new PrintWriter(formatFile)) {
                writer.println("Name:Constructed");
                writer.println("Type:Constructed");
                writer.println("Subtype:Custom");
                writer.println("Sets:" + (config.defaultEdition != null ? config.defaultEdition : ""));
                writer.println("Banned:");
                writer.println("Restricted:");
            }
            System.out.println("  ✓ Created format file: Constructed.txt");
        }
    }

    /**
     * Create edition file
     */
    private void createEditionFile(String editionCode) throws IOException {
        File editionFile = new File(editionsPath, editionCode + ".txt");

        if (!editionFile.exists()) {
            try (PrintWriter writer = new PrintWriter(editionFile)) {
                writer.println("[metadata]");
                writer.println("Code=" + editionCode);
                writer.println("Date=" + new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date()));
                writer.println("Name=" + editionCode + " Set");
                writer.println("Type=Other");
                writer.println();
                writer.println("[cards]");
                writer.println("# Card entries will be added here");
            }
            System.out.println("  ✓ Created edition file: " + editionFile.getName());
        }
    }

    /**
     * Cleanup and shutdown
     */
    public void cleanup() {
        if (!initialized) {
            return;
        }

        System.out.println("🧹 Cleaning up Forge environment...");

        // Clear static references
        staticData = null;

        // Cleanup singleton instances if needed
        // Note: Forge singletons are typically not cleaned up

        initialized = false;
        System.out.println("✅ Forge environment cleaned up");
    }

    /**
     * Check if environment is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get StaticData instance
     */
    public StaticData getStaticData() {
        if (!initialized) {
            throw new IllegalStateException("Forge environment not initialized");
        }
        return staticData;
    }

    /**
     * Get card count
     */
    public int getCardCount() {
        if (staticData != null && staticData.getCommonCards() != null) {
            return staticData.getCommonCards().getAllCards().size();
        }
        return 0;
    }

    /**
     * Initialization configuration
     */
    public static class InitializationConfig {
        // Localization
        public String locale = "en-US";

        // Card loading
        public boolean ignoreInvalidCards = false;
        public boolean enableUnknownCards = false;
        public boolean loadNonLegalCards = true;
        public String cardArtPreference = "LATEST_ART_ALL_EDITIONS";
        public IProgressMonitor progressMonitor = null;

        // File creation
        public boolean createBasicFiles = true;
        public String defaultEdition = "TUTORIAL";

        // Validation
        public boolean allowEmptyDatabase = false;

        /**
         * Create default configuration
         */
        public static InitializationConfig createDefault() {
            return new InitializationConfig();
        }

        /**
         * Create minimal configuration
         */
        public static InitializationConfig createMinimal() {
            InitializationConfig config = new InitializationConfig();
            config.createBasicFiles = false;
            config.allowEmptyDatabase = true;
            return config;
        }

        /**
         * Create tutorial configuration
         */
        public static InitializationConfig createTutorial() {
            InitializationConfig config = new InitializationConfig();
            config.defaultEdition = "TUTORIAL";
            config.createBasicFiles = true;
            return config;
        }
    }

    /**
     * Custom initialization exception
     */
    public static class InitializationException extends Exception {
        public InitializationException(String message) {
            super(message);
        }

        public InitializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}