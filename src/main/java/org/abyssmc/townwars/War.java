package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.compress.utils.FileNameUtils;
import com.palmergames.paperlib.PaperLib;
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
    public int lastPeaceRequestEpoch = Integer.MIN_VALUE / 4; // don't underflow, just have a small number
    public UUID warUUID;
    // Don't remove combat log on login to stop exploits.
    public int lastDamageTakenByAttackersTick = Integer.MIN_VALUE / 4;
    public WarState currentState = WarState.PREWAR;
    HashSet<Nation> nationsDefending = new HashSet<>();
    HashSet<Nation> nationsAttacking = new HashSet<>();
    // TODO: WE NEED A HASHMAP HERE BECAUSE EVERY TOWN IN A NATION WAR CAN BE SIEGED!
    int ticksOccupiedWithoutCombat = 0;
    int ticksWithoutAttackersOccupying = 0;
    int tick = 0;

    // TODO: Cleanup code by using this list instead of iterating upon loops and loops
    HashSet<Player> attackingPlayers = new HashSet<>();
    HashSet<Player> defendingPlayers = new HashSet<>();

    TownWarBossBar warBossbar;

    Town playerIteratorTown;
    // TODO: There has to be a better way
    HashMap<Integer, HashMap<Location, BlockData>> blocksToRestore = new HashMap<>();

    // TODO: Allow the nation of a town to participate in a town vs nation war
    public War(Town attackers, Town defenders) {
        //if (attackers.hasNation() && defenders.hasNation()) isNationWar = true;
        isNationWar = false;

        this.warUUID = UUID.randomUUID();
        this.attackers = attackers;
        this.defenders = defenders;

        if (isNationWar) {
            warBossbar = new NationWarBossBar(this);
        } else {
            warBossbar = new TownWarBossBar(this);
        }

        addTownsNationsToList();
        warBossbar.updateBossBar();
        warBossbar.sendEveryoneBossBars();

        attackers.setAdminEnabledPVP(true);
        defenders.setAdminEnabledPVP(true);

        makeAttackersGlow();
    }

    // Allow loading from a saved war
    public War(File file) {
        load(file);
    }

    public void tick() {
        // save war info every minute, just in case the server crashes
        // This also saves everything immediately
        if (tick++ % 1200 == 0) save(true);

        boolean attackersOccupyingDefenders = isAnAttackerInDefenderLand();
        boolean attackersInCombat = Math.abs(tick - lastDamageTakenByAttackersTick) < ConfigHandler.ticksUntilNoLongerInCombat;

        if (tick > ConfigHandler.ticksBeforeWarBegins) {
            if (attackersInCombat && attackersOccupyingDefenders) {
                currentState = WarState.OCCUPIED_IN_COMBAT;
                ticksWithoutAttackersOccupying = 0;

            } else if (attackersOccupyingDefenders) {
                currentState = WarState.CAPTURING;
                ticksWithoutAttackersOccupying = 0;
                ticksOccupiedWithoutCombat++;

                if (ticksOccupiedWithoutCombat > ConfigHandler.ticksToCaptureTown) {
                    WarManager.attackersWinWar(this);
                    return;
                }

            } else {
                currentState = WarState.ATTACKERS_MISSING;
                ticksOccupiedWithoutCombat = 0;

                if (++ticksWithoutAttackersOccupying > ConfigHandler.ticksWithoutAttackersOccupyingUntilDefendersWin) {
                    WarManager.defendersWinWar(this);
                    return;
                }

                if (ticksWithoutAttackersOccupying % ConfigHandler.ticksBetweenNotOccupyingWarning == 0) {
                    if (ConfigHandler.ticksWithoutAttackersOccupyingUntilDefendersWin - ticksWithoutAttackersOccupying < 20) {
                        // It gets rounded down and that's not good.
                        return;
                    }

                    messageAttackers(LocaleReader.NOT_OCCUPYING_WARNING.replace("{SECONDS_NOT_OCCUPIED_UNTIL_FAIL}", WarManager.formatSeconds((ConfigHandler.ticksWithoutAttackersOccupyingUntilDefendersWin - ticksWithoutAttackersOccupying) / 20)));
                }
            }

            if (isNationWar) {
                if (tick > ConfigHandler.tickLimitNationWar) {
                    WarManager.defendersWinWar(this);
                    return;
                }
            } else {
                if (tick > ConfigHandler.tickLimitTownWar) {
                    WarManager.defendersWinWar(this);
                    return;
                }
            }

            if (!isNationWar && ticksWithoutAttackersOccupying > ConfigHandler.ticksWithoutAttackersOccupyingUntilDefendersWin) {
                WarManager.defendersWinWar(this);
                return;
            }

            // It shouldn't announce when the war is done and when it just starts, so it is last
            if (tick % (ConfigHandler.secondBetweenAnnounceTimeLeft * 20) == 0) {
                if (isNationWar) {
                    messageAll(LocaleReader.NATION_WAR_TIME_LEFT);
                } else {
                    messageAll(LocaleReader.TOWN_WAR_TIME_LEFT
                            .replace("{SECONDS_FOR_ATTACKER_WIN}", WarManager.formatSeconds((ConfigHandler.ticksToCaptureTown - ticksOccupiedWithoutCombat) / 20)));
                }
            }
        }

        warBossbar.updateBossBar();

        // Most likely to error, so do this last
        if (blocksToRestore.containsKey(tick)) {
            for (Map.Entry<Location, BlockData> key : blocksToRestore.get(tick).entrySet()) {
                // Getting blocks is x [0 : 15] y [0 : 255] z [0 : 15]
                // 1.17 negative y coordinates will be painful, but it works for now...
                PaperLib.getChunkAtAsync(key.getKey()).thenAccept(chunk -> chunk.getBlock(key.getKey().getBlockX() & 0xF, key.getKey().getBlockY() & 0xFF, key.getKey().getBlockZ() & 0xF).setBlockData(key.getValue(), false));
            }
        }

        blocksToRestore.remove(tick);
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
            int secondsLeft;
            if (isNationWar) {
                secondsLeft = (ConfigHandler.tickLimitNationWar - tick) / 20;
            } else {
                secondsLeft = (ConfigHandler.tickLimitTownWar - tick) / 20;
            }

            string = string.replace("{TIME_LEFT}", WarManager.formatSeconds(secondsLeft));
        }

        return string.replace("{ATTACKERS}", attackers.getName()).replace("{DEFENDERS}", defenders.getName());
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

            addTownsNationsToList();

        } catch (NotRegisteredException e) {
            e.printStackTrace();
        }

        WarManager.setupWar(this, false);
        warBossbar.sendEveryoneBossBars();

        List<String> blocksToRollback = warConfiguration.getStringList("blocksToRollback");

        for (String string : blocksToRollback) {
            try {
                // Example entry: world,1543.0,64.0,809.0,minecraft:oak_slab[type=bottom,waterlogged=false]
                String[] splitList = string.split(",");

                int tick = Integer.parseInt(splitList[0]);
                String world = splitList[1];
                double x = Double.parseDouble(splitList[2]);
                double y = Double.parseDouble(splitList[3]);
                double z = Double.parseDouble(splitList[4]);
                StringBuilder blockData = new StringBuilder(splitList[5]);

                for (int i = 6; i < splitList.length; i++) {
                    blockData.append(",").append(splitList[i]);
                }

                // We can use either
                restoreBlock(new Location(Bukkit.getWorld(world), x, y, z), Bukkit.getServer().createBlockData(blockData.toString()), tick);

            } catch (Exception e) {
                TownWars.plugin.getLogger().warning("Unable to parse block data to rollback - " + string);
                TownWars.plugin.getLogger().warning("Continuing anyways... blocks may not rollback correctly and there may be more corruption");
                e.printStackTrace();
            }
        }
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


        for (Map.Entry<Integer, HashMap<Location, BlockData>> tickMap : blocksToRestore.entrySet()) {
            // It's a spaghetti sandwich!
            for (Map.Entry<Location, BlockData> iteratorBlock : tickMap.getValue().entrySet()) {
                Location blockLocation = iteratorBlock.getKey();
                String world = blockLocation.getWorld().getName();
                double x = blockLocation.getX();
                double y = blockLocation.getY();
                double z = blockLocation.getZ();
                String blockData = iteratorBlock.getValue().getAsString();

                blocksToRollback.add(tickMap.getKey() + "," + world + "," + x + "," + y + "," + z + "," + blockData);
            }
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

    public void restoreBlockPlaced(Location location, BlockData block) {
        int restoreTick = tick + ConfigHandler.ticksToRemovePlacedBlocks;

        restoreBlock(location, block, restoreTick);
    }

    public void restoreBlockBroken(Location location, BlockData block) {
        int restoreTick = tick + ConfigHandler.ticksToRestoreBrokenBlocks;

        restoreBlock(location, block, restoreTick);
    }

    public void restoreBlock(Location location, BlockData block, int restoreTick) {
        for (Map.Entry<Integer, HashMap<Location, BlockData>> tick : blocksToRestore.entrySet()) {
            if (tick.getValue().containsKey(location)) return;
        }

        if (!blocksToRestore.containsKey(restoreTick)) {
            blocksToRestore.put(restoreTick, new HashMap<>());
        }

        blocksToRestore.get(restoreTick).put(location, block);
    }

    public boolean isAnAttackerInDefenderLand() {
        if (isNationWar) {
            for (Nation nation : nationsAttacking) {
                for (Resident resident : nation.getResidents()) {
                    if (resident.getPlayer() == null || resident.getPlayer().isDead()) continue;
                    if (TownyAPI.getInstance().getTown(resident.getPlayer().getLocation()) == defenders) {
                        return true;
                    }
                }
            }
        } else {
            for (Resident resident : attackers.getResidents()) {
                if (resident.getPlayer() == null || resident.getPlayer().isDead()) continue;
                playerIteratorTown = TownyAPI.getInstance().getTown(resident.getPlayer().getLocation());
                if (playerIteratorTown != null && playerIteratorTown == defenders) {
                    return true;
                }
            }
        }
        return false;
    }

    // TODO: This really isn't needed after new system
    // TODO: What do I do?
    public void attackerIsDead(Resident player) {
        ticksOccupiedWithoutCombat = 0;
    }

    public void defenderHasDied(Resident player) {
        // TODO: Give them potions or respawn kit of basic tools so they can't be spawn killed/camped
    }

    public void messagePlayer(Player player, String message) {
        LocaleReader.send(player, setPlaceholders(message));
    }


    public void messageGlobal(String message) {
        message = setPlaceholders(message);

        LocaleReader.sendToPlayers(Bukkit.getOnlinePlayers(), message);
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
                for (Resident resident : nation.getResidents()) {
                    if (resident.getPlayer() == null) continue;
                    LocaleReader.send(resident.getPlayer(), message);
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
                for (Resident resident : nation.getResidents()) {
                    if (resident.getPlayer() == null) continue;
                    LocaleReader.send(resident.getPlayer(), message);
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
        message = setPlaceholders(message);
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
        message = setPlaceholders(message);
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

    public void makeAttackersGlow() {
        if (isNationWar) {
            for (Nation nation : nationsAttacking) {
                for (Resident resident : nation.getResidents()) {
                    if (resident.getPlayer() == null) continue;
                    resident.getPlayer().setGlowing(true);
                }
            }
        } else {
            for (Resident resident : attackers.getResidents()) {
                if (resident.getPlayer() == null) continue;
                resident.getPlayer().setGlowing(true);
            }
        }
    }

    public void removeGlow() {
        if (isNationWar) {
            for (Nation nation : nationsAttacking) {
                for (Resident resident : nation.getResidents()) {
                    if (resident.getPlayer() == null) continue;
                    resident.getPlayer().setGlowing(false);
                }
            }
        } else {
            for (Resident resident : attackers.getResidents()) {
                if (resident.getPlayer() == null) continue;
                resident.getPlayer().setGlowing(false);
            }
        }
    }

    public enum WarState {
        PREWAR,
        OCCUPIED_IN_COMBAT,
        CAPTURING,
        ATTACKERS_MISSING
    }
}
