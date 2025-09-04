package com.example.research;

import forge.CardStorageReader;
import forge.ImageKeys;
import forge.StaticData;
import forge.util.Lang;
import forge.util.Localizer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Improved ForgeApp with configurable paths, better error handling,
 * and enhanced initialization options.
 */
public class ForgeApp {

    // Configuration constants
    private static final String DEFAULT_LANGUAGE = "en-US";
    private static final String CONFIG_FILE = "forge-config.properties";

    // Configurable paths
    private final String cardDataDir;
    private final String resDir;
    private final String cacheDir;
    private final String languageDir;
    private final String editionsDir;
    private final String blockDataDir;

    // Initialization options
    private final boolean createMissingFiles;
    private final boolean verboseLogging;

    /**
     * Default constructor using standard configuration
     */
    public ForgeApp() {
        this(loadConfiguration());
    }

    /**
     * Constructor with custom configuration
     */
    public ForgeApp(ForgeConfig config) {
        this.cardDataDir = config.cardDataDir;
        this.resDir = config.resDir;
        this.cacheDir = config.cacheDir;
        this.languageDir = config.languageDir;
        this.editionsDir = config.editionsDir;
        this.blockDataDir = config.blockDataDir;
        this.createMissingFiles = config.createMissingFiles;
        this.verboseLogging = config.verboseLogging;

        initializeForgeEnvironment();
    }

    /**
     * Configuration class for ForgeApp
     */
    public static class ForgeConfig {
        //public String cardDataDir = "D:\\my_files\\cards";
        public String cardDataDir = "D:\\my_files\\cards";
        public String resDir = "res";
        public String cacheDir = "cache";
        public String languageDir = "res/languages";
        public String editionsDir = "res/editions";
        public String blockDataDir = "res/blockdata";
        public boolean createMissingFiles = true;
        public boolean verboseLogging = true;

        public static ForgeConfig withCustomCardDir(String cardDir) {
            ForgeConfig config = new ForgeConfig();
            config.cardDataDir = cardDir;
            return config;
        }

        public static ForgeConfig silent() {
            ForgeConfig config = new ForgeConfig();
            config.verboseLogging = false;
            return config;
        }
    }

    /**
     * Load configuration from properties file or use defaults
     */
    private static ForgeConfig loadConfiguration() {
        ForgeConfig config = new ForgeConfig();

        try {
            File configFile = new File(CONFIG_FILE);
            if (configFile.exists()) {
                Properties props = new Properties();
                props.load(Files.newBufferedReader(configFile.toPath()));

                config.cardDataDir = props.getProperty("forge.cards.dir", config.cardDataDir);
                config.resDir = props.getProperty("forge.res.dir", config.resDir);
                config.cacheDir = props.getProperty("forge.cache.dir", config.cacheDir);
                config.createMissingFiles = Boolean.parseBoolean(
                        props.getProperty("forge.create.missing.files", "true"));
                config.verboseLogging = Boolean.parseBoolean(
                        props.getProperty("forge.verbose.logging", "true"));

                if (config.verboseLogging) {
                    System.out.println("✓ Loaded configuration from " + CONFIG_FILE);
                }
            }
        } catch (IOException e) {
            System.out.println("⚠️  Could not load config file, using defaults: " + e.getMessage());
        }

        return config;
    }

    /**
     * Save current configuration to properties file
     */
    public void saveConfiguration() {
        try {
            Properties props = new Properties();
            props.setProperty("forge.cards.dir", cardDataDir);
            props.setProperty("forge.res.dir", resDir);
            props.setProperty("forge.cache.dir", cacheDir);
            props.setProperty("forge.create.missing.files", String.valueOf(createMissingFiles));
            props.setProperty("forge.verbose.logging", String.valueOf(verboseLogging));

            try (PrintWriter writer = new PrintWriter(CONFIG_FILE)) {
                props.store(writer, "Forge Research Configuration");
            }

            if (verboseLogging) {
                System.out.println("✓ Configuration saved to " + CONFIG_FILE);
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to save configuration: " + e.getMessage());
        }
    }

    private void initializeForgeEnvironment() {
        try {
            setupForgeDirectories();
            setupLocalization();
            initializeStaticData();

            initializeNeo4jConnection();

            log("✓ Forge environment initialized successfully");

        } catch (Exception e) {
            System.err.println("❌ Failed to initialize Forge environment: " + e.getMessage());
            throw new RuntimeException("Forge initialization failed", e);
        }
    }

    /**
     * Initialize Neo4J database connection using configuration from properties files
     */
    private static void initializeNeo4jConnection() {
        try {
            // First, load the template configuration
            Properties configProps = new Properties();
            Path configPath = Paths.get("res/neo4j/forge-config.properties");

            if (!Files.exists(configPath)) {
                System.err.println("Neo4J config file not found at: " + configPath);
                return;
            }

            try (InputStream configStream = new FileInputStream(configPath.toFile())) {
                configProps.load(configStream);
            }

            // Check if Neo4J is enabled
            boolean enabled = Boolean.parseBoolean(configProps.getProperty("neo4j.enabled", "false"));
            if (!enabled) {
                System.out.println("Neo4J integration is disabled in configuration");
                return;
            }

            // Now load the actual credentials from the credentials file
            Properties credentialsProps = new Properties();
            Path credentialsPath = Paths.get("res/neo4j/Neo4j-bf31f3ea-Created-2025-07-30.txt");

            if (!Files.exists(credentialsPath)) {
                System.err.println("Neo4J credentials file not found at: " + credentialsPath);
                return;
            }

            // Parse the credentials file (it's in KEY=VALUE format)
            try {
                for (String line : Files.readAllLines(credentialsPath)) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    int equalsIndex = line.indexOf('=');
                    if (equalsIndex > 0) {
                        String key = line.substring(0, equalsIndex).trim();
                        String value = line.substring(equalsIndex + 1).trim();
                        credentialsProps.setProperty(key, value);
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to read credentials file: " + e.getMessage());
                return;
            }

            // Create Neo4J configuration using the actual credentials
            Neo4jService.Neo4jConfig neo4jConfig = Neo4jService.Neo4jConfig.defaultConfig()
                    .withUri(credentialsProps.getProperty("NEO4J_URI", configProps.getProperty("neo4j.uri")))
                    .withAuth(
                            credentialsProps.getProperty("NEO4J_USERNAME", configProps.getProperty("neo4j.username")),
                            credentialsProps.getProperty("NEO4J_PASSWORD", configProps.getProperty("neo4j.password"))
                    )
                    .withDatabase(credentialsProps.getProperty("NEO4J_DATABASE", configProps.getProperty("neo4j.database")));

            // Initialize the Neo4J service
            Neo4jService.initialize(neo4jConfig);

            System.out.println("✓ Neo4J connection initialized successfully");
            System.out.println("  Connected to: " + credentialsProps.getProperty("NEO4J_URI"));
            System.out.println("  Database: " + credentialsProps.getProperty("NEO4J_DATABASE"));
            System.out.println("  Instance: " + credentialsProps.getProperty("AURA_INSTANCENAME", "Unknown"));

        } catch (Exception e) {
            System.err.println("❌ Failed to initialize Neo4J connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupForgeDirectories() {
        log("📁 Setting up Forge directory structure...");

        try {
            String[] requiredDirs = {
                    resDir,
                    cardDataDir,
                    editionsDir,
                    blockDataDir,
                    blockDataDir + "/formats",
                    languageDir,
                    cacheDir,
                    cacheDir + "/pics",
                    cacheDir + "/layouts",
                    cacheDir + "/tokens"
            };

            for (String dir : requiredDirs) {
                createDirectoryIfNotExists(dir);
            }

            if (createMissingFiles) {
                createLanguageFiles();
            }

            log("✓ Directory structure created");

        } catch (Exception e) {
            System.err.println("❌ Failed to create directories: " + e.getMessage());
            throw new RuntimeException("Directory setup failed", e);
        }
    }

    private void createDirectoryIfNotExists(String dirPath) throws IOException {
        Path path = Paths.get(dirPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            log("  Created directory: " + dirPath);
        }
    }

    private void setupLocalization() {
        log("🌐 Setting up localization...");

        // Try to initialize Localizer with graceful fallback
        boolean localizerInitialized = false;
        try {
            Localizer.getInstance().initialize(DEFAULT_LANGUAGE, languageDir);
            log("✓ Localizer initialized successfully");
            localizerInitialized = true;
        } catch (Exception e) {
            log("⚠️  Localizer initialization failed, trying fallback: " + e.getMessage());
            try {
                Localizer.getInstance();
                log("✓ Basic Localizer instance created");
                localizerInitialized = true;
            } catch (Exception e2) {
                log("⚠️  Basic Localizer also failed: " + e2.getMessage());
            }
        }

        // Initialize Lang instance (more critical)
        try {
            log("🔧 Initializing Lang instance...");
            Lang.createInstance(DEFAULT_LANGUAGE);
            log("✓ Lang instance created successfully");
        } catch (Exception e) {
            if (localizerInitialized) {
                // If Localizer worked but Lang failed, continue with warning
                log("⚠️  Lang initialization failed but Localizer is available: " + e.getMessage());
            } else {
                // Both failed - this is more serious
                System.err.println("❌ Failed to create Lang instance: " + e.getMessage());
                throw new RuntimeException("Lang initialization failed", e);
            }
        }
    }

    private void initializeStaticData() {
        try {
            log("🖼️  Initializing ImageKeys...");
            initializeImageKeys();

            log("📊 Initializing card storage...");
            CardStorageReader cardReader = new CardStorageReader(cardDataDir, null, false);

            StaticData staticData = new StaticData(
                    cardReader,
                    null,  // questReader
                    editionsDir,
                    editionsDir,  // preconstructedDir
                    blockDataDir,
                    "LATEST_ART_ALL_EDITIONS",
                    true,  // enablePrecons
                    true   // enableBoosters
            );

            int cardCount = staticData.getCommonCards().getAllCards().size();
            log("✓ StaticData initialized successfully");
            log("   Available cards: " + cardCount);

            if (cardCount == 0) {
                log("⚠️  No cards found - you may need to populate the cards directory");
            }

        } catch (Exception e) {
            System.err.println("❌ StaticData initialization failed: " + e.getMessage());
            if (verboseLogging) {
                e.printStackTrace();
            }
            throw new RuntimeException("Failed to initialize StaticData", e);
        }
    }

    private void initializeImageKeys() {
        try {
            // Create image subdirectories
            String[] imageDirs = {
                    cacheDir + "/pics",
                    cacheDir + "/pics/cards",
                    cacheDir + "/pics/tokens",
                    cacheDir + "/pics/icons",
                    cacheDir + "/pics/boosters",
                    cacheDir + "/pics/fatpacks",
                    cacheDir + "/pics/boosterboxes",
                    cacheDir + "/pics/precons",
                    cacheDir + "/pics/tournamentpacks"
            };

            for (String dir : imageDirs) {
                createDirectoryIfNotExists(dir);
            }

            // Initialize ImageKeys with proper paths
            Map<String, String> cardSubdirs = new HashMap<>();
            cardSubdirs.put("TUTORIAL", "TUTORIAL");

            ImageKeys.initializeDirs(
                    cacheDir + "/pics/cards/",
                    cardSubdirs,
                    cacheDir + "/pics/tokens/",
                    cacheDir + "/pics/icons/",
                    cacheDir + "/pics/boosters/",
                    cacheDir + "/pics/fatpacks/",
                    cacheDir + "/pics/boosterboxes/",
                    cacheDir + "/pics/precons/",
                    cacheDir + "/pics/tournamentpacks/"
            );

            log("✓ ImageKeys initialized successfully");

        } catch (Exception e) {
            System.err.println("❌ ImageKeys initialization failed: " + e.getMessage());
            throw new RuntimeException("Failed to initialize ImageKeys", e);
        }
    }

    private void createLanguageFiles() throws IOException {
        File langDir = new File(languageDir);
        File langFile = new File(langDir, DEFAULT_LANGUAGE + ".txt");

        if (!langFile.exists()) {
            log("📝 Creating language file: " + langFile.getPath());

            try (PrintWriter writer = new PrintWriter(langFile)) {
                writer.println("# Basic language file for Forge Research");
                writer.println("# Generated automatically by ForgeApp");
                writer.println();

                // Essential UI labels
                writer.println("lblName=Name");
                writer.println("lblType=Type");
                writer.println("lblCost=Cost");
                writer.println("lblPower=Power");
                writer.println("lblToughness=Toughness");

                // Zone labels
                writer.println("lblLibrary=Library");
                writer.println("lblHand=Hand");
                writer.println("lblBattlefield=Battlefield");
                writer.println("lblGraveyard=Graveyard");
                writer.println("lblExile=Exile");
                writer.println("lblStack=Stack");
                writer.println("lblCommand=Command");
                writer.println("lblAnte=Ante");
                writer.println("lblSideboard=Sideboard");

                // Phase labels
                writer.println("lblUntapStep=Untap Step");
                writer.println("lblUpkeepStep=Upkeep Step");
                writer.println("lblDrawStep=Draw Step");
                writer.println("lblMainPhase=Main Phase");
                writer.println("lblCombatPhase=Combat Phase");
                writer.println("lblEndStep=End Step");
                writer.println("lblCleanupStep=Cleanup Step");

                // Common game terms
                writer.println("lblTurn=Turn");
                writer.println("lblPhase=Phase");
                writer.println("lblStep=Step");
                writer.println("lblPlayer=Player");
                writer.println("lblOpponent=Opponent");
            }

            log("✓ Language file created");
        }
    }

    /**
     * Utility method for conditional logging
     */
    private void log(String message) {
        if (verboseLogging) {
            System.out.println(message);
        }
    }

    /**
     * Get the current configuration
     */
    public ForgeConfig getConfiguration() {
        ForgeConfig config = new ForgeConfig();
        config.cardDataDir = this.cardDataDir;
        config.resDir = this.resDir;
        config.cacheDir = this.cacheDir;
        config.languageDir = this.languageDir;
        config.editionsDir = this.editionsDir;
        config.blockDataDir = this.blockDataDir;
        config.createMissingFiles = this.createMissingFiles;
        config.verboseLogging = this.verboseLogging;
        return config;
    }

    /**
     * Check if the Forge environment is properly initialized
     */
    public boolean isInitialized() {
        try {
            return StaticData.instance() != null &&
                    StaticData.instance().getCommonCards() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get statistics about the initialized environment
     */
    public void printEnvironmentInfo() {
        System.out.println("\n📊 Forge Environment Information:");
        System.out.println("  Card Data Directory: " + cardDataDir);
        System.out.println("  Resource Directory: " + resDir);
        System.out.println("  Cache Directory: " + cacheDir);

        if (isInitialized()) {
            int cardCount = StaticData.instance().getCommonCards().getAllCards().size();
            System.out.println("  Total Cards Available: " + cardCount);
            System.out.println("  Status: ✓ Initialized");
        } else {
            System.out.println("  Status: ❌ Not Initialized");
        }
    }
}