package net.runelite.client.plugins.microbot.ThreeTickHunter;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
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
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ThreeTickHunterScript extends Script {

    @Getter
    public enum State {
        IDLE, WALKING_TO_AREA, INITIALIZING_TRAPS, INITIAL_TRAP_SETUP, THREE_TICK_HUNTING, STOPPED
    }

    @Getter private State currentState = State.IDLE;
    @Getter private long startHunterXp = 0;
    @Getter private int trapsCaught = 0;
    private int tickCounter = 0;
    private boolean hasInitialized = false;

    @Getter private final WorldArea HUNTING_AREA = new WorldArea(2510, 9285, 25, 25, 0);
    private static final WorldPoint HUNTING_AREA_CENTER = new WorldPoint(2522, 9297, 0);

    @Getter private final List<WorldPoint> trapLocations = new ArrayList<>();

    private static final int TRAP_BOX_SET_1 = 9380;
    private static final int TRAP_BOX_SET_2 = 9385;
    private static final int TRAP_SHAKING = 9383;
    private static final int TRAP_FAILED = 9384;

    public boolean isRunning() {
        // KORREKTUR: super.run() ist eine Methode, keine Variable
        return super.run();
    }

    public boolean run(ThreeTickHunterConfig config) {
        Microbot.enableAutoRunOn = true;
        currentState = State.IDLE;
        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.HUNTING_CARNIVOROUS_CHINCHOMPAS);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                // KORREKTUR: super.run() ist eine Methode, keine Variable
                if (!Microbot.isLoggedIn() || !super.run()) return;

                if (Rs2Antiban.getTIMEOUT() > 0) {
                    Rs2Antiban.setTIMEOUT(Rs2Antiban.getTIMEOUT() - 1);
                    return;
                }

                if (!hasInitialized) {
                    startHunterXp = Microbot.getClient().getSkillExperience(Skill.HUNTER);
                    if (startHunterXp > 0) hasInitialized = true;
                    return;
                }

                Rs2Antiban.takeMicroBreakByChance();
                determineState(config);

                switch (currentState) {
                    case WALKING_TO_AREA: Rs2Walker.walkTo(HUNTING_AREA_CENTER); break;
                    case INITIALIZING_TRAPS: initializeTrapLocations(); break;
                    case INITIAL_TRAP_SETUP: handleInitialSetup(); break;
                    case THREE_TICK_HUNTING: handleThreeTickCycle(config); break;
                    case IDLE:
                    case STOPPED: break;
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

    // ... (Die Methoden determineState, initializeTrapLocations, handleInitialSetup, handleThreeTickCycle, performTickManipulation, handleFinishedTraps, layNewTrapCycle bleiben gleich)
    private void determineState(ThreeTickHunterConfig config) {
        if (!HUNTING_AREA.contains(Rs2Player.getWorldLocation())) {
            currentState = State.WALKING_TO_AREA;
        } else if (trapLocations.isEmpty()) {
            currentState = State.INITIALIZING_TRAPS;
        } else if (countAllTraps() < trapLocations.size()) {
            currentState = State.INITIAL_TRAP_SETUP;
        } else {
            currentState = State.THREE_TICK_HUNTING;
        }
    }

    private void initializeTrapLocations() {
        WorldPoint startingPoint = Rs2Player.getWorldLocation();
        trapLocations.clear();

        List<WorldPoint> potentialLocations = new ArrayList<>();
        potentialLocations.add(startingPoint.dx(-1).dy(1));
        potentialLocations.add(startingPoint.dx(1).dy(1));
        potentialLocations.add(startingPoint);
        potentialLocations.add(startingPoint.dx(-1).dy(-1));
        potentialLocations.add(startingPoint.dx(1).dy(-1));

        trapLocations.addAll(potentialLocations.stream()
                .filter(point -> !isTrapOnTile(point))
                .limit(getMaxTraps())
                .collect(Collectors.toList()));

        if (trapLocations.isEmpty()) {
            Microbot.log("CRITICAL: No valid trap locations found. Please move and restart.");
            currentState = State.STOPPED;
        } else {
            Microbot.log("Trap locations initialized: " + trapLocations.size());
        }
    }

    private void handleInitialSetup() {
        if (Rs2Player.isAnimating()) return;

        Optional<WorldPoint> nextSpot = trapLocations.stream()
                .filter(point -> !isTrapOnTile(point))
                .findFirst();

        nextSpot.ifPresent(targetSpot -> {
            if (Rs2Player.getWorldLocation().equals(targetSpot)) {
                if (Rs2Inventory.interact(10008, "Lay")) {
                    Rs2Antiban.actionCooldown();
                }
                sleep(1200, 1800);
            } else {
                Rs2Walker.walkTo(targetSpot);
            }
        });
    }

    private void handleThreeTickCycle(ThreeTickHunterConfig config) {
        if (tickCounter % 3 == 0) {
            if (!handleFinishedTraps()) {
                layNewTrapCycle();
            }
        } else if (tickCounter % 3 == 1) {
            performTickManipulation(config);
        }
        tickCounter++;
    }

    private void performTickManipulation(ThreeTickHunterConfig config) {
        if (config.tickMethod() == ThreeTickHunterConfig.TickManipulationMethod.TEAK_LOGS) {
            if (Rs2Inventory.hasItem(946) && Rs2Inventory.hasItem(6333)) {
                Rs2Inventory.interact(946, "Use");
                sleep(50, 100);
                Rs2Inventory.interact(6333, "Use");
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

            if (action.equals("Check") && Rs2Inventory.isFull()) return false;

            Rs2GameObject.interact(trapToHandle, action);
            if (action.equals("Check")) trapsCaught++;

            Rs2Antiban.actionCooldown();

            sleep(600, 1000);
            return true;
        }
        return false;
    }

    private boolean layNewTrapCycle() {
        if (Rs2Player.isAnimating() || countAllTraps() >= trapLocations.size()) return false;

        Optional<WorldPoint> nextSpot = trapLocations.stream()
                .filter(point -> !isTrapOnTile(point))
                .findFirst();

        if (nextSpot.isPresent()) {
            WorldPoint targetSpot = nextSpot.get();
            if (Rs2Player.getWorldLocation().equals(targetSpot)) {
                if (Rs2Inventory.interact(10008, "Lay")) {
                    Rs2Antiban.actionCooldown();
                    return true;
                }
                return false;
            } else {
                Rs2Walker.walkTo(targetSpot);
                return true;
            }
        }
        return false;
    }


    private List<TileObject> getObjectsOnTile(WorldPoint worldPoint) {
        List<TileObject> objects = new ArrayList<>();
        // KORREKTUR: Veraltete (deprecated) getScene() Methode ersetzt
        if (worldPoint == null || Microbot.getClient().getTopLevelWorldView().getScene() == null) return objects;

        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);
        if (localPoint == null) return objects;

        int plane = Microbot.getClient().getTopLevelWorldView().getPlane();
        // KORREKTUR: Veraltete (deprecated) getScene() Methode ersetzt
        Tile tile = Microbot.getClient().getTopLevelWorldView().getScene().getTiles()[plane][localPoint.getSceneX()][localPoint.getSceneY()];
        if (tile == null) return objects;

        if (tile.getGameObjects() != null) objects.addAll(Arrays.asList(tile.getGameObjects()));
        if (tile.getGroundObject() != null) objects.add(tile.getGroundObject());
        if (tile.getWallObject() != null) objects.add(tile.getWallObject());
        if (tile.getDecorativeObject() != null) objects.add(tile.getDecorativeObject());

        objects.removeAll(Collections.singleton(null));
        return objects;
    }

    private boolean isTrapOnTile(WorldPoint point) {
        for (TileObject object : getObjectsOnTile(point)) {
            int id = object.getId();
            if (id == TRAP_BOX_SET_1 || id == TRAP_BOX_SET_2 || id == TRAP_SHAKING || id == TRAP_FAILED) {
                return true;
            }
        }
        return false;
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