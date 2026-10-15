package com.sicost.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SoftDeleteContractTest extends PostgresTestSupport {
    @Test
    void disablingCodeGroupRetainsSamePhysicalRow() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO cmn.cd_grp (cd_grp_id, cd_grp_nm)
                VALUES ('CONTRACT_FIXTURE', '격리 검증')
                """);
            assertThat(number("SELECT COUNT(*) FROM cmn.cd_grp WHERE cd_grp_id = 'CONTRACT_FIXTURE' AND use_yn = 'Y'"))
                .isEqualTo(1);
            statement.executeUpdate("""
                UPDATE cmn.cd_grp
                SET use_yn = 'N', updated_by = NULL
                WHERE cd_grp_id = 'CONTRACT_FIXTURE'
                """);
            assertThat(number("SELECT COUNT(*) FROM cmn.cd_grp WHERE cd_grp_id = 'CONTRACT_FIXTURE' AND use_yn = 'N'"))
                .isEqualTo(1);
        }
    }
}
