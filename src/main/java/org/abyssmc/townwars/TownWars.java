package org.abyssmc.townwars;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

// Main class!  Doesn't look bad yet.
public final class TownWars extends JavaPlugin {
    public static HashSet<War> currentWars = new HashSet<>();
    public static ItemStack warFlag = new ItemStack(Material.RED_BANNER);

    @Override
    public void onEnable() {
        // Plugin startup logic
        getCommand("war").setExecutor(new WarCommand());
        getServer().getPluginManager().registerEvents(new Events(), this);
        createWarFlag();
        tickWars();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public void createWarFlag() {
        ItemMeta itemMeta = warFlag.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "War banner");
        itemMeta.setLore(lore);
        warFlag.setItemMeta(itemMeta);
    }

    public void tickWars() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (War war : currentWars) {
                war.tick();
            }
        }, 0, 1);
    }
}
