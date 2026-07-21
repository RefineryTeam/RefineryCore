package xyz.refineryteam.refinerycore.api.database.dialect;

import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.database.DatabaseType;

/**
 * Encapsulates the small syntax differences between supported {@link DatabaseType}s
 * that a {@link xyz.refineryteam.refinerycore.api.database.repository.Repository}
 * needs to generate correct SQL for each backend (upserts, identity columns, quoting).
 */
public enum SqlDialect {

    MYSQL(DatabaseType.MYSQL) {
        @Override
        public String identityColumnDefinition() {
            return "BIGINT AUTO_INCREMENT PRIMARY KEY";
        }

        @Override
        public String upsert(String table, String[] columns, String[] updateColumns) {
            String insert = insert(table, columns);
            StringBuilder sql = new StringBuilder(insert).append(" ON DUPLICATE KEY UPDATE ");
            for (int i = 0; i < updateColumns.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append(quote(updateColumns[i])).append(" = VALUES(").append(quote(updateColumns[i])).append(")");
            }
            return sql.toString();
        }

        @Override
        public String quote(String identifier) {
            return "`" + identifier + "`";
        }
    },

    SQLITE(DatabaseType.SQLITE) {
        @Override
        public String identityColumnDefinition() {
            return "INTEGER PRIMARY KEY AUTOINCREMENT";
        }

        @Override
        public String upsert(String table, String[] columns, String[] updateColumns) {
            return sqliteStyleUpsert(table, columns, updateColumns);
        }

        @Override
        public String quote(String identifier) {
            return "\"" + identifier + "\"";
        }
    },

    H2(DatabaseType.H2) {
        @Override
        public String identityColumnDefinition() {
            return "BIGINT AUTO_INCREMENT PRIMARY KEY";
        }

        @Override
        public String upsert(String table, String[] columns, String[] updateColumns) {
            // H2 in MySQL compatibility mode (see RefineryDatabase#h2) understands
            // ON DUPLICATE KEY UPDATE just like MySQL does.
            return MYSQL.upsert(table, columns, updateColumns);
        }

        @Override
        public String quote(String identifier) {
            return "\"" + identifier + "\"";
        }
    };

    private final DatabaseType type;

    SqlDialect(DatabaseType type) {
        this.type = type;
    }

    public static @NonNull SqlDialect of(DatabaseType type) {
        for (SqlDialect dialect : values()) {
            if (dialect.type == type) return dialect;
        }
        throw new IllegalArgumentException("No dialect for database type: " + type);
    }

    public DatabaseType type() {
        return type;
    }

    public String tableExistsQuery() {
        return switch (this) {
            case MYSQL -> "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
            case SQLITE -> "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?";
            case H2 -> "SELECT 1 FROM information_schema.tables WHERE table_name = ?";
        };
    }

    public String limitOffset(int limit, int offset) {
        return " LIMIT " + limit + " OFFSET " + offset;
    }

    public String insert(String table, String @NonNull [] columns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(quote(table)).append(" (");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) sql.append(", ");
            sql.append(quote(columns[i]));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(")");
        return sql.toString();
    }

    /**
     * SQLite's upsert syntax (`INSERT ... ON CONFLICT (...) DO UPDATE SET ...`) needs an
     * explicit conflict target, which the default repository always resolves to the
     * primary key column, so this helper is shared by dialects that follow that grammar.
     */
    protected String sqliteStyleUpsert(String table, String @NonNull [] columns, String[] updateColumns) {
        String primaryKey = columns[0];
        StringBuilder sql = new StringBuilder(insert(table, columns))
                .append(" ON CONFLICT(").append(quote(primaryKey)).append(") DO UPDATE SET ");
        for (int i = 0; i < updateColumns.length; i++) {
            if (i > 0) sql.append(", ");
            sql.append(quote(updateColumns[i])).append(" = excluded.").append(quote(updateColumns[i]));
        }
        return sql.toString();
    }

    public abstract String identityColumnDefinition();

    public abstract String upsert(String table, String[] columns, String[] updateColumns);

    public abstract String quote(String identifier);
}