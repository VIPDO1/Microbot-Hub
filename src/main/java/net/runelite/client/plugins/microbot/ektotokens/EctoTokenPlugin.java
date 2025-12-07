package net.runelite.client.plugins.microbot.ektotokens;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
        name = PluginConstants.VIP + "Ecto Slime Collector",
        description = "Automates collecting buckets of slime.",
        tags = {"slime", "ectofuntus", "microbot", "buckets"},
        authors = { "Microbot User" },
        version = "1.0.0",
        enabledByDefault = false
)
@Slf4j
public class EctoTokenPlugin extends Plugin {

    @Inject
    EctoTokenScript ectoTokenScript;
    @Inject
    private EctoTokenConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private EctoTokenOverlay ectoTokenOverlay;

    private Instant scriptStartTime;

    @Provides
    EctoTokenConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(EctoTokenConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(ectoTokenOverlay);
        }
        ectoTokenScript.run(config);
    }

    @Override
    protected void shutDown() {
        ectoTokenScript.shutdown();
        overlayManager.remove(ectoTokenOverlay);
        scriptStartTime = null;
        ectoTokenScript.slimeCollected = 0;

    }


    public String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "00:00:00";
    }

    public EctoTokenScript getScript() {
        return ectoTokenScript;
    }
}