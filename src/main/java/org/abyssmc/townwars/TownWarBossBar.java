package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.object.Resident;
import net.kyori.adventure.text.Component;
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
        for (Resident resident : war.getOnlineAttackers()) {
            TownWars.adventure().player(resident.getPlayer()).hideBossBar(attackersBossBar);
        }

        for (Resident resident : war.getOnlineDefenders()) {
            TownWars.adventure().player(resident.getPlayer()).hideBossBar(defendersBossBar);
        }
    }

    public void sendEveryoneBossBars() {
        for (Resident resident : war.getOnlineAttackers()) {
            TownWars.adventure().player(resident.getPlayer()).showBossBar(attackersBossBar);
        }

        for (Resident resident : war.getOnlineDefenders()) {
            TownWars.adventure().player(resident.getPlayer()).showBossBar(defendersBossBar);
        }
    }

    public void sendPlayerBossBarIfInWar(Player player) {
        for (Resident resident : war.getOnlineAttackers()) {
            if (resident.getPlayer() == player) {
                TownWars.adventure().player(resident.getPlayer()).showBossBar(attackersBossBar);
                return;
            }
        }

        for (Resident resident : war.getOnlineDefenders()) {
            if (resident.getPlayer() == player) {
                TownWars.adventure().player(resident.getPlayer()).showBossBar(defendersBossBar);
                return;
            }
        }
    }

    public void updateBossBar() {
        switch (war.currentState) {
            case WAR:
                attackersBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.GREEN);
                attackersBossBar.name(LegacyComponentSerializer.legacyAmpersand().deserialize(
                        LocaleReader.ATTACKING_BOSS_BAR_WAR_MESSAGE
                                .replace("{ATTACKER_LIVES}", String.valueOf(war.attackerLives))
                                .replace("{DEFENDER_LIVES}", String.valueOf(war.defenderLives))));
                attackersBossBar.progress(Math.max(0, Math.min(1, 1 - (war.tick - ConfigHandler.ticksBeforeWarBegins)
                        / ((float) ConfigHandler.tickLimitTownWar - ConfigHandler.ticksBeforeWarBegins))));

                defendersBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.GREEN);
                defendersBossBar.name(LegacyComponentSerializer.legacyAmpersand().deserialize(
                        LocaleReader.DEFENDING_BOSS_BAR_WAR_MESSAGE
                                .replace("{ATTACKER_LIVES}", String.valueOf(war.attackerLives))
                                .replace("{DEFENDER_LIVES}", String.valueOf(war.defenderLives))));
                defendersBossBar.progress(Math.max(0, Math.min(1, 1 - (war.tick - ConfigHandler.ticksBeforeWarBegins)
                        / ((float) ConfigHandler.tickLimitTownWar - ConfigHandler.ticksBeforeWarBegins))));

                break;
            case PREWAR:
                attackersBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.WHITE);
                attackersBossBar.name(LocaleReader.ATTACKING_BOSS_BAR_PREWAR_MESSAGE);
                attackersBossBar.progress(Math.max(0, Math.min(1, war.tick / (float) ConfigHandler.ticksBeforeWarBegins)));

                defendersBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.WHITE);
                defendersBossBar.name(LocaleReader.DEFENDING_BOSS_BAR_PREWAR_MESSAGE);
                defendersBossBar.progress(Math.max(0, Math.min(1, war.tick / (float) ConfigHandler.ticksBeforeWarBegins)));

                break;
        }
    }
}
