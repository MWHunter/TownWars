package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.compress.utils.FileNameUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

// This is the war class that cannot touch the main hashmaps and caches
public class War {
    public boolean isNationWar;
    public Town attackers;
    public Town defenders;
    public boolean attackerWantsPeace;
    public boolean defenderWantsPeace;
    public Long startTimeEpoch;
    public Long lastPeaceRequestEpoch = 0L;
    public int attackerKills = 0;
    public int defenderKills = 0;
    public UUID warUUID;
    HashSet<Nation> nationsDefending = new HashSet<>();
    HashSet<Nation> nationsAttacking = new HashSet<>();
    HashMap<Location, BlockData> restoreBlocksAfterWar = new HashMap<>();
    int tick = 0;

    public War(Town attackers, Town defenders, boolean isNationWar) {
        // This means we are loading from a saved war
        if (attackers == null && defenders == null) return;

        this.warUUID = UUID.randomUUID();
        this.attackers = attackers;
        this.defenders = defenders;
        this.isNationWar = isNationWar;
        startTimeEpoch = Instant.now().getEpochSecond();

        addTownsNationsToList();
    }

    public void load(File warFile) {
        YamlConfiguration warConfiguration = YamlConfiguration.loadConfiguration(warFile);

        try {
            warUUID = UUID.fromString(FileNameUtils.getBaseName(warFile.getName()));
            isNationWar = warConfiguration.getBoolean("isNationWar");
            attackers = TownyAPI.getInstance().getDataSource().getTown(UUID.fromString(warConfiguration.getString("attackers")));
            defenders = TownyAPI.getInstance().getDataSource().getTown(UUID.fromString(warConfiguration.getString("defenders")));
            attackerWantsPeace = warConfiguration.getBoolean("attackerWantsPeace");
            defenderWantsPeace = warConfiguration.getBoolean("defenderWantsPeace");
            startTimeEpoch = warConfiguration.getLong("startTimeEpoch");
            lastPeaceRequestEpoch = warConfiguration.getLong("lastPeaceRequestEpoch");
            attackerKills = warConfiguration.getInt("attackerKills");
            defenderKills = warConfiguration.getInt("defenderKills");
            tick = warConfiguration.getInt("tick");

            List<String> blocksToRollback = warConfiguration.getStringList("blocksToRollback");

            addTownsNationsToList();

            for (String string : blocksToRollback) {
                try {
                    // Example entry: world,1543.0,64.0,809.0,minecraft:oak_slab[type=bottom,waterlogged=false]
                    String[] splitList = string.split(",");
                    String world = splitList[0];
                    double x = Double.parseDouble(splitList[1]);
                    double y = Double.parseDouble(splitList[2]);
                    double z = Double.parseDouble(splitList[3]);
                    StringBuilder blockData = new StringBuilder(splitList[4]);

                    for (int i = 5; i < splitList.length; i++) {
                        blockData.append(",").append(splitList[i]);
                    }

                    restoreBlocksAfterWar.put(new Location(Bukkit.getWorld(world), x, y, z), Bukkit.getServer().createBlockData(blockData.toString()));
                } catch (Exception e) {
                    TownWars.plugin.getLogger().warning("Unable to parse block data to rollback - " + string);
                    TownWars.plugin.getLogger().warning("Continuing anyways... blocks may not rollback correctly and there may be more corruption");
                    e.printStackTrace();
                }

            }

        } catch (NotRegisteredException e) {
            e.printStackTrace();
        }

        WarManager.setupWar(this, false);
    }

    // If we are shutting down the server, do it sync to 100% be sure the save finishes
    public void save(boolean async) {
        File warFolder = new File(TownWars.plugin.getDataFolder() + File.separator + "wars");
        File warFile = new File(warFolder + File.separator + warUUID + ".yml");
        YamlConfiguration warConfiguration = YamlConfiguration.loadConfiguration(warFile);

        warConfiguration.set("isNationWar", isNationWar);
        warConfiguration.set("attackers", attackers.getUUID().toString());
        warConfiguration.set("defenders", defenders.getUUID().toString());
        warConfiguration.set("attackerWantsPeace", attackerWantsPeace);
        warConfiguration.set("defenderWantsPeace", defenderWantsPeace);
        warConfiguration.set("startTimeEpoch", startTimeEpoch);
        warConfiguration.set("lastPeaceRequestEpoch", lastPeaceRequestEpoch);
        warConfiguration.set("attackerKills", attackerKills);
        warConfiguration.set("defenderKills", defenderKills);
        warConfiguration.set("tick", tick);

        // Saving blocks to roll back
        List<String> blocksToRollback = new ArrayList<>();

        for (Map.Entry<Location, BlockData> block : restoreBlocksAfterWar.entrySet()) {
            Location blockLocation = block.getKey();
            String world = blockLocation.getWorld().getName();
            double x = blockLocation.getX();
            double y = blockLocation.getY();
            double z = blockLocation.getZ();
            String blockData = block.getValue().getAsString();

            blocksToRollback.add(world + "," + x + "," + y + "," + z + "," + blockData);
        }

        warConfiguration.set("blocksToRollback", blocksToRollback);

        if (!async) {
            try {
                warConfiguration.save(warFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(TownWars.plugin, () -> {
                try {
                    warConfiguration.save(warFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void addTownsNationsToList() {
        try {
            if (attackers.hasNation()) {
                nationsAttacking.add(attackers.getNation());
            }

            if (defenders.hasNation()) {
                nationsDefending.add(defenders.getNation());
            }
        } catch (NotRegisteredException e) {
            e.printStackTrace();
        }
    }

    public void delete() {
        File warFolder = new File(TownWars.plugin.getDataFolder() + File.separator + "wars");
        File warFile = new File(warFolder + File.separator + warUUID + ".yml");

        Bukkit.getScheduler().runTaskAsynchronously(TownWars.plugin, warFile::delete);
    }

    public void restoreBlock(Location location, BlockData block) {
        if (restoreBlocksAfterWar.containsKey(location)) return;
        restoreBlocksAfterWar.put(location, block);
    }

    // TODO: Message time left in war
    public void tick() {
        // save war info every minute, just in case the server crashes
        // This also saves everything immediately
        if (tick++ % 1200 == 0) save(true);

        if (isNationWar) {
            if (ConfigHandler.isTimeLimitNationWar && tick > ConfigHandler.timeLimitNationWar
                    || attackerKills >= ConfigHandler.killLimitNationWar
                    || defenderKills >= ConfigHandler.killLimitNationWar) {
                endWarForcefully();
            }
        } else {
            if (ConfigHandler.isTimeLimitTownWar && tick > ConfigHandler.timeLimitTownWar
                    || attackerKills >= ConfigHandler.killLimitTownWar
                    || defenderKills >= ConfigHandler.killLimitTownWar) {
                endWarForcefully();
            }
        }

        // It shouldn't announce when the war is done and when it just starts
        if (tick % (ConfigHandler.secondBetweenAnnounceTimeLeft * 20) == 0) {
            messageAll(LocaleReader.NATION_WAR_TIME_LEFT.replace("{TIME}", String.valueOf((ConfigHandler.timeLimitTownWar - tick) / 20)));
        }
    }

    public void checkIfDone() {
        Bukkit.broadcastMessage(isNationWar + "");
        Bukkit.broadcastMessage(attackerKills + " " + ConfigHandler.killLimitTownWar);
        Bukkit.broadcastMessage(defenderKills + " " + ConfigHandler.killLimitTownWar);
        if (isNationWar && (attackerKills >= ConfigHandler.killLimitNationWar || defenderKills >= ConfigHandler.killLimitNationWar)) {
            endWarForcefully();
        } else if (!isNationWar && (attackerKills >= ConfigHandler.killLimitTownWar || defenderKills >= ConfigHandler.killLimitTownWar)) {
            endWarForcefully();
        }
    }

    public void endWarForcefully() {
        if (attackerKills < defenderKills) {
            WarManager.attackersLoseWar(this);
        } else if (attackerKills > defenderKills){
            WarManager.defendersLoseWar(this);
        } else {
            // It's a draw!  No one wins anything... should stop defenders from simply logging out when a war starts.
            if (isNationWar) {
                try {
                    messageAttackers(LocaleReader.ATTACKERS_YOU_TIED_NATION_WAR.replace("{NATION_DEFENDERS}", defenders.getNation().getName()));
                    messageDefenders(LocaleReader.DEFENDERS_YOU_TIED_NATION_WAR.replace("{NATION_ATTACKERS}", attackers.getNation().getName()));
                } catch (NotRegisteredException e) {
                    e.printStackTrace();
                }
            } else {
                messageAttackers(LocaleReader.ATTACKERS_YOU_TIED_TOWN_WAR.replace("{DEFENDERS}", defenders.getName()));
                messageDefenders(LocaleReader.DEFENDERS_YOU_TIED_TOWN_WAR.replace("{ATTACKERS}", attackers.getName()));
            }

            WarManager.returnToPeace(this);
        }
    }

    public void incrementAttackerKills() {
        attackerKills++;
        messageAll(LocaleReader.ATTACKERS_SCORED_KILL.replace("{ATTACKERS_KILLS}", attackerKills + "").replace("{DEFENDERS_KILLS}", defenderKills + ""));
        checkIfDone();
    }

    public void incrementDefenderKills() {
        defenderKills++;
        messageAll(LocaleReader.DEFENDERS_SCORED_KILL.replace("{ATTACKERS_KILLS}", attackerKills + "").replace("{DEFENDERS_KILLS}", defenderKills + ""));
        checkIfDone();
    }


    public void messageAll(String message) {
        messageAttackers(message);
        messageDefenders(message);
    }

    public void messageAttackers(String message) {
        if (isNationWar) {
            for (Nation nation : nationsAttacking) {
                for (Town town : nation.getTowns()) {
                    for (Resident resident : town.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        LocaleReader.send(resident.getPlayer(), message);
                    }
                }
            }
        } else {
            for (Resident resident : attackers.getResidents()) {
                if (resident.getPlayer() == null) continue;
                LocaleReader.send(resident.getPlayer(), message);
            }
        }
    }

    public void messageDefenders(String message) {
        if (isNationWar) {
            for (Nation nation : nationsDefending) {
                for (Town town : nation.getTowns()) {
                    for (Resident resident : town.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        LocaleReader.send(resident.getPlayer(), message);
                    }
                }
            }
        } else {
            for (Resident resident : defenders.getResidents()) {
                if (resident.getPlayer() == null) continue;
                LocaleReader.send(resident.getPlayer(), message);
            }
        }
    }

    public double calculateTotalSpentByAttackers() {
        double cost = ConfigHandler.costStartNationWar;
        cost += (nationsAttacking.size() - 1) * ConfigHandler.costJoinNationWarAttackers;

        return cost;
    }
}
