package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.EconomyException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

// This class plays with War objects.
// It is allowed to play with the main hashsets and caches
public class WarManager {
    private static TownyAPI towny = TownyAPI.getInstance();

    public static void checkThenCreateWar(Town attackers, Town defenders, Player player) {
        boolean isNationWar = false;

        if (attackers == defenders) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_YOURSELF);
            return;
        }

        if (attackers.hasNation() && defenders.hasNation()) {
            try {
                if (!attackers.getNation().hasEnemy(defenders.getNation())) {
                    LocaleReader.send(player, LocaleReader.COMMAND_MUST_BE_ENEMIES);
                    return;
                }

                if (attackers.getNation().isNeutral()) {
                    LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_AS_NEUTRAL);
                    return;
                }

                if (defenders.getNation().isNeutral()) {
                    LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_NEUTRAL);
                    return;
                }
            } catch (NotRegisteredException e) {
                e.printStackTrace();
                LocaleReader.send(player, LocaleReader.COMMAND_AN_ERROR_OCCURRED);
                return;
            }

            if (!attackers.isCapital() && defenders.isCapital()) {
                LocaleReader.send(player, LocaleReader.COMMAND_ONLY_CAPITAL_CAN_ATTACK_CAPITAL);
                return;
            }

            if (attackers.isCapital() && defenders.isCapital()) {
                isNationWar = true;
            }
        }

        if (attackers.isAlliedWith(defenders)) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_ALLIES);
            return;
        }

        double online = 0;

        for (Resident resident : defenders.getResidents()) {
            if (resident.getPlayer() != null) ++online;
        }

        // TODO: Stop nations from creating a capital and having no players online in the capital
        if (!(online > ConfigHandler.minPlayersOnlineBypassPercent) && online /
                ((double) defenders.getResidents().size()) < ConfigHandler.minPercentOnline ||
                online < ConfigHandler.minPlayersOnline) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_NOT_ENOUGH_DEFENDERS_ONLINE
                    .replace("{PERCENT}", String.valueOf(100 * ConfigHandler.minPercentOnline))
                    .replace("{NUMBER_WITH_PERCENT}", String.valueOf((int) ConfigHandler.minPlayersOnline))
                    .replace("{NUMBER}", String.valueOf((int) ConfigHandler.minPlayersOnlineBypassPercent)));

            return;
        }

        for (War war : TownWars.currentWars) {
            if (war.attackers == attackers && war.defenders == defenders || war.defenders == attackers && war.attackers == defenders) {
                LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_ALREADY_AT_WAR);
                return;
            }
        }

        try {
            if (isNationWar) {
                if (!attackers.getAccount().canPayFromHoldings(ConfigHandler.costStartNationWar)) {
                    LocaleReader.send(player, LocaleReader.COMMAND_NOT_ENOUGH_BALANCE_NATION_WAR);
                    return;
                }

                attackers.getAccount().withdraw(ConfigHandler.costStartNationWar, LocaleReader.NATION_WAR_WITHDRAW);
            } else {
                if (!attackers.getAccount().canPayFromHoldings(ConfigHandler.costStartTownWar)) {
                    LocaleReader.send(player, LocaleReader.COMMAND_NOT_ENOUGH_BALANCE_TOWN_WAR);
                    return;
                }

                attackers.getAccount().withdraw(ConfigHandler.costStartTownWar, LocaleReader.TOWN_WAR_WITHDRAW);
            }

        } catch (EconomyException e) {
            e.printStackTrace();
            LocaleReader.send(player, LocaleReader.WITHDRAW_ERROR);
            return;
        }

        War war = new War(attackers, defenders, isNationWar);
        setupWar(war, true);
    }

    // sendMessages is set to false during a reload.
    public static void setupWar(War war, boolean sendMessages) {
        Town attackers = war.attackers;
        Town defenders = war.defenders;
        boolean isNationWar = war.isNationWar;

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
                        .replace("{NATION_ATTACKERS}", attackers.getNation().getName())
                        .replace("{NATION_DEFENDERS}", defenders.getNation().getName())
                        .replace("{CAPITAL_BEING_SIEGED}", defenders.getName()));
                war.messageDefenders(LocaleReader.DEFENDERS_NATION_WAR_WAS_DECLARED
                        .replace("{NATION_ATTACKERS}", attackers.getNation().getName())
                        .replace("{NATION_DEFENDERS}", defenders.getNation().getName())
                        .replace("{CAPITAL_BEING_SIEGED}", defenders.getName()));

                // This is about messaging allies, not the nation or people in the war.
                for (Nation nation : attackers.getNation().getAllies()) {
                    for (Resident resident : nation.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        Bukkit.broadcastMessage(nation.getName());

                        LocaleReader.send(resident.getPlayer(), LocaleReader.ASSIST_NATION_ATTACK
                                .replace("{NATION_ATTACKERS}", attackers.getNation().getName())
                                .replace("{NATION_DEFENDERS}", defenders.getNation().getName())
                                .replace("{CAPITAL_BEING_SIEGED}", defenders.getName()));
                    }
                }

                for (Nation nation : defenders.getNation().getAllies()) {
                    for (Resident resident : nation.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        Bukkit.broadcastMessage(nation.getName());

                        LocaleReader.send(resident.getPlayer(), LocaleReader.ASSIST_NATION_DEFENSE
                                .replace("{NATION_ATTACKERS}", attackers.getNation().getName())
                                .replace("{NATION_DEFENDERS}", defenders.getNation().getName())
                                .replace("{CAPITAL_BEING_SIEGED}", defenders.getName()));
                    }
                }

            } catch (Exception e) {
                TownWars.plugin.getLogger().warning("Error while sending messages... not fatal but please report the bug");
                e.printStackTrace();
            }

        } else {
            war.messageAttackers(LocaleReader.COMMAND_NOW_AT_WAR_WITH_TOWN
                    .replace("{TOWN_ATTACKERS}", attackers.getName())
                    .replace("{TOWN_DEFENDERS}", defenders.getName()));
            war.messageDefenders(LocaleReader.DEFENDERS_TOWN_WAR_WAS_DECLARED
                    .replace("{TOWN_ATTACKERS}", attackers.getName())
                    .replace("{TOWN_DEFENDERS}", defenders.getName()));
        }
    }

    public static void endDiplomatically(War targetWar, Player player) {
        Town playerTown;

        // This shouldn't even be possible!
        try {
            playerTown = towny.getDataSource().getResident(player.getName()).getTown();
        } catch (NotRegisteredException e) {
            LocaleReader.send(player, LocaleReader.COMMAND_NOT_PART_OF_TOWN);
            return;
        }

        if (targetWar.attackers == playerTown) {
            targetWar.attackerWantsPeace = true;
        } else {
            targetWar.defenderWantsPeace = true;
        }

        if (!targetWar.attackerWantsPeace || !targetWar.defenderWantsPeace) {
            if (Instant.now().getEpochSecond() - targetWar.lastPeaceRequestEpoch < ConfigHandler.minSecondsBetweenPeaceRequests) {
                LocaleReader.send(player, LocaleReader.COMMAND_NEXT_PEACE_REQUEST_TIME);
                return;
            }

            LocaleReader.send(player, LocaleReader.COMMAND_PEACE_REQUEST_SENT);
            sendMessageToTown(targetWar.attackers == playerTown ? targetWar.defenders : targetWar.attackers, LocaleReader.TOWN_WANTS_PEACE
                    .replace("{TOWN}", playerTown.getName()));
            targetWar.lastPeaceRequestEpoch = Instant.now().getEpochSecond();

            return;
        }

        // Both the attacker and defender want peace
        returnToPeace(targetWar);
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
            attackersLoseWar(targetWar);
        } else {
            defendersLoseWar(targetWar);
        }
    }

    public static void attackersLoseWar(War war) {
        TownWars.currentWars.remove(war);

        if (war.isNationWar) {
            try {
                war.defenders.getAccount().deposit(ConfigHandler.costStartNationWar, LocaleReader.NATION_WAR_DEPOSIT);
                sendMessageToTown(war.attackers, LocaleReader.ATTACKERS_YOU_LOST_NATION_WAR.replace("{NATION_DEFENDERS}", war.defenders.getNation().getName()));
                sendMessageToTown(war.defenders, LocaleReader.DEFENDERS_YOU_WON_NATION_WAR
                        .replace("{ATTACKERS}", war.attackers.getNation().getName())
                        .replace("{COST}", ConfigHandler.costStartNationWar + ""));

                for (Nation nation : war.nationsAttacking) {
                    for (Town town : nation.getTowns()) {
                        if (town == war.attackers) continue;

                        sendMessageToTown(town, LocaleReader.ATTACKING_NATIONS_YOU_LOST_NATION_WAR
                                .replace("{NATION_ATTACKERS}", war.attackers.getNation().getName())
                                .replace("{NATION_DEFENDERS}", war.defenders.getNation().getName()));
                    }
                }

                for (Nation nation : war.nationsDefending) {
                    for (Town town : nation.getTowns()) {
                        if (town == war.defenders) continue;

                        sendMessageToTown(town, LocaleReader.DEFENDING_NATIONS_YOU_WON_NATION_WAR
                                .replace("{NATION_ATTACKERS}", war.attackers.getNation().getName())
                                .replace("{NATION_DEFENDERS}", war.defenders.getNation().getName())
                                .replace("{COST}", war.calculateTotalSpentByAttackers() + ""));
                    }
                }
            } catch (EconomyException | NotRegisteredException e) {
                e.printStackTrace();
            }
        } else {
            // This is a town war, we only need to message the two towns
            try {
                war.defenders.getAccount().deposit(ConfigHandler.costStartTownWar, LocaleReader.TOWN_WAR_DEPOSIT);
                sendMessageToTown(war.attackers, LocaleReader.ATTACKERS_YOU_LOST_TOWN_WAR.replace("{DEFENDERS}", war.defenders.getName()));
                sendMessageToTown(war.defenders, LocaleReader.DEFENDERS_YOU_WON_TOWN_WAR
                        .replace("{ATTACKERS}", war.attackers.getName())
                        .replace("{COST}", String.valueOf(ConfigHandler.costStartTownWar)));
            } catch (EconomyException e) {
                e.printStackTrace();
            }
        }

        returnToPeace(war);
    }

    public static void defendersLoseWar(War war) {
        TownWars.currentWars.remove(war);

        // Set the defending town as conquered
        if (war.attackers.hasNation() && !war.isNationWar && war.defenders.hasNation() && ConfigHandler.conquerIfInNation) {
            try {
                war.defenders.setNation(war.attackers.getNation());
                war.defenders.setConquered(true);
                war.defenders.setConqueredDays(ConfigHandler.daysConqueredIfInNation);
            } catch (AlreadyRegisteredException | NotRegisteredException e) {
                e.printStackTrace();
            }
        }

        if (war.attackers.hasNation() && !war.defenders.hasNation() && ConfigHandler.conquerIfWithoutNation) {
            try {
                war.defenders.setNation(war.attackers.getNation());
                war.defenders.setConquered(true);
                war.defenders.setConqueredDays(ConfigHandler.daysConqueredIfWithoutNation);
            } catch (AlreadyRegisteredException | NotRegisteredException e) {
                e.printStackTrace();
            }
        }

        /*if (lost < 10) {
            lost = 10;
        }*/

        try {
            if (war.isNationWar) {
                int defenderBlocks = 0;

                for (Town town : war.defenders.getNation().getTowns()) {
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

                sendMessageToTown(war.attackers, LocaleReader.ATTACKERS_YOU_WON_NATION_WAR
                        .replace("{CLAIMS}", remainingBlocks + baseBlocksPerAttacker + "")
                        .replace("{NATION_DEFENDERS}", war.attackers.getNation().getName()));

                // Attacking nation gets the blocks that were lost by rounding.
                for (Nation nation : war.nationsAttacking) {
                    for (Town town : nation.getTowns()) {
                        town.setBonusBlocks(town.getBonusBlocks() + baseBlocksPerAttacker);
                        if (town == war.attackers) continue;

                        sendMessageToTown(town, LocaleReader.ATTACKERS_YOU_WON_NATION_WAR
                                .replace("{CLAIMS}", baseBlocksPerAttacker + "")
                                .replace("{NATION_DEFENDERS}", war.attackers.getNation().getName()));
                    }
                }

                sendMessageToTown(war.defenders, LocaleReader.DEFENDERS_YOU_LOST_NATION_WAR
                        .replace("{CLAIMS}", remainingBlocks + baseBlocksPerAttacker + "")
                        .replace("{NATION_ATTACKERS}", war.attackers.getNation().getName()));

                // Attacking nation gets the blocks that were lost by rounding.
                for (Nation nation : war.nationsDefending) {
                    for (Town town : nation.getTowns()) {
                        if (town == war.defenders) continue;

                        sendMessageToTown(town, LocaleReader.DEFENDING_NATIONS_YOU_LOST_NATION_WAR
                                .replace("{CLAIMS}", baseBlocksPerAttacker + "")
                                .replace("{NATION_DEFENDERS}", war.defenders.getNation().getName()
                                        .replace("{NATION_ATTACKERS}", war.attackers.getNation().getName())));
                    }
                }

            } else {
                int defenderBlocks = war.defenders.getTotalBlocks();
                int lost = (int) Math.ceil(defenderBlocks * ConfigHandler.townWarBlocksTransferPercent);

                war.defenders.setBonusBlocks(war.defenders.getBonusBlocks() - lost);
                war.attackers.setBonusBlocks(war.attackers.getBonusBlocks() + lost);

                sendMessageToTown(war.attackers, LocaleReader.ATTACKERS_YOU_WON_TOWN_WAR
                        .replace("{CLAIMS}", lost + "")
                        .replace("{DEFENDERS}", war.attackers.getName()));
                sendMessageToTown(war.defenders, LocaleReader.DEFENDERS_YOU_LOST_TOWN_WAR
                        .replace("{CLAIMS}", lost + "")
                        .replace("{ATTACKERS}", war.attackers.getName()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        returnToPeace(war);
    }

    public static void returnToPeace(War war) {
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

        // events class should take care of this
        //war.attackers.setAdminEnabledPVP(isTownInWar(war.attackers));
        //war.defenders.setAdminEnabledPVP(isTownInWar(war.defenders));

        // and finally, delete the saved war file
        war.delete();

        // This is the most likely to error, so try to restore the blocks
        for (Map.Entry<Location, BlockData> blockEntry : war.restoreBlocksAfterWar.entrySet()) {
            Location blockLocation = blockEntry.getKey();
            blockLocation.getWorld().getBlockAt(blockLocation).setBlockData(blockEntry.getValue());
        }
    }

    public static int countWarsForTown(Town town) {
        int count = 0;
        for (War war : TownWars.currentWars) {
            if (war.attackers == town || war.defenders == town) {
                count++;
            }
        }

        return count;
    }

    public static boolean isTownInWar(Town town) {
        return countWarsForTown(town) > 0;
    }

    public static void sendMessageToTown(Town town, String message) {
        for (Resident resident : town.getResidents()) {
            if (resident.getPlayer() == null) continue;
            LocaleReader.send(resident.getPlayer(), message);
        }
    }
}
