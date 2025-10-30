package com.example.randomitempvp;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RandomItemPVP extends JavaPlugin {
    private static RandomItemPVP instance;
    private GameManager gameManager;
    private ConfigManager configManager;
    private ItemAbilityManager itemAbilityManager;
    private RewardManager rewardManager;
    private AirdropManager airdropManager;

    @Override
    public void onEnable() {
        instance = this;

        // 初始化配置管理器（加载config.yml，支持热加载）
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        // 初始化游戏管理器（传入配置）
        gameManager = new GameManager(this, configManager);

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
            RipvpCommand cmdExecutor = new RipvpCommand(gameManager, configManager);
            ripvpCmd.setExecutor(cmdExecutor);
            ripvpCmd.setTabCompleter(cmdExecutor);
        }

        getLogger().info("RandomItemPVP 插件已启用！");
        getLogger().info("特性：TNT投掷 | 火焰弹投掷 | 末影水晶 | 击杀奖励 | 连杀系统 | 空投系统");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stopGame(false);
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
}
