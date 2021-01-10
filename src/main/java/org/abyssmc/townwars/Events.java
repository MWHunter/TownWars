package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.actions.TownyBuildEvent;
import com.palmergames.bukkit.towny.event.actions.TownyDestroyEvent;
import com.palmergames.bukkit.towny.event.actions.TownyExplodingBlocksEvent;
import com.palmergames.bukkit.towny.event.actions.TownySwitchEvent;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.regen.TownyRegenAPI;
import org.bukkit.Bukkit;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class Events implements Listener {
    static HashSet<UUID> cancelNextBlockBreakDrop = new HashSet<>();
    static HashMap<UUID, War> logNextBlockPlace = new HashMap<>();
    TownyAPI towny = TownyAPI.getInstance();

    // TODO: Make defender actions also be rolled back?
    // TODO: Individual configs for allowing placing/breaking containers for attackers/defenders
    // default off to stop attackers from breaking all the chests that defenders need for defense

    // Towny uses event priority high for some reason
    // Using event priority higher allows us interact with towny's block break event.
    // Make it so players can't dupe by breaking blocks during a war
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBukkitBlockBreakEvent(BlockBreakEvent event) {
        if (cancelNextBlockBreakDrop.contains(event.getPlayer().getUniqueId())) {
            cancelNextBlockBreakDrop.remove(event.getPlayer().getUniqueId());
            event.setDropItems(false);
        }
    }

    @EventHandler
    public void onBlockBreakEvent(TownyDestroyEvent event) {
        //if (!event.isCancelled()) return;
        try {
            if (event.getTownBlock() == null) return;
            Town destroyTown = event.getTownBlock().getTown();
            if (TownWars.townsUnderSiege.containsKey(destroyTown)) {
                Town breakerTown = towny.getDataSource().getResident(event.getPlayer().getName()).getTown();
                War war = TownWars.townsUnderSiege.get(destroyTown);

                // Saving container data is a mess in spigot.  It relies on NMS.
                // I doubt players will build walls with furnaces to stop attackers
                // nvm, they probably will
                // TODO: Fix this before players start building walls with furnaces to keep attackers out
                if (event.getBlock().getState() instanceof Container) return;

                if (war != null && war.currentState != War.WarState.PREWAR && (war.attackers == breakerTown ||
                        war.defenders == breakerTown)) {
                    event.setCancelled(false);
                    cancelNextBlockBreakDrop.add(event.getPlayer().getUniqueId());
                    war.restoreBlock(event.getBlock().getLocation(), event.getBlock().getBlockData());

                    return;
                }

                if (breakerTown.hasNation() && destroyTown.hasNation() &&
                        TownWars.nationCurrentNationWars.containsKey(breakerTown.getNation()) &&
                        TownWars.nationCurrentNationWars.containsKey(destroyTown.getNation())) {
                    // Unless the attacker is some random person, both towns SHOULD have a nation
                    for (War war2 : TownWars.nationCurrentNationWars.get(breakerTown.getNation())) {
                        if (war2.currentState != War.WarState.PREWAR &&
                                TownWars.nationCurrentNationWars.get(destroyTown.getNation()).contains(war2)) {
                            // both nations are in a war.
                            event.setCancelled(false);
                            cancelNextBlockBreakDrop.add(event.getPlayer().getUniqueId());
                            war2.restoreBlock(event.getBlock().getLocation(), event.getBlock().getBlockData());

                            return;
                        }
                    }
                }
            }
        } catch (NotRegisteredException ignored) {
        }
    }

    @EventHandler
    public void onExplosionDamagingBlocks(TownyExplodingBlocksEvent event) {
        int count = 0;

        //List<Block> alreadyAllowed = event.getTownyFilteredBlockList();
        for (Block block : event.getVanillaBlockList()) {
            if (!TownWars.townsUnderSiege.containsKey(towny.getTown(block.getLocation()))) continue;
            // Don't allow blowing up chests, defenders should have access to them!
            // TODO: Make this a config
            if (block.getState() instanceof Container) continue;

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
            war.restoreBlock(event.getBlock().getLocation(), event.getBlockReplacedState().getBlockData());
        }
    }

    @EventHandler
    public void onBlockPlaceEvent(TownyBuildEvent event) {
        try {
            if (event.getTownBlock() == null) return;
            Town placeTown = event.getTownBlock().getTown();
            if (TownWars.townsUnderSiege.containsKey(placeTown)) {
                Town openerTown = towny.getDataSource().getResident(event.getPlayer().getName()).getTown();
                War war = TownWars.townsUnderSiege.get(placeTown);

                // Town wars
                if (war != null && war.currentState != War.WarState.PREWAR && (war.attackers == openerTown
                        || war.defenders == openerTown)) {
                    if (event.getBlock().isLiquid() || event.getBlock().getState() instanceof Container) return;

                    event.setCancelled(false);
                    logNextBlockPlace.put(event.getPlayer().getUniqueId(), war);

                    return;
                }

                // Nation wars
                if (openerTown.hasNation() && placeTown.hasNation() &&
                        TownWars.nationCurrentNationWars.containsKey(openerTown.getNation()) &&
                        TownWars.nationCurrentNationWars.containsKey(placeTown.getNation())) {
                    // Unless the attacker is some random person, both towns SHOULD have a nation
                    for (War war2 : TownWars.nationCurrentNationWars.get(openerTown.getNation())) {
                        if (war2.currentState != War.WarState.PREWAR &&
                                TownWars.nationCurrentNationWars.get(placeTown.getNation()).contains(war2)) {
                            // both nations are in a war.
                            // TODO: Make this a config
                            if (event.getBlock().isLiquid() || event.getBlock().getState() instanceof Container) return;

                            event.setCancelled(false);
                            logNextBlockPlace.put(event.getPlayer().getUniqueId(), war2);

                            return;
                        }
                    }
                }
            }
        } catch (NotRegisteredException ignored) {
        }
    }

    @EventHandler
    public void onSwitchUse(TownySwitchEvent event) {
        if (!event.isCancelled()) return;
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
                        TownWars.nationCurrentNationWars.containsKey(openerTown.getNation()) &&
                        TownWars.nationCurrentNationWars.containsKey(switchTown.getNation())) {
                    // Unless the attacker is some random person, both towns SHOULD have a nation
                    for (War war2 : TownWars.nationCurrentNationWars.get(openerTown.getNation())) {
                        if (war2.currentState != War.WarState.PREWAR &&
                                TownWars.nationCurrentNationWars.get(switchTown.getNation()).contains(war2)) {
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

                // This is the most likely case, nation wars are rare
                War warDefenderDamaged = TownWars.townsUnderSiege.get(damagedPlayerTown);
                War warAttackerDamaged = TownWars.townsUnderSiege.get(damagerTown);

                // war attacker damaged
                if (warAttackerDamaged != null && warAttackerDamaged.currentState != War.WarState.PREWAR &&
                        warAttackerDamaged.defenders == damagerTown) {
                    event.setCancelled(false);
                    warAttackerDamaged.lastDamageTakenByAttackersTick = warAttackerDamaged.tick;
                    return;
                }

                // war defender damaged
                if (warDefenderDamaged != null && warDefenderDamaged.currentState != War.WarState.PREWAR
                        && warDefenderDamaged.attackers == damagerTown) {
                    event.setCancelled(false);
                    return;
                }

                if (damagerTown.hasNation() && damagedPlayerTown.hasNation() &&
                        TownWars.nationCurrentNationWars.containsKey(damagerTown.getNation()) &&
                        TownWars.nationCurrentNationWars.containsKey(damagedPlayerTown.getNation())) {
                    // Unless the attacker is some random person, both towns SHOULD have a nation
                    for (War war2 : TownWars.nationCurrentNationWars.get(damagerTown.getNation())) {
                        if (war2.currentState != War.WarState.PREWAR &&
                                TownWars.nationCurrentNationWars.get(damagedPlayerTown.getNation()).contains(war2)) {

                            // both nations are in a war.
                            event.setCancelled(false);

                            if (war2.nationsAttacking.contains(damagedPlayerTown.getNation())) {
                                war2.lastDamageTakenByAttackersTick = war2.tick;
                            }

                            return;
                        }
                    }
                }
            } catch (NotRegisteredException ignored) {
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

                War deadPlayerIsDefenderWar = TownWars.townsUnderSiege.get(deadPlayerTown);
                War attackerIsDeadPlayerWar = TownWars.townsUnderSiege.get(killerTown);

                // This is a mess.  A kill could count for two (maybe three?) wars but it's better than
                // letting wars stop points from being scored.
                // I can't do another about this without preventing people from joining multiple wars at once.
                // But that itself could allow exploits in stopping people from joining a nation war
                // by having another nation declare war on their nation.  It's a mess.
                if (deadPlayerIsDefenderWar != null && !deadPlayerIsDefenderWar.isNationWar) {
                    if (deadPlayerIsDefenderWar.attackers == killerTown && deadPlayerIsDefenderWar.defenders == deadPlayerTown) {
                        deadPlayerIsDefenderWar.attackerIsDead(deadPlayer);
                    } else if (deadPlayerIsDefenderWar.attackers == deadPlayerTown && deadPlayerIsDefenderWar.defenders == killerTown) {
                        deadPlayerIsDefenderWar.defenderHasDied(deadPlayer);
                    }
                }

                if (attackerIsDeadPlayerWar != null && !attackerIsDeadPlayerWar.isNationWar) {
                    if (attackerIsDeadPlayerWar.attackers == killerTown && attackerIsDeadPlayerWar.defenders == deadPlayerTown) {
                        attackerIsDeadPlayerWar.attackerIsDead(deadPlayer);
                    } else if (attackerIsDeadPlayerWar.attackers == deadPlayerTown && attackerIsDeadPlayerWar.defenders == killerTown) {
                        attackerIsDeadPlayerWar.defenderHasDied(deadPlayer);
                    }
                }

                if (killerTown.hasNation() && deadPlayerTown.hasNation() &&
                        TownWars.nationCurrentNationWars.containsKey(killerTown.getNation()) &&
                        TownWars.nationCurrentNationWars.containsKey(deadPlayerTown.getNation())) {
                    // Unless the attacker is some random person, both towns SHOULD have a nation
                    for (War war : TownWars.nationCurrentNationWars.get(killerTown.getNation())) {
                        if (!war.isNationWar) continue;
                        if (TownWars.nationCurrentNationWars.get(deadPlayerTown.getNation()).contains(war)) {
                            if (war.nationsAttacking.contains(killerTown.getNation())) {
                                war.attackerIsDead(deadPlayer);
                            }

                            if (war.nationsDefending.contains(killerTown.getNation())) {
                                war.defenderHasDied(deadPlayer);
                            }
                        }
                    }
                }
            } catch (NotRegisteredException e) {
                e.printStackTrace();
            }
        }
    }

    // Give players bossbars if they need it
    @EventHandler
    public void playerJoinEvent(PlayerJoinEvent event) {
        for (War war : TownWars.currentWars) {
            if (WarManager.isPartOfWar(event.getPlayer(), war)) {
                TownWars.adventure().player(event.getPlayer()).showBossBar(war.warBossBar);
            }
        }
    }
}
