package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.EconomyException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;

import static org.abyssmc.townwars.WarCommand.towny;

// This class plays with War objects.
public class WarManager {

    public static boolean createWar(Town attackers, Town defenders, Location warFlag, Player player) {

        if (attackers.hasNation() && defenders.hasNation()) {
            try {
                if (!attackers.getNation().hasEnemy(defenders.getNation())) {
                    player.sendMessage(ChatColor.RED + "You must become enemies before attacking a nation");
                    player.sendMessage(ChatColor.RED + "Do this by running /nation enemy add " + defenders.getNation().getName());
                    return false;
                }

                if (attackers.getNation().isNeutral()) {
                    player.sendMessage(ChatColor.RED + "You cannot attack as a neutral nation");
                    return false;
                }

                if (defenders.getNation().isNeutral()) {
                    player.sendMessage(ChatColor.RED + "You cannot attack a neutral nation");
                    return false;
                }
            } catch (NotRegisteredException e) {
                e.printStackTrace();
                player.sendMessage(ChatColor.RED + "An error occurred.  Contact an admin.");
                return false;
            }
        }


        if (attackers == defenders) {
            player.sendMessage(ChatColor.RED + "You can't go to war against yourself");
            return false;
        }

        if (attackers.isAlliedWith(defenders)) {
            player.sendMessage(ChatColor.RED + "You cannot attack your allies!");
            return false;
        }

        for (War war : TownWars.currentWars) {
            if (war.attackers == attackers && war.defenders == defenders || war.defenders == attackers && war.attackers == defenders) {
                player.sendMessage(ChatColor.RED + "You are already at war with this town!");
                return false;
            }
        }

        try {
            if (!attackers.getAccount().canPayFromHoldings(5000)) {
                player.sendMessage(ChatColor.RED + "You cannot pay the $5000 from your town's account required to start a war");
                return false;
            }

            attackers.getAccount().withdraw(5000, "War against " + defenders.getName());
        } catch (EconomyException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "An error has occurred while withdrawing from your town balance");
            return false;
        }

        War war = new War(attackers, defenders, warFlag);
        TownWars.currentWars.add(war);

        boolean inventoryHasSpace = false;
        for (ItemStack stack : player.getInventory()) {
            if (stack == null) {
                inventoryHasSpace = true;
                break;
            }
        }

        if (inventoryHasSpace) {
            player.getInventory().addItem(TownWars.warFlag);
        } else {
            player.getLocation().getWorld().dropItemNaturally(player.getLocation(), TownWars.warFlag);
            player.sendMessage(ChatColor.GREEN + "Your inventory is full so the war banner was dropped");
        }

        war.messageDefenders(ChatColor.RED + "You are now at war with " + attackers.getName());
        war.messageAttackers(ChatColor.RED + "You are now at war with " + defenders.getName());

        attackers.setAdminEnabledPVP(true);
        defenders.setAdminEnabledPVP(true);


        return true;
    }

    public static void endDiplomatically(War targetWar, Player player) {
        Town playerTown;

        // This shouldn't even be possible!
        try {
            playerTown = towny.getDataSource().getResident(player.getName()).getTown();
        } catch (NotRegisteredException e) {
            player.sendMessage(ChatColor.RED + "You must be a part of a town to go to war");
            return;
        }

        if (targetWar.attackers == playerTown) {
            targetWar.attackerWantsPeace = true;
        } else {
            targetWar.defenderWantsPeace = true;
        }

        // TODO: Make this configurable for time between request notifications
        if (!targetWar.attackerWantsPeace || !targetWar.defenderWantsPeace) {
            if (Instant.now().getEpochSecond() - targetWar.lastPeaceRequestEpoch < 30) {
                player.sendMessage(ChatColor.RED + "You can send another peace request in " + (Instant.now().getEpochSecond() - targetWar.lastPeaceRequestEpoch) + " seconds");
                return;
            }

            player.sendMessage(ChatColor.AQUA + "Peace request sent");
            sendMessageToTown(targetWar.attackers == playerTown ? targetWar.defenders : targetWar.attackers,
                    ChatColor.AQUA + (targetWar.attackers == playerTown ?
                            targetWar.attackers.getName() : targetWar.defenders.getName()) + " wants peace");
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
            player.sendMessage(ChatColor.RED + "You must be a part of a town to go to war");
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

        try {
            war.defenders.getAccount().deposit(5000, "Winning war as defenders");
            sendMessageToTown(war.attackers, ChatColor.AQUA + "You lost the war!");
            sendMessageToTown(war.defenders, ChatColor.AQUA + "Your town won $5000 for winning the war");
        } catch (EconomyException e) {
            e.printStackTrace();
        }
    }

    public static void defendersLoseWar(War war) {
        TownWars.currentWars.remove(war);

        // Set the defending town as conquered
        if (war.attackers.hasNation()) {
            try {
                war.defenders.setNation(war.attackers.getNation());
                war.defenders.setConquered(true);
                war.defenders.setConqueredDays(7);
            } catch (AlreadyRegisteredException | NotRegisteredException e) {
                e.printStackTrace();
            }
        }

        int defenderBlocks = war.defenders.getTotalBlocks();
        int lost = (int) Math.ceil(defenderBlocks * 0.1);

        if (lost < 10) {
            lost = 10;
        }

        if (war.defenders.getTotalBlocks() < lost) {
            lost = war.defenders.getBonusBlocks();
            war.attackers.setBonusBlocks(war.attackers.getBonusBlocks() + lost);

            // This should NEVER be possible with this plugin alone
            if (lost < 0) {
                lost = 0;
            }

            sendMessageToTown(war.attackers, ChatColor.AQUA + "Your town has won " + lost + " blocks for deleting " + war.defenders.getName());
            sendMessageToTown(war.defenders, ChatColor.AQUA + "Your town has fallen into ruin due to losing the war!");

            towny.getDataSource().deleteTown(war.defenders);
        } else {
            war.defenders.setBonusBlocks(war.defenders.getBonusBlocks() - lost);

            sendMessageToTown(war.attackers, ChatColor.AQUA + "Your town has won " + lost + " blocks for winning the war against " + war.defenders.getName());
            sendMessageToTown(war.defenders, ChatColor.AQUA + "Your town has lost " + lost + " bonus claims for losing the war against" + war.attackers.getName());
        }
    }

    public static void returnToPeace(War war) {
        TownWars.currentWars.remove(war);

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

    public static boolean isTownInWar(Town town) {
        return countWarsForTown(town) > 0;
    }

    public static void sendMessageToTown(Town town, String message) {
        for (Resident resident : town.getResidents()) {
            Player player = Bukkit.getPlayer(resident.getName());

            if (player != null) {
                player.sendMessage(message);
            }
        }
    }
}
