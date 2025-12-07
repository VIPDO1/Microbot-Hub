package net.runelite.client.plugins.microbot.ektotokens;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class EctoTokenOverlay extends OverlayPanel {

    private final EctoTokenPlugin plugin;
    private final EctoTokenConfig config;

    @Inject
    EctoTokenOverlay(EctoTokenPlugin plugin, EctoTokenConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            String titleText = "Microbot Ecto AIO";
            if (config.craftBonemeal()) {
                titleText = "Microbot Bonemeal";
            } else if (config.craftBucketOfSlime()) {
                titleText = "Microbot Slime";
            }

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text(titleText)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Running Time:")
                    .right(plugin.getTimeRunning())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(plugin.getScript().state.toString())
                    .rightColor(Color.CYAN)
                    .build());

            if (config.craftBucketOfSlime()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Slime Collected:")
                        .right(String.valueOf(plugin.getScript().slimeCollected))
                        .build());
            }

            if (config.craftBonemeal()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Bonemeal Ground:")
                        .right(String.valueOf(plugin.getScript().bonemealGround))
                        .build());
            }

        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}