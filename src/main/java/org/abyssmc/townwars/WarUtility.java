package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class WarUtility {
    public static War parseTargetWar(String[] args, Player sender) {
        Town playerTown;
        Town targetTown = null;
        War targetWar = null;
        int countWars = 0;

        // This should never happen.  WarCommands should return if the player has no town.
        // Stack trace just so we know that something is wrong.
        try {
            playerTown = WarCommand.towny.getDataSource().getResident(sender.getName()).getTown();
        } catch (NotRegisteredException e) {
            LocaleReader.send(sender, LocaleReader.COMMAND_NOT_PART_OF_TOWN);
            e.printStackTrace();
            return null;
        }

        if (args.length > 1) {
            targetTown = parseTargetTown(args[1]);
        }

        for (War war : TownWars.currentWars) {
            if (war.attackers == playerTown) {
                targetWar = war;
                countWars++;

                if (args.length > 1 && targetTown != null && war.defenders.getName().equalsIgnoreCase(targetTown.getName())) {
                    return targetWar;
                }
            }

            if (war.defenders == playerTown) {
                targetWar = war;
                countWars++;

                if (args.length > 1 && targetTown != null && war.attackers.getName().equalsIgnoreCase(targetTown.getName())) {
                    return targetWar;
                }
            }
        }

        if (countWars == 0) {
            LocaleReader.send(sender, LocaleReader.COMMAND_NOT_IN_ANY_WARS);
            return null;
        }

        if (countWars > 1 && args.length < 2) {
            LocaleReader.send(sender, LocaleReader.COMMAND_MUST_SPECIFY_TOWN_BECAUSE_MULTIPLE_WARS);
            return null;
        }

        return targetWar;
    }

    public static Town parseTargetTown(String target) {
        try {
            return WarCommand.towny.getDataSource().getTown(target);
        } catch (NotRegisteredException e) {
            Player targetPlayer = Bukkit.getPlayer(target);

            if (targetPlayer != null) {
                return getPlayerTown(targetPlayer);
            }
        }

        return null;
    }

    public static Nation parseTargetNation(String target) {
        try {
            return WarCommand.towny.getDataSource().getNation(target);
        } catch (NotRegisteredException e) {
            Player targetPlayer = Bukkit.getPlayer(target);

            if (targetPlayer != null) {
                return getPlayerNation(targetPlayer);
            }
        }

        return null;
    }

    public static Nation getPlayerNation(Player player) {
        try {
            return getPlayerTown(player).getNation();
        } catch (NotRegisteredException e) {
            return null;
        }
    }

    public static Town getPlayerTown(Player player) {
        try {
            return WarCommand.towny.getDataSource().getResident(player.getName()).getTown();
        } catch (NotRegisteredException e) {
            return null;
        }
    }
}
