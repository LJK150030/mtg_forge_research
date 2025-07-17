package com.example.research;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import forge.*;
import forge.card.ColorSet;
import forge.card.ICardFace;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.deck.*;
import forge.game.*;
import forge.game.ability.effects.RollDiceEffect;
import forge.game.combat.Combat;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostPartMana;
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
 * Fixed ForgeTest with proper initialization sequence
 */
public class ForgeTest {

    public static void main(String[] args) {
        System.out.println("🎮 Starting Forge Match Test with Tutorial Decks");

        try {
            // Step 1: Initialize Forge environment in correct order
            System.out.println("🔧 Initializing Forge environment...");
            initializeForgeEnvironment();

            // Step 2: Create tutorial decks with actual cards
            System.out.println("🏗️  Creating tutorial decks...");
            Deck catsDeck = createCatsDeck();
            Deck vampiresDeck = createVampiresDeck();

            // Step 3: Run the match
            runMatch(catsDeck, vampiresDeck);

        } catch (Exception e) {
            System.err.println("❌ Error setting up match: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void initializeForgeEnvironment() {
        try {
            // Step 1: Create directory structure first
            setupForgeDirectories();

            // Step 2: Initialize localization properly
            setupLocalization();

            // Step 3: Initialize StaticData with proper card data
            System.out.println("🔧 Initializing StaticData...");
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
            // Initialize the Localizer properly
            Localizer.getInstance().initialize("en-US", "res/languages");
            System.out.println("✓ Localizer initialized successfully");
        } catch (Exception e) {
            System.out.println("⚠️  Localizer initialization failed, continuing with defaults: " + e.getMessage());
            // Try basic initialization
            try {
                Localizer.getInstance();
                System.out.println("✓ Basic Localizer instance created");
            } catch (Exception e2) {
                System.out.println("⚠️  Basic Localizer failed too: " + e2.getMessage());
            }
        }

        // CRITICAL: Initialize Lang instance separately
        try {
            System.out.println("🔧 Initializing Lang instance...");
            Lang.createInstance("en-US");
            System.out.println("✓ Lang instance created successfully");
        } catch (Exception e) {
            System.err.println("❌ Failed to create Lang instance: " + e.getMessage());
            throw new RuntimeException("Lang initialization failed", e);
        }

        System.out.println("✓ Localization setup complete");
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

            // Create basic card definitions
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
        // Create a basic language file to prevent Lang initialization issues
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

        // Create basic lands with proper Oracle text
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

        // CATS DECK CARDS - CORRECTED TO MATCH TABLE DATA

        // Savannah Lions - Verified correct
        createCard(cardsDir, "savannah_lions", "Savannah Lions", "W", "Creature Cat",
                "PT:2/1",
                "Savannah Lions");

        // CORRECTED: Leonin Skyhunter - Fixed mana cost
        createCard(cardsDir, "leonin_skyhunter", "Leonin Skyhunter", "WW", "Creature Cat Knight",
                "PT:2/2\nK:Flying",
                "Flying");

        // CORRECTED: Prideful Parent - Fixed mana cost
        createCard(cardsDir, "prideful_parent", "Prideful Parent", "2W", "Creature Cat",
                "PT:2/2\n" +
                        "K:Vigilance\n" +
                        "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | " +
                        "Execute$ TrigToken | TriggerDescription$ When CARDNAME enters the battlefield, create a 1/1 white Cat creature token.\n" +
                        "SVar:TrigToken:DB$ Token | TokenAmount$ 1 | TokenScript$ w_1_1_cat | TokenOwner$ You | " +
                        "TokenColors$ White | TokenTypes$ Creature,Cat | TokenPower$ 1 | TokenToughness$ 1",
                "Vigilance (Attacking doesn't cause this creature to tap.)\nWhen this creature enters, create a 1/1 white Cat creature token.");

        // CORRECTED: Felidar Savior - Fixed mana cost
        createCard(cardsDir, "felidar_savior", "Felidar Savior", "3W", "Creature Cat Beast",
                "PT:2/3\nK:Lifelink\nT:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | Execute$ TrigCounters | OptionalDecider$ You | TriggerDescription$ When CARDNAME enters the battlefield, put a +1/+1 counter on each of up to two other target creatures you control.\nSVar:TrigCounters:DB$ PutCounter | ValidTgts$ Creature.YouCtrl+Other | TgtPrompt$ Select up to two other creatures you control | TargetMin$ 0 | TargetMax$ 2 | CounterType$ P1P1 | CounterNum$ 1",
                "Lifelink (Damage dealt by this creature also causes you to gain that much life.)\nWhen this creature enters, put a +1/+1 counter on each of up to two other target creatures you control.");

        // CORRECTED: Jazal Goldmane - Fixed mana cost
        createCard(cardsDir, "jazal_goldmane", "Jazal Goldmane", "2WW", "Legendary Creature Cat Warrior",
                "PT:4/4\nK:First Strike\nA:AB$ PumpAll | Cost$ 3 W W | ValidCards$ Creature.attacking+YouCtrl | NumAtt$ X | NumDef$ X | References$ X | SpellDescription$ Attacking creatures you control get +X/+X until end of turn, where X is the number of attacking creatures.\nSVar:X:Count$Valid Creature.attacking+YouCtrl",
                "First strike (This creature deals combat damage before creatures without first strike.)\n{3}{W}{W}: Attacking creatures you control get +X/+X until end of turn, where X is the number of attacking creatures.");

        // CORRECTED: Angelic Edict - Fixed mana cost and effect
        createCard(cardsDir, "angelic_edict", "Angelic Edict", "4W", "Sorcery",
                "A:SP$ ChangeZone | Cost$ 4 W | ValidTgts$ Creature,Enchantment | TgtPrompt$ Select target creature or enchantment | Origin$ Battlefield | Destination$ Exile | SpellDescription$ Exile target creature or enchantment.",
                "Exile target creature or enchantment.");

        // CORRECTED: Pacifism - Fixed enchant syntax
        createCard(cardsDir, "pacifism", "Pacifism", "1W", "Enchantment Aura",
                "K:Enchant:creature\nS:Mode$ Continuous | Affected$ Creature.EnchantedBy | AddHiddenKeyword$ HIDDEN CARDNAME can't attack or block. | Description$ Enchanted creature can't attack or block.",
                "Enchant creature\nEnchanted creature can't attack or block.");

        // CORRECTED: Ingenious Leonin - Fixed mana cost
        createCard(cardsDir, "ingenious_leonin", "Ingenious Leonin", "4W", "Creature Cat Soldier",
                "PT:4/4\nA:AB$ PutCounter | Cost$ 3 W | ValidTgts$ Creature.attacking+YouCtrl+Other | TgtPrompt$ Select another target attacking creature you control | CounterType$ P1P1 | CounterNum$ 1 | SubAbility$ DBFirstStrike | SpellDescription$ Put a +1/+1 counter on another target attacking creature you control. If that creature is a Cat, it gains first strike until end of turn.\nSVar:DBFirstStrike:DB$ Pump | Defined$ Targeted | KW$ First Strike | ConditionDefined$ Targeted | ConditionPresent$ Creature.Cat | SpellDescription$ If that creature is a Cat, it gains first strike until end of turn.",
                "{3}{W}: Put a +1/+1 counter on another target attacking creature you control. If that creature is a Cat, it gains first strike until end of turn. (It deals combat damage before creatures without first strike.)");

        // CORRECTED: Helpful Hunter - Fixed mana cost
        createCard(cardsDir, "helpful_hunter", "Helpful Hunter", "1W", "Creature Cat",
                "PT:1/1\nT:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | Execute$ TrigDraw | TriggerDescription$ When CARDNAME enters the battlefield, draw a card.\nSVar:TrigDraw:DB$ Draw | Defined$ You | NumCards$ 1",
                "When this creature enters, draw a card.");

        // Leonin Vanguard - Verified correct
        createCard(cardsDir, "leonin_vanguard", "Leonin Vanguard", "W", "Creature Cat Soldier",
                "PT:1/1\n" +
                        "T:Mode$ Phase | Phase$ BeginCombat | ValidPlayer$ You | CheckSVar$ X | SVarCompare$ GE3 | " +
                        "Execute$ TrigBonus | TriggerDescription$ At the beginning of combat on your turn, " +
                        "if you control three or more creatures, CARDNAME gets +1/+1 until end of turn and you gain 1 life.\n" +
                        "SVar:TrigBonus:DB$ Pump | Defined$ Self | NumAtt$ +1 | NumDef$ +1 | SubAbility$ DBGainLife\n" +
                        "SVar:DBGainLife:DB$ GainLife | Defined$ You | LifeAmount$ 1\n" +
                        "SVar:X:Count$Valid Creature.YouCtrl",
                "At the beginning of combat on your turn, if you control three or more creatures, this creature gets +1/+1 until end of turn and you gain 1 life.");

        // Moment of Triumph - Verified correct
        createCard(cardsDir, "moment_of_triumph", "Moment of Triumph", "W", "Instant",
                "A:SP$ Pump | Cost$ W | ValidTgts$ Creature | TgtPrompt$ Select target creature | NumAtt$ +2 | NumDef$ +2 | SubAbility$ DBGainLife | SpellDescription$ Target creature gets +2/+2 until end of turn. You gain 2 life.\nSVar:DBGainLife:DB$ GainLife | Defined$ You | LifeAmount$ 2",
                "Target creature gets +2/+2 until end of turn. You gain 2 life.");

        // Elspeth's Smite - Verified correct
        createCard(cardsDir, "elspeths_smite", "Elspeth's Smite", "W", "Instant",
                "A:SP$ DealDamage | Cost$ W | ValidTgts$ Creature.attacking,Creature.blocking | TgtPrompt$ Select target attacking or blocking creature | NumDmg$ 3 | RememberTargets$ True | SubAbility$ DBExileReplacement | SpellDescription$ CARDNAME deals 3 damage to target attacking or blocking creature. If that creature would die this turn, exile it instead.\nSVar:DBExileReplacement:DB$ Effect | Name$ Elspeth's Smite Effect | Triggers$ TrigExile | SVars$ ExileDying | Duration$ EndOfTurn\nSVar:TrigExile:Mode$ ChangesZone | Origin$ Battlefield | Destination$ Graveyard | ValidCard$ Card.IsRemembered | Execute$ ExileDying | TriggerDescription$ If the damaged creature would die this turn, exile it instead.\nSVar:ExileDying:DB$ ChangeZone | Defined$ TriggeredCard | Origin$ Graveyard | Destination$ Exile",
                "Elspeth's Smite deals 3 damage to target attacking or blocking creature. If that creature would die this turn, exile it instead.");

        // VAMPIRE DECK CARDS - CORRECTED TO MATCH TABLE DATA

        // CORRECTED: Vampire Interloper - Fixed mana cost
        createCard(cardsDir, "vampire_interloper", "Vampire Interloper", "1B", "Creature Vampire Scout",
                "PT:2/1\nK:Flying\nK:CARDNAME can't block.",
                "Flying\nThis creature can't block.");

        // CORRECTED: Vampire Spawn - Fixed mana cost
        createCard(cardsDir, "vampire_spawn", "Vampire Spawn", "2B", "Creature Vampire",
                "PT:2/3\nT:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | Execute$ TrigLifeDrain | TriggerDescription$ When CARDNAME enters the battlefield, each opponent loses 2 life and you gain 2 life.\nSVar:TrigLifeDrain:DB$ LoseLife | Defined$ Opponent | LifeAmount$ 2 | SubAbility$ DBGainLife\nSVar:DBGainLife:DB$ GainLife | Defined$ You | LifeAmount$ 2",
                "When this creature enters, each opponent loses 2 life and you gain 2 life.");

        // Moment of Craving - Verified correct
        createCard(cardsDir, "moment_of_craving", "Moment of Craving", "1B", "Instant",
                "A:SP$ Pump | Cost$ 1 B | ValidTgts$ Creature | TgtPrompt$ Select target creature | NumAtt$ -2 | NumDef$ -2 | SubAbility$ DBGainLife | SpellDescription$ Target creature gets -2/-2 until end of turn. You gain 2 life.\nSVar:DBGainLife:DB$ GainLife | Defined$ You | LifeAmount$ 2",
                "Target creature gets -2/-2 until end of turn. You gain 2 life.");

        // Highborn Vampire - Verified correct
        createCard(cardsDir, "highborn_vampire", "Highborn Vampire", "3B", "Creature Vampire Warrior",
                "PT:4/3",
                "Highborn Vampire");

        // CORRECTED: Untamed Hunger - Fixed enchant syntax
        createCard(cardsDir, "untamed_hunger", "Untamed Hunger", "2B", "Enchantment Aura",
                "K:Enchant:creature\nS:Mode$ Continuous | Affected$ Creature.EnchantedBy | AddPower$ 2 | AddToughness$ 1 | AddKeyword$ Menace | Description$ Enchanted creature gets +2/+1 and has menace.",
                "Enchant creature\nEnchanted creature gets +2/+1 and has menace. (It can't be blocked except by two or more creatures.)");

        // CORRECTED: Bloodtithe Collector - Fixed mana cost
        createCard(cardsDir, "bloodtithe_collector", "Bloodtithe Collector", "4B", "Creature Vampire Noble",
                "PT:3/4\n" +
                        "K:Flying\n" +
                        "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | " +
                        "CheckSVar$ X | SVarCompare$ GE1 | Execute$ TrigDiscard | " +
                        "TriggerDescription$ When CARDNAME enters the battlefield, if an opponent lost life this turn, each opponent discards a card.\n" +
                        "SVar:TrigDiscard:DB$ Discard | Defined$ Opponent | NumCards$ 1 | Mode$ TgtChoose\n" +
                        "SVar:X:Count$OpponentLostLifeThisTurn",
                "Flying\nWhen this creature enters, if an opponent lost life this turn, each opponent discards a card.");

        // CORRECTED: Crossway Troublemakers - Fixed mana cost
        createCard(cardsDir, "crossway_troublemakers", "Crossway Troublemakers", "5B", "Creature Vampire",
                "PT:5/5\n" +
                        "S:Mode$ Continuous | Affected$ Creature.Vampire+attacking+YouCtrl | AddKeyword$ Deathtouch & Lifelink | " +
                        "Description$ Attacking Vampires you control have deathtouch and lifelink.\n" +
                        "T:Mode$ ChangesZone | Origin$ Battlefield | Destination$ Graveyard | ValidCard$ Creature.Vampire+YouCtrl | " +
                        "Execute$ TrigPayLife | OptionalDecider$ You | TriggerDescription$ Whenever a Vampire you control dies, " +
                        "you may pay 2 life. If you do, draw a card.\n" +
                        "SVar:TrigPayLife:AB$ LoseLife | Cost$ 0 | Defined$ You | LifeAmount$ 2 | " +
                        "UnlessCost$ 0 | UnlessPayer$ You | SubAbility$ DBDraw\n" +
                        "SVar:DBDraw:DB$ Draw | Defined$ You | NumCards$ 1",
                "Attacking Vampires you control have deathtouch and lifelink. (Any amount of damage they deal to a creature is enough to destroy it. Damage dealt by those creatures also causes their controller to gain that much life.)\nWhenever a Vampire you control dies, you may pay 2 life. If you do, draw a card.");

        // CORRECTED: Vengeful Bloodwitch - Fixed mana cost
        createCard(cardsDir, "vengeful_bloodwitch", "Vengeful Bloodwitch", "1B", "Creature Vampire Warlock",
                "PT:1/1\n" +
                        "T:Mode$ ChangesZone | Origin$ Battlefield | Destination$ Graveyard | " +
                        "ValidCard$ Creature.Self,Creature.YouCtrl+Other | Execute$ TrigLifeDrain | " +
                        "TriggerDescription$ Whenever CARDNAME or another creature you control dies, target opponent loses 1 life and you gain 1 life.\n" +
                        "SVar:TrigLifeDrain:DB$ LoseLife | ValidTgts$ Opponent | TgtPrompt$ Select target opponent | " +
                        "LifeAmount$ 1 | SubAbility$ DBGainLife\n" +
                        "SVar:DBGainLife:DB$ GainLife | Defined$ You | LifeAmount$ 1",
                "Whenever this creature or another creature you control dies, target opponent loses 1 life and you gain 1 life.");

        // CORRECTED: Hero's Downfall - Fixed mana cost
        createCard(cardsDir, "heros_downfall", "Hero's Downfall", "1BB", "Instant",
                "A:SP$ Destroy | Cost$ 1 B B | ValidTgts$ Creature,Planeswalker | TgtPrompt$ Select target creature or planeswalker | SpellDescription$ Destroy target creature or planeswalker.",
                "Destroy target creature or planeswalker.");

        // CORRECTED: Vampire Neonate - Fixed power/toughness
        createCard(cardsDir, "vampire_neonate", "Vampire Neonate", "B", "Creature Vampire",
                "PT:0/3\nA:AB$ LoseLife | Cost$ 2 T | ValidTgts$ Player.Opponent | TgtPrompt$ Select target opponent | LifeAmount$ 1 | SubAbility$ DBGainLife | SpellDescription$ Target opponent loses 1 life and you gain 1 life.\nSVar:DBGainLife:DB$ GainLife | Defined$ You | LifeAmount$ 1",
                "{2}, {T}: Each opponent loses 1 life and you gain 1 life.");

        // CORRECTED: Offer Immortality - Fixed mana cost
        createCard(cardsDir, "offer_immortality", "Offer Immortality", "1B", "Instant",
                "A:SP$ Pump | Cost$ 1 B | ValidTgts$ Creature | TgtPrompt$ Select target creature | KW$ Deathtouch & Indestructible | SpellDescription$ Target creature gains deathtouch and indestructible until end of turn.",
                "Target creature gains deathtouch and indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)");

        createCard(cardsDir, "stromkirk_bloodthief", "Stromkirk Bloodthief", "2B", "Creature Vampire Rogue",
                "PT:2/2\n" +
                        "T:Mode$ Phase | Phase$ End of Turn | ValidPlayer$ You | CheckSVar$ X | SVarCompare$ GE1 | " +
                        "Execute$ TrigPutCounter | TriggerDescription$ At the beginning of your end step, if an opponent lost life this turn, " +
                        "put a +1/+1 counter on target Vampire you control.\n" +
                        "SVar:TrigPutCounter:DB$ PutCounter | ValidTgts$ Creature.Vampire+YouCtrl | TgtPrompt$ Select target Vampire you control | " +
                        "CounterType$ P1P1 | CounterNum$ 1\n" +
                        "SVar:X:Count$OpponentLostLifeThisTurn",
                "At the beginning of your end step, if an opponent lost life this turn, put a +1/+1 counter on target Vampire you control.");

        createCard(cardsDir, "uncharted_haven", "Uncharted Haven", "0", "Land",
                "K:CARDNAME enters the battlefield tapped.\n" +
                        "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | " +
                        "Execute$ ChooseColor | TriggerDescription$ As CARDNAME enters the battlefield, choose a color.\n" +
                        "SVar:ChooseColor:DB$ ChooseColor | Defined$ You | AILogic$ MostProminentInComputerHand\n" +
                        "A:AB$ Mana | Cost$ T | Produced$ Chosen | Amount$ 1 | SpellDescription$ Add one mana of the chosen color.",
                "This land enters tapped. As it enters, choose a color.\n{T}: Add one mana of the chosen color.");

        createCard(cardsDir, "w_1_1_cat", "Cat", "", "Token Creature Cat",
                "PT:1/1\n" +
                        "Colors:white",
                "Cat Token");
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
            writer.println("9 C Vampire Spawn");
            writer.println("10 C Leonin Skyhunter");
            writer.println("11 C Prideful Parent");
            writer.println("12 C Felidar Savior");
            writer.println("13 C Jazal Goldmane");
            writer.println("14 C Angelic Edict");
            writer.println("15 C Pacifism");
            writer.println("16 C Moment of Triumph");
            writer.println("17 C Elspeth's Smite");
            writer.println("18 C Highborn Vampire");
            writer.println("19 C Bloodtithe Collector");
            writer.println("20 C Crossway Troublemakers");
            writer.println("21 C Moment of Craving");
            writer.println("22 C Untamed Hunger");
            writer.println("23 C Vengeful Bloodwitch");
            writer.println("24 C Hero's Downfall");
            writer.println("25 C Vampire Neonate");
            writer.println("26 C Offer Immortality");
            writer.println("27 C Stromkirk Bloodthief");
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
            // CRITICAL: Initialize ImageKeys BEFORE StaticData
            System.out.println("🖼️  Initializing ImageKeys...");
            initializeImageKeys();

            // Use the local card data directory we created
            String cardDataDir = "D:/my_files/cards";
            String editionFolder = "res/editions";
            String blockDataFolder = "res/blockdata";

            // Create card storage reader with the local directory
            CardStorageReader cardReader = new CardStorageReader(cardDataDir, null, false);

            // Initialize StaticData with minimal configuration
            StaticData staticData = new StaticData(
                    cardReader,
                    null, // custom card reader
                    editionFolder,
                    editionFolder,
                    blockDataFolder,
                    "LATEST_ART_ALL_EDITIONS",
                    true, // enable unknown cards
                    true  // load non-legal cards
            );

            System.out.println("✓ StaticData initialized successfully");
            System.out.println("   Available cards: " + staticData.getCommonCards().getAllCards().size());

        } catch (Exception e) {
            System.err.println("❌ StaticData initialization failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize StaticData", e);
        }
    }

    /**
     * Initialize ImageKeys with proper directory mapping
     * This MUST be called before StaticData initialization
     */
    private static void initializeImageKeys() {
        try {
            // Create image directories
            String[] imageDirs = {
                    "cache/pics",
                    "cache/pics/cards",
                    "cache/pics/tokens",
                    "cache/pics/icons",
                    "cache/pics/boosters",
                    "cache/pics/fatpacks",
                    "cache/pics/boosterboxes",
                    "cache/pics/precons",
                    "cache/pics/tournamentpacks"
            };

            for (String dir : imageDirs) {
                File directory = new File(dir);
                if (!directory.exists()) {
                    directory.mkdirs();
                }
            }

            // Create subdirectory mapping for card sets
            Map<String, String> cardSubdirs = new HashMap<>();
            cardSubdirs.put("TUTORIAL", "TUTORIAL");
            // Add any additional set mappings here

            // Initialize ImageKeys with proper directory structure
            ImageKeys.initializeDirs(
                    "cache/pics/cards/",      // cards directory
                    cardSubdirs,              // card subdirectories map
                    "cache/pics/tokens/",     // tokens directory
                    "cache/pics/icons/",      // icons directory
                    "cache/pics/boosters/",   // boosters directory
                    "cache/pics/fatpacks/",   // fatpacks directory
                    "cache/pics/boosterboxes/", // booster boxes directory
                    "cache/pics/precons/",    // precons directory
                    "cache/pics/tournamentpacks/" // tournament packs directory
            );

            System.out.println("✓ ImageKeys initialized successfully");

            // Optional: Create sample card images to test the system
            createSampleCardImages();

        } catch (Exception e) {
            System.err.println("❌ ImageKeys initialization failed: " + e.getMessage());
            throw new RuntimeException("Failed to initialize ImageKeys", e);
        }
    }

    /**
     * Create sample card images for testing
     */
    private static void createSampleCardImages() {
        try {
            // Create TUTORIAL set directory
            File tutorialDir = new File("cache/pics/cards/TUTORIAL");
            tutorialDir.mkdirs();

            // Create placeholder image files for key cards
            String[] cardNames = {
                    "Plains", "Island", "Swamp", "Mountain", "Forest",
                    "Savannah Lions", "Vampire Interloper", "Leonin Skyhunter"
            };

            for (String cardName : cardNames) {
                // Create placeholder image file
                File imageFile = new File(tutorialDir, cardName + ".jpg");
                if (!imageFile.exists()) {
                    // Create empty placeholder file
                    imageFile.createNewFile();
                    System.out.println("  Created placeholder: " + imageFile.getPath());
                }
            }

            System.out.println("✓ Sample card images created");

        } catch (Exception e) {
            System.err.println("⚠️  Failed to create sample images: " + e.getMessage());
            // Don't fail the entire initialization for this
        }
    }



    private static Deck createCatsDeck() {
        Deck deck = new Deck("Cats Tutorial Deck");

        // Add cards in tutorial order
        addCardToDeck(deck, "Plains", 4);
        addCardToDeck(deck, "Savannah Lions", 1);
        addCardToDeck(deck, "Leonin Skyhunter", 1);
        addCardToDeck(deck, "Prideful Parent", 1);
        addCardToDeck(deck, "Plains", 2);
        addCardToDeck(deck, "Felidar Savior", 1);
        addCardToDeck(deck, "Plains", 1);
        addCardToDeck(deck, "Angelic Edict", 1);
        addCardToDeck(deck, "Jazal Goldmane", 1);
        addCardToDeck(deck, "Pacifism", 1);
        addCardToDeck(deck, "Moment of Triumph", 1);
        addCardToDeck(deck, "Elspeth's Smite", 1);
        addCardToDeck(deck, "Uncharted Haven", 1);

        // Add more Plains to reach 20 cards
        int currentSize = deck.getMain().countAll();
        addCardToDeck(deck, "Plains", Math.max(0, 20 - currentSize));

        return deck;
    }

    private static Deck createVampiresDeck() {
        Deck deck = new Deck("Vampires Tutorial Deck");

        // Add cards in tutorial order
        addCardToDeck(deck, "Swamp", 4);
        addCardToDeck(deck, "Vampire Interloper", 1);
        addCardToDeck(deck, "Vampire Spawn", 1);
        addCardToDeck(deck, "Moment of Craving", 1);
        addCardToDeck(deck, "Swamp", 2);
        addCardToDeck(deck, "Highborn Vampire", 1);
        addCardToDeck(deck, "Untamed Hunger", 1);
        addCardToDeck(deck, "Bloodtithe Collector", 1);
        addCardToDeck(deck, "Crossway Troublemakers", 1);
        addCardToDeck(deck, "Vengeful Bloodwitch", 1);
        addCardToDeck(deck, "Hero's Downfall", 1);
        addCardToDeck(deck, "Vampire Neonate", 1);
        addCardToDeck(deck, "Offer Immortality", 1);
        addCardToDeck(deck, "Stromkirk Bloodthief", 1);
        addCardToDeck(deck, "Swamp", 1);
        addCardToDeck(deck, "Uncharted Haven", 1);

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

    private static void runMatch(Deck deck1, Deck deck2) {
        try {
            System.out.println("👥 Creating AI players...");

            // Create AI lobby players
            AILobbyPlayer aiPlayer1 = new AILobbyPlayer("Cats AI");
            AILobbyPlayer aiPlayer2 = new AILobbyPlayer("Vampires AI");

            System.out.println("✓ Created AI players");

            // Create registered players
            RegisteredPlayer regPlayer1 = new RegisteredPlayer(deck1);
            RegisteredPlayer regPlayer2 = new RegisteredPlayer(deck2);

            // Link lobby players to registered players
            regPlayer1.setPlayer(aiPlayer1);
            regPlayer2.setPlayer(aiPlayer2);

            List<RegisteredPlayer> players = Arrays.asList(regPlayer1, regPlayer2);
            System.out.println("✓ Created registered players");

            // Create game rules
            GameRules gameRules = new GameRules(GameType.Constructed);
            gameRules.setGamesPerMatch(1);
            System.out.println("✓ Created game rules");

            // Create and start match
            Match match = new Match(gameRules, players, "Tutorial Match");
            System.out.println("✓ Created match");

            // Start game
            Game game = match.createGame();
            System.out.println("✓ Created game");

            // Add event handling to prevent null pointer exceptions
            try {
                match.startGame(game, null);
                System.out.println("🎉 Game started successfully!");
            } catch (Exception e) {
                System.err.println("⚠️  Game start encountered issues: " + e.getMessage());
                System.out.println("🎉 Game created successfully, but may have initialization issues");
            }

            // Display game state
            displayGameState(game);

        } catch (Exception e) {
            System.err.println("❌ Error during match: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void displayGameState(Game game) {
        try {
            System.out.println("📊 Game State:");
            System.out.println("   - Players: " + game.getRegisteredPlayers().size());

            if (game.getPhaseHandler() != null) {
                System.out.println("   - Current Turn: " + game.getPhaseHandler().getTurn());
                System.out.println("   - Current Phase: " + game.getPhaseHandler().getPhase());
            } else {
                System.out.println("   - Phase handler not initialized");
            }

            for (Player player : game.getPlayers()) {
                System.out.println("   - " + player.getName() +
                        " Life: " + player.getLife() +
                        ", Hand: " + player.getCardsIn(ZoneType.Hand).size() +
                        ", Library: " + player.getCardsIn(ZoneType.Library).size());
            }
        } catch (Exception e) {
            System.err.println("❌ Error displaying game state: " + e.getMessage());
        }
    }

    /**
     * Simple AI LobbyPlayer implementation
     */
    static class AILobbyPlayer extends LobbyPlayer implements IGameEntitiesFactory {

        public AILobbyPlayer(String name) {
            super(name);
        }

        @Override
        public void hear(LobbyPlayer player, String message) {
            // Simple implementation - just log the message
            System.out.println("[" + getName() + "] Heard: " + message);
        }

        @Override
        public Player createIngamePlayer(Game game, int id) {
            // Create the player
            Player player = new Player(getName(), game, id);

            // Create a minimal AI controller
            PlayerController controller = new MinimalAIController(game, player, this);

            // Set the controller
            player.setFirstController(controller);

            return player;
        }

        @Override
        public PlayerController createMindSlaveController(Player master, Player slave) {
            // For mind slave effects, return the slave's controller
            return slave.getController();
        }
    }


    /**
     * Minimal AI controller that makes random legal moves
     */
    static class MinimalAIController extends PlayerController {

        private Random random = new Random();

        public MinimalAIController(Game game, Player player, LobbyPlayer lobbyPlayer) {
            super(game, player, lobbyPlayer);
        }

        @Override
        public boolean isAI() {
            return true;
        }

        @Override
        public void resetAtEndOfTurn() {
            // Clean up any turn-specific state
        }

        @Override
        public List<OptionalCostValue> chooseOptionalCosts(SpellAbility spellAbility, List<OptionalCostValue> list) {
            return List.of();
        }

        @Override
        public List<CostPart> orderCosts(List<CostPart> list) {
            return List.of();
        }

        @Override
        public boolean payCostToPreventEffect(Cost cost, SpellAbility spellAbility, boolean b, FCollectionView<Player> fCollectionView) {
            return false;
        }

        @Override
        public boolean payCostDuringRoll(Cost cost, SpellAbility spellAbility, FCollectionView<Player> fCollectionView) {
            return false;
        }

        @Override
        public boolean payCombatCost(Card card, Cost cost, SpellAbility spellAbility, String s) {
            return false;
        }

        @Override
        public boolean payManaCost(ManaCost manaCost, CostPartMana costPartMana, SpellAbility spellAbility, String s, ManaConversionMatrix manaConversionMatrix, boolean b) {
            return false;
        }

        // Implement all abstract methods with basic AI logic
        @Override
        public SpellAbility getAbilityToPlay(Card hostCard, List<SpellAbility> abilities, ITriggerEvent triggerEvent) {
            if (abilities.isEmpty()) return null;
            return abilities.get(random.nextInt(abilities.size()));
        }

        @Override
        public void playSpellAbilityNoStack(SpellAbility effectSA, boolean mayChooseNewTargets) {
            // Just play the spell ability
        }

        @Override
        public void orderAndPlaySimultaneousSa(List<SpellAbility> activePlayerSAs) {
            // Play abilities in order
            for (SpellAbility sa : activePlayerSAs) {
                // Play each ability
            }
        }

        @Override
        public boolean playTrigger(Card host, WrappedAbility wrapperAbility, boolean isMandatory) {
            // Play mandatory triggers, randomly decide on optional ones
            return isMandatory || random.nextBoolean();
        }

        @Override
        public boolean playSaFromPlayEffect(SpellAbility tgtSA) {
            return true; // Usually play the spell
        }

        @Override
        public List<PaperCard> sideboard(Deck deck, GameType gameType, String s) {
            return List.of();
        }

        @Override
        public List<PaperCard> chooseCardsYouWonToAddToDeck(List<PaperCard> list) {
            return List.of();
        }

        @Override
        public Map<Card, Integer> assignCombatDamage(Card card, CardCollectionView cardCollectionView, CardCollectionView cardCollectionView1, int i, GameEntity gameEntity, boolean b) {
            return Map.of();
        }

        @Override
        public Map<GameEntity, Integer> divideShield(Card card, Map<GameEntity, Integer> map, int i) {
            return Map.of();
        }

        @Override
        public Map<Byte, Integer> specifyManaCombo(SpellAbility spellAbility, ColorSet colorSet, int i, boolean b) {
            return Map.of();
        }

        @Override
        public List<SpellAbility> chooseSpellAbilityToPlay() {
            // Return empty list - let the game handle spell selection
            return new ArrayList<>();
        }

        @Override
        public boolean playChosenSpellAbility(SpellAbility spellAbility) {
            return false;
        }

        @Override
        public List<AbilitySub> chooseModeForAbility(SpellAbility spellAbility, List<AbilitySub> list, int i, int i1, boolean b) {
            return List.of();
        }

        @Override
        public int chooseNumberForCostReduction(SpellAbility spellAbility, int i, int i1) {
            return 0;
        }

        @Override
        public int chooseNumberForKeywordCost(SpellAbility spellAbility, Cost cost, KeywordInterface keywordInterface, String s, int i) {
            return 0;
        }

        @Override
        public int chooseNumber(SpellAbility spellAbility, String s, int i, int i1) {
            return 0;
        }

        @Override
        public int chooseNumber(SpellAbility spellAbility, String s, List<Integer> list, Player player) {
            return 0;
        }

        @Override
        public Player chooseStartingPlayer(boolean isFirstGame) {
            // Choose randomly
            List<Player> players = new ArrayList<>(getGame().getPlayers());
            return players.get(random.nextInt(players.size()));
        }

        @Override
        public PlayerZone chooseStartingHand(List<PlayerZone> list) {
            return null;
        }

        @Override
        public Mana chooseManaFromPool(List<Mana> list) {
            return null;
        }

        @Override
        public String chooseSomeType(String s, SpellAbility spellAbility, Collection<String> collection, boolean b) {
            return "";
        }

        @Override
        public String chooseSector(Card card, String s, List<String> list) {
            return "";
        }

        @Override
        public List<Card> chooseContraptionsToCrank(List<Card> list) {
            return List.of();
        }

        @Override
        public int chooseSprocket(Card card, boolean b) {
            return 0;
        }

        @Override
        public PlanarDice choosePDRollToIgnore(List<PlanarDice> list) {
            return null;
        }

        @Override
        public Integer chooseRollToIgnore(List<Integer> list) {
            return 0;
        }

        @Override
        public List<Integer> chooseDiceToReroll(List<Integer> list) {
            return List.of();
        }

        @Override
        public Integer chooseRollToModify(List<Integer> list) {
            return 0;
        }

        @Override
        public RollDiceEffect.DieRollResult chooseRollToSwap(List<RollDiceEffect.DieRollResult> list) {
            return null;
        }

        @Override
        public String chooseRollSwapValue(List<String> list, Integer integer, int i, int i1) {
            return "";
        }

        @Override
        public Object vote(SpellAbility spellAbility, String s, List<Object> list, ListMultimap<Object, Player> listMultimap, Player player, boolean b) {
            return null;
        }

        @Override
        public CardCollectionView choosePermanentsToSacrifice(SpellAbility sa, int min, int max, CardCollectionView valid, String message) {
            CardCollection result = new CardCollection();
            List<Card> options = new ArrayList<>((Collection) valid);
            int toSacrifice = Math.min(max, Math.max(min, random.nextInt(max + 1)));

            for (int i = 0; i < toSacrifice && !options.isEmpty(); i++) {
                Card chosen = options.remove(random.nextInt(options.size()));
                result.add(chosen);
            }

            return result;
        }

        @Override
        public CardCollectionView choosePermanentsToDestroy(SpellAbility spellAbility, int i, int i1, CardCollectionView cardCollectionView, String s) {
            return null;
        }

        @Override
        public Integer announceRequirements(SpellAbility spellAbility, String s) {
            return 0;
        }

        @Override
        public TargetChoices chooseNewTargetsFor(SpellAbility spellAbility, Predicate<GameObject> predicate, boolean b) {
            return null;
        }

        @Override
        public boolean chooseTargetsFor(SpellAbility spellAbility) {
            return false;
        }

        @Override
        public boolean helpPayForAssistSpell(ManaCostBeingPaid manaCostBeingPaid, SpellAbility spellAbility, int i, int i1) {
            return false;
        }

        @Override
        public Player choosePlayerToAssistPayment(FCollectionView<Player> fCollectionView, SpellAbility spellAbility, String s, int i) {
            return null;
        }

        @Override
        public CardCollectionView chooseCardsForEffect(CardCollectionView cardCollectionView, SpellAbility spellAbility, String s, int i, int i1, boolean b, Map<String, Object> map) {
            return null;
        }

        @Override
        public CardCollection chooseCardsForEffectMultiple(Map<String, CardCollection> map, SpellAbility spellAbility, String s, boolean b) {
            return null;
        }

        @Override
        public <T extends GameEntity> T chooseSingleEntityForEffect(FCollectionView<T> fCollectionView, DelayedReveal delayedReveal, SpellAbility spellAbility, String s, boolean b, Player player, Map<String, Object> map) {
            return null;
        }

        @Override
        public <T extends GameEntity> List<T> chooseEntitiesForEffect(FCollectionView<T> fCollectionView, int i, int i1, DelayedReveal delayedReveal, SpellAbility spellAbility, String s, Player player, Map<String, Object> map) {
            return List.of();
        }

        @Override
        public List<SpellAbility> chooseSpellAbilitiesForEffect(List<SpellAbility> list, SpellAbility spellAbility, String s, int i, Map<String, Object> map) {
            return List.of();
        }

        @Override
        public SpellAbility chooseSingleSpellForEffect(List<SpellAbility> list, SpellAbility spellAbility, String s, Map<String, Object> map) {
            return null;
        }

        @Override
        public boolean confirmAction(SpellAbility spellAbility, PlayerActionConfirmMode playerActionConfirmMode, String s, List<String> list, Card card, Map<String, Object> map) {
            return false;
        }

        @Override
        public boolean confirmBidAction(SpellAbility spellAbility, PlayerActionConfirmMode playerActionConfirmMode, String s, int i, Player player) {
            return false;
        }

        @Override
        public boolean confirmReplacementEffect(ReplacementEffect replacementEffect, SpellAbility spellAbility, GameEntity gameEntity, String s) {
            return false;
        }

        @Override
        public boolean confirmStaticApplication(Card card, PlayerActionConfirmMode playerActionConfirmMode, String s, String s1) {
            return false;
        }

        @Override
        public boolean confirmTrigger(WrappedAbility wrappedAbility) {
            return false;
        }

        @Override
        public List<Card> exertAttackers(List<Card> list) {
            return List.of();
        }

        @Override
        public List<Card> enlistAttackers(List<Card> list) {
            return List.of();
        }

        @Override
        public void declareAttackers(Player player, Combat combat) {

        }

        @Override
        public void declareBlockers(Player player, Combat combat) {

        }

        @Override
        public CardCollection orderBlockers(Card card, CardCollection cardCollection) {
            return null;
        }

        @Override
        public CardCollection orderBlocker(Card card, Card card1, CardCollection cardCollection) {
            return null;
        }

        @Override
        public CardCollection orderAttackers(Card card, CardCollection cardCollection) {
            return null;
        }

        @Override
        public void reveal(CardCollectionView cardCollectionView, ZoneType zoneType, Player player, String s, boolean b) {

        }

        @Override
        public void reveal(List<CardView> list, ZoneType zoneType, PlayerView playerView, String s, boolean b) {

        }

        @Override
        public List<SpellAbility> chooseSaToActivateFromOpeningHand(List<SpellAbility> usableFromOpeningHand) {
            // Don't activate from opening hand
            return new ArrayList<>();
        }

        @Override
        public boolean chooseBinary(SpellAbility sa, String question, BinaryChoiceType kindOfChoice, Boolean defaultVal) {
            // Return default or random choice
            return defaultVal != null ? defaultVal : random.nextBoolean();
        }

        @Override
        public boolean chooseFlipResult(SpellAbility sa, Player flipper, boolean[] results, boolean call) {
            // Random choice
            return random.nextBoolean();
        }

        @Override
        public byte chooseColor(String s, SpellAbility spellAbility, ColorSet colorSet) {
            return 0;
        }

        @Override
        public byte chooseColorAllowColorless(String s, Card card, ColorSet colorSet) {
            return 0;
        }

        @Override
        public List<String> chooseColors(String s, SpellAbility spellAbility, int i, int i1, List<String> list) {
            return List.of();
        }

        @Override
        public ICardFace chooseSingleCardFace(SpellAbility spellAbility, String s, Predicate<ICardFace> predicate, String s1) {
            return null;
        }

        @Override
        public ICardFace chooseSingleCardFace(SpellAbility spellAbility, List<ICardFace> list, String s) {
            return null;
        }

        @Override
        public CardState chooseSingleCardState(SpellAbility spellAbility, List<CardState> list, String s, Map<String, Object> map) {
            return null;
        }

        @Override
        public boolean chooseCardsPile(SpellAbility spellAbility, CardCollectionView cardCollectionView, CardCollectionView cardCollectionView1, String s) {
            return false;
        }

        @Override
        public CounterType chooseCounterType(List<CounterType> list, SpellAbility spellAbility, String s, Map<String, Object> map) {
            return null;
        }

        @Override
        public String chooseKeywordForPump(List<String> list, SpellAbility spellAbility, String s, Card card) {
            return "";
        }

        @Override
        public boolean confirmPayment(CostPart costPart, String s, SpellAbility spellAbility) {
            return false;
        }

        @Override
        public ReplacementEffect chooseSingleReplacementEffect(List<ReplacementEffect> list) {
            return null;
        }

        @Override
        public StaticAbility chooseSingleStaticAbility(String s, List<StaticAbility> list) {
            return null;
        }

        @Override
        public String chooseProtectionType(String s, SpellAbility spellAbility, List<String> list) {
            return "";
        }

        @Override
        public void revealAnte(String s, Multimap<Player, PaperCard> multimap) {

        }

        @Override
        public void revealAISkipCards(String s, Map<Player, Map<DeckSection, List<? extends PaperCard>>> map) {

        }

        @Override
        public Pair<SpellAbilityStackInstance, GameObject> chooseTarget(SpellAbility sa, List<Pair<SpellAbilityStackInstance, GameObject>> targets) {
            // Choose randomly
            return targets.isEmpty() ? null : targets.get(random.nextInt(targets.size()));
        }

        @Override
        public void notifyOfValue(SpellAbility sa, GameObject realtedTarget, String value) {
            // Just acknowledge
        }

        @Override
        public ImmutablePair<CardCollection, CardCollection> arrangeForScry(CardCollection cardCollection) {
            return null;
        }

        @Override
        public ImmutablePair<CardCollection, CardCollection> arrangeForSurveil(CardCollection cardCollection) {
            return null;
        }

        @Override
        public boolean willPutCardOnTop(Card card) {
            return false;
        }

        @Override
        public CardCollectionView orderMoveToZoneList(CardCollectionView cardCollectionView, ZoneType zoneType, SpellAbility spellAbility) {
            return null;
        }

        @Override
        public CardCollectionView chooseCardsToDiscardFrom(Player player, SpellAbility spellAbility, CardCollection cardCollection, int i, int i1) {
            return null;
        }

        @Override
        public CardCollectionView chooseCardsToDiscardUnlessType(int i, CardCollectionView cardCollectionView, String s, SpellAbility spellAbility) {
            return null;
        }

        @Override
        public CardCollection chooseCardsToDiscardToMaximumHandSize(int i) {
            return null;
        }

        @Override
        public CardCollectionView chooseCardsToDelve(int i, CardCollection cardCollection) {
            return null;
        }

        @Override
        public Map<Card, ManaCostShard> chooseCardsForConvokeOrImprovise(SpellAbility spellAbility, ManaCost manaCost, CardCollectionView cardCollectionView, boolean b) {
            return Map.of();
        }

        @Override
        public List<Card> chooseCardsForSplice(SpellAbility spellAbility, List<Card> list) {
            return List.of();
        }

        @Override
        public CardCollectionView chooseCardsToRevealFromHand(int i, int i1, CardCollectionView cardCollectionView) {
            return null;
        }

        @Override
        public String chooseCardName(SpellAbility sa, Predicate<ICardFace> cpp, String valid, String message) {
            // Choose a basic card name
            String[] basicNames = {"Plains", "Island", "Swamp", "Mountain", "Forest"};
            return basicNames[random.nextInt(basicNames.length)];
        }

        @Override
        public String chooseCardName(SpellAbility sa, List<ICardFace> faces, String message) {
            // Choose randomly from faces
            return faces.isEmpty() ? "Plains" : faces.get(random.nextInt(faces.size())).getName();
        }

        @Override
        public Card chooseDungeon(Player player, List<PaperCard> dungeonCards, String message) {
            // Don't choose dungeons
            return null;
        }

        @Override
        public Card chooseSingleCardForZoneChange(ZoneType destination, List<ZoneType> origin, SpellAbility sa, CardCollection fetchList, DelayedReveal delayedReveal, String selectPrompt, boolean isOptional, Player decider) {
            // Choose randomly
            return fetchList.isEmpty() ? null : fetchList.get(random.nextInt(fetchList.size()));
        }

        @Override
        public List<Card> chooseCardsForZoneChange(ZoneType destination, List<ZoneType> origin, SpellAbility sa, CardCollection fetchList, int min, int max, DelayedReveal delayedReveal, String selectPrompt, Player decider) {
            List<Card> result = new ArrayList<>();
            List<Card> options = new ArrayList<>(fetchList);
            int toChoose = Math.min(max, Math.max(min, random.nextInt(max + 1)));

            for (int i = 0; i < toChoose && !options.isEmpty(); i++) {
                result.add(options.remove(random.nextInt(options.size())));
            }

            return result;
        }

        @Override
        public void autoPassCancel() {
            // Do nothing
        }

        @Override
        public void awaitNextInput() {
            // Do nothing
        }

        @Override
        public void cancelAwaitNextInput() {
            // Do nothing
        }

        // Add stubs for other abstract methods that weren't implemented
        // (This is a minimal implementation - a full AI would need all methods)

        // For now, let's add some basic stubs to prevent compilation errors
        @Override
        public boolean mulliganKeepHand(Player player, int cardsToReturn) {
            return random.nextBoolean(); // Randomly decide to keep or mulligan
        }

        @Override
        public CardCollectionView londonMulliganReturnCards(Player player, int i) {
            return null;
        }

        @Override
        public boolean confirmMulliganScry(Player player) {
            return false;
        }
    }
}