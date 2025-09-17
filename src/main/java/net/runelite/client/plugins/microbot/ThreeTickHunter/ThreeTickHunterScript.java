package net.runelite.client.plugins.microbot.ThreeTickHunter;

import lombok.Getter;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity; // Neuer Import für die Intensität
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.ThreeTickHunter.ThreeTickHunterConfig.HuntingLocation.PISCARILIUS_GREY_CHINS;

public class ThreeTickHunterScript extends Script {

    // --- Statistik-Felder für das Overlay ---
    @Getter
    private long startHunterXp = 0;
    public int trapsCaught = 0;

    // --- Konfigurations- und Zustandsfelder ---
    private ThreeTickHunterConfig config;
    private List<WorldPoint> currentTrapLocations = Collections.emptyList();
    private WorldPoint huntingAreaCenter = null;

    // Object IDs für die verschiedenen Fallenzustände
    private static final int TRAP_SET = 19187;
    private static final int TRAP_SHAKING = 19189;
    private static final int TRAP_FAILED = 19192;

    // Item ID für die Box-Falle
    private static final int ITEM_ID_BOX_TRAP = 10008;

    public boolean run(ThreeTickHunterConfig config) {
        this.config = config;
        this.startHunterXp = Microbot.getClient().getSkillExperience(Skill.HUNTER);
        this.trapsCaught = 0;

        // KORREKTE ANTIBAN INITIALISIERUNG
        // Setze die Basis-Aktivität auf Chinchompa-Jagen
        Rs2Antiban.setActivity(Activity.HUNTING_CHINCHOMPAS);
        // Überschreibe die Intensität, da 3-Ticking eine extrem hohe Anforderung hat
        Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);

        initializeLocations(config.huntingLocation());

        if (huntingAreaCenter == null) {
            Microbot.log("Kein gültiger Jagdort ausgewählt. Skript wird beendet.");
            return false;
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::mainLoop, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private void mainLoop() {
        try {
            if (Rs2Antiban.getTIMEOUT() > 0) {
                Rs2Antiban.setTIMEOUT(Rs2Antiban.getTIMEOUT() - 1);
                return;
            }

            if (!Microbot.isLoggedIn() || !super.run()) return;

            if (Rs2Player.getWorldLocation().distanceTo(huntingAreaCenter) > 15) {
                Rs2Walker.walkTo(huntingAreaCenter);
                return;
            }

            performTickManipulation();

            boolean actionPerformed = handleFinishedTraps();

            if (!actionPerformed) {
                actionPerformed = layNewTraps();
            }

            if (actionPerformed) {
                Rs2Antiban.actionCooldown();
            }

        } catch (Exception ex) {
            Microbot.log("[ThreeTickHunter] Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void performTickManipulation() {
        if (config.tickMethod() == ThreeTickHunterConfig.TickManipulationMethod.TEAK_LOGS) {
            if (Rs2Inventory.hasItem(ItemID.TEAK_LOGS) && Rs2Inventory.hasItem(ItemID.KNIFE)) {
                Rs2Inventory.combine(ItemID.KNIFE, ItemID.TEAK_LOGS);
            }
        } else if (config.tickMethod() == ThreeTickHunterConfig.TickManipulationMethod.HERB_AND_TAR) {
            if (Rs2Inventory.hasItem(ItemID.SWAMP_TAR) && Rs2Inventory.hasItem(ItemID.GUAM_LEAF)) {
                Rs2Inventory.combine(ItemID.GUAM_LEAF, ItemID.SWAMP_TAR);
            }
        }
        sleep(100, 150);
    }

    private boolean handleFinishedTraps() {
        for (WorldPoint location : currentTrapLocations) {
            TileObject trap = Rs2GameObject.findObjectByLocation(location);
            if (trap == null) continue;

            if (trap.getId() == TRAP_SHAKING) {
                Rs2GameObject.interact(trap, "Check");
                sleepUntil(() -> Rs2GameObject.findObjectByLocation(location) == null, 2400);
                trapsCaught++;
                return true;
            }



            if (trap.getId() == TRAP_FAILED) {
                Rs2GameObject.interact(trap, "Dismantle");
                sleepUntil(() -> Rs2GameObject.findObjectByLocation(location) == null, 2400);
                return true;
            }
        }
        return false;
    }

    private boolean layNewTraps() {
        int maxTraps = getMaxTraps();
        long laidTrapsCount = currentTrapLocations.stream()
                .map(Rs2GameObject::findObjectByLocation)
                .filter(obj -> obj != null && (obj.getId() == TRAP_SET || obj.getId() == TRAP_SHAKING))
                .count();

        if (laidTrapsCount < maxTraps && Rs2Inventory.hasItem(ITEM_ID_BOX_TRAP)) {
            for (WorldPoint location : currentTrapLocations) {
                if (Rs2GameObject.findObjectByLocation(location) == null) {
                    if (Rs2Inventory.use(ITEM_ID_BOX_TRAP)) {
                        if (sleepUntil(Rs2Inventory::isItemSelected, 1000)) {
                            LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient(), location);
                            if (localPoint != null) {
                                Point tilePoint = Perspective.localToCanvas(Microbot.getClient(), localPoint, Microbot.getClient().getPlane());
                                if (tilePoint != null) {
                                    Microbot.getMouse().click(tilePoint);
                                    sleepUntil(() -> Rs2GameObject.findObjectByLocation(location) != null, 2400);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void initializeLocations(ThreeTickHunterConfig.HuntingLocation location) {
        switch (location) {
            case FELDIP_HILLS_RED_CHINS:
                this.huntingAreaCenter = new WorldPoint(2557, 2908, 0);
                this.currentTrapLocations = List.of(
                        new WorldPoint(2555, 2908, 0),
                        new WorldPoint(2557, 2910, 0),
                        new WorldPoint(2559, 2908, 0),
                        new WorldPoint(2557, 2906, 0),
                        new WorldPoint(2557, 2912, 0)
                );
                break;
            case PISCARILIUS_GREY_CHINS:
                this.huntingAreaCenter = new WorldPoint(1759, 3862, 0);
                this.currentTrapLocations = List.of(
                        // Hier die Koordinaten für graue Chins hinzufügen
                );
                break;
        }
    }

    private int getMaxTraps() {
        int level = Microbot.getClient().getRealSkillLevel(Skill.HUNTER);
        if (level >= 80) return 5;
        if (level >= 60) return 4;
        if (level >= 40) return 3;
        if (level >= 20) return 2;
        return 1;
    }
}