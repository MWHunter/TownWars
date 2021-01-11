package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.object.Resident;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

public class TownWarBossBar {
    War war;
    net.kyori.adventure.bossbar.BossBar attackersBossBar = net.kyori.adventure.bossbar.BossBar.bossBar(Component.text("secret war bossbar :)"),
            0, net.kyori.adventure.bossbar.BossBar.Color.PINK, net.kyori.adventure.bossbar.BossBar.Overlay.NOTCHED_10);
    net.kyori.adventure.bossbar.BossBar defendersBossBar = net.kyori.adventure.bossbar.BossBar.bossBar(Component.text("secret war bossbar :)"),
            0, net.kyori.adventure.bossbar.BossBar.Color.PINK, net.kyori.adventure.bossbar.BossBar.Overlay.NOTCHED_10);

    public TownWarBossBar(War war) {
        this.war = war;
    }

    public void removeEveryoneBossBars() {
        for (Resident resident : war.attackers.getResidents()) {
            if (resident.getPlayer() == null) continue;
            TownWars.adventure().player(resident.getPlayer()).hideBossBar(attackersBossBar);
        }

        for (Resident resident : war.defenders.getResidents()) {
            if (resident.getPlayer() == null) continue;
            TownWars.adventure().player(resident.getPlayer()).hideBossBar(defendersBossBar);
        }
    }

    public void sendEveryoneBossBars() {
        for (Resident resident : war.attackers.getResidents()) {
            if (resident.getPlayer() == null) continue;
            TownWars.adventure().player(resident.getPlayer()).showBossBar(attackersBossBar);
        }

        for (Resident resident : war.defenders.getResidents()) {
            if (resident.getPlayer() == null) continue;
            TownWars.adventure().player(resident.getPlayer()).showBossBar(defendersBossBar);
        }
    }

    public void sendPlayerBossBar(Player player) {
        for (Resident resident : war.attackers.getResidents()) {
            if (resident.getPlayer() == player) {
                TownWars.adventure().player(resident.getPlayer()).showBossBar(attackersBossBar);
                return;
            }
        }

        for (Resident resident : war.defenders.getResidents()) {
            if (resident.getPlayer() == player) {
                TownWars.adventure().player(resident.getPlayer()).showBossBar(defendersBossBar);
                return;
            }
        }
    }

    public void updateBossBar() {
        // TODO: How to do this with nation wars?
        // TODO: Separate bars for attackers and defenders
        // TODO: Add this stuff to the language config
        // I prefer sending time left in chat so more valuable info isn't lost.

        switch (war.currentState) {
            case CAPTURING:
                attackersBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.GREEN);
                attackersBossBar.name(LocaleReader.ATTACKING_BOSS_BAR_CAPTURING_MESSAGE);
                attackersBossBar.progress(Math.max(0, Math.min(1, (float) war.ticksOccupiedWithoutCombat / ConfigHandler.ticksToCaptureTown)));

                defendersBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.GREEN);
                defendersBossBar.name(LocaleReader.DEFENDING_BOSS_BAR_CAPTURING_MESSAGE);
                defendersBossBar.progress(Math.max(0, Math.min(1, (float) war.ticksOccupiedWithoutCombat / ConfigHandler.ticksToCaptureTown)));

                break;
            case OCCUPIED_IN_COMBAT:
                attackersBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.YELLOW);
                attackersBossBar.name(LocaleReader.ATTACKING_BOSS_BAR_OCCUPIED_IN_COMBAT_MESSAGE);
                attackersBossBar.progress(Math.max(0, Math.min(1, 1 - (float) Math.abs(war.tick - war.lastDamageTakenByAttackersTick) / ConfigHandler.ticksUntilNoLongerInCombat)));

                defendersBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.YELLOW);
                defendersBossBar.name(LocaleReader.DEFENDING_BOSS_BAR_OCCUPIED_IN_COMBAT_MESSAGE);
                defendersBossBar.progress(Math.max(0, Math.min(1, 1 - (float) Math.abs(war.tick - war.lastDamageTakenByAttackersTick) / ConfigHandler.ticksUntilNoLongerInCombat)));

                break;
            case ATTACKERS_MISSING:
                attackersBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.RED);
                attackersBossBar.name(LocaleReader.ATTACKING_BOSS_BAR_ATTACKERS_MISSING_MESSAGE);
                attackersBossBar.progress(Math.max(0, Math.min(1, 1 - (float) war.ticksWithoutAttackersOccupying / ConfigHandler.ticksWithoutAttackersOccupyingUntilDefendersWin)));

                defendersBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.RED);
                defendersBossBar.name(LocaleReader.DEFENDING_BOSS_BAR_ATTACKERS_MISSING_MESSAGE);
                defendersBossBar.progress(Math.max(0, Math.min(1, 1 - (float) war.ticksWithoutAttackersOccupying / ConfigHandler.ticksWithoutAttackersOccupyingUntilDefendersWin)));

                break;
            case PREWAR:
                attackersBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.WHITE);
                attackersBossBar.name(LocaleReader.ATTACKING_BOSS_BAR_PREWAR_MESSAGE);
                attackersBossBar.progress(Math.max(0, Math.min(1, 1 - (float) war.tick / ConfigHandler.ticksBeforeWarBegins)));

                defendersBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.WHITE);
                defendersBossBar.name(LocaleReader.DEFENDING_BOSS_BAR_PREWAR_MESSAGE);
                defendersBossBar.progress(Math.max(0, Math.min(1, 1 - (float) war.tick / ConfigHandler.ticksBeforeWarBegins)));

                break;
        }
    }
}
