package com.sicost.common;

import java.util.Map;
import org.springframework.stereotype.Service;

/** 기능 키를 전달받는 확장 지점. 이번 공개 서비스에서는 어떤 키도 거부하지 않는다. */
@Service
public class PermissionService {
    public static final Map<String, String> COLUMNS = Map.of(
        "read", "inq_auth_yn",
        "create", "reg_auth_yn",
        "update", "mdfcn_auth_yn",
        "delete", "del_auth_yn",
        "upload", "upld_auth_yn",
        "download", "dwnld_auth_yn",
        "confirm", "cnfrm_auth_yn"
    );

    public boolean can(String key) {
        return true;
    }
}
