package org.luminolcraft.randomitempvp;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI 扩展 - 提供玩家统计数据变量
 */
public class RandomItemPVPExpansion extends PlaceholderExpansion {
    private final RandomItemPVP plugin;
    private final PlayerStatsManager statsManager;
    
    public RandomItemPVPExpansion(RandomItemPVP plugin, PlayerStatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
    }
    
    @Override
    @NotNull
    public String getIdentifier() {
        return "randomitempvp";
    }
    
    @Override
    @NotNull
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }
    
    @Override
    @NotNull
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true; // 插件重载后保持注册
    }
    
    @Override
    @Nullable
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        
        // 异步获取数据可能导致延迟，这里使用缓存或同步方式
        // 为了性能，我们提供一个同步的获取方式
        try {
            PlayerStatsManager.PlayerStats stats = statsManager.getPlayerStats(
                player.getUniqueId(), 
                player.getName() != null ? player.getName() : "Unknown"
            ).get(); // 同步等待结果
            
            switch (params.toLowerCase()) {
                // 基础统计
                case "wins":
                    return String.valueOf(stats.getWins());
                
                case "losses":
                    return String.valueOf(stats.getLosses());
                
                case "kills":
                    return String.valueOf(stats.getKills());
                
                case "deaths":
                    return String.valueOf(stats.getDeaths());
                
                case "games":
                case "games_played":
                    return String.valueOf(stats.getGamesPlayed());
                
                // 计算值
                case "kd":
                case "kdratio":
                    return String.format("%.2f", stats.getKDRatio());
                
                case "winrate":
                case "win_rate":
                    return String.format("%.1f", stats.getWinRate());
                
                case "winrate_percent":
                    return String.format("%.1f%%", stats.getWinRate());
                
                // 带格式的显示
                case "kd_formatted":
                    double kd = stats.getKDRatio();
                    if (kd >= 2.0) {
                        return "§a" + String.format("%.2f", kd); // 绿色（优秀）
                    } else if (kd >= 1.0) {
                        return "§e" + String.format("%.2f", kd); // 黄色（良好）
                    } else {
                        return "§c" + String.format("%.2f", kd); // 红色（较低）
                    }
                
                case "winrate_formatted":
                    double winRate = stats.getWinRate();
                    if (winRate >= 50.0) {
                        return "§a" + String.format("%.1f%%", winRate); // 绿色
                    } else if (winRate >= 30.0) {
                        return "§e" + String.format("%.1f%%", winRate); // 黄色
                    } else {
                        return "§c" + String.format("%.1f%%", winRate); // 红色
                    }
                
                // 战绩汇总
                case "record":
                    return stats.getWins() + "胜" + stats.getLosses() + "负";
                
                case "record_en":
                    return stats.getWins() + "W " + stats.getLosses() + "L";
                
                case "kill_death":
                    return stats.getKills() + "/" + stats.getDeaths();
                
                // 排名相关（需要额外查询，这里先返回占位符）
                case "rank_wins":
                    return "N/A"; // 可以后续实现排名缓存
                
                case "rank_kills":
                    return "N/A";
                
                case "rank_kd":
                    return "N/A";
                
                default:
                    return null; // 未知变量
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("获取 PAPI 变量失败: " + params + " for player " + player.getName());
            e.printStackTrace();
            return "§c错误";
        }
    }
}























