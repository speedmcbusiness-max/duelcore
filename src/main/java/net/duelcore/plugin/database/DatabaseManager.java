package net.duelcore.plugin.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.duelcore.plugin.DuelCorePlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final DuelCorePlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(DuelCorePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        FileConfiguration cfg = plugin.getConfig();
        String host = cfg.getString("database.host", "127.0.0.1");
        int port = cfg.getInt("database.port", 3306);
        String name = cfg.getString("database.name", "duelcore");
        String user = cfg.getString("database.username", "duelcore");
        String pass = cfg.getString("database.password", "");
        int poolSize = cfg.getInt("database.pool-size", 10);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name
                + "?useSSL=false&autoReconnect=true&characterEncoding=utf8");
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(pass);
        hikariConfig.setMaximumPoolSize(poolSize);
        hikariConfig.setPoolName("DuelCore-Pool");
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            this.dataSource = new HikariDataSource(hikariConfig);
            try (Connection ignored = dataSource.getConnection()) {
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Database connection failed: " + e.getMessage());
            return false;
        }
    }

    /** Runs schema.sql (idempotent CREATE TABLE IF NOT EXISTS statements) on startup. */
    public void runSchema() {
        try (InputStream in = plugin.getResource("schema.sql")) {
            if (in == null) {
                plugin.getLogger().warning("schema.sql not found in jar resources.");
                return;
            }
            StringBuilder sql = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("--") || line.isBlank()) continue;
                    sql.append(line).append('\n');
                }
            }
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                for (String statement : sql.toString().split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }
            }
        } catch (IOException | SQLException e) {
            plugin.getLogger().severe("Failed to run schema.sql: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
