package com.liuitemlib.db;

import com.liuitemlib.config.DbConfig;
import com.liuitemlib.item.LibraryItem;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 物品库存储层。
 * MySQL 跨服共享：revision 全局自增，其它服按 revision > lastSeen 增量拉取；
 * 写入用 WHERE revision = ? 做乐观锁，避免两台服同时改同一条时静默覆盖。
 */
public final class DatabaseManager implements AutoCloseable {
    private static final String REVISION_KEY = "revision";

    private final DbConfig cfg;
    private final Logger logger;
    private HikariDataSource ds;

    public DatabaseManager(DbConfig cfg, Logger logger) {
        this.cfg = cfg;
        this.logger = logger;
    }

    public void init() throws Exception {
        Class.forName(cfg.isSqlite() ? "org.sqlite.JDBC" : "com.mysql.cj.jdbc.Driver");
        if (!cfg.isSqlite() && cfg.autoCreateDatabase) {
            createDatabase();
        }
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.jdbcUrl());
        if (!cfg.isSqlite()) {
            hc.setUsername(cfg.user);
            hc.setPassword(cfg.password);
        }
        // SQLite 单连接串行写入，避免 database is locked
        hc.setMaximumPoolSize(cfg.isSqlite() ? 1 : cfg.poolSize);
        hc.setMinimumIdle(cfg.isSqlite() ? 1 : Math.min(2, cfg.poolSize));
        hc.setConnectionTimeout(cfg.connectionTimeoutMs);
        hc.setPoolName("liuitemlib");
        ds = new HikariDataSource(hc);
        if (cfg.autoCreateTables) {
            createTables();
        }
    }

    private void createDatabase() throws SQLException {
        try (Connection c = DriverManager.getConnection(cfg.baseJdbcUrl(), cfg.user, cfg.password);
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + cfg.database
                    + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private void createTables() throws SQLException {
        String items = cfg.table("items");
        String meta = cfg.table("meta");
        String dataType = cfg.isSqlite() ? "TEXT" : "MEDIUMTEXT";
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS `" + meta + "` ("
                    + "`k` VARCHAR(32) NOT NULL PRIMARY KEY,"
                    + "`v` BIGINT NOT NULL DEFAULT 0)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS `" + items + "` ("
                    + "`id` VARCHAR(64) NOT NULL PRIMARY KEY,"
                    + "`revision` BIGINT NOT NULL,"
                    + "`display_name` VARCHAR(128) NOT NULL DEFAULT '',"
                    + "`category` VARCHAR(32) NOT NULL DEFAULT 'default',"
                    + "`tags` VARCHAR(255) NOT NULL DEFAULT '',"
                    + "`material` VARCHAR(48) NOT NULL DEFAULT '',"
                    + "`data` " + dataType + " NOT NULL,"
                    + "`data_hash` CHAR(32) NOT NULL DEFAULT '',"
                    + "`created_by` VARCHAR(64) NOT NULL DEFAULT '',"
                    + "`created_at` BIGINT NOT NULL DEFAULT 0,"
                    + "`updated_by` VARCHAR(64) NOT NULL DEFAULT '',"
                    + "`updated_at` BIGINT NOT NULL DEFAULT 0,"
                    + "`deleted` TINYINT NOT NULL DEFAULT 0)");
            try {
                // MySQL 不支持 CREATE INDEX IF NOT EXISTS，重复建索引报错忽略
                st.executeUpdate("CREATE INDEX `idx_" + items + "_rev` ON `" + items + "` (`revision`)");
            } catch (SQLException ignored) {
                // 索引已存在
            }
        }
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO `" + meta + "` (`k`,`v`) VALUES ('" + REVISION_KEY + "',0)");
        } catch (SQLException ignored) {
            // 已存在主键，忽略
        }
    }

    /** 全局最大 revision（同步轮询用，走索引）。 */
    public long maxRevision() throws SQLException {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(`revision`),0) FROM `" + cfg.table("items") + "`")) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /** 全量加载未删除条目（启动/重载）。 */
    public List<LibraryItem> loadAll() throws SQLException {
        return query("SELECT * FROM `" + cfg.table("items") + "` WHERE `deleted` = 0");
    }

    /** 增量拉取（含已删除标记，供其他服剔除缓存）。 */
    public List<LibraryItem> changedSince(long revision) throws SQLException {
        String sql = "SELECT * FROM `" + cfg.table("items") + "` WHERE `revision` > ? ORDER BY `revision` ASC";
        List<LibraryItem> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, revision);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public LibraryItem find(String id) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM `" + cfg.table("items") + "` WHERE `id` = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** 原子占用下一个全局 revision。 */
    public long nextRevision() throws SQLException {
        String meta = cfg.table("meta");
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE `" + meta + "` SET `v` = `v` + 1 WHERE `k` = ?")) {
                ps.setString(1, REVISION_KEY);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT `v` FROM `" + meta + "` WHERE `k` = ?")) {
                ps.setString(1, REVISION_KEY);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("meta.revision 缺失");
                    }
                    return rs.getLong(1);
                }
            }
        }
    }

    public void insert(LibraryItem item) throws SQLException {
        String sql = "INSERT INTO `" + cfg.table("items") + "` (`id`,`revision`,`display_name`,`category`,`tags`,"
                + "`material`,`data`,`data_hash`,`created_by`,`created_at`,`updated_by`,`updated_at`,`deleted`)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, item);
            ps.executeUpdate();
        }
    }

    /** 乐观锁更新；返回 false 表示 revision 已被其他服/其他操作改过。 */
    public boolean update(long expectedRevision, LibraryItem item) throws SQLException {
        String sql = "UPDATE `" + cfg.table("items") + "` SET `revision`=?,`display_name`=?,`category`=?,`tags`=?,"
                + "`material`=?,`data`=?,`data_hash`=?,`updated_by`=?,`updated_at`=?,`deleted`=?"
                + " WHERE `id`=? AND `revision`=?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setLong(i++, item.revision());
            ps.setString(i++, item.displayName());
            ps.setString(i++, item.category());
            ps.setString(i++, item.tags());
            ps.setString(i++, item.material());
            ps.setString(i++, item.data());
            ps.setString(i++, item.dataHash());
            ps.setString(i++, item.updatedBy());
            ps.setLong(i++, item.updatedAt());
            ps.setInt(i++, item.deleted() ? 1 : 0);
            ps.setString(i++, item.id());
            ps.setLong(i, expectedRevision);
            return ps.executeUpdate() > 0;
        }
    }

    private List<LibraryItem> query(String sql) throws SQLException {
        List<LibraryItem> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(map(rs));
            }
        }
        return out;
    }

    private static void bind(PreparedStatement ps, LibraryItem item) throws SQLException {
        int i = 1;
        ps.setString(i++, item.id());
        ps.setLong(i++, item.revision());
        ps.setString(i++, item.displayName());
        ps.setString(i++, item.category());
        ps.setString(i++, item.tags());
        ps.setString(i++, item.material());
        ps.setString(i++, item.data());
        ps.setString(i++, item.dataHash());
        ps.setString(i++, item.createdBy());
        ps.setLong(i++, item.createdAt());
        ps.setString(i++, item.updatedBy());
        ps.setLong(i++, item.updatedAt());
        ps.setInt(i, item.deleted() ? 1 : 0);
    }

    private static LibraryItem map(ResultSet rs) throws SQLException {
        return new LibraryItem(
                rs.getString("id"),
                rs.getLong("revision"),
                rs.getString("display_name"),
                rs.getString("category"),
                rs.getString("tags"),
                rs.getString("material"),
                rs.getString("data"),
                rs.getString("data_hash"),
                rs.getString("created_by"),
                rs.getLong("created_at"),
                rs.getString("updated_by"),
                rs.getLong("updated_at"),
                rs.getInt("deleted") != 0);
    }

    public void close() {
        if (ds != null) {
            ds.close();
            ds = null;
        }
    }
}
