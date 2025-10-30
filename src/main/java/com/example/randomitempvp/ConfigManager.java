package com.example.randomitempvp;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public int getArenaRadius() { return config.getInt("arena.radius", 48); }
    public int getMinPlayers() { return config.getInt("arena.min-players", 2); }
    public int getStartCountdown() { return config.getInt("arena.start-countdown", 10); }
    public double getBorderDamageAmount() { return config.getDouble("border.damage", 3.0); }
    public List<String> getItemBlacklist() { List<String> l = config.getStringList("items.blacklist"); return l == null ? Collections.emptyList() : l; }
    public long getItemInterval() { return config.getLong("items.interval_ticks", 60L); }
    public long getEventDelayMin() { return config.getLong("events.delay_min_ticks", 600L); }
    public long getEventDelayMax() { return config.getLong("events.delay_max_ticks", 1200L); }
    public double getShrinkAmount() { return config.getDouble("border.shrink_amount_per_interval", 4.0); }
    public long getShrinkInterval() { return config.getLong("border.shrink_interval_ticks", 100L); }
    public long getShrinkDelay() { return config.getLong("border.first_shrink_delay_ticks", 200L); }
    public double getMinBorderSize() { return config.getDouble("border.min_diameter", 10.0); }
}



