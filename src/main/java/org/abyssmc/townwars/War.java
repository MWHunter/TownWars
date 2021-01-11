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
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

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

    HashSet<Player> attackingPlayers = new HashSet<>();
    HashSet<Player> defendingPlayers = new HashSet<>();

    BossBar warBossbar = new BossBar(this);

    Town playerIteratorTown;
    // TODO: There has to be a better way
    HashMap<Integer, HashMap<Location, BlockState>> blocksToRestore = new HashMap<>();

    // TODO: Allow the nation of a town to participate in a town vs nation war
    public War(Town attackers, Town defenders) {
        if (attackers.hasNation() && defenders.hasNation()) isNationWar = true;

        this.warUUID = UUID.randomUUID();
        this.attackers = attackers;
        this.defenders = defenders;

        addTownsNationsToList();
        warBossbar.updateBossBar();
        warBossbar.sendEveryoneBossBars();
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
        // TODO: Complete containers
        if (blocksToRestore.containsKey(tick)) {
            for (Map.Entry<Location, BlockState> key : blocksToRestore.get(tick).entrySet()) {
                PaperLib.getChunkAtAsync(key.getKey()).thenAccept(chunk -> {
                    Block targetBlock = chunk.getBlock(key.getKey().getBlockX(), key.getKey().getBlockY(), key.getKey().getBlockZ());
                    targetBlock.setBlockData(key.getValue().getBlockData());
                    if (targetBlock.getState() instanceof Container) {
                        Container targetContainer = (Container) targetBlock;
                        ((Container) targetBlock).getInventory().getContents();
                    }
                });
            }
        }
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

    public void restoreBlockPlaced(Location location, BlockState block) {
        int restoreTick = ConfigHandler.ticksToRemovePlacedBlocks;

        if (!blocksToRestore.containsKey(restoreTick)) {
            blocksToRestore.put(restoreTick, new HashMap<>());
        }

        blocksToRestore.get(restoreTick).put(location, block);
    }

    public void restoreBlockBroken(Location location, BlockState block) {
        int restoreTick = ConfigHandler.ticksToRestoreBrokenBlocks;

        if (!blocksToRestore.containsKey(restoreTick)) {
            blocksToRestore.put(restoreTick, new HashMap<>());
        }

        blocksToRestore.get(restoreTick).put(location, block);
    }

    public boolean isAnAttackerInDefenderLand() {
        if (isNationWar) {
            for (Nation nation : nationsAttacking) {
                for (Town town : nation.getTowns()) {
                    for (Resident resident : town.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        if (TownyAPI.getInstance().getTown(resident.getPlayer().getLocation()) == defenders) {
                            return true;
                        }
                    }
                }

            }
        } else {
            for (Resident resident : attackers.getResidents()) {
                if (resident.getPlayer() == null) continue;
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

    public enum WarState {
        PREWAR,
        OCCUPIED_IN_COMBAT,
        CAPTURING,
        ATTACKERS_MISSING
    }
}
