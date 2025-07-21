package com.example.research;

import forge.StaticData;
import forge.deck.Deck;
import forge.item.PaperCard;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class ForgeDeck {

    public ForgeDeck() throws IOException {
        createBasicCards();
        createTutorialCards();
        createEditionFiles();
        createFormatFiles();
    }

    private void createCard(File dir, String filename, String name, String cost, String types, String abilities, String oracleText) throws IOException {
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

    private void createBasicCards() throws IOException {
        File cardsDir = new File("D:/my_files/cards");

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

    private void createTutorialCards() throws IOException {
        File cardsDir = new File("D:/my_files/cards");

        // CATS DECK CARDS
        createCard(cardsDir, "savannah_lions", "Savannah Lions", "W", "Creature Cat",
                "PT:2/1", "Savannah Lions");

        createCard(cardsDir, "leonin_skyhunter", "Leonin Skyhunter", "WW", "Creature Cat Knight",
                "PT:2/2\nK:Flying", "Flying");

        createCard(cardsDir, "prideful_parent", "Prideful Parent", "2W", "Creature Cat",
                "PT:2/2\nK:Vigilance", "Vigilance");

        createCard(cardsDir, "felidar_savior", "Felidar Savior", "3W", "Creature Cat Beast",
                "PT:2/3\nK:Lifelink", "Lifelink");

        createCard(cardsDir, "jazal_goldmane", "Jazal Goldmane", "2WW", "Legendary Creature Cat Warrior",
                "PT:4/4\nK:First Strike", "First strike");

        createCard(cardsDir, "angelic_edict", "Angelic Edict", "4W", "Sorcery",
                "A:SP$ Destroy | Cost$ 4 W | ValidTgts$ Creature,Enchantment | TgtPrompt$ Select target creature or enchantment | SpellDescription$ Destroy target creature or enchantment.",
                "Destroy target creature or enchantment.");

        createCard(cardsDir, "pacifism", "Pacifism", "1W", "Enchantment Aura",
                "K:Enchant:creature", "Enchant creature\\nEnchanted creature can't attack or block.");

        createCard(cardsDir, "moment_of_triumph", "Moment of Triumph", "W", "Instant",
                "A:SP$ Pump | Cost$ W | ValidTgts$ Creature | TgtPrompt$ Select target creature | NumAtt$ +2 | NumDef$ +2 | SpellDescription$ Target creature gets +2/+2 until end of turn.",
                "Target creature gets +2/+2 until end of turn. You gain 2 life.");

        createCard(cardsDir, "elspeths_smite", "Elspeth's Smite", "W", "Instant",
                "A:SP$ DealDamage | Cost$ W | ValidTgts$ Creature.attacking,Creature.blocking | TgtPrompt$ Select target attacking or blocking creature | NumDmg$ 3 | SpellDescription$ CARDNAME deals 3 damage to target attacking or blocking creature.",
                "Elspeth's Smite deals 3 damage to target attacking or blocking creature.");

        // VAMPIRE DECK CARDS
        createCard(cardsDir, "vampire_interloper", "Vampire Interloper", "1B", "Creature Vampire Scout",
                "PT:2/1\nK:Flying", "Flying\\nThis creature can't block.");

        createCard(cardsDir, "vampire_spawn", "Vampire Spawn", "2B", "Creature Vampire",
                "PT:2/3", "When this creature enters, each opponent loses 2 life and you gain 2 life.");

        createCard(cardsDir, "moment_of_craving", "Moment of Craving", "1B", "Instant",
                "A:SP$ Pump | Cost$ 1 B | ValidTgts$ Creature | TgtPrompt$ Select target creature | NumAtt$ -2 | NumDef$ -2 | SpellDescription$ Target creature gets -2/-2 until end of turn.",
                "Target creature gets -2/-2 until end of turn. You gain 2 life.");

        createCard(cardsDir, "highborn_vampire", "Highborn Vampire", "3B", "Creature Vampire Warrior",
                "PT:4/3", "Highborn Vampire");

        createCard(cardsDir, "untamed_hunger", "Untamed Hunger", "2B", "Enchantment Aura",
                "K:Enchant:creature", "Enchant creature\\nEnchanted creature gets +2/+1 and has menace.");

        createCard(cardsDir, "bloodtithe_collector", "Bloodtithe Collector", "4B", "Creature Vampire Noble",
                "PT:3/4\nK:Flying", "Flying");

        createCard(cardsDir, "crossway_troublemakers", "Crossway Troublemakers", "5B", "Creature Vampire",
                "PT:5/5", "Attacking Vampires you control have deathtouch and lifelink.");

        createCard(cardsDir, "vengeful_bloodwitch", "Vengeful Bloodwitch", "1B", "Creature Vampire Warlock",
                "PT:1/1", "Whenever this creature or another creature you control dies, target opponent loses 1 life and you gain 1 life.");

        createCard(cardsDir, "heros_downfall", "Hero's Downfall", "1BB", "Instant",
                "A:SP$ Destroy | Cost$ 1 B B | ValidTgts$ Creature,Planeswalker | TgtPrompt$ Select target creature or planeswalker | SpellDescription$ Destroy target creature or planeswalker.",
                "Destroy target creature or planeswalker.");

        createCard(cardsDir, "vampire_neonate", "Vampire Neonate", "B", "Creature Vampire",
                "PT:0/3", "{2}, {T}: Each opponent loses 1 life and you gain 1 life.");

        createCard(cardsDir, "offer_immortality", "Offer Immortality", "1B", "Instant",
                "A:SP$ Pump | Cost$ 1 B | ValidTgts$ Creature | TgtPrompt$ Select target creature | KW$ Deathtouch & Indestructible | SpellDescription$ Target creature gains deathtouch and indestructible until end of turn.",
                "Target creature gains deathtouch and indestructible until end of turn.");

        createCard(cardsDir, "stromkirk_bloodthief", "Stromkirk Bloodthief", "2B", "Creature Vampire Rogue",
                "PT:2/2", "At the beginning of your end step, if an opponent lost life this turn, put a +1/+1 counter on target Vampire you control.");

        createCard(cardsDir, "uncharted_haven", "Uncharted Haven", "0", "Land",
                "K:CARDNAME enters the battlefield tapped.", "This land enters tapped. As it enters, choose a color.\\n{T}: Add one mana of the chosen color.");
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
            writer.println("2 C Savannah Lions");
            writer.println("3 C Leonin Skyhunter");
            writer.println("4 C Prideful Parent");
            writer.println("5 C Felidar Savior");
            writer.println("6 C Angelic Edict");
            writer.println("7 C Jazal Goldmane");
            writer.println("8 C Pacifism");
            writer.println("9 C Ingenious Leonin");
            writer.println("10 C Helpful Hunter");
            writer.println("12 C Leonin Vanguard");
            writer.println("13 C Moment of Triumph");
            writer.println("14 C Uncharted Haven");
            writer.println("15 C Swamp");
            writer.println("16 C Vampire Interloper");
            writer.println("17 C Vampire Spawn");
            writer.println("18 C Moment of Craving");
            writer.println("19 C Highborn Vampire");
            writer.println("20 C Untamed Hunger");
            writer.println("21 C Bloodtithe Collector");
            writer.println("22 C Crossway Troublemakers");
            writer.println("23 C Vengeful Bloodwitch");
            writer.println("24 C Hero's Downfall");
            writer.println("25 C Vampire Neonate");
            writer.println("26 C Offer Immortality");
            writer.println("27 C Stromkirk Bloodthief");
            // Add more cards as needed
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


    private void addCardToDeck(Deck deck, String cardName, int count) {
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

    public Deck createCatsDeck() {
        Deck deck = new Deck("Cats Tutorial Deck");
        addCardToDeck(deck, "Plains", 7);
        addCardToDeck(deck, "Savannah Lions", 1);
        addCardToDeck(deck, "Leonin Skyhunter", 1);
        addCardToDeck(deck, "Prideful Parent", 1);
        addCardToDeck(deck, "Felidar Savior", 1);
        addCardToDeck(deck, "Angelic Edict", 1);
        addCardToDeck(deck, "Jazal Goldmane", 1);
        addCardToDeck(deck, "Pacifism", 1);
        addCardToDeck(deck, "Ingenious Leonin", 1);
        addCardToDeck(deck, "Helpful Hunter", 1);
        addCardToDeck(deck, "Leonin Vanguard", 1);
        addCardToDeck(deck, "Moment of Triumph", 1);
        addCardToDeck(deck, "Elspeth's Smite", 1);
        addCardToDeck(deck, "Uncharted Haven", 1);
        return deck;
    }

    public Deck createVampiresDeck() {
        Deck deck = new Deck("Vampires Tutorial Deck");
        addCardToDeck(deck, "Swamp", 7);
        addCardToDeck(deck, "Vampire Interloper", 1);
        addCardToDeck(deck, "Vampire Spawn", 1);
        addCardToDeck(deck, "Moment of Craving", 1);
        addCardToDeck(deck, "Highborn Vampire", 1);
        addCardToDeck(deck, "Untamed Hunger", 1);
        addCardToDeck(deck, "Bloodtithe Collector", 1);
        addCardToDeck(deck, "Crossway Troublemakers", 1);
        addCardToDeck(deck, "Vengeful Bloodwitch", 1);
        addCardToDeck(deck, "Hero's Downfall", 1);
        addCardToDeck(deck, "Vampire Neonate", 1);
        addCardToDeck(deck, "Offer Immortality", 1);
        addCardToDeck(deck, "Stromkirk Bloodthief", 1);
        addCardToDeck(deck, "Uncharted Haven", 1);
        return deck;
    }
}
