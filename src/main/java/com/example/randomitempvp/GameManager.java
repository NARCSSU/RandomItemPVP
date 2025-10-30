package com.example.randomitempvp;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.WorldBorder;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GameManager implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private boolean gameRunning = false;
    private boolean preparing = false; // 准备阶段（倒计时中）
    private boolean eventTriggered = false;
    private final Random random = new Random();
    private ScheduledTask itemTask;
    private ScheduledTask eventTask;
    private ScheduledTask borderShrinkTask;
    private ScheduledTask countdownTask; // 倒计时任务（用于取消）
    private Location spawnLocation;
    private WorldBorder gameBorder;
    private final List<Location> bedrockPillars = new ArrayList<>();
    private final List<Location> placedBlocks = new ArrayList<>();
    private final List<Entity> spawnedMobs = new ArrayList<>();
    private final Map<Player, GameMode> playerGameModes = new HashMap<>();
    private ScheduledTask aliveCountTask;
    private final Set<Player> participants = new HashSet<>(); // 参与者列表
    private final Map<Player, Location> playerOriginalLocations = new HashMap<>(); // 记录玩家原始位置
    private Location gatherLocation = null; // 集合点位置
    private int lastAliveCount = -1; // 记录上一次的存活人数，用于防止消息刷屏

    public GameManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        if (!Bukkit.getWorlds().isEmpty()) {
            spawnLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
    }

    public boolean isRunning() { return gameRunning; }
    public boolean isPreparing() { return preparing; }
    public int getAliveCount() { return getSurvivingPlayers().size(); }
    public void setSpawnLocation(Location location) { this.spawnLocation = location; }
    public int getParticipantCount() { return participants.size(); }
    public Set<Player> getParticipants() { return new HashSet<>(participants); }
    
    // 玩家加入游戏
    public boolean joinGame(Player player) {
        if (gameRunning) return false;
        if (!participants.add(player)) return false; // 已经在列表中
        
        // 记录原始位置
        playerOriginalLocations.put(player, player.getLocation().clone());
        
        // 传送到集合点
        if (gatherLocation != null) {
            player.teleportAsync(gatherLocation).thenRun(() -> {
                player.sendMessage("§a已传送到集合点！");
            });
        }
        
        return true;
    }
    
    // 玩家离开游戏
    public boolean leaveGame(Player player) {
        if (gameRunning) return false;
        if (!participants.remove(player)) return false; // 不在列表中
        
        // 传送回原位置
        Location originalLoc = playerOriginalLocations.remove(player);
        if (originalLoc != null) {
            player.teleportAsync(originalLoc).thenRun(() -> {
                player.sendMessage("§a已传送回原位置！");
            });
        }
        
        return true;
    }
    
    // 取消游戏准备
    public void cancelGame() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        preparing = false;
        
        // 将所有参与者传送回原位置
        for (Player player : new ArrayList<>(participants)) {
            Location originalLoc = playerOriginalLocations.remove(player);
            if (originalLoc != null && player.isOnline()) {
                player.teleportAsync(originalLoc).thenRun(() -> {
                    player.sendMessage("§c游戏已取消，已传送回原位置！");
                });
            }
        }
        
        participants.clear();
        gatherLocation = null;
        Bukkit.broadcastMessage("§c游戏已取消！");
    }

    public void startGame(List<Player> participants) {
        if (gameRunning) return;
        startRound();
    }
    
    /**
     * 带倒计时启动游戏
     */
    public void startGameWithCountdown(List<Player> initialParticipants) {
        if (gameRunning || preparing) return;
        
        // 检查是否设置了出生点
        if (spawnLocation == null) {
            Bukkit.broadcastMessage("§c错误：未设置游戏出生点！请使用 /ripvp setspawn 设置");
            return;
        }
        
        // 设置准备阶段
        preparing = true;
        participants.clear();
        playerOriginalLocations.clear();
        
        // 设置集合点（使用 /ripvp setspawn 设置的坐标）
        gatherLocation = spawnLocation.clone();
        
        // 记录并传送所有初始参与者
        for (Player player : initialParticipants) {
            // 记录原始位置
            playerOriginalLocations.put(player, player.getLocation().clone());
            participants.add(player);
            
            // 传送到集合点
            player.teleportAsync(gatherLocation).thenRun(() -> {
                player.sendMessage("§a已传送到集合点！");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            });
        }
        
        Bukkit.broadcastMessage("§a游戏准备中！参与者：§6" + participants.size() + " §a人");
        Bukkit.broadcastMessage("§7使用 /ripvp join 加入或 /ripvp leave 退出");
        
        int countdown = config.getStartCountdown();
        final int[] currentCount = {countdown}; // 使用数组以便在 lambda 中修改
        
        // 使用定时任务进行倒计时
        countdownTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (!preparing) {
                // 游戏被取消
                task.cancel();
                return;
            }
            
            int remaining = currentCount[0];
            
            if (remaining <= 0) {
                // 倒计时结束，启动游戏
                task.cancel();
                preparing = false;
                
                // 检查参与者数量
                int minPlayers = config.getMinPlayers();
                if (participants.size() < minPlayers) {
                    Bukkit.broadcastMessage("§c参与者不足！游戏取消。需要至少 " + minPlayers + " 人");
                    participants.clear();
                    return;
                }
                
                // 开始游戏
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle("§a§l开始！", "§6祝你好运！", 0, 40, 10);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                }
                startRound();
                return;
            }
            
            // 显示倒计时
            if (remaining <= 3) {
                // 最后3秒特殊提示
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle("§6§l" + remaining, "§e游戏即将开始", 0, 20, 10);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                }
            } else {
                Bukkit.broadcastMessage("§e游戏将在 §6" + remaining + " §e秒后开始... (参与者：§6" + participants.size() + "§e人)");
            }
            
            currentCount[0]--;
        }, 1, 20L); // 延迟1 tick，每20 ticks（1秒）执行一次
    }

    public void stopGame(boolean sendMessage) {
        gameRunning = false;
        cancelAllTasks();
        
        // 方块操作必须在区域调度器中执行（Folia 要求）
        // 但如果插件已禁用（服务器关闭），则直接同步执行
        if (spawnLocation != null) {
            if (plugin.isEnabled()) {
                // 插件运行中，使用区域调度器（线程安全）
                Bukkit.getRegionScheduler().run(plugin, spawnLocation, task -> {
                    destroyArena();
                });
            } else {
                // 插件已禁用（服务器关闭），直接同步执行
                destroyArena();
            }
        }
        
        clearArenaEntities();
        
        // 重置边界到超大值（移除边界限制）
        if (gameBorder != null && spawnLocation != null) {
            gameBorder.setSize(59999968.0, 0); // 立即设置为最大边界值
            gameBorder.setDamageAmount(0.0); // 取消边界伤害
            gameBorder.setWarningDistance(0); // 取消警告距离
            
            if (sendMessage) {
                Bukkit.broadcastMessage("§a边界已重置。");
            }
        }
        
        // 清除特殊物品冷却时间
        ItemAbilityManager abilityManager = RandomItemPVP.getInstance().getItemAbilityManager();
        if (abilityManager != null) {
            abilityManager.clearCooldowns();
        }
        
        // 停止空投系统
        AirdropManager airdropManager = RandomItemPVP.getInstance().getAirdropManager();
        if (airdropManager != null) {
            airdropManager.stopAirdrop();
        }
        
        // 清除连杀记录
        RewardManager rewardManager = RandomItemPVP.getInstance().getRewardManager();
        if (rewardManager != null) {
            rewardManager.clearKillStreaks();
        }
        
        // 恢复所有参与者的游戏模式和位置
        for (Player player : new ArrayList<>(participants)) {
            player.getInventory().clear();
            
            // 恢复原始游戏模式
            GameMode originalMode = playerGameModes.getOrDefault(player, GameMode.SURVIVAL);
            player.setGameMode(originalMode);
            
            // 传送回原位置
            Location originalLoc = playerOriginalLocations.get(player);
            if (originalLoc != null) {
                player.teleportAsync(originalLoc).thenRun(() -> {
                    if (sendMessage) player.sendMessage("§c游戏已结束，已传送回原位置。");
                });
            } else if (spawnLocation != null) {
                player.teleportAsync(spawnLocation).thenRun(() -> {
                    if (sendMessage) player.sendMessage("§c游戏已结束。");
                });
            }
        }
        
        playerGameModes.clear();
        participants.clear(); // 清空参与者列表
        playerOriginalLocations.clear(); // 清空原始位置记录
        gatherLocation = null; // 清空集合点
        lastAliveCount = -1; // 重置存活人数记录
    }

    private void startRound() {
        gameRunning = true;
        eventTriggered = false;
        cancelAllTasks();
        
        // 方块操作必须在区域调度器中执行（Folia 要求）
        Bukkit.getRegionScheduler().run(plugin, spawnLocation, task -> {
            destroyArena();
            generateArena();
        });
        
        clearArenaEntities();
        setupWorldBorder();
        resetPlayers();
        startItemTask();
        startEventTask();
        startBorderShrink();
        
        // 启动空投系统
        AirdropManager airdropManager = RandomItemPVP.getInstance().getAirdropManager();
        if (airdropManager != null && spawnLocation != null) {
            airdropManager.startAirdrop(spawnLocation);
        }
        
        Bukkit.broadcastMessage("§a新一轮随机物品PVP开始！");
        Bukkit.broadcastMessage("§e击杀敌人可获得丰厚奖励！连杀有特殊称号！");
        Bukkit.broadcastMessage("§6空投系统已激活，稀有装备即将空降！");
        
        // 启动存活人数显示
        startAliveCountDisplay();
    }
    
    /**
     * 启动存活人数显示
     */
    private void startAliveCountDisplay() {
        if (aliveCountTask != null) {
            aliveCountTask.cancel();
        }
        
        lastAliveCount = -1; // 重置存活人数记录
        
        aliveCountTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (!gameRunning) {
                task.cancel();
                lastAliveCount = -1;
                return;
            }
            
            int alive = getSurvivingPlayers().size();
            int total = Bukkit.getOnlinePlayers().size();
            
            // 给所有玩家显示存活人数
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§a存活：§6" + alive + "§7/§e" + total + " §8| §c边界正在缩小"
                ));
            }
            
            // 只在存活人数变化时才广播消息（防止刷屏）
            if (alive != lastAliveCount) {
                // 最后5人提示
                if (alive == 5 && alive < total) {
                    Bukkit.broadcast(net.kyori.adventure.text.Component.text("§c§l【最后5人】§e决战时刻到来！"));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
                    }
                }
                
                // 最后3人提示
                if (alive == 3 && alive < total) {
                    Bukkit.broadcast(net.kyori.adventure.text.Component.text("§6§l【最后3人】§e谁能笑到最后？"));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                    }
                }
                
                // 最后2人提示
                if (alive == 2 && alive < total) {
                    Bukkit.broadcast(net.kyori.adventure.text.Component.text("§4§l【最后2人】§c巅峰对决！"));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
                    }
                }
                
                lastAliveCount = alive; // 更新存活人数记录
            }
        }, 1, 20L); // 每秒更新一次
    }

    private void cancelAllTasks() {
        if (itemTask != null) { itemTask.cancel(); itemTask = null; }
        if (eventTask != null) { eventTask.cancel(); eventTask = null; }
        if (borderShrinkTask != null) { borderShrinkTask.cancel(); borderShrinkTask = null; }
        if (aliveCountTask != null) { aliveCountTask.cancel(); aliveCountTask = null; }
    }

    private void setupWorldBorder() {
        if (spawnLocation == null) return;
        World world = spawnLocation.getWorld();
        gameBorder = world.getWorldBorder();
        gameBorder.setCenter(spawnLocation);
        // 立即设置边界大小（0秒过渡，避免从上一局的超大值慢慢过渡）
        gameBorder.setSize(config.getArenaRadius() * 2, 0);
        gameBorder.setDamageBuffer(0);
        gameBorder.setDamageAmount(config.getBorderDamageAmount());
        gameBorder.setWarningDistance(5);
        gameBorder.setWarningTime(10);
    }

    private void generateArena() {
        if (spawnLocation == null || gatherLocation == null) return;
        World world = spawnLocation.getWorld();
        
        // 获取参与者列表，围成一个圈
        List<Player> players = new ArrayList<>(participants);
        int playerCount = players.size();
        if (playerCount == 0) return;
        
        // 计算玩家间的角度间隔（围成一个圈）
        double angleStep = 2 * Math.PI / playerCount;
        int circleRadius = Math.min(20, config.getArenaRadius() / 2); // 圆圈半径（玩家之间的距离）
        int pillarHeight = 128; // 基岩柱子高度
        
        // 使用集合点的高度作为基准（所有人在同一位置，确保安全）
        int baseGroundY = gatherLocation.getBlockY();
        
        for (int i = 0; i < playerCount; i++) {
            Player player = players.get(i);
            
            // 计算该玩家的位置（围绕竞技场中心圆形分布）
            double angle = i * angleStep;
            int pillarX = spawnLocation.getBlockX() + (int)(Math.cos(angle) * circleRadius);
            int pillarZ = spawnLocation.getBlockZ() + (int)(Math.sin(angle) * circleRadius);
            
            // 使用固定高度，确保安全（避免区块未加载或虚空问题）
            int groundY = baseGroundY;
            
            // 先生成128格高的基岩柱子（从地面向上）
            for (int y = 0; y < pillarHeight; y++) {
                Location bedrockLoc = new Location(world, pillarX, groundY + y, pillarZ);
                bedrockLoc.getBlock().setType(Material.BEDROCK);
                bedrockPillars.add(bedrockLoc);
            }
            
            // 在柱子顶部生成一个3x3的平台，防止玩家掉下去
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Location platformLoc = new Location(world, pillarX + x, groundY + pillarHeight, pillarZ + z);
                    platformLoc.getBlock().setType(Material.WHITE_STAINED_GLASS);
                    bedrockPillars.add(platformLoc); // 记录以便清理
                }
            }
            
            // 传送玩家到柱子顶部的平台上（+1 是站在平台上方）
            Location playerSpawn = new Location(world, pillarX + 0.5, groundY + pillarHeight + 1, pillarZ + 0.5);
            
            // 先传送玩家
            player.teleportAsync(playerSpawn).thenRun(() -> {
                // 传送完成后，使用实体调度器给玩家添加药水效果（Folia 要求）
                player.getScheduler().run(plugin, task -> {
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOW_FALLING, 
                        100, // 5秒
                        0, 
                        false, 
                        false
                    ));
                }, null);
                
                player.sendMessage("§a你已加入游戏！站在 128 格高的基岩柱子上！");
                player.sendMessage("§7提示：跳下柱子时会有缓降效果");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            });
        }
    }

    private void destroyArena() {
        // 清除基岩柱子和平台（包括基岩和玻璃）
        for (Location loc : bedrockPillars) {
            if (loc.getWorld() != null) {
                Material type = loc.getBlock().getType();
                if (type == Material.BEDROCK || type == Material.WHITE_STAINED_GLASS) {
                    loc.getBlock().setType(Material.AIR);
                }
            }
        }
        bedrockPillars.clear();
        
        // 清除玩家放置的方块
        for (Location loc : placedBlocks) {
            if (loc.getWorld() != null) {
                loc.getBlock().setType(Material.AIR);
            }
        }
        placedBlocks.clear();
        
        // 注意：不在这里重置边界，因为 startRound() 会调用 setupWorldBorder() 重新设置
        // 只有在 stopGame() 中才需要将边界重置为超大值
    }

    private void clearArenaEntities() {
        if (spawnLocation == null) return;
        
        // 如果插件已禁用（服务器关闭），跳过实体清理
        // 服务器关闭时会自动清理所有实体，手动清理会导致错误
        if (!plugin.isEnabled()) {
            spawnedMobs.clear();
            return;
        }
        
        // 清除事件生成的怪物（使用实体调度器，Folia 要求）
        for (Entity mob : new ArrayList<>(spawnedMobs)) {
            if (mob != null && mob.isValid()) {
                // 必须在实体自己的线程上移除
                mob.getScheduler().run(plugin, task -> {
                    mob.remove();
                }, null);
            }
        }
        spawnedMobs.clear();
        
        // 使用区域调度器获取实体列表，然后用实体调度器移除
        World world = spawnLocation.getWorld();
        Bukkit.getRegionScheduler().run(plugin, spawnLocation, task -> {
            java.util.Collection<Entity> entities = world.getNearbyEntities(spawnLocation, 100, 100, 100);
            for (Entity entity : entities) {
                if (entity instanceof Player) continue;
                if (entity instanceof Item || entity instanceof Arrow || 
                    entity instanceof Monster || entity instanceof Flying) {
                    // 使用实体调度器在实体自己的线程上移除
                    entity.getScheduler().run(plugin, removeTask -> {
                        entity.remove();
                    }, null);
                }
            }
        });
    }

    private void resetPlayers() {
        for (Player player : participants) {
            // 保存原始游戏模式
            playerGameModes.put(player, player.getGameMode());
            
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.getInventory().clear();
            
            // 注意：不在这里传送玩家，传送由 generateArena() 负责
            // generateArena() 会将玩家传送到 128 格高的基岩柱子顶部
        }
    }

    private void startItemTask() {
        long intervalTicks = config.getItemInterval();
        itemTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (!gameRunning) {
                task.cancel();
                return;
            }
            List<Player> survivors = getSurvivingPlayers();
            if (survivors.isEmpty()) return;

            List<Material> validMaterials = Arrays.stream(Material.values())
                    .filter(Material::isItem)
                    .filter(m -> !config.getItemBlacklist().contains(m.name()))
                    .filter(this::isUsefulItem)
                    .collect(Collectors.toList());

            for (Player player : survivors) {
                if (validMaterials.isEmpty()) break;
                Material randomMat = validMaterials.get(random.nextInt(validMaterials.size()));
                player.getInventory().addItem(new ItemStack(randomMat, 1));
            }
        }, 1, intervalTicks);
    }
    
    private boolean isUsefulItem(Material material) {
        String name = material.name();
        
        // 排除花类
        if (name.contains("FLOWER") || name.contains("TULIP") || name.contains("ORCHID") || 
            name.contains("ALLIUM") || name.contains("BLUET") || name.contains("DAISY") ||
            name.contains("POPPY") || name.contains("DANDELION") || name.contains("CORNFLOWER") ||
            name.contains("LILY") || name.contains("ROSE") || name.contains("PEONY") || 
            name.contains("LILAC") || name.contains("SUNFLOWER")) {
            return false;
        }
        
        // 排除红石相关
        if (name.contains("REDSTONE") || name.equals("REPEATER") || name.equals("COMPARATOR") ||
            name.equals("OBSERVER") || name.equals("PISTON") || name.equals("STICKY_PISTON") ||
            name.equals("DISPENSER") || name.equals("DROPPER") || name.equals("HOPPER")) {
            return false;
        }
        
        // 排除挽具
        if (name.contains("HORSE_ARMOR") || name.contains("SADDLE") || name.equals("LEAD") ||
            name.equals("NAME_TAG") || name.contains("BANNER_PATTERN")) {
            return false;
        }
        
        // 排除唱片
        if (name.contains("MUSIC_DISC") || name.equals("JUKEBOX")) {
            return false;
        }
        
        // 排除指南针和时钟
        if (name.equals("COMPASS") || name.equals("CLOCK") || name.equals("RECOVERY_COMPASS")) {
            return false;
        }
        
        // 排除火把
        if (name.contains("TORCH")) {
            return false;
        }
        
        // 排除原材料（矿石、粗金属、碎片等）
        if (name.contains("_ORE") || name.contains("RAW_") || name.contains("SCRAP") ||
            name.contains("NETHERITE_UPGRADE") || name.contains("SMITHING_TEMPLATE") ||
            name.contains("SHARD") || name.contains("ECHO_SHARD") || name.equals("AMETHYST_SHARD") ||
            name.equals("PRISMARINE_SHARD") || name.equals("PRISMARINE_CRYSTALS") ||
            name.equals("NETHER_STAR") || name.equals("DRAGON_EGG") || name.equals("DRAGON_HEAD") ||
            name.equals("DRAGON_BREATH")) {
            return false;
        }
        
        // 排除种子和农作物种子
        if (name.contains("SEEDS") || name.equals("WHEAT_SEEDS") || name.equals("BEETROOT_SEEDS") ||
            name.equals("MELON_SEEDS") || name.equals("PUMPKIN_SEEDS") || name.equals("TORCHFLOWER_SEEDS") ||
            name.equals("PITCHER_POD")) {
            return false;
        }
        
        // 排除刷怪蛋
        if (name.contains("SPAWN_EGG")) {
            return false;
        }
        
        // 排除书和纸相关
        if (name.equals("BOOK") || name.equals("WRITABLE_BOOK") || name.equals("WRITTEN_BOOK") ||
            name.equals("ENCHANTED_BOOK") || name.equals("KNOWLEDGE_BOOK") || name.equals("PAPER")) {
            return false;
        }
        
        // 排除装饰性和无用物品
        if (name.equals("PAINTING") || name.equals("ITEM_FRAME") || name.equals("GLOW_ITEM_FRAME") ||
            name.equals("ARMOR_STAND") || name.contains("POTTERY") || name.contains("SHERD") ||
            name.equals("BRUSH") || name.equals("SPYGLASS")) {
            return false;
        }
        
        return true;
    }

    private void startEventTask() {
        long delay = config.getEventDelayMin() + random.nextLong(config.getEventDelayMax() - config.getEventDelayMin() + 1);
        eventTask = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (!gameRunning || eventTriggered) return;
            triggerRandomEvent();
            eventTriggered = true;
        }, delay);
    }

    private void startBorderShrink() {
        long delay = config.getShrinkDelay();
        long interval = config.getShrinkInterval();
        borderShrinkTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (!gameRunning || gameBorder == null) { 
                task.cancel(); 
                return; 
            }
            double currentSize = gameBorder.getSize();
            double minSize = config.getMinBorderSize();
            if (currentSize <= minSize) { 
                task.cancel(); 
                Bukkit.broadcastMessage("§c边界已缩小到最小范围（" + minSize + "格）！"); 
                return; 
            }
            double newSize = Math.max(minSize, currentSize - config.getShrinkAmount());
            long shrinkSeconds = config.getShrinkInterval() / 20;
            gameBorder.setSize(newSize, shrinkSeconds);
            Bukkit.broadcastMessage("§e边界正在缩小！当前直径：§6" + (int)newSize + "格");
            for (Player p : getSurvivingPlayers()) p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
        }, delay, interval);
    }

    private void triggerRandomEvent() {
        int eventType = random.nextInt(3) + 1;
        List<Player> survivors = getSurvivingPlayers();
        if (survivors.isEmpty()) return;
        switch (eventType) {
            case 1:
                Bukkit.broadcastMessage("§c随机事件：§6天空下起箭雨！");
                final int[] count = {0};
                Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
                    if (count[0] >= 20 || !gameRunning) { 
                        task.cancel(); 
                        return; 
                    }
                    List<Player> currentSurvivors = getSurvivingPlayers();
                    for (Player p : currentSurvivors) {
                        World world = p.getWorld();
                        for (int i = 0; i < 5; i++) {
                            double ox = random.nextDouble() * 10 - 5;
                            double oz = random.nextDouble() * 10 - 5;
                            Location spawnLoc = p.getLocation().add(ox, 25, oz);
                            Arrow arrow = world.spawnArrow(spawnLoc, new Vector(0, -1, 0), 1, 0);
                            arrow.setDamage(2.0);
                        }
                    }
                    count[0]++;
                }, 1, 20);
                break;
            case 2:
                Player ghastTarget = survivors.get(random.nextInt(survivors.size()));
                Bukkit.broadcastMessage("§c随机事件：§6一只恶魂在" + ghastTarget.getName() + "附近生成！");
                // 实体生成必须在区域调度器中执行（Folia 要求）
                Location ghastLoc = ghastTarget.getLocation().add(random.nextInt(10) - 5, 5, random.nextInt(10) - 5);
                Bukkit.getRegionScheduler().run(plugin, ghastLoc, task -> {
                    Entity ghast = ghastTarget.getWorld().spawnEntity(ghastLoc, EntityType.GHAST);
                    spawnedMobs.add(ghast);
                });
                break;
            case 3:
                Player zombieTarget = survivors.get(random.nextInt(survivors.size()));
                Bukkit.broadcastMessage("§c随机事件：§63只僵尸在" + zombieTarget.getName() + "附近生成！");
                // 实体生成必须在区域调度器中执行（Folia 要求）
                for (int i = 0; i < 3; i++) {
                    Location zombieLoc = zombieTarget.getLocation().add(random.nextInt(10) - 5, 0, random.nextInt(10) - 5);
                    Bukkit.getRegionScheduler().run(plugin, zombieLoc, task -> {
                        Entity zombie = zombieTarget.getWorld().spawnEntity(zombieLoc, EntityType.ZOMBIE);
                        spawnedMobs.add(zombie);
                    });
                }
                break;
        }
    }

    private List<Player> getSurvivingPlayers() {
        List<Player> survivors = new ArrayList<>();
        // 只检查参与者，不检查所有在线玩家
        for (Player p : participants) {
            if (p.isOnline() && p.getGameMode() == GameMode.SURVIVAL) {
                survivors.add(p);
            }
        }
        return survivors;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!gameRunning) return;
        Player player = event.getEntity();
        event.getDrops().clear();
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage("§c你已死亡！切换为旁观者模式，等待下一轮。");
        
        List<Player> survivors = getSurvivingPlayers();
        if (survivors.size() == 1) {
            Player winner = survivors.get(0);
            
            // 使用标题显示胜利消息
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(Title.title(
                    net.kyori.adventure.text.Component.text("§6§l游戏结束"),
                    net.kyori.adventure.text.Component.text("§a" + winner.getName() + " §e获得胜利！")
                ));
            }
            
            Bukkit.broadcastMessage("§6" + winner.getName() + " §e赢得了本局比赛！");
            Bukkit.broadcastMessage("§7使用 /ripvp start 开始下一局");
            
            // 延迟5秒后结束游戏
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                stopGame(false);
            }, 100L);
        } else if (survivors.isEmpty()) {
            Bukkit.broadcastMessage("§c所有玩家已死亡！游戏结束。");
            Bukkit.broadcastMessage("§7使用 /ripvp start 开始下一局");
            
            // 延迟5秒后结束游戏
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                stopGame(false);
            }, 100L);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (gameRunning) {
            player.setGameMode(GameMode.SPECTATOR);
            if (spawnLocation != null) player.teleportAsync(spawnLocation);
            player.getInventory().clear();
            player.sendMessage("§e游戏正在进行中！你将以旁观者模式等待下一轮。");
        } else {
            player.sendMessage("§a随机物品PVP插件已加载！输入 /ripvp start 启动游戏。");
        }
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!gameRunning) return;
        // 记录玩家放置的方块位置
        placedBlocks.add(event.getBlock().getLocation());
    }
}
