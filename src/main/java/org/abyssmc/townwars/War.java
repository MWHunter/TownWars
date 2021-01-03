package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.compress.utils.FileNameUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

// This is the data only class for each war.
public class War {
    public boolean isNationWar;
    public Town attackers;
    public Town defenders;
    public boolean attackerWantsPeace;
    public boolean defenderWantsPeace;
    public Long startTimeEpoch;
    public Long lastPeaceRequestEpoch = 0L;
    public boolean isTimeLimit;
    public int timeLimit;
    public int killLimit;
    public int attackerKills = 0;
    public int defenderKills = 0;
    HashSet<Nation> nationsDefending = new HashSet<>();
    HashSet<Nation> nationsAttacking = new HashSet<>();
    int tick = 0;
    public UUID warUUID;

    public War(Town attackers, Town defenders, boolean isNationWar) {
        // This means we are loading from a saved war
        if (attackers == null && defenders == null) return;

        this.warUUID = UUID.randomUUID();
        this.attackers = attackers;
        this.defenders = defenders;
        this.isNationWar = isNationWar;

        startTimeEpoch = Instant.now().getEpochSecond();

        try {
            if (attackers.hasNation()) {
                nationsAttacking.add(attackers.getNation());
            }

            if (defenders.hasNation()) {
                nationsDefending.add(defenders.getNation());
            }
        } catch (NotRegisteredException e) {
            e.printStackTrace();
        }

        if (isNationWar) {
            isTimeLimit = ConfigHandler.isTimeLimitNationWar;
            timeLimit = ConfigHandler.timeLimitNationWar;
            killLimit = ConfigHandler.killLimitNationWar;
        } else {
            isTimeLimit = ConfigHandler.isTimeLimitTownWar;
            timeLimit = ConfigHandler.timeLimitTownWar;
            killLimit = ConfigHandler.killLimitTownWar;
        }
    }

    public void load(File warFile) {
        YamlConfiguration warConfiguration = YamlConfiguration.loadConfiguration(warFile);

        try {
            warUUID = UUID.fromString(FileNameUtils.getBaseName(warFile.getName()));
            isNationWar = warConfiguration.getBoolean("isNationWar");
            attackers = TownyAPI.getInstance().getDataSource().getTown(UUID.fromString(warConfiguration.getString("attackers")));
            defenders = TownyAPI.getInstance().getDataSource().getTown(UUID.fromString(warConfiguration.getString("defenders")));
            attackerWantsPeace = warConfiguration.getBoolean("attackerWantsPeace");
            defenderWantsPeace = warConfiguration.getBoolean("defenderWantsPeace");
            startTimeEpoch = warConfiguration.getLong("startTimeEpoch");
            lastPeaceRequestEpoch = warConfiguration.getLong("lastPeaceRequestEpoch");
            attackerKills = warConfiguration.getInt("attackerKills");
            defenderKills = warConfiguration.getInt("defenderKills");
        } catch (NotRegisteredException e) {
            e.printStackTrace();
        }
    }

    // If we are shutting down the server, do it sync to 100% be sure the save finishes
    public void save(boolean async) {
        File warFolder = new File(TownWars.plugin.getDataFolder() + File.separator + "wars");
        File warFile = new File(warFolder + File.separator + warUUID + ".yml");
        YamlConfiguration warConfiguration = YamlConfiguration.loadConfiguration(warFile);

        warConfiguration.set("isNationWar", isNationWar);
        warConfiguration.set("attackers", attackers.getUUID().toString());
        warConfiguration.set("defenders", defenders.getUUID().toString());
        warConfiguration.set("attackerWantsPeace", attackerWantsPeace);
        warConfiguration.set("defenderWantsPeace", defenderWantsPeace);
        warConfiguration.set("startTimeEpoch", startTimeEpoch);
        warConfiguration.set("lastPeaceRequestEpoch", lastPeaceRequestEpoch);
        warConfiguration.set("attackerKills", attackerKills);
        warConfiguration.set("defenderKills", defenderKills);

        if (!async) {
            try {
                warConfiguration.save(warFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(TownWars.plugin, () -> {
                try {
                    warConfiguration.save(warFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public void tick() {
        // save war info every minute, just in case the server crashes
        // This also saves everything immediately
        if (tick++ % 1200 == 0) save(true);

        if (isTimeLimit && tick > timeLimit) {
            endWarForcefully();
        }
    }

    public void checkIfDone() {
        if (attackerKills > killLimit) {
            endWarForcefully();
        }

        if (defenderKills > killLimit) {
            endWarForcefully();
        }
    }

    public void endWarForcefully() {
        messageAll("Ending war");
        if (attackerKills > defenderKills) {
            WarManager.defendersLoseWar(this);
        } else if (defenderKills <= attackerKills) {
            WarManager.attackersLoseWar(this);
        }
    }

    public void incrementAttackerKills() {
        messageAll("Attackers scored a kill");
        attackerKills++;
        checkIfDone();
    }

    public void incrementDefenderKills() {
        messageAll("Defenders scored a kill");
        defenderKills++;
        checkIfDone();
    }


    public void messageAll(String message) {
        messageAttackers(message);
        messageDefenders(message);
    }

    public void messageAttackers(String message) {
        if (isNationWar) {
            for (Nation nation : nationsAttacking) {
                for (Town town : nation.getTowns()) {
                    for (Resident resident : town.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        resident.getPlayer().sendMessage(message);
                    }
                }
            }
        } else {
            for (Resident resident : attackers.getResidents()) {
                if (resident.getPlayer() == null) continue;
                resident.getPlayer().sendMessage(message);
            }
        }

    }

    public void messageDefenders(String message) {
        if (isNationWar) {
            for (Nation nation : nationsDefending) {
                for (Town town : nation.getTowns()) {
                    for (Resident resident : town.getResidents()) {
                        if (resident.getPlayer() == null) continue;
                        resident.getPlayer().sendMessage(message);
                    }
                }
            }
        } else {
            for (Resident resident : defenders.getResidents()) {
                if (resident.getPlayer() == null) continue;
                resident.getPlayer().sendMessage(message);
            }
        }
    }
}
