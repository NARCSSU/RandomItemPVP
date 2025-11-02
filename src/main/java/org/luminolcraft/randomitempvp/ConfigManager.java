package org.luminolcraft.randomitempvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public long getEventDelayMinFinal() { return config.getLong("events.delay_min_ticks_final_circle", 200L); }
    public long getEventDelayMaxFinal() { return config.getLong("events.delay_max_ticks_final_circle", 600L); }
    public double getShrinkAmount() { return config.getDouble("border.shrink_amount_per_interval", 4.0); }
    public long getShrinkInterval() { return config.getLong("border.shrink_interval_ticks", 100L); }
    public long getShrinkDelay() { return config.getLong("border.first_shrink_delay_ticks", 200L); }
    public double getMinBorderSize() { return config.getDouble("border.min_diameter", 10.0); }
    
    /**
     * 获取物品权重配置
     * @return 物品权重映射，键为物品类型，值为权重（只包含配置文件中明确指定的物品）
     */
    public Map<Material, Integer> getItemWeights() {
        Map<Material, Integer> weights = new HashMap<>();
        ConfigurationSection weightsSection = config.getConfigurationSection("items.weights");
        
        if (weightsSection != null) {
            for (String key : weightsSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(key.toUpperCase());
                    int weight = weightsSection.getInt(key, 1);
                    if (weight > 0) {
                        weights.put(material, weight);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的物品类型配置: " + key);
                }
            }
        }
        
        // 如果配置为空，返回一些默认物品
        if (weights.isEmpty()) {
            plugin.getLogger().warning("配置文件中未找到任何物品权重配置！将使用默认物品。");
            weights.put(Material.IRON_SWORD, 10);
            weights.put(Material.BOW, 10);
            weights.put(Material.ARROW, 20);
            weights.put(Material.GOLDEN_APPLE, 5);
            weights.put(Material.COBBLESTONE, 15);
        }
        
        return weights;
    }
    
    /**
     * 获取所有可掉落的物品列表（只包含配置文件中指定的物品）
     * @return 物品列表
     */
    public List<Material> getDroppableItems() {
        return new ArrayList<>(getItemWeights().keySet());
    }
    
    /**
     * 获取指定物品的权重
     * @param material 物品类型
     * @return 权重值（默认为1）
     */
    public int getItemWeight(Material material) {
        return getItemWeights().getOrDefault(material, 1);
    }
    
    /**
     * 保存游戏出生点到配置文件
     * @param location 出生点位置
     */
    public void saveSpawnLocation(Location location) {
        if (location == null) return;
        
        config.set("arena.spawn.world", location.getWorld().getName());
        config.set("arena.spawn.x", location.getX());
        config.set("arena.spawn.y", location.getY());
        config.set("arena.spawn.z", location.getZ());
        config.set("arena.spawn.yaw", location.getYaw());
        config.set("arena.spawn.pitch", location.getPitch());
        
        plugin.saveConfig();
        plugin.getLogger().info("游戏出生点已保存到配置文件：" + 
            String.format("世界=%s, 坐标=(%.1f, %.1f, %.1f)", 
            location.getWorld().getName(), 
            location.getX(), 
            location.getY(), 
            location.getZ()));
    }
    
    /**
     * 从配置文件加载游戏出生点
     * @return 出生点位置，如果未配置或世界不存在则返回null
     */
    public Location loadSpawnLocation() {
        return loadSpawnLocation(false);
    }
    
    /**
     * 从配置文件加载游戏出生点
     * @param showLog 是否显示日志
     * @return 出生点位置，如果未配置或世界不存在则返回null
     */
    public Location loadSpawnLocation(boolean showLog) {
        if (!config.contains("arena.spawn.world")) {
            return null;
        }
        
        String worldName = config.getString("arena.spawn.world");
        World world = Bukkit.getWorld(worldName);
        
        if (world == null) {
            plugin.getLogger().warning("配置文件中的世界 '" + worldName + "' 不存在！");
            return null;
        }
        
        double x = config.getDouble("arena.spawn.x", 0.0);
        double y = config.getDouble("arena.spawn.y", 64.0);
        double z = config.getDouble("arena.spawn.z", 0.0);
        float yaw = (float) config.getDouble("arena.spawn.yaw", 0.0);
        float pitch = (float) config.getDouble("arena.spawn.pitch", 0.0);
        
        Location location = new Location(world, x, y, z, yaw, pitch);
        
        if (showLog) {
            plugin.getLogger().info("从配置文件加载游戏出生点：" + 
                String.format("世界=%s, 坐标=(%.1f, %.1f, %.1f)", 
                worldName, x, y, z));
        }
        
        return location;
    }
}



