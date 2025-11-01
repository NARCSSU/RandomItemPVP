package org.luminolcraft.randomitempvp;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

/**
 * 数据库管理器 - 支持 SQLite 和 MySQL
 */
public class DatabaseManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private HikariDataSource dataSource;
    private DatabaseType databaseType;
    
    public enum DatabaseType {
        SQLITE,
        MYSQL
    }
    
    public DatabaseManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }
    
    /**
     * 连接数据库
     */
    public void connect() {
        String typeString = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();
        
        try {
            databaseType = DatabaseType.valueOf(typeString);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无效的数据库类型：" + typeString + "，使用默认的 SQLite");
            databaseType = DatabaseType.SQLITE;
        }
        
        try {
            if (databaseType == DatabaseType.SQLITE) {
                connectSQLite();
            } else {
                connectMySQL();
            }
            
            // 创建表
            createTables();
            
            plugin.getLogger().info("数据库连接成功！使用：" + databaseType.name());
        } catch (SQLException e) {
            plugin.getLogger().severe("数据库连接失败！");
            e.printStackTrace();
        }
    }
    
    /**
     * 连接 SQLite
     */
    private void connectSQLite() throws SQLException {
        String filePath = plugin.getConfig().getString("database.sqlite.file", "plugins/RandomItemPVP/data.db");
        File dbFile = new File(filePath);
        
        // 确保父目录存在
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("RandomItemPVP-SQLite");
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        hikariConfig.setMaximumPoolSize(1); // SQLite 只支持单连接
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(60000); // 增加超时时间到60秒
        hikariConfig.setIdleTimeout(600000); // 10分钟空闲超时
        hikariConfig.setMaxLifetime(1800000); // 30分钟最大生命周期
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setAutoCommit(true); // 自动提交
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("journal_mode", "WAL"); // SQLite WAL 模式，支持并发读
        
        dataSource = new HikariDataSource(hikariConfig);
    }
    
    /**
     * 连接 MySQL
     */
    private void connectMySQL() throws SQLException {
        ConfigurationSection mysqlConfig = plugin.getConfig().getConfigurationSection("database.mysql");
        if (mysqlConfig == null) {
            throw new SQLException("MySQL 配置未找到！");
        }
        
        String host = mysqlConfig.getString("host", "localhost");
        int port = mysqlConfig.getInt("port", 3306);
        String database = mysqlConfig.getString("database", "randomitempvp");
        String username = mysqlConfig.getString("username", "root");
        String password = mysqlConfig.getString("password", "");
        
        // 构建连接参数
        StringBuilder propertiesBuilder = new StringBuilder();
        ConfigurationSection properties = mysqlConfig.getConfigurationSection("properties");
        if (properties != null) {
            for (String key : properties.getKeys(false)) {
                Object value = properties.get(key);
                propertiesBuilder.append("&").append(key).append("=").append(value);
            }
        }
        
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?%s", 
            host, port, database, propertiesBuilder.toString());
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("RandomItemPVP-MySQL");
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        
        // 连接池设置
        ConfigurationSection poolConfig = mysqlConfig.getConfigurationSection("pool");
        if (poolConfig != null) {
            hikariConfig.setMaximumPoolSize(poolConfig.getInt("maximum-pool-size", 10));
            hikariConfig.setMinimumIdle(poolConfig.getInt("minimum-idle", 2));
            hikariConfig.setConnectionTimeout(poolConfig.getLong("connection-timeout", 30000));
            hikariConfig.setIdleTimeout(poolConfig.getLong("idle-timeout", 600000));
            hikariConfig.setMaxLifetime(poolConfig.getLong("max-lifetime", 1800000));
        }
        
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        dataSource = new HikariDataSource(hikariConfig);
    }
    
    /**
     * 创建数据表
     */
    private void createTables() throws SQLException {
        String createTableSQL;
        
        if (databaseType == DatabaseType.SQLITE) {
            createTableSQL = """
                CREATE TABLE IF NOT EXISTS player_stats (
                    uuid TEXT PRIMARY KEY,
                    player_name TEXT NOT NULL,
                    wins INTEGER DEFAULT 0,
                    losses INTEGER DEFAULT 0,
                    kills INTEGER DEFAULT 0,
                    deaths INTEGER DEFAULT 0,
                    games_played INTEGER DEFAULT 0,
                    last_played INTEGER DEFAULT 0
                )
                """;
        } else {
            createTableSQL = """
                CREATE TABLE IF NOT EXISTS player_stats (
                    uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(16) NOT NULL,
                    wins INT DEFAULT 0,
                    losses INT DEFAULT 0,
                    kills INT DEFAULT 0,
                    deaths INT DEFAULT 0,
                    games_played INT DEFAULT 0,
                    last_played BIGINT DEFAULT 0,
                    INDEX idx_wins (wins),
                    INDEX idx_kills (kills)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
        }
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(createTableSQL)) {
            stmt.execute();
        }
    }
    
    /**
     * 获取数据库连接
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("数据库未连接！");
        }
        return dataSource.getConnection();
    }
    
    /**
     * 异步执行SQL（无返回值）
     */
    public CompletableFuture<Void> executeAsync(String sql, Object... params) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("SQL 执行失败：" + sql);
                e.printStackTrace();
            }
        });
    }
    
    /**
     * 关闭数据库连接
     */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("数据库连接已关闭。");
        }
    }
    
    /**
     * 获取数据库类型
     */
    public DatabaseType getDatabaseType() {
        return databaseType;
    }
}

