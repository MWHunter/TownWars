package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.actions.TownyBuildEvent;
import com.palmergames.bukkit.towny.event.actions.TownyDestroyEvent;
import com.palmergames.bukkit.towny.event.actions.TownyExplodingBlocksEvent;
import com.palmergames.bukkit.towny.event.actions.TownySwitchEvent;
import com.palmergames.bukkit.towny.event.nation.toggle.NationToggleNeutralEvent;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.regen.TownyRegenAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

// TODO: The only way to break a chest is with tnt.  There is no way to break dispensers.
public class Events implements Listener {
    static HashSet<UUID> cancelNextBlockBreakDrop = new HashSet<>();
    static HashMap<UUID, War> logNextBlockPlace = new HashMap<>();
    TownyAPI towny = TownyAPI.getInstance();

    // fuck towny
    // Their destroy event with too many things in it will mess up our plugin
    public static void clearLists() {
        Bukkit.getScheduler().runTaskTimer(TownWars.plugin, () -> {
            cancelNextBlockBreakDrop.clear();
            logNextBlockPlace.clear();
        }, 1, 1);
    }

    // Towny uses event priority high for some reason
    // Using event priority higher allows us interact with towny's block break event.
    // Make it so players can't dupe by breaking blocks during a war
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBukkitBlockBreakEvent(BlockBreakEvent event) {
        if (cancelNextBlockBreakDrop.contains(event.getPlayer().getUniqueId())) {
            cancelNextBlockBreakDrop.remove(event.getPlayer().getUniqueId());
            event.setDropItems(false);

            // Disable griefing via updating blocks
            // TODO: This code sucks and will break other plugins
            // the alternative is to listen to blockPhysicsEvent, which is called
            // thousands of times a second
            event.getBlock().setType(Material.AIR, false);

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreakEvent(TownyDestroyEvent event) {
        // Okay.  So TownyDestroyEvent is called when players hurt animals
        // An animal has a 1234.56789876 exact coordinate while a block has 1234.0 as an exact coordinate
        // Therefore if we check whether the exact coordinate equals the block coordinate, we know whether
        // it is an animal or a block.  Yes.  This is stupid and a hack.  Blame Towny, not me.
        if (event.getLocation().getX() != event.getLocation().getBlockX() && event.getLocation().getZ() != event.getLocation().getBlockZ()) {
            return;
        }

        // Item frame or something similar
        if (TownWars.disallowedBlocksBroken.contains(event.getBlock().getType())) return;

        try {
            Town placerTown = towny.getDataSource().getResident(event.getPlayer().getName()).getTown();
            Town destroyTown = null;

            if (event.getTownBlock() != null && event.getTownBlock().hasTown())
                destroyTown = event.getTownBlock().getTown();

            if (placerTown == null) return;

            // Don't interfere with people placing stuff in their own territorry
            if (placerTown == destroyTown) return;

            for (War war : TownWars.currentWars) {
                if (war.isNationWar) {
                    if (!placerTown.hasNation()) continue;
                    if (destroyTown == null || !destroyTown.hasNation()) return;

                    // Allow attackers and defenders to place things in each others territory, which will be rolled back
                    if (war.nationsDefending.contains(destroyTown.getNation()) || war.nationsAttacking.contains(destroyTown.getNation())
                            && war.nationsDefending.contains(placerTown.getNation()) || war.nationsAttacking.contains(placerTown.getNation())) {
                        if (event.getBlock().getState() instanceof InventoryHolder) return;

                        event.setCancelled(false);
                        cancelNextBlockBreakDrop.add(event.getPlayer().getUniqueId());
                        war.restoreBlockBroken(event.getBlock().getLocation(), event.getBlock().getBlockData());
                    }
                } else {
                    // Let attackers and defenders place things in each other's territory, which will be rolled back
                    if (placerTown == war.attackers || placerTown == war.defenders && destroyTown == war.attackers || destroyTown == war.defenders) {
                        if (event.getBlock().getState() instanceof InventoryHolder) return;

                        event.setCancelled(false);
                        cancelNextBlockBreakDrop.add(event.getPlayer().getUniqueId());
                        war.restoreBlockBroken(event.getBlock().getLocation(), event.getBlock().getBlockData());
                    }
                }
            }
        } catch (NotRegisteredException ignored) {
        }

    }

    @EventHandler
    public void onExplosionDamagingBlocks(TownyExplodingBlocksEvent event) {
        int count = 0;

        for (Block block : event.getVanillaBlockList()) {
            if (!TownWars.townsUnderSiege.containsKey(towny.getTown(block.getLocation()))) continue;
            // Don't allow blowing up chests, defenders should have access to them!
            if (block instanceof Container) continue;

            count++;
            TownyRegenAPI.beginProtectionRegenTask(block, count, TownyAPI.getInstance().getTownyWorld(block.getLocation().getWorld().getName()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBukkitBlockPlaceEvent(BlockPlaceEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();

        if (logNextBlockPlace.containsKey(playerUUID)) {
            War war = logNextBlockPlace.get(playerUUID);
            logNextBlockPlace.remove(playerUUID);
            event.getBlockReplacedState().getBlock();
            war.restoreBlockPlaced(event.getBlock().getLocation(), event.getBlockReplacedState().getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlaceEvent(TownyBuildEvent event) {
        try {
            Town placerTown = towny.getDataSource().getResident(event.getPlayer().getName()).getTown();
            Town destroyTown = null;

            if (event.getTownBlock() != null && event.getTownBlock().hasTown())
                destroyTown = event.getTownBlock().getTown();

            if (placerTown == null) return;

            // Don't interfere with people placing stuff in their own territorry
            if (placerTown == destroyTown) return;

            for (War war : TownWars.currentWars) {
                if (war.isNationWar) {
                    if (!placerTown.hasNation()) continue;
                    if (destroyTown == null || !destroyTown.hasNation()) return;

                    // Allow attackers and defenders to place things in each others territory, which will be rolled back
                    if (war.nationsDefending.contains(destroyTown.getNation()) || war.nationsAttacking.contains(destroyTown.getNation())
                            && war.nationsDefending.contains(placerTown.getNation()) || war.nationsAttacking.contains(placerTown.getNation())) {
                        if (event.getBlock().getState() instanceof InventoryHolder) return;

                        event.setCancelled(false);
                        logNextBlockPlace.put(event.getPlayer().getUniqueId(), war);
                    }
                } else {
                    // Let attackers and defenders place things in each other's territory, which will be rolled back
                    if (placerTown == war.attackers || placerTown == war.defenders && destroyTown == war.attackers || destroyTown == war.defenders) {
                        if (event.getBlock().getState() instanceof InventoryHolder) return;

                        event.setCancelled(false);
                        logNextBlockPlace.put(event.getPlayer().getUniqueId(), war);
                    }
                }
            }
        } catch (NotRegisteredException ignored) {
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwitchUse(TownySwitchEvent event) {
        if (event.getTownBlock() == null) return;
        try {
            Town switchTown = event.getTownBlock().getTown();
            if (TownWars.townsUnderSiege.containsKey(switchTown) && TownWars.allowedSwitches.contains(event.getBlock().getType())) {
                Town openerTown = towny.getDataSource().getResident(event.getPlayer().getName()).getTown();
                War war = TownWars.townsUnderSiege.get(switchTown);

                // Town wars
                if (war != null && war.attackers == openerTown && war.currentState != War.WarState.PREWAR) {
                    event.setCancelled(false);
                    return;
                }

                // Nation wars
                if (openerTown.hasNation() && switchTown.hasNation() &&
                        TownWars.warsListForEachNation.containsKey(openerTown.getNation()) &&
                        TownWars.warsListForEachNation.containsKey(switchTown.getNation())) {
                    // Unless the attacker is some random person, both towns SHOULD have a nation
                    for (War war2 : TownWars.warsListForEachNation.get(openerTown.getNation())) {
                        if (war2.currentState != War.WarState.PREWAR &&
                                TownWars.warsListForEachNation.get(switchTown.getNation()).contains(war2)) {
                            // both nations are in a war.
                            event.setCancelled(false);
                            return;
                        }
                    }
                }
            }
        } catch (NotRegisteredException ignored) {
        }
    }

    // Use highest to override towny...
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamagePlayerEvent(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            try {
                Town damagedPlayerTown = towny.getDataSource().getResident(event.getEntity().getName()).getTown();
                Town damagerTown = towny.getDataSource().getResident(event.getDamager().getName()).getTown();

                // The player that was damaged has his town under a siege
                War victimDefendingWar = TownWars.townsUnderSiege.get(damagedPlayerTown);
                // The player that attacked has his town under a siege
                War damagerDefendingWar = TownWars.townsUnderSiege.get(damagerTown);

                if (damagerDefendingWar != null && damagerDefendingWar.currentState != War.WarState.PREWAR &&
                        damagerDefendingWar.defenders == damagerTown && damagerDefendingWar.attackers == damagedPlayerTown) {
                    event.setCancelled(false);
                    damagerDefendingWar.lastDamageTakenByAttackersTick = damagerDefendingWar.tick;
                    return;
                }

                if (victimDefendingWar != null && victimDefendingWar.currentState != War.WarState.PREWAR
                        && victimDefendingWar.attackers == damagerTown && victimDefendingWar.defenders == damagedPlayerTown) {
                    event.setCancelled(false);
                    return;
                }

                if (damagerTown.hasNation() && damagedPlayerTown.hasNation()) {
                    Nation damagerNation = damagerTown.getNation();
                    Nation victimNation = damagedPlayerTown.getNation();

                    if (!TownWars.warsListForEachNation.containsKey(damagerTown.getNation())) return;

                    // Are both nations in at least one war?
                    // Okay, both players are in a war, but are they the same war?
                    for (War war2 : TownWars.warsListForEachNation.get(damagerTown.getNation())) {
                        // Prewar stops players from attacking each other anyways
                        if (war2.currentState == War.WarState.PREWAR) continue;

                        if (war2.nationsAttacking.contains(damagerNation)) {
                            // An attacker attacked a defender
                            if (war2.nationsAttacking.contains(victimNation)) {
                                event.setCancelled(true);
                                return;
                            }

                            event.setCancelled(false);

                        } else if (war2.nationsDefending.contains(damagerNation)) {
                            // A defender attacked an attacker
                            if (war2.nationsDefending.contains(victimNation)) {
                                event.setCancelled(true);
                                return;
                            }

                            event.setCancelled(false);
                            war2.lastDamageTakenByAttackersTick = war2.tick;
                        } else {
                            // It is some random person attacking
                            event.setCancelled(true);
                        }
                    }
                }
            } catch (NotRegisteredException ignored) {
                // Okay, at least one player does not have a town then
                try {
                    boolean victimHasTown = towny.getDataSource().getResident(event.getEntity().getName()).hasTown();
                    boolean attackerHasTown = towny.getDataSource().getResident(event.getDamager().getName()).hasTown();

                    // They are just two random people fighting.
                    if (!victimHasTown && !attackerHasTown) return;

                    Town singleTown;

                    if (victimHasTown) {
                        singleTown = towny.getDataSource().getResident(event.getEntity().getName()).getTown();
                    } else {
                        singleTown = towny.getDataSource().getResident(event.getDamager().getName()).getTown();
                    }

                    if (WarUtility.isTownInWar(singleTown)) {
                        event.setCancelled(true);
                    }

                } catch (NotRegisteredException exception) {
                    exception.printStackTrace();
                }
            }
        }

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKill(PlayerDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            try {
                Resident deadPlayer = towny.getDataSource().getResident(event.getEntity().getName());
                Resident killer = towny.getDataSource().getResident(event.getEntity().getKiller().getName());

                Town deadPlayerTown = deadPlayer.getTown();
                Town killerTown = killer.getTown();

                War defenderIsDeadWar = TownWars.townsUnderSiege.get(deadPlayerTown);
                War attackerIsDeadWar = TownWars.townsUnderSiege.get(killerTown);

                // Okay, so this is a town war and the defender IS a defender in at least ONE war
                if (defenderIsDeadWar != null && !defenderIsDeadWar.isNationWar) {
                    if (defenderIsDeadWar.attackers == killerTown && defenderIsDeadWar.defenders == deadPlayerTown) {
                        defenderIsDeadWar.defenderIsDead(deadPlayer, event);
                    } else if (defenderIsDeadWar.attackers == deadPlayerTown && defenderIsDeadWar.defenders == killerTown) {
                        defenderIsDeadWar.attackerIsDead(deadPlayer, event);
                    }
                }

                if (attackerIsDeadWar != null && !attackerIsDeadWar.isNationWar) {
                    if (attackerIsDeadWar.attackers == killerTown && attackerIsDeadWar.defenders == deadPlayerTown) {
                        attackerIsDeadWar.defenderIsDead(deadPlayer, event);
                    } else if (attackerIsDeadWar.attackers == deadPlayerTown && attackerIsDeadWar.defenders == killerTown) {
                        attackerIsDeadWar.attackerIsDead(deadPlayer, event);
                    }
                }

                // So this is a nation war and both nations are in at least one nation war
                if (killerTown.hasNation() && deadPlayerTown.hasNation() &&
                        TownWars.warsListForEachNation.containsKey(killerTown.getNation()) &&
                        TownWars.warsListForEachNation.containsKey(deadPlayerTown.getNation())) {

                    for (War war : TownWars.warsListForEachNation.get(killerTown.getNation())) {
                        // We checked for a town war above, and it is false, so just continue
                        if (!war.isNationWar) continue;

                        if (war.nationsAttacking.contains(killerTown.getNation())) {
                            war.defenderIsDead(deadPlayer, event);
                        }

                        if (war.nationsDefending.contains(killerTown.getNation())) {
                            war.attackerIsDead(deadPlayer, event);
                        }
                    }
                }
            } catch (NotRegisteredException e) {
                e.printStackTrace();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerRespawnEvent(PlayerRespawnEvent event) {
        for (War war : TownWars.currentWars) {
            if (war.playerLocationsWhenWarBegan.containsKey(event.getPlayer())) {
                event.setRespawnLocation(war.playerLocationsWhenWarBegan.get(event.getPlayer()));
            }
        }
    }


    // Give players bossbars if they need it
    @EventHandler
    public void playerJoinEvent(PlayerJoinEvent event) {
        // Double check that player isn't glowing
        event.getPlayer().setGlowing(false);

        for (War war : TownWars.currentWars) {
            if (WarManager.isPartOfWar(event.getPlayer(), war)) {
                war.playerJoinListener(event.getPlayer());
                war.warBossbar.sendPlayerBossBarIfInWar(event.getPlayer());
                war.makeAttackersGlow();
            }
        }
    }

    // Make player not glow
    @EventHandler
    public void playerQuitEvent(PlayerQuitEvent event) {
        event.getPlayer().setGlowing(false);
    }

    @EventHandler
    public void preWarNeutralEvent(NationToggleNeutralEvent event) {

    }
}
