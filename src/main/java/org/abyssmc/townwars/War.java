package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.compress.utils.FileNameUtils;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
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
    HashMap<Location, BlockData> restoreBlocksAfterWar = new HashMap<>();
    // TODO: WE NEED A HASHMAP HERE BECAUSE EVERY TOWN IN A NATION WAR CAN BE SIEGED!
    int ticksOccupiedWithoutCombat = 0;
    int ticksWithoutAttackersOccupying = 0;
    int tick = 0;
    BossBar warBossBar = net.kyori.adventure.bossbar.BossBar.bossBar(Component.text("secret war bossbar :)"),
            0, BossBar.Color.PINK, BossBar.Overlay.NOTCHED_10);
    Town playerIteratorTown;

    public War(Town attackers, Town defenders) {
        //if (attackers.isCapital() && defenders.isCapital()) isNationWar = true;
        isNationWar = false;

        this.warUUID = UUID.randomUUID();
        this.attackers = attackers;
        this.defenders = defenders;

        addTownsNationsToList();
        updateBossBar();
        sendEveryoneBossBars();
    }

    // Allow loading from a saved war
    public War(File file) {
        load(file);
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

            string = string.replace("{TIME_LEFT}", formatSeconds(secondsLeft));
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
        sendEveryoneBossBars();
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
                    messageAttackers(LocaleReader.NOT_OCCUPYING_WARNING.replace("{SECONDS_NOT_OCCUPIED_UNTIL_FAIL}", formatSeconds((ConfigHandler.ticksWithoutAttackersOccupyingUntilDefendersWin - ticksWithoutAttackersOccupying) / 20)));
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
                            .replace("{SECONDS_FOR_ATTACKER_WIN}", formatSeconds((ConfigHandler.ticksToCaptureTown - ticksOccupiedWithoutCombat) / 20)));
                }
            }
        }

        updateBossBar();
    }

    // TODO: This really isn't needed after new system
    // TODO: What do I do?
    public void attackerIsDead(Resident player) {
        ticksOccupiedWithoutCombat = 0;
    }

    public void defenderHasDied(Resident player) {
        //
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

    public void removeEveryoneBossBars() {
        if (isNationWar) {
            for (Nation nation : nationsAttacking) {
                for (Town town : nation.getTowns()) {
                    for (Resident resident : town.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        TownWars.adventure().player(resident.getPlayer()).hideBossBar(warBossBar);
                    }
                }
            }

            for (Nation nation : nationsDefending) {
                for (Town town : nation.getTowns()) {
                    for (Resident resident : town.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        TownWars.adventure().player(resident.getPlayer()).hideBossBar(warBossBar);
                    }
                }
            }

        } else {
            for (Resident resident : attackers.getResidents()) {
                if (resident.getPlayer() == null) continue;
                TownWars.adventure().player(resident.getPlayer()).hideBossBar(warBossBar);
            }

            for (Resident resident : defenders.getResidents()) {
                if (resident.getPlayer() == null) continue;
                TownWars.adventure().player(resident.getPlayer()).hideBossBar(warBossBar);
            }
        }
    }

    public void sendEveryoneBossBars() {
        if (isNationWar) {
            for (Nation nation : nationsAttacking) {
                for (Town town : nation.getTowns()) {
                    for (Resident resident : town.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        TownWars.adventure().player(resident.getPlayer()).showBossBar(warBossBar);
                    }
                }
            }

            for (Nation nation : nationsDefending) {
                for (Town town : nation.getTowns()) {
                    for (Resident resident : town.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        TownWars.adventure().player(resident.getPlayer()).showBossBar(warBossBar);
                    }
                }
            }

        } else {
            for (Resident resident : attackers.getResidents()) {
                if (resident.getPlayer() == null) continue;
                TownWars.adventure().player(resident.getPlayer()).showBossBar(warBossBar);
            }

            for (Resident resident : defenders.getResidents()) {
                if (resident.getPlayer() == null) continue;
                TownWars.adventure().player(resident.getPlayer()).showBossBar(warBossBar);
            }
        }
    }

    public void updateBossBar() {
        // TODO: How to do this with nation wars?
        // I prefer sending time left in chat so more valuable info isn't lost.
        /*if (ticksShowingBossBar > 0 || tick % (ConfigHandler.secondsBetweenShowTimeBossBar * 20) == 0) {
            if (++ticksShowingBossBar > ConfigHandler.secondsToShowTimeBossBar * 20) ticksShowingBossBar = 0;

            warBossBar.color(BossBar.Color.BLUE);
            warBossBar.name(Component.text().color(TextColor.color(0x51f5e7)).append(Component.text(formatSeconds(ConfigHandler.timeLimitTownWar - tick / 20)).append(Component.text(" remaining"))));
            warBossBar.progress(Math.max(0, Math.min(1, 1 - (float) ticksWithoutAttackersOccupying / ConfigHandler.ticksWithoutAttackersOccupyingUntilDefendersWin)));
        } else {*/
        switch (currentState) {
            case CAPTURING:
                warBossBar.color(BossBar.Color.GREEN);
                warBossBar.name(Component.text().color(TextColor.color(0x34eb49)).append(Component.text("Capture in progress")));
                warBossBar.progress(Math.max(0, Math.min(1, (float) ticksOccupiedWithoutCombat / ConfigHandler.ticksToCaptureTown)));
                break;
            case OCCUPIED_IN_COMBAT:
                warBossBar.color(BossBar.Color.YELLOW);
                warBossBar.name(Component.text().color(TextColor.color(0xf7f719)).append(Component.text("Attackers in combat")));
                warBossBar.progress(Math.max(0, Math.min(1, 1 - (float) Math.abs(tick - lastDamageTakenByAttackersTick) / ConfigHandler.ticksUntilNoLongerInCombat)));
                break;
            case ATTACKERS_MISSING:
                warBossBar.color(BossBar.Color.RED);
                warBossBar.name(Component.text().color(TextColor.color(0xe01010)).append(Component.text("Attackers absent")));
                warBossBar.progress(Math.max(0, Math.min(1, 1 - (float) ticksWithoutAttackersOccupying / ConfigHandler.ticksWithoutAttackersOccupyingUntilDefendersWin)));
                break;
            case PREWAR:
                // TODO: Progress bar for this
                warBossBar.color(BossBar.Color.WHITE);
                warBossBar.name(Component.text().color(TextColor.color(0xe01010)).append(Component.text("War declared")));
                warBossBar.progress(Math.max(0, Math.min(1, 1 - (float) tick / ConfigHandler.ticksBeforeWarBegins)));
                break;
        }
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
