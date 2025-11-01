package org.luminolcraft.randomitempvp;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 玩家统计数据管理器
 */
public class PlayerStatsManager {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    
    public PlayerStatsManager(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }
    
    /**
     * 玩家统计数据类
     */
    public static class PlayerStats {
        private final UUID uuid;
        private String playerName;
        private int wins;
        private int losses;
        private int kills;
        private int deaths;
        private int gamesPlayed;
        private long lastPlayed;
        
        public PlayerStats(UUID uuid, String playerName) {
            this.uuid = uuid;
            this.playerName = playerName;
        }
        
        public UUID getUuid() { return uuid; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String name) { this.playerName = name; }
        public int getWins() { return wins; }
        public void setWins(int wins) { this.wins = wins; }
        public int getLosses() { return losses; }
        public void setLosses(int losses) { this.losses = losses; }
        public int getKills() { return kills; }
        public void setKills(int kills) { this.kills = kills; }
        public int getDeaths() { return deaths; }
        public void setDeaths(int deaths) { this.deaths = deaths; }
        public int getGamesPlayed() { return gamesPlayed; }
        public void setGamesPlayed(int games) { this.gamesPlayed = games; }
        public long getLastPlayed() { return lastPlayed; }
        public void setLastPlayed(long time) { this.lastPlayed = time; }
        
        /**
         * 计算 KD 比率
         */
        public double getKDRatio() {
            if (deaths == 0) {
                return kills;
            }
            return (double) kills / deaths;
        }
        
        /**
         * 计算胜率
         */
        public double getWinRate() {
            if (gamesPlayed == 0) {
                return 0.0;
            }
            return (double) wins / gamesPlayed * 100;
        }
    }
    
    /**
     * 获取玩家统计数据（异步）
     */
    public CompletableFuture<PlayerStats> getPlayerStats(UUID uuid, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String selectSql = "SELECT * FROM player_stats WHERE uuid = ?";
            String insertSql = "INSERT INTO player_stats (uuid, player_name, last_played) VALUES (?, ?, ?)";
            
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                
                stmt.setString(1, uuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    PlayerStats stats = new PlayerStats(uuid, playerName);
                    
                    if (rs.next()) {
                        // 玩家数据存在，读取
                        stats.setPlayerName(rs.getString("player_name"));
                        stats.setWins(rs.getInt("wins"));
                        stats.setLosses(rs.getInt("losses"));
                        stats.setKills(rs.getInt("kills"));
                        stats.setDeaths(rs.getInt("deaths"));
                        stats.setGamesPlayed(rs.getInt("games_played"));
                        stats.setLastPlayed(rs.getLong("last_played"));
                    } else {
                        // 新玩家，直接在当前连接中初始化数据（避免嵌套异步导致死锁）
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                            insertStmt.setString(1, uuid.toString());
                            insertStmt.setString(2, playerName);
                            insertStmt.setLong(3, System.currentTimeMillis());
                            insertStmt.executeUpdate();
                        }
                    }
                    
                    return stats;
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("获取玩家统计数据失败：" + uuid);
                e.printStackTrace();
                return new PlayerStats(uuid, playerName);
            }
        });
    }
    
    /**
     * 创建玩家统计数据
     */
    private CompletableFuture<Void> createPlayerStats(UUID uuid, String playerName) {
        String sql = "INSERT INTO player_stats (uuid, player_name, last_played) VALUES (?, ?, ?)";
        return database.executeAsync(sql, uuid.toString(), playerName, System.currentTimeMillis());
    }
    
    /**
     * 更新玩家名称
     */
    public CompletableFuture<Void> updatePlayerName(UUID uuid, String playerName) {
        String sql = "UPDATE player_stats SET player_name = ? WHERE uuid = ?";
        return database.executeAsync(sql, playerName, uuid.toString());
    }
    
    /**
     * 记录玩家胜利
     */
    public CompletableFuture<Void> recordWin(Player player) {
        return CompletableFuture.runAsync(() -> {
            UUID uuid = player.getUniqueId();
            String playerName = player.getName();
            long now = System.currentTimeMillis();
            
            // 使用 UPSERT 确保数据存在（直接在当前连接中执行）
            String sql;
            if (database.getDatabaseType() == DatabaseManager.DatabaseType.SQLITE) {
                sql = "INSERT INTO player_stats (uuid, player_name, wins, games_played, last_played) VALUES (?, ?, 1, 1, ?) " +
                      "ON CONFLICT(uuid) DO UPDATE SET wins = wins + 1, games_played = games_played + 1, player_name = ?, last_played = ?";
            } else {
                sql = "INSERT INTO player_stats (uuid, player_name, wins, games_played, last_played) VALUES (?, ?, 1, 1, ?) " +
                      "ON DUPLICATE KEY UPDATE wins = wins + 1, games_played = games_played + 1, player_name = ?, last_played = ?";
            }
            
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, playerName);
                stmt.setLong(3, now);
                stmt.setString(4, playerName);
                stmt.setLong(5, now);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("记录玩家胜利失败：" + playerName);
                e.printStackTrace();
            }
        });
    }
    
    /**
     * 记录玩家失败
     */
    public CompletableFuture<Void> recordLoss(Player player) {
        return CompletableFuture.runAsync(() -> {
            UUID uuid = player.getUniqueId();
            String playerName = player.getName();
            long now = System.currentTimeMillis();
            
            String sql;
            if (database.getDatabaseType() == DatabaseManager.DatabaseType.SQLITE) {
                sql = "INSERT INTO player_stats (uuid, player_name, losses, games_played, last_played) VALUES (?, ?, 1, 1, ?) " +
                      "ON CONFLICT(uuid) DO UPDATE SET losses = losses + 1, games_played = games_played + 1, player_name = ?, last_played = ?";
            } else {
                sql = "INSERT INTO player_stats (uuid, player_name, losses, games_played, last_played) VALUES (?, ?, 1, 1, ?) " +
                      "ON DUPLICATE KEY UPDATE losses = losses + 1, games_played = games_played + 1, player_name = ?, last_played = ?";
            }
            
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, playerName);
                stmt.setLong(3, now);
                stmt.setString(4, playerName);
                stmt.setLong(5, now);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("记录玩家失败失败：" + playerName);
                e.printStackTrace();
            }
        });
    }
    
    /**
     * 记录玩家击杀
     */
    public CompletableFuture<Void> recordKill(Player player) {
        return CompletableFuture.runAsync(() -> {
            UUID uuid = player.getUniqueId();
            String playerName = player.getName();
            long now = System.currentTimeMillis();
            
            String sql;
            if (database.getDatabaseType() == DatabaseManager.DatabaseType.SQLITE) {
                sql = "INSERT INTO player_stats (uuid, player_name, kills, last_played) VALUES (?, ?, 1, ?) " +
                      "ON CONFLICT(uuid) DO UPDATE SET kills = kills + 1, player_name = ?, last_played = ?";
            } else {
                sql = "INSERT INTO player_stats (uuid, player_name, kills, last_played) VALUES (?, ?, 1, ?) " +
                      "ON DUPLICATE KEY UPDATE kills = kills + 1, player_name = ?, last_played = ?";
            }
            
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, playerName);
                stmt.setLong(3, now);
                stmt.setString(4, playerName);
                stmt.setLong(5, now);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("记录玩家击杀失败：" + playerName);
                e.printStackTrace();
            }
        });
    }
    
    /**
     * 记录玩家死亡
     */
    public CompletableFuture<Void> recordDeath(Player player) {
        return CompletableFuture.runAsync(() -> {
            UUID uuid = player.getUniqueId();
            String playerName = player.getName();
            long now = System.currentTimeMillis();
            
            String sql;
            if (database.getDatabaseType() == DatabaseManager.DatabaseType.SQLITE) {
                sql = "INSERT INTO player_stats (uuid, player_name, deaths, last_played) VALUES (?, ?, 1, ?) " +
                      "ON CONFLICT(uuid) DO UPDATE SET deaths = deaths + 1, player_name = ?, last_played = ?";
            } else {
                sql = "INSERT INTO player_stats (uuid, player_name, deaths, last_played) VALUES (?, ?, 1, ?) " +
                      "ON DUPLICATE KEY UPDATE deaths = deaths + 1, player_name = ?, last_played = ?";
            }
            
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, playerName);
                stmt.setLong(3, now);
                stmt.setString(4, playerName);
                stmt.setLong(5, now);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("记录玩家死亡失败：" + playerName);
                e.printStackTrace();
            }
        });
    }
    
    /**
     * 获取排行榜（按胜利次数）
     */
    public CompletableFuture<List<PlayerStats>> getTopWins(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM player_stats ORDER BY wins DESC LIMIT ?";
            List<PlayerStats> topPlayers = new ArrayList<>();
            
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, limit);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String name = rs.getString("player_name");
                        PlayerStats stats = new PlayerStats(uuid, name);
                        stats.setWins(rs.getInt("wins"));
                        stats.setLosses(rs.getInt("losses"));
                        stats.setKills(rs.getInt("kills"));
                        stats.setDeaths(rs.getInt("deaths"));
                        stats.setGamesPlayed(rs.getInt("games_played"));
                        stats.setLastPlayed(rs.getLong("last_played"));
                        topPlayers.add(stats);
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("获取排行榜失败！");
                e.printStackTrace();
            }
            
            return topPlayers;
        });
    }
    
    /**
     * 获取排行榜（按击杀数）
     */
    public CompletableFuture<List<PlayerStats>> getTopKills(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM player_stats ORDER BY kills DESC LIMIT ?";
            List<PlayerStats> topPlayers = new ArrayList<>();
            
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, limit);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String name = rs.getString("player_name");
                        PlayerStats stats = new PlayerStats(uuid, name);
                        stats.setWins(rs.getInt("wins"));
                        stats.setLosses(rs.getInt("losses"));
                        stats.setKills(rs.getInt("kills"));
                        stats.setDeaths(rs.getInt("deaths"));
                        stats.setGamesPlayed(rs.getInt("games_played"));
                        stats.setLastPlayed(rs.getLong("last_played"));
                        topPlayers.add(stats);
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("获取排行榜失败！");
                e.printStackTrace();
            }
            
            return topPlayers;
        });
    }
    
    /**
     * 获取排行榜（按KD比率）
     */
    public CompletableFuture<List<PlayerStats>> getTopKD(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            // 需要至少10场游戏才能上榜（避免只打1场就上榜）
            String sql = "SELECT * FROM player_stats WHERE games_played >= 10 ORDER BY (CAST(kills AS REAL) / NULLIF(deaths, 0)) DESC LIMIT ?";
            if (database.getDatabaseType() == DatabaseManager.DatabaseType.MYSQL) {
                sql = "SELECT * FROM player_stats WHERE games_played >= 10 ORDER BY (kills / NULLIF(deaths, 0)) DESC LIMIT ?";
            }
            
            List<PlayerStats> topPlayers = new ArrayList<>();
            
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, limit);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String name = rs.getString("player_name");
                        PlayerStats stats = new PlayerStats(uuid, name);
                        stats.setWins(rs.getInt("wins"));
                        stats.setLosses(rs.getInt("losses"));
                        stats.setKills(rs.getInt("kills"));
                        stats.setDeaths(rs.getInt("deaths"));
                        stats.setGamesPlayed(rs.getInt("games_played"));
                        stats.setLastPlayed(rs.getLong("last_played"));
                        topPlayers.add(stats);
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("获取排行榜失败！");
                e.printStackTrace();
            }
            
            return topPlayers;
        });
    }
}

