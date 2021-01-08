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
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

// This is the war class that cannot touch the main hashmaps and caches
public class War {
    public boolean isNationWar;
    public Town attackers;
    public Town defenders;
    public boolean attackerWantsPeace;
    public boolean defenderWantsPeace;
    public int lastPeaceRequestEpoch = Integer.MIN_VALUE / 2; // don't underflow, just have a small number
    public UUID warUUID;
    // Don't remove combat log on login to stop exploits.
    public HashMap<String, Integer> lastDamageTakenByAttackersTick = new HashMap<>();
    HashSet<Nation> nationsDefending = new HashSet<>();
    HashSet<Nation> nationsAttacking = new HashSet<>();
    HashMap<Location, BlockData> restoreBlocksAfterWar = new HashMap<>();

    // TODO: FUCK THIS!!1
    // TODO: WE NEED A HASHMAP BECAUSE EVERY TOWN IN A NATION WAR CAN BE SIEGED!
    int ticksOccupiedWithoutCombat = 0;
    int ticksWithoutAttackersOccupying = 0;
    int tick = 0;
    int tickIgnoringOccupationStatus = 0;
    public boolean hasStartedOccupation = false;

    public War(Town attackers, Town defenders) {
        if (attackers.isCapital() && defenders.isCapital()) isNationWar = true;

        this.warUUID = UUID.randomUUID();
        this.attackers = attackers;
        this.defenders = defenders;

        addTownsNationsToList();
    }

    // Allow loading from a saved war
    public War(File file) {
        load(file);
    }

    public String setPlaceholders(String string) {
        if (isNationWar) {
            if (string.contains("{NATION_ATTACKERS}")) {
                StringBuilder nationsAttackingString = new StringBuilder();
                for (Nation attackers : nationsAttacking) {
                    nationsAttackingString.append(attackers.getName());
                    nationsAttackingString.append(", ");
                }

                string = string.replace("{NATION_ATTACKERS}", nationsAttackingString.substring(0, nationsAttackingString.length() - 2));
            }

            if (string.contains("{NATION_DEFENDERS}")) {
                StringBuilder nationsDefendingString = new StringBuilder();
                for (Nation defenders : nationsDefending) {
                    nationsDefendingString.append(defenders.getName());
                    nationsDefendingString.append(", ");
                }

                string = string.replace("{NATION_DEFENDERS}", nationsDefendingString.substring(0, nationsDefendingString.length() - 2));
            }

            string = string.replace("{NATION_ATTACKER}", getAttackingNation().getName())
                    .replace("{NATION_DEFENDER}", getDefendingNation().getName());
        }

        if (string.contains("{TIME_LEFT}")) {
            int secondsLeft = ConfigHandler.timeLimitNationWar - (tick / 20);

            string = string.replace("{TIME_LEFT}", formatSeconds(secondsLeft));
        }

        return string.replace("{ATTACKERS}", attackers.getName()).replace("{DEFENDERS}", defenders.getName());
    }

    public static String formatSeconds(int secondsLeft) {
        String timeLeftString = "";

        int newSeconds = secondsLeft % 60;
        int newHours = secondsLeft / 60;
        int newMinutes = newHours % 60;
        newHours = newHours / 60;

        if (newHours == 1) timeLeftString += newHours + " hours ";
        if (newHours > 1) timeLeftString += newHours + " hours ";
        if (newMinutes == 1) timeLeftString += newMinutes + " minute ";
        if (newMinutes > 1) timeLeftString += newMinutes + " minutes ";
        if (newSeconds == 1) timeLeftString += newSeconds + " second";
        if (newSeconds > 1) timeLeftString += newSeconds + " seconds";
        if (timeLeftString.endsWith(" ")) timeLeftString = timeLeftString.substring(0, timeLeftString.length() - 1);

        return timeLeftString;
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
            lastPeaceRequestEpoch = warConfiguration.getInt("lastPeaceRequestEpoch");
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
        warConfiguration.set("lastPeaceRequestEpoch", lastPeaceRequestEpoch);
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

    public void tick() {
        // save war info every minute, just in case the server crashes
        // This also saves everything immediately
        if (tick++ % 1200 == 0) save(true);

        boolean attackersInCombat = false;
        for (Map.Entry<String, Integer> playerNameIterator : lastDamageTakenByAttackersTick.entrySet()) {
            if ((tick - playerNameIterator.getValue()) < ConfigHandler.ticksUntilNoLongerInCombat) {
                attackersInCombat = true;
                break;
            }
        }

        if (!attackersInCombat) {
            ticksOccupiedWithoutCombat++;
        }

        // TODO: Probably needs a bit of optimization
        // TODO: Auto declare war for towns during nation war
        boolean attackersOccupyingDefenders = false;
        if (isNationWar) {
            for (Nation nation : nationsAttacking) {
                for (Town town : nation.getTowns()) {
                    for (Resident resident : town.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        if (TownyAPI.getInstance().getTown(resident.getPlayer().getLocation()) == defenders) {
                            attackersOccupyingDefenders = true;
                            hasStartedOccupation = true;
                            break;
                        }
                    }
                }

            }
        } else {
            for (Resident resident : attackers.getResidents()) {
                if (resident.getPlayer() == null) continue;
                if (TownyAPI.getInstance().getTown(resident.getPlayer().getLocation()) == defenders) {
                    attackersOccupyingDefenders = true;
                    hasStartedOccupation = true;
                    break;
                }
            }
        }

        if (!attackersOccupyingDefenders && hasStartedOccupation) {
            if (++ticksWithoutAttackersOccupying > ConfigHandler.ticksWithoutAttackersOccupyingUntilDefendersWin) {
                WarManager.defendersWinWar(this);
                return;
            }

            if (ticksWithoutAttackersOccupying % ConfigHandler.ticksBetweenNotOccupyingWarning == 0) {
                messageAttackers(LocaleReader.NOT_OCCUPYING_WARNING.replace("{SECONDS_NOT_OCCUPIED_UNTIL_FAIL}", formatSeconds((ConfigHandler.ticksWithoutAttackersOccupyingUntilDefendersWin - ticksWithoutAttackersOccupying) / 20)));
            }
        } else {
            ticksWithoutAttackersOccupying = 0;
        }

        if (ticksOccupiedWithoutCombat > ConfigHandler.secondsToCaptureTown * 20) {
            WarManager.attackersWinWar(this);
            return;
        } else if (tick > ConfigHandler.timeLimitTownWar * 20) {
            WarManager.defendersWinWar(this);
            return;
        }

        // It shouldn't announce when the war is done and when it just starts
        if (tick % (ConfigHandler.secondBetweenAnnounceTimeLeft * 20) == 0) {
            messageAll(LocaleReader.NATION_WAR_TIME_LEFT);
        }
    }

    // TODO: This really isn't needed after new system
    // TODO: What do I do?
    public void attackerIsDead(Resident player) {
        messageAll(LocaleReader.ATTACKERS_SCORED_KILL);
    }

    public void defenderHasDied(Resident player) {
        messageAll(LocaleReader.DEFENDERS_SCORED_KILL);
    }

    public void messagePlayer(Player player, String message) {
        LocaleReader.send(player, setPlaceholders(message));
    }

    // We have methods to use and not use placeholders so we only call the expensive setPlaceholders method
    // once instead of twice!
    public void messageAll(String message) {
        message = setPlaceholders(message);

        messageAttackersWithoutPlaceholders(message);
        messageDefendersWithoutPlaceholders(message);
    }

    public void messageAttackers(String message) {
        message = setPlaceholders(message);
        messageAttackersWithoutPlaceholders(message);
    }

    public void messageAttackersWithoutPlaceholders(String message) {
        message = setPlaceholders(message);
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
        message = setPlaceholders(message);
        messageDefendersWithoutPlaceholders(message);
    }

    public void messageDefendersWithoutPlaceholders(String message) {
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

    public void messageAttackingAllies(String message) {
        for (Nation nation : nationsAttacking) {
            for (Town town : nation.getTowns()) {
                if (town == attackers) continue;

                for (Resident resident : town.getResidents()) {
                    if (resident.getPlayer() == null) continue;
                    LocaleReader.send(resident.getPlayer(), message);
                }
            }
        }
    }

    public void messageDefendingAllies(String message) {
        for (Nation nation : nationsAttacking) {
            for (Town town : nation.getTowns()) {
                if (town == attackers) continue;

                for (Resident resident : town.getResidents()) {
                    if (resident.getPlayer() == null) continue;
                    LocaleReader.send(resident.getPlayer(), message);
                }
            }
        }
    }

    public double calculateTotalSpentByAttackers() {
        double cost = ConfigHandler.costStartNationWar;
        cost += (nationsAttacking.size() - 1) * ConfigHandler.costJoinNationWarAttackers;

        return cost;
    }

    // Shouldn't stacktrace if this is a nation war
    public Nation getAttackingNation() {
        try {
            return attackers.getNation();
        } catch (NotRegisteredException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public Nation getDefendingNation() {
        try {
            return defenders.getNation();
        } catch (NotRegisteredException exception) {
            exception.printStackTrace();
            return null;
        }
    }
}
