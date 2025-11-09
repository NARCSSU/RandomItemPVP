package org.luminolcraft.randomitempvp;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 表示一个游戏房间
 */
public class GameArena {
    private final String arenaName;
    private Location spawnLocation; // 改为可变的，支持投票后更新
    private World world; // 改为可变的，支持世界实例化
    private String instanceWorldKey; // 实例世界的 key（如果启用了世界实例化）
    private String currentMapId; // 当前选中的地图ID
    private String configPreset; // 当前使用的配置预设名称（如果有）
    private GameInstance gameInstance; // 游戏实例
    
    public enum ArenaStatus {
        WAITING,    // 等待中
        PREPARING,  // 准备中（倒计时）
        RUNNING,    // 游戏中
        ENDING      // 结束中
    }
    
    private volatile ArenaStatus status = ArenaStatus.WAITING;
    
    public GameArena(String arenaName, Location spawnLocation, JavaPlugin plugin, ConfigManager config, PlayerStatsManager statsManager) {
        this.arenaName = arenaName;
        this.spawnLocation = spawnLocation != null ? spawnLocation.clone() : null;
        this.world = spawnLocation != null ? spawnLocation.getWorld() : null;
        this.gameInstance = new GameInstance(this, plugin, config, statsManager);
    }
    
    public String getArenaName() {
        return arenaName;
    }
    
    public Location getSpawnLocation() {
        if (spawnLocation == null) return null;
        return spawnLocation.clone();
    }
    
    /**
     * 设置出生点（用于投票后更新地图）
     * @param location 新的出生点
     */
    public void setSpawnLocation(Location location) {
        this.spawnLocation = location != null ? location.clone() : null;
    }
    
    public World getWorld() {
        return world;
    }
    
    /**
     * 设置世界（用于世界实例化）
     * @param world 新的世界对象
     */
    public void setWorld(World world) {
        this.world = world;
    }
    
    /**
     * 获取实例世界 key（如果使用了世界实例化）
     * @return 实例世界 key，如果未使用实例化则返回 null
     */
    public String getInstanceWorldKey() {
        return instanceWorldKey;
    }
    
    /**
     * 设置实例世界 key
     * @param instanceWorldKey 实例世界 key
     */
    public void setInstanceWorldKey(String instanceWorldKey) {
        this.instanceWorldKey = instanceWorldKey;
    }
    
    /**
     * 获取当前选中的地图ID
     * @return 地图ID，如果未选择则返回null
     */
    public String getCurrentMapId() {
        return currentMapId;
    }
    
    /**
     * 设置当前选中的地图ID
     * @param mapId 地图ID
     */
    public void setCurrentMapId(String mapId) {
        this.currentMapId = mapId;
    }
    
    /**
     * 获取当前使用的配置预设名称
     * @return 配置预设名称，如果未使用预设则返回null
     */
    public String getConfigPreset() {
        return configPreset;
    }
    
    /**
     * 设置当前使用的配置预设名称
     * @param presetName 配置预设名称，如果为null则清除预设
     */
    public void setConfigPreset(String presetName) {
        this.configPreset = presetName;
    }
    
    public GameInstance getGameInstance() {
        return gameInstance;
    }
    
    public ArenaStatus getStatus() {
        return status;
    }
    
    public void setStatus(ArenaStatus status) {
        this.status = status;
    }
    
    public boolean isRunning() {
        return status == ArenaStatus.RUNNING;
    }
    
    public boolean isPreparing() {
        return status == ArenaStatus.PREPARING;
    }
    
    public boolean canJoin() {
        return status == ArenaStatus.WAITING || status == ArenaStatus.PREPARING;
    }
    
    public int getPlayerCount() {
        // 确保返回实时玩家数
        return gameInstance.getParticipantCount();
    }
    
    /**
     * 同步状态（确保状态与实际游戏状态一致）
     */
    public void syncStatus() {
        GameInstance instance = getGameInstance();
        if (instance.isRunning()) {
            if (status != ArenaStatus.RUNNING) {
                status = ArenaStatus.RUNNING;
            }
        } else if (instance.isPreparing()) {
            if (status != ArenaStatus.PREPARING) {
                status = ArenaStatus.PREPARING;
            }
        } else {
            if (status != ArenaStatus.WAITING) {
                status = ArenaStatus.WAITING;
            }
        }
    }
}

