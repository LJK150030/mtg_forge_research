package com.example.research.ai;

import com.example.research.mtg_commons;
import forge.item.PaperCard;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.card.ColorSet;
import forge.card.MagicColor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DraftAI - Specialized AI for draft and sealed formats
 *
 * Implements advanced card evaluation and pick strategies for limited formats.
 * Based on proven draft algorithms with color commitment and curve considerations.
 */
public class DraftAI {

    // Draft configuration
    private final DraftConfig config;

    // Draft state tracking
    private final DraftPool pool;
    private final ColorCommitment colorCommitment;
    private final CurveAnalyzer curveAnalyzer;
    private final SynergyEvaluator synergyEvaluator;

    // Pick history and analytics
    private final List<PickRecord> pickHistory;
    private int packNumber = 1;
    private int pickNumber = 1;

    /**
     * Constructor with default configuration
     */
    public DraftAI() {
        this(DraftConfig.createDefault());
    }

    /**
     * Constructor with custom configuration
     */
    public DraftAI(DraftConfig config) {
        this.config = config;
        this.pool = new DraftPool();
        this.colorCommitment = new ColorCommitment();
        this.curveAnalyzer = new CurveAnalyzer();
        this.synergyEvaluator = new SynergyEvaluator();
        this.pickHistory = new ArrayList<>();
    }

    /**
     * Make a draft pick from available options
     */
    public PaperCard makePick(List<PaperCard> pack) {
        if (pack.isEmpty()) return null;

        // Evaluate all cards in the pack
        List<CardEvaluation> evaluations = new ArrayList<>();
        for (PaperCard card : pack) {
            double score = evaluateCardForPick(card);
            evaluations.add(new CardEvaluation(card, score));
        }

        // Sort by score
        evaluations.sort((a, b) -> Double.compare(b.score, a.score));

        // Select best card
        PaperCard pick = evaluations.get(0).card;

        // Record the pick
        recordPick(pick, evaluations);

        // Update draft state
        pool.addCard(pick);
        colorCommitment.updateWithPick(pick, pickNumber);
        curveAnalyzer.addCard(pick);

        // Advance pick counter
        pickNumber++;
        if (pickNumber > 15) { // Assuming 15-card packs
            packNumber++;
            pickNumber = 1;
        }

        return pick;
    }

    /**
     * Evaluate a card for drafting
     */
    private double evaluateCardForPick(PaperCard card) {
        double score = 0.0;

        // 1. Base card quality (inherent power level)
        score += evaluateCardQuality(card) * config.qualityWeight;

        // 2. Color commitment bonus
        score += colorCommitment.getColorBonus(card, pickNumber) * config.colorWeight;

        // 3. Curve considerations
        score += curveAnalyzer.getCurveBonus(card) * config.curveWeight;

        // 4. Synergy with existing picks
        score += synergyEvaluator.evaluateSynergy(card, pool) * config.synergyWeight;

        // 5. Replaceability (how likely to see similar cards)
        score += evaluateReplaceability(card) * config.replaceabilityWeight;

        // 6. Sideboard value
        score += evaluateSideboardValue(card) * config.sideboardWeight;

        // Early pick adjustments
        if (pickNumber <= 3) {
            score *= config.earlyPickMultiplier;
        }

        return score;
    }

    /**
     * Evaluate inherent card quality
     */
    private double evaluateCardQuality(PaperCard card) {
        double score = 0.0;

        if (card.getRules().getType().isCreature()) {
            score = evaluateCreatureQuality(card);
        } else if (card.getRules().getType().isInstant() || card.getRules().getType().isSorcery()) {
            score = evaluateSpellQuality(card);
        } else if (card.getRules().getType().isEnchantment()) {
            score = evaluateEnchantmentQuality(card);
        } else if (card.getRules().getType().isArtifact()) {
            score = evaluateArtifactQuality(card);
        } else if (card.getRules().getType().isPlaneswalker()) {
            score = 4.5; // Planeswalkers are bombs
        } else if (card.getRules().getType().isLand()) {
            score = evaluateLandQuality(card);
        }

        // Rarity adjustment
        switch (card.getRarity()) {
            case Mythic:
                score *= 1.3;
                break;
            case Rare:
                score *= 1.15;
                break;
            case Uncommon:
                score *= 1.05;
                break;
        }

        return Math.min(5.0, score); // Cap at 5.0
    }

    /**
     * Evaluate creature quality using quadrant theory
     */
    private double evaluateCreatureQuality(PaperCard card) {
        double score = 2.0; // Base creature score

        try {
            int power = Integer.parseInt(card.getRules().getPower());
            int toughness = Integer.parseInt(card.getRules().getToughness());
            int cmc = card.getRules().getManaCost().getCMC();

            // Vanilla test (P+T vs CMC)
            double vanillaRatio = (power + toughness) / (double) Math.max(1, cmc);
            if (vanillaRatio >= 2.0) score += 0.5;
            if (vanillaRatio >= 2.5) score += 0.5;

            // Quadrant evaluation
            score += evaluateQuadrant(card, "developing", power, toughness, cmc) * 0.25;
            score += evaluateQuadrant(card, "parity", power, toughness, cmc) * 0.25;
            score += evaluateQuadrant(card, "winning", power, toughness, cmc) * 0.25;
            score += evaluateQuadrant(card, "losing", power, toughness, cmc) * 0.25;

        } catch (NumberFormatException e) {
            // Variable P/T, evaluate abilities only
        }

        // Keyword abilities
        String oracle = card.getRules().getOracleText().toLowerCase();
        score += evaluateCreatureKeywords(oracle);

        return score;
    }

    /**
     * Evaluate creature in different game states
     */
    private double evaluateQuadrant(PaperCard card, String quadrant, int power, int toughness, int cmc) {
        String oracle = card.getRules().getOracleText().toLowerCase();
        double score = 0.0;

        switch (quadrant) {
            case "developing":
                // Early game - efficiency matters
                if (cmc <= 3 && power >= 2) score += 1.0;
                if (oracle.contains("haste")) score += 0.5;
                break;

            case "parity":
                // Board stall - evasion and abilities matter
                if (oracle.contains("flying") || oracle.contains("menace")) score += 1.0;
                if (oracle.contains("deathtouch")) score += 0.8;
                if (power >= 4) score += 0.5;
                break;

            case "winning":
                // Ahead - pressure and protection matter
                if (oracle.contains("hexproof") || oracle.contains("indestructible")) score += 0.5;
                if (power >= 3) score += 0.5;
                if (oracle.contains("trample")) score += 0.5;
                break;

            case "losing":
                // Behind - stabilization matters
                if (toughness >= 4) score += 0.5;
                if (oracle.contains("lifelink")) score += 1.0;
                if (oracle.contains("reach") || oracle.contains("flying")) score += 0.5;
                break;
        }

        return score;
    }

    /**
     * Evaluate creature keywords
     */
    private double evaluateCreatureKeywords(String oracle) {
        double score = 0.0;

        // Evasion
        if (oracle.contains("flying")) score += 0.8;
        if (oracle.contains("menace")) score += 0.4;
        if (oracle.contains("trample")) score += 0.3;
        if (oracle.contains("unblockable") || oracle.contains("can't be blocked")) score += 1.0;

        // Combat abilities
        if (oracle.contains("first strike")) score += 0.5;
        if (oracle.contains("double strike")) score += 1.0;
        if (oracle.contains("deathtouch")) score += 0.6;
        if (oracle.contains("lifelink")) score += 0.5;

        // Protection
        if (oracle.contains("hexproof")) score += 0.7;
        if (oracle.contains("indestructible")) score += 0.8;
        if (oracle.contains("vigilance")) score += 0.3;

        // Utility
        if (oracle.contains("haste")) score += 0.4;
        if (oracle.contains("reach")) score += 0.2;
        if (oracle.contains("flash")) score += 0.3;

        // Card advantage
        if (oracle.contains("draw") && oracle.contains("card")) score += 0.6;
        if (oracle.contains("enters the battlefield") || oracle.contains("etb")) score += 0.3;

        return score;
    }

    /**
     * Evaluate spell quality
     */
    private double evaluateSpellQuality(PaperCard card) {
        double score = 1.5; // Base spell score
        String oracle = card.getRules().getOracleText().toLowerCase();

        // Removal
        if (oracle.contains("destroy target creature")) score += 1.5;
        if (oracle.contains("exile target creature")) score += 1.8;
        if (oracle.contains("deals") && oracle.contains("damage")) {
            if (oracle.contains("any target")) score += 1.6;
            else if (oracle.contains("creature")) score += 1.2;
        }

        // Card advantage
        if (oracle.contains("draw")) {
            if (oracle.contains("two cards")) score += 1.0;
            if (oracle.contains("three cards")) score += 1.5;
        }

        // Combat tricks
        if (card.getRules().getType().isInstant()) {
            if (oracle.contains("+") && oracle.contains("/+")) score += 0.5;
            if (oracle.contains("indestructible")) score += 0.8;
            if (oracle.contains("hexproof")) score += 0.6;
        }

        // Board wipes
        if (oracle.contains("all creatures") || oracle.contains("each creature")) {
            if (oracle.contains("destroy")) score += 2.0;
            if (oracle.contains("damage")) score += 1.5;
        }

        return score;
    }

    /**
     * Evaluate enchantment quality
     */
    private double evaluateEnchantmentQuality(PaperCard card) {
        double score = 1.0; // Base enchantment score
        String oracle = card.getRules().getOracleText().toLowerCase();

        // Removal auras
        if (oracle.contains("enchant creature") &&
                (oracle.contains("can't attack") || oracle.contains("can't block"))) {
            score += 1.2;
        }

        // Beneficial auras
        if (oracle.contains("enchant creature") &&
                (oracle.contains("+") || oracle.contains("flying") || oracle.contains("lifelink"))) {
            score += 0.8;
        }

        // Global enchantments
        if (!oracle.contains("enchant")) {
            if (oracle.contains("creatures you control")) score += 1.0;
            if (oracle.contains("whenever") && oracle.contains("draw")) score += 1.2;
        }

        return score;
    }

    /**
     * Evaluate artifact quality
     */
    private double evaluateArtifactQuality(PaperCard card) {
        double score = 1.0; // Base artifact score
        String oracle = card.getRules().getOracleText().toLowerCase();

        // Equipment
        if (card.getRules().getType().hasSubtype("Equipment")) {
            if (oracle.contains("+") && oracle.contains("/+")) {
                score += 0.8;
                if (oracle.contains("first strike") || oracle.contains("flying")) score += 0.4;
            }
        }

        // Mana artifacts
        if (oracle.contains("add") && oracle.contains("mana")) {
            score += 1.5; // Fixing/ramp is valuable
        }

        // Card advantage artifacts
        if (oracle.contains("draw") && oracle.contains("card")) {
            score += 1.0;
        }

        return score;
    }

    /**
     * Evaluate land quality
     */
    private double evaluateLandQuality(PaperCard card) {
        if (card.getRules().getType().isBasicLand()) {
            return 0.1; // Basics are always available
        }

        // Dual lands and fixing
        String oracle = card.getRules().getOracleText().toLowerCase();
        if (oracle.contains("add") && (oracle.contains("or") || oracle.contains("any color"))) {
            return 3.0; // High pick for fixing
        }

        return 1.0;
    }

    /**
     * Evaluate replaceability of a card
     */
    private double evaluateReplaceability(PaperCard card) {
        // Higher score means less replaceable (more unique)

        if (card.getRarity() == IPaperCard.Rarity.Mythic) return 1.0;
        if (card.getRarity() == IPaperCard.Rarity.Rare) return 0.8;

        // Removal is hard to replace
        String oracle = card.getRules().getOracleText().toLowerCase();
        if (oracle.contains("destroy") || oracle.contains("exile")) return 0.7;

        // Efficient creatures are somewhat replaceable
        if (card.getRules().getType().isCreature()) return 0.3;

        return 0.5;
    }

    /**
     * Evaluate sideboard value
     */
    private double evaluateSideboardValue(PaperCard card) {
        String oracle = card.getRules().getOracleText().toLowerCase();
        double score = 0.0;

        // Artifact/enchantment removal
        if (oracle.contains("destroy") &&
                (oracle.contains("artifact") || oracle.contains("enchantment"))) {
            score += 0.8;
        }

        // Color hosers
        if (oracle.contains("protection from")) score += 0.5;

        // Graveyard hate
        if (oracle.contains("exile") && oracle.contains("graveyard")) score += 0.6;

        return score;
    }

    /**
     * Build final deck from draft pool
     */
    public Deck buildDeck() {
        // Determine colors to play
        ColorSet playableColors = colorCommitment.determineFinalColors(pool);

        // Filter playable cards
        List<PaperCard> playableCards = pool.getCards().stream()
                .filter(card -> isPlayable(card, playableColors))
                .collect(Collectors.toList());

        // Separate by type
        List<PaperCard> creatures = new ArrayList<>();
        List<PaperCard> nonCreatures = new ArrayList<>();
        List<PaperCard> lands = new ArrayList<>();

        for (PaperCard card : playableCards) {
            if (card.getRules().getType().isLand()) {
                lands.add(card);
            } else if (card.getRules().getType().isCreature()) {
                creatures.add(card);
            } else {
                nonCreatures.add(card);
            }
        }

        // Sort by quality
        creatures.sort((a, b) -> Double.compare(evaluateCardQuality(b), evaluateCardQuality(a)));
        nonCreatures.sort((a, b) -> Double.compare(evaluateCardQuality(b), evaluateCardQuality(a)));

        // Build deck
        Deck deck = new Deck("Draft Deck");
        DeckSection main = deck.getMain();

        // Add best creatures (aim for 14-16)
        int creatureCount = Math.min(16, creatures.size());
        for (int i = 0; i < creatureCount; i++) {
            main.add(creatures.get(i));
        }

        // Add best non-creatures (aim for 6-8)
        int nonCreatureCount = Math.min(7, nonCreatures.size());
        for (int i = 0; i < nonCreatureCount; i++) {
            main.add(nonCreatures.get(i));
        }

        // Add lands (17 for limited)
        int landsNeeded = 17;
        Map<String, Integer> colorRequirements = calculateColorRequirements(main);
        addLands(main, lands, colorRequirements, landsNeeded);

        // Add remaining cards to sideboard
        DeckSection sideboard = deck.getSideboard();
        for (PaperCard card : pool.getCards()) {
            if (!main.contains(card)) {
                sideboard.add(card);
            }
        }

        return deck;
    }

    /**
     * Check if card is playable in chosen colors
     */
    private boolean isPlayable(PaperCard card, ColorSet colors) {
        if (card.getRules().getType().isLand()) return true;

        ColorSet cardColors = card.getRules().getColor();
        return colors.containsAllColorsFrom(cardColors.getColor());
    }

    /**
     * Calculate color requirements from deck
     */
    private Map<String, Integer> calculateColorRequirements(DeckSection deck) {
        Map<String, Integer> requirements = new HashMap<>();

        for (PaperCard card : deck) {
            if (!card.getRules().getType().isLand()) {
                String cost = card.getRules().getManaCost().toString();
                for (char c : cost.toCharArray()) {
                    String color = String.valueOf(c);
                    if ("WUBRG".contains(color)) {
                        requirements.put(color, requirements.getOrDefault(color, 0) + 1);
                    }
                }
            }
        }

        return requirements;
    }

    /**
     * Add appropriate lands to deck
     */
    private void addLands(DeckSection deck, List<PaperCard> availableLands,
                          Map<String, Integer> colorRequirements, int targetCount) {
        // First add dual lands
        for (PaperCard land : availableLands) {
            if (!land.getRules().getType().isBasicLand() && deck.size() < 40) {
                deck.add(land);
                targetCount--;
            }
        }

        // Calculate basic land distribution
        int totalRequirements = colorRequirements.values().stream().mapToInt(Integer::intValue).sum();
        if (totalRequirements == 0) totalRequirements = 1;

        // Add basics proportionally
        for (Map.Entry<String, Integer> entry : colorRequirements.entrySet()) {
            String color = entry.getKey();
            int requirement = entry.getValue();
            int basicCount = (int) Math.round(targetCount * (requirement / (double) totalRequirements));

            String basicName = getBasicLandName(color);
            for (PaperCard land : availableLands) {
                if (land.getName().equals(basicName) && basicCount > 0) {
                    deck.add(land);
                    basicCount--;
                }
            }
        }
    }

    /**
     * Get basic land name for color
     */
    private String getBasicLandName(String color) {
        switch (color) {
            case "W": return "Plains";
            case "U": return "Island";
            case "B": return "Swamp";
            case "R": return "Mountain";
            case "G": return "Forest";
            default: return "Wastes";
        }
    }

    /**
     * Record pick for analysis
     */
    private void recordPick(PaperCard pick, List<CardEvaluation> evaluations) {
        PickRecord record = new PickRecord();
        record.packNumber = packNumber;
        record.pickNumber = pickNumber;
        record.pickedCard = pick;
        record.allEvaluations = new ArrayList<>(evaluations);
        record.colorState = colorCommitment.getCurrentState();

        pickHistory.add(record);
    }

    /**
     * Get draft analytics
     */
    public DraftAnalytics getAnalytics() {
        return new DraftAnalytics(pickHistory, pool, colorCommitment);
    }

    /**
     * Reset for new draft
     */
    public void reset() {
        pool.clear();
        colorCommitment.reset();
        curveAnalyzer.reset();
        pickHistory.clear();
        packNumber = 1;
        pickNumber = 1;
    }

    // Inner classes

    /**
     * Draft configuration
     */
    public static class DraftConfig {
        // Evaluation weights
        public double qualityWeight = 1.0;
        public double colorWeight = 0.8;
        public double curveWeight = 0.6;
        public double synergyWeight = 0.4;
        public double replaceabilityWeight = 0.3;
        public double sideboardWeight = 0.2;

        // Pick adjustments
        public double earlyPickMultiplier = 1.2;
        public double latePickMultiplier = 0.8;

        // Deck building targets
        public int targetCreatures = 16;
        public int targetLands = 17;
        public int targetNonCreatures = 7;

        public static DraftConfig createDefault() {
            return new DraftConfig();
        }

        public static DraftConfig createAggressive() {
            DraftConfig config = new DraftConfig();
            config.curveWeight = 0.9;
            config.targetCreatures = 18;
            return config;
        }

        public static DraftConfig createControl() {
            DraftConfig config = new DraftConfig();
            config.qualityWeight = 1.2;
            config.synergyWeight = 0.6;
            config.targetCreatures = 14;
            config.targetNonCreatures = 9;
            return config;
        }
    }

    /**
     * Card evaluation with score
     */
    private static class CardEvaluation {
        final PaperCard card;
        final double score;

        CardEvaluation(PaperCard card, double score) {
            this.card = card;
            this.score = score;
        }
    }

    /**
     * Pick record for analysis
     */
    private static class PickRecord {
        int packNumber;
        int pickNumber;
        PaperCard pickedCard;
        List<CardEvaluation> allEvaluations;
        String colorState;
    }

    /**
     * Draft pool management
     */
    private static class DraftPool {
        private final List<PaperCard> cards = new ArrayList<>();
        private final Map<String, Integer> colorCounts = new HashMap<>();
        private final Map<Integer, Integer> cmcCounts = new HashMap<>();

        void addCard(PaperCard card) {
            cards.add(card);

            // Update color counts
            if (!card.getRules().getColor().isColorless()) {
                for (String color : new String[]{"W", "U", "B", "R", "G"}) {
                    if (card.getRules().getColor().hasAnyColor(Integer.parseInt(color))) {
                        colorCounts.put(color, colorCounts.getOrDefault(color, 0) + 1);
                    }
                }
            }

            // Update CMC counts
            int cmc = card.getRules().getManaCost().getCMC();
            cmcCounts.put(cmc, cmcCounts.getOrDefault(cmc, 0) + 1);
        }

        List<PaperCard> getCards() {
            return Collections.unmodifiableList(cards);
        }

        void clear() {
            cards.clear();
            colorCounts.clear();
            cmcCounts.clear();
        }
    }

    /**
     * Color commitment tracking
     */
    private static class ColorCommitment {
        private String primaryColor = null;
        private String secondaryColor = null;
        private final Map<String, Double> colorScores = new HashMap<>();

        void updateWithPick(PaperCard card, int pickNumber) {
            if (card.getRules().getType().isLand()) return;

            // Update color scores
            if (!card.getRules().getColor().isColorless()) {
                double pickWeight = Math.max(0.5, 1.0 - (pickNumber / 45.0));

                for (String color : new String[]{"W", "U", "B", "R", "G"}) {
                    if (card.getRules().getColor().hasAnyColor(Integer.parseInt(color))) {
                        colorScores.put(color, colorScores.getOrDefault(color, 0.0) + pickWeight);
                    }
                }
            }

            // Update commitments
            if (pickNumber >= 5) {
                List<Map.Entry<String, Double>> sorted = colorScores.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .collect(Collectors.toList());

                if (!sorted.isEmpty()) {
                    primaryColor = sorted.get(0).getKey();
                }
                if (sorted.size() > 1 && sorted.get(1).getValue() > 3.0) {
                    secondaryColor = sorted.get(1).getKey();
                }
            }
        }

        double getColorBonus(PaperCard card, int pickNumber) {
            if (primaryColor == null) return 0.0;

            double bonus = 0.0;

            if (card.getRules().getColor().hasAnyColor(Integer.parseInt(primaryColor))) {
                bonus += 1.0;
            }
            if (secondaryColor != null &&
                    card.getRules().getColor().hasAnyColor(Integer.parseInt(secondaryColor))) {
                bonus += 0.6;
            }

            // Penalize off-color cards after commitment
            if (pickNumber > 10 && !card.getRules().getColor().isColorless()) {
                boolean inColors = false;
                if (primaryColor != null &&
                        card.getRules().getColor().hasAnyColor(Integer.parseInt(primaryColor))) {
                    inColors = true;
                }
                if (secondaryColor != null &&
                        card.getRules().getColor().hasAnyColor(Integer.parseInt(secondaryColor))) {
                    inColors = true;
                }
                if (!inColors) {
                    bonus -= 2.0;
                }
            }

            return bonus;
        }

        ColorSet determineFinalColors(DraftPool pool) {
            if (primaryColor == null) return ColorSet.ALL_COLORS;

            byte colorMask = MagicColor.fromName(primaryColor);
            if (secondaryColor != null) {
                colorMask |= MagicColor.fromName(secondaryColor);
            }

            return ColorSet.fromMask(colorMask);
        }

        String getCurrentState() {
            return primaryColor + (secondaryColor != null ? "/" + secondaryColor : "");
        }

        void reset() {
            primaryColor = null;
            secondaryColor = null;
            colorScores.clear();
        }
    }

    /**
     * Mana curve analysis
     */
    private static class CurveAnalyzer {
        private final int[] curve = new int[8]; // 0-7+ CMC
        private int totalNonLands = 0;

        void addCard(PaperCard card) {
            if (!card.getRules().getType().isLand()) {
                int cmc = Math.min(7, card.getRules().getManaCost().getCMC());
                curve[cmc]++;
                totalNonLands++;
            }
        }

        double getCurveBonus(PaperCard card) {
            if (card.getRules().getType().isLand()) return 0.0;

            int cmc = card.getRules().getManaCost().getCMC();

            // Ideal limited curve peaks at 2-3 CMC
            double idealCount = getIdealCount(cmc);
            double currentCount = curve[Math.min(7, cmc)];

            if (currentCount < idealCount) {
                return 0.5; // Need more at this CMC
            } else if (currentCount > idealCount * 1.5) {
                return -0.5; // Have too many
            }

            return 0.0;
        }

        private double getIdealCount(int cmc) {
            switch (cmc) {
                case 0: return 0;
                case 1: return 1;
                case 2: return 5;
                case 3: return 4;
                case 4: return 3;
                case 5: return 2;
                case 6: return 1;
                default: return 0.5;
            }
        }

        void reset() {
            Arrays.fill(curve, 0);
            totalNonLands = 0;
        }
    }

    /**
     * Synergy evaluation
     */
    private static class SynergyEvaluator {

        double evaluateSynergy(PaperCard card, DraftPool pool) {
            double synergy = 0.0;
            String oracle = card.getRules().getOracleText().toLowerCase();

            // Tribal synergies
            for (String tribe : Arrays.asList("vampire", "cat", "human", "elf", "goblin")) {
                if (oracle.contains(tribe)) {
                    long tribeCount = pool.getCards().stream()
                            .filter(c -> c.getRules().getType().hasSubtype(tribe))
                            .count();
                    synergy += tribeCount * 0.2;
                }
            }

            // Keyword synergies
            if (oracle.contains("flying")) {
                long flyersCount = pool.getCards().stream()
                        .filter(c -> c.getRules().getOracleText().toLowerCase().contains("flying"))
                        .count();
                synergy += flyersCount * 0.1;
            }

            // +1/+1 counter synergies
            if (oracle.contains("+1/+1 counter")) {
                long counterCards = pool.getCards().stream()
                        .filter(c -> c.getRules().getOracleText().toLowerCase().contains("+1/+1 counter"))
                        .count();
                synergy += counterCards * 0.15;
            }

            return Math.min(1.0, synergy);
        }
    }

    /**
     * Draft analytics for post-draft analysis
     */
    public static class DraftAnalytics {
        private final List<PickRecord> pickHistory;
        private final DraftPool finalPool;
        private final ColorCommitment colorCommitment;

        DraftAnalytics(List<PickRecord> pickHistory, DraftPool finalPool, ColorCommitment colorCommitment) {
            this.pickHistory = new ArrayList<>(pickHistory);
            this.finalPool = finalPool;
            this.colorCommitment = colorCommitment;
        }

        public void printAnalysis() {
            System.out.println("\n=== Draft Analysis ===");
            System.out.println("Total picks: " + pickHistory.size());
            System.out.println("Final colors: " + colorCommitment.getCurrentState());

            // First picks
            System.out.println("\nFirst picks by pack:");
            for (PickRecord record : pickHistory) {
                if (record.pickNumber == 1) {
                    System.out.println("Pack " + record.packNumber + ": " +
                            record.pickedCard.getName() + " (Score: " +
                            record.allEvaluations.get(0).score + ")");
                }
            }

            // Color distribution
            System.out.println("\nColor distribution:");
            Map<String, Integer> colorCounts = new HashMap<>();
            for (PaperCard card : finalPool.getCards()) {
                if (!card.getRules().getColor().isColorless()) {
                    for (String color : new String[]{"W", "U", "B", "R", "G"}) {
                        if (card.getRules().getColor().hasAnyColor(Integer.parseInt(color))) {
                            colorCounts.put(color, colorCounts.getOrDefault(color, 0) + 1);
                        }
                    }
                }
            }
            colorCounts.forEach((color, count) ->
                    System.out.println("  " + color + ": " + count + " cards"));
        }
    }
}