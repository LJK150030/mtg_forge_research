package com.example.research.render;

import com.example.research.mtg_commons;
import com.example.research.simulation.SimulationConfig;
import forge.game.*;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.phase.*;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.game.mana.ManaPool;
import forge.game.spellability.SpellAbilityStackInstance;

import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GameStatePrinter - Advanced game state visualization for MTG Forge
 *
 * Provides comprehensive game state printing with:
 * - Detailed zone visualization
 * - Combat state display
 * - Stack visualization
 * - Mana pool tracking
 * - Hidden information control
 */
public class GameStatePrinter {

    private final SimulationConfig config;
    private final PrintStream output;
    private final ConsoleRenderer renderer;

    // Display options
    private boolean showCardDetails = true;
    private boolean groupByType = true;
    private boolean showCardIds = false;
    private boolean abbreviateCardNames = false;
    private int maxCardsPerLine = 5;

    /**
     * Constructor with configuration
     */
    public GameStatePrinter(SimulationConfig config, PrintStream output) {
        this.config = config;
        this.output = output;
        this.renderer = new ConsoleRenderer(output, config.colorizeOutput, true);
    }

    /**
     * Print full game state
     */
    public void printFullGameState(Game game) {
        if (game == null) {
            renderer.renderError("No game instance available");
            return;
        }

        // Header
        renderer.renderSeparator();
        renderer.renderHeader("GAME STATE - Turn " + game.getPhaseHandler().getTurn());
        renderer.renderSeparator();

        // Match information
        printMatchInfo(game);

        // Phase information
        printPhaseInfo(game);

        // Stack
        if (!game.getStack().isEmpty()) {
            printStack(game);
        }

        // Combat
        if (game.getCombat() != null && !game.getCombat().getAttackers().isEmpty()) {
            printCombat(game);
        }

        // Players
        List<Player> players = game.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);

            // Check focus player
            if (config.focusPlayerIndex >= 0 && config.focusPlayerIndex != i) {
                continue;
            }

            printPlayerState(player, i);
        }

        renderer.renderSeparator();
    }

    /**
     * Print match information
     */
    private void printMatchInfo(Game game) {
        renderer.renderKeyValue("Game Format", game.getRules().getGameType().toString());
        renderer.renderKeyValue("Turn Count", String.valueOf(game.getPhaseHandler().getTurn()));
        renderer.renderKeyValue("Active Player", game.getPhaseHandler().getPlayerTurn().getName());
        renderer.renderKeyValue("Priority", game.getPhaseHandler().getPriorityPlayer().getName());
        renderer.renderSubSeparator();
    }

    /**
     * Print phase information
     */
    private void printPhaseInfo(Game game) {
        PhaseHandler ph = game.getPhaseHandler();
        PhaseType phase = ph.getPhase();

        renderer.renderTitle("Current Phase");
        renderer.renderPhaseChange(phase, ph.getPlayerTurn().getName());

        // Phase-specific information
        switch (phase) {
            case COMBAT_DECLARE_ATTACKERS:
                renderer.renderMessage("  Waiting for attackers declaration...");
                break;
            case COMBAT_DECLARE_BLOCKERS:
                renderer.renderMessage("  Waiting for blockers declaration...");
                break;
            case MAIN1:
            case MAIN2:
                renderer.renderMessage("  Main phase - spells and abilities can be played");
                break;
        }

        renderer.renderSubSeparator();
    }

    /**
     * Print stack contents
     */
    private void printStack(Game game) {
        renderer.renderTitle("Stack (" + game.getStack().size() + " objects)");

        List<SpellAbilityStackInstance> stack = game.getStack();
        for (int i = 0; i < stack.size(); i++) {
            SpellAbilityStackInstance si = stack.get(i);
            String index = "[" + (i + 1) + "]";
            String description = si.getStackDescription();
            String controller = si.getActivatingPlayer().getName();

            renderer.renderMessage(String.format("  %s %s (Controller: %s)",
                    index, description, controller));
        }

        renderer.renderSubSeparator();
    }

    /**
     * Print combat state
     */
    private void printCombat(Game game) {
        Combat combat = game.getCombat();
        renderer.renderTitle("Combat");

        // Attackers
        if (!combat.getAttackers().isEmpty()) {
            renderer.renderSubtitle("  Attackers:");
            for (Card attacker : combat.getAttackers()) {
                String target = "";
                GameEntity defender = combat.getDefenderByAttacker(attacker);
                if (defender instanceof Player) {
                    target = " → " + ((Player) defender).getName();
                } else if (defender instanceof Card) {
                    target = " → " + ((Card) defender).getName();
                }

                renderer.renderMessage("    " + formatCreature(attacker) + target);
            }
        }

        // Blockers
        if (!combat.getAllBlockers().isEmpty()) {
            renderer.renderSubtitle("  Blockers:");
            for (Card blocker : combat.getAllBlockers()) {
                List<Card> blocking = combat.getAttackersBlockedBy(blocker);
                String targets = blocking.stream()
                        .map(Card::getName)
                        .collect(Collectors.joining(", "));

                renderer.renderMessage("    " + formatCreature(blocker) + " → " + targets);
            }
        }

        renderer.renderSubSeparator();
    }

    /**
     * Print detailed player state
     */
    private void printPlayerState(Player player, int index) {
        renderer.renderSeparator();
        renderer.renderHeader("PLAYER " + (index + 1) + ": " + player.getName());
        renderer.renderSeparator();

        // Basic stats
        printPlayerStats(player);

        // Mana pool
        if (config.showManaPool && player.getManaPool().totalMana() > 0) {
            printManaPool(player);
        }

        // Zones
        printZones(player);
    }

    /**
     * Print player statistics
     */
    private void printPlayerStats(Player player) {
        renderer.renderTitle("Stats");

        // Create stats table
        List<String[]> stats = new ArrayList<>();
        stats.add(new String[]{"Life", String.valueOf(player.getLife())});
        stats.add(new String[]{"Poison", String.valueOf(player.getPoisonCounters())});
        stats.add(new String[]{"Max Hand Size", String.valueOf(player.getMaxHandSize())});
        stats.add(new String[]{"Lands Played", player.getLandsPlayedThisTurn() + "/" + player.getLandsPlayedPerTurn()});

        // Experience counters
        if (player.getCounters(CounterEnumType.EXPERIENCE) > 0) {
            stats.add(new String[]{"Experience", String.valueOf(player.getCounters(CounterEnumType.EXPERIENCE))});
        }

        // Energy counters
        if (player.getCounters(CounterEnumType.ENERGY) > 0) {
            stats.add(new String[]{"Energy", String.valueOf(player.getCounters(CounterEnumType.ENERGY))});
        }

        for (String[] stat : stats) {
            renderer.renderKeyValue(stat[0], stat[1]);
        }

        renderer.renderSubSeparator();
    }

    /**
     * Print mana pool
     */
    private void printManaPool(Player player) {
        ManaPool pool = player.getManaPool();
        renderer.renderTitle("Mana Pool");

        List<String> manaTypes = new ArrayList<>();
        if (pool.getAmountOfColor(MagicColor.WHITE) > 0) {
            manaTypes.add("W:" + pool.getAmountOfColor(MagicColor.WHITE));
        }
        if (pool.getAmountOfColor(MagicColor.BLUE) > 0) {
            manaTypes.add("U:" + pool.getAmountOfColor(MagicColor.BLUE));
        }
        if (pool.getAmountOfColor(MagicColor.BLACK) > 0) {
            manaTypes.add("B:" + pool.getAmountOfColor(MagicColor.BLACK));
        }
        if (pool.getAmountOfColor(MagicColor.RED) > 0) {
            manaTypes.add("R:" + pool.getAmountOfColor(MagicColor.RED));
        }
        if (pool.getAmountOfColor(MagicColor.GREEN) > 0) {
            manaTypes.add("G:" + pool.getAmountOfColor(MagicColor.GREEN));
        }
        if (pool.getAmountOfColor(MagicColor.COLORLESS) > 0) {
            manaTypes.add("C:" + pool.getAmountOfColor(MagicColor.COLORLESS));
        }

        renderer.renderMessage("  " + String.join(", ", manaTypes));
        renderer.renderSubSeparator();
    }

    /**
     * Print player zones
     */
    private void printZones(Player player) {
        // Hand
        if (config.showHiddenZones || config.focusPlayerIndex >= 0) {
            printZone(player, ZoneType.Hand, true);
        } else {
            renderer.renderTitle("Hand (" + player.getCardsIn(ZoneType.Hand).size() + " cards)");
            renderer.renderMessage("  [Hidden]");
            renderer.renderSubSeparator();
        }

        // Battlefield
        printBattlefield(player);

        // Graveyard
        if (config.showGraveyardCounts || !player.getCardsIn(ZoneType.Graveyard).isEmpty()) {
            printZone(player, ZoneType.Graveyard, false);
        }

        // Exile
        if (!player.getCardsIn(ZoneType.Exile).isEmpty()) {
            printZone(player, ZoneType.Exile, false);
        }

        // Library
        if (config.showLibraryCounts) {
            renderer.renderTitle("Library");
            renderer.renderMessage("  " + player.getCardsIn(ZoneType.Library).size() + " cards");
            renderer.renderSubSeparator();
        }
    }

    /**
     * Print zone contents
     */
    private void printZone(Player player, ZoneType zone, boolean detailed) {
        List<Card> cards = player.getCardsIn(zone);
        renderer.renderTitle(zone.toString() + " (" + cards.size() + " cards)");

        if (cards.isEmpty()) {
            renderer.renderMessage("  [Empty]");
        } else if (detailed && groupByType) {
            printCardsGroupedByType(cards);
        } else {
            printCardList(cards, detailed);
        }

        renderer.renderSubSeparator();
    }

    /**
     * Print battlefield with special formatting
     */
    private void printBattlefield(Player player) {
        List<Card> battlefield = player.getCardsIn(ZoneType.Battlefield);
        renderer.renderTitle("Battlefield (" + battlefield.size() + " permanents)");

        if (battlefield.isEmpty()) {
            renderer.renderMessage("  [Empty]");
            renderer.renderSubSeparator();
            return;
        }

        // Separate by type
        List<Card> lands = new ArrayList<>();
        List<Card> creatures = new ArrayList<>();
        List<Card> artifacts = new ArrayList<>();
        List<Card> enchantments = new ArrayList<>();
        List<Card> planeswalkers = new ArrayList<>();
        List<Card> other = new ArrayList<>();

        for (Card card : battlefield) {
            if (card.isLand()) {
                lands.add(card);
            } else if (card.isCreature()) {
                creatures.add(card);
            } else if (card.isPlaneswalker()) {
                planeswalkers.add(card);
            } else if (card.isEnchantment()) {
                enchantments.add(card);
            } else if (card.isArtifact()) {
                artifacts.add(card);
            } else {
                other.add(card);
            }
        }

        // Print each type
        if (!lands.isEmpty()) {
            renderer.renderSubtitle("  Lands:");
            printLands(lands);
        }

        if (!creatures.isEmpty()) {
            renderer.renderSubtitle("  Creatures:");
            printCreatures(creatures);
        }

        if (!planeswalkers.isEmpty()) {
            renderer.renderSubtitle("  Planeswalkers:");
            printPlaneswalkers(planeswalkers);
        }

        if (!artifacts.isEmpty()) {
            renderer.renderSubtitle("  Artifacts:");
            printCardList(artifacts, true);
        }

        if (!enchantments.isEmpty()) {
            renderer.renderSubtitle("  Enchantments:");
            printCardList(enchantments, true);
        }

        if (!other.isEmpty()) {
            renderer.renderSubtitle("  Other:");
            printCardList(other, true);
        }

        renderer.renderSubSeparator();
    }

    /**
     * Print lands with grouping
     */
    private void printLands(List<Card> lands) {
        Map<String, Integer> landCounts = new HashMap<>();
        Map<String, Card> landExamples = new HashMap<>();

        for (Card land : lands) {
            String name = land.getName();
            landCounts.put(name, landCounts.getOrDefault(name, 0) + 1);
            landExamples.putIfAbsent(name, land);
        }

        for (Map.Entry<String, Integer> entry : landCounts.entrySet()) {
            String name = entry.getKey();
            int count = entry.getValue();
            Card example = landExamples.get(name);

            String status = example.isTapped() ? "(T)" : "(U)";
            String line = count > 1 ? count + "x " + name + " " + status : name + " " + status;

            renderer.renderMessage("    " + line);
        }
    }

    /**
     * Print creatures with details
     */
    private void printCreatures(List<Card> creatures) {
        for (Card creature : creatures) {
            renderer.renderMessage("    " + formatCreature(creature));
        }
    }

    /**
     * Print planeswalkers with loyalty
     */
    private void printPlaneswalkers(List<Card> planeswalkers) {
        for (Card pw : planeswalkers) {
            String loyalty = " [" + pw.getCurrentLoyalty() + "]";
            renderer.renderMessage("    " + pw.getName() + loyalty);
        }
    }

    /**
     * Print cards grouped by type
     */
    private void printCardsGroupedByType(List<Card> cards) {
        Map<String, List<Card>> groups = new HashMap<>();

        for (Card card : cards) {
            String type = getCardTypeString(card);
            groups.computeIfAbsent(type, k -> new ArrayList<>()).add(card);
        }

        for (Map.Entry<String, List<Card>> entry : groups.entrySet()) {
            renderer.renderSubtitle("  " + entry.getKey() + ":");
            printCardList(entry.getValue(), true);
        }
    }

    /**
     * Print card list
     */
    private void printCardList(List<Card> cards, boolean detailed) {
        if (!detailed) {
            // Simple count by name
            Map<String, Integer> counts = new HashMap<>();
            for (Card card : cards) {
                counts.put(card.getName(), counts.getOrDefault(card.getName(), 0) + 1);
            }

            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                String count = entry.getValue() > 1 ? entry.getValue() + "x " : "";
                renderer.renderMessage("    " + count + entry.getKey());
            }
        } else {
            // Detailed view
            for (Card card : cards) {
                renderer.renderMessage("    " + formatCard(card));
            }
        }
    }

    /**
     * Format creature for display
     */
    private String formatCreature(Card creature) {
        StringBuilder sb = new StringBuilder();

        // Name
        sb.append(creature.getName());

        // P/T
        sb.append(" (").append(creature.getNetPower()).append("/").append(creature.getNetToughness()).append(")");

        // Status
        List<String> status = new ArrayList<>();
        if (creature.isTapped()) status.add("Tapped");
        if (creature.isSick()) status.add("Summoning Sick");
        if (creature.getDamage() > 0) status.add(creature.getDamage() + " damage");

        // Keywords
        for (String keyword : creature.getKeywords()) {
            if (isImportantKeyword(keyword)) {
                status.add(keyword);
            }
        }

        // Counters
        if (creature.getCounters().size() > 0) {
            for (Map.Entry<CounterType, Integer> counter : creature.getCounters().entrySet()) {
                status.add(counter.getValue() + " " + counter.getKey().toString());
            }
        }

        if (!status.isEmpty()) {
            sb.append(" [").append(String.join(", ", status)).append("]");
        }

        return sb.toString();
    }

    /**
     * Format card for display
     */
    private String formatCard(Card card) {
        StringBuilder sb = new StringBuilder();

        // Name
        sb.append(card.getName());

        // Mana cost
        if (showCardDetails && card.getManaCost() != null) {
            sb.append(" ").append(card.getManaCost());
        }

        // Type
        if (showCardDetails) {
            sb.append(" - ").append(card.getType());
        }

        // Status
        if (card.isTapped()) {
            sb.append(" [Tapped]");
        }

        // Card ID
        if (showCardIds) {
            sb.append(" {").append(card.getId()).append("}");
        }

        return sb.toString();
    }

    /**
     * Get card type string for grouping
     */
    private String getCardTypeString(Card card) {
        if (card.isCreature()) return "Creatures";
        if (card.isLand()) return "Lands";
        if (card.isInstant()) return "Instants";
        if (card.isSorcery()) return "Sorceries";
        if (card.isEnchantment()) return "Enchantments";
        if (card.isArtifact()) return "Artifacts";
        if (card.isPlaneswalker()) return "Planeswalkers";
        return "Other";
    }

    /**
     * Check if keyword is important enough to display
     */
    private boolean isImportantKeyword(String keyword) {
        String lower = keyword.toLowerCase();
        return lower.contains("flying") ||
                lower.contains("first strike") ||
                lower.contains("double strike") ||
                lower.contains("deathtouch") ||
                lower.contains("lifelink") ||
                lower.contains("trample") ||
                lower.contains("vigilance") ||
                lower.contains("haste") ||
                lower.contains("hexproof") ||
                lower.contains("indestructible") ||
                lower.contains("menace") ||
                lower.contains("reach");
    }

    /**
     * Print quick game summary
     */
    public void printQuickSummary(Game game) {
        renderer.renderTitle("Quick Game Summary - Turn " + game.getPhaseHandler().getTurn());

        for (Player player : game.getPlayers()) {
            String summary = String.format("%s: %d life, %d cards in hand, %d permanents",
                    player.getName(),
                    player.getLife(),
                    player.getCardsIn(ZoneType.Hand).size(),
                    player.getCardsIn(ZoneType.Battlefield).size()
            );
            renderer.renderMessage(summary);
        }
    }

    // Configuration methods

    public void setShowCardDetails(boolean show) {
        this.showCardDetails = show;
    }

    public void setGroupByType(boolean group) {
        this.groupByType = group;
    }

    public void setShowCardIds(boolean show) {
        this.showCardIds = show;
    }

    public void setAbbreviateCardNames(boolean abbreviate) {
        this.abbreviateCardNames = abbreviate;
    }

    public void setMaxCardsPerLine(int max) {
        this.maxCardsPerLine = max;
    }
}