package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

// This is the class that deals with commands only
public class WarCommand extends BaseCommand implements CommandExecutor {
    public static final List<String> warSubCommands = Arrays.asList("declare", "confirm", "peace", "surrender", "list", "help");
    public static HashMap<UUID, String[]> commandsToConfirm = new HashMap<>();

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

        // TODO: Allow targeting a town VS a nation!
        // A nation can have the same name as a town!
        switch (args[0]) {
            case "declare": {
                if (player.hasPermission("townwars.declare")) {
                    if (args.length < 2) {
                        LocaleReader.send(player, LocaleReader.COMMAND_NO_SPECIFIED_TOWN);
                        return true;
                    }

                    Town targetTown = WarUtility.parseTargetTown(args[1]);

                    // This checks if target town is null, we don't have to do it
                    if (WarManager.checkCanCreateWar(playerTown, targetTown, player)) {
                    /*if (playerTown.isCapital() && playerTown.hasNation() && targetTown.hasNation()) {
                        LocaleReader.send(player, LocaleReader.CONFIRM_START_NATION_WAR
                                .replace("{NATION_ATTACKERS}", WarUtility.getPlayerNation(player).getName())
                                .replace("{NATION_DEFENDERS}", WarUtility.getNation(targetTown).getName())
                                .replace("{TIME_LIMIT}", War.formatSeconds(ConfigHandler.tickLimitNationWar / 20))
                                .replace("{COST}", String.valueOf(ConfigHandler.costStartNationWar)));
                    } else {*/
                        LocaleReader.send(player, LocaleReader.CONFIRM_START_TOWN_WAR
                                .replace("{ATTACKERS}", playerTown.getName())
                                .replace("{DEFENDERS}", targetTown.getName())
                                .replace("{TIME_LIMIT}", WarManager.formatSeconds(ConfigHandler.tickLimitNationWar / 20))
                                .replace("{COST}", String.valueOf(ConfigHandler.costStartTownWar)));
                        //}

                        addCommandToConfirm(player, args);
                    }
                } else {
                    LocaleReader.send(player, LocaleReader.NO_PERMISSION);
                }

                return true;
            }
            /*case "join": {
                if (args.length < 2) {
                    LocaleReader.send(player, LocaleReader.COMMAND_NO_SPECIFIED_TOWN);
                }

                Town targetTown = WarUtility.parseTargetTown(args[1]);
                Nation targetNation = WarUtility.parseTargetNation(args[1]);
                Nation playerNation = WarUtility.getPlayerNation(player);

                for (War war : TownWars.currentWars) {
                    if (war.defenders != targetTown) continue;

                    if (!war.isNationWar) {
                        LocaleReader.send(player, LocaleReader.COMMAND_NOT_NATION_WAR);
                        return true;
                    }

                    Nation attackingNation = war.getAttackingNation();

                    if (playerNation.hasAlly(attackingNation) && playerNation.hasAlly(targetNation)) { // Unknown what player is joining as
                        war.messagePlayer(player, LocaleReader.COMMAND_ALLY_OF_BOTH_ATTACKERS_AND_DEFENDERS);
                        return true;
                    } else if (playerNation.hasAlly(targetNation)) { // The player is joining as a defender
                        try {
                            if (!playerTown.getAccount().canPayFromHoldings(ConfigHandler.costJoinNationWarDefenders)) {
                                LocaleReader.send(player, LocaleReader.COMMAND_NOT_ENOUGH_BALANCE_NATION_WAR
                                        .replace("{COST}", String.valueOf(ConfigHandler.costStartNationWar)));
                                return true;
                            }

                        } catch (EconomyException e) {
                            e.printStackTrace();
                            LocaleReader.send(player, LocaleReader.WITHDRAW_ERROR);
                            return true;
                        }

                        LocaleReader.send(player, LocaleReader.CONFIRM_JOIN_NATION_WAR_DEFENDERS);

                    } else if (playerNation.hasAlly(attackingNation)) { // Player joining as attacker
                        // Confirm war join as attacker
                        try {
                            if (!playerTown.getAccount().canPayFromHoldings(ConfigHandler.costJoinNationWarAttackers)) {
                                LocaleReader.send(player, LocaleReader.COMMAND_NOT_ENOUGH_BALANCE_NATION_WAR.replace("{COST}", String.valueOf(ConfigHandler.costStartNationWar)));
                                return true;
                            }

                        } catch (EconomyException e) {
                            e.printStackTrace();
                            LocaleReader.send(player, LocaleReader.WITHDRAW_ERROR);
                            return true;
                        }

                        LocaleReader.send(player, LocaleReader.CONFIRM_JOIN_NATION_WAR_ATTACKERS);

                    } else {
                        LocaleReader.send(player, LocaleReader.COMMAND_NOT_ALLY_OF_DEFENDER_OR_ATTACKER);
                        return true;
                    }

                    addCommandToConfirm(player, args);
                }

                return true;
            }*/
            case "confirm": {
                if (!commandsToConfirm.containsKey(player.getUniqueId())) {
                    LocaleReader.send(player, LocaleReader.COMMAND_NOTHING_TO_CONFIRM);
                    return true;
                }

                WarManager.confirmWar(commandsToConfirm.get(player.getUniqueId()), player);
                commandsToConfirm.remove(player.getUniqueId());

                return true;
            }
            case "peace": {
                if (player.hasPermission("townwars.peace")) {
                    War targetWar = WarUtility.parseTargetWar(args, player);

                    if (targetWar == null) {
                        // parsing already sent a message
                        return true;
                    }

                    WarManager.endDiplomatically(targetWar, player);

                    return true;
                } else {
                    LocaleReader.send(player, LocaleReader.NO_PERMISSION);
                }
            }
            case "surrender": {
                if (player.hasPermission("townwars.surrender")) {
                    War war = WarUtility.parseTargetWar(args, player);

                    if (war == null) {
                        LocaleReader.send(player, LocaleReader.COMMAND_MUST_SPECIFY_WAR_TO_SURRENDER);
                        return true;
                    }

                    WarManager.surrender(war, player);
                } else {
                    LocaleReader.send(player, LocaleReader.NO_PERMISSION);
                }
                return true;
            }
            case "list": {
                boolean hasSentHeader = false;

                if (player.hasPermission("townwars.list")) {
                    for (War warIterator : TownWars.currentWars) {
                        // send the header once... so this so a different message can be sent if the player isn't in wars
                        if (!hasSentHeader) {
                            LocaleReader.send(player, LocaleReader.WAR_LIST_HEADER);
                            hasSentHeader = true;
                        }

                        if (warIterator.isNationWar) {
                            warIterator.messagePlayer(player, LocaleReader.WAR_LIST_FORMAT_NATION);
                        } else {
                            warIterator.messagePlayer(player, LocaleReader.WAR_LIST_FORMAT_TOWN);
                        }
                    }

                    if (!hasSentHeader) {
                        LocaleReader.send(sender, LocaleReader.COMMAND_NOT_IN_ANY_WARS);
                        return true;
                    }


                } else {
                    LocaleReader.send(player, LocaleReader.NO_PERMISSION);
                }
                return true;
            }

            case "help": {
                // TODO: Explain the war system in the help command
                if (sender.hasPermission("townwars.use")) {
                    LocaleReader.send(sender, LocaleReader.COMMAND_HELP);
                } else {
                    LocaleReader.send(player, LocaleReader.NO_PERMISSION);
                }
                return true;
            }
            case "reload": {
                if (sender.hasPermission("townwars.reload")) {
                    ConfigHandler.reload();
                    LocaleReader.reload();
                    sender.sendMessage(ChatColor.GREEN + "reloaded config and lang files");
                } else {
                    LocaleReader.send(player, LocaleReader.NO_PERMISSION);
                }
                return true;
            }
            case "uuid":
                if (sender.hasPermission("townwars.debug")) {
                    sender.sendMessage("You belong to town UUID: " + playerTown.getUUID());
                    if (TownyAPI.getInstance().getTown(player.getLocation()) != null) {
                        sender.sendMessage("You are standing in town UUID " + TownyAPI.getInstance().getTown(player.getLocation()).getUUID());
                    }

                    return true;
                } else {
                    LocaleReader.send(player, LocaleReader.NO_PERMISSION);
                }
        }

        return false;
    }

    public void addCommandToConfirm(Player player, String[] args) {
        commandsToConfirm.put(player.getUniqueId(), args);
        Bukkit.getScheduler().runTaskLater(TownWars.plugin, () -> {
            if (commandsToConfirm.remove(player.getUniqueId(), args)) {
                LocaleReader.send(player, LocaleReader.CONFIRMATION_EXPIRED.replace("{SECONDS_TO_CONFIRM}", String.valueOf(ConfigHandler.secondToConfirmAction)));
            }
        }, ConfigHandler.secondToConfirmAction * 20L);
    }

    // Design of tab completion is from Towny, since we are using the Towny API
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> returnList = new ArrayList<>();
        switch (args.length) {
            case 1:
                return warSubCommands;
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
