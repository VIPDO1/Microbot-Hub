package net.runelite.client.plugins.microbot.ThreeTickHunter; // Stelle sicher, dass der Paketname korrekt ist

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
        name = "Microbot - 3-Tick Hunter",
        description = "Automates 3-tick box trapping for Hunter training.",
        tags = {"hunter", "skilling", "microbot", "chinchompa", "3-tick"},
        authors = { "Microbot" },
        minClientVersion = "1.9.8",
        version = "1.0.0",
        enabledByDefault = false
)
@Slf4j
public class ThreeTickHunterPlugin extends Plugin {

    @Inject
    private ThreeTickHunterConfig config;
    @Inject
    private ThreeTickHunterScript threeTickHunterScript;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ThreeTickHunterOverlay threeTickHunterOverlay;

    private Instant scriptStartTime;

    @Provides
    ThreeTickHunterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ThreeTickHunterConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(threeTickHunterOverlay);
        }
        // Übergib die Konfiguration an das Skript und starte es
        threeTickHunterScript.run(config);
    }

    @Override
    protected void shutDown() {
        threeTickHunterScript.shutdown();
        overlayManager.remove(threeTickHunterOverlay);
        scriptStartTime = null;
        // Setze den Zähler für gefangene Tiere zurück
        if (threeTickHunterScript != null) {
            threeTickHunterScript.trapsCaught = 0;
        }
    }

    // --- Methoden zur Statistik-Anzeige im Overlay ---

    public String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    public long getHunterXpGained() {
        long startXp = threeTickHunterScript.getStartHunterXp();
        if (startXp == 0) return 0;

        return Microbot.getClient().getSkillExperience(Skill.HUNTER) - startXp;
    }

    public long getHunterXpPerHour() {
        long xpGained = getHunterXpGained();
        if (scriptStartTime == null || xpGained <= 0) return 0;

        long secondsElapsed = java.time.Duration.between(scriptStartTime, Instant.now()).getSeconds();
        if (secondsElapsed <= 0) return 0;

        return (xpGained * 3600L) / secondsElapsed;
    }

    public int getTrapsCaught() {
        return threeTickHunterScript.trapsCaught;
    }

    public int getTrapsPerHour() {
        int trapsCaught = getTrapsCaught();
        if (scriptStartTime == null || trapsCaught <= 0) return 0;

        long secondsElapsed = java.time.Duration.between(scriptStartTime, Instant.now()).getSeconds();
        if (secondsElapsed <= 0) return 0;

        return (int) ((long) trapsCaught * 3600L / secondsElapsed);
    }
}