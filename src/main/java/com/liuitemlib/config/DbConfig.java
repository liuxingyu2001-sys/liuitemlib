package com.liuitemlib.config;

/** 数据库连接配置（sqlite 单服调试 / mysql 跨服共享）。 */
public final class DbConfig {
    public String type = "sqlite";
    public String host = "127.0.0.1";
    public int port = 3306;
    public String database = "liuitemlib";
    public String user = "root";
    public String password = "";
    public String params = "useSSL=false&characterEncoding=utf8&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true";
    public String sqliteFile = "plugins/liuitemlib/data.db";
    public String tablePrefix = "liuitemlib";
    public int poolSize = 5;
    public int connectionTimeoutMs = 5000;
    public boolean autoCreateDatabase = true;
    public boolean autoCreateTables = true;

    public boolean isSqlite() {
        return "sqlite".equalsIgnoreCase(type);
    }

    public String jdbcUrl() {
        if (isSqlite()) {
            return "jdbc:sqlite:" + sqliteFile;
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + database + "?" + params;
    }

    public String baseJdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?" + params;
    }

    public String table(String base) {
        return tablePrefix + "_" + base;
    }
}
