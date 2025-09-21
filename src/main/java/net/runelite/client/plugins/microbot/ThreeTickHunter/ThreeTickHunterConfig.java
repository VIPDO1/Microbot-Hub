package net.runelite.client.plugins.microbot.ThreeTickHunter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("ThreeTickHunter")
@ConfigInformation("<html>"
        + "<h2 style='color: #6d9eeb;'>3-Tick Hunter by Microbot</h2>"
        + "<p>This plugin automates 3-tick hunter for chinchompas.</p>\n"
        + "<p><b>Instructions:</b></p>\n"
        + "<ol>\n"
        + "    <li>Stand in the center of your desired trap setup.</li>\n"
        + "    <li>Have box traps and your 3-tick items (e.g., herb & tar or logs & knife) in your inventory.</li>\n"
        + "    <li>Enable the plugin to start.</li>\n"
        + "</ol>\n"
        + "<p>The script will automatically determine trap locations around you and begin the 3-tick cycle.</p>\n"
        + "</html>")
public interface ThreeTickHunterConfig extends Config {

    enum TickManipulationMethod {
        HERB_AND_TAR,
        TEAK_LOGS
    }

    @ConfigItem(
            keyName = "tickMethod",
            name = "3-Tick Method",
            description = "Choose the items to use for 3-ticking.",
            position = 1
    )
    default TickManipulationMethod tickMethod() {
        return TickManipulationMethod.TEAK_LOGS;
    }
}