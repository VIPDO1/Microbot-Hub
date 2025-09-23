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
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
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
    private final Map<WorldPoint, Instant> trapTimestamps = new HashMap<>();

    // --- IDs ---
    private static final int BOX_TRAP_ITEM_ID = 10008;
    private static final int RED_CHINCHOMPA_ID = 10034;
    private static final Set<Integer> TRAP_SET_IDS = Set.of(9380);
    private static final Set<Integer> TRAP_FAILED_IDS = Set.of(9385);
    private static final Set<Integer> TRAP_SHAKING_IDS = Set.of(9383);
    private static final Set<Integer> ALL_TRAP_OBJECT_IDS = new HashSet<>();
    static {
        ALL_TRAP_OBJECT_IDS.addAll(TRAP_SET_IDS);
        ALL_TRAP_OBJECT_IDS.addAll(TRAP_FAILED_IDS);
        ALL_TRAP_OBJECT_IDS.addAll(TRAP_SHAKING_IDS);
    }

    // --- Timing / anti-race constants ---
    private static final Duration RECENT_PLACEMENT_WINDOW = Duration.ofSeconds(5);
    private static final Duration STALE_TRAP_THRESHOLD = Duration.ofSeconds(90);

    // --- Hauptlogik ---

    public boolean run(ChinchompaHunterConfig config) {
        if (startHunterXp == 0) this.startHunterXp = Microbot.getClient().getSkillExperience(Skill.HUNTER);
        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.HUNTING_CARNIVOROUS_CHINCHOMPAS);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                if (Rs2Player.isAnimating() || Rs2Player.isMoving()) return;

                // If we don't have enough placed traps, try to place/replace
                if (countPlacedTraps() < getMaxTraps()) {
                    currentState = State.SETTING_UP;
                    Microbot.log("Priorität: Fallen aufstellen (" + countPlacedTraps() + "/" + getMaxTraps() + ").");
                    if (trapSpots.size() < getMaxTraps()) {
                        setupTrapAtCurrentLocation();
                    } else {
                        replaceMissingTraps();
                    }
                    return;
                }

                currentState = State.WAITING;
                Microbot.log("Alle Fallen stehen. Beginne Wartungs-Zyklus.");

                if (handleShakingTraps()) return;
                if (handleFailedTraps()) return;
                if (handleStaleTraps()) return;
                if (handleInventory()) return;

                Microbot.log("Keine unmittelbare Aktion nötig. Warte...");

            } catch (Exception ex) {
                Microbot.log("!!! Unerwarteter Fehler in der Hauptschleife !!!");
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    // --- Setup-Methoden ---

    private void setupTrapAtCurrentLocation() {
        if (!Rs2Inventory.hasItem(BOX_TRAP_ITEM_ID)) return;
        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        Microbot.log("Setup: Prüfe aktuelle Kachel " + currentLocation);
        if (isTileValidForPlacement(currentLocation)) {
            Microbot.log("Setup: Aktuelle Kachel ist gültig. Platziere Falle.");
            trapSpots.add(currentLocation);
            placeTrapAt(currentLocation);
        } else {
            Optional<WorldPoint> nearbyClearTile = findNearestValidTile(currentLocation);
            if (nearbyClearTile.isPresent()) {
                Microbot.log("Setup: Aktuelle Kachel ungültig. Gehe zu nahegelegener gültiger Kachel: " + nearbyClearTile.get());
                Rs2Walker.walkTo(nearbyClearTile.get());
                sleep(600, 1000);
            } else {
                Microbot.log("FATAL: Konnte keine gültige Kachel zum Hingehen finden!");
                sleep(5000);
            }
        }
    }

    // --- Routine-Methoden ---

    private void replaceMissingTraps() {
        if (!Rs2Inventory.hasItem(BOX_TRAP_ITEM_ID)) return;
        for (WorldPoint spot : trapSpots) {
            if (!isTrapOnTile(spot)) {
                // Wenn die Falle erst kürzlich neu gesetzt oder resetted wurde, warte ein bisschen (Race-Condition-Filter)
                Instant last = trapTimestamps.get(spot);
                if (last != null && Duration.between(last, Instant.now()).compareTo(RECENT_PLACEMENT_WINDOW) < 0) {
                    Microbot.log("Routine: Spot " + spot + " wurde kürzlich aktualisiert (" + last + "). Überspringe kurz, um Race zu vermeiden.");
                    continue;
                }

                Microbot.log("Routine: Fehlende Falle am Spot " + spot + " erkannt. Ersetze sie.");

                // Falls auf dem Boden eine Box trap (Item) liegt -> aufnehmen statt unnötig 'Lay' zu machen
                if (Rs2GroundItem.getGroundItems().get(spot, BOX_TRAP_ITEM_ID) != null) {
                    Microbot.log("Routine: Box trap Item auf Boden bei " + spot + " gefunden. Aufnehmen statt neu zu legen.");
                    if (Rs2GroundItem.lootItemsBasedOnLocation(spot, BOX_TRAP_ITEM_ID)) {
                        Microbot.log("Routine: Ground item aufgenommen.");
                        sleepUntil(() -> Rs2Inventory.hasItem(BOX_TRAP_ITEM_ID), 3000);
                        // kurz warten damit das Spiel das Item verarbeitet
                        sleep(400, 700);
                        placeTrapAt(spot);
                    }
                    return;
                }

                // Normales Legen
                placeTrapAt(spot);
                return;
            }
        }
    }

    private void placeTrapAt(WorldPoint target) {
        if (Rs2Player.getWorldLocation().equals(target)) {
            final WorldPoint positionBeforePlacing = Rs2Player.getWorldLocation();
            Microbot.log("Aktion: Versuche 'Lay' an " + target + " (Position vor Aktion: " + positionBeforePlacing + ")");
            if (Rs2Inventory.interact(BOX_TRAP_ITEM_ID, "Lay")) {
                Microbot.log("Aktion: Lege Falle bei " + target);
                // Warten bis eine Trap-Objekt existiert ODER eine Inventar-Box (failed -> ground) sichtbar ist
                sleepUntil(() -> isTrapOnTile(target) || Rs2GroundItem.getGroundItems().get(target, BOX_TRAP_ITEM_ID) != null, 6000);
                trapTimestamps.put(target, Instant.now());
                Microbot.log("Aktion: Falle platziert (oder GroundItem vorhanden). Timestamp gesetzt: " + trapTimestamps.get(target));
                // leichte Pause um menschliches Verhalten zu simulieren und Race-Conditions zu reduzieren
                sleep(800, 1300);
            } else {
                Microbot.log("Aktion: Lay-Interaktion fehlgeschlagen für " + target);
            }
        } else {
            Microbot.log("Aktion: Gehe zu " + target + ", um Falle zu legen.");
            Rs2Walker.walkTo(target);
        }
    }

    private boolean handleShakingTraps() {
        Optional<TileObject> trapToHandle = findNearestTrap(TRAP_SHAKING_IDS);
        if (trapToHandle.isPresent()) {
            WorldPoint trapTile = trapToHandle.get().getWorldLocation();
            currentState = State.CHECKING_TRAPS;
            Microbot.log("Wartung: Erfolgreiche Falle bei " + trapTile + ". Führe 'Reset' aus.");
            if (Rs2GameObject.interact(trapToHandle.get(), "Reset")) {
                trapsCaught++;
                trapTimestamps.put(trapTile, Instant.now());
                if (!trapSpots.contains(trapTile)) trapSpots.add(trapTile);
                // Warten bis ein gesetzter Trap auftaucht
                sleepUntil(() -> isTrapOnTile(trapTile), 5000);
                Microbot.log("Wartung: 'Reset' abgeschlossen für " + trapTile + ", trapsCaught=" + trapsCaught);
                // kurze Pause
                sleep(600, 1000);
                return true;
            } else {
                Microbot.log("Wartung: 'Reset' für Shaking trap bei " + trapTile + " fehlgeschlagen.");
            }
        }
        return false;
    }

    private boolean handleFailedTraps() {
        Optional<TileObject> failedTrap = findNearestTrap(TRAP_FAILED_IDS);
        if (failedTrap.isPresent()) {
            WorldPoint trapTile = failedTrap.get().getWorldLocation();
            currentState = State.RESETTING_STALE_TRAPS;
            Microbot.log("Wartung: Fehlgeschlagene Falle bei " + trapTile + ". Führe 'Reset' aus.");

            // Versuche Reset - wenn Reset nicht möglich oder Objekt verschwindet, handle Ground Item
            if (Rs2GameObject.interact(failedTrap.get(), "Reset")) {
                trapTimestamps.put(trapTile, Instant.now());
                if (!trapSpots.contains(trapTile)) trapSpots.add(trapTile);
                sleepUntil(() -> isTrapOnTile(trapTile), 5000);
                Microbot.log("Wartung: 'Reset' erfolgreich für fehlgeschlagene Falle bei " + trapTile);
                sleep(600, 1000);
                return true;
            } else {
                Microbot.log("Wartung: Reset-Interaktion fehlgeschlagen bei " + trapTile + ", prüfe Boden-Item...");
                // Falls das Objekt verschwunden ist und die Fallenkiste am Boden liegt, nimm sie auf
                if (Rs2GroundItem.getGroundItems().get(trapTile, BOX_TRAP_ITEM_ID) != null) {
                    Microbot.log("Wartung: Box trap Item auf Boden bei " + trapTile + " gefunden. Aufnehmen.");
                    if (Rs2GroundItem.lootItemsBasedOnLocation(trapTile, BOX_TRAP_ITEM_ID)) {
                        Microbot.log("Wartung: Ground item aufgenommen. Lege Falle neu.");
                        sleepUntil(() -> Rs2Inventory.hasItem(BOX_TRAP_ITEM_ID), 3000);
                        placeTrapAt(trapTile);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean handleStaleTraps() {
        Optional<WorldPoint> trapToReset = trapTimestamps.entrySet().stream()
                .filter(entry -> Duration.between(entry.getValue(), Instant.now()).getSeconds() > STALE_TRAP_THRESHOLD.getSeconds())
                .map(Map.Entry::getKey)
                .min(Comparator.comparing(loc -> loc.distanceTo(Rs2Player.getWorldLocation())));
        if (trapToReset.isPresent()) {
            WorldPoint trapTile = trapToReset.get();
            if (Optional.ofNullable(Rs2GameObject.getGameObject(trapTile)).map(GameObject::getId).filter(TRAP_SET_IDS::contains).isPresent()) {
                currentState = State.RESETTING_STALE_TRAPS;
                Microbot.log("Wartung: Alte Falle bei " + trapTile + " gefunden. Führe 'Reset' aus.");
                if (Rs2GameObject.interact(trapTile, "Reset")) {
                    trapTimestamps.put(trapTile, Instant.now());
                    sleepUntil(() -> !isTrapOnTile(trapTile), 3000);
                    Microbot.log("Wartung: Stale Trap resettet bei " + trapTile);
                    sleep(600, 1000);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleInventory() {
        if (Rs2Inventory.getEmptySlots() < 2 && Rs2Inventory.hasItem(RED_CHINCHOMPA_ID)) {
            currentState = State.RELEASING_CHINS;
            Microbot.log("Wartung: Inventar fast voll. Lasse Chinchompas frei.");
            Rs2Inventory.interact(RED_CHINCHOMPA_ID, "Release");
            // kleine Pause nach Release
            sleep(500, 900);
            return true;
        }
        return false;
    }

    // --- Helfermethoden mit vollem Logging ---

    private long countPlacedTraps() {
        if (trapSpots.isEmpty()) return 0;
        return trapSpots.stream().filter(this::isTrapOnTile).count();
    }

    private Optional<TileObject> findNearestTrap(Set<Integer> ids) {
        return Rs2GameObject.getAll().stream()
                .filter(obj -> ids.contains(obj.getId()))
                .filter(trap -> trapSpots.contains(trap.getWorldLocation()))
                .min(Comparator.comparingInt(t -> t.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())));
    }

    // VOLLSTÄNDIGES LOGGING HIER WIEDERHERGESTELLT
    private boolean isTileValidForPlacement(WorldPoint point) {
        boolean isWalkable = Rs2Tile.isWalkable(point);
        boolean isTrapPresent = isTrapOnTile(point);
        boolean isAlreadyASpot = trapSpots.contains(point);
        boolean isValid = isWalkable && !isTrapPresent && !isAlreadyASpot;
        // Gib nur dann eine Log-Meldung aus, wenn die Kachel ungültig ist, um Spam zu vermeiden
        if (!isValid) {
            Microbot.log("Detail-Prüfung Kachel " + point + ": UNGÜLTIG! -> Begehbar: " + isWalkable + " | Falle da: " + isTrapPresent + " | Schon Spot: " + isAlreadyASpot);
        }
        return isValid;
    }

    private Optional<WorldPoint> findNearestValidTile(WorldPoint center) {
        Microbot.log("Suche nach nächster gültiger Kachel um " + center + "...");
        for (int radius = 1; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius) continue;
                    WorldPoint tile = center.dx(dx).dy(dy);
                    if (isTileValidForPlacement(tile)) {
                        Microbot.log("Gültige Kachel bei " + tile + " gefunden.");
                        return Optional.of(tile);
                    }
                }
            }
        }
        Microbot.log("Suche in Radius 5 beendet. Keine gültige Kachel gefunden.");
        return Optional.empty();
    }

    private boolean isTrapOnTile(WorldPoint point) {
        if (point == null) return false;
        return Rs2GameObject.getGameObjects(point).stream()
                .filter(obj -> ALL_TRAP_OBJECT_IDS.contains(obj.getId()))
                .anyMatch(trap -> trap.getWorldLocation().equals(point));
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
        Microbot.log("Aktiviere angepasste Anti-Ban-Einstellungen für ChinchompaHunter-Skript...");

        // --- Grundlegende Aktivierung ---
        Rs2AntibanSettings.antibanEnabled = true;     // Hauptschalter, um das System zu aktivieren.
        Rs2AntibanSettings.universalAntiban = true;   // Sorgt dafür, dass die Einstellungen auf alle Aktionen angewendet werden.

        // --- Pausen & Timing-Verhalten ---
        // Microbreaks deaktivieren für dieses Skript: die bisherigen langen Pausen (15 Minuten) sind für Hunter nicht sinnvoll
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.randomIntervals = true;    // Sorgt dafür, dass Aktionen nicht immer im exakt gleichen Takt ausgeführt werden.
        Rs2AntibanSettings.simulateFatigue = false;    // Nicht übermäßig lange Pausen simulieren

        // --- Mausverhalten ---
        Rs2AntibanSettings.naturalMouse = true;         // Nutzt Algorithmen für menschlichere, unperfekte Mausbewegungen anstelle von geraden Linien.
        Rs2AntibanSettings.moveMouseRandomly = true;    // Bewegt die Maus gelegentlich ziellos über den Bildschirm, wie es ein gelangweilter Spieler tun würde.
        Rs2AntibanSettings.moveMouseOffScreen = false;   // Nicht ständig Maus aus dem Fenster bewegen (sieht verdächtig aus)

        // --- Verhaltenssimulation ---
        Rs2AntibanSettings.usePlayStyle = true;               // Nutzt ein vordefiniertes Profil (z.B. "effizient" oder "entspannt"), um das allgemeine Verhalten zu steuern.
        Rs2AntibanSettings.behavioralVariability = true;      // Sorgt dafür, dass sich das Skript über die Zeit leicht anders verhält und nicht starr einem Muster folgt.

        // Simuliere keine Fehlklicks standardmäßig (kann gefährlich sein wenn das Skript nicht robust ist)
        Rs2AntibanSettings.simulateMistakes = false;

        Microbot.log("Anti-Ban Einstellungen angewendet: microbreaks=" + Rs2AntibanSettings.takeMicroBreaks + ", fatigue=" + Rs2AntibanSettings.simulateFatigue + ", moveOffscreen=" + Rs2AntibanSettings.moveMouseOffScreen);
    }
}
