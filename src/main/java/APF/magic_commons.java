package APF;

import java.util.*;
import java.util.regex.Pattern;

public final class magic_commons {

    public static final List<String> PLAYERS = Arrays.asList(
            "NULL", "Player_1", "Player_2"
    );

    public static final List<String> CARD_COLOR_ID = Arrays.asList(
            "White", "Blue", "Black", "Red", "Green"
    );

    static final Map<Character, String> COLOR_BY_SYMBOL = Map.of(
            'W', "White",
            'U', "Blue",
            'B', "Black",
            'R', "Red",
            'G', "Green"
    );

    private static final Pattern BRACED = Pattern.compile("\\{([^}]+)}");
    static final Pattern FULL_BRACED = Pattern.compile("^(?:\\{(?:\\d+|X|S|C|[WUBRG]|2\\/[WUBRG]|[WUBRG]\\/P|([WUBRG])\\/(?!\\1)[WUBRG])\\})+$");
    static final Pattern ELEMENT_BASED_BRACED = Pattern.compile("\\{(\\d+|X|S|C|[WUBRG]|2\\/[WUBRG]|[WUBRG]\\/P|([WUBRG])\\/(?!\\2)[WUBRG])\\}");

    static final Pattern MATCH_ANYTHING = Pattern.compile(".*");
    static final Pattern NON_EMPTY = Pattern.compile(".+");
    static final Pattern DIGIT_ONLY = Pattern.compile("^\\d+$");
    static final Pattern SPLIT_COMBINED = Pattern.compile("\\s*[|;,]+\\s*");


    public static final List<String> CARD_SUPERTYPES = Arrays.asList(
            "Basic", "Legendary", "Ongoing", "Snow", "World"
    );

    public static final List<String> CARD_TYPES = Arrays.asList(
            "Artifact", "Battle", "Conspiracy", "Creature", "Dungeon", "Enchantment", "Instant", "Kindred", "Land",
            "Phenomenon", "Plane", "Planeswalker", "Scheme", "Sorcery", "Vanguard"
    );

    public static final List<String> ARTIFACT_TYPES = Arrays.asList(
            "Attraction", "Blood", "Bobblehead", "Clue", "Contraption", "Equipment", "Food", "Fortification", "Gold",
            "Incubator", "Junk", "Lander", "Map", "Powerstone", "Spacecraft", "Treasure", "Vehicle"
    );

    public static final List<String> ENCHANTMENT_TYPES = Arrays.asList(
            "Aura", "Background", "Cartouche", "Case", "Class", "Curse", "Role", "Room", "Rune", "Saga", "Shard",
            "Shrine"
    );

    public static final List<String> LAND_TYPES = Arrays.asList(
            "Cave", "Desert", "Forest", "Gate", "Island", "Lair", "Locus", "Mine", "Mountain", "Plains", "Planet",
            "Power-Plant", "Sphere", "Swamp", "Tower", "Town", "Urza’s"
    );

    public static final List<String> PLANESWALKER_TYPES = Arrays.asList(
            "Ajani", "Aminatou", "Angrath", "Arlinn", "Ashiok", "Bahamut", "Basri", "Bolas", "Calix", "Chandra",
            "Comet", "Dack", "Dakkon", "Daretti", "Davriel", "Dihada", "Domri", "Dovin", "Ellywick", "Elminster",
            "Elspeth", "Estrid", "Freyalise", "Garruk", "Gideon", "Grist", "Guff", "Huatli", "Jace", "Jared", "Jaya",
            "Jeska", "Kaito", "Karn", "Kasmina", "Kaya", "Kiora", "Koth", "Liliana", "Lolth", "Lukka", "Minsc",
            "Mordenkainen", "Nahiri", "Narset", "Niko", "Nissa", "Nixilis", "Oko", "Quintorius", "Ral", "Rowan",
            "Saheeli", "Samut", "Sarkhan", "Serra", "Sivitri", "Sorin", "Szat", "Tamiyo", "Tasha", "Teferi", "Teyo",
            "Tezzeret", "Tibalt", "Tyvar", "Ugin", "Urza", "Venser", "Vivien", "Vraska", "Vronos", "Will", "Windgrace",
            "Wrenn", "Xenagos", "Yanggu", "Yanling", "Zariel"
    );

    public static final List<String> SPELL_TYPES = Arrays.asList(
            "Adventure", "Arcane", "Lesson", "Omen", "Trap"
    );

    public static final List<String> CREATURE_TYPES = Arrays.asList(
            "Advisor", "Aetherborn", "Alien", "Ally", "Angel", "Antelope", "Ape", "Archer", "Archon", "Armadillo",
            "Army", "Artificer", "Assassin", "Assembly-Worker", "Astartes", "Atog", "Aurochs", "Avatar", "Azra",
            "Badger", "Balloon", "Barbarian", "Bard", "Basilisk", "Bat", "Bear", "Beast", "Beaver", "Beeble",
            "Beholder", "Berserker", "Bird", "Blinkmoth", "Boar", "Bringer", "Brushwagg", "Camarid", "Camel",
            "Capybara", "Caribou", "Carrier", "Cat", "Centaur", "Child", "Chimera", "Citizen", "Cleric", "Clown",
            "Cockatrice", "Construct", "Coward", "Coyote", "Crab", "Crocodile", "C’tan", "Custodes", "Cyberman",
            "Cyclops", "Dalek", "Dauthi", "Demigod", "Demon", "Deserter", "Detective", "Devil", "Dinosaur", "Djinn",
            "Doctor", "Dog", "Dragon", "Drake", "Dreadnought", "Drix", "Drone", "Druid", "Dryad", "Dwarf", "Echidna",
            "Efreet", "Egg", "Elder", "Eldrazi", "Elemental", "Elephant", "Elf", "Elk", "Employee", "Eye", "Faerie",
            "Ferret", "Fish", "Flagbearer", "Fox", "Fractal", "Frog", "Fungus", "Gamer", "Gargoyle", "Germ", "Giant",
            "Gith", "Glimmer", "Gnoll", "Gnome", "Goat", "Goblin", "God", "Golem", "Gorgon", "Graveborn", "Gremlin",
            "Griffin", "Guest", "Hag", "Halfling", "Hamster", "Harpy", "Hedgehog", "Hellion", "Hero", "Hippo",
            "Hippogriff", "Homarid", "Homunculus", "Horror", "Horse", "Human", "Hydra", "Hyena", "Illusion", "Imp",
            "Incarnation", "Inkling", "Inquisitor", "Insect", "Jackal", "Jellyfish", "Juggernaut", "Kavu", "Kirin",
            "Kithkin", "Knight", "Kobold", "Kor", "Kraken", "Llama", "Lamia", "Lammasu", "Leech", "Leviathan",
            "Lhurgoyf", "Licid", "Lizard", "Lobster", "Manticore", "Masticore", "Mercenary", "Merfolk", "Metathran",
            "Minion", "Minotaur", "Mite", "Mole", "Monger", "Mongoose", "Monk", "Monkey", "Moogle", "Moonfolk", "Mount",
            "Mouse", "Mutant", "Myr", "Mystic", "Nautilus", "Necron", "Nephilim", "Nightmare", "Nightstalker", "Ninja",
            "Noble", "Noggle", "Nomad", "Nymph", "Octopus", "Ogre", "Ooze", "Orb", "Orc", "Orgg", "Otter", "Ouphe",
            "Ox", "Oyster", "Pangolin", "Peasant", "Pegasus", "Pentavite", "Performer", "Pest", "Phelddagrif",
            "Phoenix", "Phyrexian", "Pilot", "Pincher", "Pirate", "Plant", "Porcupine", "Possum", "Praetor", "Primarch",
            "Prism", "Processor", "Qu", "Rabbit", "Raccoon", "Ranger", "Rat", "Rebel", "Reflection", "Rhino", "Rigger",
            "Robot", "Rogue", "Sable", "Salamander", "Samurai", "Sand", "Saproling", "Satyr", "Scarecrow", "Scientist",
            "Scion", "Scorpion", "Scout", "Sculpture", "Seal", "Serf", "Serpent", "Servo", "Shade", "Shaman",
            "Shapeshifter", "Shark", "Sheep", "Siren", "Skeleton", "Skunk", "Slith", "Sliver", "Sloth", "Slug", "Snail",
            "Snake", "Soldier", "Soltari", "Spawn", "Specter", "Spellshaper", "Sphinx", "Spider", "Spike", "Spirit",
            "Splinter", "Sponge", "Squid", "Squirrel", "Starfish", "Surrakar", "Survivor", "Synth", "Tentacle",
            "Tetravite", "Thalakos", "Thopter", "Thrull", "Tiefling", "Time Lord", "Toy", "Treefolk", "Trilobite",
            "Triskelavite", "Troll", "Turtle", "Tyranid", "Unicorn", "Vampire", "Varmint", "Vedalken", "Volver",
            "Wall", "Walrus", "Warlock", "Warrior", "Weasel", "Weird", "Werewolf", "Whale", "Wizard", "Wolf",
            "Wolverine", "Wombat", "Worm", "Wraith", "Wurm", "Yeti", "Zombie", "Zubera"
    );


    public static final List<String> PLANAR_TYPES = Arrays.asList(
            "The Abyss", "Alara", "Alfava Metraxis", "Amonkhet", "Androzani Minor", "Antausia", "Apalapucia",
            "Arcavios", "Arkhos", "Avishkar", "Azgol", "Belenon", "Bolas’s Meditation Realm", "Capenna", "Cridhe",
            "The Dalek Asylum", "Darillium", "Dominaria", "Earth", "Echoir", "Eldraine", "Equilor", "Ergamon",
            "Fabacin", "Fiora", "Gallifrey", "Gargantikar", "Gobakhan", "Horsehead Nebula", "Ikoria", "Innistrad",
            "Iquatana", "Ir", "Ixalan", "Kaldheim", "Kamigawa", "Kandoka", "Karsus", "Kephalai", "Kinshala", "Kolbahan",
            "Kylem", "Kyneth", "The Library", "Lorwyn", "Luvion", "Mars", "Mercadia", "Mirrodin", "Moag", "Mongseng",
            "Moon", "Muraganda", "Necros", "New Earth", "New Phyrexia", "Outside Mutter’s Spiral", "Phyrexia",
            "Pyrulea", "Rabiah", "Rath", "Ravnica", "Regatha", "Segovia", "Serra’s Realm", "Shadowmoor", "Shandalar",
            "Shenmeng", "Skaro", "Spacecraft", "Tarkir", "Theros", "Time", "Trenzalore", "Ulgrotha", "Unknown Planet",
            "Valla", "Vryn", "Wildfire", "Xerex", "Zendikar", "Zhalfir "
    );

    public static final List<String> DUNGEON_TYPES = Arrays.asList(
            "Undercity"
    );

    public static final List<String> BATTLE_TYPES = Arrays.asList(
            "Siege"
    );

    public static final List<String> KEYWORD_ABILITIES = Arrays.asList(
            "Absorb", "Affinity", "Afflict", "Afterlife", "Aftermath", "Amplify", "Annihilator", "Assist", "Aura Swap",
            "Awaken", "Backup", "Battle Cry", "Bestow", "Blitz", "Bloodthirst", "Bushido", "Buyback", "Cascade",
            "Casualty", "Cipher", "Cleave", "Companion", "Compleated", "Conspire", "Convoke", "Crew",
            "Cumulative Upkeep", "Cycling", "Dash", "Deathtouch", "Decayed", "Defender", "Delve", "Dethrone", "Devour",
            "Discover", "Disguise", "Disturb", "Double Strike", "Dredge", "Echo", "Embalm", "Emerge", "Enchant",
            "Encore", "Enlist", "Entwine", "Epic", "Equip", "Escalate", "Escape", "Eternalize", "Evoke", "Evolve",
            "Exalted", "Exploit", "Extort", "Fabricate", "Fading", "Fear", "First Strike", "Flanking", "Flash",
            "Flashback", "Flying", "For Mirrodin!", "Forecast", "Foretell", "Fortify", "Frenzy", "Freerunning", "Fuse",
            "Gift", "Graft", "Gravestorm", "Haste", "Haunt", "Hexproof", "Hidden Agenda", "Hideaway", "Horsemanship",
            "Improvise", "Indestructible", "Infect", "Ingest", "Intimidate", "Jump-Start", "Level Up", "Lifelink",
            "Living Metal", "Living Weapon", "Madness", "Melee", "Mentor", "Miracle", "Modular",
            "More Than Meets the Eye", "Morph", "Ninjutsu", "Offering", "Offspring", "Outlast", "Overload",
            "Partner with", "Persist", "Phasing", "Plot", "Poisonous", "Protection", "Provoke", "Prowess", "Prowl",
            "Rampage", "Ravenous", "Reach", "Read Ahead", "Rebound", "Reconfigure", "Recover", "Reinforce", "Renown",
            "Replicate", "Retrace", "Riot", "Ripple", "Saddle", "Scavenge", "Shadow", "Shroud", "Skulk", "Soulbond",
            "Soulshift", "Space Sculptor", "Spectacle", "Splice", "Split Second", "Squad", "Storm", "Sunburst", "Surge",
            "Suspend", "Toxic", "Training", "Trample", "Transfigure", "Transmute", "Tribute", "Umbra Armor",
            "Undaunted", "Undying", "Unearth", "Unleash", "Vanishing", "Vigilance", "Visit", "Wither",
            "ChooseColor"
    );

    public static final Set<String> KEYWORD_ABILITY_SET =
            KEYWORD_ABILITIES.stream()
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet());

    static final Map<String, String> KEYWORD_CANON =
            KEYWORD_ABILITIES.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            s -> s.toLowerCase(Locale.ROOT),
                            s -> s,
                            (a, b) -> a,
                            LinkedHashMap::new));

    private static final Set<String> KEYWORD_ABILITY_SET_LOWER = KEYWORD_CANON.keySet();

    static final int MAX_KEYWORD_WORDS =
            KEYWORD_ABILITIES.stream()
                    .mapToInt(s -> s.split("\\s+").length)
                    .max().orElse(5);

    static final class AltKeyword {
        final String head;          // e.g., "ETBReplacement"
        final List<String> args;    // e.g., ["Other","ChooseColor"]
        final String refSVar;       // if last arg looks like an SVar name
        final String refAction;     // resolved from SVar body’s DB$ if available
        AltKeyword(String head, List<String> args, String refSVar, String refAction) {
            this.head = head; this.args = args; this.refSVar = refSVar; this.refAction = refAction;
        }
        @Override public String toString() { return head + ":" + String.join(":", args); }
    }

    static final class KeywordParseResult {
        final List<String> canonical;                 // e.g., ["Flying","Haste"]
        final Map<String, List<AltKeyword>> alt;      // head -> instances
        KeywordParseResult(List<String> canonical, Map<String, List<AltKeyword>> alt) {
            this.canonical = canonical; this.alt = alt;
        }
    }

    static Map<String, String> parseSVarActions(Map<String, String> cardData) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> e : cardData.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("SVar:")) {
                String name = k.substring("SVar:".length()).trim();   // e.g., "ChooseColor"
                String v = e.getValue() == null ? "" : e.getValue();
                // Find DB$ ActionName
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\\bDB\\$\\s*([A-Za-z]+)")
                        .matcher(v);
                if (m.find()) out.put(name, m.group(1));              // e.g., ChooseColor -> ChooseColor
            }
        }
        return out;
    }




    public static final Set<String> CARD_SUPERTYPES_SET = new HashSet<>(CARD_SUPERTYPES);
    public static final Set<String> CARD_TYPES_SET = new HashSet<>(CARD_TYPES);
    public static final Map<String, Set<String>> TYPE_TO_SUBTYPES = new HashMap<>();

    static {
        TYPE_TO_SUBTYPES.put("Artifact", new HashSet<>(ARTIFACT_TYPES));
        TYPE_TO_SUBTYPES.put("Creature", new HashSet<>(CREATURE_TYPES));
        TYPE_TO_SUBTYPES.put("Kindred", new HashSet<>(CREATURE_TYPES));
        TYPE_TO_SUBTYPES.put("Enchantment", new HashSet<>(ENCHANTMENT_TYPES));
        TYPE_TO_SUBTYPES.put("Land", new HashSet<>(LAND_TYPES));
        TYPE_TO_SUBTYPES.put("Planeswalker", new HashSet<>(PLANESWALKER_TYPES));
        TYPE_TO_SUBTYPES.put("Instant", new HashSet<>(SPELL_TYPES));
        TYPE_TO_SUBTYPES.put("Sorcery", new HashSet<>(SPELL_TYPES));
        TYPE_TO_SUBTYPES.put("Plane", new HashSet<>(PLANAR_TYPES));
        TYPE_TO_SUBTYPES.put("Dungeon", new HashSet<>(DUNGEON_TYPES));
        TYPE_TO_SUBTYPES.put("Battle", new HashSet<>(BATTLE_TYPES));
        // Phenomenon, Scheme, Vanguard, Conspiracy have no subtypes - no need to add them
    }

    public static final Set<String> ALL_SUBTYPES = new HashSet<>();

    static {
        TYPE_TO_SUBTYPES.values().forEach(ALL_SUBTYPES::addAll);
    }


    public static final List<String> ZONE_TYPES = Arrays.asList(
            "Ante", "Battlefield", "Command", "Exile", "Graveyard", "Hand", "Library", "Stack"
    );

    public static final Set<String> ZONE_TYPES_SET = new HashSet<>(ZONE_TYPES);


    public static final List<String> CARD_COUNTER_TYPES = Arrays.asList(
            "M1M1", "P1P1", "Loyalty", "Acorn", "Aegis", "Age", "Aim", "Arrow", "Arrowhead", "Awakening", "Bait",
            "Blaze", "Blessing", "Blight", "Blood", "Bloodline", "Bloodstain", "Bore", "Bounty", "Brain", "Bribery",
            "Brick", "Burden", "Cage", "Carrion", "Cell ", "Charge", "Chorus", "Coin", "Collection", "Component",
            "Contested", "Corpse", "Corruption", "Croak", "Credit", "Crystal", "Cube", "Currency", "Death", "Defense",
            "Delay", "Depletion", "Descent", "Despair", "Devotion", "Discovery", "Divinity", "Doom", "Dread ", "Dream",
            "Duty", "Echo", "Egg", "Elixir", "Ember", "Eon", "Eruption", "Exposure", "Eyeball", "Eyestalk",
            "Everything", "Fade", "Fate", "Feather", "Feeding", "Fellowship", "Fetch", "Filibuster", "Finality", "Fire",
            "Flame", "Flavor", "Flood", "Foreshadow", "Fungus", "Funk", "Fury", "Fuse", "Gem", "Ghostform", "Glyph",
            "Gold", "Growth", "Harmony", "Hatching", "Hatchling", "Healing", "Hit", "Hone", "Hope", "Hoofprint", "Hour",
            "Hourglass", "Hunger", "Husk", "Ice", "Impostor", "Incarnation", "Incubation", "Ingredient", "Infection",
            "Influence", "Ingenuity", "Intel", "Intervention", "Invitation", "Isolation", "Javelin", "Judgment", "Ki",
            "Kick", "Knowledge", "Landmark", "Level", "Loot", "Lore", "Luck", "Manabond", "M0m1", "M0m2", "M1m0",
            "M2m1", "M2m2", "Magnet", "Mana", "Manifestation", "Mannequin", "Matrix", "Memory", "Midway", "Mine",
            "Mining", "Mire", "Music", "Muster", "Necrodermis", "Net", "Nest", "Oil", "Omen", "Ore", "Page", "Pain",
            "Paralyzation", "Petal", "Petrification", "Pin", "Plague", "Plot", "Pressure", "Phylactery", "Phyresis",
            "Point", "Polyp", "Possession", "Prey", "Pupa", "P0p1", "P0p2", "P1p0", "P1p2", "P2p0", "P2p2", "Quest",
            "Rally", "Release", "Reprieve", "Rejection", "Rev", "Revival", "Ribbon", "Ritual", "Rope", "Rust", "Scream",
            "Scroll", "Shell", "Shield", "Shred", "Silver", "Skewer", "Sleep", "Slumber", "Sleight", "Slime", "Soul",
            "Soot", "Spite", "Spore", "Stash", "Storage", "Story", "Strife", "Study", "Stun", "Supply", "Takeover",
            "Task", "Theft", "Tide", "Time", "Tower", "Training", "Trap", "Treasure", "Unity", "Unlock", "Valor",
            "Velocity", "Verse", "Vitality", "Vortex", "Voyage", "Wage", "Winch", "Wind", "Wish", "Wreck"
    );

    public static final Set<String> CARD_COUNTER_TYPES_SET = new HashSet<>(CARD_COUNTER_TYPES);

    public static final List<String> PLAYER_COUNTER_TYPES = Arrays.asList(
            "Energy", "Experience", "Poison", "Rad", "Ticket"
    );

    public static final Set<String> PLAYER_COUNTER_TYPES_SET = new HashSet<>(PLAYER_COUNTER_TYPES);


}
