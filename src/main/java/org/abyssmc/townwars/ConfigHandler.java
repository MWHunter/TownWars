package org.abyssmc.townwars;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;

import static org.abyssmc.townwars.TownWars.allowedSwitches;

public class ConfigHandler {
    // General settings
    public static double minPercentOnline = 0.5;
    public static double minPlayersOnline = 2;
    public static double minPlayersOnlineBypassPercent = 4;
    public static boolean conquerIfWithoutNation = true;
    public static boolean conquerIfInNation = true;
    public static boolean capitalsCanBeConquered = false;
    public static double costStartTownWar = 500;
    public static double costStartNationWar = 1500;
    public static double costJoinNationWarAttackers = 500;
    public static double costJoinNationWarDefenders = 0;
    public static int secondBetweenAnnounceTimeLeft = 60;
    public static int minSecondsBetweenPeaceRequests = 30;
    public static int daysConqueredIfWithoutNation = 7;
    public static int daysConqueredIfInNation = 7;
    public static double nationWarBlocksTransferPercent = 0.1;
    public static double townWarBlocksTransferPercent = 0.1;

    // How to win the war
    public static boolean isTimeLimitTownWar = true;
    public static int timeLimitTownWar = 12000;
    public static int killLimitTownWar = 20;
    public static boolean isTimeLimitNationWar = true;
    public static int timeLimitNationWar = 36000;
    public static int killLimitNationWar = 100;
    public static TownWars plugin;
    public static FileConfiguration config;

    public static void reload() {
        addMaterials();

        minPercentOnline = config.getDouble("minPercentOnline", 0.5);
        minPlayersOnline = config.getInt("minPlayersOnline", 2);
        minPlayersOnlineBypassPercent = config.getDouble("minPlayersOnlineBypassPercent", 5);
        conquerIfWithoutNation = config.getBoolean("conquerIfWithoutNation", true);
        conquerIfInNation = config.getBoolean("conquerIfInNation", true);
        capitalsCanBeConquered = config.getBoolean("capitalsCanBeConquered", false);
        costStartTownWar = config.getDouble("costStartTownWar", 500);
        costStartNationWar = config.getDouble("costStartNationWar", 1500);
        costJoinNationWarAttackers = config.getDouble("costJoinNationWarAttackers", 500);
        costJoinNationWarDefenders = config.getDouble("costJoinNationWarDefenders", 0);
        secondBetweenAnnounceTimeLeft = config.getInt("secondsBetweenAnnounceTimeLeft", 60);
        minSecondsBetweenPeaceRequests = config.getInt("minSecondsBetweenPeaceRequests", 30);
        daysConqueredIfWithoutNation = config.getInt("daysConqueredIfWithoutNation", 7);
        daysConqueredIfInNation = config.getInt("daysConqueredIfInNation", 7);
        nationWarBlocksTransferPercent = config.getDouble("nationWarBlocksTransferPercent", 0.1);
        townWarBlocksTransferPercent = config.getDouble("townWarBlocksTransferPercent", 0.1);

        isTimeLimitTownWar = config.getBoolean("isTimeLimitTownWar", true);
        timeLimitTownWar = config.getInt("timeLimitTownWar", 12000);
        killLimitTownWar = config.getInt("killLimitTownWar", 20);

        isTimeLimitNationWar = config.getBoolean("isTimeLimitNationWar", true);
        timeLimitNationWar = config.getInt("timeLimitNationWar", 36000);
        killLimitNationWar = config.getInt("killLimitNationWar", 100);
    }

    public static void addMaterials() {
        allowedSwitches = new HashSet<>();

        for (String string : config.getStringList("allowed-switches-in-war")) {
            try {
                allowedSwitches.add(Material.getMaterial(string));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse material " + string);
                e.printStackTrace();
            }
        }
    }
}
