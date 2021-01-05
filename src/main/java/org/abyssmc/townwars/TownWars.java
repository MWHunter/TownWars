package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

// TODO: Force players to enemy all enemies
// TODO: Add bstats
public final class TownWars extends JavaPlugin {
    public static HashSet<War> currentWars = new HashSet<>();
    public static HashMap<Town, War> townsUnderSiege = new HashMap<>();
    public static Plugin plugin;
    public static HashMap<Nation, HashSet<War>> nationCurrentNationWars = new HashMap<>();
    public static HashSet<Material> allowedSwitches = new HashSet<>();

    @Override
    public void onEnable() {
        plugin = this;

        // Plugin startup logic
        saveDefaultConfig();
        ConfigHandler.plugin = this;
        ConfigHandler.config = getConfig();
        ConfigHandler.reload();
        LocaleReader.reload();

        getCommand("war").setExecutor(new WarCommand());
        getServer().getPluginManager().registerEvents(new Events(), this);
        tickWars();

        File warFolder = new File(getDataFolder() + File.separator + "wars");
        if (!warFolder.exists()) warFolder.mkdirs();

        for (File file : warFolder.listFiles()) {
            try {
                War war = new War(null, null, false);
                war.load(file);
                WarManager.setupWar(war, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDisable() {
        // save wars
        for (War war : currentWars) {
            war.save(false);
        }
    }

    public void tickWars() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (War war : currentWars) {
                war.tick();
            }
        }, 0, 1);
    }
}
