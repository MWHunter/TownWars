package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

// This is the class that deals with commands only
public class WarCommand extends BaseCommand implements CommandExecutor {
    public static TownyAPI towny = TownyAPI.getInstance();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players may run commands!");
            return true;
        }

        Player player = (Player) sender;
        Town playerTown = WarUtility.getPlayerTown(player);

        if (args.length == 0) {
            return false;
        }

        if (playerTown == null) {
            LocaleReader.send(sender, LocaleReader.COMMAND_NOT_PART_OF_TOWN);
            return true;
        }

        switch (args[0]) {
            // TODO: Add confirmations
            case "declare":
                if (args.length < 2) {
                    LocaleReader.send(player, LocaleReader.COMMAND_NO_SPECIFIED_TOWN);
                }

                Town targetTown = WarUtility.parseTargetTown(args[1]);

                if (targetTown == null) {
                    LocaleReader.send(player, LocaleReader.COMMAND_TOWN_NOT_FOUND);
                    return true;
                }

                WarManager.checkThenCreateWar(playerTown, targetTown, player);

                return true;
            case "join":
                // TODO: Nation war join logic - make it idiot proof
                // TODO: Autocomplete
                // TODO: Cost for joining wars
                if (args.length < 2) {
                    LocaleReader.send(player, LocaleReader.COMMAND_NO_SPECIFIED_WAR);
                }

                Town targetTown2 = WarUtility.parseTargetTown(args[1]);

                for (War war : TownWars.currentWars) {
                    if (war.defenders != targetTown2) continue;

                    if (!war.isNationWar) {
                        LocaleReader.send(player, LocaleReader.COMMAND_NOT_NATION_WAR);
                        return true;
                    }

                    if (!playerTown.hasNation()) {
                        LocaleReader.send(player, LocaleReader.COMMAND_NOT_PART_OF_NATION);
                        return true;
                    }

                    // error should never happen
                    try {
                        Nation playerNation = playerTown.getNation();
                        Nation targetNation = targetTown2.getNation();

                        Nation attackingNation = war.attackers.getNation();

                        if (playerNation.hasAlly(targetNation)) {
                            LocaleReader.send(player, LocaleReader.WAR_JOINED_AS_DEFENDER
                                    .replace("{NATION}", playerNation.getName())
                                    .replace("{NATION_ATTACKERS}", war.attackers.getNation().getName()
                                            .replace("{NATION_DEFENDERS}", war.defenders.getNation().getName())));

                            if (!TownWars.nationCurrentNationWars.containsKey(playerNation)) {
                                TownWars.nationCurrentNationWars.put(playerNation, new HashSet<>());
                            }

                            TownWars.nationCurrentNationWars.get(playerNation).add(war);
                            war.nationsDefending.add(playerNation);
                        } else if (playerNation.hasAlly(attackingNation)) {
                            LocaleReader.send(player, LocaleReader.WAR_JOINED_AS_ATTACKER
                                    .replace("{NATION}", playerNation.getName())
                                    .replace("{NATION_ATTACKERS}", war.attackers.getNation().getName()
                                            .replace("{NATION_DEFENDERS}", war.defenders.getNation().getName())));

                            if (!TownWars.nationCurrentNationWars.containsKey(playerNation)) {
                                TownWars.nationCurrentNationWars.put(playerNation, new HashSet<>());
                            }

                            TownWars.nationCurrentNationWars.get(playerNation).add(war);
                            war.nationsAttacking.add(playerNation);
                        } else {
                            LocaleReader.send(player, LocaleReader.COMMAND_NOT_ALLY_OF_DEFENDER_OR_ATTACKER);
                        }

                    } catch (NotRegisteredException ignored) {
                    }
                }

                return true;
            case "end":
                War targetWar = WarUtility.parseTargetWar(args, player);

                if (targetWar == null) {
                    LocaleReader.send(player, LocaleReader.COMMAND_MUST_SPECIFY_WAR_TO_END);
                    return true;
                }

                WarManager.endDiplomatically(WarUtility.parseTargetWar(args, player), player);

                return true;
            case "surrender":
                War war = WarUtility.parseTargetWar(args, player);

                if (war == null) {
                    LocaleReader.send(player, LocaleReader.COMMAND_MUST_SPECIFY_WAR_TO_SURRENDER);
                    return true;
                }

                WarManager.surrender(war, player);

                return true;
            case "list":
                boolean hasSentHeader = false;

                for (War warIterator : TownWars.currentWars) {
                    // send the header once... so this so a different message can be sent if the player isn't in wars
                    if (!hasSentHeader) {
                        LocaleReader.send(player, LocaleReader.WAR_LIST_HEADER);
                        hasSentHeader = true;
                    }

                    if (warIterator.isNationWar) {
                        StringBuilder attackingNations = new StringBuilder();
                        for (Nation attackers : warIterator.nationsAttacking) {
                            attackingNations.append(attackers.getName());
                        }

                        StringBuilder defendingNations = new StringBuilder();
                        for (Nation defenders : warIterator.nationsDefending) {
                            defendingNations.append(defenders.getName());
                        }

                        LocaleReader.send(sender, LocaleReader.WAR_LIST_FORMAT
                                .replace("{ATTACKERS}", attackingNations)
                                .replace("{ATTACKER_KILLS}", warIterator.attackerKills + "")
                                .replace("{DEFENDERS}", defendingNations)
                                .replace("{DEFENDER_KILLS}", warIterator.defenderKills + "")
                                .replace("{TIME}", ((ConfigHandler.timeLimitNationWar - warIterator.tick) / 20 + "")));
                    } else {
                        LocaleReader.send(sender, LocaleReader.WAR_LIST_FORMAT
                                .replace("{ATTACKERS}", warIterator.attackers.getName())
                                .replace("{ATTACKER_KILLS}", warIterator.attackerKills + "")
                                .replace("{DEFENDERS}", warIterator.defenders.getName())
                                .replace("{DEFENDER_KILLS}", warIterator.defenderKills + "")
                                .replace("{TIME}", ((ConfigHandler.timeLimitTownWar - warIterator.tick) / 20 + "")));
                    }
                }

                if (!hasSentHeader) {
                    LocaleReader.send(sender, LocaleReader.COMMAND_NOT_IN_ANY_WARS);
                }

                return true;
            case "help":
                LocaleReader.send(sender, LocaleReader.COMMAND_HELP);
                return true;
            case "reload":
                if (sender.hasPermission("townwars.reload")) {
                    ConfigHandler.reload();
                    LocaleReader.reload();
                    sender.sendMessage(ChatColor.GREEN + "reloaded config and lang files");
                }
                return true;
        }

        return false;
    }

    // Design of tab completion is from Towny, since we are using the Towny API
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> returnList = new ArrayList<>();
        switch (args.length) {
            case 1:
                return null;
            case 2:
                switch (args[0].toLowerCase()) {
                    case "declare":
                        returnList.addAll(TownyUniverse.getInstance().getTownsTrie().getStringsFromKey(args[1]));
                        Bukkit.getOnlinePlayers().forEach(player -> returnList.add(player.getName()));

                        return returnList;
                }
        }

        return returnList;
    }
}
