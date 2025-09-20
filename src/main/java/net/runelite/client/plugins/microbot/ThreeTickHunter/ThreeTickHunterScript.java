package net.runelite.client.plugins.microbot.ThreeTickHunter;

import lombok.Getter;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ThreeTickHunterScript extends Script {

    @Getter
    private long startHunterXp = 0;
    public int trapsCaught = 0;
    private ThreeTickHunterConfig config;
    private int tickCounter = 0;
    private boolean initialTrapsLaid = false;

    private static final WorldArea HUNTING_AREA = new WorldArea(2510, 9285, 25, 25, 0);
    private static final WorldPoint HUNTING_AREA_CENTER = new WorldPoint(2522, 9297, 0);

    private static final int TRAP_BOX_SET_1 = 9380;
    private static final int TRAP_BOX_SET_2 = 9385;
    private static final int TRAP_SHAKING = 9383;
    private static final int TRAP_FAILED = 9384;
    private static final int ITEM_ID_BOX_TRAP = 10008;

    public boolean run(ThreeTickHunterConfig config) {
        this.config = config;
        this.startHunterXp = Microbot.getClient().getSkillExperience(Skill.HUNTER);
        this.trapsCaught = 0;
        this.tickCounter = 0;
        this.initialTrapsLaid = false;

        Rs2Antiban.setActivity(Activity.HUNTING_CHINCHOMPAS);
        Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);

        Microbot.getEventBus().register(this);
        return true;
    }

    @Override
    public void shutdown() {
        Microbot.getEventBus().unregister(this);
        super.shutdown();
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        try {
            if (!Microbot.isLoggedIn() || !super.run() || Rs2Antiban.getTIMEOUT() > 0) return;

            if (!HUNTING_AREA.contains(Rs2Player.getWorldLocation())) {
                Microbot.log("Walking to hunting area...");
                Rs2Walker.walkTo(HUNTING_AREA_CENTER);
                return;
            }

            if (!initialTrapsLaid) {
                handleInitialSetup();
            } else {
                handleThreeTickCycle();
            }
        } catch (Exception ex) {
            Microbot.log("[ThreeTickHunter] Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void handleInitialSetup() {
        long laidTraps = countLaidTraps();
        if (laidTraps >= getMaxTraps()) {
            Microbot.log("Initial trap setup complete. Starting 3-tick cycle.");
            initialTrapsLaid = true;
            tickCounter = 0;
            return;
        }

        Microbot.log("Setting up initial traps (" + laidTraps + "/" + getMaxTraps() + ")");
        if (!handleFinishedTraps()) {
            if (!Rs2Player.isMoving() && !Rs2Player.isAnimating()) {
                layNewTraps();
                sleep(1200, 1800);
            }
        }
    }

    private void handleThreeTickCycle() {
        if (tickCounter % 3 == 0) {
            boolean actionPerformed = handleFinishedTraps() || layNewTraps();
            if (actionPerformed) {
                Rs2Antiban.actionCooldown();
            }
        } else if (tickCounter % 3 == 1) {
            performTickManipulation();
        }
        tickCounter++;
    }

    private void performTickManipulation() {
        if (config.tickMethod() == ThreeTickHunterConfig.TickManipulationMethod.TEAK_LOGS) {
            if (Rs2Inventory.hasItem(ItemID.TEAK_LOGS) && Rs2Inventory.hasItem(ItemID.KNIFE)) {
                Rs2Inventory.combine(ItemID.KNIFE, ItemID.TEAK_LOGS);
                Rs2Antiban.actionCooldown();
            }
        } else if (config.tickMethod() == ThreeTickHunterConfig.TickManipulationMethod.HERB_AND_TAR) {
            if (Rs2Inventory.hasItem(ItemID.SWAMP_TAR) && Rs2Inventory.hasItem(ItemID.GUAM_LEAF)) {
                Rs2Inventory.combine(ItemID.GUAM_LEAF, ItemID.SWAMP_TAR);
                Rs2Antiban.actionCooldown();
            }
        }
    }

    private boolean handleFinishedTraps() {
        List<TileObject> finishedTraps = Rs2GameObject.getAll(trap -> {
            int id = trap.getId();
            return (id == TRAP_SHAKING || id == TRAP_FAILED) && HUNTING_AREA.contains(trap.getWorldLocation());
        });

        if (!finishedTraps.isEmpty()) {
            finishedTraps.sort(Comparator.comparingInt(t -> t.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())));
            TileObject trapToHandle = finishedTraps.get(0);
            String action = trapToHandle.getId() == TRAP_SHAKING ? "Check" : "Dismantle";

            if (action.equals("Check") && Rs2Inventory.isFull()) {
                Microbot.log("Inventory is full, cannot check trap.");
                return false;
            }

            Rs2GameObject.interact(trapToHandle, action);
            if (action.equals("Check")) trapsCaught++;
            return true;
        }
        return false;
    }

    // NEUE HILFSMETHODE: Löst den "Lay"-Befehl direkt im Inventar aus
    private boolean layTrapDirect() {
        Rs2ItemModel trap = Rs2Inventory.get(ITEM_ID_BOX_TRAP);
        if (trap == null) return false;

        // Hier wird die "Lay"-Aktion direkt aufgerufen
        boolean success = Rs2Inventory.interact(trap, "Lay");
        if (success) {
            WorldPoint pos = Rs2Player.getWorldLocation();
            // Wartet, bis das Fallen-Objekt auf dem Spieler-Tile erscheint
            return sleepUntil(() -> Rs2GameObject.findObjectByLocation(pos) != null, 2500);
        }
        return false;
    }

    // ÜBERARBEITETE METHODE: Nutzt die neue, direkte "Lay"-Aktion
    private boolean layNewTraps() {
        if (countLaidTraps() >= getMaxTraps() || !Rs2Inventory.hasItem(ITEM_ID_BOX_TRAP)) {
            return false;
        }

        WorldPoint playerPos = Rs2Player.getWorldLocation();
        boolean currentTileIsSafe = Rs2Tile.isWalkable(playerPos) && Rs2GameObject.findObjectByLocation(playerPos) == null;

        if (currentTileIsSafe) {
            // Wenn der aktuelle Platz sicher ist, lege die Falle direkt
            return layTrapDirect();
        } else {
            // Ansonsten, suche einen neuen Platz und laufe dorthin
            Optional<WorldPoint> bestSpot = HUNTING_AREA.toWorldPointList().stream()
                    .filter(p -> p.distanceTo(playerPos) <= 7)
                    .filter(Rs2Tile::isWalkable)
                    .filter(p -> Rs2GameObject.findObjectByLocation(p) == null)
                    .min(Comparator.comparingInt(p -> p.distanceTo(playerPos)));

            bestSpot.ifPresent(spot -> {
                Microbot.log("Current tile is blocked, walking to a new spot.");
                Rs2Walker.walkTo(spot);
            });
        }
        return false;
    }

    private long countLaidTraps() {
        return Rs2GameObject.getAll(trap -> {
            int id = trap.getId();
            return (id == TRAP_BOX_SET_1 || id == TRAP_BOX_SET_2 || id == TRAP_SHAKING) && HUNTING_AREA.contains(trap.getWorldLocation());
        }).size();
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