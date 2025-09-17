package net.runelite.client.plugins.microbot.ThreeTickHunter; // Stelle sicher, dass der Paketname korrekt ist

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

public class ThreeTickHunterOverlay extends OverlayPanel {

    private final ThreeTickHunterPlugin plugin;
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);

    @Inject
    ThreeTickHunterOverlay(ThreeTickHunterPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(270, 300));
            panelComponent.getChildren().clear();

            // --- Title ---
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Microbot 3-Tick Hunter")
                    .color(Color.GREEN)
                    .build());

            // --- Time Running ---
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time Running:")
                    .right(plugin.getTimeRunning())
                    .build());

            // --- Current Level ---
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Hunter Level:")
                    .right(String.valueOf(Microbot.getClient().getRealSkillLevel(net.runelite.api.Skill.HUNTER)))
                    .build());

            // --- XP Gained and Per Hour ---
            long xpGained = plugin.getHunterXpGained();
            long xpPerHour = plugin.getHunterXpPerHour();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP Gained (p/h):")
                    .right(formatNumber(xpGained) + " (" + formatNumber(xpPerHour) + ")")
                    .build());

            // --- Traps Caught and Per Hour ---
            int trapsCaught = plugin.getTrapsCaught();
            int trapsPerHour = plugin.getTrapsPerHour();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Traps Caught (p/h):")
                    .right(formatNumber(trapsCaught) + " (" + formatNumber(trapsPerHour) + ")")
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }

    /**
     * Formatiert eine Zahl für eine bessere Lesbarkeit (z.B. 1000 -> 1k, 1000000 -> 1m).
     */
    private String formatNumber(long number) {
        if (number < 1000) {
            return String.valueOf(number);
        }
        if (number < 1_000_000) {
            return number / 1000 + "k";
        }
        return number / 1_000_000 + "m";
    }
}