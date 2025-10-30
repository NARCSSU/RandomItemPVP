package com.example.randomitempvp;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender; // 修正导入路径
import org.bukkit.entity.Player;

public class MLGCommand implements CommandExecutor { // 确保实现 CommandExecutor 接口
    private final GameManager gameManager;
    private final MLGManager mlgManager;

    public MLGCommand(GameManager gameManager, MLGManager mlgManager) {
        this.gameManager = gameManager;
        this.mlgManager = mlgManager;
    }

    @Override // 必须正确实现接口方法
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("start")) {
                if (gameManager.isRunning()) {
                    player.sendMessage(ChatColor.RED + "Cannot start MLG while RIPVP is running!");
                    return true;
                }
                mlgManager.start();
                player.sendMessage(ChatColor.GREEN + "MLG event started!");
                return true;
            } else if (args[0].equalsIgnoreCase("stop")) {
                mlgManager.stop();
                player.sendMessage(ChatColor.RED + "MLG event stopped!");
                return true;
            }
        }

        player.sendMessage(ChatColor.YELLOW + "Usage: /mlg start | stop");
        return true;
    }
}
