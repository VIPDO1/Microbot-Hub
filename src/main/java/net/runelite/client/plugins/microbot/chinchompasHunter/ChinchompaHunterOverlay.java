package net.runelite.client.plugins.microbot.chinchompasHunter;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;

public class ChinchompaHunterOverlay extends OverlayPanel {

    private final ChinchompaHunterPlugin plugin;

    @Inject
    ChinchompaHunterOverlay(ChinchompaHunterPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        ChinchompaHunterScript script = plugin.getScript();
        if (script == null || !script.isRunning()) return null;

        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Microbot Chinchompa Hunter")
                .color(Color.ORANGE)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(script.getCurrentState().toString())
                .rightColor(getStateColor(script.getCurrentState()))
                .build());

        if (plugin.getStartTime() == null || script.getStartHunterXp() == 0) {
            return super.render(graphics);
        }

        long elapsedMillis = Duration.between(plugin.getStartTime(), Instant.now()).toMillis();
        String timeRunning = formatDuration(elapsedMillis);

        long xpGained = Microbot.getClient().getSkillExperience(Skill.HUNTER) - script.getStartHunterXp();
        long xpPerHour = (long) (xpGained * 3_600_000.0 / (elapsedMillis > 0 ? elapsedMillis : 1));

        int trapsCaught = script.getTrapsCaught();
        long trapsPerHour = (long) (trapsCaught * 3_600_000.0 / (elapsedMillis > 0 ? elapsedMillis : 1));

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time Running:")
                .right(timeRunning)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Hunter Lvl:")
                .right(String.valueOf(Microbot.getClient().getRealSkillLevel(Skill.HUNTER)))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("XP Gained (p/h):")
                .right(formatNumber(xpGained) + " (" + formatNumber(xpPerHour) + ")")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Traps Caught (p/h):")
                .right(formatNumber(trapsCaught) + " (" + formatNumber(trapsPerHour) + ")")
                .build());

        return super.render(graphics);
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000) return String.format("%.1fm", number / 1_000_000.0);
        if (number >= 1000) return String.format("%.1fk", number / 1000.0);
        return String.valueOf(number);
    }

    private String formatDuration(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = millis / (1000 * 60 * 60);
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * ✅ KORREKTUR: Farben an die exakten Zustände der V6 angepasst.
     */
    private Color getStateColor(ChinchompaHunterScript.State state) {
        if (state == null) return Color.WHITE;
        switch (state) {
            case CHECKING_TRAPS:
                return Color.CYAN;          // Hellblau für den Fang
            case RELEASING_CHINS:
                return Color.GREEN;         // Grün für Inventar-Aktionen
            case SETTING_UP:
                return new Color(173, 216, 230); // Hellblau für den Aufbau
            case WAITING:
                return Color.ORANGE;        // Orange für passives Warten
            case RESETTING_STALE_TRAPS:
                return Color.MAGENTA;       // Magenta für Wartungsaktionen
            case IDLE:
            default:
                return Color.WHITE;
        }
    }
}