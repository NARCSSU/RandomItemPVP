package org.luminolcraft.randomitempvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RipvpCommand implements CommandExecutor, TabCompleter {
    private final GameManager gameManager;
    private final ArenaManager arenaManager;
    private final ConfigManager configManager;
    private final PlayerStatsManager statsManager;

    public RipvpCommand(GameManager gameManager, ArenaManager arenaManager, ConfigManager configManager, PlayerStatsManager statsManager) {
        this.gameManager = gameManager;
        this.arenaManager = arenaManager;
        this.configManager = configManager;
        this.statsManager = statsManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 无参数 -> 帮助
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        // 控制台可执行的命令：status, create, delete, list, reload
        boolean consoleAllowed = args.length >= 1 && (
            args[0].equalsIgnoreCase("status") ||
            args[0].equalsIgnoreCase("create") ||
            args[0].equalsIgnoreCase("delete") ||
            args[0].equalsIgnoreCase("list") ||
            args[0].equalsIgnoreCase("arenas") ||
            args[0].equalsIgnoreCase("reload")
        );
        
        // 仅玩家可执行（除了控制台允许的命令）
        if (!(sender instanceof Player) && !consoleAllowed) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以执行此命令！");
            return true;
        }

        Player player = (sender instanceof Player) ? (Player) sender : null;

        // 处理子命令
        if (args.length >= 1) {
            switch (args[0].toLowerCase()) {
                case "start":
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.use")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    
                    // 如果提供了房间名，使用多房间系统
                    if (args.length >= 2) {
                        String arenaName = args[1];
                        GameArena arena = arenaManager.getArena(arenaName);
                        
                        // 如果房间不存在，自动创建（启动投票）
                        if (arena == null) {
                            if (arenaManager.createArena(arenaName, player)) {
                                player.sendMessage(ChatColor.GREEN + "房间 '§6" + arenaName + "§a' 已创建！地图投票已开始！");
                                player.sendMessage(ChatColor.YELLOW + "使用 /ripvp vote <地图名> 投票选择地图");
                            } else {
                                player.sendMessage(ChatColor.RED + "创建房间失败！");
                                return true;
                            }
                            
                            // 重新获取房间（因为刚刚创建）
                            arena = arenaManager.getArena(arenaName);
                        }
                        
                        // 检查房间状态
                        if (arena.isRunning()) {
                            player.sendMessage(ChatColor.RED + "房间 '§6" + arenaName + "§c' 正在进行游戏！");
                            return true;
                        }
                        
                        if (arena.isPreparing()) {
                            player.sendMessage(ChatColor.RED + "房间 '§6" + arenaName + "§c' 正在准备中！");
                            return true;
                        }
                        
                        // 确保玩家在房间中
                        if (!arenaManager.isPlayerInArena(player) || !arenaName.equals(arenaManager.getPlayerArena(player))) {
                            if (!arenaManager.joinArena(player, arenaName)) {
                                player.sendMessage(ChatColor.RED + "加入房间失败！");
                                return true;
                            }
                        }
                        
                        // 启动游戏倒计时
                        GameInstance instance = arena.getGameInstance();
                        Set<Player> participantsSet = instance.getParticipants();
                        
                        if (participantsSet.size() >= configManager.getMinPlayers()) {
                            List<Player> participants = new ArrayList<>(participantsSet);
                            instance.startGameWithCountdown(participants);
                            player.sendMessage(ChatColor.GREEN + "房间 '§6" + arenaName + "§a' 游戏倒计时已开始！");
                        } else {
                            player.sendMessage(ChatColor.YELLOW + "玩家数不足！当前：§e" + participantsSet.size() + 
                                "§7/§e" + configManager.getMinPlayers() + 
                                "§7，需要至少 §e" + configManager.getMinPlayers() + " §7人才能开始！");
                            player.sendMessage(ChatColor.GRAY + "其他玩家可以使用 /ripvp join " + arenaName + " 加入");
                        }
                        return true;
                    }
                    
                    // 没有提供房间名，使用旧的单房间逻辑（向后兼容）
                    if (gameManager.isRunning()) {
                        player.sendMessage(ChatColor.RED + "游戏已在运行中！");
                        return true;
                    }
                    
                    if (gameManager.isPreparing()) {
                        player.sendMessage(ChatColor.RED + "游戏准备中！请等待或使用 /ripvp join 加入");
                        return true;
                    }
                    
                    // 启动游戏（只传送发起者自己）
                    player.sendMessage(ChatColor.GREEN + "游戏即将开始！其他玩家可以使用 /ripvp join 加入");
                    List<Player> initiator = new ArrayList<>();
                    initiator.add(player);
                    gameManager.startGameWithCountdown(initiator);
                    return true;

                case "stop":
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.admin")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    if (!gameManager.isRunning()) {
                        player.sendMessage(ChatColor.RED + "没有正在运行的游戏！");
                        return true;
                    }
                    gameManager.stopGame(true);
                    player.sendMessage(ChatColor.RED + "游戏已停止！");
                    return true;

                case "status":
                    sender.sendMessage(ChatColor.AQUA + "===== 游戏状态 =====");
                    sender.sendMessage(ChatColor.WHITE + "是否运行：" + (gameManager.isRunning() ? ChatColor.GREEN + "是" : ChatColor.RED + "否"));
                    sender.sendMessage(ChatColor.WHITE + "存活玩家：" + ChatColor.YELLOW + gameManager.getAliveCount());
                    sender.sendMessage(ChatColor.AQUA + "===================");
                    return true;

                case "reload":
                    if (!sender.hasPermission("ripvp.admin")) {
                        sender.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    
                    // 检查是否有游戏在进行
                    if (gameManager.isRunning()) {
                        sender.sendMessage(ChatColor.RED + "游戏进行中无法热加载配置！");
                        sender.sendMessage(ChatColor.YELLOW + "请先使用 /ripvp stop 停止游戏。");
                        return true;
                    }
                    
                    if (gameManager.isPreparing()) {
                        sender.sendMessage(ChatColor.RED + "游戏准备中无法热加载配置！");
                        sender.sendMessage(ChatColor.YELLOW + "请先使用 /ripvp cancel 取消游戏。");
                        return true;
                    }
                    
                    // 热加载配置
                    configManager.reloadConfig();
                    
                    // 重载出生点
                    gameManager.reloadSpawnLocation();
                    
                    // 显示重载信息
                    sender.sendMessage(ChatColor.GREEN + "✓ 配置文件已热加载！");
                    sender.sendMessage(ChatColor.AQUA + "当前配置：");
                    sender.sendMessage(ChatColor.WHITE + "  - 竞技场半径: " + ChatColor.YELLOW + configManager.getArenaRadius());
                    sender.sendMessage(ChatColor.WHITE + "  - 最少玩家数: " + ChatColor.YELLOW + configManager.getMinPlayers());
                    sender.sendMessage(ChatColor.WHITE + "  - 倒计时时长: " + ChatColor.YELLOW + configManager.getStartCountdown() + "秒");
                    sender.sendMessage(ChatColor.WHITE + "  - 边界伤害: " + ChatColor.YELLOW + configManager.getBorderDamageAmount() + "/秒");
                    sender.sendMessage(ChatColor.WHITE + "  - 物品发放间隔: " + ChatColor.YELLOW + (configManager.getItemInterval() / 20.0) + "秒");
                    sender.sendMessage(ChatColor.WHITE + "  - 物品种类数: " + ChatColor.YELLOW + configManager.getItemWeights().size());
                    if (configManager.hasItemsConfig()) {
                        sender.sendMessage(ChatColor.GRAY + "  - 物品配置来源: items.yml（独立文件）");
                    } else {
                        sender.sendMessage(ChatColor.GRAY + "  - 物品配置来源: config.yml（主配置）");
                    }
                    sender.sendMessage(ChatColor.WHITE + "  - 缩圈间隔: " + ChatColor.YELLOW + (configManager.getShrinkInterval() / 20.0) + "秒");
                    sender.sendMessage(ChatColor.YELLOW + "新配置已生效，可以开始新游戏。");
                    return true;
                
                case "join":
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.use")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    
                    // 如果提供了房间名，使用房间系统
                    if (args.length >= 2) {
                        String arenaName = args[1];
                        if (arenaManager.joinArena(player, arenaName)) {
                            // joinArena 内部已经发送了消息
                            return true;
                        } else {
                            player.sendMessage(ChatColor.RED + "无法加入房间 '" + arenaName + "'！");
                            return true;
                        }
                    }
                    
                    // 否则使用旧的单房间系统（向后兼容）
                    if (gameManager.isRunning()) {
                        player.sendMessage(ChatColor.RED + "游戏已经开始，无法加入！");
                        return true;
                    }
                    if (!gameManager.isPreparing()) {
                        player.sendMessage(ChatColor.RED + "当前没有准备中的游戏！使用 /ripvp start 开始游戏或 /ripvp join <房间名> 加入房间。");
                        return true;
                    }
                    if (gameManager.joinGame(player)) {
                        player.sendMessage(ChatColor.GREEN + "你已加入游戏！");
                        Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + " §a加入了游戏！ (§6" + 
                            gameManager.getParticipantCount() + "§a/§6" + Bukkit.getOnlinePlayers().size() + "§a)");
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "你已经在游戏中了！");
                    }
                    return true;
                
                case "leave":
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.use")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    
                    // 先尝试从房间系统离开
                    if (arenaManager.leaveArena(player)) {
                        return true; // leaveArena 内部已经发送了消息
                    }
                    
                    // 否则使用旧的单房间系统（向后兼容）
                    if (gameManager.isRunning()) {
                        player.sendMessage(ChatColor.RED + "游戏已经开始，无法退出！");
                        return true;
                    }
                    if (!gameManager.isPreparing()) {
                        player.sendMessage(ChatColor.RED + "当前没有准备中的游戏！");
                        return true;
                    }
                    if (gameManager.leaveGame(player)) {
                        player.sendMessage(ChatColor.YELLOW + "你已退出游戏！");
                        Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + " §c退出了游戏！ (§6" + 
                            gameManager.getParticipantCount() + "§c/§6" + Bukkit.getOnlinePlayers().size() + "§c)");
                    } else {
                        player.sendMessage(ChatColor.RED + "你没有参与游戏！");
                    }
                    return true;
                
                case "cancel":
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.admin")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    if (gameManager.isRunning()) {
                        player.sendMessage(ChatColor.RED + "游戏已经开始，使用 /ripvp stop 停止游戏。");
                        return true;
                    }
                    if (!gameManager.isPreparing()) {
                        player.sendMessage(ChatColor.RED + "当前没有准备中的游戏！");
                        return true;
                    }
                    gameManager.cancelGame();
                    player.sendMessage(ChatColor.GREEN + "游戏已取消！");
                    return true;
                
                case "create":
                    if (!sender.hasPermission("ripvp.admin")) {
                        sender.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "用法：/ripvp create <房间名>");
                        return true;
                    }
                    String arenaName = args[1];
                    Player creator = (sender instanceof Player) ? (Player) sender : null;
                    if (arenaManager.createArena(arenaName, creator)) {
                        sender.sendMessage(ChatColor.GREEN + "✓ 房间 '" + arenaName + "' 已创建！");
                        if (creator != null) {
                            sender.sendMessage(ChatColor.GREEN + "✓ 地图投票已开始！");
                            sender.sendMessage(ChatColor.GREEN + "✓ 你已自动加入房间！");
                            sender.sendMessage(ChatColor.YELLOW + "使用 /ripvp vote <地图名> 投票选择地图");
                            
                            // 显示可用地图
                            RandomItemPVP pluginInstance = RandomItemPVP.getInstance();
                            if (pluginInstance != null) {
                                MapVoteManager voteManager = pluginInstance.getMapVoteManager();
                                if (voteManager != null && voteManager.isVoting(arenaName)) {
                                    Map<String, Integer> voteResults = voteManager.getVoteResults(arenaName);
                                    if (!voteResults.isEmpty()) {
                                        StringBuilder mapList = new StringBuilder("§a可用地图：");
                                        for (String mapId : voteResults.keySet()) {
                                            String mapName = configManager.getMapName(mapId);
                                            mapList.append(" §e").append(mapName).append("§7(/ripvp vote ").append(mapId).append(")");
                                        }
                                        sender.sendMessage(mapList.toString());
                                    }
                                }
                            }
                        } else {
                            sender.sendMessage(ChatColor.YELLOW + "提示：玩家可以使用 /ripvp join " + arenaName + " 加入房间");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "房间 '" + arenaName + "' 已存在！");
                    }
                    return true;
                
                case "delete":
                    if (!sender.hasPermission("ripvp.admin")) {
                        sender.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "用法：/ripvp delete <房间名>");
                        return true;
                    }
                    String deleteName = args[1];
                    if (arenaManager.deleteArena(deleteName)) {
                        sender.sendMessage(ChatColor.GREEN + "✓ 房间 '" + deleteName + "' 已删除！");
                    } else {
                        sender.sendMessage(ChatColor.RED + "房间 '" + deleteName + "' 不存在！");
                    }
                    return true;
                
                case "vote":
                    if (player == null) return true;
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "用法：/ripvp vote <地图名>");
                        player.sendMessage(ChatColor.YELLOW + "使用 /ripvp vote cancel 取消投票（弃票）");
                        player.sendMessage(ChatColor.YELLOW + "使用 /ripvp list 查看正在投票的房间");
                        return true;
                    }
                    
                    // 检查玩家是否在房间中
                    String playerArenaName = arenaManager.getPlayerArena(player);
                    if (playerArenaName == null) {
                        player.sendMessage(ChatColor.RED + "你不在任何房间中！");
                        player.sendMessage(ChatColor.YELLOW + "使用 /ripvp join <房间名> 加入房间");
                        return true;
                    }
                    
                    // 获取投票管理器
                    RandomItemPVP pluginInstance = RandomItemPVP.getInstance();
                    if (pluginInstance == null) {
                        player.sendMessage(ChatColor.RED + "插件未初始化！");
                        return true;
                    }
                    
                    MapVoteManager voteManager = pluginInstance.getMapVoteManager();
                    if (voteManager == null) {
                        player.sendMessage(ChatColor.RED + "投票系统未初始化！");
                        return true;
                    }
                    
                    // 检查房间是否正在投票
                    if (!voteManager.isVoting(playerArenaName)) {
                        player.sendMessage(ChatColor.RED + "房间 '" + playerArenaName + "' 的投票已结束！");
                        return true;
                    }
                    
                    // 投票（支持弃票）
                    String mapId = args[1];
                    if (voteManager.vote(playerArenaName, player, mapId)) {
                        // 投票成功消息已在 voteManager 中发送
                    } else {
                        player.sendMessage(ChatColor.RED + "投票失败！请检查地图名是否正确。");
                    }
                    return true;
                
                case "list":
                case "arenas":
                    Set<String> arenaNames = arenaManager.getArenaNames();
                    if (arenaNames.isEmpty()) {
                        sender.sendMessage(ChatColor.YELLOW + "当前没有可用房间！");
                        sender.sendMessage(ChatColor.GRAY + "使用 /ripvp create <房间名> 创建房间");
                        return true;
                    }
                    sender.sendMessage(ChatColor.AQUA + "===== 可用房间 =====");
                    for (String name : arenaNames) {
                        GameArena arena = arenaManager.getArena(name);
                        if (arena != null) {
                            // 同步状态，确保显示正确
                            arena.syncStatus();
                            
                            int playerCount = arena.getPlayerCount();
                            // 使用当前地图的最少玩家数（如果有），否则使用全局配置
                            String currentMapId = arena.getCurrentMapId();
                            int minPlayers = currentMapId != null ? configManager.getMapMinPlayers(currentMapId) : configManager.getMinPlayers();
                            String statusText = getArenaStatusText(arena);
                            
                            // 显示格式：房间名 - 状态 (当前玩家数人，最少需要minPlayers人)
                            if (playerCount < minPlayers) {
                                sender.sendMessage(ChatColor.WHITE + "  " + name + " - " + 
                                    statusText + ChatColor.WHITE + " (" + 
                                    ChatColor.YELLOW + playerCount + 
                                    ChatColor.WHITE + "人，最少需要" + 
                                    ChatColor.YELLOW + minPlayers + 
                                    ChatColor.WHITE + "人)");
                            } else {
                                sender.sendMessage(ChatColor.WHITE + "  " + name + " - " + 
                                    statusText + ChatColor.WHITE + " (" + 
                                    ChatColor.YELLOW + playerCount + 
                                    ChatColor.WHITE + "人)");
                            }
                        }
                    }
                    sender.sendMessage(ChatColor.AQUA + "===================");
                    sender.sendMessage(ChatColor.GRAY + "使用 /ripvp join <房间名> 加入房间");
                    return true;
                
                case "remap":
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.admin")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    // 检查玩家是否在房间中
                    String remapArenaName = arenaManager.getPlayerArena(player);
                    if (remapArenaName == null) {
                        player.sendMessage(ChatColor.RED + "你不在任何房间中！");
                        player.sendMessage(ChatColor.YELLOW + "使用 /ripvp join <房间名> 加入房间");
                        return true;
                    }
                    
                    GameArena remapArena = arenaManager.getArena(remapArenaName);
                    if (remapArena == null) {
                        player.sendMessage(ChatColor.RED + "房间不存在！");
                        return true;
                    }
                    
                    // 只能在准备阶段或等待阶段重新选择地图
                    if (remapArena.isRunning()) {
                        player.sendMessage(ChatColor.RED + "游戏进行中无法重新选择地图！");
                        return true;
                    }
                    
                    // 如果提供了地图ID，使用该地图；否则随机选择
                    if (args.length >= 2) {
                        String remapMapId = args[1];
                        if (configManager.mapExists(remapMapId)) {
                            if (arenaManager.reselectMap(remapArenaName, remapMapId)) {
                                String remapMapName = configManager.getMapName(remapMapId);
                                player.sendMessage(ChatColor.GREEN + "✓ 房间 '" + remapArenaName + "' 已重新选择地图：§e" + remapMapName);
                            } else {
                                player.sendMessage(ChatColor.RED + "重新选择地图失败！");
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + "地图 '" + remapMapId + "' 不存在！");
                            player.sendMessage(ChatColor.YELLOW + "使用 /ripvp list 查看可用地图");
                        }
                    } else {
                        // 随机选择地图
                        if (arenaManager.reselectMap(remapArenaName, null)) {
                            player.sendMessage(ChatColor.GREEN + "✓ 房间 '" + remapArenaName + "' 已随机选择地图");
                        } else {
                            player.sendMessage(ChatColor.RED + "重新选择地图失败！");
                        }
                    }
                    return true;
                
                case "setspawn":
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.admin")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    // 如果提供了房间名，设置房间出生点
                    if (args.length >= 2) {
                        String spawnArenaName = args[1];
                        GameArena arena = arenaManager.getArena(spawnArenaName);
                        if (arena == null) {
                            player.sendMessage(ChatColor.RED + "房间 '" + spawnArenaName + "' 不存在！");
                            return true;
                        }
                        // TODO: 实现设置房间出生点的功能
                        player.sendMessage(ChatColor.YELLOW + "房间出生点功能待实现");
                        return true;
                    }
                    // 否则使用旧的全局出生点设置（向后兼容）
                    gameManager.setSpawnLocation(player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "✓ 游戏出生点已设置为当前位置！");
                    player.sendMessage(ChatColor.GREEN + "✓ 已保存到配置文件，重启后不会丢失！");
                    player.sendMessage(ChatColor.YELLOW + "位置：" + 
                        String.format("世界=%s, X=%.1f, Y=%.1f, Z=%.1f", 
                        player.getWorld().getName(),
                        player.getLocation().getX(),
                        player.getLocation().getY(),
                        player.getLocation().getZ()));
                    return true;
                
                case "stats":
                    if (player == null) return true;
                    // 查看自己的统计或指定玩家的统计
                    if (args.length == 1) {
                        // 查看自己的统计
                        showPlayerStats(player, player);
                    } else {
                        // 查看指定玩家的统计
                        Player target = Bukkit.getPlayer(args[1]);
                        if (target == null) {
                            player.sendMessage(ChatColor.RED + "玩家不在线！");
                            return true;
                        }
                        showPlayerStats(player, target);
                    }
                    return true;
                
                case "top":
                    if (player == null) return true;
                    // 排行榜类型：wins（胜利）、kills（击杀）、kd（KD比率）
                    String rankType = args.length >= 2 ? args[1].toLowerCase() : "wins";
                    showLeaderboard(player, rankType);
                    return true;

                default:
                    sendHelp(sender);
                    return true;
            }
        }
        return true;
    }

    // 命令补全
    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "join", "leave", "cancel", "create", "delete", "list", "setspawn", "status", "reload", "stats", "top", "vote", "remap");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "start":
                    // /ripvp start 可以补全房间名
                    List<String> arenaNames = new ArrayList<>(arenaManager.getArenaNames());
                    return arenaNames.isEmpty() ? null : arenaNames;
                case "join":
                case "delete":
                case "setspawn":
                    // 返回房间名列表
                    return new ArrayList<>(arenaManager.getArenaNames());
                case "vote":
                    // 如果玩家在房间中，返回可用地图列表
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        String playerArenaName = arenaManager.getPlayerArena(player);
                        if (playerArenaName != null) {
                            RandomItemPVP pluginInstance = RandomItemPVP.getInstance();
                            if (pluginInstance != null) {
                                MapVoteManager voteManager = pluginInstance.getMapVoteManager();
                                if (voteManager != null && voteManager.isVoting(playerArenaName)) {
                                    return new ArrayList<>(configManager.getAvailableMaps());
                                }
                            }
                        }
                    }
                    return null;
                case "top":
                    return Arrays.asList("wins", "kills", "kd");
                case "remap":
                    // 如果玩家在房间中，返回可用地图列表
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        String playerArenaName = arenaManager.getPlayerArena(player);
                        if (playerArenaName != null) {
                            return new ArrayList<>(configManager.getAvailableMaps());
                        }
                    }
                    return null;
                case "reload":
                    // reload 命令不需要参数
                    return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }
    
    // 获取房间状态文本
    private String getArenaStatusText(GameArena arena) {
        switch (arena.getStatus()) {
            case WAITING:
                return ChatColor.GREEN + "等待中";
            case PREPARING:
                return ChatColor.YELLOW + "倒计时中";
            case RUNNING:
                return ChatColor.RED + "游戏中";
            case ENDING:
                return ChatColor.GRAY + "结束中";
            default:
                return ChatColor.WHITE + "未知";
        }
    }

    // 发送帮助信息
    private void sendHelp(CommandSender sender) {
        boolean isAdmin = sender instanceof Player && sender.hasPermission("ripvp.admin");
        
        sender.sendMessage(ChatColor.YELLOW + "===== /ripvp 命令帮助 =====");
        sender.sendMessage(ChatColor.GREEN + "玩家命令：");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp start [房间名] - 发起游戏或开启房间（房间不存在时自动创建）");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp join [房间名] - 加入游戏或房间");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp leave - 退出游戏或房间");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp list - 查看所有房间");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp vote <地图名> - 投票选择地图（在房间中时）");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp status - 查看游戏状态");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp stats [玩家] - 查看统计数据");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp top [wins|kills|kd] - 查看排行榜");
        
        if (isAdmin) {
            sender.sendMessage(ChatColor.RED + "管理员命令：");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp create <房间名> - 创建房间（控制台可用）");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp delete <房间名> - 删除房间（控制台可用）");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp remap [地图名] - 重新选择地图（准备阶段）");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp setspawn [房间名] - 设置游戏出生点");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp stop - 强制停止当前游戏");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp cancel - 取消准备中的游戏");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp reload - 热加载配置文件（控制台可用）");
        }
        
        sender.sendMessage(ChatColor.YELLOW + "==========================");
    }
    
    /**
     * 显示玩家统计数据
     */
    private void showPlayerStats(Player viewer, Player target) {
        viewer.sendMessage(ChatColor.AQUA + "正在加载统计数据...");
        
        statsManager.getPlayerStats(target.getUniqueId(), target.getName()).thenAccept(stats -> {
            Bukkit.getScheduler().runTask(RandomItemPVP.getInstance(), () -> {
                viewer.sendMessage(ChatColor.GOLD + "========== " + stats.getPlayerName() + " 的统计 ==========");
                viewer.sendMessage(ChatColor.YELLOW + "胜利次数：" + ChatColor.GREEN + stats.getWins());
                viewer.sendMessage(ChatColor.YELLOW + "失败次数：" + ChatColor.RED + stats.getLosses());
                viewer.sendMessage(ChatColor.YELLOW + "总场次：" + ChatColor.WHITE + stats.getGamesPlayed());
                viewer.sendMessage(ChatColor.YELLOW + "胜率：" + ChatColor.AQUA + String.format("%.1f%%", stats.getWinRate()));
                viewer.sendMessage(ChatColor.YELLOW + "击杀数：" + ChatColor.GREEN + stats.getKills());
                viewer.sendMessage(ChatColor.YELLOW + "死亡数：" + ChatColor.RED + stats.getDeaths());
                viewer.sendMessage(ChatColor.YELLOW + "KD比率：" + ChatColor.GOLD + String.format("%.2f", stats.getKDRatio()));
                viewer.sendMessage(ChatColor.GOLD + "=========================================");
            });
        });
    }
    
    /**
     * 显示排行榜
     */
    private void showLeaderboard(Player player, String type) {
        player.sendMessage(ChatColor.AQUA + "正在加载排行榜...");
        
        switch (type) {
            case "wins":
                statsManager.getTopWins(10).thenAccept(topPlayers -> {
                    Bukkit.getScheduler().runTask(RandomItemPVP.getInstance(), () -> {
                        player.sendMessage(ChatColor.GOLD + "========== 胜利排行榜 TOP 10 ==========");
                        int rank = 1;
                        for (PlayerStatsManager.PlayerStats stats : topPlayers) {
                            String medal = getMedalForRank(rank);
                            player.sendMessage(ChatColor.YELLOW + "#" + rank + " " + medal + " " + 
                                ChatColor.WHITE + stats.getPlayerName() + " - " + 
                                ChatColor.GREEN + stats.getWins() + " 胜 " + 
                                ChatColor.GRAY + "(" + stats.getGamesPlayed() + " 场)");
                            rank++;
                        }
                        player.sendMessage(ChatColor.GOLD + "=========================================");
                    });
                });
                break;
            
            case "kills":
                statsManager.getTopKills(10).thenAccept(topPlayers -> {
                    Bukkit.getScheduler().runTask(RandomItemPVP.getInstance(), () -> {
                        player.sendMessage(ChatColor.GOLD + "========== 击杀排行榜 TOP 10 ==========");
                        int rank = 1;
                        for (PlayerStatsManager.PlayerStats stats : topPlayers) {
                            String medal = getMedalForRank(rank);
                            player.sendMessage(ChatColor.YELLOW + "#" + rank + " " + medal + " " + 
                                ChatColor.WHITE + stats.getPlayerName() + " - " + 
                                ChatColor.RED + stats.getKills() + " 击杀 " + 
                                ChatColor.GRAY + "(KD: " + String.format("%.2f", stats.getKDRatio()) + ")");
                            rank++;
                        }
                        player.sendMessage(ChatColor.GOLD + "=========================================");
                    });
                });
                break;
            
            case "kd":
                statsManager.getTopKD(10).thenAccept(topPlayers -> {
                    Bukkit.getScheduler().runTask(RandomItemPVP.getInstance(), () -> {
                        player.sendMessage(ChatColor.GOLD + "========== KD比率排行榜 TOP 10 ==========");
                        player.sendMessage(ChatColor.GRAY + "（需要至少 10 场游戏才能上榜）");
                        int rank = 1;
                        for (PlayerStatsManager.PlayerStats stats : topPlayers) {
                            String medal = getMedalForRank(rank);
                            player.sendMessage(ChatColor.YELLOW + "#" + rank + " " + medal + " " + 
                                ChatColor.WHITE + stats.getPlayerName() + " - " + 
                                ChatColor.GOLD + String.format("%.2f", stats.getKDRatio()) + " KD " + 
                                ChatColor.GRAY + "(" + stats.getKills() + "/" + stats.getDeaths() + ")");
                            rank++;
                        }
                        player.sendMessage(ChatColor.GOLD + "=========================================");
                    });
                });
                break;
            
            default:
                player.sendMessage(ChatColor.RED + "无效的排行榜类型！可用: wins, kills, kd");
                break;
        }
    }
    
    /**
     * 获取排名对应的奖牌
     */
    private String getMedalForRank(int rank) {
        switch (rank) {
            case 1: return "🥇";
            case 2: return "🥈";
            case 3: return "🥉";
            default: return "  ";
        }
    }
}
