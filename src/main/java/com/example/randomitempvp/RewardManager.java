package com.example.randomitempvp;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * 管理击杀奖励系统
 */
public class RewardManager implements Listener {
    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final Random random = new Random();
    
    // 击杀奖励配置
    private static final double KILL_HEAL_AMOUNT = 6.0; // 击杀回血3颗心
    private static final int KILL_REWARD_ITEMS = 3; // 击杀奖励3个随机物品
    
    // 死亡消息模板
    private static final String[] DEATH_MESSAGES = {
        "%killer% §e制裁了 §c%victim%",
        "%killer% §e送 §c%victim% §e回家了",
        "%killer% §e教 §c%victim% §e做人",
        "%killer% §e击败了 §c%victim%",
        "%killer% §e终结了 §c%victim%",
        "%killer% §e让 §c%victim% §e见识到了实力",
        "%killer% §e碾压了 §c%victim%",
        "§c%victim% §e被 %killer% §e淘汰",
        "%killer% §e成功狙击 §c%victim%",
        "§c%victim% §e不敌 %killer%"
    };
    
    public RewardManager(JavaPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }
    
    @EventHandler
    public void onPlayerKill(PlayerDeathEvent event) {
        if (!gameManager.isRunning()) return;
        
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        if (killer != null && killer != victim) {
            // 给予击杀奖励
            giveKillReward(killer);
            
            // 花样死亡消息
            broadcastDeathMessage(killer, victim);
        } else {
            // 非PVP死亡
            Bukkit.broadcast(Component.text("§c" + victim.getName() + " §7阵亡了"));
        }
    }
    
    /**
     * 给予击杀奖励
     */
    private void giveKillReward(Player killer) {
        // 基础回血
        double newHealth = Math.min(20.0, killer.getHealth() + KILL_HEAL_AMOUNT);
        killer.setHealth(newHealth);
        
        // 物品奖励池
        List<Material> rewardPool = Arrays.asList(
            Material.GOLDEN_APPLE,
            Material.ENDER_PEARL,
            Material.ARROW,
            Material.COOKED_BEEF,
            Material.GOLDEN_CARROT,
            Material.SHIELD,
            Material.EXPERIENCE_BOTTLE
        );
        
        // 随机给予物品
        for (int i = 0; i < KILL_REWARD_ITEMS; i++) {
            Material reward = rewardPool.get(random.nextInt(rewardPool.size()));
            killer.getInventory().addItem(new ItemStack(reward, 1));
        }
        
        // 音效和粒子
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        killer.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, killer.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        
        killer.sendActionBar(Component.text("§a击杀奖励：回血 + 随机物品"));
    }
    
    /**
     * 播报花样死亡消息
     */
    private void broadcastDeathMessage(Player killer, Player victim) {
        // 随机选择死亡消息
        String message = DEATH_MESSAGES[random.nextInt(DEATH_MESSAGES.length)];
        message = message.replace("%killer%", "§6" + killer.getName())
                         .replace("%victim%", victim.getName());
        Bukkit.broadcast(Component.text(message));
    }
    
    /**
     * 清除数据（游戏结束时调用，保持接口兼容）
     */
    public void clearKillStreaks() {
        // 无需清除，保持方法存在以保持兼容性
    }
}

