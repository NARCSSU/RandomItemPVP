# PlaceholderAPI 变量使用指南

## 📋 前置要求

1. 安装 [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) 插件
2. 重启服务器或重载插件

## 🎯 变量格式

所有变量使用格式：`%randomitempvp_<变量名>%`

## 📊 可用变量列表

### 基础统计

| 变量 | 说明 | 示例输出 |
|------|------|----------|
| `%randomitempvp_wins%` | 胜利次数 | `15` |
| `%randomitempvp_losses%` | 失败次数 | `8` |
| `%randomitempvp_kills%` | 击杀数 | `47` |
| `%randomitempvp_deaths%` | 死亡数 | `23` |
| `%randomitempvp_games%` | 总游戏场次 | `23` |
| `%randomitempvp_games_played%` | 总游戏场次（别名） | `23` |

### 计算数据

| 变量 | 说明 | 示例输出 |
|------|------|----------|
| `%randomitempvp_kd%` | KD比率 | `2.04` |
| `%randomitempvp_kdratio%` | KD比率（别名） | `2.04` |
| `%randomitempvp_winrate%` | 胜率（小数） | `65.2` |
| `%randomitempvp_win_rate%` | 胜率（别名） | `65.2` |
| `%randomitempvp_winrate_percent%` | 胜率（带百分号） | `65.2%` |

### 带颜色格式化

| 变量 | 说明 | 颜色规则 |
|------|------|----------|
| `%randomitempvp_kd_formatted%` | 彩色KD比率 | §a绿色(≥2.0) §e黄色(≥1.0) §c红色(<1.0) |
| `%randomitempvp_winrate_formatted%` | 彩色胜率 | §a绿色(≥50%) §e黄色(≥30%) §c红色(<30%) |

### 战绩汇总

| 变量 | 说明 | 示例输出 |
|------|------|----------|
| `%randomitempvp_record%` | 胜负记录（中文） | `15胜8负` |
| `%randomitempvp_record_en%` | 胜负记录（英文） | `15W 8L` |
| `%randomitempvp_kill_death%` | 击杀/死亡 | `47/23` |

### 排名（预留）

| 变量 | 说明 | 当前输出 |
|------|------|----------|
| `%randomitempvp_rank_wins%` | 胜利排名 | `N/A` |
| `%randomitempvp_rank_kills%` | 击杀排名 | `N/A` |
| `%randomitempvp_rank_kd%` | KD排名 | `N/A` |

*注：排名功能将在后续版本实现*

## 💡 使用示例

### 1. 记分板显示

使用 DeluxeScoreboard、FeatherBoard 等记分板插件：

```yaml
scoreboard:
  lines:
    - '&6&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬'
    - '&e游戏统计'
    - ''
    - '&f胜利: &a%randomitempvp_wins%'
    - '&f失败: &c%randomitempvp_losses%'
    - '&f胜率: %randomitempvp_winrate_formatted%'
    - ''
    - '&f击杀: &a%randomitempvp_kills%'
    - '&f死亡: &c%randomitempvp_deaths%'
    - '&fKD: %randomitempvp_kd_formatted%'
    - '&6&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬'
```

### 2. 聊天前缀

使用 LuckPerms + Vault：

```yaml
# 在聊天插件中配置
chat-format: '&7[&e%randomitempvp_record_en%&7] &f{PLAYER}: {MESSAGE}'
# 显示效果: [15W 8L] Steve: Hello!
```

### 3. TAB列表

使用 TAB 插件：

```yaml
tablist-format:
  header:
    - '&6=== RandomItemPVP ==='
    - '&7你的战绩: %randomitempvp_record%'
  footer:
    - '&7KD: %randomitempvp_kd% | 胜率: %randomitempvp_winrate_percent%'
```

### 4. 头顶名称

使用 TAB 或 NametagEdit：

```yaml
nametag-format: '&7[KD:%randomitempvp_kd%] &f{PLAYER}'
# 显示效果: [KD:2.04] Steve
```

### 5. 动态MOTD

使用 ServerListPlus：

```yaml
motd:
  - '&6欢迎来到 RandomItemPVP 服务器'
  - '&7你的战绩: %randomitempvp_wins%胜 | KD: %randomitempvp_kd%'
```

### 6. 全息图

使用 HolographicDisplays 或 DecentHolograms：

```
/hd create stats
/hd addline stats &6你的游戏统计
/hd addline stats &f战绩: %randomitempvp_record%
/hd addline stats &fKD: %randomitempvp_kd_formatted%
/hd addline stats &f胜率: %randomitempvp_winrate_formatted%
```

### 7. 自定义命令输出

使用 CommandPanels、ChestCommands 等：

```yaml
items:
  stats:
    material: PLAYER_HEAD
    display-name: '&6我的统计'
    lore:
      - '&f战绩: &e%randomitempvp_record%'
      - '&f击杀: &a%randomitempvp_kills% &7死亡: &c%randomitempvp_deaths%'
      - '&fKD: %randomitempvp_kd_formatted%'
      - '&f胜率: %randomitempvp_winrate_formatted%'
```

## 🎨 颜色代码参考

| 代码 | 颜色 | 用途 |
|------|------|------|
| `§a` / `&a` | 绿色 | 优秀数据 |
| `§e` / `&e` | 黄色 | 良好数据 |
| `§c` / `&c` | 红色 | 较低数据 |
| `§6` / `&6` | 金色 | 标题 |
| `§7` / `&7` | 灰色 | 次要信息 |
| `§f` / `&f` | 白色 | 主要文字 |

## 📝 测试变量

使用 PlaceholderAPI 的测试命令：

```
/papi parse me %randomitempvp_wins%
/papi parse me %randomitempvp_kd_formatted%
/papi parse me %randomitempvp_record%
```

## ⚙️ 高级用法

### 条件显示

配合 PAPI 的条件扩展（需要安装 Conditional 扩展）：

```
%conditional_{CONDITION}?{TRUE_VALUE}:{FALSE_VALUE}%
```

示例：
```
# 如果KD大于2，显示"精英玩家"，否则显示战绩
%conditional_randomitempvp_kd >= 2?&a&l精英玩家:&7%randomitempvp_record%%
```

### 数学运算

配合 Math 扩展：

```
# 计算总参与次数
%math_{randomitempvp_kills}+{randomitempvp_deaths}%

# 计算击杀贡献度
%math_{randomitempvp_kills}*100/{randomitempvp_games_played}%
```

## 🔧 故障排除

### 变量显示为原始文本

**问题**：`%randomitempvp_wins%` 显示为原样而不是数字

**解决方案**：
1. 确认已安装 PlaceholderAPI
2. 检查插件是否正常加载：`/papi info randomitempvp`
3. 重载 PAPI：`/papi reload`
4. 如果还不行，重启服务器

### 变量显示"N/A"或"错误"

**问题**：变量显示异常值

**解决方案**：
1. 检查玩家是否有统计数据（至少参与过一次游戏）
2. 确认数据库连接正常
3. 查看服务器日志是否有错误信息

### 变量更新延迟

**问题**：统计数据不是实时更新

**说明**：
- 数据库写入是异步的，通常在1秒内完成
- 某些记分板插件有刷新间隔设置
- 可以在记分板配置中降低刷新间隔

## 📚 相关资源

- [PlaceholderAPI Wiki](https://github.com/PlaceholderAPI/PlaceholderAPI/wiki)
- [PAPI 扩展列表](https://github.com/PlaceholderAPI/PlaceholderAPI/wiki/Placeholders)
- [颜色代码生成器](https://www.colorschemer.com/minecraft-color-codes/)

## 🆕 未来计划

- [ ] 实时排名变量（`rank_wins`, `rank_kills`, `rank_kd`）
- [ ] 赛季统计变量
- [ ] 成就进度变量
- [ ] 最高连杀记录变量
- [ ] 最常使用物品变量

## 💬 示例配置模板

### 完整记分板配置（示例）

```yaml
# 适用于大多数记分板插件
title: '&6&lRandomItemPVP'

lines:
  - '&7&m--------------------'
  - ''
  - '&e▸ &f战绩'
  - '  &7%randomitempvp_record% &8(%randomitempvp_games%场)'
  - ''
  - '&e▸ &f战斗数据'
  - '  &7击杀: &a%randomitempvp_kills% &8| &7死亡: &c%randomitempvp_deaths%'
  - '  &7KD: %randomitempvp_kd_formatted%'
  - ''
  - '&e▸ &f综合评价'
  - '  &7胜率: %randomitempvp_winrate_formatted%'
  - ''
  - '&7&m--------------------'
  - '&7play.example.com'
```

## 🎮 游戏内展示效果预览

```
╔══════════════════════════╗
║   RandomItemPVP 统计     ║
╠══════════════════════════╣
║ 玩家: Steve              ║
║                          ║
║ 战绩: 15胜8负 (23场)     ║
║ 胜率: §a65.2%§f           ║
║                          ║
║ 击杀: 47 | 死亡: 23      ║
║ KD: §a2.04§f              ║
╚══════════════════════════╝
```

---

**提示**：所有变量都是按玩家实时计算的，确保每个玩家看到的都是自己的数据！


