package com.example.randomitempvp;

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

    public RipvpCommand(GameManager gameManager, ConfigManager configManager) {
        this.gameManager = gameManager;
        this.configManager = configManager;
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
                    player.sendMessage(ChatColor.GREEN + "出生点已设置为当前位置！");
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
                    player.sendMessage(ChatColor.GREEN + "✓ 配置文件已热加载！");
                    player.sendMessage(ChatColor.YELLOW + "新配置已生效，可以开始新游戏。");
                    return true;
                
                case "join":
                    if (player == null) return true;
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
            return Arrays.asList("start", "stop", "join", "leave", "cancel", "setspawn", "status", "reload");
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
        
        if (isAdmin) {
            sender.sendMessage(ChatColor.RED + "管理员命令：");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp stop - 强制停止当前游戏");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp cancel - 取消准备中的游戏");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp setspawn - 设置游戏出生点");
            sender.sendMessage(ChatColor.WHITE + "  /ripvp reload - 热加载配置文件");
        }
        
        sender.sendMessage(ChatColor.YELLOW + "==========================");
    }
}
