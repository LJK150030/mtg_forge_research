package com.example.research;


import forge.CardStorageReader;
import forge.ImageKeys;
import forge.StaticData;
import forge.util.Lang;
import forge.util.Localizer;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class ForgeApp {

    public ForgeApp(){
        initializeForgeEnvironment();
    }

    private void initializeForgeEnvironment() {
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

    private void setupForgeDirectories() {
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

            //createBasicCards();
            //createTutorialCards();
            //createEditionFiles();
            //createFormatFiles();
            createLanguageFiles();

            System.out.println("✓ Directory structure created");
        } catch (Exception e) {
            System.err.println("❌ Failed to create directories: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void setupLocalization() {
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

    private void initializeStaticData() {
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

    private void initializeImageKeys() {
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

    private void createLanguageFiles() throws IOException {
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
}

