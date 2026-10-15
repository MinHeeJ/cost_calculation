package com.sicost.persistence;

import com.sicost.SiCostApplication;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SiCostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PersistenceStartupTest {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private SqlSessionFactory sessions;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () ->
            System.getenv().getOrDefault("TEST_DATABASE_USER", "sicost"));
        registry.add("spring.datasource.password", () ->
            System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "dev-only-password"));
    }

    @Test
    void startsImperativeMyBatisAfterFlywayOnPostgres16() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
            try (var sql = connection.createStatement(); var result = sql.executeQuery("""
                SELECT to_regclass('sys.menu') IS NOT NULL
                    AND to_regclass('fin.mth_cls') IS NOT NULL
                """)) {
                result.next();
                assertThat(result.getBoolean(1)).isTrue();
            }
        }
        assertThat(sessions.getConfiguration().getEnvironment().getDataSource()).isSameAs(dataSource);
    }
}
