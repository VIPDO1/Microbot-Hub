package net.runelite.client.plugins.microbot.ChinchompasHunter;

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

@Getter
public class ChinchompaHunterScript extends Script {

    @Getter
    public enum State {
        IDLE,
        PLACING_TRAPS,
        CHECKING_TRAPS,
        WAITING,
        STOPPED
    }

    private State currentState = State.IDLE;
    private long startHunterXp = 0;
    private int trapsCaught = 0;
    private boolean hasInitialized = false;

    private WorldPoint operatingTile;
    private WorldPoint patternCenterTile;

    private final WorldArea HUNTING_AREA = new WorldArea(2510, 9285, 25, 25, 0);
    private final List<WorldPoint> trapLocations = new ArrayList<>();

    private static final int TRAP_BOX_SET_1 = 9380;
    private static final int TRAP_BOX_SET_2 = 9385;
    private static final int TRAP_SHAKING = 9383;
    private static final int TRAP_FAILED = 9384;
    private static final int BOX_TRAP_ITEM_ID = 10008;

    public boolean run(ChinchompaHunterConfig config) {
        Microbot.enableAutoRunOn = true;
        currentState = State.IDLE;
        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.HUNTING_CARNIVOROUS_CHINCHOMPAS);

        this.operatingTile = Rs2Player.getWorldLocation();
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

                Rs2Antiban.takeMicroBreakByChance();

                // Priorität 1: Überprüfe und handle fertige Fallen
                if (handleFinishedTraps()) {
                    currentState = State.CHECKING_TRAPS;
                }
                // Priorität 2: Wenn nichts zu tun war, versuche neue Fallen zu legen
                else if (handleTrapPlacement()) {
                    currentState = State.PLACING_TRAPS;
                }
                // Priorität 3: Wenn alle Fallen stehen und keine fertig ist, warte
                else {
                    currentState = State.WAITING;
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
        currentState = State.STOPPED;
        Rs2Antiban.resetAntibanSettings();
    }

    private void initializeTrapLocations() {
        if (patternCenterTile == null) return;
        trapLocations.clear();
        trapLocations.addAll(Arrays.asList(
                patternCenterTile.dx(-1).dy(1),
                patternCenterTile.dx(1).dy(1),
                patternCenterTile,
                patternCenterTile.dx(-1).dy(-1),
                patternCenterTile.dx(1).dy(-1)
        ));
        Microbot.log("X-pattern trap locations initialized around: " + patternCenterTile);
    }

    private boolean handleTrapPlacement() {
        if (Rs2Player.isAnimating() || operatingTile == null) return false;
        if (countAllTraps() >= getMaxTraps() || !Rs2Inventory.hasItem(BOX_TRAP_ITEM_ID)) return false;

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