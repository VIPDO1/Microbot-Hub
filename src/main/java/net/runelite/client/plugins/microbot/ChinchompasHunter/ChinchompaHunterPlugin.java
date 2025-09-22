package net.runelite.client.plugins.microbot.ChinchompasHunter;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.MicrobotPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + " Chinchompa Hunter",
        description = "Microbot hunter plugin for chinchompas",
        tags = {"hunter", "microbot", "chinchompa", "skilling"},
        minClientVersion = "1.9.8",
        enabledByDefault = false
)
public class ChinchompaHunterPlugin extends MicrobotPlugin {

    @Inject
    private ChinchompaHunterConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ChinchompaHunterOverlay overlay;
    @Inject
    private ChinchompaHunterScript script;

    private Instant scriptStartTime;

    @Provides
    ChinchompaHunterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ChinchompaHunterConfig.class);
    }

    @Override
    protected void startUp() {
        scriptStartTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
        scriptStartTime = null;
    }

    public ChinchompaHunterScript getScript() {
        return this.script;
    }

    // --- Helper Methods for the Overlay ---

    public Instant getStartTime() {
        return scriptStartTime;
    }
}