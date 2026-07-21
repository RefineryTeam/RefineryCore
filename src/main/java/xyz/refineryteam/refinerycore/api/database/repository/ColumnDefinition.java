package xyz.refineryteam.refinerycore.api.database.repository;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

/**
 * Describes one non-primary-key column of a repository-backed table: its name and its raw
 * SQL type/constraints (e.g. {@code "VARCHAR(255) NOT NULL"}), used verbatim in
 * {@code CREATE TABLE}.
 */
public record ColumnDefinition(String name, String sqlType) {
    @Contract("_, _ -> new")
    public static @NonNull ColumnDefinition of(String name, String sqlType) {
        return new ColumnDefinition(name, sqlType);
    }
}