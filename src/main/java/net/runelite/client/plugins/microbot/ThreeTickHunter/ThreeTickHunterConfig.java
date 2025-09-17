package net.runelite.client.plugins.microbot.ThreeTickHunter; // Stelle sicher, dass der Paketname korrekt ist

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("ThreeTickHunter")
@ConfigInformation("<html>"
        + "<h2 style='color: #6d9eeb;'>3-Tick Hunter by Microbot</h2>"
        + "<p>This plugin automates 3-tick box trapping for Hunter training.</p>\n"
        + "<p><b>Instructions:</b> Select your hunting location and your preferred tick manipulation method. The script will run until stopped.</p>\n"
        + "</html>")
public interface ThreeTickHunterConfig extends Config {

    @RequiredArgsConstructor
    @Getter
    enum HuntingLocation {
        FELDIP_HILLS_RED_CHINS("Feldip Hills (Red Chins)"),
        PISCARILIUS_GREY_CHINS("Piscatorius (Grey Chins)"),
        WILDERNESS_BLACK_CHINS("Wilderness (Black Chins)");

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

    @ConfigItem(
            keyName = "huntingLocation",
            name = "Hunting Location",
            description = "Select where you want to hunt.",
            position = 0
    )
    default HuntingLocation huntingLocation() {
        return HuntingLocation.FELDIP_HILLS_RED_CHINS;
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