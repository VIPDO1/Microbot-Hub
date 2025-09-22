package net.runelite.client.plugins.microbot.ChinchompasHunter;

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
import java.time.Duration;
import java.time.Instant;

public class ChinchompaHunterOverlay extends OverlayPanel {

    private final ChinchompaHunterPlugin plugin;
    private final Client client;

    private static final Color ARENA_COLOR = new Color(0, 255, 255, 50);
    private static final Color TRAP_COLOR = new Color(255, 255, 0, 100);
    private static final Color OPERATING_TILE_COLOR = new Color(0, 255, 0, 100);

    @Inject
    ChinchompaHunterOverlay(ChinchompaHunterPlugin plugin, Client client) {
        super(plugin);
        this.plugin = plugin;
        this.client = client;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        ChinchompaHunterScript script = plugin.getScript();
        if (script == null || !script.isRunning()) return null;

        WorldArea huntingArea = script.getHUNTING_AREA();
        if (huntingArea != null) {
            drawArea(graphics, huntingArea, ARENA_COLOR);
        }

        for (WorldPoint trapLocation : script.getTrapLocations()) {
            drawTile(graphics, trapLocation, TRAP_COLOR);
        }

        if (script.getOperatingTile() != null) {
            drawTile(graphics, script.getOperatingTile(), OPERATING_TILE_COLOR);
        }

        panelComponent.setPreferredSize(new Dimension(270, 300));
        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Microbot Chinchompa Hunter")
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(script.getCurrentState().toString())
                .rightColor(getStateColor(script.getCurrentState()))
                .build());

        if (plugin.getStartTime() == null) return super.render(graphics);

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

    private Color getStateColor(ChinchompaHunterScript.State state) {
        if (state == null) return Color.WHITE;
        switch (state) {
            case CHECKING_TRAPS: return Color.GREEN;
            case PLACING_TRAPS: return Color.YELLOW;
            case WAITING: return Color.CYAN;
            case STOPPED: return Color.RED;
            default: return Color.WHITE;
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
        LocalPoint lp = LocalPoint.fromWorld(client, area.toWorldPoint());
        if (lp == null || client.getLocalPlayer() == null) return;
        if (lp.distanceTo(client.getLocalPlayer().getLocalLocation()) > 4000) return;

        for (int x = 0; x < area.getWidth(); x++) {
            for (int y = 0; y < area.getHeight(); y++) {
                WorldPoint tile = new WorldPoint(area.getX() + x, area.getY() + y, area.getPlane());
                drawTile(graphics, tile, color);
            }
        }
    }
}