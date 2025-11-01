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

public class RipvpCommand implements CommandExecutor, TabCompleter {
    private final GameManager gameManager;
    private final ConfigManager configManager;
    private final PlayerStatsManager statsManager;

    public RipvpCommand(GameManager gameManager, ConfigManager configManager, PlayerStatsManager statsManager) {
        this.gameManager = gameManager;
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

        // 仅玩家可执行（除了status）
        if (!(sender instanceof Player) && !args[0].equalsIgnoreCase("status")) {
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

                case "setspawn":
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.admin")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
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

                case "status":
                    sender.sendMessage(ChatColor.AQUA + "===== 游戏状态 =====");
                    sender.sendMessage(ChatColor.WHITE + "是否运行：" + (gameManager.isRunning() ? ChatColor.GREEN + "是" : ChatColor.RED + "否"));
                    sender.sendMessage(ChatColor.WHITE + "存活玩家：" + ChatColor.YELLOW + gameManager.getAliveCount());
                    sender.sendMessage(ChatColor.AQUA + "===================");
                    return true;

                case "reload":
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.admin")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    
                    // 检查是否有游戏在进行
                    if (gameManager.isRunning()) {
                        player.sendMessage(ChatColor.RED + "游戏进行中无法热加载配置！");
                        player.sendMessage(ChatColor.YELLOW + "请先使用 /ripvp stop 停止游戏，再重新加载配置。");
                        return true;
                    }
                    
                    if (gameManager.isPreparing()) {
                        player.sendMessage(ChatColor.RED + "游戏准备中无法热加载配置！");
                        player.sendMessage(ChatColor.YELLOW + "请先使用 /ripvp cancel 取消游戏，再重新加载配置。");
                        return true;
                    }
                    
                    // 热加载配置
                    configManager.reloadConfig();
                    gameManager.reloadSpawnLocation();
                    player.sendMessage(ChatColor.GREEN + "✓ 配置文件已热加载！");
                    player.sendMessage(ChatColor.GREEN + "✓ 游戏出生点已重新加载！");
                    player.sendMessage(ChatColor.YELLOW + "新配置已生效，可以开始新游戏。");
                    return true;
                
                case "join":
                    if (player == null) return true;
                    if (!player.hasPermission("ripvp.use")) {
                        player.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
                        return true;
                    }
                    if (gameManager.isRunning()) {
                        player.sendMessage(ChatColor.RED + "游戏已经开始，无法加入！");
                        return true;
                    }
                    if (!gameManager.isPreparing()) {
                        player.sendMessage(ChatColor.RED + "当前没有准备中的游戏！使用 /ripvp start 开始游戏。");
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
            return Arrays.asList("start", "stop", "join", "leave", "cancel", "setspawn", "status", "reload", "stats", "top");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            return Arrays.asList("wins", "kills", "kd");
        }
        return new ArrayList<>();
    }

    // 发送帮助信息
    private void sendHelp(CommandSender sender) {
        boolean isAdmin = sender instanceof Player && sender.hasPermission("ripvp.admin");
        
        sender.sendMessage(ChatColor.YELLOW + "===== /ripvp 命令帮助 =====");
        sender.sendMessage(ChatColor.GREEN + "玩家命令：");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp start - 发起游戏（30秒倒计时）");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp join - 加入准备中的游戏");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp leave - 退出准备中的游戏");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp status - 查看游戏状态");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp stats [玩家] - 查看统计数据");
        sender.sendMessage(ChatColor.WHITE + "  /ripvp top [wins|kills|kd] - 查看排行榜");
        
        if (isAdmin) {
            sender.sendMessage(ChatColor.RED + "管理员命令：");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp stop - 强制停止当前游戏");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp cancel - 取消准备中的游戏");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp setspawn - 设置游戏出生点");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp reload - 热加载配置文件");
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
