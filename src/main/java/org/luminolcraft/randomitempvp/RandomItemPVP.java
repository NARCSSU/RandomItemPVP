package org.luminolcraft.randomitempvp;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RandomItemPVP extends JavaPlugin {
    private static RandomItemPVP instance;
    private GameManager gameManager;
    private ArenaManager arenaManager;
    private ConfigManager configManager;
    private ItemAbilityManager itemAbilityManager;
    private RewardManager rewardManager;
    private AirdropManager airdropManager;
    private DatabaseManager databaseManager;
    private PlayerStatsManager playerStatsManager;
    private MapVoteManager mapVoteManager;
    private RandomItemPVPExpansion placeholderExpansion;

    @Override
    public void onEnable() {
        instance = this;

        // 初始化配置管理器（加载config.yml，支持热加载）
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        // 初始化数据库管理器
        databaseManager = new DatabaseManager(this, configManager);
        databaseManager.connect();
        
        // 初始化玩家统计管理器
        playerStatsManager = new PlayerStatsManager(this, databaseManager);

        // 初始化游戏管理器（传入配置和统计管理器）
        gameManager = new GameManager(this, configManager, playerStatsManager);
        
        // 初始化房间管理器
        arenaManager = new ArenaManager(this, configManager, playerStatsManager);
        
        // 初始化 Worlds 插件集成（必须在其他初始化之前）
        WorldsIntegration.initialize();
        
        // 初始化地图投票管理器
        mapVoteManager = new MapVoteManager(this, configManager);
        mapVoteManager.setArenaManager(arenaManager); // 设置 ArenaManager 引用

        // 初始化特殊物品能力管理器
        itemAbilityManager = new ItemAbilityManager(this, gameManager);
        
        // 初始化奖励管理器（击杀奖励、连杀系统）
        rewardManager = new RewardManager(this, gameManager);
        
        // 初始化空投管理器
        airdropManager = new AirdropManager(this, gameManager);

        // 注册监听器
        Bukkit.getPluginManager().registerEvents(gameManager, this);
        Bukkit.getPluginManager().registerEvents(itemAbilityManager, this);
        Bukkit.getPluginManager().registerEvents(rewardManager, this);
        Bukkit.getPluginManager().registerEvents(airdropManager, this);

        // 注册命令（/ripvp）
        PluginCommand ripvpCmd = getCommand("ripvp");
        if (ripvpCmd != null) {
            RipvpCommand cmdExecutor = new RipvpCommand(gameManager, arenaManager, configManager, playerStatsManager);
            ripvpCmd.setExecutor(cmdExecutor);
            ripvpCmd.setTabCompleter(cmdExecutor);
        }

        // 注册 PlaceholderAPI 扩展（如果安装了PAPI）
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new RandomItemPVPExpansion(this, playerStatsManager);
            if (placeholderExpansion.register()) {
                getLogger().info("PlaceholderAPI 扩展已注册！");
            } else {
                getLogger().warning("PlaceholderAPI 扩展注册失败！");
            }
        } else {
            getLogger().info("未检测到 PlaceholderAPI，变量功能将不可用。");
        }

        // 检查 Worlds 插件是否可用（必需依赖）
        if (!WorldsIntegration.isWorldsAvailable()) {
            getLogger().severe("============================================");
            getLogger().severe("Worlds 插件未安装或未启用！");
            getLogger().severe("RandomItemPVP 插件需要 Worlds 插件才能运行！");
            getLogger().severe("请安装 Worlds 插件后重启服务器。");
            getLogger().severe("============================================");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        getLogger().info("检测到 Worlds 插件，已启用多世界支持！");
        getLogger().info("提示：可以在配置文件中使用 Worlds 插件的世界 key 来引用地图");
        
        // 列出可用的方法（用于调试）
        if (getLogger().isLoggable(java.util.logging.Level.FINE)) {
            WorldsIntegration.listAvailableMethods();
        }
        
        getLogger().info("RandomItemPVP 插件已启用！");
        getLogger().info("特性：TNT投掷 | 火焰弹投掷 | 末影水晶 | 击杀奖励 | 连杀系统 | 空投系统 | 数据统计");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stopGame(false);
        }
        
        // 注销 PlaceholderAPI 扩展
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        
        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        
        getLogger().info("RandomItemPVP 插件已禁用！");
    }

    public static RandomItemPVP getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ItemAbilityManager getItemAbilityManager() {
        return itemAbilityManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public AirdropManager getAirdropManager() {
        return airdropManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlayerStatsManager getPlayerStatsManager() {
        return playerStatsManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }
    
    public MapVoteManager getMapVoteManager() {
        return mapVoteManager;
    }
}
