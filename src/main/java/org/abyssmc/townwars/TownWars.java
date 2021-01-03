package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.compress.utils.FileNameUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

// Main class!  Doesn't look bad yet.
// TODO: Allow breaking/placing blocks in enemy land
public final class TownWars extends JavaPlugin {
    public static HashSet<War> currentWars = new HashSet<>();
    public static HashMap<Town, War> townsUnderSiege = new HashMap<>();
    public static Plugin plugin;

    // caching isn't pretty but it is fast.
    public static HashMap<Nation, HashSet<War>> nationCurrentNationWars = new HashMap<>();

    public static HashSet<Material> allowedSwitches = new HashSet<>();
    ConfigHandler configHandler = new ConfigHandler(this);

    @Override
    public void onEnable() {
        plugin = this;

        // Plugin startup logic
        saveDefaultConfig();
        LocaleReader.reload(this);
        getCommand("war").setExecutor(new WarCommand(this));
        getServer().getPluginManager().registerEvents(new Events(this), this);
        tickWars();

        File warFolder = new File(getDataFolder() + File.separator + "wars");
        if (!warFolder.exists()) warFolder.mkdirs();

        for (File file : warFolder.listFiles()) {
            try {
                War war = new War(null, null, false);
                war.load(file);
                WarManager.setupWar(war, null);
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

    public ConfigHandler getConfigHandler() {
        return configHandler;
    }

    public void tickWars() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (War war : currentWars) {
                war.tick();
            }
        }, 0, 1);
    }
}
