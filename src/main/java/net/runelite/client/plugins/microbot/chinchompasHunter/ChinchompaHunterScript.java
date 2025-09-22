package net.runelite.client.plugins.microbot.chinchompasHunter;

import lombok.Getter;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Getter
public class ChinchompaHunterScript extends Script {

    @Getter
    public enum State {
        IDLE, SETTING_UP, CHECKING_TRAPS, RESETTING_STALE_TRAPS,
        RELEASING_CHINS, WAITING
    }

    @Getter private State currentState = State.IDLE;
    @Getter private long startHunterXp = 0;
    @Getter private int trapsCaught = 0;

    private final List<WorldPoint> trapSpots = new ArrayList<>();
    private boolean setupComplete = false;
    private final Map<WorldPoint, Instant> trapTimestamps = new HashMap<>();

    // --- IDs ---
    private static final int BOX_TRAP_ITEM_ID = 10008;
    private static final int RED_CHINCHOMPA_ID = 10034;
    private static final Set<Integer> TRAP_SET_IDS = Set.of(9380, 9385);
    private static final Set<Integer> TRAP_SHAKING_IDS = Set.of(9383);
    private static final Set<Integer> ALL_TRAP_OBJECT_IDS = new HashSet<>();
    static {
        ALL_TRAP_OBJECT_IDS.addAll(TRAP_SET_IDS);
        ALL_TRAP_OBJECT_IDS.addAll(TRAP_SHAKING_IDS);
    }

    // --- Hauptlogik ---

    public boolean run(ChinchompaHunterConfig config) {
        if (startHunterXp == 0) this.startHunterXp = Microbot.getClient().getSkillExperience(Skill.HUNTER);
        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.HUNTING_CARNIVOROUS_CHINCHOMPAS);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                if (Rs2Player.isAnimating() || Rs2Player.isMoving()) return;

                if (!setupComplete) {
                    currentState = State.SETTING_UP;
                    if (trapSpots.size() >= getMaxTraps()) {
                        Microbot.log("Setup abgeschlossen. Alle " + getMaxTraps() + " Fallenstandorte sind gespeichert.");
                        setupComplete = true;
                    } else {
                        setupTrapAtCurrentLocation();
                        return;
                    }
                }

                if (handleInventory()) return;
                if (handleShakingTraps()) return;
                if (handleStaleTraps()) return;
                replaceMissingTraps();

                currentState = State.WAITING;
            } catch (Exception ex) {
                Microbot.log("!!! Unerwarteter Fehler in der Hauptschleife !!!");
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    // --- Setup-Methode (vereinfacht) ---

    private void setupTrapAtCurrentLocation() {
        if (!Rs2Inventory.hasItem(BOX_TRAP_ITEM_ID)) return;

        WorldPoint currentLocation = Rs2Player.getWorldLocation();

        if (isTileValidForPlacement(currentLocation)) {
            Microbot.log("Aktuelle Kachel ist frei. Platziere Falle und speichere Spot.");
            trapSpots.add(currentLocation);
            placeTrapAt(currentLocation);
        } else {
            Microbot.log("Aktuelle Kachel ist blockiert. Suche nahegelegene freie Kachel zum Hingehen.");
            Optional<WorldPoint> nearbyClearTile = findNearestValidTile(currentLocation);
            if (nearbyClearTile.isPresent()) {
                Rs2Walker.walkTo(nearbyClearTile.get());
                sleep(600, 1000);
            } else {
                Microbot.log("KONNTE KEINE FREIE KACHEL IN DER NÄHE FINDEN. Skript steckt fest!");
                sleep(5000);
            }
        }
    }

    // --- Routine-Methoden ---

    private void replaceMissingTraps() {
        if (!Rs2Inventory.hasItem(BOX_TRAP_ITEM_ID)) return;
        for (WorldPoint spot : trapSpots) {
            if (!isTrapOnTile(spot) && !trapTimestamps.containsKey(spot)) {
                Microbot.log("Fehlende Falle am Standort " + spot + " erkannt. Setze neue Falle.");
                placeTrapAt(spot);
                return;
            }
        }
    }

    private void placeTrapAt(WorldPoint target) {
        if (Rs2Player.getWorldLocation().equals(target)) {
            // Deine Idee: Merke dir die Position, bevor die Aktion stattfindet.
            final WorldPoint positionBeforePlacing = Rs2Player.getWorldLocation();

            if (Rs2Inventory.interact(BOX_TRAP_ITEM_ID, "Lay")) {
                trapTimestamps.put(target, Instant.now());

                // Schritt 1: Warte, bis die Falle physisch im Spiel erscheint.
                sleepUntil(() -> isTrapOnTile(target), 3000);

                // Schritt 2: Implementierung deiner Logik.
                // Warte aktiv (bis zu 2 Sekunden), bis das Spiel den Charakter
                // auf eine neue Kachel geschoben hat.
                Microbot.log("Warte auf Positionsänderung nach dem Legen der Falle...");
                boolean positionChanged = sleepUntil(() -> !Rs2Player.getWorldLocation().equals(positionBeforePlacing), 2000);

                if (positionChanged) {
                    Microbot.log("Positionsänderung erkannt. Fahre fort.");
                } else {
                    Microbot.log("WARNUNG: Position hat sich nach 2 Sekunden nicht geändert.");
                }
            }
        } else {
            Rs2Walker.walkTo(target);
        }
    }

    private boolean handleInventory() {
        if (Rs2Inventory.getEmptySlots() < 2 && Rs2Inventory.hasItem(RED_CHINCHOMPA_ID)) {
            currentState = State.RELEASING_CHINS;
            Rs2Inventory.interact(RED_CHINCHOMPA_ID, "Release");
            return true;
        }
        return false;
    }

    private boolean handleShakingTraps() {
        if (Rs2Inventory.isFull()) return false;

        Optional<TileObject> trapToHandle = Rs2GameObject.getAll(obj -> TRAP_SHAKING_IDS.contains(obj.getId())).stream()
                .filter(trap -> trapSpots.contains(trap.getWorldLocation()))
                .min(Comparator.comparingInt(t -> t.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())));

        if (trapToHandle.isPresent()) {
            WorldPoint trapTile = trapToHandle.get().getWorldLocation();
            currentState = State.CHECKING_TRAPS;
            if (Rs2GameObject.interact(trapToHandle.get(), "Check")) {
                trapsCaught++;
                trapTimestamps.remove(trapTile);
                sleepUntil(() -> !isTrapOnTile(trapTile), 3000);
                return true;
            }
        }
        return false;
    }

    private boolean handleStaleTraps() {
        Optional<WorldPoint> trapToReset = trapTimestamps.entrySet().stream()
                .filter(entry -> Duration.between(entry.getValue(), Instant.now()).getSeconds() > 90)
                .map(Map.Entry::getKey)
                .min(Comparator.comparing(loc -> loc.distanceTo(Rs2Player.getWorldLocation())));

        if (trapToReset.isPresent()) {
            WorldPoint trapTile = trapToReset.get();
            GameObject trapObject = Rs2GameObject.getGameObject(trapTile);
            if (trapObject != null && TRAP_SET_IDS.contains(trapObject.getId())) {
                currentState = State.RESETTING_STALE_TRAPS;
                if (Rs2GameObject.interact(trapTile, "Reset")) {
                    trapTimestamps.remove(trapTile);
                    sleepUntil(() -> !isTrapOnTile(trapTile), 3000);
                    return true;
                }
            }
        }
        return false;
    }

    // --- Helfermethoden ---

    private boolean isTileValidForPlacement(WorldPoint point) {
        // Wir prüfen jede Bedingung einzeln, um detailliertes Feedback geben zu können.
        boolean isWalkable = Rs2Tile.isWalkable(point);
        boolean isTrapPresent = isTrapOnTile(point);
        boolean isAlreadyASpot = trapSpots.contains(point);

        // Die Kachel ist nur gültig, wenn alle drei Bedingungen zutreffen.
        boolean isValid = isWalkable && !isTrapPresent && !isAlreadyASpot;

        // Wenn die Kachel als ungültig eingestuft wird, geben wir den genauen Grund aus.
        if (!isValid) {
            // Diese Zeile wird uns den Fehler verraten!
            Microbot.log("Prüfe Kachel " + point + ": UNGÜLTIG! -> Begehbar: " + isWalkable + " | Falle da: " + isTrapPresent + " | Schon Spot: " + isAlreadyASpot);
        }

        return isValid;
    }

    private Optional<WorldPoint> findNearestValidTile(WorldPoint center) {
        for (int radius = 1; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius) continue;
                    WorldPoint tile = center.dx(dx).dy(dy);
                    if (isTileValidForPlacement(tile)) {
                        return Optional.of(tile);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean isTrapOnTile(WorldPoint point) {
        return Rs2GameObject.getGameObjects(point).stream().anyMatch(obj -> ALL_TRAP_OBJECT_IDS.contains(obj.getId()));
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
        Rs2AntibanSettings.takeMicroBreaks = true;
        Rs2AntibanSettings.usePlayStyle = true;
    }
}