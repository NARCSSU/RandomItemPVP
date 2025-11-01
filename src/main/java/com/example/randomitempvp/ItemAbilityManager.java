package com.example.randomitempvp;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 管理特殊物品能力（TNT投掷、末影水晶放置等）
 */
public class ItemAbilityManager implements Listener {
    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final Map<UUID, Long> tntCooldowns = new HashMap<>();
    private final Map<UUID, Long> fireballCooldowns = new HashMap<>();
    
    // 平衡性参数
    private static final long TNT_COOLDOWN_TICKS = 100L; // 5秒冷却（20 ticks = 1秒）
    private static final int TNT_FUSE_TICKS = 50; // 2.5秒引爆时间
    private static final float TNT_THROW_POWER = 0.8f; // 投掷力度（降低射程，更平衡）
    private static final float TNT_EXPLOSION_POWER = 3.0f; // 爆炸威力（默认4.0，略微降低以平衡）
    private static final float CRYSTAL_EXPLOSION_POWER = 4.0f; // 末影水晶爆炸威力（默认6.0，降低以平衡）
    private static final float FIREBALL_THROW_POWER = 1.5f; // 火焰弹投掷力度（比TNT稍快）
    private static final long FIREBALL_COOLDOWN_MS = 2000L; // 火焰弹冷却时间（2秒，比TNT短）
    
    public ItemAbilityManager(JavaPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!gameManager.isRunning()) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || item.getType() == Material.AIR) return;
        
        // 处理 TNT 右键投掷
        if (item.getType() == Material.TNT && 
            (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            
            event.setCancelled(true);
            
            // 检查冷却时间
            UUID playerId = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            
            if (tntCooldowns.containsKey(playerId)) {
                long cooldownEnd = tntCooldowns.get(playerId);
                if (currentTime < cooldownEnd) {
                    long remainingSeconds = (cooldownEnd - currentTime) / 1000;
                    player.sendActionBar(Component.text("§cTNT 冷却中... " + remainingSeconds + "秒"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                    return;
                }
            }
            
            // 投掷 TNT
            throwTNT(player);
            
            // 消耗物品
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
            
            // 设置冷却时间
            tntCooldowns.put(playerId, currentTime + (TNT_COOLDOWN_TICKS * 50)); // 50ms per tick
            
            player.sendActionBar(Component.text("§e已投掷 TNT！冷却时间：5秒"));
            player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
        }
        
        // 处理末影水晶放置（允许放在任何固体方块上）
        if (item.getType() == Material.END_CRYSTAL && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && clickedBlock.getType().isSolid()) {
                event.setCancelled(true);
                
                // 获取放置位置（点击方块的上方）
                Location crystalLoc = clickedBlock.getLocation().add(0.5, 1, 0.5);
                World world = clickedBlock.getWorld();
                
                // 使用区域调度器生成末影水晶实体（Folia 要求）
                Bukkit.getRegionScheduler().run(plugin, crystalLoc, task -> {
                    EnderCrystal crystal = (EnderCrystal) world.spawnEntity(crystalLoc, EntityType.END_CRYSTAL);
                    crystal.setShowingBottom(false); // 不显示底座
                });
                
                // 消耗物品
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                }
                
                // 提示和音效
                player.sendActionBar(Component.text("§d末影水晶已放置！小心爆炸！"));
                player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f, 1.2f);
            }
        }
        
        // 处理火焰弹右键投掷
        if (item.getType() == Material.FIRE_CHARGE && 
            (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            
            event.setCancelled(true);
            
            // 检查冷却时间
            UUID playerId = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            
            if (fireballCooldowns.containsKey(playerId)) {
                long cooldownEnd = fireballCooldowns.get(playerId);
                if (currentTime < cooldownEnd) {
                    long remainingSeconds = (cooldownEnd - currentTime) / 1000;
                    player.sendActionBar(Component.text("§c火焰弹冷却中... " + remainingSeconds + "秒"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                    return;
                }
            }
            
            // 投掷火焰弹
            throwFireball(player);
            
            // 消耗物品
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
            
            // 设置冷却时间
            fireballCooldowns.put(playerId, currentTime + FIREBALL_COOLDOWN_MS);
            
            player.sendActionBar(Component.text("§6已投掷火焰弹！冷却时间：2秒"));
            player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
        }
    }
    
    // BlockPlaceEvent 不再需要，因为末影水晶已在 PlayerInteractEvent 中手动处理
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onCrystalDamage(EntityDamageByEntityEvent event) {
        if (!gameManager.isRunning()) return;
        
        // 检测末影水晶被攻击
        if (event.getEntity() instanceof EnderCrystal) {
            EnderCrystal crystal = (EnderCrystal) event.getEntity();
            
            // 取消原版爆炸事件，使用自定义爆炸
            event.setCancelled(true);
            
            Location loc = crystal.getLocation();
            World world = crystal.getWorld();
            
            // 创建自定义爆炸（降低威力以平衡游戏）
            world.createExplosion(loc, CRYSTAL_EXPLOSION_POWER, false, true);
            
            // 移除水晶实体
            crystal.remove();
            
            // 视觉和音效
            world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
            
            // 给附近玩家提示
            if (event.getDamager() instanceof Player) {
                Player damager = (Player) event.getDamager();
                damager.sendActionBar(Component.text("§c末影水晶爆炸！威力：" + CRYSTAL_EXPLOSION_POWER));
            }
        }
    }
    
    /**
     * 投掷 TNT
     */
    private void throwTNT(Player player) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();
        
        // 在玩家眼前生成点燃的 TNT
        Location spawnLoc = eyeLoc.add(direction.clone().multiply(0.5));
        TNTPrimed tnt = player.getWorld().spawn(spawnLoc, TNTPrimed.class);
        
        // 设置 TNT 属性
        tnt.setFuseTicks(TNT_FUSE_TICKS);
        tnt.setYield(TNT_EXPLOSION_POWER);
        tnt.setSource(player);
        
        // 给 TNT 一个抛物线速度
        Vector velocity = direction.multiply(TNT_THROW_POWER);
        tnt.setVelocity(velocity);
        
        // 视觉效果
        player.getWorld().spawnParticle(Particle.FLAME, spawnLoc, 10, 0.1, 0.1, 0.1, 0.02);
    }
    
    /**
     * 投掷火焰弹
     */
    private void throwFireball(Player player) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();
        
        // 在玩家眼前生成小型火球
        Location spawnLoc = eyeLoc.add(direction.clone().multiply(0.5));
        SmallFireball fireball = player.getWorld().spawn(spawnLoc, SmallFireball.class);
        
        // 设置火球属性
        fireball.setShooter(player);
        fireball.setIsIncendiary(true); // 点燃方块
        
        // 给火球一个速度
        Vector velocity = direction.multiply(FIREBALL_THROW_POWER);
        fireball.setVelocity(velocity);
        
        // 视觉效果
        player.getWorld().spawnParticle(Particle.FLAME, spawnLoc, 15, 0.1, 0.1, 0.1, 0.05);
        player.getWorld().spawnParticle(Particle.LAVA, spawnLoc, 5, 0.1, 0.1, 0.1, 0.0);
    }
    
    /**
     * 监听雪球命中事件 - 冰冻爆破效果
     */
    @EventHandler
    public void onSnowballHit(ProjectileHitEvent event) {
        if (!gameManager.isRunning()) return;
        
        // 检查是否是雪球
        if (!(event.getEntity() instanceof Snowball)) return;
        
        Snowball snowball = (Snowball) event.getEntity();
        
        // 检查投掷者是否是玩家
        if (!(snowball.getShooter() instanceof Player)) return;
        
        Player thrower = (Player) snowball.getShooter();
        Location hitLocation = snowball.getLocation();
        World world = hitLocation.getWorld();
        
        // 冰冻效果范围：3格半径
        double radius = 3.0;
        
        // 粒子效果 - 冰冻爆炸
        world.spawnParticle(Particle.SNOWFLAKE, hitLocation, 50, radius, radius, radius, 0.1);
        world.spawnParticle(Particle.CLOUD, hitLocation, 30, radius, 0.5, radius, 0.05);
        world.spawnParticle(Particle.ITEM_SNOWBALL, hitLocation, 40, radius, radius, radius, 0.1);
        
        // 音效 - 冰冻音效
        world.playSound(hitLocation, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
        world.playSound(hitLocation, Sound.BLOCK_SNOW_BREAK, 1.5f, 0.8f);
        
        // 给范围内的所有玩家添加缓慢效果
        int affected = 0;
        for (Player nearbyPlayer : world.getPlayers()) {
            if (nearbyPlayer.getLocation().distance(hitLocation) <= radius) {
                // 给予缓慢 II 效果，持续 5 秒
                nearbyPlayer.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS,
                    100, // 5秒（100 ticks）
                    1, // 缓慢 II
                    false,
                    true
                ));
                
                // 额外的冰冻视觉效果
                nearbyPlayer.getWorld().spawnParticle(
                    Particle.SNOWFLAKE, 
                    nearbyPlayer.getLocation().add(0, 1, 0), 
                    20, 0.3, 0.5, 0.3, 0.05
                );
                
                // 给被冰冻的玩家播放音效
                nearbyPlayer.playSound(nearbyPlayer.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 1.0f);
                
                affected++;
            }
        }
        
        // 给投掷者反馈
        if (affected > 0) {
            thrower.sendActionBar(Component.text("§b❄ 冰冻爆破！影响 " + affected + " 名玩家"));
            thrower.playSound(thrower.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        }
    }
    
    /**
     * 清除玩家的冷却时间（游戏结束时调用）
     */
    public void clearCooldowns() {
        tntCooldowns.clear();
        fireballCooldowns.clear();
    }
    
    /**
     * Component 辅助类（兼容旧版本）
     */
    private static class Component {
        static net.kyori.adventure.text.Component text(String text) {
            return net.kyori.adventure.text.Component.text(text);
        }
    }
}

