package net.runelite.client.plugins.microbot.ThreeTickHunter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("threeTickHunter")
public interface ThreeTickHunterConfig extends Config {

    enum TickManipulationMethod {
        TEAK_LOGS,
        HERB_AND_TAR
    }

    @ConfigItem(
            keyName = "tickMethod",
            name = "3-Tick Methode",
            description = "Wähle die Methode für das 3-Ticking.",
            position = 1
    )
    default TickManipulationMethod tickMethod() {
        return TickManipulationMethod.TEAK_LOGS;
    }
}