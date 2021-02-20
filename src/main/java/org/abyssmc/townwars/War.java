package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.compress.utils.FileNameUtils;
import com.palmergames.paperlib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

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
    public int attackerLives;
    public int defenderLives;
    HashSet<Nation> nationsDefending = new HashSet<>();
    HashSet<Nation> nationsAttacking = new HashSet<>();
    int tick = 0;
    // TODO: Cleanup code by using this list instead of iterating upon loops and loops
    HashSet<Player> attackingPlayers = new HashSet<>();
    HashSet<Player> defendingPlayers = new HashSet<>();
    TownWarBossBar warBossbar;

    HashSet<Player> playersInitialLives = new HashSet<>();

    HashMap<Player, Location> playerLocationsWhenWarBegan = new HashMap<>();

    HashMap<Integer, HashMap<Location, BlockData>> blocksToRestore = new HashMap<>();

    public War(Town attackers, Town defenders) {
        if (attackers.hasNation() && defenders.hasNation()) isNationWar = true;
        this.warUUID = UUID.randomUUID();
        this.attackers = attackers;
        this.defenders = defenders;

        addTownsNationsToList();

        if (isNationWar) {
            warBossbar = new NationWarBossBar(this);
        } else {
            warBossbar = new TownWarBossBar(this);
        }

        warBossbar.updateBossBar();
        warBossbar.sendEveryoneBossBars();

        attackers.setAdminDisabledPVP(true);
        defenders.setAdminDisabledPVP(true);
        attackers.setAdminEnabledPVP(false);
        defenders.setAdminEnabledPVP(false);

        makeAttackersGlow();

        save(true);
    }

    // Allow loading from a saved war
    public War(File file) {
        // TODO: Fix this when transitioning to a kill based system
        load(file);

        addTownsNationsToList();
        warBossbar.updateBossBar();
        warBossbar.sendEveryoneBossBars();
    }

    public void tick() {
        if (++tick > ConfigHandler.ticksBeforeWarBegins) {
            if (tick == ConfigHandler.ticksBeforeWarBegins + 1) {
                int largerTeam = getOnlineAttackers().size();
                int temp = getOnlineDefenders().size();

                if (largerTeam < temp) largerTeam = temp;

                attackerLives = (int) (largerTeam * ConfigHandler.livesMultiplier) - getOnlineAttackers().size();
                defenderLives = (int) (largerTeam * ConfigHandler.livesMultiplier) - getOnlineDefenders().size();

                getOnlineAttackers().forEach(resident -> playersInitialLives.add(resident.getPlayer()));
                getOnlineDefenders().forEach(resident -> playersInitialLives.add(resident.getPlayer()));

                if (isNationWar) {
                    messageAll(LocaleReader.NATION_WAR_PREWAR_ENDED);
                } else {
                    messageAll(LocaleReader.TOWN_WAR_PREWAR_ENDED);
                }
            }

            currentState = WarState.WAR;

            if (tick == ConfigHandler.ticksBeforeWarBegins + 1) {
                for (Resident resident : getOnlineAttackers()) {
                    playerLocationsWhenWarBegan.put(resident.getPlayer(), resident.getPlayer().getLocation());
                }

                for (Resident resident : getOnlineDefenders()) {
                    playerLocationsWhenWarBegan.put(resident.getPlayer(), resident.getPlayer().getLocation());
                }
            }

            // This shouldn't cause lag issues.  It's just setting a boolean, nothing else.
            attackers.setAdminEnabledPVP(true);
            defenders.setAdminEnabledPVP(true);
            attackers.setAdminDisabledPVP(false);
            defenders.setAdminDisabledPVP(false);

            if (attackerLives < 0) {
                WarManager.defendersWinWar(this);
                return;
            }

            if (defenderLives < 0) {
                WarManager.attackersWinWar(this);
                return;
            }

            if (isNationWar) {
                if (tick > ConfigHandler.tickLimitNationWar) {
                    WarManager.forceEnd(this);
                    return;
                }
            } else {
                if (tick > ConfigHandler.tickLimitTownWar) {
                    WarManager.forceEnd(this);
                    return;
                }
            }

            // It shouldn't announce when the war is done and when it just starts, so it is last
            if (tick % (ConfigHandler.secondBetweenAnnounceTimeLeft * 20) == 0) {
                if (isNationWar) {
                    messageAll(LocaleReader.NATION_WAR_TIME_LEFT);
                } else {
                    messageAll(LocaleReader.TOWN_WAR_TIME_LEFT);
                }
            }
        }

        if (warBossbar != null) {
            warBossbar.updateBossBar();
        }

        // save war info every minute, just in case the server crashes
        // Save after ending war to try and stop race conditions
        // TODO: Actually fix this race condition
        if (tick % 1200 == 0) save(true);

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
            attackerLives = warConfiguration.getInt("attackerLives");
            defenderLives = warConfiguration.getInt("defenderLives");

            addTownsNationsToList();

        } catch (NotRegisteredException e) {
            e.printStackTrace();
        }

        WarManager.setupWar(this);

        if (isNationWar) {
            warBossbar = new NationWarBossBar(this);
        } else {
            warBossbar = new TownWarBossBar(this);
        }

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
        warConfiguration.set("attackerLives", attackerLives);
        warConfiguration.set("defenderLives", defenderLives);

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

    public void attackerIsDead(Resident player, PlayerDeathEvent event) {
        new Error().printStackTrace();
        attackerLives--;

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        try {
            Coord coord = defenders.getHomeBlock().getCoord();
            int x = coord.getX();
            int z = coord.getZ();


        } catch (TownyException e) {
            e.printStackTrace();
        }

    }

    public void defenderIsDead(Resident player, PlayerDeathEvent event) {
        new Error().printStackTrace();
        defenderLives--;

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    /*public void addRandomTeleport(Location location, int radius, int donutRadius) {
        int newX = location.getBlockX() + ((donutRadius + random.nextInt(radius - donutRadius)) * (random.nextBoolean() ? 1 : -1));
        int newZ = location.getBlockZ() + ((donutRadius + random.nextInt(radius - donutRadius)) * (random.nextBoolean() ? 1 : -1));

        // Try again with larger radius
        if (TownyAPI.getInstance().getTownBlock(location).hasTown()) {
            addRandomTeleport(location, radius + 8, donutRadius + 8);
            return;
        }

        PaperLib.getChunkAtAsync(location.getWorld(), newX, newZ).thenAccept(chunk -> {
            Block highBlock = location.getWorld().getHighestBlockAt(newX & 0xF, newZ & 0xF);
            if (TownWars.disallowedRTPBlocks.contains(highBlock.getType())) {
                addRandomTeleport(location, radius + 8, donutRadius + 8);
                return;
            }

            synchronized (randomTeleports) {
                randomTeleports.add(new Location(location.getWorld(), newX, highBlock.getY() + 1, newZ));
            }
        });
    }*/

    public void messagePlayer(Player player, String message) {
        LocaleReader.send(player, setPlaceholders(message));
    }


    public void messageGlobal(String message) {
        message = setPlaceholders(message);

        LocaleReader.sendToPlayers(Bukkit.getOnlinePlayers(), message);
    }

    public void messageAll(String message) {
        messageAttackers(message);
        messageDefenders(message);
    }

    public void messageAttackers(String message) {
        message = setPlaceholders(message);

        for (Resident resident : getOnlineAttackers()) {
            LocaleReader.send(resident.getPlayer(), message);
        }
    }

    public void messageAttackingNation(String message) {
        message = setPlaceholders(message);

        for (Resident resident : getAttackingNation().getResidents()) {
            if (resident.getPlayer() == null) continue;
            LocaleReader.send(resident.getPlayer(), message);
        }
    }

    public void messageDefenders(String message) {
        message = setPlaceholders(message);

        for (Resident resident : getOnlineDefenders()) {
            LocaleReader.send(resident.getPlayer(), message);
        }
    }

    public void messageDefendingNation(String message) {
        message = setPlaceholders(message);

        for (Resident resident : getDefendingNation().getResidents()) {
            if (resident.getPlayer() == null) continue;
            LocaleReader.send(resident.getPlayer(), message);
        }
    }

    public void messageAttackingAllies(String message) {
        message = setPlaceholders(message);

        for (Nation nation : nationsAttacking) {
            if (nation == getAttackingNation()) continue;

            for (Resident resident : nation.getResidents()) {
                if (resident.getPlayer() == null) continue;
                LocaleReader.send(resident.getPlayer(), message);
            }
        }
    }

    public void messageDefendingAllies(String message) {
        message = setPlaceholders(message);

        for (Nation nation : nationsDefending) {
            if (nation == getDefendingNation()) continue;
            for (Resident resident : nation.getResidents()) {
                if (resident.getPlayer() == null) continue;
                LocaleReader.send(resident.getPlayer(), message);
            }
        }
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

    // TODO: Should attackers and defenders glow?
    public void makeAttackersGlow() {
        /*for (Resident resident : getOnlineAttackers()) {

        }
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
        }*/
    }

    public void removeGlow() {
        /*if (isNationWar) {
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
        }*/
    }

    public List<Resident> getOnlineAttackers() {
        List<Resident> residents;
        if (isNationWar) {
            residents = new ArrayList<>();
            for (Nation nation : nationsAttacking) {
                for (Resident resident : nation.getResidents()) {
                    if (resident.getPlayer() == null) continue;
                    residents.add(resident);
                }
            }

        } else {
            residents = new ArrayList<>(attackers.getResidents());
            residents.removeIf(resident -> resident.getPlayer() == null);
        }
        return residents;
    }

    public List<Resident> getOnlineDefenders() {
        List<Resident> residents;
        if (isNationWar) {
            residents = new ArrayList<>();
            for (Nation nation : nationsDefending) {
                for (Resident resident : nation.getResidents()) {
                    if (resident.getPlayer() == null) continue;
                    residents.add(resident);
                }
            }

        } else {
            residents = new ArrayList<>(defenders.getResidents());
            residents.removeIf(resident -> resident.getPlayer() == null);
        }
        return residents;
    }

    public void playerJoinListener(Player player) {
        // This means their life was already deducted
        if (playersInitialLives.contains(player)) return;

        getOnlineAttackers().forEach(resident -> {
            if (resident.getPlayer() == player) {
                if (attackerLives > 0) {
                    attackerLives -= 1;
                }
            }
        });

        getOnlineDefenders().forEach(resident -> {
            if (resident.getPlayer() == player) {
                if (attackerLives > 0) {
                    attackerLives -= 1;
                }
            }
        });
    }


    public enum WarState {
        PREWAR,
        WAR
    }
}
