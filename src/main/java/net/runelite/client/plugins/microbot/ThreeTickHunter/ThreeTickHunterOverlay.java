package net.runelite.client.plugins.microbot.ThreeTickHunter;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class ThreeTickHunterOverlay extends OverlayPanel {

    private final ThreeTickHunterPlugin plugin;
    private final Client client;

    private static final Color ARENA_COLOR = new Color(0, 255, 255, 50);
    private static final Color TRAP_COLOR = new Color(255, 255, 0, 100);

    @Inject
    ThreeTickHunterOverlay(ThreeTickHunterPlugin plugin, Client client) {
        super(plugin);
        this.plugin = plugin;
        this.client = client;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        ThreeTickHunterScript script = plugin.getScript();
        if (script == null) return null;

        // --- Ground Marker Rendering ---
        WorldArea huntingArea = script.getHUNTING_AREA();
        if (huntingArea != null) {
            drawArea(graphics, huntingArea, ARENA_COLOR);
        }
        for (WorldPoint trapLocation : script.getTrapLocations()) {
            drawTile(graphics, trapLocation, TRAP_COLOR);
        }

        // --- Info Panel Rendering ---
        panelComponent.setPreferredSize(new Dimension(270, 300));
        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Microbot 3-Tick Hunter")
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(plugin.getCurrentState())
                .rightColor(getStateColor(script.getCurrentState()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time Running:")
                .right(plugin.getTimeRunning())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Hunter Lvl:")
                .right(String.valueOf(Microbot.getClient().getRealSkillLevel(Skill.HUNTER)))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("XP Gained (p/h):")
                .right(formatNumber(plugin.getHunterXpGained()) + " (" + formatNumber(plugin.getXpPerHour()) + ")")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Traps Caught (p/h):")
                .right(formatNumber(plugin.getTrapsCaught()) + " (" + formatNumber(plugin.getTrapsPerHour()) + ")")
                .build());

        return super.render(graphics);
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000) return String.format("%.1fm", number / 1_000_000.0);
        if (number >= 1000) return String.format("%.1fk", number / 1000.0);
        return String.valueOf(number);
    }

    private Color getStateColor(ThreeTickHunterScript.State state) {
        if (state == null) return Color.WHITE;
        switch (state) {
            case THREE_TICK_HUNTING:
                return Color.GREEN;
            case WALKING_TO_AREA:
            case INITIAL_TRAP_SETUP:
                return Color.YELLOW;
            case STOPPED:
                return Color.RED;
            default:
                return Color.WHITE;
        }
    }

    private void drawTile(Graphics2D graphics, WorldPoint point, Color color) {
        if (point == null || point.getPlane() != client.getPlane()) return;
        LocalPoint lp = LocalPoint.fromWorld(client, point);
        if (lp == null) return;
        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly != null) {
            graphics.setColor(color);
            graphics.fill(poly);
        }
    }

    private void drawArea(Graphics2D graphics, WorldArea area, Color color) {
        for (WorldPoint tile : area.toWorldPointList()) {
            drawTile(graphics, tile, color);
        }
    }
}