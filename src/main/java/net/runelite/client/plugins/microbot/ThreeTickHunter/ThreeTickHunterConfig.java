package net.runelite.client.plugins.microbot.ThreeTickHunter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("ThreeTickHunter")
@ConfigInformation("<html>"
        + "<h2 style='color: #6d9eeb;'>3-Tick Hunter by Microbot</h2>"
        + "<p>This plugin automates 3-tick box trapping in the Jungle Eagle Lair.</p>\n"
        + "<p><b>Requirement:</b> Eagles' Peak quest.</p>\n"
        + "<p><b>Instructions:</b> Select your preferred tick manipulation method.</p>\n"
        + "</html>")
public interface ThreeTickHunterConfig extends Config {

    @RequiredArgsConstructor
    @Getter
    enum HuntingLocation {
        // Nur noch dieser Ort ist relevant
        JUNGLE_EAGLE_LAIR("Jungle Eagle Lair (Red Chins)");

        private final String name;

        @Override
        public String toString() {
            return name;
        }
    }

    @RequiredArgsConstructor
    @Getter
    enum TickManipulationMethod {
        TEAK_LOGS("Teak Logs + Knife"),
        HERB_AND_TAR("Herb + Swamp Tar");

        private final String name;

        @Override
        public String toString() {
            return name;
        }
    }

    // Dieser Config-Punkt bleibt bestehen, damit die Logik im Skript nicht bricht,
    // auch wenn es nur eine Option gibt.
    @ConfigItem(
            keyName = "huntingLocation",
            name = "Hunting Location",
            description = "The script is locked to the Jungle Eagle Lair.",
            position = 0
    )
    default HuntingLocation huntingLocation() {
        return HuntingLocation.JUNGLE_EAGLE_LAIR;
    }

    @ConfigItem(
            keyName = "tickMethod",
            name = "Tick Manipulation Method",
            description = "Select which items to use for 3-ticking.",
            position = 1
    )
    default TickManipulationMethod tickMethod() {
        return TickManipulationMethod.TEAK_LOGS;
    }
}