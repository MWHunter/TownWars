package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.UUID;

// This is the class that deals with commands only
public class WarCommand implements CommandExecutor {
    public static TownyAPI towny = TownyAPI.getInstance();
    public static HashMap<UUID, Double> lastFreeBanner = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player) sender;
        Town playerTown = WarUtility.getPlayerTown(player);

        if (args.length == 0) {
            return false;
        }

        if (playerTown == null) {
            sender.sendMessage(ChatColor.RED + "You must be a part of a town to go to war");
            return true;
        }

        switch (args[0]) {
            case "declare":
                boolean inventoryFreeSlots = false;
                Inventory playerInventory = player.getInventory();
                for (int i = 0; i < 36; i++) {
                    if (playerInventory.getItem(i) == null) {
                        inventoryFreeSlots = true;
                        break;
                    }
                }

                if (player.getInventory().contains(Material.RED_BANNER)) {
                    player.sendMessage(ChatColor.RED + "You already have a war banner in your inventory");
                    return true;
                }

                if (inventoryFreeSlots) {
                    player.getInventory().addItem(TownWars.warFlag);
                } else {
                    player.getLocation().getWorld().dropItemNaturally(player.getLocation(), TownWars.warFlag);
                    player.sendMessage(ChatColor.AQUA + "Your inventory is full so the war flag is dropped at your location");
                }

                player.sendMessage(ChatColor.GREEN + "Place this war banner within two chunks outside a town to begin your capture");

                return true;
            case "end":
                War targetWar = WarUtility.parseTargetWar(args, player);

                if (targetWar == null) {
                    player.sendMessage(ChatColor.RED + "Please specify a war to end");
                }

                WarManager.endDiplomatically(WarUtility.parseTargetWar(args, player), player);

                return true;
            case "surrender":
                War war = WarUtility.parseTargetWar(args, player);

                if (war == null) {
                    player.sendMessage(ChatColor.RED + "Please specify a war to surrender");
                }

                WarManager.surrender(war, player);

                return true;
            case "list":
                for (War warIterator : TownWars.currentWars) {
                    if (warIterator.attackers == playerTown) {
                        sender.sendMessage(ChatColor.AQUA + "You are at war with " + warIterator.defenders.getName());
                    }

                    if (warIterator.defenders == playerTown) {
                        sender.sendMessage(ChatColor.AQUA + "You are at war with " + warIterator.attackers.getName());
                    }
                }

                return true;
            case "help":
                return false;
        }

        return false;
    }
}
