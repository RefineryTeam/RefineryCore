package xyz.refineryteam.refinerycore.api.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * A persistent, connection-pooled (via HikariCP) SQL database backing a plugin.
 * <p>
 * For entity access, prefer building a
 * {@link xyz.refineryteam.refinerycore.api.database.repository.Repository} on top of this
 * rather than writing raw SQL against {@link #execute} / {@link #query} directly — the
 * repository layer generates dialect-correct SQL automatically for whichever
 * {@link DatabaseType} this database was opened as.
 * <p>
 * For data that doesn't need to survive a restart, see
 * {@link xyz.refineryteam.refinerycore.api.storage.TemporaryStorage} instead of using this
 * class with a throwaway file.
 */
public final class RefineryDatabase {

    @Getter
    private final JavaPlugin plugin;
    @Getter
    private final DatabaseType type;
    private HikariDataSource source;

    private RefineryDatabase(JavaPlugin plugin, DatabaseType type) {
        this.plugin = plugin;
        this.type = type;
    }

    public static @NonNull RefineryDatabase sqlite(JavaPlugin plugin, String fileName) {
        RefineryDatabase db = new RefineryDatabase(plugin, DatabaseType.SQLITE);
        HikariConfig config = new HikariConfig();
        File file = new File(plugin.getDataFolder(), fileName);
        file.getParentFile().mkdirs();
        config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName(plugin.getName() + "-sqlite");
        db.source = new HikariDataSource(config);
        return db;
    }

    public static @NonNull RefineryDatabase h2(JavaPlugin plugin, String fileName) {
        RefineryDatabase db = new RefineryDatabase(plugin, DatabaseType.H2);
        HikariConfig config = new HikariConfig();
        File file = new File(plugin.getDataFolder(), fileName);
        file.getParentFile().mkdirs();
        config.setJdbcUrl("jdbc:h2:" + file.getAbsolutePath() + ";MODE=MySQL");
        config.setDriverClassName("org.h2.Driver");
        config.setMaximumPoolSize(4);
        config.setPoolName(plugin.getName() + "-h2");
        db.source = new HikariDataSource(config);
        return db;
    }

    public static @NonNull RefineryDatabase mysql(JavaPlugin plugin, String host, int port, String database, String username, String password) {
        RefineryDatabase db = new RefineryDatabase(plugin, DatabaseType.MYSQL);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true&characterEncoding=utf8");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setPoolName(plugin.getName() + "-mysql");
        db.source = new HikariDataSource(config);
        return db;
    }

    public Connection getConnection() throws SQLException {
        return source.getConnection();
    }

    public void execute(@NonNull String sql, @NonNull Consumer<PreparedStatement> prepare) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            prepare.accept(stmt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to execute statement: " + sql, e);
        }
    }

    @Contract("_, _ -> new")
    public @NonNull CompletableFuture<Void> executeAsync(@NonNull String sql, @NonNull Consumer<PreparedStatement> prepare) {
        return CompletableFuture.runAsync(() -> execute(sql, prepare));
    }

    public <T> @NonNull List<T> query(@NonNull String sql, @NonNull Consumer<PreparedStatement> prepare, @NonNull Function<ResultSet, T> mapper) {
        List<T> results = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            prepare.accept(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) results.add(mapper.apply(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to execute query: " + sql, e);
        }
        return results;
    }

    @Contract("_, _, _ -> new")
    public <T> @NonNull CompletableFuture<List<T>> queryAsync(@NonNull String sql, @NonNull Consumer<PreparedStatement> prepare, @NonNull Function<ResultSet, T> mapper) {
        return CompletableFuture.supplyAsync(() -> query(sql, prepare, mapper));
    }

    public void close() {
        if (source != null && !source.isClosed()) source.close();
    }
}