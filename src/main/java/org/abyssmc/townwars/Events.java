package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.util.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import com.palmergames.bukkit.towny.event.actions.TownyItemuseEvent;

import java.util.Random;

public class Events implements Listener {
    TownyAPI towny = TownyAPI.getInstance();
    Random random = new Random();

    @EventHandler
    public void onSwitchUse(TownyItemuseEvent event) {
        
    }

    @EventHandler
    public void onPlayerKill(PlayerDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            try {
                Resident deadPlayer = towny.getDataSource().getResident(event.getEntity().getName());
                Resident killer = towny.getDataSource().getResident(event.getEntity().getKiller().getName());

                Town deadPlayerTown = deadPlayer.getTown();
                Town killerTown = killer.getTown();

                for (War war : TownWars.currentWars) {
                    if (deadPlayerTown == war.attackers && killerTown == war.defenders) {
                        if (war.defenders.hasJailSpawn()) {
                            // Towny indexes jails at 1.
                            killer.sendToJail(random.nextInt(war.defenders.getJailSpawns().size()) + 1, war.defenders);
                        }

                        break;
                    }
                }
            } catch (NotRegisteredException e) {
                e.printStackTrace();
            }
        }
    }

    @EventHandler
    public void holdWarBanner(PlayerItemHeldEvent event) {
        ItemStack newItem = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (newItem != null && newItem.getType() == Material.RED_BANNER) {
            event.getPlayer().sendMessage(ChatColor.GREEN + "Place this war banner within two chunks outside a town to begin your capture");
        }
    }

    @EventHandler
    public void onPlayerBreakEvent(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.RED_BANNER) {
            for (War war : TownWars.currentWars) {
                if (war.warFlagLocation.equals(event.getBlock().getLocation())) {
                    if (war.attackersHaveFlagControl()) {
                        war.messageDefenders(ChatColor.RED + "The flag is protected by an attacker within 16 blocks");
                        event.setCancelled(true);
                        return;
                    }

                    Bukkit.broadcastMessage("The war banner was broken!");
                    WarManager.attackersLoseWar(war);
                    event.setDropItems(false);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerPlaceBanner(BlockPlaceEvent event) {
        if (event.getItemInHand().getType() == Material.RED_BANNER) {
            try {
                Town playerTown = WarUtility.getPlayerTown(event.getPlayer());

                if (playerTown == null) return;

                Location placeLocation = event.getBlock().getLocation();
                WorldCoord coord = WorldCoord.parseWorldCoord(placeLocation);
                TownyWorld townyWorld = coord.getTownyWorld();
                TownBlock closestTownBlock = getClosestTownBlock(townyWorld, coord);
                Town blockChunkTown = towny.getTown(placeLocation);

                int distanceToPlots = Math.abs(coord.getX() - closestTownBlock.getX()) + Math.abs(coord.getZ() - closestTownBlock.getZ());

                if (blockChunkTown != null) {
                    event.getPlayer().sendMessage(ChatColor.RED + "You cannot place this flag inside of a town!");
                    event.setCancelled(true);
                    return;
                }

                if (distanceToPlots > 2) {
                    event.getPlayer().sendMessage(ChatColor.RED + "You must place a war banner outside a town within two chunks of it");
                    event.setCancelled(true);
                    return;
                }

                event.setCancelled(!WarManager.createWar(playerTown, closestTownBlock.getTown(), event.getBlock().getLocation(), event.getPlayer()));

            } catch (NotRegisteredException e) {
                event.getPlayer().sendMessage(ChatColor.RED + "An error occurred while placing the war banner");
                e.printStackTrace();
            }
        }
    }

    public static TownBlock getClosestTownBlock(TownyWorld world, Coord coord) {
        double minSqr = -1;
        TownBlock tb = null;

        for (Town town : world.getTowns().values()) {
            for (TownBlock b : town.getTownBlocks()) {
                if (!b.getWorld().equals(world))
                    continue;

                final int tbX = b.getX();
                final int tbZ = b.getZ();

                double distSqr = MathUtil.distanceSquared(tbX - coord.getX(), tbZ - coord.getZ());
                if (minSqr == -1 || distSqr < minSqr) {
                    minSqr = distSqr;
                    tb = b;
                }
            }
        }

        return tb;
    }
}
