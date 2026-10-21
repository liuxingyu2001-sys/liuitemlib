package com.liuitemlib;

import com.liuitemlib.api.LiuItemLibraryApi;
import com.liuitemlib.command.ItemLibCommand;
import com.liuitemlib.config.LibConfig;
import com.liuitemlib.db.DatabaseManager;
import com.liuitemlib.gui.LibraryGui;
import com.liuitemlib.item.ItemLibraryService;
import com.liuxingyu.liulib.LiuLib;
import com.liuxingyu.liulib.item.ItemResolver;
import com.liuxingyu.liulib.item.ItemService;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public final class LiuItemLibrary extends org.bukkit.plugin.java.JavaPlugin {
    private LibConfig config;
    private DatabaseManager database;
    private volatile ItemLibraryService service;
    private ItemService items;
    private BukkitTask syncTask;
    private ItemResolver libResolver;
    private boolean ready;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = LiuLib.items();
        config = LibConfig.load(getConfig());
        if (!openDatabase()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        LiuItemLibraryApi.install(this);
        ItemLibCommand command = new ItemLibCommand(this);
        if (getCommand("itemlib") != null) {
            getCommand("itemlib").setExecutor(command);
            getCommand("itemlib").setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(new LibraryGui.Listener(this), this);
        registerLibResolver();
        startSyncTask();
        ready = true;
        getLogger().info("启动完成 | DB: " + (config.db().isSqlite() ? config.db().sqliteFile : "mysql:" + config.db().database)
                + " | 物品: " + service.count() + " | revision: " + service.lastRevision()
                + " | 同步间隔: " + config.syncIntervalSeconds() + "s");
    }

    /**
     * 把物品库接入 LiuLib 的规格解析：注册 {@code lib} 前缀后，所有走 LiuLib
     * {@code build/buildOrNull} 的插件都能直接写 {@code lib:gift:spring_2026}。
     */
    private void registerLibResolver() {
        if (items == null) {
            return;
        }
        libResolver = body -> {
            ItemLibraryService svc = service;
            return svc == null ? null : svc.build(body);
        };
        items.registerResolver("lib", libResolver);
    }

    @Override
    public void onDisable() {
        ready = false;
        if (items != null) {
            items.unregisterResolver("lib");
        }
        stopSyncTask();
        if (database != null) {
            database.close();
            database = null;
        }
        service = null;
    }

    private boolean openDatabase() {
        try {
            DatabaseManager db = new DatabaseManager(config.db(), getLogger());
            db.init();
            ItemLibraryService svc = new ItemLibraryService(this, db, items, config);
            svc.loadCache();
            this.database = db;
            this.service = svc;
            return true;
        } catch (Exception e) {
            getLogger().severe("数据库初始化失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void startSyncTask() {
        int seconds = Math.max(1, config.syncIntervalSeconds());
        syncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            ItemLibraryService svc = service;
            if (svc != null) {
                svc.syncQuietly();
            }
        }, seconds * 20L, seconds * 20L);
    }

    private void stopSyncTask() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
    }

    /** /itemlib reload：重读配置、重建数据库连接、全量同步，并重启轮询。 */
    public void reloadAll() {
        stopSyncTask();
        if (database != null) {
            database.close();
            database = null;
        }
        reloadConfig();
        config = LibConfig.load(getConfig());
        if (!openDatabase()) {
            getLogger().severe("重载失败：数据库未就绪，插件保持停用状态");
            ready = false;
            return;
        }
        startSyncTask();
        ready = true;
        getLogger().info("重载完成 | DB: " + (config.db().isSqlite() ? config.db().sqliteFile : "mysql:" + config.db().database)
                + " | 物品: " + service.count() + " | revision: " + service.lastRevision());
    }

    public LibConfig config() {
        return config;
    }

    public ItemLibraryService service() {
        return service;
    }

    public ItemService items() {
        return items;
    }

    public boolean ready() {
        return ready && service != null;
    }
}
