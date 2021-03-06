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

// TODO: Add bstats
// TODO: Limit wars players can enter
// TODO: Allow breaking chests? (should we?) (don't drop items)
// TODO: Long term, add ability to disable economy part entirely
public final class TownWars extends JavaPlugin {
    public static final HashSet<War> currentWars = new HashSet<>();
    public static HashMap<Town, War> townsUnderSiege = new HashMap<>();
    public static Plugin plugin;
    // These are the wars that a nation is in... it's just a cache to speed up events.
    public static HashMap<Nation, HashSet<War>> warsListForEachNation = new HashMap<>();
    public static HashSet<Material> allowedSwitches = new HashSet<>();
    public static HashSet<Material> disallowedBlocksBroken = new HashSet<>();
    public static HashSet<Material> disallowedRTPBlocks = new HashSet<>();

    private static BukkitAudiences adventure;

    public static @NonNull BukkitAudiences adventure() {
        if (adventure == null) {
            throw new IllegalStateException("Tried to access Adventure when the plugin was disabled!");
        }
        return adventure;
    }

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
        TownWarCooldownManager.reload();
        NationWarCooldownManager.reload();
        Events.clearLists();

        getCommand("war").setExecutor(new CommandHandler());
        getServer().getPluginManager().registerEvents(new Events(), this);
        getServer().getPluginManager().registerEvents(new TownWarCooldownManager(), this);
        getServer().getPluginManager().registerEvents(new NationWarCooldownManager(), this);
        tickWars();

        File warFolder = new File(getDataFolder() + File.separator + "wars");
        if (!warFolder.exists()) warFolder.mkdirs();

        for (File file : warFolder.listFiles()) {
            try {
                War war = new War(file);
                WarManager.setupWar(war);
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
            // Wars can be removed while ticking them, so clone the list to avoid ConcurrentModificationException
            for (War war : new HashSet<>(currentWars)) {
                war.tick();
            }
        }, 0, 1);
    }
}
