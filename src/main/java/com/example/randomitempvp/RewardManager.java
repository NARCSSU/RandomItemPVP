package com.example.randomitempvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * 管理击杀奖励、连杀系统、空投等游戏乐趣功能
 */
public class RewardManager implements Listener {
    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final Random random = new Random();
    
    // 连杀追踪
    private final Map<UUID, Integer> killStreaks = new HashMap<>();
    // 连续死亡追踪（弱者保护）
    private final Map<UUID, Integer> deathStreaks = new HashMap<>();
    
    // 击杀奖励配置（降低基础奖励）
    private static final double KILL_HEAL_AMOUNT = 4.0; // 击杀回血2颗心（降低）
    private static final int KILL_REWARD_ITEMS = 2; // 击杀奖励2个随机物品（降低）
    
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
            // 计算赏金（根据受害者连杀数）
            int victimStreak = killStreaks.getOrDefault(victim.getUniqueId(), 0);
            int bountyMultiplier = Math.max(1, victimStreak / 2); // 每2连杀增加1倍奖励
            
            // 击杀奖励（赏金系统）
            giveKillReward(killer, bountyMultiplier);
            
            // 更新连杀和死亡连击
            updateKillStreak(killer);
            updateDeathStreak(victim);
            
            // 弱者保护（给受害者补偿）
            giveVictimCompensation(victim);
            
            // 花样死亡消息
            broadcastDeathMessage(killer, victim, victimStreak);
        } else {
            // 非PVP死亡
            updateDeathStreak(victim);
            Bukkit.broadcast(Component.text("§c" + victim.getName() + " §7阵亡了"));
        }
    }
    
    /**
     * 给予击杀奖励（赏金系统）
     */
    private void giveKillReward(Player killer, int bountyMultiplier) {
        // 基础回血（降低）
        double healAmount = KILL_HEAL_AMOUNT * Math.min(bountyMultiplier, 3); // 最多3倍
        double newHealth = Math.min(20.0, killer.getHealth() + healAmount);
        killer.setHealth(newHealth);
        
        // 赏金奖励提示
        if (bountyMultiplier > 1) {
            killer.sendMessage("§6§l【赏金猎人】§e击杀高连杀玩家！奖励 x" + bountyMultiplier);
            killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        }
        
        // 物品奖励（根据赏金倍数）
        List<Material> rewardPool = Arrays.asList(
            Material.GOLDEN_APPLE,
            Material.ENDER_PEARL,
            Material.ARROW,
            Material.COOKED_BEEF,
            Material.GOLDEN_CARROT
        );
        
        int itemCount = KILL_REWARD_ITEMS * Math.min(bountyMultiplier, 2); // 最多2倍
        for (int i = 0; i < itemCount; i++) {
            Material reward = rewardPool.get(random.nextInt(rewardPool.size()));
            killer.getInventory().addItem(new ItemStack(reward, 1));
        }
        
        // 移除增益效果随机性，只给赏金猎人
        if (bountyMultiplier >= 2) {
            PotionEffectType buff = PotionEffectType.SPEED;
            killer.addPotionEffect(new PotionEffect(buff, 100, 0)); // 5秒
        }
        
        // 音效和粒子
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        killer.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, killer.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        
        String rewardText = bountyMultiplier > 1 ? "§6赏金 x" + bountyMultiplier : "§e基础奖励";
        killer.sendActionBar(Component.text("§a击杀奖励：" + rewardText));
    }
    
    /**
     * 更新连杀数
     */
    private void updateKillStreak(Player killer) {
        UUID killerId = killer.getUniqueId();
        int streak = killStreaks.getOrDefault(killerId, 0) + 1;
        killStreaks.put(killerId, streak);
        
        // 清除死亡连击（翻身）
        deathStreaks.remove(killerId);
        
        // 连杀称号和奖励（大幅削弱）
        if (streak == 3) {
            broadcastStreak(killer, streak, "§e三连杀", "§6Killing Spree!");
            // 不给物品，只给称号
        } else if (streak == 5) {
            broadcastStreak(killer, streak, "§6五连杀", "§6Rampage!");
            giveStreakBonus(killer, Material.GOLDEN_APPLE, 1); // 降低奖励
        } else if (streak == 7) {
            broadcastStreak(killer, streak, "§c七连杀", "§cDominating!");
            // 成为全服目标，显示位置
            applyBountyDebuff(killer);
        } else if (streak >= 10) {
            broadcastStreak(killer, streak, "§4§l无人能挡", "§4§lUNSTOPPABLE!");
            // 高连杀惩罚：发光效果
            applyHighStreakDebuff(killer);
        }
    }
    
    /**
     * 更新死亡连击（弱者保护）
     */
    private void updateDeathStreak(Player victim) {
        UUID victimId = victim.getUniqueId();
        int deathCount = deathStreaks.getOrDefault(victimId, 0) + 1;
        deathStreaks.put(victimId, deathCount);
        
        // 清除击杀连击
        killStreaks.remove(victimId);
    }
    
    /**
     * 给予受害者补偿（弱者保护）
     */
    private void giveVictimCompensation(Player victim) {
        int deaths = deathStreaks.getOrDefault(victim.getUniqueId(), 0);
        
        // 连续死亡补偿
        if (deaths >= 3) {
            victim.sendMessage("§a§l【弱者保护】§e连续阵亡补偿：金苹果 + 抗性提升");
            victim.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 2));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 600, 0)); // 30秒抗性
            victim.playSound(victim.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
        }
        
        if (deaths >= 5) {
            victim.sendMessage("§6§l【复仇之心】§e获得额外装备！");
            victim.getInventory().addItem(new ItemStack(Material.SHIELD, 1));
            victim.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 2));
        }
    }
    
    /**
     * 施加赏金Debuff（7连杀惩罚）
     */
    private void applyBountyDebuff(Player player) {
        Bukkit.broadcast(Component.text("§c§l【赏金目标】§6" + player.getName() + " §e已成为全服公敌！击杀可获得丰厚奖励！"));
        player.sendMessage("§c你已成为赏金目标！所有人都在狙击你！");
        
        // 发光效果，让所有人看到
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 600, 0)); // 30秒发光
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
    }
    
    /**
     * 施加高连杀Debuff（10连杀惩罚）
     */
    private void applyHighStreakDebuff(Player player) {
        player.sendMessage("§4§l你的强大引起了众怒！");
        player.sendMessage("§c效果：发光 + 缓慢 I");
        
        // 持续发光 + 轻微缓慢
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 99999, 0)); // 一直发光
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 400, 0)); // 20秒缓慢
    }
    
    /**
     * 播报连杀
     */
    private void broadcastStreak(Player killer, int streak, String title, String subtitle) {
        // 全服播报
        Bukkit.broadcast(Component.text("§6§l【连杀】§r" + killer.getName() + " §e已经 " + title + " §7(" + streak + " 连杀)"));
        
        // 给击杀者显示标题
        killer.showTitle(Title.title(
            Component.text(title),
            Component.text(subtitle)
        ));
        
        // 全服音效
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (streak >= 7) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            } else {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
            }
        }
    }
    
    /**
     * 给予连杀奖励
     */
    private void giveStreakBonus(Player player, Material item, int amount) {
        player.getInventory().addItem(new ItemStack(item, amount));
        player.sendMessage("§a连杀奖励：§6" + getItemName(item) + " x" + amount);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 2, 0), 50, 0.5, 0.5, 0.5, 0.1);
    }
    
    /**
     * 播报花样死亡消息
     */
    private void broadcastDeathMessage(Player killer, Player victim, int victimStreak) {
        // 终结连杀（给予终结者丰厚奖励）
        if (victimStreak >= 5) {
            Bukkit.broadcast(Component.text("§6§l【连杀终结】§c" + killer.getName() + " §e终结了 §6" + victim.getName() + " §e的 §c" + victimStreak + " §e连杀！"));
            
            // 终结奖励
            if (victimStreak >= 7) {
                killer.getInventory().addItem(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
                killer.sendMessage("§a终结高连杀玩家！奖励：§6附魔金苹果");
            } else if (victimStreak >= 5) {
                killer.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 3));
                killer.sendMessage("§a终结连杀！奖励：§6金苹果 x3");
            }
            
            // 全服音效
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        } else {
            // 普通击杀消息
            String message = DEATH_MESSAGES[random.nextInt(DEATH_MESSAGES.length)];
            message = message.replace("%killer%", "§6" + killer.getName())
                             .replace("%victim%", victim.getName());
            Bukkit.broadcast(Component.text(message));
        }
    }
    
    /**
     * 清除连杀记录
     */
    public void clearKillStreaks() {
        killStreaks.clear();
        deathStreaks.clear();
    }
    
    /**
     * 获取药水效果名称
     */
    private String getEffectName(PotionEffectType effect) {
        if (effect.equals(PotionEffectType.SPEED)) return "速度";
        if (effect.equals(PotionEffectType.STRENGTH)) return "力量";
        if (effect.equals(PotionEffectType.REGENERATION)) return "再生";
        if (effect.equals(PotionEffectType.RESISTANCE)) return "抗性";
        return effect.getName();
    }
    
    /**
     * 获取物品名称
     */
    private String getItemName(Material material) {
        switch (material) {
            case GOLDEN_APPLE: return "金苹果";
            case ENCHANTED_GOLDEN_APPLE: return "附魔金苹果";
            case TOTEM_OF_UNDYING: return "不死图腾";
            case NETHERITE_INGOT: return "下界合金锭";
            default: return material.name();
        }
    }
}

