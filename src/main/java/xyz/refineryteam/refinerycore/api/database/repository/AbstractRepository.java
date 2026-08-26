package xyz.refineryteam.refinerycore.api.database.repository;

import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.database.RefineryDatabase;
import xyz.refineryteam.refinerycore.api.database.dialect.SqlDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

import org.jspecify.annotations.Nullable;

/**
 * Base {@link Repository} implementation shared by every entity type. Subclasses only need
 * to describe their table's shape (name, primary key column, extra columns) and how to
 * bind/read an entity — everything else (table creation, upserts, lookups) is generated
 * once here in a dialect-correct way for whichever {@link RefineryDatabase} the repository
 * was built with.
 * <p>
 * This is intentionally table-per-entity rather than a fully generic ORM: it stays simple
 * and predictable for plugin-sized schemas rather than trying to model relations, joins, or
 * migrations.
 *
 * @param <T>  the entity type
 * @param <ID> the primary key type
 */
public abstract class AbstractRepository<T, ID> implements Repository<T, ID> {

    protected final RefineryDatabase database;
    protected final SqlDialect dialect;
    protected final String table;
    protected final String idColumn;
    protected final List<ColumnDefinition> columns;

    protected AbstractRepository(@NonNull RefineryDatabase database, @NonNull String table, @NonNull String idColumn,
                                  @NonNull List<ColumnDefinition> columns) {
        this.database = database;
        this.dialect = SqlDialect.of(database.getType());
        this.table = table;
        this.idColumn = idColumn;
        this.columns = columns;
    }

    /** SQL type (including PRIMARY KEY where relevant) used for the id column in CREATE TABLE. */
    protected abstract String idColumnDefinition();

    /** Maps a single result row to an entity instance. */
    protected abstract T mapRow(ResultSet rs) throws SQLException;

    /**
     * Binds every column (id first, then each of {@link #columns} in order) of the given
     * entity onto the statement, starting at parameter index 1. Used both for inserts and
     * for the values half of an upsert.
     */
    protected abstract void bindAll(PreparedStatement stmt, T entity) throws SQLException;

    /** Binds just the id, e.g. for WHERE/DELETE clauses. */
    protected abstract void bindId(PreparedStatement stmt, int index, ID id) throws SQLException;

    private String @NonNull [] columnNames() {
        String[] names = new String[columns.size() + 1];
        names[0] = idColumn;
        for (int i = 0; i < columns.size(); i++) names[i + 1] = columns.get(i).name();
        return names;
    }

    private String @NonNull [] updateColumnNames() {
        String[] names = new String[columns.size()];
        for (int i = 0; i < columns.size(); i++) names[i] = columns.get(i).name();
        return names;
    }

    @Override
    public void createTable() {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(dialect.quote(table)).append(" (")
                .append(dialect.quote(idColumn)).append(" ").append(idColumnDefinition());
        for (ColumnDefinition column : columns) {
            sql.append(", ").append(dialect.quote(column.name())).append(" ").append(column.sqlType());
        }
        sql.append(")");

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            database.getPlugin().getLogger().log(Level.SEVERE, "Failed to create table " + table, e);
        }
    }

    @Override
    public @NonNull Optional<T> findById(@NonNull ID id) {
        String sql = "SELECT * FROM " + dialect.quote(table) + " WHERE " + dialect.quote(idColumn) + " = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindId(stmt, 1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            database.getPlugin().getLogger().log(Level.SEVERE, "Failed to query " + table + " by id", e);
        }
        return Optional.empty();
    }

    @Override
    public @NonNull List<T> findAll() {
        List<T> results = new ArrayList<>();
        String sql = "SELECT * FROM " + dialect.quote(table);
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) results.add(mapRow(rs));
        } catch (SQLException e) {
            database.getPlugin().getLogger().log(Level.SEVERE, "Failed to query all rows from " + table, e);
        }
        return results;
    }

    /** Convenience for subclasses/callers needing pagination without hand-writing LIMIT/OFFSET. */
    public @NonNull List<T> findPage(int limit, int offset) {
        List<T> results = new ArrayList<>();
        String sql = "SELECT * FROM " + dialect.quote(table) + dialect.limitOffset(limit, offset);
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) results.add(mapRow(rs));
        } catch (SQLException e) {
            database.getPlugin().getLogger().log(Level.SEVERE, "Failed to query page from " + table, e);
        }
        return results;
    }

    @Override
    public T save(@NonNull T entity) {
        String sql = dialect.upsert(table, columnNames(), updateColumnNames());
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindAll(stmt, entity);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Propagate instead of returning as if the write succeeded —
            // silent data loss is worse than a loud failure.
            throw new IllegalStateException("Failed to save row in " + table, e);
        }
        return entity;
    }

    /**
     * Same as {@link #save(Object)} but logs failures instead of throwing.
     * Use for fire-and-forget writes where losing one row is acceptable.
     */
    public @Nullable T saveQuietly(@NonNull T entity) {
        try {
            return save(entity);
        } catch (IllegalStateException e) {
            database.getPlugin().getLogger().log(Level.SEVERE, e.getMessage(), e.getCause());
            return null;
        }
    }

    @Override
    public void deleteById(@NonNull ID id) {
        String sql = "DELETE FROM " + dialect.quote(table) + " WHERE " + dialect.quote(idColumn) + " = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindId(stmt, 1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            database.getPlugin().getLogger().log(Level.SEVERE, "Failed to delete row from " + table, e);
        }
    }

    @Override
    public boolean existsById(@NonNull ID id) {
        String sql = "SELECT 1 FROM " + dialect.quote(table) + " WHERE " + dialect.quote(idColumn) + " = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindId(stmt, 1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            database.getPlugin().getLogger().log(Level.SEVERE, "Failed to check existence in " + table, e);
        }
        return false;
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM " + dialect.quote(table);
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            database.getPlugin().getLogger().log(Level.SEVERE, "Failed to count rows in " + table, e);
        }
        return 0L;
    }

    /**
     * Runs an arbitrary read/write against the repository's connection for cases the
     * standard CRUD methods don't cover (custom WHERE clauses, joins across repositories
     * sharing the same database, etc).
     */
    protected void withConnection(@NonNull ConnectionAction action) {
        try (Connection conn = database.getConnection()) {
            action.run(conn);
        } catch (SQLException e) {
            database.getPlugin().getLogger().log(Level.SEVERE, "Failed to open connection for " + table, e);
        }
    }

    @FunctionalInterface
    protected interface ConnectionAction {
        void run(Connection connection) throws SQLException;
    }
}