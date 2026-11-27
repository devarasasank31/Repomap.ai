package com.repomap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies that the Flyway migration runs against a fresh database and creates
 * the initial schema expected by the application.
 */
@SpringBootTest
class DatabaseMigrationTest {

    private static final List<String> EXPECTED_TABLES =
            List.of("users", "auth_tokens", "projects", "project_files");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    void initialSchemaTablesExist() {
        for (String table : EXPECTED_TABLES) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE UPPER(table_name) = UPPER(?) "
                            + "AND UPPER(table_schema) NOT IN ('INFORMATION_SCHEMA', 'PG_CATALOG')",
                    Integer.class, table);
            assertThat(count).as("table %s should exist", table).isEqualTo(1);
        }
    }

    @Test
    void everyTableIsQueryable() {
        for (String table : EXPECTED_TABLES) {
            Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            assertThat(rows).as("%s should be readable", table).isNotNull();
        }
    }

    @Test
    void migrationVersionOneSucceeded() {
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT installed_rank, version, description, type, success "
                        + "FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(history).as("flyway history: %s", history).isNotEmpty();

        Map<String, Object> applied = jdbcTemplate.queryForMap(
                "SELECT version, description, success FROM flyway_schema_history WHERE version = '1'");
        assertThat(applied.get("version")).isEqualTo("1");
        assertThat(String.valueOf(applied.get("description"))).containsIgnoringCase("initial schema");
        assertThat(String.valueOf(applied.get("success"))).isEqualTo("true");
    }

    @Test
    void uniqueConstraintsAreEnforced() {
        jdbcTemplate.update(
                "INSERT INTO users (username, email, password_hash) VALUES ('ann', 'ann@example.com', 'hash')");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO users (username, email, password_hash) VALUES ('ann', 'other@example.com', 'hash')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
