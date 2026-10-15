package com.sicost.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.persistence.AuditContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CurrentContextTest {
    private final CurrentUser user = new CurrentUser();

    @Test
    void fixedContextIsNotAnAuthenticatedAccount() {
        assertThat(user.userId()).isNull();
        assertThat(user.name()).isEqualTo("기본 사용자");
        assertThat(user.roleIds()).isEqualTo(List.of(1L));
        assertThat(user.dataScope()).isEqualTo(DataScope.ALL);
        assertThat(new AuditContext(user).actorId()).isNull();
        assertThat(DataScope.values())
            .containsExactly(DataScope.ALL, DataScope.PART, DataScope.PROJECT, DataScope.SELF);
    }

    @Test
    void allFunctionKeysRemainAllowedWithOriginalDatabaseColumns() {
        assertThat(PermissionService.COLUMNS).isEqualTo(Map.of(
            "read", "inq_auth_yn", "create", "reg_auth_yn", "update", "mdfcn_auth_yn",
            "delete", "del_auth_yn", "upload", "upld_auth_yn", "download", "dwnld_auth_yn",
            "confirm", "cnfrm_auth_yn"
        ));
        PermissionService permissions = new PermissionService();
        PermissionService.COLUMNS.keySet().forEach(action ->
            assertThat(permissions.can("UI-27." + action)).isTrue());
    }

    record SensitiveValue(@Sensitive Long amount) { }

    @Test
    void sensitiveMetadataDoesNotMaskResponses() throws Exception {
        assertThat(new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(new SensitiveValue(120L)))
            .get("amount").asLong()).isEqualTo(120L);
    }
}
