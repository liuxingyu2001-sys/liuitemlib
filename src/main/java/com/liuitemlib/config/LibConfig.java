package com.liuitemlib.config;

import org.bukkit.configuration.file.FileConfiguration;

/** config.yml 解析结果。 */
public final class LibConfig {
    private final DbConfig db;
    private final int syncIntervalSeconds;
    private final int maxEncodedLength;
    private final String defaultCategory;
    private final int maxGive;

    private LibConfig(DbConfig db, int syncIntervalSeconds, int maxEncodedLength, String defaultCategory, int maxGive) {
        this.db = db;
        this.syncIntervalSeconds = syncIntervalSeconds;
        this.maxEncodedLength = maxEncodedLength;
        this.defaultCategory = defaultCategory;
        this.maxGive = maxGive;
    }

    public static LibConfig load(FileConfiguration c) {
        DbConfig db = new DbConfig();
        db.type = c.getString("db.type", db.type);
        db.host = c.getString("db.host", db.host);
        db.port = c.getInt("db.port", db.port);
        db.database = c.getString("db.database", db.database);
        db.user = c.getString("db.user", db.user);
        db.password = c.getString("db.password", db.password);
        db.params = c.getString("db.params", db.params);
        db.sqliteFile = c.getString("db.sqlite-file", db.sqliteFile);
        db.tablePrefix = c.getString("db.table-prefix", db.tablePrefix);
        db.poolSize = Math.max(1, c.getInt("db.pool-size", db.poolSize));
        db.connectionTimeoutMs = Math.max(1000, c.getInt("db.connection-timeout-ms", db.connectionTimeoutMs));
        db.autoCreateDatabase = c.getBoolean("db.auto-create-database", db.autoCreateDatabase);
        db.autoCreateTables = c.getBoolean("db.auto-create-tables", db.autoCreateTables);
        int sync = Math.max(1, c.getInt("settings.sync-interval-seconds", 5));
        int maxLen = Math.max(1024, c.getInt("settings.max-encoded-length", 60000));
        String defaultCategory = c.getString("settings.default-category", "default");
        if (defaultCategory == null || defaultCategory.isBlank()) {
            defaultCategory = "default";
        }
        int maxGive = Math.max(1, c.getInt("settings.max-give", 2304));
        return new LibConfig(db, sync, maxLen, defaultCategory.trim().toLowerCase(), maxGive);
    }

    public DbConfig db() { return db; }
    public int syncIntervalSeconds() { return syncIntervalSeconds; }
    public int maxEncodedLength() { return maxEncodedLength; }
    public String defaultCategory() { return defaultCategory; }
    public int maxGive() { return maxGive; }
}
