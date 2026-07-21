package xyz.refineryteam.refinerycore.api.database.repository;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps a single {@link ResultSet} row to an entity. Unlike
 * {@link java.util.function.Function}, this is allowed to throw {@link SQLException}
 * directly since almost every {@link ResultSet} accessor does.
 */
@FunctionalInterface
public interface RowMapper<T> {
    T map(ResultSet rs) throws SQLException;
}