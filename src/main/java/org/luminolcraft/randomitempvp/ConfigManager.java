package org.luminolcraft.randomitempvp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private ConfigPreset preset; // 当前使用的配置预设（如果有）
    
    // 模块化配置文件
    private File itemsConfigFile;
    private FileConfiguration itemsConfig;
    
    private File arenaConfigFile;
    private FileConfiguration arenaConfig;
    
    private File borderConfigFile;
    private FileConfiguration borderConfig;
    
    private File eventsConfigFile;
    private FileConfiguration eventsConfig;
    
    private File databaseConfigFile;
    private FileConfiguration databaseConfig;
    
    private File mapsConfigFile;
    private FileConfiguration mapsConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.itemsConfigFile = new File(plugin.getDataFolder(), "items.yml");
        this.arenaConfigFile = new File(plugin.getDataFolder(), "arena.yml");
        this.borderConfigFile = new File(plugin.getDataFolder(), "border.yml");
        this.eventsConfigFile = new File(plugin.getDataFolder(), "events.yml");
        this.databaseConfigFile = new File(plugin.getDataFolder(), "database.yml");
        this.mapsConfigFile = new File(plugin.getDataFolder(), "maps.yml");
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.preset = null; // 默认不使用预设
        
        plugin.getLogger().info("============================================");
        plugin.getLogger().info("正在加载配置文件...");
        
        // 加载所有模块化配置文件
        loadItemsConfig();
        loadArenaConfig();
        loadBorderConfig();
        loadEventsConfig();
        loadDatabaseConfig();
        loadMapsConfig();
        
        // 显示配置加载摘要
        displayConfigSummary();
        
        plugin.getLogger().info("============================================");
    }
    
    /**
     * 加载物品配置文件（items.yml）
     */
    private void loadItemsConfig() {
        // 如果文件不存在，尝试从资源文件复制
        if (!itemsConfigFile.exists()) {
            try {
                // 尝试从插件资源复制示例文件
                java.io.InputStream resource = plugin.getResource("config-modules/items.yml");
                if (resource != null) {
                    // 确保父目录存在
                    itemsConfigFile.getParentFile().mkdirs();
                    Files.copy(resource, itemsConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("✓ 已创建物品配置文件: items.yml");
                } else {
                    // 如果资源不存在，创建空文件
                    itemsConfigFile.createNewFile();
                    plugin.getLogger().info("✓ 已创建空物品配置文件: items.yml");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("✗ 创建物品配置文件失败: items.yml - " + e.getMessage());
            }
        }
        
        // 加载配置文件
        if (itemsConfigFile.exists()) {
            this.itemsConfig = YamlConfiguration.loadConfiguration(itemsConfigFile);
            plugin.getLogger().info("✓ 已加载物品配置文件: items.yml");
            
            // 检查配置内容
            if (itemsConfig.contains("weights")) {
                int itemCount = itemsConfig.getConfigurationSection("weights").getKeys(false).size();
                plugin.getLogger().info("  → 找到 " + itemCount + " 个物品权重配置");
            } else {
                plugin.getLogger().info("  → 未找到物品权重配置，将使用 config.yml 中的配置");
            }
            
            if (itemsConfig.contains("blacklist")) {
                int blacklistCount = itemsConfig.getStringList("blacklist").size();
                plugin.getLogger().info("  → 找到 " + blacklistCount + " 个黑名单物品");
            }
            
            if (itemsConfig.contains("interval_ticks")) {
                long interval = itemsConfig.getLong("interval_ticks", 100L);
                plugin.getLogger().info("  → 物品发放间隔: " + interval + " ticks (" + (interval / 20.0) + " 秒)");
            }
        } else {
            plugin.getLogger().info("○ 物品配置文件不存在: items.yml，将使用 config.yml 中的配置");
        }
    }
    
    /**
     * 重载物品配置文件
     */
    public void reloadItemsConfig() {
        if (itemsConfigFile.exists()) {
            this.itemsConfig = YamlConfiguration.loadConfiguration(itemsConfigFile);
            plugin.getLogger().info("✓ 物品配置文件已重载: items.yml");
            
            // 检查配置内容
            if (itemsConfig.contains("weights")) {
                int itemCount = itemsConfig.getConfigurationSection("weights").getKeys(false).size();
                plugin.getLogger().info("  → 找到 " + itemCount + " 个物品权重配置");
            }
        } else {
            plugin.getLogger().info("○ 物品配置文件不存在: items.yml，将使用 config.yml 中的配置");
        }
    }
    
    /**
     * 加载竞技场配置文件（arena.yml）
     */
    private void loadArenaConfig() {
        if (!arenaConfigFile.exists()) {
            try {
                java.io.InputStream resource = plugin.getResource("config-modules/arena.yml");
                if (resource != null) {
                    arenaConfigFile.getParentFile().mkdirs();
                    Files.copy(resource, arenaConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("✓ 已创建竞技场配置文件: arena.yml");
                } else {
                    arenaConfigFile.createNewFile();
                    plugin.getLogger().info("✓ 已创建空竞技场配置文件: arena.yml");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("✗ 创建竞技场配置文件失败: arena.yml - " + e.getMessage());
            }
        }
        
        if (arenaConfigFile.exists()) {
            this.arenaConfig = YamlConfiguration.loadConfiguration(arenaConfigFile);
            plugin.getLogger().info("✓ 已加载竞技场配置文件: arena.yml");
        } else {
            plugin.getLogger().info("○ 竞技场配置文件不存在: arena.yml，将使用 config.yml 中的配置");
        }
    }
    
    /**
     * 加载边界配置文件（border.yml）
     */
    private void loadBorderConfig() {
        if (!borderConfigFile.exists()) {
            try {
                java.io.InputStream resource = plugin.getResource("config-modules/border.yml");
                if (resource != null) {
                    borderConfigFile.getParentFile().mkdirs();
                    Files.copy(resource, borderConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("✓ 已创建边界配置文件: border.yml");
                } else {
                    borderConfigFile.createNewFile();
                    plugin.getLogger().info("✓ 已创建空边界配置文件: border.yml");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("✗ 创建边界配置文件失败: border.yml - " + e.getMessage());
            }
        }
        
        if (borderConfigFile.exists()) {
            this.borderConfig = YamlConfiguration.loadConfiguration(borderConfigFile);
            plugin.getLogger().info("✓ 已加载边界配置文件: border.yml");
        } else {
            plugin.getLogger().info("○ 边界配置文件不存在: border.yml，将使用 config.yml 中的配置");
        }
    }
    
    /**
     * 加载随机事件配置文件（events.yml）
     */
    private void loadEventsConfig() {
        if (!eventsConfigFile.exists()) {
            try {
                java.io.InputStream resource = plugin.getResource("config-modules/events.yml");
                if (resource != null) {
                    eventsConfigFile.getParentFile().mkdirs();
                    Files.copy(resource, eventsConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("✓ 已创建随机事件配置文件: events.yml");
                } else {
                    eventsConfigFile.createNewFile();
                    plugin.getLogger().info("✓ 已创建空随机事件配置文件: events.yml");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("✗ 创建随机事件配置文件失败: events.yml - " + e.getMessage());
            }
        }
        
        if (eventsConfigFile.exists()) {
            this.eventsConfig = YamlConfiguration.loadConfiguration(eventsConfigFile);
            plugin.getLogger().info("✓ 已加载随机事件配置文件: events.yml");
        } else {
            plugin.getLogger().info("○ 随机事件配置文件不存在: events.yml，将使用 config.yml 中的配置");
        }
    }
    
    /**
     * 加载数据库配置文件（database.yml）
     */
    private void loadDatabaseConfig() {
        if (!databaseConfigFile.exists()) {
            try {
                java.io.InputStream resource = plugin.getResource("config-modules/database.yml");
                if (resource != null) {
                    databaseConfigFile.getParentFile().mkdirs();
                    Files.copy(resource, databaseConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("✓ 已创建数据库配置文件: database.yml");
                } else {
                    databaseConfigFile.createNewFile();
                    plugin.getLogger().info("✓ 已创建空数据库配置文件: database.yml");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("✗ 创建数据库配置文件失败: database.yml - " + e.getMessage());
            }
        }
        
        if (databaseConfigFile.exists()) {
            this.databaseConfig = YamlConfiguration.loadConfiguration(databaseConfigFile);
            plugin.getLogger().info("✓ 已加载数据库配置文件: database.yml");
        } else {
            plugin.getLogger().info("○ 数据库配置文件不存在: database.yml，将使用 config.yml 中的配置");
        }
    }
    
    /**
     * 加载地图配置文件（maps.yml）
     */
    private void loadMapsConfig() {
        if (!mapsConfigFile.exists()) {
            try {
                java.io.InputStream resource = plugin.getResource("config-modules/maps.yml");
                if (resource != null) {
                    mapsConfigFile.getParentFile().mkdirs();
                    Files.copy(resource, mapsConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("✓ 已创建地图配置文件: maps.yml");
                } else {
                    mapsConfigFile.createNewFile();
                    plugin.getLogger().info("✓ 已创建空地图配置文件: maps.yml");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("✗ 创建地图配置文件失败: maps.yml - " + e.getMessage());
            }
        }
        
        if (mapsConfigFile.exists()) {
            this.mapsConfig = YamlConfiguration.loadConfiguration(mapsConfigFile);
            plugin.getLogger().info("✓ 已加载地图配置文件: maps.yml");
        } else {
            plugin.getLogger().info("○ 地图配置文件不存在: maps.yml，将使用 config.yml 中的配置");
        }
    }
    
    /**
     * 显示配置加载摘要
     */
    private void displayConfigSummary() {
        plugin.getLogger().info("配置加载摘要:");
        
        // 主配置文件
        plugin.getLogger().info("  ✓ 主配置文件: config.yml");
        
        // 模块化配置文件
        if (arenaConfigFile.exists() && arenaConfig != null) {
            plugin.getLogger().info("  ✓ 竞技场配置: arena.yml (独立文件)");
        } else {
            plugin.getLogger().info("  ○ 竞技场配置: 使用 config.yml");
        }
        
        if (borderConfigFile.exists() && borderConfig != null) {
            plugin.getLogger().info("  ✓ 边界配置: border.yml (独立文件)");
        } else {
            plugin.getLogger().info("  ○ 边界配置: 使用 config.yml");
        }
        
        if (eventsConfigFile.exists() && eventsConfig != null) {
            plugin.getLogger().info("  ✓ 随机事件配置: events.yml (独立文件)");
        } else {
            plugin.getLogger().info("  ○ 随机事件配置: 使用 config.yml");
        }
        
        if (databaseConfigFile.exists() && databaseConfig != null) {
            plugin.getLogger().info("  ✓ 数据库配置: database.yml (独立文件)");
        } else {
            plugin.getLogger().info("  ○ 数据库配置: 使用 config.yml");
        }
        
        if (mapsConfigFile.exists() && mapsConfig != null) {
            plugin.getLogger().info("  ✓ 地图配置: maps.yml (独立文件)");
        } else {
            plugin.getLogger().info("  ○ 地图配置: 使用 config.yml");
        }
        
        // 物品配置文件
        if (hasItemsConfig()) {
            plugin.getLogger().info("  ✓ 物品配置: items.yml (独立文件)");
            Map<Material, Integer> weights = getItemWeights();
            plugin.getLogger().info("    → 物品权重数量: " + weights.size());
        } else {
            plugin.getLogger().info("  ○ 物品配置: 使用 config.yml");
            Map<Material, Integer> weights = getItemWeights();
            plugin.getLogger().info("    → 物品权重数量: " + weights.size());
        }
        
        // 配置预设
        if (preset != null) {
            plugin.getLogger().info("  ✓ 配置预设: " + preset.getPresetName());
        } else {
            plugin.getLogger().info("  ○ 配置预设: 未使用");
        }
    }
    
    /**
     * 加载配置（可指定预设）
     * @param presetName 预设名称，如果为 null 则使用主配置
     */
    public void loadConfig(String presetName) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        
        if (presetName != null && !presetName.isEmpty()) {
            this.preset = new ConfigPreset(plugin, presetName);
            if (!preset.exists()) {
                plugin.getLogger().warning("配置预设不存在: " + presetName + "，将使用主配置");
                this.preset = null;
            } else {
                plugin.getLogger().info("已加载配置预设: " + presetName);
            }
        } else {
            this.preset = null;
        }
    }

    public void reloadConfig() {
        plugin.getLogger().info("============================================");
        plugin.getLogger().info("正在重载配置文件...");
        
        // 重载主配置
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        plugin.getLogger().info("✓ 主配置文件已重载: config.yml");
        
        // 重载所有模块化配置
        reloadItemsConfig();
        loadArenaConfig();
        loadBorderConfig();
        loadEventsConfig();
        loadDatabaseConfig();
        loadMapsConfig();
        
        plugin.getLogger().info("✓ 所有配置文件已重载");
        
        // 重载预设（如果有）
        if (preset != null) {
            preset.reload();
            plugin.getLogger().info("✓ 配置预设已重载: " + preset.getPresetName());
        }
        
        // 显示配置加载摘要
        displayConfigSummary();
        
        plugin.getLogger().info("============================================");
    }
    
    /**
     * 检查物品配置文件是否存在
     */
    public boolean hasItemsConfig() {
        return itemsConfigFile.exists() && itemsConfig != null;
    }
    
    /**
     * 获取当前使用的配置预设
     * @return 配置预设，如果未使用预设则返回 null
     */
    public ConfigPreset getPreset() {
        return preset;
    }
    
    /**
     * 设置配置预设
     * @param presetName 预设名称，如果为 null 则清除预设
     */
    public void setPreset(String presetName) {
        if (presetName != null && !presetName.isEmpty()) {
            this.preset = new ConfigPreset(plugin, presetName);
            if (!preset.exists()) {
                plugin.getLogger().warning("配置预设不存在: " + presetName + "，将使用主配置");
                this.preset = null;
            }
        } else {
            this.preset = null;
        }
    }
    
    /**
     * 获取配置值（支持模块化配置、预设覆盖）
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 配置值（优先使用模块化配置，其次预设，最后主配置）
     */
    private int getIntWithPreset(String path, int defaultValue) {
        // 优先使用模块化配置
        FileConfiguration modularConfig = getModularConfig(path);
        if (modularConfig != null) {
            String subPath = getSubPath(path);
            if (modularConfig.contains(subPath)) {
                return modularConfig.getInt(subPath, defaultValue);
            }
        }
        
        // 其次使用预设
        if (preset != null && preset.contains(path)) {
            return preset.getInt(path, defaultValue);
        }
        
        // 最后使用主配置
        return config.getInt(path, defaultValue);
    }
    
    private double getDoubleWithPreset(String path, double defaultValue) {
        // 优先使用模块化配置
        FileConfiguration modularConfig = getModularConfig(path);
        if (modularConfig != null) {
            String subPath = getSubPath(path);
            if (modularConfig.contains(subPath)) {
                return modularConfig.getDouble(subPath, defaultValue);
            }
        }
        
        // 其次使用预设
        if (preset != null && preset.contains(path)) {
            return preset.getDouble(path, defaultValue);
        }
        
        // 最后使用主配置
        return config.getDouble(path, defaultValue);
    }
    
    private boolean getBooleanWithPreset(String path, boolean defaultValue) {
        // 优先使用模块化配置
        FileConfiguration modularConfig = getModularConfig(path);
        if (modularConfig != null) {
            String subPath = getSubPath(path);
            if (modularConfig.contains(subPath)) {
                return modularConfig.getBoolean(subPath, defaultValue);
            }
        }
        
        // 其次使用预设
        if (preset != null && preset.contains(path)) {
            return preset.getBoolean(path, defaultValue);
        }
        
        // 最后使用主配置
        return config.getBoolean(path, defaultValue);
    }
    
    private long getLongWithPreset(String path, long defaultValue) {
        // 优先使用模块化配置
        FileConfiguration modularConfig = getModularConfig(path);
        if (modularConfig != null) {
            String subPath = getSubPath(path);
            if (modularConfig.contains(subPath)) {
                return modularConfig.getLong(subPath, defaultValue);
            }
        }
        
        // 其次使用预设
        if (preset != null && preset.contains(path)) {
            return preset.getLong(path, defaultValue);
        }
        
        // 最后使用主配置
        return config.getLong(path, defaultValue);
    }
    
    /**
     * 获取子路径（移除模块前缀）
     * @param path 完整路径（如 "arena.radius", "border.damage"）
     * @return 子路径（如 "radius", "damage"）
     */
    private String getSubPath(String path) {
        if (path.startsWith("arena.")) {
            return path.substring(6);
        } else if (path.startsWith("border.")) {
            return path.substring(7);
        } else if (path.startsWith("events.")) {
            return path.substring(7);
        } else if (path.startsWith("database.")) {
            return path.substring(9);
        }
        return path;
    }
    
    /**
     * 根据配置路径获取对应的模块化配置文件
     * @param path 配置路径（如 "arena.radius", "border.damage"）
     * @return 模块化配置文件，如果不存在则返回 null
     */
    private FileConfiguration getModularConfig(String path) {
        if (path.startsWith("arena.")) {
            return arenaConfig;
        } else if (path.startsWith("border.")) {
            return borderConfig;
        } else if (path.startsWith("events.")) {
            return eventsConfig;
        } else if (path.startsWith("database.")) {
            return databaseConfig;
        }
        return null;
    }
    
    private List<String> getStringListWithPreset(String path, List<String> defaultValue) {
        // 优先使用预设
        if (preset != null && preset.contains(path)) {
            List<String> presetList = preset.getStringList(path, null);
            if (presetList != null && !presetList.isEmpty()) {
                return presetList;
            }
        }
        
        // 使用主配置
        List<String> mainList = config.getStringList(path);
        return mainList != null && !mainList.isEmpty() ? mainList : defaultValue;
    }

    public int getArenaRadius() { return getIntWithPreset("arena.radius", 48); }
    public int getMinPlayers() { return getIntWithPreset("arena.min-players", 2); }
    public int getStartCountdown() { return getIntWithPreset("arena.start-countdown", 30); }
    public int getAutoStartDelay() { return getIntWithPreset("arena.auto-start-delay", 5); }
    public int getVoteDuration() { return getIntWithPreset("arena.vote-duration", 15); }
    public boolean isWorldInstancingEnabled() { return getBooleanWithPreset("arena.world-instancing.enabled", true); }
    public boolean isWorldInstancingAutoCleanup() { return getBooleanWithPreset("arena.world-instancing.auto-cleanup", true); }
    public boolean isLobbyEnabled() { return getBooleanWithPreset("arena.lobby.enabled", true); }
    public double getBorderDamageAmount() { return getDoubleWithPreset("border.damage", 3.0); }
    public List<String> getItemBlacklist() {
        // 优先使用预设
        if (preset != null && preset.contains("items.blacklist")) {
            return preset.getStringList("items.blacklist", Collections.emptyList());
        }
        
        // 其次使用独立的物品配置文件
        if (hasItemsConfig() && itemsConfig.contains("blacklist")) {
            List<String> list = itemsConfig.getStringList("blacklist");
            if (list != null && !list.isEmpty()) {
                return list;
            }
        }
        
        // 最后使用主配置
        return getStringListWithPreset("items.blacklist", Collections.emptyList());
    }
    
    public long getItemInterval() {
        // 优先使用预设
        if (preset != null && preset.contains("items.interval_ticks")) {
            return preset.getLong("items.interval_ticks", 100L);
        }
        
        // 其次使用独立的物品配置文件
        if (hasItemsConfig() && itemsConfig.contains("interval_ticks")) {
            return itemsConfig.getLong("interval_ticks", 100L);
        }
        
        // 最后使用主配置
        return getLongWithPreset("items.interval_ticks", 100L);
    }
    public long getEventDelayMin() { return getLongWithPreset("events.delay_min_ticks", 600L); }
    public long getEventDelayMax() { return getLongWithPreset("events.delay_max_ticks", 2400L); }
    public long getEventDelayMinFinal() { return getLongWithPreset("events.delay_min_ticks_final_circle", 200L); }
    public long getEventDelayMaxFinal() { return getLongWithPreset("events.delay_max_ticks_final_circle", 600L); }
    public double getShrinkAmount() { return getDoubleWithPreset("border.shrink_amount_per_interval", 6.0); }
    public long getShrinkInterval() { return getLongWithPreset("border.shrink_interval_ticks", 600L); }
    public long getShrinkDelay() { return getLongWithPreset("border.first_shrink_delay_ticks", 200L); }
    public double getMinBorderSize() { return getDoubleWithPreset("border.min_diameter", 10.0); }
    
    /**
     * 获取物品权重配置（支持预设覆盖和独立文件）
     * @return 物品权重映射，键为物品类型，值为权重（只包含配置文件中明确指定的物品）
     */
    public Map<Material, Integer> getItemWeights() {
        Map<Material, Integer> weights = new HashMap<>();
        ConfigurationSection weightsSection = null;
        
        // 优先使用预设中的物品权重
        if (preset != null && preset.getConfig() != null) {
            weightsSection = preset.getConfig().getConfigurationSection("items.weights");
        }
        
        // 其次使用独立的物品配置文件（items.yml）
        if (weightsSection == null && hasItemsConfig() && itemsConfig.contains("weights")) {
            weightsSection = itemsConfig.getConfigurationSection("weights");
        }
        
        // 最后使用主配置
        if (weightsSection == null) {
            weightsSection = config.getConfigurationSection("items.weights");
        }
        
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
        
        String worldIdentifier = config.getString("arena.spawn.world");
        // 使用 WorldsIntegration 加载世界（支持 Worlds 插件的世界 key）
        World world = WorldsIntegration.loadWorld(worldIdentifier);
        
        if (world == null) {
            plugin.getLogger().warning("配置文件中的世界 '" + worldIdentifier + "' 不存在！");
            if (WorldsIntegration.isWorldsAvailable()) {
                plugin.getLogger().warning("提示：如果使用 Worlds 插件，请确保世界 key 正确，或使用 /world list 查看可用世界");
            }
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
                worldIdentifier, x, y, z));
        }
        
        return location;
    }
    
    /**
     * 获取所有可用的地图列表（支持预设覆盖）
     * @return 地图ID列表
     */
    public List<String> getAvailableMaps() {
        List<String> maps = new ArrayList<>();
        ConfigurationSection mapsSection = null;
        
        // 优先使用预设中的地图列表
        if (preset != null && preset.getConfig() != null) {
            mapsSection = preset.getConfig().getConfigurationSection("arena.maps");
        }
        
        // 使用主配置
        if (mapsSection == null) {
            mapsSection = config.getConfigurationSection("arena.maps");
        }
        
        if (mapsSection != null) {
            maps.addAll(mapsSection.getKeys(false));
        }
        
        return maps;
    }
    
    /**
     * 获取地图的显示名称（支持预设覆盖）
     * @param mapId 地图ID
     * @return 显示名称，如果不存在则返回地图ID
     */
    public String getMapName(String mapId) {
        String name = null;
        
        // 优先使用预设
        if (preset != null && preset.contains("arena.maps." + mapId + ".name")) {
            name = preset.getString("arena.maps." + mapId + ".name", null);
        }
        
        // 使用主配置
        if (name == null) {
            name = config.getString("arena.maps." + mapId + ".name");
        }
        
        return name != null ? name : mapId;
    }
    
    /**
     * 加载指定地图的出生点（支持预设覆盖）
     * 支持 Worlds 插件的世界 key
     * @param mapId 地图ID
     * @return 出生点位置，如果未配置或世界不存在则返回null
     */
    public Location loadMapSpawnLocation(String mapId) {
        String worldIdentifier = null;
        
        // 优先使用预设
        if (preset != null && preset.contains("arena.maps." + mapId + ".world")) {
            worldIdentifier = preset.getString("arena.maps." + mapId + ".world", null);
        }
        
        // 使用主配置
        if (worldIdentifier == null) {
            worldIdentifier = config.getString("arena.maps." + mapId + ".world");
        }
        
        if (worldIdentifier == null) {
            return null;
        }
        
        // 使用 WorldsIntegration 加载世界（支持 Worlds 插件的世界 key）
        World world = WorldsIntegration.loadWorld(worldIdentifier);
        if (world == null) {
            plugin.getLogger().warning("地图 '" + mapId + "' 的世界 '" + worldIdentifier + "' 不存在！");
            if (WorldsIntegration.isWorldsAvailable()) {
                plugin.getLogger().warning("提示：如果使用 Worlds 插件，请确保世界 key 正确，或使用 /world list 查看可用世界");
            }
            return null;
        }
        
        // 获取坐标（优先使用预设，其次模块化配置，最后主配置）
        double x = 0.0, y = 64.0, z = 0.0;
        float yaw = 0.0f, pitch = 0.0f;
        
        if (preset != null) {
            if (preset.contains("arena.maps." + mapId + ".x")) x = preset.getDouble("arena.maps." + mapId + ".x", 0.0);
            if (preset.contains("arena.maps." + mapId + ".y")) y = preset.getDouble("arena.maps." + mapId + ".y", 64.0);
            if (preset.contains("arena.maps." + mapId + ".z")) z = preset.getDouble("arena.maps." + mapId + ".z", 0.0);
            if (preset.contains("arena.maps." + mapId + ".yaw")) yaw = (float) preset.getDouble("arena.maps." + mapId + ".yaw", 0.0);
            if (preset.contains("arena.maps." + mapId + ".pitch")) pitch = (float) preset.getDouble("arena.maps." + mapId + ".pitch", 0.0);
        }
        
        // 使用主配置（如果预设没有）
        if (preset == null) {
            x = config.getDouble("arena.maps." + mapId + ".x", x);
            y = config.getDouble("arena.maps." + mapId + ".y", y);
            z = config.getDouble("arena.maps." + mapId + ".z", z);
            yaw = (float) config.getDouble("arena.maps." + mapId + ".yaw", yaw);
            pitch = (float) config.getDouble("arena.maps." + mapId + ".pitch", pitch);
        }
        
        return new Location(world, x, y, z, yaw, pitch);
    }
    
    /**
     * 检查地图是否存在（支持预设覆盖）
     * @param mapId 地图ID
     * @return 是否存在
     */
    public boolean mapExists(String mapId) {
        // 优先检查预设
        if (preset != null && preset.contains("arena.maps." + mapId)) {
            return true;
        }
        
        // 检查主配置
        return config.contains("arena.maps." + mapId);
    }
    
    /**
     * 加载大厅位置（游戏结束后传送玩家）
     * @return 大厅位置，如果未配置或世界不存在则返回null
     */
    public Location loadLobbyLocation() {
        if (!config.contains("arena.lobby.enabled") || !config.getBoolean("arena.lobby.enabled", true)) {
            return null; // 大厅未启用
        }
        
        String worldIdentifier = config.getString("arena.lobby.world");
        if (worldIdentifier == null) {
            return null;
        }
        
        // 使用 WorldsIntegration 加载世界（支持 Worlds 插件的世界 key）
        World world = WorldsIntegration.loadWorld(worldIdentifier);
        if (world == null) {
            plugin.getLogger().warning("大厅世界 '" + worldIdentifier + "' 不存在！");
            return null;
        }
        
        double x = config.getDouble("arena.lobby.x", 0.0);
        double y = config.getDouble("arena.lobby.y", 64.0);
        double z = config.getDouble("arena.lobby.z", 0.0);
        float yaw = (float) config.getDouble("arena.lobby.yaw", 0.0);
        float pitch = (float) config.getDouble("arena.lobby.pitch", 0.0);
        
        return new Location(world, x, y, z, yaw, pitch);
    }
    
    /**
     * 获取配置字符串值
     * @param path 配置路径
     * @return 字符串值，如果不存在则返回null
     */
    public String getString(String path) {
        return config.getString(path);
    }
    
    /**
     * 获取地图特定的半径配置（支持预设覆盖，带fallback到全局配置）
     * @param mapId 地图ID
     * @return 半径值
     */
    public int getMapRadius(String mapId) {
        if (mapId == null) return getArenaRadius();
        
        // 优先使用预设
        if (preset != null && preset.contains("arena.maps." + mapId + ".radius")) {
            return preset.getInt("arena.maps." + mapId + ".radius", getArenaRadius());
        }
        
        // 使用主配置
        if (config.contains("arena.maps." + mapId + ".radius")) {
            return config.getInt("arena.maps." + mapId + ".radius", getArenaRadius());
        }
        
        return getArenaRadius();
    }
    
    /**
     * 获取地图特定的最少玩家数配置（支持预设覆盖，带fallback到全局配置）
     * @param mapId 地图ID
     * @return 最少玩家数
     */
    public int getMapMinPlayers(String mapId) {
        if (mapId == null) return getMinPlayers();
        
        // 优先使用预设
        if (preset != null && preset.contains("arena.maps." + mapId + ".min-players")) {
            return preset.getInt("arena.maps." + mapId + ".min-players", getMinPlayers());
        }
        
        // 使用主配置
        if (config.contains("arena.maps." + mapId + ".min-players")) {
            return config.getInt("arena.maps." + mapId + ".min-players", getMinPlayers());
        }
        
        return getMinPlayers();
    }
    
    /**
     * 获取地图特定的倒计时配置（支持预设覆盖，带fallback到全局配置）
     * @param mapId 地图ID
     * @return 倒计时时长（秒）
     */
    public int getMapStartCountdown(String mapId) {
        if (mapId == null) return getStartCountdown();
        
        // 优先使用预设
        if (preset != null && preset.contains("arena.maps." + mapId + ".start-countdown")) {
            return preset.getInt("arena.maps." + mapId + ".start-countdown", getStartCountdown());
        }
        
        // 使用主配置
        if (config.contains("arena.maps." + mapId + ".start-countdown")) {
            return config.getInt("arena.maps." + mapId + ".start-countdown", getStartCountdown());
        }
        
        return getStartCountdown();
    }
    
    /**
     * 获取地图特定的自动启动延迟配置（支持预设覆盖，带fallback到全局配置）
     * @param mapId 地图ID
     * @return 自动启动延迟（秒）
     */
    public int getMapAutoStartDelay(String mapId) {
        if (mapId == null) return getAutoStartDelay();
        
        // 优先使用预设
        if (preset != null && preset.contains("arena.maps." + mapId + ".auto-start-delay")) {
            return preset.getInt("arena.maps." + mapId + ".auto-start-delay", getAutoStartDelay());
        }
        
        // 使用主配置
        if (config.contains("arena.maps." + mapId + ".auto-start-delay")) {
            return config.getInt("arena.maps." + mapId + ".auto-start-delay", getAutoStartDelay());
        }
        
        return getAutoStartDelay();
    }
    
    /**
     * 获取地图特定的投票时长配置（支持预设覆盖，带fallback到全局配置）
     * @param mapId 地图ID
     * @return 投票时长（秒）
     */
    public int getMapVoteDuration(String mapId) {
        if (mapId == null) return getVoteDuration();
        
        // 优先使用预设
        if (preset != null && preset.contains("arena.maps." + mapId + ".vote-duration")) {
            return preset.getInt("arena.maps." + mapId + ".vote-duration", getVoteDuration());
        }
        
        // 使用主配置
        if (config.contains("arena.maps." + mapId + ".vote-duration")) {
            return config.getInt("arena.maps." + mapId + ".vote-duration", getVoteDuration());
        }
        
        return getVoteDuration();
    }
}



