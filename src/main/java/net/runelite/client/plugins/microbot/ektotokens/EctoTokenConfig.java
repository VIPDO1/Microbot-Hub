package net.runelite.client.plugins.microbot.ektotokens;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigGroup("EctoTokenAIO")
@ConfigInformation("<html>"
        + "<h2 style='color: #6d9eeb;'>EctoToken AIO</h2>"
        + "<p>Wähle den gewünschten Modus durch Aktivieren der Checkboxen.</p>"
        + "<p>Modi:</p>"
        + "<ul>"
        + "    <li><b>Craft Bucket of Slime:</b> Nur Schleim sammeln.</li>"
        + "    <li><b>Craft Bonemeal:</b> Nur Knochen mahlen (Crusher oben).</li>"
        + "    <li><b>Craft Ectotokens:</b> Der volle Ablauf (Worship).</li>"
        + "</ul>"
        + "</html>")
public interface EctoTokenConfig extends Config {

    @ConfigSection(
            name = "Bot Modes",
            description = "Wähle, was der Bot tun soll. Aktiviere nur EINE Option.",
            position = 3,
            closedByDefault = false
    )
    String modeSection = "modeSection";

    @ConfigItem(
            keyName = "craftBucketOfSlime",
            name = "Craft Bucket of Slime",
            description = "Wenn WAHR: Geht in den Keller und füllt Eimer mit Ectofuntus-Schleim.",
            position = 1,
            section = modeSection
    )
    default boolean craftBucketOfSlime() {
        return false;
    }

    @ConfigItem(
            keyName = "craftBonemeal",
            name = "Craft Bonemeal",
            description = "Wenn WAHR: Geht ins Obergeschoss und mahlt Knochen zu Bonemeal.",
            position = 2,
            section = modeSection
    )
    default boolean craftBonemeal() {
        return false;
    }

    @ConfigItem(
            keyName = "craftEctotokens",
            name = "Craft Ectotokens",
            description = "Wenn WAHR: Führt den kompletten Loop aus (Worship), um Tokens zu erhalten.",
            position = 3,
            section = modeSection
    )
    default boolean craftEctotokens() {
        return true;
    }

}