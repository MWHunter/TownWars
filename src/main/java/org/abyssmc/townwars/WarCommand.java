package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;

// This is the class that deals with commands only
public class WarCommand implements CommandExecutor {
    public static TownyAPI towny = TownyAPI.getInstance();
    TownWars plugin;
    WarManager warManager;

    public WarCommand(TownWars plugin) {
        this.plugin = plugin;
        this.warManager = new WarManager(plugin);
    }

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
            case "declare":
                if (args.length < 2) {
                    LocaleReader.send(player, LocaleReader.COMMAND_NO_SPECIFIED_TOWN);
                }

                Town targetTown = WarUtility.parseTargetTown(args[1]);

                if (targetTown != null) {
                    warManager.createWar(playerTown, targetTown, player);
                    return true;
                }

                LocaleReader.send(player, LocaleReader.COMMAND_TOWN_NOT_FOUND);
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
                            LocaleReader.send(player, LocaleReader.WAR_JOINED_AS_DEFENDER);

                            if (!TownWars.nationCurrentNationWars.containsKey(playerNation)) {
                                TownWars.nationCurrentNationWars.put(playerNation, new HashSet<>());
                            }

                            TownWars.nationCurrentNationWars.get(playerNation).add(war);
                            war.nationsDefending.add(playerNation);
                        } else if (playerNation.hasAlly(attackingNation)) {
                            LocaleReader.send(player, LocaleReader.WAR_JOINED_AS_ATTACKER);

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
                }

                warManager.endDiplomatically(WarUtility.parseTargetWar(args, player), player);

                return true;
            case "surrender":
                War war = WarUtility.parseTargetWar(args, player);

                if (war == null) {
                    LocaleReader.send(player, LocaleReader.COMMAND_MUST_SPECIFY_WAR_TO_SURRENDER);
                }

                warManager.surrender(war, player);

                return true;
            case "list":
                LocaleReader.send(player, LocaleReader.WAR_LIST_HEADER);

                // TODO: How do I format stuff with LocaleReader?  Not implemented yet.
                for (War warIterator : TownWars.currentWars) {
                    if (warIterator.attackers == playerTown) {
                        LocaleReader.send(sender, LocaleReader.WAR_LIST_FORMAT);
                    }

                    if (warIterator.defenders == playerTown) {
                        LocaleReader.send(sender, LocaleReader.WAR_LIST_FORMAT);
                    }
                }

                return true;
            case "help":
                LocaleReader.send(sender, LocaleReader.COMMAND_HELP);
                return true;
            case "reload":
                if (sender.hasPermission("townwars.reload")) {
                    plugin.configHandler.reload();
                    LocaleReader.reload(plugin);
                    sender.sendMessage(ChatColor.GREEN + "reloaded config and lang files");
                }
        }

        return false;
    }
}
