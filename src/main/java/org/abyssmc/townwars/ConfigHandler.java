package org.abyssmc.townwars;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;

import static org.abyssmc.townwars.TownWars.*;

public class ConfigHandler {
    // General settings
    public static boolean conquerIfWithoutNation = true;
    public static boolean conquerIfInNation = true;
    public static double costStartTownWar = 500;
    public static double costStartNationWar = 1500;
    public static double costJoinNationWarAttackers = 500;
    public static double costJoinNationWarDefenders = 0;
    public static int secondBetweenAnnounceTimeLeft = 60;
    public static int secondToConfirmAction = 30;
    public static int minSecondsBetweenPeaceRequests = 30;
    public static int daysConqueredIfWithoutNation = 7;
    public static int daysConqueredIfInNation = 7;
    public static double nationWarBlocksTransferPercent = 0.1;
    public static double townWarBlocksTransferPercent = 0.1;

    // when people can war
    public static int cooldownSecondsAttackersLoseAttackSameTown = 604800; //  one week
    public static int cooldownSecondsAttackersLoseAttackDifferentTown = 600; // 10 minutes
    public static int cooldownSecondsDefendersLoseAttackBySameTown = 86400; // one day
    public static int cooldownSecondsDefendersLoseAttackByDifferentTown = 172800; // two days

    // when people can war
    public static int cooldownSecondsAttackersLoseAttackSameNation = 604800; //  one week
    public static int cooldownSecondsAttackersLoseAttackDifferentNation = 600; // 10 minutes
    public static int cooldownSecondsDefendersLoseAttackBySameNation = 86400; // one day
    public static int cooldownSecondsDefendersLoseAttackByDifferentNation = 172800; // two days

    // How to win the war
    public static int tickLimitTownWar = 18000;
    public static int tickLimitNationWar = 36000;
    public static int ticksToCaptureTown = 1200;
    public static int ticksUntilNoLongerInCombat = 300;
    public static int ticksWithoutAttackersOccupyingUntilDefendersWin = 400;
    public static int ticksBetweenNotOccupyingWarning = 100;
    public static int ticksBeforeWarBegins = 1200;
    public static int ticksToRestoreBrokenBlocks = 1200;
    public static int ticksToRemovePlacedBlocks = 600;
    public static double livesMultiplier = 3.0D;
    public static TownWars plugin;
    public static FileConfiguration config;

    public static void reload() {
        addMaterials();

        // General settings
        conquerIfWithoutNation = config.getBoolean("conquerIfWithoutNation", true);
        conquerIfInNation = config.getBoolean("conquerIfInNation", true);
        costStartTownWar = config.getDouble("costStartTownWar", 500);
        costStartNationWar = config.getDouble("costStartNationWar", 1500);
        costJoinNationWarAttackers = config.getDouble("costJoinNationWarAttackers", 500);
        costJoinNationWarDefenders = config.getDouble("costJoinNationWarDefenders", 0);
        secondBetweenAnnounceTimeLeft = config.getInt("secondBetweenAnnounceTimeLeft", 60);
        secondToConfirmAction = config.getInt("secondToConfirmAction", 30);
        minSecondsBetweenPeaceRequests = config.getInt("minSecondsBetweenPeaceRequests", 30);
        daysConqueredIfWithoutNation = config.getInt("daysConqueredIfWithoutNation", 7);
        daysConqueredIfInNation = config.getInt("daysConqueredIfInNation", 7);
        nationWarBlocksTransferPercent = config.getDouble("nationWarBlocksTransferEvent", 0.1);
        townWarBlocksTransferPercent = config.getDouble("townWarBlocksTransferPercent", 0.1);

        // when people can war
        cooldownSecondsAttackersLoseAttackSameNation = config.getInt("cooldown-seconds-attackers-lose-attack-same-nation", 604800); //  one week
        cooldownSecondsAttackersLoseAttackDifferentNation = config.getInt("cooldown-seconds-attackers-lose-attack-different-nation", 600); // 10 minutes
        cooldownSecondsDefendersLoseAttackBySameNation = config.getInt("cooldown-seconds-defenders-lose-attack-by-same-nation", 172800); // one day
        cooldownSecondsDefendersLoseAttackByDifferentNation = config.getInt("cooldown-seconds-defenders-lose-attack-by-different-nation", 86400); // two days


        // How to win the war
        tickLimitTownWar = config.getInt("tickLimitTownWar", 18000);
        tickLimitNationWar = config.getInt("tickLimitNationWar", 36000);
        ticksToCaptureTown = config.getInt("ticksToCaptureTown", 1200);
        ticksUntilNoLongerInCombat = config.getInt("ticksUntilNoLongerInCombat", 300);
        ticksWithoutAttackersOccupyingUntilDefendersWin = config.getInt("ticksWithoutAttackersOccupyingUntilDefendersWin", 400);
        ticksBetweenNotOccupyingWarning = config.getInt("ticksBetweenNotOccupyingWarning", 100);
        ticksBeforeWarBegins = config.getInt("ticksBeforeWarBegins", 1200);
        ticksToRestoreBrokenBlocks = config.getInt("ticksToRestoreBrokenBlocks", 1200);
        ticksToRemovePlacedBlocks = config.getInt("ticksToRemovePlacedBlocks",600);
        livesMultiplier = config.getDouble("livesMultiplier", 3.0D);
    }

    public static void addMaterials() {
        allowedSwitches = new HashSet<>();
        disallowedBlocksBroken = new HashSet<>();
        disallowedRTPBlocks = new HashSet<>();

        for (String string : config.getStringList("allowed-switches-in-war")) {
            try {
                allowedSwitches.add(Material.getMaterial(string));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse material " + string);
                e.printStackTrace();
            }
        }

        for (String string : config.getStringList("disallow-breaking-blocks-in-war")) {
            try {
                disallowedBlocksBroken.add(Material.getMaterial(string));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse material " + string);
                e.printStackTrace();
            }
        }

        for (String string : config.getStringList("blacklisted-respawn-blocks")) {
            try {
                disallowedRTPBlocks.add(Material.getMaterial(string));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse material " + string);
                e.printStackTrace();
            }
        }
    }
}
