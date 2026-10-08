package io.github.gagann06.internshiptracker;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared clean-up for integration tests. Every test class shares one Testcontainers
 * database, so each test starts by emptying it.
 */
public final class TestDatabase {

    private TestDatabase() {}

    /**
     * Empties every table the API writes to. {@code CASCADE} removes rows in the right order
     * despite the foreign keys between them.
     *
     * <p>User ids restart at 1000 while company and application ids stay low, so the two can
     * never coincide. That makes an (id, ownerId) argument swap fail loudly instead of passing
     * by accident when the numbers happen to match.
     */
    public static void clean(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE status_changes, applications, companies, users CASCADE");
        jdbc.execute("ALTER TABLE users ALTER COLUMN id RESTART WITH 1000");
    }
}
