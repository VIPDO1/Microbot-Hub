package net.runelite.client.plugins.microbot.ThreeTickHunter;

import lombok.Getter;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class ThreeTickHunterScript extends Script {

    @Getter
    public enum State {
        IDLE, SETTING_UP, SETTING_UP_TRAPS, THREE_TICK_HUNTING, STOPPED
    }

    @Getter private State currentState = State.IDLE;
    @Getter private long startHunterXp = 0;
    @Getter private int trapsCaught = 0;
    private int tickCounter = 0;
    private boolean hasInitialized = false;

    // NEU: Wir unterscheiden jetzt zwischen dem Startpunkt (operatingTile) und dem Zentrum des "X" (patternCenterTile)
    @Getter private WorldPoint operatingTile;
    @Getter private WorldPoint patternCenterTile;

    @Getter private final WorldArea HUNTING_AREA = new WorldArea(2510, 9285, 25, 25, 0);
    @Getter private final List<WorldPoint> trapLocations = new ArrayList<>();

    private static final int TRAP_BOX_SET_1 = 9380;
    private static final int TRAP_BOX_SET_2 = 9385;
    private static final int TRAP_SHAKING = 9383;
    private static final int TRAP_FAILED = 9384;
    private static final int BOX_TRAP_ITEM_ID = 10008;

    public boolean run(ThreeTickHunterConfig config) {
        Microbot.enableAutoRunOn = true;
        currentState = State.SETTING_UP;
        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.HUNTING_CARNIVOROUS_CHINCHOMPAS);

        // Dein Startpunkt wird zum "operatingTile"
        this.operatingTile = Rs2Player.getWorldLocation();
        // Das Zentrum des "X"-Musters wird als ein Feld südlich von dir definiert
        this.patternCenterTile = this.operatingTile.dy(-1);
        Microbot.log("Operating tile set to: " + this.operatingTile);
        Microbot.log("Pattern center tile calculated as: " + this.patternCenterTile);


        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                if (Rs2Antiban.getTIMEOUT() > 0) {
                    Rs2Antiban.setTIMEOUT(Rs2Antiban.getTIMEOUT() - 1);
                    return;
                }

                if (!hasInitialized) {
                    startHunterXp = Microbot.getClient().getSkillExperience(Skill.HUNTER);
                    if (startHunterXp >= 0) {
                        hasInitialized = true;
                        initializeTrapLocations();
                    }
                    return;
                }

                determineState();
                Rs2Antiban.takeMicroBreakByChance();

                switch (currentState) {
                    case SETTING_UP_TRAPS:
                        handleTrapPlacement();
                        break;
                    case THREE_TICK_HUNTING:
                        handleThreeTickCycle(config);
                        break;
                    default:
                        break;
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        currentState = State.IDLE;
        Rs2Antiban.resetAntibanSettings();
    }

    private void determineState() {
        if (countAllTraps() < getMaxTraps()) {
            currentState = State.SETTING_UP_TRAPS;
        } else {
            currentState = State.THREE_TICK_HUNTING;
        }
    }

    private void initializeTrapLocations() {
        if (patternCenterTile == null) return;
        trapLocations.clear();
        // NEU: Koordinaten für das X-Muster
        trapLocations.addAll(Arrays.asList(
                patternCenterTile.dx(-1).dy(1),  // oben links
                patternCenterTile.dx(1).dy(1),   // oben rechts
                patternCenterTile,                       // ZENTRUM
                patternCenterTile.dx(-1).dy(-1), // unten links
                patternCenterTile.dx(1).dy(-1)  // unten rechts
        ));
        Microbot.log("X-pattern trap locations initialized around: " + patternCenterTile);
    }

    private boolean handleTrapPlacement() {
        if (Rs2Player.isAnimating() || operatingTile == null) return false;
        if (countAllTraps() >= getMaxTraps() || !Rs2Inventory.hasItem(BOX_TRAP_ITEM_ID)) return false;

        // Sicherheits-Check: Stelle sicher, dass der Spieler sich nicht vom Startpunkt wegbewegt hat
        if (!Rs2Player.getWorldLocation().equals(operatingTile)) {
            Microbot.log("Player has moved from the operating tile. Stopping script for safety.");
            this.shutdown();
            return false;
        }

        Optional<WorldPoint> nextSpot = trapLocations.stream()
                .filter(point -> !isTrapOnTile(point))
                .findFirst();

        nextSpot.ifPresent(targetSpot -> {
            if (Rs2Inventory.use(BOX_TRAP_ITEM_ID)) {
                sleepUntil(Rs2Inventory::isItemSelected, 1000);

                LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient(), targetSpot);
                if (localPoint != null) {
                    Point canvasPoint = Perspective.localToCanvas(Microbot.getClient(), localPoint, Microbot.getClient().getPlane());
                    if (canvasPoint != null) {
                        Microbot.getMouse().click(canvasPoint);
                        Rs2Antiban.actionCooldown();
                        sleepUntil(() -> isTrapOnTile(targetSpot), 3000);
                    }
                }
            }
        });
        return nextSpot.isPresent();
    }

    private void handleThreeTickCycle(ThreeTickHunterConfig config) {
        if (tickCounter % 3 == 0) {
            if (!handleFinishedTraps()) {
                handleTrapPlacement();
            }
        } else if (tickCounter % 3 == 1) {
            performTickManipulation(config);
        }
        tickCounter = (tickCounter + 1) % 3;
    }

    private void performTickManipulation(ThreeTickHunterConfig config) {
        if (config.tickMethod() == ThreeTickHunterConfig.TickManipulationMethod.TEAK_LOGS) {
            if (Rs2Inventory.hasItem(946) && Rs2Inventory.hasItem(6333)) {
                Rs2Inventory.interact(6333, "Use");
                sleep(50, 100);
                Rs2Inventory.interact(946, "Use");
            }
        }
    }

    private boolean handleFinishedTraps() {
        if (Rs2Player.isAnimating()) return false;

        List<TileObject> finishedTraps = Rs2GameObject.getAll(trap -> {
            int id = trap.getId();
            return (id == TRAP_SHAKING || id == TRAP_FAILED) && HUNTING_AREA.contains(trap.getWorldLocation());
        });

        if (!finishedTraps.isEmpty()) {
            finishedTraps.sort(Comparator.comparingInt(t -> t.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())));
            TileObject trapToHandle = finishedTraps.get(0);

            if (trapToHandle.getId() == TRAP_SHAKING && Rs2Inventory.isFull()) {
                return false;
            }
            String action = trapToHandle.getId() == TRAP_SHAKING ? "Check" : "Dismantle";

            if (Rs2GameObject.interact(trapToHandle, action)) {
                if (action.equals("Check")) trapsCaught++;
                Rs2Antiban.actionCooldown();
                sleep(600, 1000);
                return true;
            }
        }
        return false;
    }

    private boolean isTrapOnTile(WorldPoint point) {
        Predicate<TileObject> isTrapOnTilePredicate = obj -> {
            int id = obj.getId();
            return id == TRAP_BOX_SET_1 || id == TRAP_BOX_SET_2 || id == TRAP_SHAKING || id == TRAP_FAILED;
        };
        return !Rs2GameObject.getAll(isTrapOnTilePredicate, point).isEmpty();
    }

    private long countAllTraps() {
        return trapLocations.stream().filter(this::isTrapOnTile).count();
    }

    private int getMaxTraps() {
        int level = Microbot.getClient().getRealSkillLevel(Skill.HUNTER);
        if (level >= 80) return 5;
        if (level >= 60) return 4;
        if (level >= 40) return 3;
        if (level >= 20) return 2;
        return 1;
    }

    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.actionCooldownChance = 0.35;
        Rs2AntibanSettings.usePlayStyle = true;
    }
}