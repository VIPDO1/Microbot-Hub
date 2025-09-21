package net.runelite.client.plugins.microbot.ThreeTickHunter;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;

@PluginDescriptor(
        name = "Microbot 3-Tick Hunter",
        description = "A Microbot plugin for 3-tick box trapping.",
        tags = {"microbot", "hunter", "skilling", "3-tick"},
        enabledByDefault = false
)
@Slf4j
public class ThreeTickHunterPlugin extends Plugin {

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
    protected void startUp() throws Exception {
        log.info("Microbot 3-Tick Hunter started!");
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        startTime = Instant.now();
        script = new ThreeTickHunterScript();
        script.run(config);
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("Microbot 3-Tick Hunter stopped!");
        if (script != null) {
            script.shutdown();
            script = null;
        }
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
        startTime = null;
    }
}