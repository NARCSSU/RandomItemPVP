package com.example.randomitempvp;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.Map;
import java.util.HashMap;

/**
 * 空投系统 - 定时掉落稀有装备箱
 */
public class AirdropManager implements Listener {
    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final Random random = new Random();
    private ScheduledTask airdropTask;
    private final Set<Location> airdropChests = new HashSet<>();
    private final Map<Location, Material> originalBlocks = new HashMap<>(); // 保存被信标替换的原始方块
    
    // 空投配置（快节奏）
    private static final long AIRDROP_INTERVAL_TICKS = 800L; // 40秒一次空投（加快）
    private static final long FIRST_AIRDROP_DELAY = 400L; // 首次空投延迟20秒（提前）
    
    public AirdropManager(JavaPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }
    
    /**
     * 启动空投系统
     */
    public void startAirdrop(Location centerLocation) {
        if (airdropTask != null) {
            airdropTask.cancel();
        }
        
        airdropTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (!gameManager.isRunning()) {
                task.cancel();
                return;
            }
            dropAirdrop(centerLocation);
        }, FIRST_AIRDROP_DELAY, AIRDROP_INTERVAL_TICKS);
    }
    
    /**
     * 停止空投
     */
    public void stopAirdrop() {
        if (airdropTask != null) {
            airdropTask.cancel();
            airdropTask = null;
        }
        clearAirdropChests();
    }
    
    /**
     * 投放空投
     */
    private void dropAirdrop(Location center) {
        World world = center.getWorld();
        
        // 获取当前边界大小，确保空投掉落在边界内
        WorldBorder border = world.getWorldBorder();
        double currentRadius = border.getSize() / 2.0;
        
        // 空投范围为当前边界的 70%（留出安全边距）
        int maxOffset = (int) (currentRadius * 0.7);
        if (maxOffset < 10) maxOffset = 10; // 最小范围 10 格
        
        // 随机位置（在边界安全范围内）
        int range = maxOffset * 2;
        int offsetX = random.nextInt(range) - maxOffset;
        int offsetZ = random.nextInt(range) - maxOffset;
        
        // 方块操作必须在区域调度器中执行（Folia 要求）
        Location checkLoc = new Location(world, center.getBlockX() + offsetX, 64, center.getBlockZ() + offsetZ);
        Bukkit.getRegionScheduler().run(plugin, checkLoc, task -> {
            int y = world.getHighestBlockYAt(center.getBlockX() + offsetX, center.getBlockZ() + offsetZ) + 1;
            Location dropLoc = new Location(world, center.getBlockX() + offsetX, y, center.getBlockZ() + offsetZ);
            
            // 全服播报
            Bukkit.broadcast(Component.text("§6§l【空投来袭】§e稀有装备箱即将降落！坐标：§b" + 
                dropLoc.getBlockX() + ", " + dropLoc.getBlockY() + ", " + dropLoc.getBlockZ()));
            
            // 音效提示
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.8f);
            }
            
            // 延迟3秒后投放（给玩家反应时间）
            Bukkit.getRegionScheduler().runDelayed(plugin, dropLoc, spawnTask -> {
                spawnAirdropChest(dropLoc);
            }, 60L);
        });
    }
    
    /**
     * 生成空投箱
     */
    private void spawnAirdropChest(Location location) {
        World world = location.getWorld();
        
        // 在空中显示信标光束效果
        Location beaconBase = location.clone().subtract(0, 1, 0);
        
        // 保存信标底座位置的原始方块
        Material originalBlock = beaconBase.getBlock().getType();
        
        // 如果原始方块是空气或非固体方块，保存为草方块（避免留下洞）
        if (!originalBlock.isSolid() || originalBlock == Material.AIR) {
            originalBlock = Material.GRASS_BLOCK;
        }
        
        originalBlocks.put(beaconBase, originalBlock);
        
        beaconBase.getBlock().setType(Material.BEACON);
        location.getBlock().setType(Material.CHEST);
        
        // 记录箱子位置
        airdropChests.add(location);
        
        // 填充宝箱
        Chest chest = (Chest) location.getBlock().getState();
        fillAirdropChest(chest.getInventory());
        
        // 粒子效果
        world.spawnParticle(Particle.FIREWORK, location.clone().add(0.5, 1, 0.5), 100, 0.5, 2, 0.5, 0.1);
        world.spawnParticle(Particle.END_ROD, location.clone().add(0.5, 1, 0.5), 50, 0.5, 2, 0.5, 0.05);
        
        // 音效
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2.0f, 1.0f);
        world.playSound(location, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);
        
        // 播报
        Bukkit.broadcast(Component.text("§a§l【空投已送达】§e快去抢夺稀有装备！"));
    }
    
    /**
     * 填充空投箱内容
     */
    private void fillAirdropChest(Inventory inv) {
        // 稀有物品池
        List<ItemStack> rareItems = Arrays.asList(
            new ItemStack(Material.DIAMOND_SWORD, 1),
            new ItemStack(Material.DIAMOND_AXE, 1),
            new ItemStack(Material.BOW, 1),
            new ItemStack(Material.CROSSBOW, 1),
            new ItemStack(Material.DIAMOND_HELMET, 1),
            new ItemStack(Material.DIAMOND_CHESTPLATE, 1),
            new ItemStack(Material.DIAMOND_LEGGINGS, 1),
            new ItemStack(Material.DIAMOND_BOOTS, 1),
            new ItemStack(Material.SHIELD, 1),
            new ItemStack(Material.TOTEM_OF_UNDYING, 1),
            new ItemStack(Material.GOLDEN_APPLE, 3),
            new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1),
            new ItemStack(Material.ENDER_PEARL, 3),
            new ItemStack(Material.TNT, 5),
            new ItemStack(Material.END_CRYSTAL, 2),
            new ItemStack(Material.FIRE_CHARGE, 8),
            new ItemStack(Material.ARROW, 32),
            new ItemStack(Material.SPECTRAL_ARROW, 16),
            new ItemStack(Material.NETHERITE_INGOT, 1)
        );
        
        // 随机选择5-8个物品
        int itemCount = 5 + random.nextInt(4);
        for (int i = 0; i < itemCount; i++) {
            ItemStack item = rareItems.get(random.nextInt(rareItems.size())).clone();
            inv.addItem(item);
        }
        
        // 必定包含一个特殊物品
        ItemStack guaranteed = Arrays.asList(
            new ItemStack(Material.TOTEM_OF_UNDYING, 1),
            new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 2),
            new ItemStack(Material.NETHERITE_INGOT, 1)
        ).get(random.nextInt(3));
        inv.addItem(guaranteed);
    }
    
    /**
     * 清除所有空投箱
     */
    private void clearAirdropChests() {
        // 方块操作必须在区域调度器中执行（Folia 要求）
        // 但如果插件已禁用（服务器关闭），则直接同步执行
        for (Location loc : airdropChests) {
            if (plugin.isEnabled()) {
                // 插件运行中，使用区域调度器（线程安全）
                Bukkit.getRegionScheduler().run(plugin, loc, task -> {
                    if (loc.getBlock().getType() == Material.CHEST) {
                        loc.getBlock().setType(Material.AIR);
                    }
                    // 恢复信标底座的原始方块
                    Location below = loc.clone().subtract(0, 1, 0);
                    if (below.getBlock().getType() == Material.BEACON) {
                        Material original = originalBlocks.getOrDefault(below, Material.AIR);
                        below.getBlock().setType(original);
                    }
                });
            } else {
                // 插件已禁用（服务器关闭），直接同步执行
                if (loc.getBlock().getType() == Material.CHEST) {
                    loc.getBlock().setType(Material.AIR);
                }
                // 恢复信标底座的原始方块
                Location below = loc.clone().subtract(0, 1, 0);
                if (below.getBlock().getType() == Material.BEACON) {
                    Material original = originalBlocks.getOrDefault(below, Material.AIR);
                    below.getBlock().setType(original);
                }
            }
        }
        airdropChests.clear();
        originalBlocks.clear(); // 清除原始方块记录
    }
    
    @EventHandler
    public void onChestOpen(InventoryOpenEvent event) {
        if (!gameManager.isRunning()) return;
        
        if (event.getInventory().getHolder() instanceof Chest) {
            Chest chest = (Chest) event.getInventory().getHolder();
            Location chestLoc = chest.getLocation();
            
            // 检查是否是空投箱
            if (airdropChests.contains(chestLoc)) {
                Player player = (Player) event.getPlayer();
                
                // 给所有玩家显示标题提示
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle("§6§l空投被打开！", "§e" + player.getName() + " §7获得了稀有装备", 5, 40, 10);
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                    p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
                }
                
                // 聊天消息播报
                Bukkit.broadcast(Component.text("§6§l【空投】§e" + player.getName() + " §7打开了空投箱并获得稀有装备！"));
                
                // 特效
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, chestLoc.clone().add(0.5, 1, 0.5), 30, 0.5, 0.5, 0.5, 0.1);
                player.getWorld().spawnParticle(Particle.FIREWORK, chestLoc.clone().add(0.5, 1, 0.5), 20, 0.3, 0.3, 0.3, 0.05);
                
                // 给打开玩家特别的音效
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }
    }
}

