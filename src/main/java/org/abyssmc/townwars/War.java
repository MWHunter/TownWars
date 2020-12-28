package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;

import java.time.Instant;

// This is the data only class for each war.
public class War {
    public Town attackers;
    public Town defenders;
    public boolean attackerWantsPeace;
    public boolean defenderWantsPeace;
    public Location warFlagLocation;
    public Long startTimeEpoch;
    public Long lastPeaceRequestEpoch = 0L;
    public int flagProgress = 0;
    // TODO: Set this back to 120 (10 minutes)
    public int flagDone = 5;
    int tick = 0;

    // War is won by scoring a hundred points points higher than the other team
    // Every second 1/4 point is given for
    public War(Town attackers, Town defenders, Location warFlagLocation) {
        this.attackers = attackers;
        this.defenders = defenders;
        this.warFlagLocation = warFlagLocation;
        startTimeEpoch = Instant.now().getEpochSecond();
    }

    public void tick() {
        if (++tick % 100 != 0) return;

        boolean defendersAtFlag = defendersHaveFlagControl();
        boolean attackersAtFlag = attackersHaveFlagControl();

        if (!attackersAtFlag) return;

        if (defendersAtFlag) {
            messageAll(ChatColor.AQUA + "The flag is contested!");
            return;
        }

        if (++flagProgress >= flagDone) {
            WarManager.defendersLoseWar(this);
            return;
        }

        messageAll(ChatColor.RED + defenders.getName() + " will be captured in " + (flagDone - flagProgress) + " more seconds");
    }

    public boolean attackersHaveFlagControl() {
        for (Resident attacker : attackers.getResidents()) {
            if (attacker.getPlayer() == null) continue;
            if (getMaxDistanceBetweenPoints(attacker.getPlayer().getLocation(), warFlagLocation) < 16) {
                return true;
            }
        }

        return false;
    }

    public boolean defendersHaveFlagControl() {
        for (Resident defender : defenders.getResidents()) {
            if (defender.getPlayer() == null) continue;
            if (getMaxDistanceBetweenPoints(defender.getPlayer().getLocation(), warFlagLocation) < 16) {
                return true;
            }
        }

        return false;
    }

    public double getMaxDistanceBetweenPoints(Location location1, Location location2) {
        double temp1 = Math.max(Math.abs(location1.getBlockX() - location2.getBlockX()), Math.abs(location1.getBlockZ() - location2.getBlockZ()));
        return Math.max(Math.abs(location1.getBlockY() - location2.getBlockY()), temp1);
    }

    public void messageAll(String message) {
        messageAttackers(message);
        messageDefenders(message);
    }

    public void messageAttackers(String message) {
        for (Resident resident : attackers.getResidents()) {
            if (resident.getPlayer() == null) continue;
            resident.getPlayer().sendMessage(message);
        }
    }

    public void messageDefenders(String message) {
        for (Resident resident : defenders.getResidents()) {
            if (resident.getPlayer() == null) continue;
            resident.getPlayer().sendMessage(message);
        }
    }
}
