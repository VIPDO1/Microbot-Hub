package net.runelite.client.plugins.microbot.ektotokens;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

public class EctoTokenScript extends Script {

    public enum EctoState {
        IDLE,
        TELEPORT_TO_ECTO,
        WALKING_TO_LOCATION,
        WORKING,
        TELEPORT_TO_VARROCK,
        WALKING_TO_BANK,
        BANKING
    }

    public EctoState state = EctoState.IDLE;

    public long slimeCollected = 0;
    public long bonemealGround = 0;
    public long worshipsDone = 0;

    private final int ECTOPHIAL_FULL = 4251;

    private final int LAW_RUNE = 563;
    private final int AIR_RUNE = 556;
    private final int FIRE_RUNE = 554;

    private final int BUCKET_ID = 1925;
    private final int BUCKET_OF_SLIME_ID = 4286;
    private final int SLIME_OBJECT_ID = 17119;

    private final int BONE_ID = 526;
    private final int POT_ID = 1931;

    private final int BONEMEAL_ID = 4255;
    private final int POT_OF_BONEMEAL_ID = 4278;

    private final int LOADER_ID = 16654;
    private final int STAIRS_UP_ID = 16646;
    private final int ECTOFUNTUS_ALTAR_ID = 16648;

    private final int TRAPDOOR_CLOSED_ID = 16113;
    private final int TRAPDOOR_OPEN_ID = 16114;

    private final WorldPoint SLIME_POOL_LOCATION = new WorldPoint(3683, 9888, 0);
    private final WorldPoint HOPPER_LOCATION = new WorldPoint(3659, 3524, 1);
    private final WorldPoint ALTAR_LOCATION = new WorldPoint(3660, 3520, 0);
    private final WorldPoint ECTO_TELE_POINT = new WorldPoint(3659, 3522, 0);
    private final WorldPoint GRAND_EXCHANGE_AREA = new WorldPoint(3164, 3487, 0);

    public boolean run(EctoTokenConfig config) {
        state = EctoState.IDLE;
        Microbot.enableAutoRunOn = true;

        applyAntibanSettings();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                determineState(config);

                switch (state) {
                    case TELEPORT_TO_ECTO:
                        if (Rs2Inventory.interact(ECTOPHIAL_FULL, "Empty")) {
                            Microbot.log("Teleporting to Ectofuntus...");
                            Rs2Player.waitForAnimation();
                            sleep(3000, 4500);
                        }
                        break;

                    case WALKING_TO_LOCATION:
                        if (config.craftEctotokens()) {
                            handleWalkingToAltar();
                        } else if (config.craftBonemeal()) {
                            handleWalkingToHopper();
                        } else if (config.craftBucketOfSlime()) {
                            handleWalkingToSlime();
                        }
                        break;

                    case WORKING:
                        if (config.craftEctotokens()) {
                            handleWorshipping();
                        } else if (config.craftBonemeal()) {
                            handleGrindingBones();
                        } else if (config.craftBucketOfSlime()) {
                            handleCollectingSlime();
                        }
                        break;

                    case TELEPORT_TO_VARROCK:
                        Microbot.log("Teleporting to Grand Exchange...");
                        if (Rs2Magic.cast(Rs2Spells.VARROCK_TELEPORT, "Grand Exchange", 2)) {
                            Rs2Player.waitForAnimation();
                            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(GRAND_EXCHANGE_AREA) < 15, 8000);
                        }
                        break;

                    case WALKING_TO_BANK:
                        if (!Rs2Bank.isOpen()) {
                            Microbot.log("Walking to Bank...");
                            Rs2Bank.openBank();
                            sleepUntil(Rs2Bank::isOpen, 10000);
                        }
                        break;

                    case BANKING:
                        if (config.craftEctotokens()) {
                            handleWorshipBanking();
                        } else if (config.craftBonemeal()) {
                            handleBonemealBanking();
                        } else if (config.craftBucketOfSlime()) {
                            handleSlimeBanking();
                        }
                        break;
                }

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void determineState(EctoTokenConfig config) {
        if (config.craftEctotokens()) {
            determineWorshipState();
        } else if (config.craftBonemeal()) {
            determineBonemealState();
        } else if (config.craftBucketOfSlime()) {
            determineSlimeState();
        } else {
            Microbot.log("Bitte wähle einen Modus in den Einstellungen!");
        }
    }

    private void determineWorshipState() {
        boolean hasSlime = Rs2Inventory.hasItem(BUCKET_OF_SLIME_ID);
        boolean hasBonemeal = Rs2Inventory.hasItem(BONEMEAL_ID) || Rs2Inventory.hasItem(POT_OF_BONEMEAL_ID);

        if (!hasSlime || !hasBonemeal) {
            if (Rs2Player.getWorldLocation().distanceTo(GRAND_EXCHANGE_AREA) < 150) {
                if (Rs2Bank.isOpen()) state = EctoState.BANKING;
                else state = EctoState.WALKING_TO_BANK;
            } else {
                state = EctoState.TELEPORT_TO_VARROCK;
            }
            return;
        }

        if (Rs2Player.getWorldLocation().getPlane() == 0 && Rs2Player.getWorldLocation().distanceTo(ALTAR_LOCATION) < 10) {
            state = EctoState.WORKING;
        } else {
            state = EctoState.WALKING_TO_LOCATION;
            if (Rs2Player.getWorldLocation().distanceTo(GRAND_EXCHANGE_AREA) < 200) {
                state = EctoState.TELEPORT_TO_ECTO;
            }
        }
    }

    private void handleWalkingToAltar() {
        if (Rs2Player.getWorldLocation().distanceTo(ECTO_TELE_POINT) > 60) {
            if (Rs2Inventory.interact(ECTOPHIAL_FULL, "Empty")) {
                Rs2Player.waitForAnimation();
                sleep(3000);
            }
            return;
        }

        if (Rs2Player.getWorldLocation().getPlane() != 0) {
            if (Rs2Inventory.interact(ECTOPHIAL_FULL, "Empty")) {
                sleep(3000);
            }
            return;
        }

        Rs2Walker.walkTo(ALTAR_LOCATION);
    }

    private void handleWorshipping() {
        boolean hasResources = Rs2Inventory.hasItem(BUCKET_OF_SLIME_ID)
                && (Rs2Inventory.hasItem(BONEMEAL_ID) || Rs2Inventory.hasItem(POT_OF_BONEMEAL_ID));

        if (!hasResources) return;

        if (Rs2GameObject.interact(ECTOFUNTUS_ALTAR_ID, "Worship")) {
            sleep(250, 500);
            worshipsDone++;
        }
    }

    private void handleWorshipBanking() {
        if (!Rs2Bank.isOpen()) return;

        Rs2Bank.depositAllExcept(ECTOPHIAL_FULL, LAW_RUNE, AIR_RUNE, FIRE_RUNE);
        sleep(600, 1000);

        if (!Rs2Inventory.hasItemAmount(BUCKET_OF_SLIME_ID, 12)) {
            if (Rs2Bank.hasItem(BUCKET_OF_SLIME_ID)) {
                Rs2Bank.withdrawX(BUCKET_OF_SLIME_ID, 12);
                sleep(600, 1000);
            } else {
                Microbot.log("Keine Bucket of Slime mehr!");
                shutdown();
                return;
            }
        }

        final int bonemealToWithdraw = (!Rs2Bank.hasItem(BONEMEAL_ID) && Rs2Bank.hasItem(POT_OF_BONEMEAL_ID))
                ? POT_OF_BONEMEAL_ID
                : BONEMEAL_ID;

        if (!Rs2Inventory.hasItemAmount(bonemealToWithdraw, 12)) {
            if (Rs2Bank.hasItem(bonemealToWithdraw)) {
                Rs2Bank.withdrawX(bonemealToWithdraw, 12);
                sleepUntil(() -> Rs2Inventory.hasItemAmount(bonemealToWithdraw, 12), 2000);
            } else {
                Microbot.log("Kein Bonemeal mehr!");
                shutdown();
                return;
            }
        }

        if (Rs2Inventory.hasItemAmount(BUCKET_OF_SLIME_ID, 12) && Rs2Inventory.hasItemAmount(bonemealToWithdraw, 12)) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 1500);
        }
    }

    private void determineBonemealState() {
        if (!Rs2Inventory.hasItem(BONE_ID) || !Rs2Inventory.hasItem(POT_ID)) {
            if (Rs2Player.getWorldLocation().distanceTo(GRAND_EXCHANGE_AREA) < 150) {
                if (Rs2Bank.isOpen()) state = EctoState.BANKING;
                else state = EctoState.WALKING_TO_BANK;
            } else {
                state = EctoState.TELEPORT_TO_VARROCK;
            }
            return;
        }
        if (Rs2Player.getWorldLocation().getPlane() == 1 && Rs2Player.getWorldLocation().distanceTo(HOPPER_LOCATION) < 6) {
            state = EctoState.WORKING;
        } else {
            state = EctoState.WALKING_TO_LOCATION;
            if (Rs2Player.getWorldLocation().distanceTo(GRAND_EXCHANGE_AREA) < 200) {
                state = EctoState.TELEPORT_TO_ECTO;
            }
        }
    }

    private void handleWalkingToHopper() {
        if (Rs2Player.getWorldLocation().distanceTo(ECTO_TELE_POINT) > 60 && Rs2Player.getWorldLocation().getPlane() == 0) {
            if (Rs2Player.getWorldLocation().getY() < 9000) {
                if (Rs2Inventory.interact(ECTOPHIAL_FULL, "Empty")) {
                    Rs2Player.waitForAnimation();
                    sleep(3000);
                }
                return;
            }
        }
        if (Rs2Player.getWorldLocation().getPlane() == 0) {
            WorldPoint stairsLocation = new WorldPoint(3660, 3524, 0);
            if (Rs2Player.getWorldLocation().distanceTo(stairsLocation) > 8) {
                Rs2Walker.walkTo(stairsLocation);
            } else {
                Rs2GameObject.interact(STAIRS_UP_ID, "Climb-up");
                sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 1, 5000);
            }
            return;
        }
        if (Rs2Player.getWorldLocation().getPlane() == 1) {
            Rs2Walker.walkTo(HOPPER_LOCATION);
        }
    }

    private void handleGrindingBones() {
        if (!Rs2Inventory.hasItem(BONE_ID) || !Rs2Inventory.hasItem(POT_ID)) return;
        if (Rs2Inventory.use(BONE_ID)) {
            if (Rs2GameObject.interact(LOADER_ID, "Use")) {
                Microbot.log("Starte Auto-Grind...");
                sleep(600, 1200);
                boolean grindingFinished = sleepUntil(
                        () -> !Rs2Inventory.hasItem(BONE_ID) && !Rs2Inventory.hasItem(POT_ID),
                        60000
                );
                if (grindingFinished) {
                    bonemealGround += 12;
                    Microbot.log("Verarbeitung abgeschlossen.");
                }
            }
        }
    }

    private void handleBonemealBanking() {
        if (!Rs2Bank.isOpen()) return;
        if (Rs2Inventory.hasItem(BONEMEAL_ID)) Rs2Bank.depositAll(BONEMEAL_ID);
        if (Rs2Inventory.hasItem(POT_OF_BONEMEAL_ID)) Rs2Bank.depositAll(POT_OF_BONEMEAL_ID);
        sleep(400, 600);

        if (!Rs2Inventory.hasItemAmount(BONE_ID, 12)) {
            if (Rs2Bank.hasItem(BONE_ID)) Rs2Bank.withdrawX(BONE_ID, 12);
            else { shutdown(); return; }
            sleep(600, 1000);
        }
        if (!Rs2Inventory.hasItemAmount(POT_ID, 12)) {
            if (Rs2Bank.hasItem(POT_ID)) Rs2Bank.withdrawX(POT_ID, 12);
            else { shutdown(); return; }
            sleepUntil(() -> Rs2Inventory.hasItemAmount(POT_ID, 12), 2000);
        }
        if (Rs2Inventory.hasItemAmount(BONE_ID, 12) && Rs2Inventory.hasItemAmount(POT_ID, 12)) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 1500);
        }
    }

    private void determineSlimeState() {
        if (!Rs2Inventory.hasItem(BUCKET_ID)) {
            if (Rs2Player.getWorldLocation().distanceTo(GRAND_EXCHANGE_AREA) < 150) {
                if (Rs2Bank.isOpen()) state = EctoState.BANKING;
                else state = EctoState.WALKING_TO_BANK;
            } else {
                state = EctoState.TELEPORT_TO_VARROCK;
            }
            return;
        }
        if (Rs2Player.getWorldLocation().getY() > 9000 && Rs2Player.getWorldLocation().distanceTo(SLIME_POOL_LOCATION) < 8) {
            state = EctoState.WORKING;
        } else {
            state = EctoState.WALKING_TO_LOCATION;
            if (Rs2Player.getWorldLocation().distanceTo(GRAND_EXCHANGE_AREA) < 200) {
                state = EctoState.TELEPORT_TO_ECTO;
            }
        }
    }

    private void handleWalkingToSlime() {
        if (Rs2Player.getWorldLocation().getY() > 9000) {
            Rs2Walker.walkTo(SLIME_POOL_LOCATION);
            return;
        }
        if (Rs2Player.getWorldLocation().distanceTo(ECTO_TELE_POINT) > 15) {
            Rs2Walker.walkTo(ECTO_TELE_POINT);
            return;
        }
        if (Rs2GameObject.exists(TRAPDOOR_OPEN_ID)) {
            Rs2GameObject.interact(TRAPDOOR_OPEN_ID, "Climb-down");
            sleepUntil(() -> Rs2Player.getWorldLocation().getY() > 9000, 5000);
        } else if (Rs2GameObject.exists(TRAPDOOR_CLOSED_ID)) {
            Rs2GameObject.interact(TRAPDOOR_CLOSED_ID, "Open");
            sleepUntil(() -> Rs2GameObject.exists(TRAPDOOR_OPEN_ID), 3000);
        }
    }

    private void handleCollectingSlime() {
        if (!Rs2Inventory.hasItem(BUCKET_ID)) return;
        if (Rs2Inventory.use(BUCKET_ID)) {
            if (Rs2GameObject.interact(SLIME_OBJECT_ID, "Use")) {
                sleep(600, 1200);
                boolean bucketsGone = sleepUntil(() -> !Rs2Inventory.hasItem(BUCKET_ID), 60000);
                if (bucketsGone) {
                    slimeCollected += 27;
                    Microbot.log("Alle Eimer gefüllt.");
                }
            }
        }
    }

    private void handleSlimeBanking() {
        if (!Rs2Bank.isOpen()) return;
        if (Rs2Inventory.hasItem(BUCKET_OF_SLIME_ID)) {
            Rs2Bank.depositAll(BUCKET_OF_SLIME_ID);
            sleep(400, 600);
        }
        if (!Rs2Inventory.hasItem(BUCKET_ID)) {
            if (Rs2Bank.hasItem(BUCKET_ID)) {
                Rs2Bank.withdrawX(BUCKET_ID, 27);
                sleepUntil(() -> Rs2Inventory.hasItem(BUCKET_ID), 2000);
            } else {
                Microbot.log("Keine Eimer mehr!");
                shutdown();
                return;
            }
        }
        if (Rs2Inventory.hasItem(BUCKET_ID) && !Rs2Inventory.hasItem(BUCKET_OF_SLIME_ID)) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 1500);
        }
    }

    private void applyAntibanSettings() {
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2Antiban.setActivity(Activity.GENERAL_COLLECTING);
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.moveMouseRandomly = true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }
}