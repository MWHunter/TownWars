package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;

public class NationWarBossBar extends TownWarBossBar {
    public NationWarBossBar(War war) {
        super(war);
    }

    @Override
    public void removeEveryoneBossBars() {
        for (Nation nation : war.nationsAttacking) {
            for (Resident resident : nation.getResidents()) {
                if (resident.getPlayer() == null) continue;
                TownWars.adventure().player(resident.getPlayer()).hideBossBar(attackersBossBar);
            }
        }

        for (Nation nation : war.nationsDefending) {
            for (Resident resident : nation.getResidents()) {
                if (resident.getPlayer() == null) continue;
                TownWars.adventure().player(resident.getPlayer()).hideBossBar(defendersBossBar);
            }
        }
    }

    @Override
    public void sendEveryoneBossBars() {
        for (Nation nation : war.nationsAttacking) {
            for (Resident resident : nation.getResidents()) {
                if (resident.getPlayer() == null) continue;
                TownWars.adventure().player(resident.getPlayer()).showBossBar(attackersBossBar);
            }
        }

        for (Nation nation : war.nationsDefending) {
            for (Resident resident : nation.getResidents()) {
                if (resident.getPlayer() == null) continue;
                TownWars.adventure().player(resident.getPlayer()).showBossBar(defendersBossBar);
            }
        }
    }
}
