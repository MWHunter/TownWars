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

import java.util.*;

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

                boolean isNationWar = targetTown.hasNation() && playerTown.hasNation();

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
                setupWar(war);
                sendWarStartedMessage(war);

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

                    if (war.currentState == War.WarState.WAR) {
                        LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_JOIN_WAR_IN_PROGRESS);
                        return;
                    }

                    try {
                        if (war.nationsAttacking.contains(playerTown.getNation()) || war.nationsDefending.contains(playerTown.getNation())) {
                            war.messagePlayer(player, LocaleReader.COMMAND_ALREADY_JOINED_WAR);
                            return;
                        }

                        try {
                            Nation playerNation = playerTown.getNation();

                            Nation attackingNation = war.getAttackingNation();

                            switch (WarUtility.joiningAsEnum(playerNation, attackingNation, war.getDefendingNation())) {
                                case BOTH:
                                    war.messagePlayer(player, LocaleReader.COMMAND_ALLY_OF_BOTH_ATTACKERS_AND_DEFENDERS);
                                    return;

                                case ATTACKER:
                                    try {
                                        if (!playerTown.getAccount().canPayFromHoldings(ConfigHandler.costJoinNationWarAttackers)) {
                                            LocaleReader.send(player, LocaleReader.COMMAND_NOT_ENOUGH_BALANCE_NATION_WAR.replace("{COST}", String.valueOf(ConfigHandler.costStartNationWar)));
                                            return;
                                        }

                                        playerTown.getAccount().withdraw(ConfigHandler.costJoinNationWarAttackers, LocaleReader.NATION_WAR_JOIN_ATTACKERS);

                                    } catch (EconomyException e) {
                                        e.printStackTrace();
                                        LocaleReader.send(player, LocaleReader.WITHDRAW_ERROR);
                                        return;
                                    }

                                    if (!TownWars.warsListForEachNation.containsKey(playerNation)) {
                                        TownWars.warsListForEachNation.put(playerNation, new HashSet<>());
                                    }

                                    TownWars.warsListForEachNation.get(playerNation).add(war);
                                    war.nationsAttacking.add(playerNation);

                                    war.messageAll(LocaleReader.WAR_JOINED_AS_ATTACKER
                                            .replace("{NATION}", playerNation.getName()));

                                    war.makeAttackersGlow();
                                    war.warBossbar.sendEveryoneBossBars();
                                    return;

                                case DEFENDER:
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

                                    if (!TownWars.warsListForEachNation.containsKey(playerNation)) {
                                        TownWars.warsListForEachNation.put(playerNation, new HashSet<>());
                                    }

                                    TownWars.warsListForEachNation.get(playerNation).add(war);
                                    war.nationsDefending.add(playerNation);

                                    war.messageAll(LocaleReader.WAR_JOINED_AS_DEFENDER
                                            .replace("{NATION}", playerNation.getName()));

                                    return;

                                case NONE:
                                    war.messagePlayer(player, LocaleReader.COMMAND_NOT_ALLY_OF_DEFENDER_OR_ATTACKER);
                                    return;
                            }

                        } catch (NotRegisteredException e) {
                            e.printStackTrace();
                        }
                    } catch (NotRegisteredException exception) {
                        exception.printStackTrace();
                    }
                }

                LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_FIND_WAR_TO_JOIN);
            }
        }
    }

    // TODO: Probably could simplify this with lambdas
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
        if (defenders == null) {
            LocaleReader.send(player, LocaleReader.COMMAND_TOWN_NOT_FOUND);
            return false;
        }

        boolean isNationWar = attackers.hasNation() && defenders.hasNation();
        Nation attackingNation = WarUtility.getNation(attackers);
        Nation defendingNation = WarUtility.getNation(defenders);

        if (attackers == defenders || (isNationWar && attackingNation == defendingNation)) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_YOURSELF);
            return false;
        }

        /*if (!attackers.hasNation()) {
            LocaleReader.send(player, LocaleReader.ATTACKERS_MUST_BE_IN_NATION);
            return false;
        }

        if (!defenders.hasNation()) {
            LocaleReader.send(player, LocaleReader.DEFENDERS_MUST_BE_IN_NATION);
            return false;
        }*/

        if (isNationWar && !checkCanCreateNationWar(attackingNation, defendingNation, player))
            return false;

        if (isNationWar && attackingNation.getAllies().contains(defendingNation)) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_ALLIES);
            return false;
        }

        if (isNationWar) {


            try {
                UUID attackersUUID = attackers.getNation().getUUID();
                UUID defendersUUID = defenders.getNation().getUUID();

                double lastWarLost = NationWarCooldownManager.getAttackerLastLostSecondsAgoNation(attackersUUID, defendersUUID);

                if (lastWarLost < ConfigHandler.cooldownSecondsAttackersLoseAttackSameNation) {
                    LocaleReader.send(player, LocaleReader.COOLDOWN_ATTACKERS_LOSE_ATTACK_SAME_NATION
                            .replace("{DEFENDERS}", defenders.getNation().getName())
                            .replace("{TIME}", formatSeconds((int) (ConfigHandler.cooldownSecondsAttackersLoseAttackSameNation - lastWarLost))));
                    return false;
                }

                double lastWarLostOther = NationWarCooldownManager.getAttackerLostSecondsAgoNation(attackersUUID);

                if (lastWarLostOther < ConfigHandler.cooldownSecondsAttackersLoseAttackDifferentNation) {
                    LocaleReader.send(player, LocaleReader.COOLDOWN_ATTACKERS_LOSE_ATTACK_DIFFERENT_NATION
                            .replace("{TIME}", formatSeconds((int) (ConfigHandler.cooldownSecondsAttackersLoseAttackDifferentNation - lastWarLostOther))));
                    return false;
                }

                double lastDefenderWarLost = NationWarCooldownManager.getDefendersLostSecondsAgoNation(attackersUUID, defendersUUID);

                if (lastDefenderWarLost < ConfigHandler.cooldownSecondsDefendersLoseAttackByDifferentNation) {
                    LocaleReader.send(player, LocaleReader.COOLDOWN_DEFENDERS_LOSE_ATTACK_BY_DIFFERENT_NATION
                            .replace("{DEFENDERS}", defenders.getNation().getName())
                            .replace("{TIME}", formatSeconds((int) (ConfigHandler.cooldownSecondsAttackersLoseAttackSameNation - lastDefenderWarLost))));
                    return false;
                }

                double lastDefenderWarLostDiffNation = NationWarCooldownManager.getDefenderLostSecondsAgoNation(defendersUUID);

                if (lastDefenderWarLostDiffNation < ConfigHandler.cooldownSecondsDefendersLoseAttackByDifferentNation) {
                    LocaleReader.send(player, LocaleReader.COOLDOWN_DEFENDERS_LOSE_ATTACK_BY_DIFFERENT_NATION
                            .replace("{DEFENDERS}", defenders.getNation().getName())
                            .replace("{TIME}", formatSeconds((int) (ConfigHandler.cooldownSecondsDefendersLoseAttackByDifferentNation - lastDefenderWarLostDiffNation))));
                    return false;
                }
            } catch (NotRegisteredException exception) {
                exception.printStackTrace();
            }

        } else {
            double lastWarLost = TownWarCooldownManager.getAttackerLastLostSecondsAgoTown(attackers.getUUID(), defenders.getUUID());

            if (lastWarLost < ConfigHandler.cooldownSecondsAttackersLoseAttackSameTown) {
                LocaleReader.send(player, LocaleReader.COOLDOWN_ATTACKERS_LOSE_ATTACK_SAME_TOWN
                        .replace("{DEFENDERS}", defenders.getName())
                        .replace("{TIME}", formatSeconds((int) (ConfigHandler.cooldownSecondsAttackersLoseAttackSameTown - lastWarLost))));
                return false;
            }

            double lastWarLostOther = TownWarCooldownManager.getAttackerLostSecondsAgoTown(attackers.getUUID());

            if (lastWarLostOther < ConfigHandler.cooldownSecondsAttackersLoseAttackDifferentTown) {
                LocaleReader.send(player, LocaleReader.COOLDOWN_ATTACKERS_LOSE_ATTACK_DIFFERENT_TOWN
                        .replace("{TIME}", formatSeconds((int) (ConfigHandler.cooldownSecondsAttackersLoseAttackDifferentTown - lastWarLostOther))));
                return false;
            }

            double lastDefenderWarLost = TownWarCooldownManager.getDefendersLostSecondsAgoTown(attackers.getUUID(), defenders.getUUID());

            if (lastDefenderWarLost < ConfigHandler.cooldownSecondsDefendersLoseAttackByDifferentTown) {
                LocaleReader.send(player, LocaleReader.COOLDOWN_DEFENDERS_LOSE_ATTACK_BY_DIFFERENT_TOWN
                        .replace("{DEFENDERS}", defenders.getName())
                        .replace("{TIME}", formatSeconds((int) (ConfigHandler.cooldownSecondsAttackersLoseAttackSameTown - lastDefenderWarLost))));
                return false;
            }

            double lastDefenderWarLostDiffTown = TownWarCooldownManager.getDefenderLostSecondsAgoTown(defenders.getUUID());

            if (lastDefenderWarLostDiffTown < ConfigHandler.cooldownSecondsDefendersLoseAttackByDifferentTown) {
                LocaleReader.send(player, LocaleReader.COOLDOWN_DEFENDERS_LOSE_ATTACK_BY_DIFFERENT_TOWN
                        .replace("{DEFENDERS}", defenders.getName())
                        .replace("{TIME}", formatSeconds((int) (ConfigHandler.cooldownSecondsDefendersLoseAttackByDifferentTown - lastDefenderWarLostDiffTown))));
                return false;
            }
        }


        int online = 0;
        int requiredOnline = 0;

        for (Resident resident : defenders.getResidents()) {
            if (resident.getPlayer() != null) ++online;
        }

        List<String> stringList = TownWars.plugin.getConfig().getStringList("min-online-to-be-attacked-town-wars");

        if (isNationWar)
            stringList = TownWars.plugin.getConfig().getStringList("min-online-to-be-attacked-nation-wars");

        // Get how many players are required to be online
        for (String string : stringList) {
            try {
                String[] split = string.split(", ");
                int minOnline = Integer.parseInt(split[0]);
                int townSize = Integer.parseInt(split[1]);

                // Check if town is large enough to apply, and another setting hasn't applied a larger amount
                // The latter can happen with an unsorted list of minimum players online
                if (defenders.getResidents().size() >= townSize && requiredOnline < minOnline) {
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
                    .replace("{CURRENT_PLAYERS}", String.valueOf(defenders.getResidents().size()))
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
        if (attackers.getAllies().contains(defenders)) {
            LocaleReader.send(player, LocaleReader.COMMAND_CANNOT_ATTACK_ALLIES);
            return false;
        }

        return true;
    }

    // sendMessages is set to false during a reload.
    public static void setupWar(War war) {
        Town attackers = war.attackers;
        Town defenders = war.defenders;
        //boolean isNationWar = attackers.isCapital() && defenders.isCapital();
        boolean isNationWar = attackers.hasNation() && defenders.hasNation();
        war.isNationWar = isNationWar;

        TownWars.currentWars.add(war);
        TownWars.townsUnderSiege.put(defenders, war);

        if (isNationWar) {
            try {
                HashSet<War> currentWars = TownWars.warsListForEachNation.get(defenders.getNation());
                if (currentWars == null) currentWars = new HashSet<>();
                currentWars.add(war);
                TownWars.warsListForEachNation.put(defenders.getNation(), currentWars);

                currentWars = TownWars.warsListForEachNation.get(attackers.getNation());
                if (currentWars == null) currentWars = new HashSet<>();
                currentWars.add(war);
                TownWars.warsListForEachNation.put(attackers.getNation(), currentWars);
            } catch (NotRegisteredException e) {
                e.printStackTrace();
            }
        }
    }

    public static void sendWarStartedMessage(War war) {
        if (war.isNationWar) {

            war.messageGlobal(LocaleReader.GLOBAL_NEW_NATION_WAR);
            war.messageAttackers(LocaleReader.COMMAND_NOW_AT_WAR_WITH_NATION);
            war.messageDefenders(LocaleReader.DEFENDERS_NATION_WAR_WAS_DECLARED);

            // This is about messaging allies, not the nation or people in the war.
            // We have to handle the case of being allied to both, which is why we have our own method
            for (Nation nation : war.getAttackingNation().getAllies()) {
                if (nation.getAllies().contains(war.getDefendingNation())) {
                    for (Resident resident : nation.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        war.messagePlayer(resident.getPlayer(), LocaleReader.ASSIST_ALLIED_TO_BOTH_ATTACKERS_DEFENDERS);
                    }
                } else {
                    for (Resident resident : nation.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        war.messagePlayer(resident.getPlayer(), LocaleReader.ASSIST_NATION_ATTACK);
                    }
                }
            }

            for (Nation nation : war.getDefendingNation().getAllies()) {
                // We already sent a message to nations that have allied both
                if (nation.getAllies().contains(war.getAttackingNation())) continue;

                for (Resident resident : nation.getResidents()) {
                    if (resident.getPlayer() == null) continue;
                    war.messagePlayer(resident.getPlayer(), LocaleReader.ASSIST_NATION_DEFENSE);
                }
            }

        } else {
            war.messageGlobal(LocaleReader.GLOBAL_NEW_TOWN_WAR);

            war.messageAttackers(LocaleReader.COMMAND_NOW_AT_WAR_WITH_TOWN);
            war.messageDefenders(LocaleReader.DEFENDERS_TOWN_WAR_WAS_DECLARED);
        }
    }

    // TODO: Refund people who joined in when it is ended diplomatically
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
            war.messageGlobal(LocaleReader.GLOBAL_NATION_WAR_ENDS_PEACEFULLY);
            war.messageAttackers(LocaleReader.ATTACKERS_NOW_AT_PEACE_NATION_WAR);
            war.messageDefenders(LocaleReader.DEFENDERS_NOW_AT_PEACE_NATION_WAR);
        } else {
            war.messageGlobal(LocaleReader.GLOBAL_TOWN_WAR_ENDS_PEACEFULLY);
            war.messageAttackers(LocaleReader.ATTACKERS_NOW_AT_PEACE_TOWN_WAR);
            war.messageDefenders(LocaleReader.DEFENDERS_NOW_AT_PEACE_TOWN_WAR);
        }


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

    public static void forceEnd(War war) {
        if (war.attackerLives > war.defenderLives) {
            attackersWinWar(war);
        } else {
            defendersWinWar(war);
        }
    }

    public static void defendersWinWar(War war) {
        new Error().printStackTrace();
        TownWars.currentWars.remove(war);

        if (war.isNationWar) {
            try {
                war.defenders.getAccount().deposit(ConfigHandler.costStartNationWar, LocaleReader.NATION_WAR_DEPOSIT);

                war.messageGlobal(LocaleReader.GLOBAL_DEFENDERS_WIN_NATION_WAR);
                war.messageAttackers(LocaleReader.ATTACKERS_YOU_LOST_NATION_WAR);
                war.messageDefenders(LocaleReader.DEFENDERS_YOU_WON_NATION_WAR
                        .replace("{COST}", String.valueOf(ConfigHandler.costStartNationWar)));

                double moneyToAttackerNation = ((war.nationsDefending.size() - 1) *
                        ConfigHandler.costJoinNationWarDefenders) / war.nationsAttacking.size();

                for (Nation nation : war.nationsAttacking) {
                    for (Town town : nation.getTowns()) {
                        if (town == war.attackers) continue;

                        town.getAccount().deposit(moneyToAttackerNation, LocaleReader.NATION_WAR_DEPOSIT);

                        sendMessageToTown(town, LocaleReader.ATTACKING_NATIONS_YOU_LOST_NATION_WAR
                                .replace("{COST}", String.valueOf(moneyToAttackerNation)));
                    }
                }

                double moneyToDefenderNation = ((war.nationsAttacking.size() - 1) *
                        ConfigHandler.costJoinNationWarAttackers) / war.nationsDefending.size();

                for (Nation nation : war.nationsDefending) {
                    for (Town town : nation.getTowns()) {
                        if (town == war.defenders) continue;

                        town.getAccount().deposit(moneyToDefenderNation, LocaleReader.NATION_WAR_DEPOSIT);

                        sendMessageToTown(town, LocaleReader.DEFENDING_NATIONS_YOU_WON_NATION_WAR
                                .replace("{COST}", String.valueOf(moneyToDefenderNation)));
                    }
                }

                try {
                    NationWarCooldownManager.setAttackerLostNation(war.attackers.getNation().getUUID(), war.defenders.getNation().getUuid());
                } catch (NotRegisteredException exception) {
                    exception.printStackTrace();
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

                TownWarCooldownManager.setAttackerLostTown(war.attackers.getUUID(), war.defenders.getUUID());
            } catch (EconomyException e) {
                e.printStackTrace();
            }
        }

        deleteWar(war);
    }

    public static void attackersWinWar(War war) {
        new Error().printStackTrace();
        TownWars.currentWars.remove(war);

        // Set the defending town as conquered
        if (war.currentState == War.WarState.WAR && war.attackers.hasNation() && !war.isNationWar && war.defenders.hasNation() && ConfigHandler.conquerIfInNation) {
            try {
                war.defenders.setNation(null);
                war.defenders.setNation(war.getAttackingNation());
                war.defenders.setConquered(true);
                war.defenders.setConqueredDays(ConfigHandler.daysConqueredIfInNation);
            } catch (AlreadyRegisteredException e) {
                e.printStackTrace();
            }
        }

        if (war.currentState == War.WarState.WAR && war.attackers.hasNation() && !war.defenders.hasNation() && ConfigHandler.conquerIfWithoutNation) {
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
                war.messageGlobal(LocaleReader.GLOBAL_ATTACKERS_WIN_NATION_WAR);

                int defenderBlocks = 0;
                int baseBlocksPerAttacker = 0;
                int remainingBlocks = 0;

                if (war.currentState == War.WarState.WAR) {
                    for (Town town : war.getDefendingNation().getTowns()) {
                        int takenBlocks = (int) (town.getTotalBlocks() * ConfigHandler.nationWarBlocksTransferPercent);

                        town.setBonusBlocks(town.getBonusBlocks() - takenBlocks);
                        defenderBlocks += takenBlocks;
                    }

                    int attackerTownCount = 0;
                    for (Nation nation : war.nationsAttacking) {
                        attackerTownCount += nation.getTowns().size();
                    }

                    // Make it so bonus blocks aren't lost by rounding... not like it matters much
                    baseBlocksPerAttacker = defenderBlocks / attackerTownCount;
                    remainingBlocks = defenderBlocks - (baseBlocksPerAttacker * attackerTownCount);
                    war.attackers.setBonusBlocks(war.attackers.getBonusBlocks() + remainingBlocks);
                }


                war.messageAttackingNation(LocaleReader.ATTACKERS_YOU_WON_NATION_WAR
                        .replace("{CLAIMS}", String.valueOf(remainingBlocks + baseBlocksPerAttacker))
                        .replace("{COST}", String.valueOf(ConfigHandler.costStartTownWar)));

                // Attacking nation gets the blocks that were lost by rounding.
                war.messageAttackingAllies(LocaleReader.ATTACKERS_YOU_WON_NATION_WAR
                        .replace("{CLAIMS}", String.valueOf(baseBlocksPerAttacker))
                        .replace("{COST}", String.valueOf(ConfigHandler.costJoinNationWarAttackers)));

                war.messageDefendingNation(LocaleReader.DEFENDERS_YOU_LOST_NATION_WAR
                        .replace("{CLAIMS}", String.valueOf(remainingBlocks + baseBlocksPerAttacker)));

                war.messageDefendingAllies(LocaleReader.DEFENDING_NATIONS_YOU_LOST_NATION_WAR);

                war.attackers.getAccount().deposit(ConfigHandler.costStartNationWar, LocaleReader.NATION_WAR_DEPOSIT);

                try {
                    NationWarCooldownManager.setDefenderLostNation(war.attackers.getNation().getUUID(), war.defenders.getNation().getUuid());
                } catch (NotRegisteredException exception) {
                    exception.printStackTrace();
                }

            } else {
                int defenderBlocks = war.defenders.getTotalBlocks();
                int lost = 0;

                if (war.currentState == War.WarState.WAR) {
                    lost = (int) Math.ceil(defenderBlocks * ConfigHandler.townWarBlocksTransferPercent);
                    war.defenders.setBonusBlocks(war.defenders.getBonusBlocks() - lost);
                    war.attackers.setBonusBlocks(war.attackers.getBonusBlocks() + lost);
                }

                war.messageGlobal(LocaleReader.GLOBAL_ATTACKERS_WIN_TOWN_WAR);

                war.messageAttackers(LocaleReader.ATTACKERS_YOU_WON_TOWN_WAR
                        .replace("{CLAIMS}", lost + "")
                        .replace("{COST}", String.valueOf(ConfigHandler.costStartTownWar)));
                war.messageDefenders(LocaleReader.DEFENDERS_YOU_LOST_TOWN_WAR
                        .replace("{CLAIMS}", lost + ""));

                war.attackers.getAccount().deposit(ConfigHandler.costStartTownWar, LocaleReader.TOWN_WAR_DEPOSIT);

                TownWarCooldownManager.setDefenderLostTown(war.attackers.getUUID(), war.defenders.getUUID());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        deleteWar(war);
    }

    public static void deleteWar(War war) {
        // This is not the fastest way to iterate, remove, and modify a hashmap, but it works.
        HashMap<Nation, HashSet<War>> clonedMap = (HashMap<Nation, HashSet<War>>) TownWars.warsListForEachNation.clone();

        for (Map.Entry<Nation, HashSet<War>> nation : clonedMap.entrySet()) {
            TownWars.warsListForEachNation.get(nation.getKey()).remove(war);

            // stop memory leaks
            if (nation.getValue().size() > 0) continue;
            TownWars.warsListForEachNation.remove(nation.getKey());
        }

        // and finally, delete the saved war file
        war.delete();

        war.warBossbar.removeEveryoneBossBars();

        war.attackers.setAdminEnabledPVP(false);
        war.defenders.setAdminEnabledPVP(false);

        war.removeGlow();

        TownWars.currentWars.remove(war);
        TownWars.townsUnderSiege.remove(war.defenders);

        // This is the most likely to error, so try to restore the blocks
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

    public static String formatSeconds(int numSeconds) {
        String timeLeftString = "";

        // Copied from https://stackoverflow.com/a/21323783
        int numWeeks = numSeconds / (7 * 24 * 60 * 60);
        numSeconds -= numWeeks * 7 * 24 * 60 * 60;
        int numDays = numSeconds / (24 * 60 * 60);
        numSeconds -= numDays * 24 * 60 * 60;
        int numHours = numSeconds / (60 * 60);
        numSeconds -= numHours * 60 * 60;
        int numMinutes = numSeconds / 60;
        numSeconds -= numMinutes * 60;

        // There SHOULD be a better way to do this, but I can't find it.
        if (numWeeks == 1) timeLeftString += numWeeks + " week ";
        if (numWeeks > 1) timeLeftString += numWeeks + " weeks ";
        if (numDays == 1) timeLeftString += numDays + " days ";
        if (numDays > 1) timeLeftString += numDays + " day ";
        if (numHours == 1) timeLeftString += numHours + " hour ";
        if (numHours > 1) timeLeftString += numHours + " hours ";
        if (numMinutes == 1) timeLeftString += numMinutes + " minute ";
        if (numMinutes > 1) timeLeftString += numMinutes + " minutes ";
        if (numSeconds == 1) timeLeftString += numSeconds + " second";
        if (numSeconds > 1) timeLeftString += numSeconds + " seconds";
        if (timeLeftString.endsWith(" ")) timeLeftString = timeLeftString.substring(0, timeLeftString.length() - 1);

        return timeLeftString;
    }
}
