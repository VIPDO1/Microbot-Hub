package net.runelite.client.plugins.microbot.ThreeTickHunter;

import com.google.inject.Provides;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotPlugin;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "3-Tick Hunter",
        description = "Microbot 3-tick hunter plugin for chinchompas",
        tags = {"hunter", "microbot", "chinchompa", "skilling"},
        enabledByDefault = false
)
public class ThreeTickHunterPlugin extends MicrobotPlugin {

    @Inject
    private ThreeTickHunterConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ThreeTickHunterOverlay overlay;
    @Inject
    private ThreeTickHunterScript script;

    private Instant scriptStartTime;

    @Provides
    ThreeTickHunterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ThreeTickHunterConfig.class);
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

    public ThreeTickHunterScript getScript() {
        return this.script;
    }

    // --- Helper Methods for the Overlay ---

    public String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "00:00:00";
    }

    public long getHunterXpGained() {
        long startXp = script.getStartHunterXp();
        if (startXp == 0) return 0;
        return Microbot.getClient().getSkillExperience(Skill.HUNTER) - startXp;
    }

    public long getXpPerHour() {
        if (scriptStartTime == null) return 0;
        long secondsElapsed = Duration.between(scriptStartTime, Instant.now()).getSeconds();
        if (secondsElapsed <= 0) return 0;
        return (getHunterXpGained() * 3600) / secondsElapsed;
    }

    public int getTrapsCaught() {
        return script.getTrapsCaught();
    }

    public long getTrapsPerHour() {
        if (scriptStartTime == null) return 0;
        long secondsElapsed = Duration.between(scriptStartTime, Instant.now()).getSeconds();
        if (secondsElapsed <= 0) return 0;
        return (long)(getTrapsCaught() * 3600) / secondsElapsed;
    }

    public String getCurrentState() {
        if (script.getCurrentState() != null) {
            return script.getCurrentState().name().replace('_', ' ');
        }
        return "LOADING...";
    }
}