package com.sicost.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.assertj.core.api.Assertions.assertThat;

/** 明示された使い捨て PostgreSQL 16 のみを使用し、未設定時は接続前に skip する。 */
abstract class PostgresTestSupport {
    private static String url;
    private static String user;
    private static String password;
    protected Connection connection;

    @BeforeAll
    static void migrateDedicatedDatabase() throws Exception {
        url = System.getenv("TEST_DATABASE_URL");
        assumeTrue(url != null && !url.isBlank(), "TEST_DATABASE_URL absent; selected-engine checks deferred");
        user = System.getenv().getOrDefault("TEST_DATABASE_USER", "sicost");
        password = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "dev-only-password");
        try (var check = DriverManager.getConnection(url, user, password)) {
            assertThat(check.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
        }
        Flyway.configure().dataSource(url, user, password).load().migrate();
    }

    @BeforeEach
    void connect() throws SQLException {
        connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(false);
    }

    @AfterEach
    void rollback() throws SQLException {
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
    }

    protected long number(String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
