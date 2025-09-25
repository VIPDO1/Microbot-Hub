package net.runelite.client.plugins.microbot.construction;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.GeoffPlugins.construction2.Construction2Config;
import net.runelite.client.plugins.microbot.GeoffPlugins.construction2.Construction2Overlay;
import net.runelite.client.plugins.microbot.GeoffPlugins.construction2.Construction2Script;
import net.runelite.client.plugins.microbot.GeoffPlugins.construction2.enums.Construction2State;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Geoff + "Construction 2",
        description = "Geoff's Microbot construction plugin with added new bits.",
        tags = {"skilling", "microbot", "construction"},
        version = ConstructionPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class ConstructionPlugin extends Plugin {
    public static final String version = "1.3.1";

    @Inject
    private net.runelite.client.plugins.microbot.GeoffPlugins.construction2.Construction2Config config;

    @Provides
    net.runelite.client.plugins.microbot.GeoffPlugins.construction2.Construction2Config provideConfig(ConfigManager configManager) {
        return configManager.getConfig(Construction2Config.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private Construction2Overlay construction2Overlay;

    private final net.runelite.client.plugins.microbot.GeoffPlugins.construction2.Construction2Script construction2Script = new Construction2Script();

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(construction2Overlay);
        }
        construction2Script.run(config);
    }

    @Override
    protected void shutDown() {
        construction2Script.shutdown();
        overlayManager.remove(construction2Overlay);
    }

    public Construction2State getState() {
        return construction2Script.getState();
    }
}
