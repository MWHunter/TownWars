package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.EconomyException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.abyssmc.townwars.WarCommand.towny;

// This class plays with War objects.
public class WarManager {
    static TownWars plugin;

    public WarManager(TownWars plugin) {
        WarManager.plugin = plugin;
    }

    public static boolean createWar(Town attackers, Town defenders, Player player) {
        boolean isNationWar = false;

        if (attackers.hasNation() && defenders.hasNation()) {
            try {
                if (!attackers.getNation().hasEnemy(defenders.getNation())) {
                    LocaleReader.send(player, LocaleReader.COMMAND_MUST_BE_ENEMIES);
                    return false;
                }

                if (attackers.getNation().isNeutral()) {
                    LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_AS_NEUTRAL);
                    return false;
                }

                if (defenders.getNation().isNeutral()) {
                    LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_NEUTRAL);
                    return false;
                }
            } catch (NotRegisteredException e) {
                e.printStackTrace();
                LocaleReader.send(player, LocaleReader.COMMAND_AN_ERROR_OCCURRED);
                return false;
            }

            if (!attackers.isCapital() && defenders.isCapital()) {
                LocaleReader.send(player, LocaleReader.COMMAND_ONLY_CAPITAL_CAN_ATTACK_CAPITAL);
                return false;
            }

            if (attackers.isCapital() && defenders.isCapital()) {
                isNationWar = true;
            }
        }


        if (attackers == defenders) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_YOURSELF);
            return false;
        }

        if (attackers.isAlliedWith(defenders)) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_ALLIES);
            return false;
        }

        double online = 0;

        for (Resident resident : attackers.getResidents()) {
            if (resident.getPlayer() != null) ++online;
        }

        if (online < ConfigHandler.minPlayersOnline) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_NOT_ENOUGH_DEFENDERS_ONLINE);
            return false;
        }

        if (online / ((double) defenders.getResidents().size()) < ConfigHandler.minPercentOnline || online < ConfigHandler.minPlayersOnline) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_NOT_ENOUGH_DEFENDERS_ONLINE);
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
                    LocaleReader.send(player, LocaleReader.COMMAND_NOT_ENOUGH_BALANCE_NATION_WAR);
                    return false;
                }

                attackers.getAccount().withdraw(plugin.getConfigHandler().costStartNationWar, LocaleReader.NATION_WAR_WITHDRAW);
            } else {
                if (!attackers.getAccount().canPayFromHoldings(plugin.getConfigHandler().costStartTownWar)) {
                    LocaleReader.send(player, LocaleReader.COMMAND_NOT_ENOUGH_BALANCE_TOWN_WAR);
                    return false;
                }

                attackers.getAccount().withdraw(plugin.getConfigHandler().costStartTownWar, LocaleReader.TOWN_WAR_WITHDRAW);
            }

        } catch (EconomyException e) {
            e.printStackTrace();
            LocaleReader.send(player, LocaleReader.WITHDRAW_ERROR);
            return false;
        }

        War war = new War(attackers, defenders, isNationWar);
        setupWar(war, player);

        return true;
    }

    // nothing bad will happen if player is null
    public static void setupWar(War war, Player player) {
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

                // This is about messaging allies, not the nation or people in the war.
                for (Nation nation : attackers.getNation().getAllies()) {
                    war.messageAttackers(LocaleReader.ASSIST_NATION_ATTACK);
                    for (Resident resident : nation.getResidents()) {
                        if (resident.getPlayer() == null) continue;

                        LocaleReader.send(resident.getPlayer(), LocaleReader.ASSIST_NATION_ATTACK);
                    }
                }

                for (Nation nation : defenders.getNation().getAllies()) {
                    for (Resident resident : nation.getResidents()) {
                        if (resident.getPlayer() == null) continue;

                        LocaleReader.send(resident.getPlayer(), LocaleReader.ASSIST_NATION_DEFENSE);
                    }
                }
            } catch (NotRegisteredException ignored) {
            }
        }

        attackers.setAdminEnabledPVP(true);
        defenders.setAdminEnabledPVP(true);

        if (war.isNationWar) {
            LocaleReader.send(player, LocaleReader.COMMAND_NOW_AT_WAR_WITH_NATION);
        } else {
            LocaleReader.send(player, LocaleReader.COMMAND_NOW_AT_WAR_WITH_TOWN);
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

        // TODO: Make this configurable for time between request notifications
        // TODO: Different logic for nation wars
        if (!targetWar.attackerWantsPeace || !targetWar.defenderWantsPeace) {
            if (Instant.now().getEpochSecond() - targetWar.lastPeaceRequestEpoch < plugin.getConfigHandler().minSecondsBetweenPeaceRequests) {
                LocaleReader.send(player, LocaleReader.COMMAND_NEXT_PEACE_REQUEST_TIME);
                return;
            }

            LocaleReader.send(player, LocaleReader.COMMAND_PEACE_REQUEST_SENT);
            sendMessageToTown(targetWar.attackers == playerTown ? targetWar.defenders : targetWar.attackers, LocaleReader.TOWN_WANTS_PEACE);
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

        // TODO: Unify this method
        if (war.isNationWar) {
            try {
                war.defenders.getAccount().deposit(plugin.getConfigHandler().costStartNationWar, LocaleReader.NATION_WAR_DEPOSIT);
                sendMessageToTown(war.attackers, LocaleReader.ATTACKERS_YOU_LOST_NATION_WAR);
                sendMessageToTown(war.defenders, LocaleReader.DEFENDERS_YOU_WON_NATION_WAR);
            } catch (EconomyException e) {
                e.printStackTrace();
            }
        } else {
            try {
                war.defenders.getAccount().deposit(plugin.getConfigHandler().costStartTownWar, LocaleReader.TOWN_WAR_DEPOSIT);
                sendMessageToTown(war.attackers, LocaleReader.ATTACKERS_YOU_LOST_TOWN_WAR);
                sendMessageToTown(war.defenders, LocaleReader.DEFENDERS_YOU_LOST_TOWN_WAR);
            } catch (EconomyException e) {
                e.printStackTrace();
            }
        }

        returnToPeace(war);
    }

    public static void defendersLoseWar(War war) {
        TownWars.currentWars.remove(war);

        // Set the defending town as conquered
        if (war.attackers.hasNation() && !war.isNationWar && war.defenders.hasNation() && plugin.getConfigHandler().conquerIfInNation) {
            try {
                war.defenders.setNation(war.attackers.getNation());
                war.defenders.setConquered(true);
                war.defenders.setConqueredDays(plugin.getConfigHandler().daysConqueredIfInNation);
            } catch (AlreadyRegisteredException | NotRegisteredException e) {
                e.printStackTrace();
            }
        }

        if (war.attackers.hasNation() && !war.defenders.hasNation() && plugin.getConfigHandler().conquerIfWithoutNation) {
            try {
                war.defenders.setNation(war.attackers.getNation());
                war.defenders.setConquered(true);
                war.defenders.setConqueredDays(plugin.getConfigHandler().daysConqueredIfWithoutNation);
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
                    int takenBlocks = (int) (town.getBonusBlocks() * plugin.getConfigHandler().nationWarBlocksTransferPercent);
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

                sendMessageToTown(war.attackers, LocaleReader.ATTACKERS_YOU_WON_NATION_WAR);

                // Attacking nation gets the blocks that were lost by rounding.
                for (Nation nation : war.nationsAttacking) {
                    for (Town town : nation.getTowns()) {
                        town.setBonusBlocks(town.getBonusBlocks() + baseBlocksPerAttacker);
                        if (town == war.attackers) continue;

                        sendMessageToTown(town, LocaleReader.ATTACKERS_YOU_WON_NATION_WAR);
                    }
                }
            } else {
                int defenderBlocks = war.defenders.getTotalBlocks();
                int lost = (int) Math.ceil(defenderBlocks * plugin.getConfigHandler().townWarBlocksTransferPercent);

                war.defenders.setBonusBlocks(war.defenders.getBonusBlocks() - lost);
                war.attackers.setBonusBlocks(war.attackers.getBonusBlocks() + lost);

                sendMessageToTown(war.attackers, LocaleReader.ATTACKERS_YOU_WON_TOWN_WAR);
                sendMessageToTown(war.defenders, LocaleReader.DEFENDERS_YOU_LOST_TOWN_WAR);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        returnToPeace(war);
    }

    public static void returnToPeace(War war) {
        TownWars.currentWars.remove(war);
        TownWars.townsUnderSiege.remove(war.defenders);

        HashMap<Nation, HashSet<War>> clonedMap = (HashMap<Nation, HashSet<War>>) TownWars.nationCurrentNationWars.clone();

        for (Map.Entry<Nation, HashSet<War>> nation : clonedMap.entrySet()) {
            TownWars.nationCurrentNationWars.get(nation.getKey()).remove(war);

            // stop memory leaks
            if (nation.getValue().size() > 0) continue;
            TownWars.nationCurrentNationWars.remove(nation.getKey());
        }

        war.attackers.setAdminEnabledPVP(isTownInWar(war.attackers));
        war.defenders.setAdminEnabledPVP(isTownInWar(war.defenders));
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

    public static  boolean isTownInWar(Town town) {
        return countWarsForTown(town) > 0;
    }

    public static void sendMessageToTown(Town town, String message) {
        for (Resident resident : town.getResidents()) {
            Player player = Bukkit.getPlayer(resident.getName());

            if (player != null) {
                LocaleReader.send(player, message);
            }
        }
    }
}
