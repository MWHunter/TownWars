package org.abyssmc.townwars;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

// TODO: Force players to enemy all enemies (nation wars)
// TODO: Add bstats
// TODO: Limit wars players can enter
// TODO: Cooldown after losing a war
// TODO: Allow breaking chests? (should we?) (don't drop items)
// TODO: Require some Towny permission for starting a war.
// TODO: Confirm cost to join wars
// TODO: Long term, add ability to disable economy part entirely
public final class TownWars extends JavaPlugin {
    public static HashSet<War> currentWars = new HashSet<>();
    public static HashMap<Town, War> townsUnderSiege = new HashMap<>();
    public static Plugin plugin;
    public static HashMap<Nation, HashSet<War>> nationCurrentNationWars = new HashMap<>();
    public static HashSet<Material> allowedSwitches = new HashSet<>();
    public static HashSet<Material> disallowedBlocksBroken = new HashSet<>();

    private static BukkitAudiences adventure;

    @Override
    public void onEnable() {
        plugin = this;
        adventure = BukkitAudiences.create(this);

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
                War war = new War(file);
                WarManager.setupWar(war, false);
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

        if (adventure != null) {
            adventure.close();
            adventure = null;
        }
    }

    public void tickWars() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (War war : currentWars) {
                war.tick();
            }
        }, 0, 1);
    }

    public static @NonNull BukkitAudiences adventure() {
        if (adventure == null) {
            throw new IllegalStateException("Tried to access Adventure when the plugin was disabled!");
        }
        return adventure;
    }
}
