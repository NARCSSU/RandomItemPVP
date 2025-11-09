package org.luminolcraft.randomitempvp;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理所有游戏房间
 */
public class ArenaManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final PlayerStatsManager statsManager;
    
    // 房间列表 <房间名, 房间>
    private final Map<String, GameArena> arenas = new ConcurrentHashMap<>();
    
    // 玩家所在房间 <玩家, 房间名>
    private final Map<Player, String> playerArena = new ConcurrentHashMap<>();
    
    // 自动启动延迟任务 <房间名, 任务>
    private final Map<String, ScheduledTask> autoStartTasks = new ConcurrentHashMap<>();
    
    public ArenaManager(JavaPlugin plugin, ConfigManager config, PlayerStatsManager statsManager) {
        this.plugin = plugin;
        this.config = config;
        this.statsManager = statsManager;
    }
    
    /**
     * 向房间内的所有玩家发送消息
     * @param arenaName 房间名
     * @param message 消息内容
     */
    private void sendMessageToArena(String arenaName, String message) {
        GameArena arena = arenas.get(arenaName);
        if (arena == null) return;
        
        GameInstance instance = arena.getGameInstance();
        Set<Player> participants = instance.getParticipants();
        
        for (Player player : participants) {
            if (player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }
    
    /**
     * 向房间内的所有玩家广播消息
     * @param arenaName 房间名
     * @param messages 消息内容数组
     */
    private void sendMessagesToArena(String arenaName, String... messages) {
        for (String message : messages) {
            sendMessageToArena(arenaName, message);
        }
    }
    
    /**
     * 创建房间（不绑定地图，启动投票）
     * @param arenaName 房间名
     * @param creator 创建者（可选，如果提供则自动加入房间）
     * @return 是否创建成功
     */
    public boolean createArena(String arenaName, Player creator) {
        if (arenas.containsKey(arenaName)) {
            return false; // 房间已存在
        }
        
        // 检查是否有可用地图
        List<String> availableMaps = config.getAvailableMaps();
        if (availableMaps.isEmpty()) {
            // 如果没有地图配置，使用旧的单点模式（使用配置中的 spawn）
            Location defaultSpawn = config.loadSpawnLocation();
            if (defaultSpawn == null) {
                if (creator != null) {
                    creator.sendMessage("§c错误：没有可用的地图配置，且未设置默认出生点！");
                }
                return false;
            }
            return createArena(arenaName, defaultSpawn, creator);
        }
        
        // 使用临时出生点创建房间（将在投票后更新）
        // 使用创建者的位置或世界出生点作为临时位置
        Location tempSpawn;
        if (creator != null && creator.isOnline()) {
            tempSpawn = creator.getLocation();
        } else {
            tempSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        
        GameArena arena = new GameArena(arenaName, tempSpawn, plugin, config, statsManager);
        arenas.put(arenaName, arena);
        
        plugin.getLogger().info("房间 '" + arenaName + "' 已创建");
        
        // 广播创建房间的消息
        String creatorName = creator != null && creator.isOnline() ? creator.getName() : "控制台";
        Bukkit.broadcastMessage("§a[RandomItemPVP] 房间 '§6" + arenaName + "§a' 已创建！创建者：§e" + creatorName);
        Bukkit.broadcastMessage("§7使用 /ripvp join " + arenaName + " 加入房间");
        
        // 如果提供了创建者，自动加入房间（加入后会检查是否开始投票）
        if (creator != null && creator.isOnline()) {
            joinArena(creator, arenaName);
        }
        
        return true;
    }
    
    /**
     * 创建房间（旧版兼容：直接指定出生点，不使用投票，支持配置预设）
     * @param arenaName 房间名
     * @param spawnLocation 出生点
     * @param creator 创建者（可选，如果提供则自动加入房间）
     * @param presetName 配置预设名称（可选，如果为null则使用主配置）
     * @return 是否创建成功
     */
    public boolean createArena(String arenaName, Location spawnLocation, Player creator, String presetName) {
        if (arenas.containsKey(arenaName)) {
            return false; // 房间已存在
        }
        
        // 如果指定了配置预设，创建独立的 ConfigManager
        ConfigManager arenaConfig = config;
        if (presetName != null && !presetName.isEmpty()) {
            arenaConfig = new ConfigManager(plugin);
            arenaConfig.loadConfig(presetName);
            ConfigPreset preset = arenaConfig.getPreset();
            if (preset == null || !preset.exists()) {
                plugin.getLogger().warning("配置预设不存在: " + presetName + "，将使用主配置");
                arenaConfig = config;
                presetName = null; // 清除无效的预设名称
            } else {
                plugin.getLogger().info("房间 '" + arenaName + "' 使用配置预设: " + presetName);
            }
        }
        
        GameArena arena = new GameArena(arenaName, spawnLocation, plugin, arenaConfig, statsManager);
        if (presetName != null) {
            arena.setConfigPreset(presetName);
        }
        arenas.put(arenaName, arena);
        
        // 保存到配置文件
        saveArenaConfig(arena);
        
        plugin.getLogger().info("房间 '" + arenaName + "' 已创建在 " + 
            spawnLocation.getWorld().getName() + " (" + 
            (int)spawnLocation.getX() + ", " + 
            (int)spawnLocation.getY() + ", " + 
            (int)spawnLocation.getZ() + ")" + (presetName != null ? " (配置预设: " + presetName + ")" : ""));
        
        // 广播创建房间的消息
        String creatorName = creator != null && creator.isOnline() ? creator.getName() : "控制台";
        Bukkit.broadcastMessage("§a[RandomItemPVP] 房间 '§6" + arenaName + "§a' 已创建！创建者：§e" + creatorName + (presetName != null ? " §7(预设: §e" + presetName + "§7)" : ""));
        Bukkit.broadcastMessage("§7使用 /ripvp join " + arenaName + " 加入房间");
        
        // 如果提供了创建者，自动加入房间
        if (creator != null && creator.isOnline()) {
            joinArena(creator, arenaName);
        }
        
        return true;
    }
    
    /**
     * 创建房间（旧版兼容：直接指定出生点，不使用投票，使用主配置）
     * @param arenaName 房间名
     * @param spawnLocation 出生点
     * @param creator 创建者（可选，如果提供则自动加入房间）
     * @return 是否创建成功
     */
    public boolean createArena(String arenaName, Location spawnLocation, Player creator) {
        return createArena(arenaName, spawnLocation, creator, null);
    }
    
    /**
     * 创建房间（无创建者自动加入）
     * @param arenaName 房间名
     * @param spawnLocation 出生点
     * @return 是否创建成功
     */
    public boolean createArena(String arenaName, Location spawnLocation) {
        return createArena(arenaName, spawnLocation, null);
    }
    
    /**
     * 删除房间
     * @param arenaName 房间名
     * @return 是否删除成功
     */
    public boolean deleteArena(String arenaName) {
        GameArena arena = arenas.get(arenaName);
        if (arena == null) {
            return false; // 房间不存在
        }
        
        // 无论什么状态，都先强制停止游戏实例（确保倒计时和游戏都被停止）
        GameInstance instance = arena.getGameInstance();
        instance.forceStop(); // 强制停止游戏实例
        
        // 取消自动启动任务
        cancelAutoStart(arenaName);
        
        // 取消地图投票
        RandomItemPVP pluginInstance = (RandomItemPVP) plugin;
        if (pluginInstance != null) {
            MapVoteManager voteManager = pluginInstance.getMapVoteManager();
            if (voteManager != null) {
                voteManager.cancelVote(arenaName);
            }
        }
        
        // 从玩家房间映射中移除所有在此房间的玩家
        List<Player> toRemove = new ArrayList<>();
        for (Map.Entry<Player, String> entry : playerArena.entrySet()) {
            if (entry.getValue().equals(arenaName)) {
                toRemove.add(entry.getKey());
            }
        }
        for (Player player : toRemove) {
            playerArena.remove(player);
        }
        
        // 从房间列表中移除（最后一步，确保所有清理都完成了）
        arenas.remove(arenaName);
        
        // 清理世界实例（如果使用了世界实例化）
        if (config.isWorldInstancingEnabled() && WorldsIntegration.isWorldsAvailable()) {
            String instanceWorldKey = arena.getInstanceWorldKey();
            if (instanceWorldKey != null && config.isWorldInstancingAutoCleanup()) {
                // 安全检查：确保是实例世界，不是模板世界
                if (!isValidInstanceWorldKey(instanceWorldKey, arenaName)) {
                    plugin.getLogger().severe("[房间 " + arenaName + "] 安全警告：实例世界 key 格式不正确，拒绝删除！Key: " + instanceWorldKey);
                    return true; // 跳过删除，但继续删除房间
                }
                
                // 删除世界实例
                plugin.getLogger().info("[房间 " + arenaName + "] 正在删除世界实例: " + instanceWorldKey);
                plugin.getLogger().info("[房间 " + arenaName + "] 安全验证：确认为实例世界（非模板世界）");
                if (WorldsIntegration.deleteWorldInstance(instanceWorldKey)) {
                    plugin.getLogger().info("[房间 " + arenaName + "] 世界实例已删除: " + instanceWorldKey);
                } else {
                    plugin.getLogger().warning("[房间 " + arenaName + "] 删除世界实例失败: " + instanceWorldKey);
                }
            }
        }
        
        // 从配置文件删除
        removeArenaConfig(arenaName);
        
        plugin.getLogger().info("房间 '" + arenaName + "' 已删除");
        return true;
    }
    
    /**
     * 验证世界 key 是否为实例世界（安全措施）
     * 实例世界的格式应该是：<模板key>_<房间名>
     * @param worldKey 世界 key
     * @param arenaName 房间名（用于验证）
     * @return 是否为有效的实例世界 key
     */
    private boolean isValidInstanceWorldKey(String worldKey, String arenaName) {
        if (worldKey == null || arenaName == null || arenaName.isEmpty()) {
            return false;
        }
        
        // 实例世界 key 应该包含下划线和房间名
        String normalizedArenaName = arenaName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        
        // 检查是否以下划线+房间名结尾
        if (!worldKey.toLowerCase().endsWith("_" + normalizedArenaName)) {
            return false;
        }
        
        // 确保包含下划线（模板名_房间名格式）
        if (!worldKey.contains("_")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 清理房间的世界实例（游戏结束后调用）
     * 专注于使用 Worlds 插件删除克隆的世界实例
     * @param arena 房间
     */
    public void cleanupArenaWorld(GameArena arena) {
        if (!WorldsIntegration.isWorldsAvailable()) {
            plugin.getLogger().warning("[房间 " + arena.getArenaName() + "] Worlds 插件不可用，无法删除世界实例");
            return;
        }
        
        String instanceWorldKey = arena.getInstanceWorldKey();
        
        // 获取投票管理器
        RandomItemPVP pluginInstance = (RandomItemPVP) plugin;
        MapVoteManager voteManager = pluginInstance != null ? pluginInstance.getMapVoteManager() : null;
        
        if (!config.isWorldInstancingEnabled()) {
            plugin.getLogger().info("[房间 " + arena.getArenaName() + "] 世界实例化未启用，跳过删除");
            // 清除当前地图选择，确保下次游戏时重新投票
            arena.setCurrentMapId(null);
            if (voteManager != null) {
                voteManager.cancelVote(arena.getArenaName());
            }
            return;
        }
        
        if (instanceWorldKey == null || instanceWorldKey.isEmpty()) {
            plugin.getLogger().info("[房间 " + arena.getArenaName() + "] 没有世界实例需要删除");
            // 清除当前地图选择，确保下次游戏时重新投票
            arena.setCurrentMapId(null);
            if (voteManager != null) {
                voteManager.cancelVote(arena.getArenaName());
            }
            return;
        }
        
        // 安全检查：确保是实例世界，不是模板世界
        if (!isValidInstanceWorldKey(instanceWorldKey, arena.getArenaName())) {
            plugin.getLogger().severe("[房间 " + arena.getArenaName() + "] 安全警告：实例世界 key 格式不正确，拒绝删除！Key: " + instanceWorldKey);
            arena.setInstanceWorldKey(null); // 清除异常的 key
            arena.setCurrentMapId(null);
            if (voteManager != null) {
                voteManager.cancelVote(arena.getArenaName());
            }
            return;
        }
        
        // 使用 Worlds 插件删除世界实例
        plugin.getLogger().info("[房间 " + arena.getArenaName() + "] 游戏结束，正在删除世界实例: " + instanceWorldKey);
        
        if (WorldsIntegration.deleteWorldInstance(instanceWorldKey)) {
            plugin.getLogger().info("[房间 " + arena.getArenaName() + "] 世界实例已成功删除: " + instanceWorldKey);
            sendMessageToArena(arena.getArenaName(), "§a[房间 " + arena.getArenaName() + "] 世界实例已删除，下次选到时会自动克隆");
        } else {
            plugin.getLogger().warning("[房间 " + arena.getArenaName() + "] 删除世界实例失败: " + instanceWorldKey);
            sendMessageToArena(arena.getArenaName(), "§c[房间 " + arena.getArenaName() + "] 删除世界实例失败，请手动检查");
        }
        
        // 清除房间的世界实例 key（下次游戏时会重新创建/克隆）
        arena.setInstanceWorldKey(null);
        
        // 清除当前地图选择，确保下次游戏时重新投票
        arena.setCurrentMapId(null);
        
        // 清除投票管理器中保存的地图选择
        if (voteManager != null) {
            voteManager.cancelVote(arena.getArenaName());
        }
    }
    
    /**
     * 玩家加入房间
     * @param player 玩家
     * @param arenaName 房间名
     * @return 是否加入成功
     */
    public boolean joinArena(Player player, String arenaName) {
        GameArena arena = arenas.get(arenaName);
        if (arena == null) {
            return false; // 房间不存在
        }
        
        // 检查玩家是否已在其他房间
        String currentArena = playerArena.get(player);
        if (currentArena != null) {
            if (currentArena.equals(arenaName)) {
                // 玩家已经在当前房间中，检查房间状态
                GameInstance instance = arena.getGameInstance();
                // 如果游戏不在运行或准备中，允许重新加入（更新位置等）
                if (!instance.isRunning() && !instance.isPreparing()) {
                    // 允许重新加入（更新原始位置和传送）
                    if (instance.joinGame(player)) {
                        player.sendMessage("§a已重新加入房间 '§6" + arenaName + "§a'！");
                        
                        int playerCount = arena.getPlayerCount();
                        int minPlayers = config.getMinPlayers();
                        
                        // 检查地图投票
                        RandomItemPVP pluginInstance2 = (RandomItemPVP) plugin;
                        MapVoteManager voteManager2 = null;
                        if (pluginInstance2 != null) {
                            voteManager2 = pluginInstance2.getMapVoteManager();
                        }
                        
                        if (playerCount < minPlayers) {
                            player.sendMessage("§7当前玩家数：§e" + playerCount + "§7/§e" + minPlayers);
                            player.sendMessage("§7还差 §e" + (minPlayers - playerCount) + " §7人即可开始投票！");
                            cancelAutoStart(arenaName);
                        } else {
                            // 人数足够，检查是否需要开始投票
                            if (voteManager2 != null && !voteManager2.isVoting(arenaName)) {
                                // 达到最少玩家数，开始投票
                                if (voteManager2.startVote(arenaName, playerCount, minPlayers)) {
                                    sendMessageToArena(arenaName, "§a[房间 " + arenaName + "] 玩家数已足够，地图投票开始！");
                                }
                            } else if (voteManager2 != null && voteManager2.isVoting(arenaName)) {
                                // 正在投票，延长投票时间
                                voteManager2.extendVoteTime(arenaName, 5); // 延长5秒
                            }
                            
                            if (!arena.isPreparing() && !arena.isRunning()) {
                                if (autoStartTasks.containsKey(arenaName)) {
                                    cancelAutoStart(arenaName);
                                    sendMessageToArena(arenaName, "§e[房间 " + arenaName + "] 有新玩家加入！延迟计时已重置...");
                                }
                                scheduleAutoStart(arena);
                            }
                        }
                        return true;
                    }
                } else {
                    player.sendMessage("§e你已经在房间 '" + arenaName + "' 中了！");
                    return false;
                }
            } else {
                leaveArena(player); // 离开之前的房间
            }
        }
        
        // 检查房间状态
        if (!arena.canJoin()) {
            player.sendMessage("§c房间 '" + arenaName + "' 正在进行游戏，无法加入！");
            return false;
        }
        
        // 加入房间
        playerArena.put(player, arenaName);
        
        // 通过 GameInstance 加入游戏
        GameInstance instance = arena.getGameInstance();
        if (instance.joinGame(player)) {
            player.sendMessage("§a已加入房间 '§6" + arenaName + "§a'！");
            
            int playerCount = arena.getPlayerCount();
            // 使用当前地图的最少玩家数（如果有），否则使用全局配置
            String currentMapId = arena.getCurrentMapId();
            int minPlayers = currentMapId != null ? config.getMapMinPlayers(currentMapId) : config.getMinPlayers();
            
            // 检查地图投票
            RandomItemPVP pluginInstance = (RandomItemPVP) plugin;
            MapVoteManager voteManager = null;
            if (pluginInstance != null) {
                voteManager = pluginInstance.getMapVoteManager();
            }
            
            if (playerCount < minPlayers) {
                player.sendMessage("§7当前玩家数：§e" + playerCount + "§7/§e" + minPlayers);
                player.sendMessage("§7还差 §e" + (minPlayers - playerCount) + " §7人即可开始投票！");
                
                // 取消可能存在的自动启动任务（玩家数不足时）
                cancelAutoStart(arenaName);
            } else {
                // 人数足够，检查是否需要开始投票
                if (voteManager != null && !voteManager.isVoting(arenaName)) {
                    // 达到最少玩家数，开始投票
                    if (voteManager.startVote(arenaName, playerCount, minPlayers)) {
                        sendMessageToArena(arenaName, "§a[房间 " + arenaName + "] 玩家数已足够，地图投票开始！");
                    }
                } else if (voteManager != null && voteManager.isVoting(arenaName)) {
                    // 正在投票，延长投票时间
                    voteManager.extendVoteTime(arenaName, 5); // 延长5秒
                }
                
                // 人数足够，自动开始倒计时（带延迟）
                if (!arena.isPreparing() && !arena.isRunning()) {
                    // 如果已有延迟任务在运行，重置它（重新计时）
                    if (autoStartTasks.containsKey(arenaName)) {
                        cancelAutoStart(arenaName);
                        sendMessageToArena(arenaName, "§e[房间 " + arenaName + "] 有新玩家加入！延迟计时已重置...");
                    }
                    scheduleAutoStart(arena);
                }
            }
            
            return true;
        }
        
        return false;
    }
    
    /**
     * 从玩家房间映射中移除玩家（不发送消息，用于清理）
     * @param player 玩家
     */
    public void removePlayerFromArena(Player player) {
        playerArena.remove(player);
    }
    
    /**
     * 玩家离开房间
     * @param player 玩家
     * @return 是否离开成功
     */
    public boolean leaveArena(Player player) {
        String arenaName = playerArena.remove(player);
        if (arenaName == null) {
            return false; // 不在任何房间
        }
        
        GameArena arena = arenas.get(arenaName);
        if (arena != null) {
            GameInstance instance = arena.getGameInstance();
            
            // 尝试通过 GameInstance 离开
            boolean leftFromGame = instance.leaveGame(player);
            
            // 如果游戏运行中，使用强制离开
            if (!leftFromGame && instance.isRunning()) {
                leftFromGame = instance.forceLeaveGame(player);
                if (leftFromGame) {
                    player.sendMessage("§a已离开房间 '" + arenaName + "'（游戏运行中）");
                    return true;
                }
            }
            
            // 如果离开成功（非游戏运行中），leaveGame 内部已经发送了消息
            if (leftFromGame) {
                return true;
            }
            
            // 如果玩家不在参与者列表中（可能已经不在游戏中），但仍然在房间映射中
            // 确保传送回原位置
            Location originalLoc = instance.getPlayerOriginalLocation(player);
            if (originalLoc != null) {
                player.teleportAsync(originalLoc).thenRun(() -> {
                    player.sendMessage("§a已传送回原位置！");
                });
            } else if (arena.getWorld() != null) {
                // 没有原位置，传送到世界出生点
                Location spawnLoc = arena.getWorld().getSpawnLocation();
                player.teleportAsync(spawnLoc).thenRun(() -> {
                    player.sendMessage("§a已传送到世界出生点！");
                });
            }
        }
        
        player.sendMessage("§a已离开房间 '" + arenaName + "'");
        return true;
    }
    
    /**
     * 获取玩家所在房间
     * @param player 玩家
     * @return 房间名，如果不在房间则返回 null
     */
    public String getPlayerArena(Player player) {
        return playerArena.get(player);
    }
    
    /**
     * 检查玩家是否在某个房间中
     * @param player 玩家
     * @return 是否在房间中
     */
    public boolean isPlayerInArena(Player player) {
        return playerArena.containsKey(player);
    }
    
    /**
     * 获取房间
     * @param arenaName 房间名
     * @return 房间，如果不存在则返回 null
     */
    public GameArena getArena(String arenaName) {
        return arenas.get(arenaName);
    }
    
    /**
     * 获取所有房间
     * @return 房间名列表
     */
    public Set<String> getArenaNames() {
        return new HashSet<>(arenas.keySet());
    }
    
    /**
     * 获取所有房间
     * @return 房间列表
     */
    public Collection<GameArena> getArenas() {
        return new ArrayList<>(arenas.values());
    }
    
    /**
     * 计划自动启动（带延迟）
     */
    private void scheduleAutoStart(GameArena arena) {
        String arenaName = arena.getArenaName();
        
        // 如果已经有任务在运行，不重复启动
        if (autoStartTasks.containsKey(arenaName)) {
            return;
        }
        
        int delay = config.getAutoStartDelay();
        
        // 如果延迟为0，立即开始
        if (delay <= 0) {
            startCountdown(arena);
            return;
        }
        
        // 广播等待消息
        sendMessageToArena(arenaName, "§e[房间 " + arenaName + "] 玩家数已足够！将在 §6" + delay + "§e 秒后开始倒计时...");
        sendMessageToArena(arenaName, "§7使用 /ripvp join " + arenaName + " 快速加入");
        
        // 延迟后启动倒计时
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
            autoStartTasks.remove(arenaName);
            
            // 再次检查玩家数和状态
            GameInstance instance = arena.getGameInstance();
            int playerCount = instance.getParticipantCount();
            int minPlayers = config.getMinPlayers();
            
            if (playerCount >= minPlayers && !arena.isPreparing() && !arena.isRunning()) {
                startCountdown(arena);
            } else {
                sendMessageToArena(arenaName, "§c[房间 " + arenaName + "] 自动启动已取消（玩家数不足或游戏状态已改变）");
            }
        }, delay * 20L); // 延迟秒数转换为 ticks
        
        autoStartTasks.put(arenaName, task);
    }
    
    /**
     * 取消自动启动任务
     */
    private void cancelAutoStart(String arenaName) {
        ScheduledTask task = autoStartTasks.remove(arenaName);
        if (task != null) {
            task.cancel();
        }
    }
    
    /**
     * 开始倒计时
     */
    private void startCountdown(GameArena arena) {
        if (arena.isPreparing() || arena.isRunning()) {
            return;
        }
        
        // 取消可能存在的自动启动任务
        cancelAutoStart(arena.getArenaName());
        
        // 检查是否有地图投票正在进行，如果有则等待投票结束
        RandomItemPVP pluginInstance = (RandomItemPVP) plugin;
        if (pluginInstance != null) {
            MapVoteManager voteManager = pluginInstance.getMapVoteManager();
            if (voteManager != null && voteManager.isVoting(arena.getArenaName())) {
                // 投票正在进行，延迟启动倒计时（等待投票结束）
                String arenaName = arena.getArenaName();
                // 使用当前地图的投票时长（如果有），否则使用全局配置
                String currentMapId = arena.getCurrentMapId();
                int voteDuration = currentMapId != null ? config.getMapVoteDuration(currentMapId) : config.getVoteDuration();
                
                // 等待投票结束后再启动倒计时
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                    // 投票结束后检查选中地图并更新出生点
                    String selectedMapId = voteManager.getSelectedMap(arenaName);
                    if (selectedMapId != null) {
                        Location mapSpawn = config.loadMapSpawnLocation(selectedMapId);
                        if (mapSpawn != null) {
                        // 如果启用了世界实例化，创建独立的世界实例
                        if (config.isWorldInstancingEnabled() && WorldsIntegration.isWorldsAvailable()) {
                            setupWorldInstance(arena, selectedMapId, mapSpawn);
                        } else {
                            arena.setSpawnLocation(mapSpawn);
                        }
                        // 设置当前地图ID
                        arena.setCurrentMapId(selectedMapId);
                        String mapName = config.getMapName(selectedMapId);
                        sendMessageToArena(arenaName, "§a[房间 " + arenaName + "] 已选择地图：§e" + mapName);
                        } else {
                            // 地图加载失败，使用默认出生点或随机选择
                            selectRandomMap(arena);
                        }
                    } else {
                        // 投票未完成或没有选中地图，使用默认出生点或随机选择
                        selectRandomMap(arena);
                    }
                    
                    // 再次检查是否可以开始倒计时
                    startCountdown(arena);
                }, (voteDuration + 1) * 20L); // 等待投票时间结束 + 1秒
                return;
            } else {
                // 投票已结束或未开始，检查选中地图
                String selectedMapId = voteManager != null ? voteManager.getSelectedMap(arena.getArenaName()) : null;
                if (selectedMapId != null) {
                    Location mapSpawn = config.loadMapSpawnLocation(selectedMapId);
                    if (mapSpawn != null) {
                        // 如果启用了世界实例化，创建独立的世界实例
                        if (config.isWorldInstancingEnabled() && WorldsIntegration.isWorldsAvailable()) {
                            setupWorldInstance(arena, selectedMapId, mapSpawn);
                        } else {
                            arena.setSpawnLocation(mapSpawn);
                        }
                        // 设置当前地图ID
                        arena.setCurrentMapId(selectedMapId);
                        String mapName = config.getMapName(selectedMapId);
                        sendMessageToArena(arena.getArenaName(), "§a[房间 " + arena.getArenaName() + "] 已选择地图：§e" + mapName);
                    } else {
                        // 地图加载失败，使用随机选择
                        selectRandomMap(arena);
                    }
                } else if (arena.getSpawnLocation() == null) {
                    // 没有选中地图且没有出生点，随机选择
                    selectRandomMap(arena);
                }
            }
        }
        
        arena.setStatus(GameArena.ArenaStatus.PREPARING);
        
        GameInstance instance = arena.getGameInstance();
        Set<Player> participantsSet = instance.getParticipants();
        
        // 使用当前地图的最少玩家数（如果有），否则使用全局配置
        String currentMapId = arena.getCurrentMapId();
        int minPlayers = currentMapId != null ? config.getMapMinPlayers(currentMapId) : config.getMinPlayers();
        if (participantsSet.size() >= minPlayers) {
            // 确保有出生点
            if (arena.getSpawnLocation() == null) {
                // 尝试加载默认出生点
                Location defaultSpawn = config.loadSpawnLocation();
                if (defaultSpawn != null) {
                    arena.setSpawnLocation(defaultSpawn);
                } else {
                    sendMessageToArena(arena.getArenaName(), "§c[房间 " + arena.getArenaName() + "] 错误：未设置游戏出生点！无法开始游戏。");
                    return;
                }
            }
            
            List<Player> participants = new ArrayList<>(participantsSet);
            instance.startGameWithCountdown(participants);
            
            // 广播消息
            sendMessageToArena(arena.getArenaName(), "§a房间 '§6" + arena.getArenaName() + "§a' 开始倒计时！");
            sendMessageToArena(arena.getArenaName(), "§7使用 /ripvp join " + arena.getArenaName() + " 加入");
        }
    }
    
    /**
     * 设置世界实例（如果启用了世界实例化）
     * @param arena 房间
     * @param mapId 地图ID
     * @param templateSpawn 模板出生点
     */
    private void setupWorldInstance(GameArena arena, String mapId, Location templateSpawn) {
        if (!config.isWorldInstancingEnabled() || !WorldsIntegration.isWorldsAvailable()) {
            arena.setSpawnLocation(templateSpawn);
            return;
        }
        
        // 重要：从配置文件获取原始的模板世界 key，而不是从已加载的世界获取
        // 这样可以确保始终克隆模板世界，而不是克隆已经克隆过的实例世界
        String templateWorldKey = config.getString("arena.maps." + mapId + ".world");
        
        if (templateWorldKey == null || templateWorldKey.isEmpty()) {
            plugin.getLogger().warning("[房间 " + arena.getArenaName() + "] 地图 '" + mapId + "' 的世界配置缺失！无法创建世界实例。");
            arena.setSpawnLocation(templateSpawn);
            return;
        }
        
        // 安全检查：确保模板世界key不包含房间名后缀（不应该是实例世界）
        String normalizedArenaName = arena.getArenaName().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        if (templateWorldKey.toLowerCase().endsWith("_" + normalizedArenaName)) {
            plugin.getLogger().severe("[房间 " + arena.getArenaName() + "] 安全警告：检测到模板世界 key 可能是实例世界！Key: " + templateWorldKey);
            plugin.getLogger().severe("[房间 " + arena.getArenaName() + "] 请检查配置文件中的地图世界配置，确保使用原始模板世界，而不是实例世界！");
            arena.setSpawnLocation(templateSpawn);
            return;
        }
        
        // 生成实例世界的 key（格式：<模板key>_<房间名>）
        String instanceWorldKey = templateWorldKey + "_" + normalizedArenaName;
        
        plugin.getLogger().info("[房间 " + arena.getArenaName() + "] 正在从模板世界克隆实例: " + instanceWorldKey + " (模板: " + templateWorldKey + ")");
        plugin.getLogger().info("[房间 " + arena.getArenaName() + "] 安全验证：确认使用原始模板世界进行克隆");
        
        // 创建或获取世界实例
        World instanceWorld = WorldsIntegration.getOrCreateWorldInstance(templateWorldKey, instanceWorldKey);
        
        if (instanceWorld != null && instanceWorld != templateSpawn.getWorld()) {
            // 成功创建独立实例
            arena.setInstanceWorldKey(instanceWorldKey);
            arena.setWorld(instanceWorld);
            
            // 创建新的出生点（使用实例世界）
            Location instanceSpawn = new Location(
                instanceWorld,
                templateSpawn.getX(),
                templateSpawn.getY(),
                templateSpawn.getZ(),
                templateSpawn.getYaw(),
                templateSpawn.getPitch()
            );
            arena.setSpawnLocation(instanceSpawn);
            
            plugin.getLogger().info("[房间 " + arena.getArenaName() + "] 成功创建独立世界实例: " + instanceWorldKey);
            sendMessageToArena(arena.getArenaName(), "§a[房间 " + arena.getArenaName() + "] 已创建独立世界实例，玩家互不干扰");
        } else {
            // 创建失败，使用共享模式
            plugin.getLogger().warning("[房间 " + arena.getArenaName() + "] 无法创建独立世界实例，使用共享世界（可能导致房间间干扰）");
            arena.setSpawnLocation(templateSpawn);
        }
    }
    
    /**
     * 重新选择地图（在准备阶段可以调用）
     * @param arenaName 房间名
     * @param mapId 地图ID，如果为null则随机选择
     * @return 是否成功
     */
    public boolean reselectMap(String arenaName, String mapId) {
        GameArena arena = arenas.get(arenaName);
        if (arena == null) {
            return false; // 房间不存在
        }
        
        // 只能在准备阶段或等待阶段重新选择地图
        if (arena.isRunning()) {
            return false; // 游戏进行中不能重新选择
        }
        
        // 如果提供了地图ID，使用该地图；否则随机选择
        if (mapId != null && config.mapExists(mapId)) {
            Location mapSpawn = config.loadMapSpawnLocation(mapId);
            if (mapSpawn != null) {
                // 如果启用了世界实例化，清理旧的世界实例并创建新的
                if (config.isWorldInstancingEnabled() && WorldsIntegration.isWorldsAvailable()) {
                    String oldInstanceKey = arena.getInstanceWorldKey();
                    if (oldInstanceKey != null && isValidInstanceWorldKey(oldInstanceKey, arenaName)) {
                        // 清理旧的世界实例
                        WorldsIntegration.deleteWorldInstance(oldInstanceKey);
                        arena.setInstanceWorldKey(null);
                    }
                    setupWorldInstance(arena, mapId, mapSpawn);
                } else {
                    arena.setSpawnLocation(mapSpawn);
                }
                arena.setCurrentMapId(mapId);
                String mapName = config.getMapName(mapId);
                sendMessageToArena(arenaName, "§a[房间 " + arenaName + "] 已重新选择地图：§e" + mapName);
                return true;
            }
        } else {
            // 随机选择地图
            selectRandomMap(arena);
            return true;
        }
        
        return false;
    }
    
    /**
     * 随机选择地图（当投票未选中或地图加载失败时）
     * @param arena 房间
     */
    private void selectRandomMap(GameArena arena) {
        List<String> availableMaps = config.getAvailableMaps();
        if (!availableMaps.isEmpty()) {
            // 随机选择地图
            String randomMapId = availableMaps.get(new java.util.Random().nextInt(availableMaps.size()));
            Location mapSpawn = config.loadMapSpawnLocation(randomMapId);
            if (mapSpawn != null) {
                // 如果启用了世界实例化，创建独立的世界实例
                if (config.isWorldInstancingEnabled() && WorldsIntegration.isWorldsAvailable()) {
                    setupWorldInstance(arena, randomMapId, mapSpawn);
                } else {
                    arena.setSpawnLocation(mapSpawn);
                }
                // 设置当前地图ID
                arena.setCurrentMapId(randomMapId);
                String mapName = config.getMapName(randomMapId);
                sendMessageToArena(arena.getArenaName(), "§a[房间 " + arena.getArenaName() + "] 随机选择地图：§e" + mapName);
            } else {
                // 随机地图加载失败，使用默认出生点
                Location defaultSpawn = config.loadSpawnLocation();
                if (defaultSpawn != null) {
                    arena.setSpawnLocation(defaultSpawn);
                    arena.setCurrentMapId(null); // 未选择地图
                    sendMessageToArena(arena.getArenaName(), "§c[房间 " + arena.getArenaName() + "] 地图加载失败，使用默认出生点");
                }
            }
        } else {
            // 没有可用地图，使用默认出生点
            Location defaultSpawn = config.loadSpawnLocation();
            if (defaultSpawn != null) {
                arena.setSpawnLocation(defaultSpawn);
                arena.setCurrentMapId(null); // 未选择地图
            }
        }
    }
    
    /**
     * 保存房间配置
     */
    private void saveArenaConfig(GameArena arena) {
        // TODO: 实现配置保存
        // 保存到 config.yml 的 arenas 部分
    }
    
    /**
     * 删除房间配置
     */
    private void removeArenaConfig(String arenaName) {
        // TODO: 实现配置删除
    }
    
    /**
     * 从配置文件加载所有房间
     */
    public void loadArenas() {
        // TODO: 从配置文件加载房间
    }
    
    /**
     * 保存所有房间到配置文件
     */
    public void saveAllArenas() {
        // TODO: 保存所有房间到配置文件
    }
}

