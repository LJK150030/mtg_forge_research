package com.example.research.render;

import com.example.research.mtg_commons;
import com.example.research.simulation.SimulationEngine;
import forge.game.phase.PhaseType;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;

/**
 * ConsoleRenderer - Formatted console output for MTG game states
 *
 * Provides comprehensive console rendering functionality:
 * - Structured game state display
 * - Player information formatting
 * - Event logging with timestamps
 * - ASCII-based visual representations
 */
public class ConsoleRenderer {

    // Output configuration
    private final PrintStream output;
    private final boolean useColors;
    private final boolean useUnicode;
    private final SimpleDateFormat timestampFormat;

    // Display settings
    private int consoleWidth = 80;
    private boolean compactMode = false;
    private boolean showTimestamps = true;

    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String BLACK = "\u001B[30m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String BOLD = "\u001B[1m";

    // Unicode symbols
    private static final String UNICODE_HEART = "♥";
    private static final String UNICODE_SHIELD = "🛡";
    private static final String UNICODE_SWORD = "⚔";
    private static final String UNICODE_MANA = "◈";
    private static final String UNICODE_CARD = "🎴";
    private static final String UNICODE_CHECKMARK = "✓";
    private static final String UNICODE_CROSS = "✗";

    /**
     * Default constructor with System.out
     */
    public ConsoleRenderer() {
        this(System.out, true, true);
    }

    /**
     * Constructor with custom output stream
     */
    public ConsoleRenderer(PrintStream output) {
        this(output, true, true);
    }

    /**
     * Full constructor
     */
    public ConsoleRenderer(PrintStream output, boolean useColors, boolean useUnicode) {
        this.output = output;
        this.useColors = useColors;
        this.useUnicode = useUnicode;
        this.timestampFormat = new SimpleDateFormat("HH:mm:ss");
    }

    /**
     * Clear console (platform-specific)
     */
    public void clear() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                output.print("\033[H\033[2J");
                output.flush();
            }
        } catch (Exception e) {
            // Fallback: print many newlines
            for (int i = 0; i < 50; i++) {
                output.println();
            }
        }
    }

    /**
     * Render separator line
     */
    public void renderSeparator() {
        output.println(mtg_commons.CONSOLE_SEPARATOR);
    }

    /**
     * Render sub-separator line
     */
    public void renderSubSeparator() {
        output.println(mtg_commons.CONSOLE_SUBSEPARATOR);
    }

    /**
     * Render header with formatting
     */
    public void renderHeader(String text) {
        String formatted = centerText(text, consoleWidth);
        if (useColors) {
            output.println(BOLD + BLUE + formatted + RESET);
        } else {
            output.println(formatted);
        }
    }

    /**
     * Render title
     */
    public void renderTitle(String title) {
        if (useColors) {
            output.println(BOLD + title + RESET);
        } else {
            output.println(title);
        }
    }

    /**
     * Render subtitle
     */
    public void renderSubtitle(String subtitle) {
        if (useColors) {
            output.println(CYAN + subtitle + RESET);
        } else {
            output.println(subtitle);
        }
    }

    /**
     * Render key-value pair
     */
    public void renderKeyValue(String key, String value) {
        String formatted = String.format("  %-20s: %s", key, value);
        output.println(formatted);
    }

    /**
     * Render message with optional timestamp
     */
    public void renderMessage(String message) {
        if (showTimestamps) {
            String timestamp = "[" + timestampFormat.format(new Date()) + "] ";
            output.println(timestamp + message);
        } else {
            output.println(message);
        }
    }

    /**
     * Render error message
     */
    public void renderError(String error) {
        if (useColors) {
            output.println(RED + "❌ " + error + RESET);
        } else {
            output.println("[ERROR] " + error);
        }
    }

    /**
     * Render warning message
     */
    public void renderWarning(String warning) {
        if (useColors) {
            output.println(YELLOW + "⚠️  " + warning + RESET);
        } else {
            output.println("[WARNING] " + warning);
        }
    }

    /**
     * Render success message
     */
    public void renderSuccess(String message) {
        if (useColors) {
            output.println(GREEN + "✅ " + message + RESET);
        } else {
            output.println("[SUCCESS] " + message);
        }
    }

    /**
     * Render player state
     */
    public void renderPlayerState(String name, int life, int handSize, int librarySize, int manaPool) {
        StringBuilder sb = new StringBuilder();
        sb.append("  ").append(name).append(": ");

        // Life
        if (useUnicode) {
            sb.append(UNICODE_HEART);
        } else {
            sb.append("Life:");
        }
        sb.append(" ").append(formatLife(life)).append(" | ");

        // Hand
        if (useUnicode) {
            sb.append(UNICODE_CARD);
        } else {
            sb.append("Hand:");
        }
        sb.append(" ").append(handSize).append(" | ");

        // Library
        sb.append("Library: ").append(librarySize).append(" | ");

        // Mana
        if (useUnicode) {
            sb.append(UNICODE_MANA);
        } else {
            sb.append("Mana:");
        }
        sb.append(" ").append(manaPool);

        output.println(sb.toString());
    }

    /**
     * Render list with custom formatter
     */
    public <T> void renderList(String title, List<T> items, Function<T, String> formatter) {
        if (items.isEmpty()) {
            return;
        }

        output.println("    " + title + ":");
        for (T item : items) {
            output.println("      - " + formatter.apply(item));
        }
    }

    /**
     * Render event with timestamp
     */
    public void renderEvent(long timestamp, String message) {
        String time = timestampFormat.format(new Date(timestamp));
        output.println("  [" + time + "] " + message);
    }

    /**
     * Render phase change
     */
    public void renderPhaseChange(PhaseType phase, String activePlayer) {
        String icon = getPhaseIcon(phase);
        String message = icon + " " + phase + " - " + activePlayer;

        if (useColors) {
            output.println(PURPLE + message + RESET);
        } else {
            output.println(message);
        }
    }

    /**
     * Render game over
     */
    public void renderGameOver(String winner) {
        renderSeparator();
        if (useColors) {
            output.println(BOLD + GREEN + "🏆 GAME OVER - Winner: " + winner + " 🏆" + RESET);
        } else {
            output.println("*** GAME OVER - Winner: " + winner + " ***");
        }
        renderSeparator();
    }

    /**
     * Render simulation info
     */
    public void renderSimulationInfo(SimulationEngine.SimulationStats stats) {
        renderSubSeparator();
        renderSubtitle("Simulation Status");
        renderKeyValue("Turns Simulated", String.valueOf(stats.turnsSimulated));
        renderKeyValue("Total Actions", String.valueOf(stats.totalActions));
        renderKeyValue("AI Decisions", String.valueOf(stats.aiDecisions));
    }

    /**
     * Render progress bar
     */
    public void renderProgressBar(String label, int current, int total) {
        int barWidth = 40;
        int progress = (int) ((double) current / total * barWidth);

        StringBuilder bar = new StringBuilder();
        bar.append(label).append(" [");

        for (int i = 0; i < barWidth; i++) {
            if (i < progress) {
                bar.append(useUnicode ? "█" : "=");
            } else {
                bar.append(useUnicode ? "░" : "-");
            }
        }

        bar.append("] ");
        bar.append(String.format("%d/%d (%.1f%%)", current, total, (double) current / total * 100));

        output.println(bar.toString());
    }

    /**
     * Render table
     */
    public void renderTable(String[] headers, List<String[]> rows) {
        // Calculate column widths
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }

        for (String[] row : rows) {
            for (int i = 0; i < row.length && i < widths.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }

        // Render headers
        StringBuilder headerLine = new StringBuilder();
        for (int i = 0; i < headers.length; i++) {
            headerLine.append(String.format("%-" + widths[i] + "s", headers[i]));
            if (i < headers.length - 1) {
                headerLine.append(" | ");
            }
        }

        if (useColors) {
            output.println(BOLD + headerLine.toString() + RESET);
        } else {
            output.println(headerLine.toString());
        }

        // Render separator
        StringBuilder separator = new StringBuilder();
        for (int i = 0; i < headers.length; i++) {
            separator.append("-".repeat(widths[i]));
            if (i < headers.length - 1) {
                separator.append("-+-");
            }
        }
        output.println(separator.toString());

        // Render rows
        for (String[] row : rows) {
            StringBuilder rowLine = new StringBuilder();
            for (int i = 0; i < row.length && i < widths.length; i++) {
                rowLine.append(String.format("%-" + widths[i] + "s", row[i]));
                if (i < row.length - 1) {
                    rowLine.append(" | ");
                }
            }
            output.println(rowLine.toString());
        }
    }

    /**
     * Render ASCII box
     */
    public void renderBox(String title, List<String> content) {
        int maxWidth = title.length();
        for (String line : content) {
            maxWidth = Math.max(maxWidth, line.length());
        }

        // Top border
        output.println("┌─" + "─".repeat(maxWidth) + "─┐");

        // Title
        output.println("│ " + centerText(title, maxWidth) + " │");

        // Separator
        output.println("├─" + "─".repeat(maxWidth) + "─┤");

        // Content
        for (String line : content) {
            output.println("│ " + String.format("%-" + maxWidth + "s", line) + " │");
        }

        // Bottom border
        output.println("└─" + "─".repeat(maxWidth) + "─┘");
    }

    /**
     * Format life total with color
     */
    private String formatLife(int life) {
        if (!useColors) {
            return String.valueOf(life);
        }

        if (life >= 15) {
            return GREEN + life + RESET;
        } else if (life >= 7) {
            return YELLOW + life + RESET;
        } else {
            return RED + life + RESET;
        }
    }

    /**
     * Get phase icon
     */
    private String getPhaseIcon(PhaseType phase) {
        if (!useUnicode) {
            return ">";
        }

        switch (phase) {
            case UNTAP: return "♻️";
            case UPKEEP: return "⏰";
            case DRAW: return "🎴";
            case MAIN1:
            case MAIN2: return "🎯";
            case COMBAT_BEGIN:
            case COMBAT_DECLARE_ATTACKERS:
            case COMBAT_DECLARE_BLOCKERS:
            case COMBAT_FIRST_STRIKE_DAMAGE:
            case COMBAT_DAMAGE:
            case COMBAT_END: return "⚔️";
            case END_OF_TURN: return "🔚";
            case CLEANUP: return "🧹";
            default: return "📍";
        }
    }

    /**
     * Center text within width
     */
    private String centerText(String text, int width) {
        if (text.length() >= width) {
            return text;
        }

        int padding = (width - text.length()) / 2;
        String padded = " ".repeat(padding) + text;
        return String.format("%-" + width + "s", padded);
    }

    // Configuration methods

    public void setConsoleWidth(int width) {
        this.consoleWidth = width;
    }

    public void setCompactMode(boolean compact) {
        this.compactMode = compact;
    }

    public void setShowTimestamps(boolean show) {
        this.showTimestamps = show;
    }

    public PrintStream getOutput() {
        return output;
    }
}