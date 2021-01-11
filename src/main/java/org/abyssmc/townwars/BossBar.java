package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Player;

public class BossBar {
    War war;
    net.kyori.adventure.bossbar.BossBar warBossBar = net.kyori.adventure.bossbar.BossBar.bossBar(Component.text("secret war bossbar :)"),
            0, net.kyori.adventure.bossbar.BossBar.Color.PINK, net.kyori.adventure.bossbar.BossBar.Overlay.NOTCHED_10);

    public BossBar(War war) {
        this.war = war;
    }

    public void removeEveryoneBossBars() {
        if (war.isNationWar) {
            for (Nation nation : war.nationsAttacking) {
                for (Town town : nation.getTowns()) {
                    for (Resident resident : town.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        TownWars.adventure().player(resident.getPlayer()).hideBossBar(warBossBar);
                    }
                }
            }

            for (Nation nation : war.nationsDefending) {
                for (Town town : nation.getTowns()) {
                    for (Resident resident : town.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        TownWars.adventure().player(resident.getPlayer()).hideBossBar(warBossBar);
                    }
                }
            }

        } else {
            for (Resident resident : war.attackers.getResidents()) {
                if (resident.getPlayer() == null) continue;
                TownWars.adventure().player(resident.getPlayer()).hideBossBar(warBossBar);
            }

            for (Resident resident : war.defenders.getResidents()) {
                if (resident.getPlayer() == null) continue;
                TownWars.adventure().player(resident.getPlayer()).hideBossBar(warBossBar);
            }
        }
    }

    public void sendEveryoneBossBars() {
        if (war.isNationWar) {
            for (Nation nation : war.nationsAttacking) {
                for (Town town : nation.getTowns()) {
                    for (Resident resident : town.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        TownWars.adventure().player(resident.getPlayer()).showBossBar(warBossBar);
                    }
                }
            }

            for (Nation nation : war.nationsDefending) {
                for (Town town : nation.getTowns()) {
                    for (Resident resident : town.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        TownWars.adventure().player(resident.getPlayer()).showBossBar(warBossBar);
                    }
                }
            }

        } else {
            for (Resident resident : war.attackers.getResidents()) {
                if (resident.getPlayer() == null) continue;
                TownWars.adventure().player(resident.getPlayer()).showBossBar(warBossBar);
            }

            for (Resident resident : war.defenders.getResidents()) {
                if (resident.getPlayer() == null) continue;
                TownWars.adventure().player(resident.getPlayer()).showBossBar(warBossBar);
            }
        }
    }

    public void sendPlayerBossBar(Player player) {
        TownWars.adventure().player(player).showBossBar(warBossBar);
    }

    public void updateBossBar() {
        // TODO: How to do this with nation wars?
        // TODO: Separate bars for attackers and defenders
        // TODO: Add this stuff to the language config
        // I prefer sending time left in chat so more valuable info isn't lost.

        switch (war.currentState) {
            case CAPTURING:
                warBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.GREEN);
                warBossBar.name(Component.text().color(TextColor.color(0x34eb49)).append(Component.text("Capture in progress")));
                warBossBar.progress(Math.max(0, Math.min(1, (float) war.ticksOccupiedWithoutCombat / ConfigHandler.ticksToCaptureTown)));
                break;
            case OCCUPIED_IN_COMBAT:
                warBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.YELLOW);
                warBossBar.name(Component.text().color(TextColor.color(0xf7f719)).append(Component.text("Attackers in combat")));
                warBossBar.progress(Math.max(0, Math.min(1, 1 - (float) Math.abs(war.tick - war.lastDamageTakenByAttackersTick) / ConfigHandler.ticksUntilNoLongerInCombat)));
                break;
            case ATTACKERS_MISSING:
                warBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.RED);
                warBossBar.name(Component.text().color(TextColor.color(0xe01010)).append(Component.text("Attackers absent")));
                warBossBar.progress(Math.max(0, Math.min(1, 1 - (float) war.ticksWithoutAttackersOccupying / ConfigHandler.ticksWithoutAttackersOccupyingUntilDefendersWin)));
                break;
            case PREWAR:
                warBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.WHITE);
                warBossBar.name(Component.text().color(TextColor.color(0xe01010)).append(Component.text("War declared")));
                warBossBar.progress(Math.max(0, Math.min(1, 1 - (float) war.tick / ConfigHandler.ticksBeforeWarBegins)));
                break;
        }
    }
}
