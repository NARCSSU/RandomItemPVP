package org.luminolcraft.randomitempvp;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Worlds 插件集成类
 * 使用反射调用 Worlds 插件的 API，避免编译时依赖
 * 
 * 注意：Worlds 插件是必需依赖！如果 Worlds 插件不可用，插件将无法启动
 */
public class WorldsIntegration {
    private static final Logger logger = Logger.getLogger("RandomItemPVP");
    
    // Worlds 插件实例
    private static Plugin worldsPlugin = null;
    private static Object worldsAPI = null;
    private static Object worldsAPIInstance = null;
    
    // 缓存的方法引用（提高性能）
    private static Method methodGetWorld = null;
    private static Method methodCopyWorld = null;
    private static Method methodDeleteWorld = null;
    private static Method methodResetWorld = null;
    
    // 标志位：是否已记录类加载错误（避免重复日志）
    private static boolean hasLoggedClassLoadError = false;
    
    // 初始化标志位
    private static boolean initialized = false;
    
    /**
     * 初始化 Worlds 插件集成
     * 在插件启动时调用（只会初始化一次）
     */
    public static void initialize() {
        // 如果已经初始化过，直接返回
        if (initialized) {
            return;
        }
        
        worldsPlugin = Bukkit.getPluginManager().getPlugin("Worlds");
        
        if (worldsPlugin == null) {
            logger.warning("[WorldsIntegration] Worlds 插件未找到");
            initialized = true; // 标记为已初始化（即使失败）
            return;
        }
        
        logger.info("[WorldsIntegration] 找到 Worlds 插件: " + worldsPlugin.getDescription().getVersion());
        
        // 尝试获取 Worlds API
        try {
            // 尝试多种可能的 API 获取方式
            Class<?> apiClass = Class.forName("org.popcornmc.worlds.WorldsAPI");
            if (apiClass != null) {
                // 尝试获取静态实例（使用更安全的方式）
                try {
                    // 先检查方法是否存在，避免触发类加载
                    Method getInstance = null;
                    try {
                        getInstance = apiClass.getDeclaredMethod("getInstance");
                    } catch (NoSuchMethodException e) {
                        // 尝试公共方法
                        getInstance = apiClass.getMethod("getInstance");
                    }
                    
                    if (getInstance != null) {
                        getInstance.setAccessible(true);
                        worldsAPI = getInstance.invoke(null);
                        logger.info("[WorldsIntegration] 通过 getInstance() 获取 Worlds API");
                    } else {
                        // 尝试直接使用静态类
                        worldsAPI = apiClass;
                        logger.info("[WorldsIntegration] 使用 WorldsAPI 静态类");
                    }
                } catch (NoClassDefFoundError e) {
                    // 如果依赖类不存在，直接使用静态类
                    worldsAPI = apiClass;
                    logger.info("[WorldsIntegration] 使用 WorldsAPI 静态类（跳过 getInstance）");
                } catch (Exception e) {
                    // 尝试直接使用静态方法
                    worldsAPI = apiClass;
                    logger.info("[WorldsIntegration] 使用 WorldsAPI 静态类");
                }
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // 尝试其他可能的类名
            try {
                Class<?> apiClass = Class.forName("com.popcornmc.worlds.WorldsAPI");
                try {
                    Method getInstance = null;
                    try {
                        getInstance = apiClass.getDeclaredMethod("getInstance");
                    } catch (NoSuchMethodException ex) {
                        getInstance = apiClass.getMethod("getInstance");
                    }
                    
                    if (getInstance != null) {
                        getInstance.setAccessible(true);
                        worldsAPI = getInstance.invoke(null);
                        logger.info("[WorldsIntegration] 通过 getInstance() 获取 Worlds API (com.popcornmc)");
                    } else {
                        worldsAPI = apiClass;
                        logger.info("[WorldsIntegration] 使用 WorldsAPI 静态类 (com.popcornmc)");
                    }
                } catch (NoClassDefFoundError ex) {
                    worldsAPI = apiClass;
                    logger.info("[WorldsIntegration] 使用 WorldsAPI 静态类 (com.popcornmc，跳过 getInstance)");
                } catch (Exception ex) {
                    worldsAPI = apiClass;
                    logger.info("[WorldsIntegration] 使用 WorldsAPI 静态类 (com.popcornmc)");
                }
            } catch (ClassNotFoundException | NoClassDefFoundError ex) {
                // 尝试从插件获取
                try {
                    Method getAPI = null;
                    try {
                        getAPI = worldsPlugin.getClass().getDeclaredMethod("getAPI");
                    } catch (NoSuchMethodException exc) {
                        getAPI = worldsPlugin.getClass().getMethod("getAPI");
                    }
                    
                    if (getAPI != null) {
                        getAPI.setAccessible(true);
                        worldsAPI = getAPI.invoke(worldsPlugin);
                        logger.info("[WorldsIntegration] 通过插件 getAPI() 获取 Worlds API");
                    } else {
                        worldsAPI = worldsPlugin;
                        logger.info("[WorldsIntegration] 使用插件实例作为 API");
                    }
                } catch (NoClassDefFoundError exc) {
                    logger.warning("[WorldsIntegration] 无法获取 Worlds API，使用插件实例");
                    worldsAPI = worldsPlugin;
                } catch (Exception exc) {
                    logger.warning("[WorldsIntegration] 无法获取 Worlds API，尝试直接使用插件实例");
                    worldsAPI = worldsPlugin;
                }
            }
        }
        
        // 初始化方法缓存
        initializeMethods();
        
        // 标记为已初始化
        initialized = true;
    }
    
    /**
     * 初始化方法缓存（安全方式，避免触发类加载）
     */
    private static void initializeMethods() {
        if (worldsAPI == null) {
            return;
        }
        
        Class<?> apiClass = worldsAPI instanceof Class ? (Class<?>) worldsAPI : worldsAPI.getClass();
        
        try {
            // 尝试查找 getWorld 方法
            methodGetWorld = findMethod(apiClass, "getWorld", String.class);
            if (methodGetWorld == null) {
                methodGetWorld = findMethod(apiClass, "loadWorld", String.class);
            }
            if (methodGetWorld == null) {
                methodGetWorld = findMethod(apiClass, "getWorldByName", String.class);
            }
            
            // 尝试查找 copyWorld 方法（可能的变体：copyWorld, cloneWorld, duplicateWorld）
            methodCopyWorld = findMethod(apiClass, "copyWorld", String.class, String.class);
            if (methodCopyWorld == null) {
                methodCopyWorld = findMethod(apiClass, "cloneWorld", String.class, String.class);
            }
            if (methodCopyWorld == null) {
                methodCopyWorld = findMethod(apiClass, "duplicateWorld", String.class, String.class);
            }
            
            // 尝试查找 deleteWorld 方法（可能的变体：deleteWorld, removeWorld, unloadWorld）
            methodDeleteWorld = findMethod(apiClass, "deleteWorld", String.class);
            if (methodDeleteWorld == null) {
                methodDeleteWorld = findMethod(apiClass, "removeWorld", String.class);
            }
            if (methodDeleteWorld == null) {
                methodDeleteWorld = findMethod(apiClass, "unloadWorld", String.class);
            }
            
            // 尝试查找 resetWorld 方法（可能的变体：resetWorld, reset, reloadWorld）
            methodResetWorld = findMethod(apiClass, "resetWorld", String.class);
            if (methodResetWorld == null) {
                methodResetWorld = findMethod(apiClass, "reset", String.class);
            }
            if (methodResetWorld == null) {
                methodResetWorld = findMethod(apiClass, "reloadWorld", String.class);
            }
            
            // 记录找到的方法
            if (methodGetWorld != null) {
                logger.info("[WorldsIntegration] 找到 getWorld/loadWorld 方法");
            }
            if (methodCopyWorld != null) {
                logger.info("[WorldsIntegration] 找到 copyWorld/cloneWorld 方法");
            }
            if (methodDeleteWorld != null) {
                logger.info("[WorldsIntegration] 找到 deleteWorld/removeWorld 方法");
            }
            if (methodResetWorld != null) {
                logger.info("[WorldsIntegration] 找到 resetWorld/reset 方法");
            }
        } catch (NoClassDefFoundError e) {
            logger.warning("[WorldsIntegration] 初始化方法时遇到类加载错误: " + e.getMessage());
            logger.warning("[WorldsIntegration] 将使用动态查找方式");
        }
    }
    
    /**
     * 查找方法（支持方法名模糊匹配，安全方式避免触发类加载）
     */
    private static Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            // 先尝试公共方法
            try {
                return clazz.getMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                // 尝试声明的方法
                try {
                    return clazz.getDeclaredMethod(methodName, parameterTypes);
                } catch (NoSuchMethodException ex) {
                    // 继续查找
                }
            }
        } catch (NoClassDefFoundError e) {
            // 如果依赖类不存在，返回 null（只记录一次）
            if (!hasLoggedClassLoadError) {
                logger.warning("[WorldsIntegration] Worlds 插件包含可选依赖类，某些方法可能无法访问: " + e.getMessage());
                logger.info("[WorldsIntegration] 将使用动态方法查找，不影响核心功能");
                hasLoggedClassLoadError = true;
            }
            return null;
        }
        
        // 尝试查找所有方法，进行模糊匹配（使用更安全的方式）
        try {
            // 先尝试公共方法（安全获取）
            Method[] methods = null;
            try {
                methods = clazz.getMethods();
            } catch (NoClassDefFoundError e) {
                // 如果获取方法列表时就失败，直接返回 null
                if (!hasLoggedClassLoadError) {
                    logger.warning("[WorldsIntegration] 无法获取方法列表，某些 Worlds 插件功能可能不可用");
                    hasLoggedClassLoadError = true;
                }
                return null;
            }
            
            for (Method method : methods) {
                if (method.getName().equalsIgnoreCase(methodName) && 
                    method.getParameterCount() == parameterTypes.length) {
                    try {
                        // 安全获取参数类型
                        Class<?>[] paramTypes = method.getParameterTypes();
                        boolean match = true;
                        for (int i = 0; i < paramTypes.length; i++) {
                            try {
                                if (!paramTypes[i].isAssignableFrom(parameterTypes[i])) {
                                    match = false;
                                    break;
                                }
                            } catch (NoClassDefFoundError e) {
                                // 参数类型无法加载，跳过此方法
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            return method;
                        }
                    } catch (NoClassDefFoundError e) {
                        // 跳过这个方法，继续查找（不记录日志，避免重复）
                        continue;
                    }
                }
            }
            
            // 如果公共方法没找到，尝试声明的方法
            try {
                Method[] declaredMethods = clazz.getDeclaredMethods();
                for (Method method : declaredMethods) {
                    if (method.getName().equalsIgnoreCase(methodName) && 
                        method.getParameterCount() == parameterTypes.length) {
                        try {
                            Class<?>[] paramTypes = method.getParameterTypes();
                            boolean match = true;
                            for (int i = 0; i < paramTypes.length; i++) {
                                try {
                                    if (!paramTypes[i].isAssignableFrom(parameterTypes[i])) {
                                        match = false;
                                        break;
                                    }
                                } catch (NoClassDefFoundError e) {
                                    match = false;
                                    break;
                                }
                            }
                            if (match) {
                                method.setAccessible(true);
                                return method;
                            }
                        } catch (NoClassDefFoundError e) {
                            // 跳过这个方法，继续查找
                            continue;
                        }
                    }
                }
            } catch (NoClassDefFoundError e) {
                // 忽略（不记录日志）
            }
        } catch (NoClassDefFoundError e) {
            // 只在第一次遇到时记录
            if (!hasLoggedClassLoadError) {
                logger.warning("[WorldsIntegration] 查找方法时遇到类加载错误: " + e.getMessage());
                hasLoggedClassLoadError = true;
            }
        }
        
        return null;
    }
    
    /**
     * 检查 Worlds 插件是否可用
     * @return 如果 Worlds 插件已加载且可用，返回 true
     */
    public static boolean isWorldsAvailable() {
        if (worldsPlugin == null) {
            // 尝试重新初始化（只初始化一次）
            initialize();
        }
        return worldsPlugin != null && worldsPlugin.isEnabled() && worldsAPI != null;
    }
    
    /**
     * 列出可用的方法（用于调试）
     */
    public static void listAvailableMethods() {
        if (!isWorldsAvailable()) {
            logger.warning("[WorldsIntegration] Worlds 插件不可用，无法列出方法");
            return;
        }
        
        Class<?> apiClass = worldsAPI instanceof Class ? (Class<?>) worldsAPI : worldsAPI.getClass();
        logger.info("[WorldsIntegration] Worlds API 类: " + apiClass.getName());
        logger.info("[WorldsIntegration] 可用方法:");
        for (Method method : apiClass.getMethods()) {
            logger.info("[WorldsIntegration]   - " + method.getName() + "(" + 
                java.util.Arrays.toString(method.getParameterTypes()) + ")");
        }
    }
    
    /**
     * 加载世界（通过 Worlds 插件或 Bukkit API）
     * @param worldKey 世界 key 或名称
     * @return 世界对象，如果不存在则返回 null
     */
    public static World loadWorld(String worldKey) {
        if (worldKey == null || worldKey.isEmpty()) {
            return null;
        }
        
        // 首先尝试通过 Worlds 插件加载
        if (isWorldsAvailable()) {
            try {
                Object target = worldsAPIInstance != null ? worldsAPIInstance : worldsAPI;
                
                if (methodGetWorld != null && target != null) {
                    Object result = methodGetWorld.invoke(target, worldKey);
                    if (result instanceof World) {
                        return (World) result;
                    }
                }
                
                // 如果缓存的方法不存在，尝试动态查找
                if (target != null) {
                    Class<?> apiClass = target instanceof Class ? (Class<?>) target : target.getClass();
                    Method getWorldMethod = findMethod(apiClass, "getWorld", String.class);
                    if (getWorldMethod == null) {
                        getWorldMethod = findMethod(apiClass, "loadWorld", String.class);
                    }
                    
                    if (getWorldMethod != null) {
                        Object result = getWorldMethod.invoke(target, worldKey);
                        if (result instanceof World) {
                            methodGetWorld = getWorldMethod; // 缓存方法
                            return (World) result;
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("[WorldsIntegration] 通过 Worlds API 加载世界失败: " + e.getMessage());
            }
        }
        
        // 回退到 Bukkit API
        World world = Bukkit.getWorld(worldKey);
        if (world != null) {
            return world;
        }
        
        return null;
    }
    
    /**
     * 复制世界（必须使用 Worlds 插件）
     * @param templateWorldKey 模板世界的 key（源世界）
     * @param newWorldKey 新世界的 key（目标世界，通常为 <模板key>_<房间名>）
     * @return 是否复制成功
     */
    public static boolean copyWorld(String templateWorldKey, String newWorldKey) {
        if (templateWorldKey == null || newWorldKey == null) {
            logger.warning("[WorldsIntegration] 复制世界失败: 世界 key 为空");
            return false;
        }
        
        if (!isWorldsAvailable()) {
            logger.severe("[WorldsIntegration] Worlds 插件不可用，无法复制世界！");
            return false;
        }
        
        try {
            Object target = worldsAPIInstance != null ? worldsAPIInstance : worldsAPI;
            
            // 如果方法已缓存，直接使用
            if (methodCopyWorld != null && target != null) {
                try {
                    Object result = methodCopyWorld.invoke(target, templateWorldKey, newWorldKey);
                    if (result instanceof Boolean && (Boolean) result) {
                        logger.info("[WorldsIntegration] 成功通过 Worlds API 复制世界: " + templateWorldKey + " -> " + newWorldKey);
                        return true;
                    }
                } catch (Exception e) {
                    logger.warning("[WorldsIntegration] 调用 copyWorld 失败: " + e.getMessage());
                }
            }
            
            // 如果缓存的方法不存在，尝试动态查找
            if (target != null) {
                Method copyMethod = findMethod(target.getClass(), "copyWorld", String.class, String.class);
                if (copyMethod == null) {
                    copyMethod = findMethod(target.getClass(), "cloneWorld", String.class, String.class);
                }
                if (copyMethod == null) {
                    copyMethod = findMethod(target.getClass(), "duplicateWorld", String.class, String.class);
                }
                
                if (copyMethod != null) {
                    Object result = copyMethod.invoke(target, templateWorldKey, newWorldKey);
                    if (result instanceof Boolean && (Boolean) result) {
                        methodCopyWorld = copyMethod; // 缓存方法
                        logger.info("[WorldsIntegration] 成功通过 Worlds API 复制世界: " + templateWorldKey + " -> " + newWorldKey);
                        return true;
                    }
                } else {
                    logger.severe("[WorldsIntegration] Worlds 插件未找到复制世界的方法（copyWorld/cloneWorld/duplicateWorld）！");
                }
            }
        } catch (Exception e) {
            logger.severe("[WorldsIntegration] Worlds API 复制世界失败: " + e.getMessage());
        }
        
        logger.severe("[WorldsIntegration] 复制世界失败，Worlds 插件必需！");
        return false;
    }
    
    /**
     * 创建世界的独立实例（为房间使用）
     * 如果世界已存在，直接返回该世界
     * 如果不存在，从模板世界复制（必须使用 Worlds 插件）
     * 
     * @param templateWorldKey 模板世界 key
     * @param instanceWorldKey 实例世界 key（通常为 <模板key>_<房间名>）
     * @return 世界对象，如果创建失败则返回 null
     */
    public static World getOrCreateWorldInstance(String templateWorldKey, String instanceWorldKey) {
        if (!isWorldsAvailable()) {
            logger.severe("[WorldsIntegration] Worlds 插件不可用，无法创建世界实例！");
            return null;
        }
        
        // 首先检查实例世界是否已存在
        World instanceWorld = loadWorld(instanceWorldKey);
        if (instanceWorld != null) {
            return instanceWorld;
        }
        
        // 如果不存在，使用 Worlds 插件复制模板世界
        boolean copied = copyWorld(templateWorldKey, instanceWorldKey);
        if (copied) {
            // 等待一下让世界加载完成
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 再次尝试加载
            instanceWorld = loadWorld(instanceWorldKey);
            if (instanceWorld != null) {
                return instanceWorld;
            }
        }
        
        // 如果复制失败，返回 null（不再使用共享模式）
        logger.severe("[WorldsIntegration] 无法创建独立世界实例 '" + instanceWorldKey + 
            "'，Worlds 插件必需！");
        return null;
    }
    
    /**
     * 删除世界实例（游戏结束后清理）
     * 必须使用 Worlds 插件，没有回退方案
     * 
     * @param worldKey 世界 key
     * @return 是否删除成功
     */
    public static boolean deleteWorldInstance(String worldKey) {
        if (worldKey == null || worldKey.isEmpty()) {
            logger.warning("[WorldsIntegration] 删除世界失败: 世界 key 为空");
            return false;
        }
        
        if (!isWorldsAvailable()) {
            logger.severe("[WorldsIntegration] Worlds 插件不可用，无法删除世界！");
            return false;
        }
        
        try {
            Object target = worldsAPIInstance != null ? worldsAPIInstance : worldsAPI;
            
            // 如果方法已缓存，直接使用
            if (methodDeleteWorld != null && target != null) {
                try {
                    Object result = methodDeleteWorld.invoke(target, worldKey);
                    if (result instanceof Boolean && (Boolean) result) {
                        logger.info("[WorldsIntegration] 成功通过 Worlds API 删除世界: " + worldKey);
                        return true;
                    }
                } catch (Exception e) {
                    logger.warning("[WorldsIntegration] 调用 deleteWorld 失败: " + e.getMessage());
                }
            }
            
            // 如果缓存的方法不存在，尝试动态查找
            if (target != null) {
                Method deleteMethod = findMethod(target.getClass(), "deleteWorld", String.class);
                if (deleteMethod == null) {
                    deleteMethod = findMethod(target.getClass(), "removeWorld", String.class);
                }
                if (deleteMethod == null) {
                    deleteMethod = findMethod(target.getClass(), "unloadWorld", String.class);
                }
                
                if (deleteMethod != null) {
                    Object result = deleteMethod.invoke(target, worldKey);
                    if (result instanceof Boolean && (Boolean) result) {
                        methodDeleteWorld = deleteMethod; // 缓存方法
                        logger.info("[WorldsIntegration] 成功通过 Worlds API 删除世界: " + worldKey);
                        return true;
                    }
                } else {
                    logger.severe("[WorldsIntegration] Worlds 插件未找到删除世界的方法（deleteWorld/removeWorld/unloadWorld）！");
                }
            }
        } catch (Exception e) {
            logger.severe("[WorldsIntegration] Worlds API 删除世界失败: " + e.getMessage());
        }
        
        logger.severe("[WorldsIntegration] 删除世界失败，Worlds 插件必需！");
        return false;
    }
    
    /**
     * 重置世界（游戏结束后清理，可选）
     * 如果 Worlds 插件支持，使用重置功能；否则返回 false
     * 
     * @param worldKey 世界 key
     * @return 是否重置成功
     */
    public static boolean resetWorldInstance(String worldKey) {
        if (worldKey == null || worldKey.isEmpty()) {
            logger.warning("[WorldsIntegration] 重置世界失败: 世界 key 为空");
            return false;
        }
        
        if (!isWorldsAvailable()) {
            logger.severe("[WorldsIntegration] Worlds 插件不可用，无法重置世界！");
            return false;
        }
        
        try {
            Object target = worldsAPIInstance != null ? worldsAPIInstance : worldsAPI;
            
            // 如果方法已缓存，直接使用
            if (methodResetWorld != null && target != null) {
                try {
                    Object result = methodResetWorld.invoke(target, worldKey);
                    if (result instanceof Boolean && (Boolean) result) {
                        logger.info("[WorldsIntegration] 成功通过 Worlds API 重置世界: " + worldKey);
                        return true;
                    }
                } catch (Exception e) {
                    logger.warning("[WorldsIntegration] 调用 resetWorld 失败: " + e.getMessage());
                }
            }
            
            // 如果缓存的方法不存在，尝试动态查找
            if (target != null) {
                Method resetMethod = findMethod(target.getClass(), "resetWorld", String.class);
                if (resetMethod == null) {
                    resetMethod = findMethod(target.getClass(), "reset", String.class);
                }
                if (resetMethod == null) {
                    resetMethod = findMethod(target.getClass(), "reloadWorld", String.class);
                }
                
                if (resetMethod != null) {
                    Object result = resetMethod.invoke(target, worldKey);
                    if (result instanceof Boolean && (Boolean) result) {
                        methodResetWorld = resetMethod; // 缓存方法
                        logger.info("[WorldsIntegration] 成功通过 Worlds API 重置世界: " + worldKey);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("[WorldsIntegration] Worlds API 重置世界失败: " + e.getMessage());
        }
        
        return false;
    }
}

