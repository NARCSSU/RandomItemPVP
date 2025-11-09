package org.luminolcraft.randomitempvp;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地图投票管理器
 */
public class MapVoteManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private ArenaManager arenaManager;
    
    // 每个房间的投票数据：房间名 -> (地图ID -> 投票玩家列表)
    private final Map<String, Map<String, Set<Player>>> votes = new ConcurrentHashMap<>();
    // 每个房间的投票任务
    private final Map<String, ScheduledTask> voteTasks = new ConcurrentHashMap<>();
    // 每个房间的剩余投票时间（秒）
    private final Map<String, int[]> voteRemainingTime = new ConcurrentHashMap<>();
    // 每个房间的选中的地图
    private final Map<String, String> selectedMaps = new ConcurrentHashMap<>();
    
    public MapVoteManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }
    
    /**
     * 设置 ArenaManager（用于获取房间内的玩家列表）
     * @param arenaManager 房间管理器
     */
    public void setArenaManager(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }
    
    /**
     * 向房间内的所有玩家发送消息
     * @param arenaName 房间名
     * @param message 消息内容
     */
    private void sendMessageToArena(String arenaName, String message) {
        if (arenaManager == null) {
            // 如果没有 ArenaManager，回退到广播
            Bukkit.broadcastMessage(message);
            return;
        }
        
        GameArena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            return;
        }
        
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
     * 开始房间的地图投票（只有在达到最少玩家数时才真正开始）
     * @param arenaName 房间名
     * @param currentPlayerCount 当前玩家数
     * @param minPlayers 最少玩家数
     */
    public boolean startVote(String arenaName, int currentPlayerCount, int minPlayers) {
        // 检查是否达到最少玩家数
        if (currentPlayerCount < minPlayers) {
            // 未达到最少玩家数，不开始投票
            return false;
        }
        
        // 如果已经在投票，不重复开始
        if (isVoting(arenaName)) {
            return true;
        }
        
        // 清除旧的投票数据
        votes.remove(arenaName);
        selectedMaps.remove(arenaName);
        cancelVote(arenaName);
        
        // 初始化投票数据
        Map<String, Set<Player>> arenaVotes = new ConcurrentHashMap<>();
        List<String> availableMaps = config.getAvailableMaps();
        
        if (availableMaps.isEmpty()) {
            plugin.getLogger().warning("没有可用的地图配置！投票已取消。");
            return false;
        }
        
        for (String mapId : availableMaps) {
            arenaVotes.put(mapId, ConcurrentHashMap.newKeySet());
        }
        votes.put(arenaName, arenaVotes);
        
        // 向房间内的玩家发送投票开始消息
        sendMessageToArena(arenaName, "§a[房间 " + arenaName + "] 地图投票开始！");
        sendMessageToArena(arenaName, "§7使用 /ripvp vote <地图名> 投票");
        sendMessageToArena(arenaName, "§7使用 /ripvp vote cancel 取消投票");
        
        // 显示可用地图
        StringBuilder mapList = new StringBuilder("§a可用地图：");
        for (String mapId : availableMaps) {
            String mapName = config.getMapName(mapId);
            mapList.append(" §e").append(mapName).append("§7(/ripvp vote ").append(mapId).append(")");
        }
        sendMessageToArena(arenaName, mapList.toString());
        
        // 启动投票倒计时
        int duration = config.getVoteDuration();
        final int[] remaining = new int[]{duration};
        voteRemainingTime.put(arenaName, remaining);
        
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (!voteRemainingTime.containsKey(arenaName)) {
                scheduledTask.cancel();
                voteTasks.remove(arenaName);
                return;
            }
            
            remaining[0]--;
            
            if (remaining[0] <= 0) {
                // 投票结束
                finishVote(arenaName);
                scheduledTask.cancel();
                voteTasks.remove(arenaName);
                voteRemainingTime.remove(arenaName);
            } else {
                // 显示剩余时间（只发送给房间内的玩家）
                if (remaining[0] <= 5 || remaining[0] % 5 == 0) {
                    sendMessageToArena(arenaName, "§e[房间 " + arenaName + "] 投票剩余 " + remaining[0] + " 秒");
                }
            }
        }, 20L, 20L); // 每秒执行一次
        
        voteTasks.put(arenaName, task);
        return true;
    }
    
    /**
     * 延长投票时间（当新玩家加入时调用）
     * @param arenaName 房间名
     * @param extraSeconds 额外秒数
     */
    public void extendVoteTime(String arenaName, int extraSeconds) {
        if (!isVoting(arenaName)) {
            return; // 未在投票中
        }
        
        int[] remaining = voteRemainingTime.get(arenaName);
        if (remaining != null) {
            int maxDuration = config.getVoteDuration();
            // 延长投票时间，但不超过最大持续时间
            remaining[0] = Math.min(remaining[0] + extraSeconds, maxDuration);
            sendMessageToArena(arenaName, "§e[房间 " + arenaName + "] 有新玩家加入！投票时间延长 " + extraSeconds + " 秒");
            sendMessageToArena(arenaName, "§e[房间 " + arenaName + "] 当前投票剩余 " + remaining[0] + " 秒");
        }
    }
    
    /**
     * 玩家投票
     * @param arenaName 房间名
     * @param player 玩家
     * @param mapId 地图ID，如果为 null 或 "cancel"，则取消投票（弃票）
     * @return 是否投票成功
     */
    public boolean vote(String arenaName, Player player, String mapId) {
        Map<String, Set<Player>> arenaVotes = votes.get(arenaName);
        if (arenaVotes == null) {
            player.sendMessage(ChatColor.RED + "该房间的投票尚未开始！");
            return false;
        }
        
        // 移除玩家之前的投票
        for (Set<Player> voters : arenaVotes.values()) {
            voters.remove(player);
        }
        
        // 如果是弃票（null 或 "cancel"）
        if (mapId == null || mapId.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.YELLOW + "你已取消投票（弃票）");
            // 显示当前投票结果
            showVoteResults(arenaName);
            return true;
        }
        
        if (!config.mapExists(mapId)) {
            player.sendMessage(ChatColor.RED + "地图 '" + mapId + "' 不存在！");
            return false;
        }
        
        // 添加新投票
        arenaVotes.get(mapId).add(player);
        
        String mapName = config.getMapName(mapId);
        player.sendMessage(ChatColor.GREEN + "你已投票给 " + mapName);
        
        // 显示当前投票结果
        showVoteResults(arenaName);
        
        return true;
    }
    
    /**
     * 显示投票结果
     * @param arenaName 房间名
     */
    private void showVoteResults(String arenaName) {
        Map<String, Set<Player>> arenaVotes = votes.get(arenaName);
        if (arenaVotes == null) return;
        
        StringBuilder result = new StringBuilder("§a[房间 " + arenaName + "] 投票结果：");
        for (Map.Entry<String, Set<Player>> entry : arenaVotes.entrySet()) {
            String mapId = entry.getKey();
            String mapName = config.getMapName(mapId);
            int voteCount = entry.getValue().size();
            result.append(" §e").append(mapName).append("(").append(voteCount).append("票)");
        }
        sendMessageToArena(arenaName, result.toString());
    }
    
    /**
     * 完成投票并选择地图
     * @param arenaName 房间名
     */
    private void finishVote(String arenaName) {
        Map<String, Set<Player>> arenaVotes = votes.get(arenaName);
        if (arenaVotes == null) return;
        
        // 统计投票结果
        int maxVotes = -1;
        List<String> tiedMaps = new ArrayList<>();
        
        for (Map.Entry<String, Set<Player>> entry : arenaVotes.entrySet()) {
            int voteCount = entry.getValue().size();
            if (voteCount > maxVotes) {
                maxVotes = voteCount;
                tiedMaps.clear();
                tiedMaps.add(entry.getKey());
            } else if (voteCount == maxVotes && voteCount > 0) {
                tiedMaps.add(entry.getKey());
            }
        }
        
        String selectedMapId;
        String selectedMapName;
        
        if (maxVotes <= 0) {
            // 无人投票，随机选择
            List<String> availableMaps = config.getAvailableMaps();
            if (availableMaps.isEmpty()) {
                sendMessageToArena(arenaName, "§c[房间 " + arenaName + "] 没有可用地图！投票已取消。");
                votes.remove(arenaName);
                return;
            }
            selectedMapId = availableMaps.get(new Random().nextInt(availableMaps.size()));
            selectedMapName = config.getMapName(selectedMapId);
            sendMessageToArena(arenaName, "§a[房间 " + arenaName + "] 无人投票，随机选择地图：§e" + selectedMapName);
        } else if (tiedMaps.size() == 1) {
            // 有明确获胜者
            selectedMapId = tiedMaps.get(0);
            selectedMapName = config.getMapName(selectedMapId);
            sendMessageToArena(arenaName, "§a[房间 " + arenaName + "] 投票结束！选中地图：§e" + selectedMapName + " §a(" + maxVotes + "票)");
        } else {
            // 平票，在平票的地图中随机选择
            selectedMapId = tiedMaps.get(new Random().nextInt(tiedMaps.size()));
            selectedMapName = config.getMapName(selectedMapId);
            sendMessageToArena(arenaName, "§a[房间 " + arenaName + "] 投票平票，随机选择：§e" + selectedMapName + " §a(" + maxVotes + "票平票)");
        }
        
        selectedMaps.put(arenaName, selectedMapId);
        votes.remove(arenaName); // 清理投票数据
    }
    
    /**
     * 获取房间选中的地图ID
     * @param arenaName 房间名
     * @return 地图ID，如果未选择则返回null
     */
    public String getSelectedMap(String arenaName) {
        return selectedMaps.get(arenaName);
    }
    
    /**
     * 取消房间的投票
     * @param arenaName 房间名
     */
    public void cancelVote(String arenaName) {
        ScheduledTask task = voteTasks.remove(arenaName);
        if (task != null) {
            task.cancel();
        }
        votes.remove(arenaName);
        voteRemainingTime.remove(arenaName);
        selectedMaps.remove(arenaName);
    }
    
    /**
     * 检查房间是否正在投票
     * @param arenaName 房间名
     * @return 是否正在投票
     */
    public boolean isVoting(String arenaName) {
        return voteTasks.containsKey(arenaName);
    }
    
    /**
     * 获取房间的投票数据（用于显示）
     * @param arenaName 房间名
     * @return 投票数据映射，键为地图ID，值为投票数
     */
    public Map<String, Integer> getVoteResults(String arenaName) {
        Map<String, Set<Player>> arenaVotes = votes.get(arenaName);
        if (arenaVotes == null) {
            return Collections.emptyMap();
        }
        
        Map<String, Integer> results = new HashMap<>();
        for (Map.Entry<String, Set<Player>> entry : arenaVotes.entrySet()) {
            results.put(entry.getKey(), entry.getValue().size());
        }
        return results;
    }
}


