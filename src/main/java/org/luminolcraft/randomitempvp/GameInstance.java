package org.luminolcraft.randomitempvp;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.WorldBorder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单个房间的游戏实例
 * 管理单个房间的游戏状态和玩家
 */
public class GameInstance {
    private final GameArena arena;
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final PlayerStatsManager statsManager;
    
    // 游戏状态
    private volatile boolean gameRunning = false;
    private volatile boolean preparing = false;
    private volatile boolean eventTriggered = false;
    private final Random random = new Random();
    
    // 参与者管理
    private final Set<Player> participants = ConcurrentHashMap.newKeySet();
    private final Map<Player, Location> playerOriginalLocations = new HashMap<>();
    private final Map<Player, ItemStack[]> playerOriginalInventories = new HashMap<>(); // 玩家原始物品
    private final Set<Player> alivePlayers = ConcurrentHashMap.newKeySet(); // 存活玩家列表
    private final Map<Player, GameMode> playerGameModes = new HashMap<>();
    private Location gatherLocation = null;
    private Location spawnLocation = null; // 游戏出生点
    
    // 观战者管理（在游戏区域内但不在参与者列表中的玩家）
    private final Map<Player, ItemStack[]> spectatorInventories = new HashMap<>();
    private final Map<Player, GameMode> spectatorGameModes = new HashMap<>();
    private final Map<Player, Location> spectatorOriginalLocations = new HashMap<>(); // 观战者原始位置
    
    // 任务管理
    private ScheduledTask countdownTask = null;
    private ScheduledTask itemTask = null;
    private ScheduledTask eventTask = null;
    private ScheduledTask borderShrinkTask = null;
    private ScheduledTask aliveCountTask = null;
    
    // 动态事件任务（如箭雨等，需要在停止时取消）
    private final List<ScheduledTask> dynamicEventTasks = Collections.synchronizedList(new ArrayList<>());
    
    // 游戏对象
    private WorldBorder gameBorder = null;
    // 已移除清理相关字段：不再需要跟踪掉落物、怪物、方块等
    // 游戏结束后直接使用 Worlds 插件删除世界实例即可
    private int lastAliveCount = -1;
    
    public GameInstance(GameArena arena, JavaPlugin plugin, ConfigManager config, PlayerStatsManager statsManager) {
        this.arena = arena;
        this.plugin = plugin;
        this.config = config;
        this.statsManager = statsManager;
    }
    
    /**
     * 玩家加入游戏
     */
    public boolean joinGame(Player player) {
        if (gameRunning) return false;
        
        // 如果玩家已经在列表中，允许重新加入（更新原始位置和传送）
        participants.add(player);
        
        // 记录原始位置（如果之前没有记录，或更新为当前位置）
        if (playerOriginalLocations.get(player) == null) {
            playerOriginalLocations.put(player, player.getLocation().clone());
        }
        
        // 保存原始物品（如果之前没有记录）
        if (playerOriginalInventories.get(player) == null && player.getInventory() != null) {
            ItemStack[] originalItems = new ItemStack[player.getInventory().getSize()];
            for (int i = 0; i < originalItems.length; i++) {
                ItemStack item = player.getInventory().getItem(i);
                originalItems[i] = item != null ? item.clone() : null;
            }
            playerOriginalInventories.put(player, originalItems);
        }
        
        // 设置集合点为房间出生点
        Location spawnLoc = arena.getSpawnLocation();
        if (spawnLoc != null) {
            gatherLocation = spawnLoc;
        } else {
            // 如果出生点为 null，使用临时位置（不应该发生，但作为保护）
            plugin.getLogger().warning("[房间 " + arena.getArenaName() + "] 出生点为 null！使用临时位置。");
            gatherLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        
        // 传送到集合点
        if (gatherLocation != null) {
            player.teleportAsync(gatherLocation).thenRun(() -> {
                player.sendMessage("§a已传送到房间集合点！");
            });
        }
        
        return true; // 无论是新玩家还是已存在的玩家，都返回 true
    }
    
    /**
     * 玩家离开游戏
     */
    public boolean leaveGame(Player player) {
        if (gameRunning) return false; // 游戏运行中需要强制离开
        if (!participants.remove(player)) return false; // 不在列表中
        
        // 恢复原始物品（如果有保存）
        ItemStack[] originalItems = playerOriginalInventories.remove(player);
        if (originalItems != null && player.isOnline()) {
            player.getInventory().clear();
            ItemStack[] restoredItems = new ItemStack[originalItems.length];
            for (int i = 0; i < originalItems.length; i++) {
                restoredItems[i] = originalItems[i] != null ? originalItems[i].clone() : null;
            }
            player.getInventory().setContents(restoredItems);
        }
        
        // 传送回原位置
        Location originalLoc = playerOriginalLocations.remove(player);
        if (originalLoc != null) {
            player.teleportAsync(originalLoc).thenRun(() -> {
                player.sendMessage("§a已传送回原位置！");
            });
        }
        
        return true;
    }
    
    /**
     * 强制离开（即使在游戏运行中）
     */
    public boolean forceLeaveGame(Player player) {
        if (!participants.contains(player)) return false; // 不在列表中
        
        // 从存活列表中移除（在移除participants之前）
        alivePlayers.remove(player);
        
        // 如果游戏正在运行，先检查是否应该结束游戏（在移除participants之前）
        if (gameRunning) {
            // 先移除当前玩家，然后检查剩余存活玩家
            participants.remove(player);
            
            List<Player> survivors = getSurvivingPlayers();
            if (survivors.size() <= 1) {
                // 只剩1人或0人，游戏应该结束
                gameRunning = false;
                arena.setStatus(GameArena.ArenaStatus.ENDING);
                
                // 5秒延迟后再处理结果
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                    if (survivors.isEmpty()) {
                        Bukkit.broadcastMessage("§c[房间 " + arena.getArenaName() + "] 游戏结束！所有玩家都已离开！");
                        // 所有玩家都离开了，记录失败
                        for (Player p : participants) {
                            if (p.isOnline()) {
                                statsManager.recordLoss(p);
                            }
                        }
                    } else {
                        Player winner = survivors.get(0);
                        Bukkit.broadcastMessage("§a[房间 " + arena.getArenaName() + "] §6" + winner.getName() + " §a获胜！");
                        statsManager.recordWin(winner);
                        // 失败者记录失败
                        for (Player p : participants) {
                            if (p != winner && p.isOnline()) {
                                statsManager.recordLoss(p);
                            }
                        }
                    }
                    
                    // 清理并重置
                    stopGame();
                }, 100L); // 5秒 = 100 ticks
            }
        } else {
            // 游戏未运行，直接移除
            participants.remove(player);
        }
        
        // 获取安全的传送位置
        Location safeLocation = getSafeTeleportLocation(player);
        
        // 传送玩家到安全位置
        if (safeLocation != null) {
            player.teleportAsync(safeLocation).thenRun(() -> {
                player.sendMessage("§a已传送回安全位置！");
                // 恢复游戏模式（如果游戏运行中离开）
                if (gameRunning) {
                    GameMode originalMode = playerGameModes.get(player);
                    if (originalMode != null) {
                        player.setGameMode(originalMode);
                    } else {
                        player.setGameMode(GameMode.SURVIVAL);
                    }
                }
            });
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取安全的传送位置
     */
    private Location getSafeTeleportLocation(Player player) {
        // 首先尝试使用原位置
        Location originalLoc = playerOriginalLocations.remove(player);
        if (originalLoc != null && originalLoc.getWorld() != null) {
            // 检查原位置是否安全
            Location safeLoc = findSafeLocation(originalLoc);
            if (safeLoc != null) {
                return safeLoc;
            }
        }
        
        // 如果原位置不安全，尝试传送到世界出生点
        if (arena.getWorld() != null) {
            Location spawnLoc = arena.getWorld().getSpawnLocation();
            Location safeSpawn = findSafeLocation(spawnLoc);
            if (safeSpawn != null) {
                return safeSpawn;
            }
        }
        
        // 如果都不安全，传送到玩家当前世界的出生点
        if (player.getWorld() != null) {
            Location worldSpawn = player.getWorld().getSpawnLocation();
            return findSafeLocation(worldSpawn);
        }
        
        return null;
    }
    
    /**
     * 查找安全的位置（确保不在虚空、边界内等）
     */
    private Location findSafeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        // 检查Y坐标是否有效
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            // Y坐标无效，找到最高方块
            y = world.getHighestBlockYAt(x, z);
            if (y < world.getMinHeight()) {
                y = world.getMinHeight() + 1;
            }
        }
        
        // 确保不在虚空
        while (y > world.getMinHeight() && world.getBlockAt(x, y, z).getType() == Material.AIR) {
            y--;
        }
        
        // 如果Y坐标仍然无效，使用世界出生点的高度
        if (y < world.getMinHeight()) {
            y = Math.max(world.getMinHeight() + 1, world.getSpawnLocation().getBlockY());
        }
        
        // 检查是否在世界边界内
        WorldBorder border = world.getWorldBorder();
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();
        double size = border.getSize() / 2;
        
        // 如果位置在世界边界外，调整到边界内
        if (Math.abs(x - centerX) > size || Math.abs(z - centerZ) > size) {
            // 调整到边界内
            if (x > centerX + size) x = (int)(centerX + size - 1);
            if (x < centerX - size) x = (int)(centerX - size + 1);
            if (z > centerZ + size) z = (int)(centerZ + size - 1);
            if (z < centerZ - size) z = (int)(centerZ - size + 1);
        }
        
        // 确保Y坐标是安全的高度（站在方块上）
        int safeY = world.getHighestBlockYAt(x, z);
        if (safeY < world.getMinHeight()) {
            safeY = world.getMinHeight() + 1;
        }
        
        // 返回安全位置（站在方块上方）
        return new Location(world, x + 0.5, safeY + 1, z + 0.5);
    }
    
    /**
     * 获取玩家的原位置（不删除）
     */
    public Location getPlayerOriginalLocation(Player player) {
        return playerOriginalLocations.get(player);
    }
    
    /**
     * 强制移除玩家（不传送）
     */
    public boolean removeParticipant(Player player) {
        participants.remove(player);
        playerOriginalLocations.remove(player);
        playerOriginalInventories.remove(player);
        return true;
    }
    
    /**
     * 添加观战者（在游戏区域内但不在参与者列表中的玩家）
     */
    public void addSpectator(Player player) {
        if (spectatorInventories.containsKey(player)) {
            return; // 已经是观战者
        }
        
        // 保存观战者的原始位置（如果之前没有保存）
        if (!spectatorOriginalLocations.containsKey(player)) {
            spectatorOriginalLocations.put(player, player.getLocation().clone());
        }
        
        // 保存观战者的物品和游戏模式
        if (player.getInventory() != null) {
            ItemStack[] originalItems = new ItemStack[player.getInventory().getSize()];
            for (int i = 0; i < originalItems.length; i++) {
                ItemStack item = player.getInventory().getItem(i);
                originalItems[i] = item != null ? item.clone() : null;
            }
            spectatorInventories.put(player, originalItems);
        }
        spectatorGameModes.put(player, player.getGameMode());
        
        // 清除物品（观战者不需要物品）
        player.getInventory().clear();
    }
    
    /**
     * 移除观战者
     */
    public void removeSpectator(Player player) {
        spectatorInventories.remove(player);
        spectatorGameModes.remove(player);
        spectatorOriginalLocations.remove(player);
    }
    
    /**
     * 检查玩家是否是观战者
     */
    public boolean isSpectator(Player player) {
        return spectatorInventories.containsKey(player);
    }
    
    /**
     * 获取参与者数量
     */
    public int getParticipantCount() {
        return participants.size();
    }
    
    /**
     * 获取参与者列表
     */
    public Set<Player> getParticipants() {
        return new HashSet<>(participants);
    }
    
    /**
     * 带倒计时启动游戏
     */
    public void startGameWithCountdown(List<Player> initialParticipants) {
        if (gameRunning || preparing) return;
        
        // 检查是否设置了出生点
        Location spawnLoc = arena.getSpawnLocation();
        if (spawnLoc == null) {
            Bukkit.broadcastMessage("§c[房间 " + arena.getArenaName() + "] 错误：未设置游戏出生点！");
            return;
        }
        
        preparing = true;
        gatherLocation = spawnLoc.clone();
        
        // 首先将初始参与者添加到列表中
        for (Player player : initialParticipants) {
            participants.add(player); // add() 会自动处理重复
        }
        
        // 记录并传送所有参与者（包括之前就在房间中的玩家）
        for (Player player : new ArrayList<>(participants)) {
            // 只处理在线玩家
            if (!player.isOnline()) {
                participants.remove(player);
                continue;
            }
            
            // 记录原始位置（如果之前没有记录）
            if (playerOriginalLocations.get(player) == null) {
                playerOriginalLocations.put(player, player.getLocation().clone());
            }
            
            // 传送到集合点
            player.teleportAsync(gatherLocation).thenRun(() -> {
                player.sendMessage("§a已传送到集合点！");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            });
        }
        
        // 广播消息
        // 向房间内的玩家发送消息
        for (Player p : participants) {
            if (p.isOnline()) {
                p.sendMessage("§a[房间 " + arena.getArenaName() + "] 游戏准备中！参与者：§6" + participants.size() + " §a人");
                p.sendMessage("§7使用 /ripvp join " + arena.getArenaName() + " 加入");
            }
        }
        
        // 使用当前地图的倒计时配置（如果有），否则使用全局配置
        String currentMapId = arena.getCurrentMapId();
        int countdown = currentMapId != null ? config.getMapStartCountdown(currentMapId) : config.getStartCountdown();
        final int[] currentCount = {countdown}; // 使用数组以便在 lambda 中修改
        
        // 使用定时任务进行倒计时
        countdownTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            // 首先检查任务是否已被取消（通过检查 countdownTask 是否为 null 或准备状态）
            if (countdownTask == null || !preparing || task.isCancelled()) {
                // 游戏被取消或任务已被取消
                if (countdownTask != null) {
                    countdownTask = null;
                }
                task.cancel();
                return;
            }
            
            int remaining = currentCount[0];
            
            if (remaining <= 0) {
                // 倒计时结束，启动游戏
                task.cancel();
                countdownTask = null;
                preparing = false;
                
                // 检查参与者数量（使用当前地图的配置）
                // 注意：currentMapId 已在外部作用域声明（第424行）
                int minPlayers = currentMapId != null ? config.getMapMinPlayers(currentMapId) : config.getMinPlayers();
                if (participants.size() < minPlayers) {
                    Bukkit.broadcastMessage("§c[房间 " + arena.getArenaName() + "] 参与者不足！游戏取消。需要至少 " + minPlayers + " 人");
                    cancelGame();
                    return;
                }
                
                // 开始游戏
                startRound();
                return;
            }
            
            // 显示倒计时（只给房间内的玩家看）
            List<Player> roomPlayers = new ArrayList<>(participants);
            if (remaining <= 3) {
                // 最后3秒：超大标题 + 强音效
                for (Player p : roomPlayers) {
                    if (p.isOnline()) {
                        p.sendTitle("§6§l" + remaining, "§e游戏即将开始", 0, 20, 10);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                    }
                }
                Bukkit.broadcastMessage("§e[房间 " + arena.getArenaName() + "] 游戏将在 §6" + remaining + " §e秒后开始... (参与者：§6" + participants.size() + "§e人)");
            } else if (remaining <= 10) {
                // 10秒内：标题 + 音效
                for (Player p : roomPlayers) {
                    if (p.isOnline()) {
                        p.sendTitle("§e" + remaining, "§7游戏准备中... (参与者：§6" + participants.size() + "§7人)", 0, 25, 10);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.8f, 1.0f);
                    }
                }
                Bukkit.broadcastMessage("§e[房间 " + arena.getArenaName() + "] 游戏将在 §6" + remaining + " §e秒后开始... (参与者：§6" + participants.size() + "§e人)");
            } else {
                // 10秒以上：标题（较小） + 聊天消息
                for (Player p : roomPlayers) {
                    if (p.isOnline()) {
                        p.sendTitle("", "§7游戏将在 §e" + remaining + "§7 秒后开始 (参与者：§6" + participants.size() + "§7人)", 5, 20, 5);
                    }
                }
                Bukkit.broadcastMessage("§e[房间 " + arena.getArenaName() + "] 游戏将在 §6" + remaining + " §e秒后开始... (参与者：§6" + participants.size() + "§e人)");
            }
            
            currentCount[0]--;
        }, 1, 20L); // 延迟1 tick，每20 ticks（1秒）执行一次
    }
    
    /**
     * 取消游戏
     */
    public void cancelGame() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        preparing = false;
        arena.setStatus(GameArena.ArenaStatus.WAITING);
        
        // 将所有参与者传送回原位置
        for (Player player : new ArrayList<>(participants)) {
            Location originalLoc = playerOriginalLocations.remove(player);
            if (originalLoc != null && player.isOnline()) {
                player.teleportAsync(originalLoc).thenRun(() -> {
                    player.sendMessage("§c[房间 " + arena.getArenaName() + "] 游戏已取消，已传送回原位置！");
                });
            }
        }
        
        participants.clear();
        gatherLocation = null;
        Bukkit.broadcastMessage("§c[房间 " + arena.getArenaName() + "] 游戏已取消！");
    }
    
    public boolean isRunning() {
        return gameRunning;
    }
    
    public boolean isPreparing() {
        return preparing;
    }
    
    /**
     * 强制停止游戏（用于删除房间时）
     */
    public void forceStop() {
        // 首先取消倒计时任务（最重要的一步）
        ScheduledTask taskToCancel = countdownTask;
        if (taskToCancel != null) {
            try {
                taskToCancel.cancel();
            } catch (Exception e) {
                // 忽略取消异常
            }
            countdownTask = null; // 立即设为 null，让任务检测到
        }
        
        // 取消所有游戏任务
        cancelAllTasks();
        
        // 不再清理竞技场，直接删除世界实例即可
        
        // 重置边界（如果有）
        if (gameBorder != null) {
            gameBorder.setSize(30000000, 0); // 重置为超大值
        }
        
        // 然后设置准备和运行标志为 false
        preparing = false;
        gameRunning = false;
        
        // 设置房间状态为 WAITING（防止状态不同步）
        arena.setStatus(GameArena.ArenaStatus.WAITING);
        
        // 恢复并传送所有参与者
        for (Player player : new ArrayList<>(participants)) {
            if (player.isOnline()) {
                // 恢复游戏模式
                GameMode originalMode = playerGameModes.get(player);
                if (originalMode != null) {
                    player.setGameMode(originalMode);
                } else {
                    player.setGameMode(GameMode.SURVIVAL);
                }
                
                // 清除游戏物品
                player.getInventory().clear();
                
                // 恢复原始物品（如果有保存）
                ItemStack[] originalItems = playerOriginalInventories.remove(player);
                if (originalItems != null) {
                    ItemStack[] restoredItems = new ItemStack[originalItems.length];
                    for (int i = 0; i < originalItems.length; i++) {
                        restoredItems[i] = originalItems[i] != null ? originalItems[i].clone() : null;
                    }
                    player.getInventory().setContents(restoredItems);
                }
                
                // 传送回原位置
                Location originalLoc = playerOriginalLocations.remove(player);
                if (originalLoc != null) {
                    // 使用安全的传送位置
                    Location safeLoc = findSafeLocation(originalLoc);
                    if (safeLoc != null) {
                        player.teleportAsync(safeLoc).thenRun(() -> {
                            player.sendMessage("§c[房间 " + arena.getArenaName() + "] 房间已删除，已传送回原位置！");
                        });
                    } else {
                        // 如果原位置不安全，传送到世界出生点
                        if (arena.getWorld() != null) {
                            Location spawnLoc = arena.getWorld().getSpawnLocation();
                            Location safeSpawn = findSafeLocation(spawnLoc);
                            if (safeSpawn != null) {
                                player.teleportAsync(safeSpawn).thenRun(() -> {
                                    player.sendMessage("§c[房间 " + arena.getArenaName() + "] 房间已删除，已传送到世界出生点！");
                                });
                            }
                        }
                    }
                } else {
                    // 没有原位置，传送到世界出生点
                    if (arena.getWorld() != null) {
                        Location spawnLoc = arena.getWorld().getSpawnLocation();
                        Location safeSpawn = findSafeLocation(spawnLoc);
                        if (safeSpawn != null) {
                            player.teleportAsync(safeSpawn).thenRun(() -> {
                                player.sendMessage("§c[房间 " + arena.getArenaName() + "] 房间已删除，已传送到世界出生点！");
                            });
                        }
                    }
                }
            }
        }
        
        // 恢复并传送所有观战者
        for (Player spectator : new ArrayList<>(spectatorInventories.keySet())) {
            if (spectator.isOnline()) {
                // 恢复游戏模式（在传送前，避免摔死）
                GameMode originalMode = spectatorGameModes.remove(spectator);
                if (originalMode != null) {
                    spectator.setGameMode(originalMode);
                } else {
                    spectator.setGameMode(GameMode.SURVIVAL);
                }
                
                // 恢复原始物品
                ItemStack[] originalItems = spectatorInventories.remove(spectator);
                if (originalItems != null) {
                    ItemStack[] restoredItems = new ItemStack[originalItems.length];
                    for (int i = 0; i < originalItems.length; i++) {
                        restoredItems[i] = originalItems[i] != null ? originalItems[i].clone() : null;
                    }
                    spectator.getInventory().setContents(restoredItems);
                }
                
                // 传送观战者回原始位置（如果有保存）
                Location originalLoc = spectatorOriginalLocations.remove(spectator);
                if (originalLoc != null && originalLoc.getWorld() != null) {
                    // 使用安全的传送位置
                    Location safeLoc = findSafeLocation(originalLoc);
                    if (safeLoc != null) {
                        spectator.teleportAsync(safeLoc).thenRun(() -> {
                            spectator.sendMessage("§c[房间 " + arena.getArenaName() + "] 房间已删除，已传送回原位置！");
                        });
                    } else {
                        // 如果原位置不安全，传送到世界出生点
                        if (arena.getWorld() != null) {
                            Location spawnLoc = arena.getWorld().getSpawnLocation();
                            Location safeSpawn = findSafeLocation(spawnLoc);
                            if (safeSpawn != null) {
                                spectator.teleportAsync(safeSpawn).thenRun(() -> {
                                    spectator.sendMessage("§c[房间 " + arena.getArenaName() + "] 房间已删除，已传送到世界出生点！");
                                });
                            }
                        }
                    }
                } else {
                    // 没有原位置，检查是否在高空，如果是则传送到安全位置
                    Location spectatorLoc = spectator.getLocation();
                    if (spectatorLoc.getY() > 100) {
                        // 在高空，传送到安全位置
                        if (arena.getWorld() != null) {
                            Location spawnLoc = arena.getWorld().getSpawnLocation();
                            Location safeSpawn = findSafeLocation(spawnLoc);
                            if (safeSpawn != null) {
                                spectator.teleportAsync(safeSpawn).thenRun(() -> {
                                    spectator.sendMessage("§c[房间 " + arena.getArenaName() + "] 房间已删除，已传送到安全位置！");
                                });
                            }
                        }
                    } else {
                        spectator.sendMessage("§c[房间 " + arena.getArenaName() + "] 房间已删除，已恢复正常模式！");
                    }
                }
            }
        }
        
        // 清空所有数据
        participants.clear();
        playerOriginalLocations.clear();
        playerOriginalInventories.clear();
        alivePlayers.clear();
        spectatorInventories.clear();
        spectatorGameModes.clear();
        spectatorOriginalLocations.clear();
        gatherLocation = null;
        spawnLocation = null;
        gameBorder = null;
        
        Bukkit.broadcastMessage("§c[房间 " + arena.getArenaName() + "] 房间已删除，游戏已强制停止！");
    }
    
    /**
     * 开始游戏回合
     */
    private void startRound() {
        gameRunning = true;
        eventTriggered = false;
        
        // 获取出生点
        spawnLocation = arena.getSpawnLocation();
        if (spawnLocation == null) {
            Bukkit.broadcastMessage("§c[房间 " + arena.getArenaName() + "] 错误：未设置游戏出生点！");
            gameRunning = false;
            arena.setStatus(GameArena.ArenaStatus.WAITING);
            return;
        }
        
        // 设置房间状态为运行中
        arena.setStatus(GameArena.ArenaStatus.RUNNING);
        
        // 取消所有任务
        cancelAllTasks();
        
        // 确保所有在房间中的在线玩家都在参与者列表中
        // 从 ArenaManager 获取房间中的所有玩家
        RandomItemPVP pluginInstance = RandomItemPVP.getInstance();
        if (pluginInstance != null) {
            ArenaManager arenaManager = pluginInstance.getArenaManager();
            if (arenaManager != null) {
                String arenaName = arena.getArenaName();
                // 使用当前地图的半径配置（如果有），否则使用全局配置
                String currentMapId = arena.getCurrentMapId();
                int radius = currentMapId != null ? config.getMapRadius(currentMapId) : config.getArenaRadius();
                World arenaWorld = spawnLocation.getWorld();
                
                // 遍历所有在线玩家
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.isOnline()) continue;
                    
                    Location playerLoc = p.getLocation();
                    
                    // 检查玩家是否在此房间中（在 ArenaManager 中）
                    if (arenaManager.isPlayerInArena(p)) {
                        String playerArenaName = arenaManager.getPlayerArena(p);
                        if (arenaName.equals(playerArenaName)) {
                            // 此玩家在此房间中，确保在参与者列表中
                            if (!participants.contains(p)) {
                                participants.add(p);
                                plugin.getLogger().info("修复：玩家 " + p.getName() + " 不在参与者列表中，已添加");
                            }
                            // 确保有原始位置记录
                            if (playerOriginalLocations.get(p) == null) {
                                playerOriginalLocations.put(p, p.getLocation().clone());
                            }
                        }
                    } else {
                        // 玩家不在房间中，但可能在游戏区域内
                        // 检查玩家是否在游戏区域内（但不在参与者列表中）
                        if (arenaWorld != null && playerLoc.getWorld().equals(arenaWorld)) {
                            double distance = playerLoc.distance(spawnLocation);
                            
                            // 如果玩家在游戏区域内（使用 2 倍半径作为检测范围），设置为观战者
                            if (distance <= radius * 2 && !participants.contains(p) && !spectatorInventories.containsKey(p)) {
                                addSpectator(p);
                                // addSpectator 已经保存了游戏模式，这里设置为旁观者
                                if (p.getGameMode() != GameMode.SPECTATOR) {
                                    p.setGameMode(GameMode.SPECTATOR);
                                }
                                p.sendMessage("§e你进入了游戏区域！已切换为观战模式。");
                                p.sendMessage("§7游戏结束后会自动恢复。");
                            }
                        }
                    }
                }
            }
        }
        
        // 清理离线玩家和不在房间中的玩家
        participants.removeIf(player -> {
            if (!player.isOnline()) {
                return true; // 移除离线玩家
            }
            // 检查玩家是否还在这个房间中
            if (pluginInstance != null) {
                ArenaManager arenaManager = pluginInstance.getArenaManager();
                if (arenaManager != null) {
                    String playerArenaName = arenaManager.getPlayerArena(player);
                    if (!arena.getArenaName().equals(playerArenaName)) {
                        return true; // 玩家不在这个房间中，移除
                    }
                }
            }
            return false;
        });
        
        // 获取游戏世界
        World gameWorld = spawnLocation.getWorld();
        if (gameWorld == null) {
            Bukkit.broadcastMessage("§c[房间 " + arena.getArenaName() + "] 错误：游戏世界为 null！");
            gameRunning = false;
            arena.setStatus(GameArena.ArenaStatus.WAITING);
            return;
        }
        
        // 初始化存活玩家列表（只添加在线的参与者），并确保所有玩家都在正确的世界
        alivePlayers.clear();
        for (Player player : new ArrayList<>(participants)) {
            if (player.isOnline()) {
                // 确保玩家在正确的世界
                if (player.getWorld() != gameWorld) {
                    // 玩家不在游戏世界，先传送到游戏世界
                    Location tempLoc = gameWorld.getSpawnLocation();
                    player.teleportAsync(tempLoc).thenRun(() -> {
                        player.sendMessage("§e你已被传送到游戏世界！");
                    });
                }
                alivePlayers.add(player);
            } else {
                participants.remove(player);
            }
        }
        
        // 方块操作必须在区域调度器中执行（Folia 要求）
        Bukkit.getRegionScheduler().run(plugin, spawnLocation, task -> {
            generateArena();
        });
        setupWorldBorder();
        resetPlayers();
        startItemTask();
        startEventTask();
        startBorderShrink();
        
        // 启动空投系统
        AirdropManager airdropManager = RandomItemPVP.getInstance().getAirdropManager();
        if (airdropManager != null && spawnLocation != null) {
            airdropManager.startAirdrop(spawnLocation);
        }
        
        Bukkit.broadcastMessage("§a[房间 " + arena.getArenaName() + "] 新一轮随机物品PVP开始！");
        Bukkit.broadcastMessage("§e击杀敌人可获得回血和随机物品奖励！");
        Bukkit.broadcastMessage("§6空投系统已激活，稀有装备即将空降！");
        
        // 启动存活人数显示
        startAliveCountDisplay();
    }
    
    /**
     * 取消所有任务
     */
    private void cancelAllTasks() {
        if (itemTask != null) { itemTask.cancel(); itemTask = null; }
        if (eventTask != null) { eventTask.cancel(); eventTask = null; }
        if (borderShrinkTask != null) { borderShrinkTask.cancel(); borderShrinkTask = null; }
        if (aliveCountTask != null) { aliveCountTask.cancel(); aliveCountTask = null; }
        
        // 取消所有动态事件任务
        synchronized (dynamicEventTasks) {
            for (ScheduledTask task : new ArrayList<>(dynamicEventTasks)) {
                if (task != null && !task.isCancelled()) {
                    try {
                        task.cancel();
                    } catch (Exception e) {
                        // 忽略取消异常
                    }
                }
            }
            dynamicEventTasks.clear();
        }
    }
    
    /**
     * 设置世界边界
     */
    private void setupWorldBorder() {
        if (spawnLocation == null) return;
        World world = spawnLocation.getWorld();
        gameBorder = world.getWorldBorder();
        gameBorder.setCenter(spawnLocation);
        // 立即设置边界大小（0秒过渡，避免从上一局的超大值慢慢过渡）
        // 使用当前地图的半径配置（如果有），否则使用全局配置
        String currentMapId = arena.getCurrentMapId();
        int radius = currentMapId != null ? config.getMapRadius(currentMapId) : config.getArenaRadius();
        gameBorder.setSize(radius * 2, 0);
        gameBorder.setDamageBuffer(0);
        gameBorder.setDamageAmount(config.getBorderDamageAmount());
        gameBorder.setWarningDistance(5);
        gameBorder.setWarningTime(10);
    }
    
    /**
     * 获取存活玩家列表
     */
    public List<Player> getSurvivingPlayers() {
        List<Player> survivors = new ArrayList<>();
        for (Player player : alivePlayers) {
            if (player.isOnline() && player.getGameMode() != GameMode.SPECTATOR) {
                survivors.add(player);
            }
        }
        return survivors;
    }
    
    /**
     * 生成竞技场（基岩柱子）
     */
    private void generateArena() {
        if (spawnLocation == null || gatherLocation == null) return;
        World world = spawnLocation.getWorld();
        
        // 获取参与者列表，围成一个圈
        List<Player> players = new ArrayList<>(participants);
        int playerCount = players.size();
        if (playerCount == 0) return;
        
        // 计算玩家间的角度间隔（围成一个圈）
        double angleStep = 2 * Math.PI / playerCount;
        // 使用当前地图的半径配置（如果有），否则使用全局配置
        String currentMapId = arena.getCurrentMapId();
        int arenaRadius = currentMapId != null ? config.getMapRadius(currentMapId) : config.getArenaRadius();
        int circleRadius = Math.min(20, arenaRadius / 2); // 圆圈半径（玩家之间的距离）
        int pillarHeight = 128; // 基岩柱子高度
        
        // 使用集合点的高度作为基准（所有人在同一位置，确保安全）
        int baseGroundY = gatherLocation.getBlockY();
        
        for (int i = 0; i < playerCount; i++) {
            Player player = players.get(i);
            
            // 检查玩家是否在线
            if (!player.isOnline()) {
                continue;
            }
            
            // 计算该玩家的位置（围绕竞技场中心圆形分布）
            double angle = i * angleStep;
            int pillarX = spawnLocation.getBlockX() + (int)(Math.cos(angle) * circleRadius);
            int pillarZ = spawnLocation.getBlockZ() + (int)(Math.sin(angle) * circleRadius);
            
            // 使用固定高度，确保安全（避免区块未加载或虚空问题）
            int groundY = baseGroundY;
            
            // 先生成128格高的基岩柱子（从地面向上）
            for (int y = 0; y < pillarHeight; y++) {
                Location bedrockLoc = new Location(world, pillarX, groundY + y, pillarZ);
                bedrockLoc.getBlock().setType(Material.BEDROCK);
            }
            
            // 在柱子顶部生成一个3x3的平台，防止玩家掉下去
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Location platformLoc = new Location(world, pillarX + x, groundY + pillarHeight, pillarZ + z);
                    platformLoc.getBlock().setType(Material.WHITE_STAINED_GLASS);
                }
            }
            
            // 传送玩家到柱子顶部的平台上（+1 是站在平台上方）
            Location playerSpawn = new Location(world, pillarX + 0.5, groundY + pillarHeight + 1, pillarZ + 0.5);
            
            // 先传送玩家
            player.teleportAsync(playerSpawn).thenRun(() -> {
                // 传送完成后，使用实体调度器给玩家添加药水效果（Folia 要求）
                player.getScheduler().run(plugin, task -> {
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOW_FALLING, 
                        100, // 5秒
                        0, 
                        false, 
                        false
                    ));
                }, null);
                
                player.sendMessage("§a你已加入游戏！站在 128 格高的基岩柱子上！");
                player.sendMessage("§7提示：跳下柱子时会有缓降效果");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            });
        }
    }
    
    // 已移除清理逻辑：destroyArena() 和 clearArenaEntities()
    // 游戏结束后直接使用 Worlds 插件删除世界实例即可，无需手动清理掉落物、怪物、方块等
    
    /**
     * 重置玩家状态
     */
    private void resetPlayers() {
        for (Player player : participants) {
            // 检查玩家是否在线
            if (!player.isOnline()) {
                continue;
            }
            
            // 保存原始游戏模式（如果之前没有保存）
            if (!playerGameModes.containsKey(player)) {
                playerGameModes.put(player, player.getGameMode());
            }
            
            // 保存原始物品（如果之前没有保存）
            if (!playerOriginalInventories.containsKey(player) && player.getInventory() != null) {
                ItemStack[] originalItems = new ItemStack[player.getInventory().getSize()];
                for (int i = 0; i < originalItems.length; i++) {
                    ItemStack item = player.getInventory().getItem(i);
                    originalItems[i] = item != null ? item.clone() : null;
                }
                playerOriginalInventories.put(player, originalItems);
            }
            
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.getInventory().clear();
            
            // 注意：不在这里传送玩家，传送由 generateArena() 负责
            // generateArena() 会将玩家传送到 128 格高的基岩柱子顶部
        }
    }
    
    /**
     * 启动物品发放任务
     */
    private void startItemTask() {
        long intervalTicks = config.getItemInterval();
        itemTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (!gameRunning) {
                task.cancel();
                return;
            }
            List<Player> survivors = getSurvivingPlayers();
            if (survivors.isEmpty()) return;

            // 从配置文件获取可掉落的物品列表（只包含配置中指定的物品）
            List<Material> droppableItems = config.getDroppableItems();
            
            if (droppableItems.isEmpty()) {
                plugin.getLogger().warning("配置文件中没有可掉落的物品！请检查 config.yml 中的 items.weights 配置。");
                return;
            }

            for (Player player : survivors) {
                // 使用权重系统选择物品
                Material randomMat = selectWeightedRandomItem(droppableItems);
                ItemStack item = createItemStack(randomMat);
                player.getInventory().addItem(item);
            }
        }, 1, intervalTicks);
    }
    
    /**
     * 创建物品堆（带特殊效果）
     * @param material 物品类型
     * @return 物品堆
     */
    private ItemStack createItemStack(Material material) {
        ItemStack item = new ItemStack(material, 1);
        
        // 如果是药水，添加随机效果
        if (material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION) {
            org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
            if (meta != null) {
                // 随机选择一个有用的药水效果
                org.bukkit.potion.PotionType[] usefulPotions = {
                    org.bukkit.potion.PotionType.STRONG_HEALING,      // 强效治疗
                    org.bukkit.potion.PotionType.REGENERATION,        // 生命恢复
                    org.bukkit.potion.PotionType.STRONG_STRENGTH,     // 强效力量
                    org.bukkit.potion.PotionType.STRONG_SWIFTNESS,    // 强效速度
                    org.bukkit.potion.PotionType.FIRE_RESISTANCE,     // 抗火
                    org.bukkit.potion.PotionType.INVISIBILITY,        // 隐身
                    org.bukkit.potion.PotionType.NIGHT_VISION,        // 夜视
                    org.bukkit.potion.PotionType.STRONG_LEAPING,      // 强效跳跃
                    org.bukkit.potion.PotionType.WATER_BREATHING,     // 水下呼吸
                    org.bukkit.potion.PotionType.STRONG_SLOWNESS,     // 强效缓慢（攻击用）
                    org.bukkit.potion.PotionType.STRONG_HARMING,      // 强效伤害（攻击用）
                    org.bukkit.potion.PotionType.POISON,              // 中毒（攻击用）
                    org.bukkit.potion.PotionType.WEAKNESS,            // 虚弱（攻击用）
                    org.bukkit.potion.PotionType.STRONG_TURTLE_MASTER // 神龟（防御）
                };
                
                org.bukkit.potion.PotionType randomPotion = usefulPotions[random.nextInt(usefulPotions.length)];
                meta.setBasePotionType(randomPotion);
                item.setItemMeta(meta);
            }
        }
        
        return item;
    }
    
    /**
     * 基于权重选择随机物品
     * @param materials 可选物品列表
     * @return 选中的物品
     */
    private Material selectWeightedRandomItem(List<Material> materials) {
        if (materials.isEmpty()) return Material.STONE;
        
        // 获取物品权重配置
        Map<Material, Integer> weights = config.getItemWeights();
        
        // 计算总权重
        int totalWeight = 0;
        for (Material mat : materials) {
            totalWeight += weights.getOrDefault(mat, 1);
        }
        
        // 生成随机数
        int randomValue = random.nextInt(totalWeight);
        
        // 找到对应的物品
        int currentWeight = 0;
        for (Material mat : materials) {
            currentWeight += weights.getOrDefault(mat, 1);
            if (randomValue < currentWeight) {
                return mat;
            }
        }
        
        // 默认返回第一个物品（理论上不会到达这里）
        return materials.get(0);
    }
    
    /**
     * 启动随机事件任务
     */
    private void startEventTask() {
        scheduleNextEvent();
    }
    
    /**
     * 调度下一次随机事件
     * 如果在最后一圈，事件频率会加快
     */
    private void scheduleNextEvent() {
        if (!gameRunning) return;
        
        // 检查是否在最后一圈
        boolean isFinalCircle = gameBorder != null && gameBorder.getSize() <= config.getMinBorderSize() * 1.2;
        
        // 根据是否在最后一圈调整延迟
        long minDelay = isFinalCircle ? config.getEventDelayMinFinal() : config.getEventDelayMin();
        long maxDelay = isFinalCircle ? config.getEventDelayMaxFinal() : config.getEventDelayMax();
        
        long delay = minDelay + random.nextLong(Math.max(1, maxDelay - minDelay + 1));
        
        eventTask = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (!gameRunning) return;
            triggerRandomEvent();
            // 触发完一次后，继续调度下一次
            scheduleNextEvent();
        }, delay);
    }
    
    /**
     * 触发随机事件
     */
    private void triggerRandomEvent() {
        int eventType = random.nextInt(6) + 1; // 现在有6种事件
        List<Player> survivors = getSurvivingPlayers();
        if (survivors.isEmpty()) return;
        switch (eventType) {
            case 1:
                // 箭雨事件
                // 显示标题
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.Component.text("§c随机事件"),
                        net.kyori.adventure.text.Component.text("§6天空下起箭雨！")
                    ));
                }
                final int[] count = {0};
                ScheduledTask arrowRainTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
                    if (count[0] >= 20 || !gameRunning) { 
                        task.cancel();
                        synchronized (dynamicEventTasks) {
                            dynamicEventTasks.remove(task);
                        }
                        return; 
                    }
                    List<Player> currentSurvivors = getSurvivingPlayers();
                    for (Player p : currentSurvivors) {
                        World world = p.getWorld();
                        // 为每个玩家生成箭
                        for (int i = 0; i < 5; i++) {
                            double ox = random.nextDouble() * 10 - 5;
                            double oz = random.nextDouble() * 10 - 5;
                            Location spawnLoc = p.getLocation().add(ox, 25, oz);
                            
                            // 使用区域调度器生成箭（Folia 要求）
                            Bukkit.getRegionScheduler().run(plugin, spawnLoc, regionTask -> {
                                Arrow arrow = world.spawnArrow(spawnLoc, new Vector(0, -1, 0), 1, 0);
                                arrow.setDamage(2.0);
                            });
                        }
                    }
                    count[0]++;
                }, 1, 20);
                
                // 保存任务以便后续取消
                synchronized (dynamicEventTasks) {
                    dynamicEventTasks.add(arrowRainTask);
                }
                break;
            case 2:
                Player ghastTarget = survivors.get(random.nextInt(survivors.size()));
                // 显示标题
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.Component.text("§c随机事件"),
                        net.kyori.adventure.text.Component.text("§6恶魂在 " + ghastTarget.getName() + " 附近！")
                    ));
                }
                // 实体生成必须在区域调度器中执行（Folia 要求）
                Location ghastLoc = ghastTarget.getLocation().add(random.nextInt(10) - 5, 5, random.nextInt(10) - 5);
                Bukkit.getRegionScheduler().run(plugin, ghastLoc, task -> {
                    Entity ghast = ghastTarget.getWorld().spawnEntity(ghastLoc, EntityType.GHAST);
                });
                break;
            case 3:
                Player zombieTarget = survivors.get(random.nextInt(survivors.size()));
                // 显示标题
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.Component.text("§c随机事件"),
                        net.kyori.adventure.text.Component.text("§6僵尸围攻 " + zombieTarget.getName() + "！")
                    ));
                }
                // 实体生成必须在区域调度器中执行（Folia 要求）
                for (int i = 0; i < 3; i++) {
                    Location zombieLoc = zombieTarget.getLocation().add(random.nextInt(10) - 5, 0, random.nextInt(10) - 5);
                    Bukkit.getRegionScheduler().run(plugin, zombieLoc, task -> {
                        Entity zombie = zombieTarget.getWorld().spawnEntity(zombieLoc, EntityType.ZOMBIE);
                    });
                }
                break;
            case 4:
                // 苦力怕雨事件
                Player creeperTarget = survivors.get(random.nextInt(survivors.size()));
                // 显示标题
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.Component.text("§a§l苦力怕雨"),
                        net.kyori.adventure.text.Component.text("§6目标：" + creeperTarget.getName() + "！")
                    ));
                }
                
                // 从高空生成5只苦力怕
                for (int i = 0; i < 5; i++) {
                    double offsetX = random.nextDouble() * 10 - 5;
                    double offsetZ = random.nextDouble() * 10 - 5;
                    Location spawnLoc = creeperTarget.getLocation().add(offsetX, 30, offsetZ); // 30格高空
                    
                    // 使用区域调度器生成苦力怕
                    Bukkit.getRegionScheduler().run(plugin, spawnLoc, task -> {
                        Creeper creeper = (Creeper) creeperTarget.getWorld().spawnEntity(spawnLoc, EntityType.CREEPER);
                        
                        // 使用实体调度器给苦力怕添加缓降效果，防止摔死
                        creeper.getScheduler().run(plugin, entityTask -> {
                            creeper.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.SLOW_FALLING,
                                200, // 10秒缓降
                                0,
                                false,
                                false
                            ));
                        }, null);
                        
                        // 音效和粒子效果
                        creeperTarget.getWorld().playSound(spawnLoc, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 0.8f);
                        creeperTarget.getWorld().spawnParticle(Particle.EXPLOSION, spawnLoc, 3, 0.5, 0.5, 0.5, 0);
                    });
                }
                
                // 给玩家额外的警告音效
                for (Player p : survivors) {
                    p.playSound(p.getLocation(), Sound.ENTITY_CREEPER_HURT, 1.0f, 0.5f);
                }
                break;
            case 5:
                // 重力反转事件
                // 显示标题
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.Component.text("§d§l重力反转"),
                        net.kyori.adventure.text.Component.text("§b所有人漂浮了！")
                    ));
                }
                
                // 给所有存活玩家添加漂浮和缓降效果
                int levitationDuration = 400; // 漂浮20秒（400 ticks）
                int slowFallingDuration = 600; // 缓降30秒（600 ticks），确保玩家安全落地
                
                for (Player p : survivors) {
                    // 使用实体调度器给玩家添加药水效果（Folia 要求）
                    p.getScheduler().run(plugin, task -> {
                        // 漂浮效果 I - 玩家会缓慢向上飘20秒
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.LEVITATION,
                            levitationDuration, // 20秒漂浮
                            0, // 等级 I（降低等级，缓慢上升）
                            false,
                            true
                        ));
                        
                        // 缓降效果 - 30秒，确保玩家有足够时间安全降落
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOW_FALLING,
                            slowFallingDuration, // 30秒缓降
                            0,
                            false,
                            true
                        ));
                        
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.5f);
                    }, null);
                }
                break;
            case 6:
                // 末影水晶爆炸事件
                Player crystalTarget = survivors.get(random.nextInt(survivors.size()));
                // 显示标题
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.Component.text("§5§l末影水晶"),
                        net.kyori.adventure.text.Component.text("§6目标：" + crystalTarget.getName() + "！")
                    ));
                }
                
                // 在目标玩家周围生成3个末影水晶
                for (int i = 0; i < 3; i++) {
                    double offsetX = random.nextDouble() * 8 - 4;
                    double offsetZ = random.nextDouble() * 8 - 4;
                    Location crystalLoc = crystalTarget.getLocation().add(offsetX, 0, offsetZ);
                    
                    // 找到合适的Y坐标（地面）
                    World world = crystalTarget.getWorld();
                    int groundY = world.getHighestBlockYAt(crystalLoc);
                    crystalLoc.setY(groundY + 1);
                    
                    // 使用区域调度器生成末影水晶
                    Bukkit.getRegionScheduler().run(plugin, crystalLoc, task -> {
                        Entity crystal = world.spawnEntity(crystalLoc, EntityType.END_CRYSTAL);
                        
                        // 3秒后爆炸
                        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, explodeTask -> {
                            if (crystal.isValid()) {
                                crystal.getScheduler().run(plugin, entityTask -> {
                                    Location loc = crystal.getLocation();
                                    world.createExplosion(loc, 6.0f, false, false);
                                    crystal.remove();
                                }, null);
                            }
                        }, 60L); // 3秒 = 60 ticks
                        
                        world.playSound(crystalLoc, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f);
                        world.spawnParticle(Particle.END_ROD, crystalLoc, 10, 0.5, 0.5, 0.5, 0.1);
                    });
                }
                
                // 给玩家警告音效
                for (Player p : survivors) {
                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);
                }
                break;
        }
    }
    
    /**
     * 启动边界缩小任务
     */
    private void startBorderShrink() {
        long delay = config.getShrinkDelay();
        long interval = config.getShrinkInterval();
        borderShrinkTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (!gameRunning || gameBorder == null) { 
                task.cancel(); 
                return; 
            }
            double currentSize = gameBorder.getSize();
            double minSize = config.getMinBorderSize();
            if (currentSize <= minSize) { 
                task.cancel(); 
                Bukkit.broadcastMessage("§c[房间 " + arena.getArenaName() + "] 边界已缩小到最小范围（" + minSize + "格）！"); 
                return; 
            }
            double newSize = Math.max(minSize, currentSize - config.getShrinkAmount());
            long shrinkSeconds = config.getShrinkInterval() / 20;
            gameBorder.setSize(newSize, shrinkSeconds);
            Bukkit.broadcastMessage("§e[房间 " + arena.getArenaName() + "] 边界正在缩小！当前直径：§6" + (int)newSize + "格");
            for (Player p : getSurvivingPlayers()) p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
        }, delay, interval);
    }
    
    /**
     * 启动存活人数显示
     */
    private void startAliveCountDisplay() {
        if (aliveCountTask != null) {
            aliveCountTask.cancel();
        }
        
        lastAliveCount = -1; // 重置存活人数记录
        
        aliveCountTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (!gameRunning) {
                task.cancel();
                lastAliveCount = -1;
                return;
            }
            
            int alive = getSurvivingPlayers().size();
            int total = participants.size();
            
            // 给房间内的玩家显示存活人数
            for (Player p : participants) {
                if (p.isOnline()) {
                    p.sendActionBar(net.kyori.adventure.text.Component.text(
                        "§a存活：§6" + alive + "§7/§e" + total + " §8| §c边界正在缩小"
                    ));
                }
            }
            
            // 只在存活人数变化时才广播消息（防止刷屏）
            if (alive != lastAliveCount) {
                // 最后5人提示
                if (alive == 5 && alive < total) {
                    Bukkit.broadcast(net.kyori.adventure.text.Component.text("§c§l【最后5人】§e决战时刻到来！"));
                    for (Player p : participants) {
                        if (p.isOnline()) {
                            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
                        }
                    }
                }
                
                // 最后3人提示
                if (alive == 3 && alive < total) {
                    Bukkit.broadcast(net.kyori.adventure.text.Component.text("§6§l【最后3人】§e谁能笑到最后？"));
                    for (Player p : participants) {
                        if (p.isOnline()) {
                            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                        }
                    }
                }
                
                // 最后2人提示
                if (alive == 2 && alive < total) {
                    Bukkit.broadcast(net.kyori.adventure.text.Component.text("§4§l【最后2人】§c巅峰对决！"));
                    for (Player p : participants) {
                        if (p.isOnline()) {
                            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
                        }
                    }
                }
                
                lastAliveCount = alive; // 更新存活人数记录
            }
        }, 1, 20L); // 每秒更新一次
    }
    
    /**
     * 处理玩家死亡
     */
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (!gameRunning) return;
        
        Player player = event.getEntity();
        if (!participants.contains(player)) return;
        
        // 从存活列表中移除
        alivePlayers.remove(player);
        
        Player killer = player.getKiller();
        if (killer != null && participants.contains(killer)) {
            // 击杀奖励
            statsManager.recordKill(killer);
            statsManager.recordDeath(player);
            
            // 回血
            double maxHealth = killer.getMaxHealth();
            killer.setHealth(Math.min(maxHealth, killer.getHealth() + 4.0));
            
            // 奖励物品
            List<Material> droppableItems = config.getDroppableItems();
            if (!droppableItems.isEmpty()) {
                Material randomMat = selectWeightedRandomItem(droppableItems);
                ItemStack reward = createItemStack(randomMat);
                killer.getInventory().addItem(reward);
            }
            
            Bukkit.broadcastMessage("§a[房间 " + arena.getArenaName() + "] §6" + killer.getName() + " §a击杀了 §c" + player.getName() + "§a！");
        } else {
            statsManager.recordDeath(player);
        }
        
        // 检查游戏是否结束
        List<Player> survivors = getSurvivingPlayers();
        if (survivors.size() <= 1) {
            gameRunning = false;
            arena.setStatus(GameArena.ArenaStatus.ENDING);
            
            // 5秒延迟后再处理结果
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                if (survivors.isEmpty()) {
                    Bukkit.broadcastMessage("§c[房间 " + arena.getArenaName() + "] 游戏结束！所有玩家都已死亡！");
                    // 所有玩家都死了，记录失败
                    for (Player p : participants) {
                        statsManager.recordLoss(p);
                    }
                } else {
                    Player winner = survivors.get(0);
                    Bukkit.broadcastMessage("§a[房间 " + arena.getArenaName() + "] §6" + winner.getName() + " §a获胜！");
                    statsManager.recordWin(winner);
                    // 失败者记录失败
                    for (Player p : participants) {
                        if (p != winner) {
                            statsManager.recordLoss(p);
                        }
                    }
                }
                
                // 清理并重置
                stopGame();
            }, 100L); // 5秒 = 100 ticks
        }
    }
    
    /**
     * 处理玩家离线
     */
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!participants.contains(player)) return;
        
        // 从存活列表中移除
        alivePlayers.remove(player);
        
        // 如果游戏正在运行，检查游戏是否结束
        if (gameRunning) {
            // 向房间内的玩家发送消息
            for (Player p : new ArrayList<>(participants)) {
                if (p.isOnline() && p != player) {
                    p.sendMessage("§c[房间 " + arena.getArenaName() + "] §6" + player.getName() + " §c离开了游戏！");
                }
            }
            
            // 检查游戏是否结束
            List<Player> survivors = getSurvivingPlayers();
            if (survivors.size() <= 1) {
                gameRunning = false;
                arena.setStatus(GameArena.ArenaStatus.ENDING);
                
                // 5秒延迟后再处理结果
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                    if (survivors.isEmpty()) {
                        // 向房间内的玩家发送消息
                        for (Player p : new ArrayList<>(participants)) {
                            if (p.isOnline()) {
                                p.sendMessage("§c[房间 " + arena.getArenaName() + "] 游戏结束！所有玩家都已离开！");
                                statsManager.recordLoss(p);
                            }
                        }
                    } else {
                        Player winner = survivors.get(0);
                        // 向房间内的玩家发送消息
                        for (Player p : new ArrayList<>(participants)) {
                            if (p.isOnline()) {
                                p.sendMessage("§a[房间 " + arena.getArenaName() + "] §6" + winner.getName() + " §a获胜！");
                                if (p == winner) {
                                    statsManager.recordWin(winner);
                                } else {
                                    statsManager.recordLoss(p);
                                }
                            }
                        }
                    }
                    
                    // 清理并重置
                    stopGame();
                }, 100L); // 5秒 = 100 ticks
            }
        }
        
        // 从参与者列表中移除（无论游戏是否运行）
        participants.remove(player);
        
        // 清理玩家数据（如果游戏未运行，这些数据可能还在）
        playerOriginalLocations.remove(player);
        playerOriginalInventories.remove(player);
        playerGameModes.remove(player);
        
        // 从 ArenaManager 的玩家房间映射中移除
        RandomItemPVP pluginInstance = RandomItemPVP.getInstance();
        if (pluginInstance != null) {
            ArenaManager arenaManager = pluginInstance.getArenaManager();
            if (arenaManager != null) {
                arenaManager.removePlayerFromArena(player);
            }
        }
    }
    
    /**
     * 停止游戏
     */
    private void stopGame() {
        gameRunning = false;
        preparing = false;
        arena.setStatus(GameArena.ArenaStatus.WAITING);
        
        // 取消所有任务
        cancelAllTasks();
        
        // 不再清理竞技场，直接删除世界实例即可
        
        // 重置边界
        if (gameBorder != null) {
            gameBorder.setSize(30000000, 0);
        }
        
        // 先恢复玩家游戏模式和物品（在大厅传送前）
        for (Player player : new ArrayList<>(participants)) {
            if (player.isOnline()) {
                // 恢复游戏模式
                GameMode originalMode = playerGameModes.get(player);
                if (originalMode != null) {
                    player.setGameMode(originalMode);
                } else {
                    player.setGameMode(GameMode.SURVIVAL);
                }
                
                // 清除游戏物品
                player.getInventory().clear();
                
                // 恢复原始物品（如果有保存）
                ItemStack[] originalItems = playerOriginalInventories.get(player);
                if (originalItems != null) {
                    // 复制物品以避免引用问题
                    ItemStack[] restoredItems = new ItemStack[originalItems.length];
                    for (int i = 0; i < originalItems.length; i++) {
                        restoredItems[i] = originalItems[i] != null ? originalItems[i].clone() : null;
                    }
                    player.getInventory().setContents(restoredItems);
                }
            }
        }
        
        // 获取大厅位置并传送所有玩家
        Location lobbyLocation = config.loadLobbyLocation();
        if (lobbyLocation != null && config.isLobbyEnabled()) {
            // 传送所有参与者回大厅
            for (Player player : new ArrayList<>(participants)) {
                if (player.isOnline()) {
                    Location safeLobby = findSafeLocation(lobbyLocation);
                    if (safeLobby != null) {
                        player.teleportAsync(safeLobby).thenRun(() -> {
                            player.sendMessage("§a已传送回大厅！");
                        });
                    } else {
                        // 如果找不到安全位置，尝试使用原始大厅位置
                        player.teleportAsync(lobbyLocation).thenRun(() -> {
                            player.sendMessage("§a已传送回大厅！");
                        });
                    }
                }
            }
            
            // 传送观战者回大厅
            for (Player spectator : new ArrayList<>(spectatorInventories.keySet())) {
                if (spectator.isOnline()) {
                    // 恢复游戏模式
                    GameMode originalMode = spectatorGameModes.remove(spectator);
                    if (originalMode != null) {
                        spectator.setGameMode(originalMode);
                    } else {
                        spectator.setGameMode(GameMode.SURVIVAL);
                    }
                    
                    // 恢复原始物品
                    ItemStack[] originalItems = spectatorInventories.remove(spectator);
                    if (originalItems != null) {
                        ItemStack[] restoredItems = new ItemStack[originalItems.length];
                        for (int i = 0; i < originalItems.length; i++) {
                            restoredItems[i] = originalItems[i] != null ? originalItems[i].clone() : null;
                        }
                        spectator.getInventory().setContents(restoredItems);
                    }
                    
                    // 传送到大厅
                    Location safeLobby = findSafeLocation(lobbyLocation);
                    if (safeLobby != null) {
                        spectator.teleportAsync(safeLobby).thenRun(() -> {
                            spectator.sendMessage("§a已传送回大厅！");
                        });
                    } else {
                        spectator.teleportAsync(lobbyLocation).thenRun(() -> {
                            spectator.sendMessage("§a已传送回大厅！");
                        });
                    }
                }
            }
        } else {
            // 如果没有配置大厅，传送玩家回原始位置
            for (Player player : new ArrayList<>(participants)) {
                if (player.isOnline()) {
                    Location originalLoc = playerOriginalLocations.get(player);
                    if (originalLoc != null) {
                        Location safeLoc = findSafeLocation(originalLoc);
                        if (safeLoc != null) {
                            player.teleportAsync(safeLoc).thenRun(() -> {
                                player.sendMessage("§a已传送回原位置！");
                            });
                        }
                    }
                }
            }
            
            // 恢复观战者的游戏模式和物品，并传送回原位置
            for (Player spectator : new ArrayList<>(spectatorInventories.keySet())) {
                if (spectator.isOnline()) {
                    // 恢复游戏模式
                    GameMode originalMode = spectatorGameModes.remove(spectator);
                    if (originalMode != null) {
                        spectator.setGameMode(originalMode);
                    } else {
                        spectator.setGameMode(GameMode.SURVIVAL);
                    }
                    
                    // 恢复原始物品
                    ItemStack[] originalItems = spectatorInventories.remove(spectator);
                    if (originalItems != null) {
                        ItemStack[] restoredItems = new ItemStack[originalItems.length];
                        for (int i = 0; i < originalItems.length; i++) {
                            restoredItems[i] = originalItems[i] != null ? originalItems[i].clone() : null;
                        }
                        spectator.getInventory().setContents(restoredItems);
                    }
                    
                    // 传送观战者回原始位置（如果有保存）
                    Location originalLoc = spectatorOriginalLocations.remove(spectator);
                    if (originalLoc != null && originalLoc.getWorld() != null) {
                        Location safeLoc = findSafeLocation(originalLoc);
                        if (safeLoc != null) {
                            spectator.teleportAsync(safeLoc).thenRun(() -> {
                                spectator.sendMessage("§a[房间 " + arena.getArenaName() + "] 游戏结束，已传送回原位置！");
                            });
                        } else {
                            // 如果原位置不安全，传送到世界出生点
                            if (arena.getWorld() != null) {
                                Location spawnLoc = arena.getWorld().getSpawnLocation();
                                Location safeSpawn = findSafeLocation(spawnLoc);
                                if (safeSpawn != null) {
                                    spectator.teleportAsync(safeSpawn).thenRun(() -> {
                                        spectator.sendMessage("§a[房间 " + arena.getArenaName() + "] 游戏结束，已传送到世界出生点！");
                                    });
                                }
                            }
                        }
                    } else {
                        spectator.sendMessage("§a[房间 " + arena.getArenaName() + "] 游戏结束，已恢复正常模式！");
                    }
                }
            }
        }
        
        // 清空游戏相关数据，但保留参与者列表（以便下一局使用）
        // 注意：不清空 participants，让玩家留在房间中等待下一局
        playerOriginalLocations.clear();
        playerOriginalInventories.clear();
        alivePlayers.clear();
        playerGameModes.clear();
        spectatorInventories.clear();
        spectatorGameModes.clear();
        spectatorOriginalLocations.clear();
        gatherLocation = null;
        spawnLocation = null;
        gameBorder = null;
        
        // 清理已离线玩家的参与者记录
        participants.removeIf(player -> !player.isOnline());
        
        // 清理世界实例（延迟执行，确保所有玩家都已传送）
        RandomItemPVP pluginInstance = RandomItemPVP.getInstance();
        if (pluginInstance != null) {
            ArenaManager arenaManager = pluginInstance.getArenaManager();
            if (arenaManager != null) {
                // 延迟 5 秒后清理世界（确保所有传送和恢复完成）
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                    // 再次检查是否有玩家在世界中，如果有则传送他们离开
                    String instanceWorldKey = arena.getInstanceWorldKey();
                    if (instanceWorldKey != null && config.isWorldInstancingEnabled()) {
                        World instanceWorld = WorldsIntegration.loadWorld(instanceWorldKey);
                        if (instanceWorld != null) {
                            // 检查世界中是否有玩家（包括已离开的参与者）
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (p.getWorld().equals(instanceWorld)) {
                                    // 玩家在已结束的游戏世界中，传送离开
                                    Location lobbyLoc = config.loadLobbyLocation();
                                    if (lobbyLoc != null && config.isLobbyEnabled()) {
                                        Location safeLobby = findSafeLocation(lobbyLoc);
                                        if (safeLobby != null) {
                                            p.teleportAsync(safeLobby).thenRun(() -> {
                                                p.sendMessage("§e游戏已结束，已传送回大厅！");
                                            });
                                        } else {
                                            // 如果找不到安全位置，直接使用大厅位置
                                            p.teleportAsync(lobbyLoc).thenRun(() -> {
                                                p.sendMessage("§e游戏已结束，已传送回大厅！");
                                            });
                                        }
                                    } else {
                                        // 没有大厅，传送到世界出生点
                                        Location worldSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                                        p.teleportAsync(worldSpawn).thenRun(() -> {
                                            p.sendMessage("§e游戏已结束，已传送到世界出生点！");
                                        });
                                    }
                                    
                                    // 从参与者列表中移除（如果还在）
                                    participants.remove(p);
                                    
                                    // 从 ArenaManager 的玩家房间映射中移除
                                    RandomItemPVP pluginInstance2 = RandomItemPVP.getInstance();
                                    if (pluginInstance2 != null) {
                                        ArenaManager arenaManager2 = pluginInstance2.getArenaManager();
                                        if (arenaManager2 != null) {
                                            arenaManager2.removePlayerFromArena(p);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // 清理世界实例
                    arenaManager.cleanupArenaWorld(arena);
                }, 100L); // 5秒 = 100 ticks
            }
        }
    }
}

