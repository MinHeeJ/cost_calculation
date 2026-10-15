package com.sicost.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

class MigrationContractTest extends PostgresTestSupport {
    @Test
    void migratesAllBusinessSchemasAndSeeds() throws Exception {
        assertThat(number("""
            SELECT COUNT(*) FROM information_schema.schemata
            WHERE schema_name IN ('cmn','sys','mst','bsn','prj','asgn','hr','cst','bill','fin','ai')
            """)).isEqualTo(11);
        assertThat(number("""
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_type = 'BASE TABLE'
              AND table_schema IN ('cmn','sys','mst','bsn','prj','asgn','hr','cst','bill','fin','ai')
            """)).isEqualTo(80);
        for (var expected : Map.of(
            "cmn.cd_grp", 36, "cmn.cd_dtl", 189, "sys.role", 5,
            "sys.menu", 41, "sys.role_menu_auth", 155, "fin.mth_cls", 10
        ).entrySet()) {
            assertThat(number("SELECT COUNT(*) FROM " + expected.getKey())).isEqualTo(expected.getValue());
        }
        assertThat(number("""
            SELECT COUNT(*) FROM fin.mth_cls
            WHERE cls_ym = '2026-09' AND cls_at = TIMESTAMP '2026-10-05 00:00:00'
            """)).isEqualTo(1);
        assertThat(number("""
            SELECT COUNT(*) FROM fin.mth_cls
            WHERE cls_ym = '2026-10' AND cls_stts_cd = 'OPEN' AND cls_at IS NULL
              AND cls_user_id IS NULL AND rls_user_id IS NULL AND rls_at IS NULL AND rls_rsn_desc IS NULL
            """)).isEqualTo(1);
    }
}
