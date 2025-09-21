package net.runelite.client.plugins.microbot.ThreeTickHunter;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.MicrobotPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.Default + "3-Tick Hunter",
        description = "Microbot 3-Tick Hunter plugin",
        tags = {"hunter", "microbot", "skilling"},
        minClientVersion = "1.9.8",
        enabledByDefault = false
)
public class ThreeTickHunterPlugin extends MicrobotPlugin {

    @Inject
    private ThreeTickHunterConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ThreeTickHunterOverlay overlay;

    @Getter
    private ThreeTickHunterScript script;
    @Getter
    private Instant startTime;

    @Provides
    ThreeTickHunterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ThreeTickHunterConfig.class);
    }

    @Override
    protected void startUp() {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script = new ThreeTickHunterScript();
        startTime = Instant.now();
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}