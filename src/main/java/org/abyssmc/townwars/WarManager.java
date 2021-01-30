package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.EconomyException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.paperlib.PaperLib;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

// This class plays with War objects.
// It is allowed to play with the main hashsets and caches
public class WarManager {
    private static final TownyAPI towny = TownyAPI.getInstance();

    public static void confirmWar(String[] args, Player player) {

        Town playerTown = WarUtility.getPlayerTown(player);
        if (playerTown == null) {
            LocaleReader.send(player, LocaleReader.COMMAND_NOT_PART_OF_TOWN);
            return;
        }

        switch (args[0]) {
            case "declare": {
                // This should not be possible
                if (args.length < 2) {
                    LocaleReader.send(player, LocaleReader.COMMAND_NO_SPECIFIED_TOWN);
                    return;
                }

                Town targetTown = WarUtility.parseTargetTown(args[1]);

                if (targetTown == null) {
                    LocaleReader.send(player, LocaleReader.COMMAND_TOWN_NOT_FOUND);
                    return;
                }

                //boolean isNationWar = targetTown.isCapital() && playerTown.isCapital();
                boolean isNationWar = false;

                try {
                    if (isNationWar) {
                        if (!playerTown.getAccount().canPayFromHoldings(ConfigHandler.costStartNationWar)) {
                            LocaleReader.send(player, LocaleReader.COMMAND_NOT_ENOUGH_BALANCE_NATION_WAR.replace("{COST}", String.valueOf(ConfigHandler.costStartNationWar)));
                            return;
                        }

                        playerTown.getAccount().withdraw(ConfigHandler.costStartNationWar, LocaleReader.NATION_WAR_WITHDRAW);
                    } else {
                        if (!playerTown.getAccount().canPayFromHoldings(ConfigHandler.costStartTownWar)) {
                            LocaleReader.send(player, LocaleReader.COMMAND_NOT_ENOUGH_BALANCE_TOWN_WAR.replace("{COST}", String.valueOf(ConfigHandler.costStartTownWar)));
                            return;
                        }

                        playerTown.getAccount().withdraw(ConfigHandler.costStartTownWar, LocaleReader.TOWN_WAR_WITHDRAW);
                    }

                } catch (EconomyException e) {
                    e.printStackTrace();
                    LocaleReader.send(player, LocaleReader.WITHDRAW_ERROR);
                    return;
                }

                War war = new War(playerTown, targetTown);
                setupWar(war, true);

                return;
            }
            case "join": {
                if (args.length < 2) {
                    LocaleReader.send(player, LocaleReader.COMMAND_NO_SPECIFIED_WAR);
                    return;
                }

                if (!playerTown.hasNation()) {
                    LocaleReader.send(player, LocaleReader.COMMAND_NOT_PART_OF_NATION);
                    return;
                }

                Town targetTown = WarUtility.parseTargetTown(args[1]);

                for (War war : TownWars.currentWars) {
                    if (war.defenders != targetTown) continue;

                    if (!war.isNationWar) {
                        LocaleReader.send(player, LocaleReader.COMMAND_NOT_NATION_WAR);
                        return;
                    }

                    // error should never happen
                    try {
                        Nation playerNation = playerTown.getNation();
                        Nation targetNation = targetTown.getNation();

                        Nation attackingNation = war.getAttackingNation();

                        if (!checkValidJoinWarInputs(playerTown, targetTown, targetNation, player)) return;

                        if (playerNation.hasAlly(attackingNation) && playerNation.hasAlly(targetNation)) { // Unknown what player is joining as
                            LocaleReader.send(player, LocaleReader.COMMAND_ALLY_OF_BOTH_ATTACKERS_AND_DEFENDERS
                                    .replace("{ATTACKING_NATION}", war.getAttackingNation().getName())
                                    .replace("{DEFENDING_NATION}", war.getDefendingNation().getName()));
                        } else if (playerNation.hasAlly(targetNation)) { // The player is joining as a defender
                            try {
                                if (!playerTown.getAccount().canPayFromHoldings(ConfigHandler.costJoinNationWarDefenders)) {
                                    LocaleReader.send(player, LocaleReader.COMMAND_NOT_ENOUGH_BALANCE_NATION_WAR.replace("{COST}", String.valueOf(ConfigHandler.costStartNationWar)));
                                    return;
                                }

                                playerTown.getAccount().withdraw(ConfigHandler.costJoinNationWarDefenders, LocaleReader.NATION_WAR_JOIN_DEFENDERS);

                            } catch (EconomyException e) {
                                e.printStackTrace();
                                LocaleReader.send(player, LocaleReader.WITHDRAW_ERROR);
                                return;
                            }

                            war.messageAll(LocaleReader.WAR_JOINED_AS_DEFENDER
                                    .replace("{NATION}", playerNation.getName()));

                            if (!TownWars.nationCurrentNationWars.containsKey(playerNation)) {
                                TownWars.nationCurrentNationWars.put(playerNation, new HashSet<>());
                            }

                            TownWars.nationCurrentNationWars.get(playerNation).add(war);
                            war.nationsDefending.add(playerNation);

                        } else if (playerNation.hasAlly(attackingNation)) { // Player joining as attacker
                            war.messageAll(LocaleReader.WAR_JOINED_AS_ATTACKER
                                    .replace("{NATION}", playerNation.getName()));

                            if (!TownWars.nationCurrentNationWars.containsKey(playerNation)) {
                                TownWars.nationCurrentNationWars.put(playerNation, new HashSet<>());
                            }

                            TownWars.nationCurrentNationWars.get(playerNation).add(war);
                            war.nationsAttacking.add(playerNation);
                        } else {
                            LocaleReader.send(player, LocaleReader.COMMAND_NOT_ALLY_OF_DEFENDER_OR_ATTACKER);
                        }

                    } catch (NotRegisteredException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static boolean isPartOfWar(Player player, War war) {
        if (war.isNationWar) {
            for (Nation nation : war.nationsAttacking) {
                for (Resident resident : nation.getResidents()) {
                    if (resident.getUUID().equals(player.getUniqueId())) {
                        return true;
                    }
                }
            }

            for (Nation nation : war.nationsDefending) {
                for (Resident resident : nation.getResidents()) {
                    if (resident.getUUID().equals(player.getUniqueId())) return true;
                }
            }

        } else {
            for (Resident resident : war.attackers.getResidents()) {
                if (resident.getUUID().equals(player.getUniqueId())) return true;
            }

            for (Resident resident : war.defenders.getResidents()) {
                if (resident.getUUID().equals(player.getUniqueId())) return true;
            }

        }
        return false;
    }

    public static boolean checkCanCreateWar(Town attackers, Town defenders, Player player) {
        //boolean isNationWar = attackers.isCapital() && defenders.isCapital();
        boolean isNationWar = false;

        if (defenders == null) {
            LocaleReader.send(player, LocaleReader.COMMAND_TOWN_NOT_FOUND);
            return true;
        }

        if (attackers == defenders) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_YOURSELF);
            return false;
        }

        if (!attackers.hasNation()) {
            LocaleReader.send(player, LocaleReader.ATTACKERS_MUST_BE_IN_NATION);
            return false;
        }

        if (!defenders.hasNation()) {
            LocaleReader.send(player, LocaleReader.DEFENDERS_MUST_BE_IN_NATION);
            return false;
        }

        if (!checkCanCreateNationWar(WarUtility.getNation(attackers), WarUtility.getNation(defenders), player))
            return false;

        if (attackers.isAlliedWith(defenders)) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_ALLIES);
            return false;
        }

        double lastWarLost = WarCooldownManager.getAttackerLastLost(attackers.getUUID(), defenders.getUUID());

        if (lastWarLost > ConfigHandler.cooldownSecondsAttackersLoseAttackSameTown) {
            LocaleReader.send(player, LocaleReader.COOLDOWN_ATTACKERS_LOSE_ATTACK_SAME_TOWN
                    .replace("{DEFENDERS}", defenders.getName())
                    .replace("{TIME}", formatSeconds((int) (ConfigHandler.cooldownSecondsAttackersLoseAttackSameTown - lastWarLost))));
            return false;
        }

        if (lastWarLost > ConfigHandler.cooldownSecondsAttackersLoseAttackDifferentTown) {
            LocaleReader.send(player, LocaleReader.COOLDOWN_ATTACKERS_LOSE_ATTACK_DIFFERENT_TOWN
                    .replace("{TIME}", formatSeconds((int) (ConfigHandler.cooldownSecondsAttackersLoseAttackDifferentTown - lastWarLost));
            return false;
        }

        if (WarCooldownManager.getDefenderLostEpoch(defenders.getUUID()) > ConfigHandler.cooldownSecondsDefendersLoseAttackByDifferentTown) {
            LocaleReader.send(player, LocaleReader.COOLDOWN_DEFENDERS_LOSE_ATTACK_BY_DIFFERENT_TOWN
                    .replace("{DEFENDERS}", defenders.getName())
                    .replace("{TIME}", formatSeconds((int) (ConfigHandler.cooldownSecondsAttackersLoseAttackSameTown - lastWarLost))));
            return false;
        }

        int online = 0;
        int requiredOnline = 0;

        for (Resident resident : defenders.getResidents()) {
            if (resident.getPlayer() != null) ++online;
        }

        // Get how many players are required to be online
        for (String string : TownWars.plugin.getConfig().getStringList("min-online-to-be-attacked-town-wars")) {
            try {
                String[] split = string.split(", ");
                int minOnline = Integer.parseInt(split[0]);
                int townSize = Integer.parseInt(split[1]);

                // Check if town is large enough to apply, and another setting hasn't applied a larger amount
                // The latter can happen with an unsorted list of minimum players online
                if (online >= townSize && requiredOnline < minOnline) {
                    requiredOnline = minOnline;
                }

            } catch (NumberFormatException e) {
                TownWars.plugin.getLogger().warning("Unable to parse " + string + " as a minimum amount online");
                TownWars.plugin.getLogger().warning("Correct format is: - \"1, 2\"");
                e.printStackTrace();
            }
        }

        // TODO: Stop nations from creating a capital and having no players online in the capital
        // TODO: Do this by making nations have a different set of values for starting wars than towns.
        if (online < requiredOnline) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_NOT_ENOUGH_DEFENDERS_ONLINE
                    .replace("{CURRENT_PLAYERS_ONLINE}", String.valueOf(online))
                    .replace("{MINIMUM_ONLINE}", String.valueOf(requiredOnline)));

            return false;
        }

        for (War war : TownWars.currentWars) {
            if (war.attackers == attackers && war.defenders == defenders || war.defenders == attackers && war.attackers == defenders) {
                LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_ALREADY_AT_WAR);
                return false;
            }
        }

        try {
            if (isNationWar) {
                if (!attackers.getAccount().canPayFromHoldings(ConfigHandler.costStartNationWar)) {
                    LocaleReader.send(player, LocaleReader.COMMAND_NOT_ENOUGH_BALANCE_NATION_WAR.replace("{COST}", String.valueOf(ConfigHandler.costStartNationWar)));
                    return false;
                }

            } else {
                if (!attackers.getAccount().canPayFromHoldings(ConfigHandler.costStartTownWar)) {
                    LocaleReader.send(player, LocaleReader.COMMAND_NOT_ENOUGH_BALANCE_TOWN_WAR.replace("{COST}", String.valueOf(ConfigHandler.costStartTownWar)));
                    return false;
                }
            }

        } catch (EconomyException e) {
            e.printStackTrace();
            LocaleReader.send(player, LocaleReader.WITHDRAW_ERROR);
        }

        return true;
    }

    public static boolean checkCanCreateNationWar(Nation attackers, Nation defenders, Player player) {
        if (attackers.isAlliedWith(defenders)) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_ALLIES);
            return false;
        }

        if (attackers.isNeutral()) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_AS_NEUTRAL);
            return false;
        }

        if (defenders.isNeutral()) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_NEUTRAL);
            return false;
        }

        return true;
    }

    // sendMessages is set to false during a reload.
    public static void setupWar(War war, boolean sendMessages) {
        Town attackers = war.attackers;
        Town defenders = war.defenders;
        //boolean isNationWar = attackers.isCapital() && defenders.isCapital();
        boolean isNationWar = false;
        war.isNationWar = isNationWar;

        TownWars.currentWars.add(war);
        TownWars.townsUnderSiege.put(defenders, war);

        if (isNationWar) {
            try {
                HashSet<War> currentWars = TownWars.nationCurrentNationWars.get(defenders.getNation());
                if (currentWars == null) currentWars = new HashSet<>();
                currentWars.add(war);
                TownWars.nationCurrentNationWars.put(defenders.getNation(), currentWars);

                currentWars = TownWars.nationCurrentNationWars.get(attackers.getNation());
                if (currentWars == null) currentWars = new HashSet<>();
                currentWars.add(war);
                TownWars.nationCurrentNationWars.put(attackers.getNation(), currentWars);
            } catch (NotRegisteredException e) {
                e.printStackTrace();
            }
        }

        if (!sendMessages) return;

        // Here begins the part where we send messages.
        if (war.isNationWar) {
            try {
                war.messageAttackers(LocaleReader.COMMAND_NOW_AT_WAR_WITH_NATION
                        .replace("{CAPITAL_BEING_SIEGED}", defenders.getName()));
                war.messageDefenders(LocaleReader.DEFENDERS_NATION_WAR_WAS_DECLARED
                        .replace("{CAPITAL_BEING_SIEGED}", defenders.getName()));

                // This is about messaging allies, not the nation or people in the war.
                for (Nation nation : attackers.getNation().getAllies()) {
                    for (Resident resident : nation.getResidents()) {
                        if (resident.getPlayer() == null) continue;

                        LocaleReader.send(resident.getPlayer(), LocaleReader.ASSIST_NATION_ATTACK
                                .replace("{CAPITAL_BEING_SIEGED}", defenders.getName()));
                    }
                }

                for (Nation nation : defenders.getNation().getAllies()) {
                    for (Resident resident : nation.getResidents()) {
                        if (resident.getPlayer() == null) continue;

                        LocaleReader.send(resident.getPlayer(), LocaleReader.ASSIST_NATION_DEFENSE
                                .replace("{CAPITAL_BEING_SIEGED}", defenders.getName()));
                    }
                }

            } catch (Exception e) {
                TownWars.plugin.getLogger().warning("Error while sending messages... not fatal but please report the bug");
                e.printStackTrace();
            }

        } else {
            war.messageGlobal(LocaleReader.GLOBAL_NEW_TOWN_WAR);

            war.messageAttackers(LocaleReader.COMMAND_NOW_AT_WAR_WITH_TOWN);
            war.messageDefenders(LocaleReader.DEFENDERS_TOWN_WAR_WAS_DECLARED);
        }
    }

    public static boolean checkValidJoinWarInputs(Town playerTown, Town targetTown, Nation targetNation, Player player) {
        if (playerTown == null) {
            LocaleReader.send(player, LocaleReader.COMMAND_NOT_PART_OF_TOWN);
            return false;
        }

        if (!playerTown.hasNation()) {
            LocaleReader.send(player, LocaleReader.COMMAND_NOT_PART_OF_NATION);
            return false;
        }


        if (targetTown == null && targetNation == null) {
            LocaleReader.send(player, LocaleReader.COMMAND_TOWN_NOT_FOUND);
            return false;
        }

        if (targetNation == null) {
            try {
                targetTown.getNation();
            } catch (NotRegisteredException e) {
                LocaleReader.send(player, LocaleReader.COMMAND_NOT_NATION_WAR);
                return false;
            }
        }

        return true;
    }

    // TODO: Make every nation accept peace?
    public static void endDiplomatically(War war, Player player) {
        Town playerTown;

        // This shouldn't even be possible!
        try {
            playerTown = towny.getDataSource().getResident(player.getName()).getTown();
        } catch (NotRegisteredException e) {
            LocaleReader.send(player, LocaleReader.COMMAND_NOT_PART_OF_TOWN);
            return;
        }

        if (war.attackers == playerTown) {
            war.attackerWantsPeace = true;
        } else {
            war.defenderWantsPeace = true;
        }

        if (!war.attackerWantsPeace || !war.defenderWantsPeace) {
            if (war.tick - war.lastPeaceRequestEpoch < ConfigHandler.minSecondsBetweenPeaceRequests * 20L) {
                war.messagePlayer(player, LocaleReader.COMMAND_NEXT_PEACE_REQUEST_TIME.replace("{TIME_LEFT}", formatSeconds((war.tick - war.lastPeaceRequestEpoch) / 20)));
                return;
            }

            LocaleReader.send(player, LocaleReader.COMMAND_PEACE_REQUEST_SENT);
            sendMessageToTown(war.attackers == playerTown ? war.defenders : war.attackers, LocaleReader.TOWN_WANTS_PEACE
                    .replace("{TOWN}", playerTown.getName()));
            war.lastPeaceRequestEpoch = war.tick;

            return;
        }

        if (war.isNationWar) {
            war.messageAttackers(LocaleReader.ATTACKERS_NOW_AT_PEACE_NATION_WAR);
            war.messageDefenders(LocaleReader.DEFENDERS_NOW_AT_PEACE_NATION_WAR);
        } else {
            war.messageAttackers(LocaleReader.ATTACKERS_NOW_AT_PEACE_TOWN_WAR);
            war.messageDefenders(LocaleReader.DEFENDERS_NOW_AT_PEACE_TOWN_WAR);
        }

        war.messageGlobal(LocaleReader.GLOBAL_TOWN_WAR_ENDS_PEACEFULLY);

        // Both the attacker and defender want peace
        deleteWar(war);
    }

    public static void surrender(War targetWar, Player player) {
        Town playerTown;
        // This shouldn't even be possible!
        try {
            playerTown = towny.getDataSource().getResident(player.getName()).getTown();
        } catch (NotRegisteredException e) {
            LocaleReader.send(player, LocaleReader.COMMAND_NOT_PART_OF_TOWN);
            return;
        }

        if (targetWar.attackers == playerTown) {
            defendersWinWar(targetWar);
        } else {
            attackersWinWar(targetWar);
        }
    }

    public static void defendersWinWar(War war) {
        TownWars.currentWars.remove(war);

        if (war.isNationWar) {
            try {
                war.defenders.getAccount().deposit(ConfigHandler.costStartNationWar, LocaleReader.NATION_WAR_DEPOSIT);
                war.messageAttackers(LocaleReader.ATTACKERS_YOU_LOST_NATION_WAR);
                war.messageDefenders(LocaleReader.DEFENDERS_YOU_WON_NATION_WAR
                        .replace("{COST}", String.valueOf(ConfigHandler.costStartNationWar)));

                for (Nation nation : war.nationsAttacking) {
                    for (Town town : nation.getTowns()) {
                        if (town == war.attackers) continue;

                        sendMessageToTown(town, LocaleReader.ATTACKING_NATIONS_YOU_LOST_NATION_WAR);
                    }
                }

                for (Nation nation : war.nationsDefending) {
                    for (Town town : nation.getTowns()) {
                        if (town == war.defenders) continue;

                        sendMessageToTown(town, LocaleReader.DEFENDING_NATIONS_YOU_WON_NATION_WAR
                                .replace("{COST}", String.valueOf(war.calculateTotalSpentByAttackers())));
                    }
                }
            } catch (EconomyException e) {
                e.printStackTrace();
            }
        } else {
            // This is a town war, we only need to message the two towns
            try {
                war.defenders.getAccount().deposit(ConfigHandler.costStartTownWar, LocaleReader.TOWN_WAR_DEPOSIT);

                war.messageGlobal(LocaleReader.GLOBAL_DEFENDERS_WIN_TOWN_WAR);

                war.messageAttackers(LocaleReader.ATTACKERS_YOU_LOST_TOWN_WAR);
                war.messageDefenders(LocaleReader.DEFENDERS_YOU_WON_TOWN_WAR
                        .replace("{COST}", String.valueOf(ConfigHandler.costStartTownWar)));

                WarCooldownManager.setAttackerLost(war.attackers.getUUID(), war.defenders.getUUID());
            } catch (EconomyException e) {
                e.printStackTrace();
            }
        }


        deleteWar(war);
    }

    public static void attackersWinWar(War war) {
        TownWars.currentWars.remove(war);

        // Set the defending town as conquered
        if (war.attackers.hasNation() && !war.isNationWar && war.defenders.hasNation() && ConfigHandler.conquerIfInNation) {
            try {
                war.defenders.setNation(null);
                war.defenders.setNation(war.getAttackingNation());
                war.defenders.setConquered(true);
                war.defenders.setConqueredDays(ConfigHandler.daysConqueredIfInNation);
            } catch (AlreadyRegisteredException e) {
                e.printStackTrace();
            }
        }

        if (war.attackers.hasNation() && !war.defenders.hasNation() && ConfigHandler.conquerIfWithoutNation) {
            try {
                war.defenders.setNation(war.getAttackingNation());
                war.defenders.setConquered(true);
                war.defenders.setConqueredDays(ConfigHandler.daysConqueredIfWithoutNation);
            } catch (AlreadyRegisteredException e) {
                e.printStackTrace();
            }
        }

        try {
            if (war.isNationWar) {
                int defenderBlocks = 0;

                for (Town town : war.getDefendingNation().getTowns()) {
                    int takenBlocks = (int) (town.getBonusBlocks() * ConfigHandler.nationWarBlocksTransferPercent);
                    town.setBonusBlocks(town.getBonusBlocks() - takenBlocks);
                    defenderBlocks += takenBlocks;
                }

                int attackerTownCount = 0;
                for (Nation nation : war.nationsAttacking) {
                    attackerTownCount += nation.getTowns().size();
                }

                // Make it so bonus blocks aren't lost by rounding... not like it matters much
                int baseBlocksPerAttacker = defenderBlocks / attackerTownCount;
                int remainingBlocks = defenderBlocks - (baseBlocksPerAttacker * attackerTownCount);
                war.attackers.setBonusBlocks(war.attackers.getBonusBlocks() + remainingBlocks);

                war.messageAttackers(LocaleReader.ATTACKERS_YOU_WON_NATION_WAR
                        .replace("{CLAIMS}", String.valueOf(remainingBlocks + baseBlocksPerAttacker))
                        .replace("{COST}", String.valueOf(ConfigHandler.costStartTownWar)));

                // Attacking nation gets the blocks that were lost by rounding.
                war.messageAttackingAllies(LocaleReader.ATTACKERS_YOU_WON_NATION_WAR
                        .replace("{CLAIMS}", String.valueOf(baseBlocksPerAttacker))
                        .replace("{COST}", String.valueOf(ConfigHandler.costJoinNationWarAttackers)));

                war.messageDefenders(LocaleReader.DEFENDERS_YOU_LOST_NATION_WAR
                        .replace("{CLAIMS}", String.valueOf(remainingBlocks + baseBlocksPerAttacker)));

                war.messageDefendingAllies(LocaleReader.DEFENDING_NATIONS_YOU_LOST_NATION_WAR);

                war.attackers.getAccount().deposit(ConfigHandler.costStartNationWar, LocaleReader.NATION_WAR_DEPOSIT);

            } else {
                int defenderBlocks = war.defenders.getTotalBlocks();
                int lost = (int) Math.ceil(defenderBlocks * ConfigHandler.townWarBlocksTransferPercent);

                war.defenders.setBonusBlocks(war.defenders.getBonusBlocks() - lost);
                war.attackers.setBonusBlocks(war.attackers.getBonusBlocks() + lost);

                war.messageGlobal(LocaleReader.GLOBAL_ATTACKERS_WIN_TOWN_WAR);

                war.messageAttackers(LocaleReader.ATTACKERS_YOU_WON_TOWN_WAR
                        .replace("{CLAIMS}", lost + "")
                        .replace("{COST}", String.valueOf(ConfigHandler.costStartTownWar)));
                war.messageDefenders(LocaleReader.DEFENDERS_YOU_LOST_TOWN_WAR
                        .replace("{CLAIMS}", lost + ""));

                war.attackers.getAccount().deposit(ConfigHandler.costStartTownWar, LocaleReader.TOWN_WAR_DEPOSIT);

                WarCooldownManager.setDefenderLost(war.defenders.getUUID());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        deleteWar(war);
    }

    public static void deleteWar(War war) {
        TownWars.currentWars.remove(war);
        TownWars.townsUnderSiege.remove(war.defenders);

        // This is not the fastest way to iterate, remove, and modify a hashmap, but it works.
        HashMap<Nation, HashSet<War>> clonedMap = (HashMap<Nation, HashSet<War>>) TownWars.nationCurrentNationWars.clone();

        for (Map.Entry<Nation, HashSet<War>> nation : clonedMap.entrySet()) {
            TownWars.nationCurrentNationWars.get(nation.getKey()).remove(war);

            // stop memory leaks
            if (nation.getValue().size() > 0) continue;
            TownWars.nationCurrentNationWars.remove(nation.getKey());
        }

        // and finally, delete the saved war file
        war.delete();

        war.warBossbar.removeEveryoneBossBars();

        war.attackers.setAdminEnabledPVP(false);
        war.defenders.setAdminEnabledPVP(false);

        war.removeGlow();

        // This is the most likely to error, so try to restore the blocks
        // TODO: Re add this
        for (HashMap<Location, BlockData> blockHashMap : war.blocksToRestore.values()) {
            for (Map.Entry<Location, BlockData> key : blockHashMap.entrySet()) {
                PaperLib.getChunkAtAsync(key.getKey()).thenAccept(chunk -> chunk.getBlock(key.getKey().getBlockX() & 0xF, key.getKey().getBlockY() & 0xFF, key.getKey().getBlockZ() & 0xF).setBlockData(key.getValue(), false));
            }
        }
    }

    public static void sendMessageToTown(Town town, String message) {
        for (Resident resident : town.getResidents()) {
            if (resident.getPlayer() == null) continue;
            LocaleReader.send(resident.getPlayer(), message);
        }
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
}
