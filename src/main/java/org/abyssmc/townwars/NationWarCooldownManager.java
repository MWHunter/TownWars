package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.DeleteNationEvent;
import com.palmergames.bukkit.towny.event.NewNationEvent;
import com.palmergames.bukkit.towny.object.Nation;
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

public class NationWarCooldownManager implements Listener {
    static File dataFolder, nationDataFolder;
    static HashMap<UUID, FileConfiguration> nationConfigs = new HashMap<>();

    // TODO: Clean up these files, not needed now since there's no working system yet
    public static void reload() {
        dataFolder = new File(TownWars.plugin.getDataFolder() + File.separator + "data");
        nationDataFolder = new File(dataFolder + File.separator + "nations");

        nationDataFolder.mkdir();

        for (Nation nation : TownyAPI.getInstance().getDataSource().getNations()) {
            createNationFile(nation.getUUID());
        }
    }

    public static void createNationFile(UUID uuid) {
        File nationFile = new File(nationDataFolder + File.separator + uuid + ".yml");
        FileConfiguration config;

        if (!nationFile.exists()) {
            try {
                nationFile.createNewFile();

                config = YamlConfiguration.loadConfiguration(nationFile);
                config.set("attacker-lost-nations", Collections.EMPTY_LIST);
                config.set("defender-lost-nations", Collections.EMPTY_LIST);

                Bukkit.getScheduler().runTaskAsynchronously(TownWars.plugin, () -> {
                    try {
                        config.save(nationFile);
                    } catch (IOException exception) {
                        exception.printStackTrace();
                    }
                });

                nationConfigs.put(uuid, config);

            } catch (IOException exception) {
                exception.printStackTrace();
            }
        } else {
            nationConfigs.put(uuid, YamlConfiguration.loadConfiguration(nationFile));
        }
    }

    public static double getAttackerLostSecondsAgoNation(UUID attacker) {
        List<String> attackerLosses = getnationConfig(attacker).getStringList("attacker-lost-nations");

        long lastLoss = -1;
        for (String string : attackerLosses) {
            String[] stringSplit = string.split(",");
            long thisLoss = Long.parseLong(stringSplit[1]);

            if (thisLoss > lastLoss) lastLoss = thisLoss;
        }

        return Instant.now().getEpochSecond() - lastLoss;
    }

    public static double getDefenderLostSecondsAgoNation(UUID defender) {
        List<String> attackerLosses = getnationConfig(defender).getStringList("defender-lost-nations");

        long lastLoss = -1;
        for (String string : attackerLosses) {
            String[] stringSplit = string.split(",");
            long thisLoss = Long.parseLong(stringSplit[1]);

            if (thisLoss > lastLoss) lastLoss = thisLoss;
        }

        return Instant.now().getEpochSecond() - lastLoss;
    }

    public static double getAttackerLastLostSecondsAgoNation(UUID attacker, UUID defender) {
        List<String> attackerLosses = getnationConfig(attacker).getStringList("attacker-lost-nations");

        long loss = -1;
        for (String string : attackerLosses) {
            String[] stringSplit = string.split(",");
            String uuid = stringSplit[0];

            if (uuid.equals(defender.toString())) {
                loss = Long.parseLong(stringSplit[1]);
            }
        }

        return Instant.now().getEpochSecond() - loss;
    }

    public static double getDefendersLostSecondsAgoNation(UUID attacker, UUID defender) {
        List<String> defenderLosses = getnationConfig(defender).getStringList("defender-lost-nations");

        long loss = -1;
        for (String string : defenderLosses) {
            String[] stringSplit = string.split(",");
            String uuid = stringSplit[0];

            if (uuid.equals(attacker.toString())) {
                loss = Long.parseLong(stringSplit[1]);
            }
        }

        return Instant.now().getEpochSecond() - loss;
    }

    public static void setAttackerLostNation(UUID attacker, UUID defender) {
        FileConfiguration attackerConfig = getnationConfig(attacker);
        List<String> attackerLosses = attackerConfig.getStringList("attacker-lost-nations");

        attackerLosses.removeIf(s -> s.startsWith(defender.toString()));
        attackerLosses.add(defender + "," + Instant.now().getEpochSecond());

        attackerConfig.set("attacker-lost-nations", attackerLosses);

        Bukkit.getScheduler().runTaskAsynchronously(TownWars.plugin, () -> {
            try {
                attackerConfig.save(new File(nationDataFolder + File.separator + defender + ".yml"));
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        });
    }

    public static void setDefenderLostNation(UUID attacker, UUID defender) {
        FileConfiguration defenderConfig = getnationConfig(defender);
        List<String> defenderLosses = defenderConfig.getStringList("defender-lost-nations");

        defenderLosses.removeIf(s -> s.startsWith(attacker.toString()));
        defenderLosses.add(attacker + "," + Instant.now().getEpochSecond());

        defenderConfig.set("defender-lost-nations", defenderLosses);

        Bukkit.getScheduler().runTaskAsynchronously(TownWars.plugin, () -> {
            try {
                defenderConfig.save(new File(nationDataFolder + File.separator + defender + ".yml"));
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        });
    }

    public static FileConfiguration getnationConfig(UUID uuid) {
        return nationConfigs.get(uuid);
    }

    @EventHandler
    public void createNationEvent(NewNationEvent event) {
        createNationFile(event.getNation().getUUID());
    }

    @EventHandler
    public void nationDeleteEvent(DeleteNationEvent event) {
        File nationFile = new File(nationDataFolder + File.separator + event.getNationUUID() + ".yml");

        Bukkit.getScheduler().runTaskAsynchronously(TownWars.plugin, nationFile::delete);
    }
}