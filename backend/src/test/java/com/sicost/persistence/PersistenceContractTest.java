package com.sicost.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PersistenceContractTest extends PostgresTestSupport {
    @Test
    void menuIdentityAndAuditDefaultsRespectDatabaseTrigger() throws Exception {
        try (var insert = connection.prepareStatement("""
            INSERT INTO sys.menu (menu_nm, created_by, updated_by)
            VALUES ('격리 감사 검증', NULL, NULL)
            RETURNING menu_id, created_at, created_by, updated_by
            """)) {
            var row = insert.executeQuery();
            row.next();
            long id = row.getLong("menu_id");
            assertThat(id).isPositive();
            assertThat(row.getTimestamp("created_at")).isNotNull();
            assertThat(row.getObject("created_by")).isNull();
            assertThat(row.getObject("updated_by")).isNull();
            try (var update = connection.prepareStatement("""
                UPDATE sys.menu
                SET menu_nm = '감사 변경', updated_at = TIMESTAMP '2000-01-01 00:00:00', updated_by = NULL
                WHERE menu_id = ?
                RETURNING updated_at, updated_by
                """)) {
                update.setLong(1, id);
                var changed = update.executeQuery();
                changed.next();
                assertThat(changed.getTimestamp("updated_at").toLocalDateTime().getYear()).isGreaterThan(2000);
                assertThat(changed.getObject("updated_by")).isNull();
            }
        }
    }

    @Test
    void monthUsesNaturalKeyWithoutInventedAuditColumnsAndSelectDoesNotMutate() throws Exception {
        assertThat(number("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = 'fin' AND table_name = 'mth_cls'
              AND column_name IN ('created_at','created_by','updated_at','updated_by','id')
            """)).isZero();
        String sql = "SELECT md5(string_agg(row_to_json(m)::text, '' ORDER BY m.cls_ym)) FROM fin.mth_cls m";
        String before;
        try (var statement = connection.createStatement(); var row = statement.executeQuery(sql)) {
            row.next();
            before = row.getString(1);
        }
        assertThat(number("SELECT COUNT(*) FROM fin.mth_cls WHERE cls_ym BETWEEN '2026-01' AND '2026-10'"))
            .isEqualTo(10);
        try (var statement = connection.createStatement(); var row = statement.executeQuery(sql)) {
            row.next();
            assertThat(row.getString(1)).isEqualTo(before);
        }
    }
}
