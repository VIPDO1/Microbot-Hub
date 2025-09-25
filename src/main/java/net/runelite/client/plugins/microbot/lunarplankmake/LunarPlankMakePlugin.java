package net.runelite.client.plugins.microbot.lunarplankmake;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.GeoffPlugins.lunarplankmake.LunarPlankMakeConfig;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Geoff + "Lunar Plank Make",
        description = "Geoff's lunar plank maker",
        tags = {"magic", "moneymaking"},
        version = LunarPlankMakePlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class LunarPlankMakePlugin extends Plugin {
    public static final String version = "1.0.1";
    @Inject
    private net.runelite.client.plugins.microbot.GeoffPlugins.lunarplankmake.LunarPlankMakeConfig config;

    @Provides
    net.runelite.client.plugins.microbot.GeoffPlugins.lunarplankmake.LunarPlankMakeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(LunarPlankMakeConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private net.runelite.client.plugins.microbot.GeoffPlugins.lunarplankmake.LunarPlankMakeOverlay LunarPlankMakeOverlay;

    @Inject
    net.runelite.client.plugins.microbot.GeoffPlugins.lunarplankmake.LunarPlankMakeScript LunarPlankMakeScript;

    @Override
    protected void startUp() throws AWTException {
        log.info("Starting up LunarPlankMakePlugin");
        if (overlayManager != null) {
            overlayManager.add(LunarPlankMakeOverlay);
        }
        LunarPlankMakeScript.run(config);
    }

    @Override
    protected void shutDown() {
        log.info("Shutting down LunarPlankMakePlugin");
        LunarPlankMakeScript.shutdown();
        overlayManager.remove(LunarPlankMakeOverlay);
    }
}
