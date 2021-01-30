package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.DeleteTownEvent;
import com.palmergames.bukkit.towny.event.NewTownEvent;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class WarCooldownManager implements Listener {
    static File dataFolder, townDataFolder, nationDataFolder;
    static HashMap<UUID, FileConfiguration> townConfigs = new HashMap<>();

    // TODO: Clean up these files, not needed now since there's no working system yet
    public static void reload() {
        dataFolder = new File(TownWars.plugin.getDataFolder() + File.separator + "data");
        townDataFolder = new File(dataFolder + File.separator + "towns");
        nationDataFolder = new File(dataFolder + File.separator + "towns");

        townDataFolder.mkdirs();
        nationDataFolder.mkdir();

        for (Town town : TownyAPI.getInstance().getDataSource().getTowns()) {
            createTownFile(town.getUUID());
        }
    }

    public static void createTownFile(UUID uuid) {
        File townFile = new File(townDataFolder + File.separator + uuid + ".yml");
        FileConfiguration config;

        if (!townFile.exists()) {
            try {
                townFile.createNewFile();

                config = YamlConfiguration.loadConfiguration(townFile);
                config.set("attacker-lost-towns", Collections.EMPTY_LIST);
                config.set("defender-lost-epoch", -1);

                Bukkit.getScheduler().runTaskAsynchronously(TownWars.plugin, () -> {
                    try {
                        config.save(townFile);
                    } catch (IOException exception) {
                        exception.printStackTrace();
                    }
                });

                townConfigs.put(uuid, config);

            } catch (IOException exception) {
                exception.printStackTrace();
            }
        } else {
            townConfigs.put(uuid, YamlConfiguration.loadConfiguration(townFile));
        }
    }

    // Y2k38 - use double not int
    public static double getAttackerLostEpoch(UUID attacker) {
        List<String> attackerLosses = getTownConfig(attacker).getStringList("attacker-lost-towns");

        long lastLoss = -1;
        for (String string : attackerLosses) {
            String[] stringSplit = string.split(",");
            long thisLoss = Long.parseLong(stringSplit[1]);

            if (thisLoss > lastLoss) lastLoss = thisLoss;
        }

        return lastLoss;
    }

    public static long getDefenderLostEpoch(UUID defender) {
        return getTownConfig(defender).getLong("defender-lost-epoch");
    }

    public static double getAttackerLastLost(UUID attacker, UUID defender) {
        List<String> attackerLosses = getTownConfig(attacker).getStringList("attacker-lost-towns");

        for (String string : attackerLosses) {
            String[] stringSplit = string.split(",");
            String uuid = stringSplit[0];

            if (uuid.equals(defender.toString())) {
                return Long.parseLong(stringSplit[1]);
            }
        }

        return -1;
    }

    public static void setAttackerLost(UUID attacker, UUID defender) {
        FileConfiguration attackerConfig = getTownConfig(attacker);
        List<String> attackerLosses = attackerConfig.getStringList("attacker-lost-towns");

        attackerLosses.removeIf(s -> s.startsWith(defender.toString()));
        attackerLosses.add(defender + "," + Instant.now().getEpochSecond());

        attackerConfig.set("attacker-lost-towns", attackerLosses);

        Bukkit.getScheduler().runTaskAsynchronously(TownWars.plugin, () -> {
            try {
                attackerConfig.save(new File(townDataFolder + File.separator + defender + ".yml"));
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        });
    }

    public static void setDefenderLost(UUID defender) {
        FileConfiguration defenderConfig = getTownConfig(defender);
        defenderConfig.set("defender-lost-epoch", Instant.now().getEpochSecond());

        Bukkit.getScheduler().runTaskAsynchronously(TownWars.plugin, () -> {
            try {
                defenderConfig.save(new File(townDataFolder + File.separator + defender + ".yml"));
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        });
    }

    public static FileConfiguration getTownConfig(UUID uuid) {
        return townConfigs.get(uuid);
    }

    @EventHandler
    public void createTownEvent(NewTownEvent event) {
        createTownFile(event.getTown().getUUID());
    }

    @EventHandler
    public void townDeleteEvent(DeleteTownEvent event) {
        File townFile = new File(townDataFolder + File.separator + event.getTownUUID() + ".yml");

        Bukkit.getScheduler().runTaskAsynchronously(TownWars.plugin, townFile::delete);
    }
}