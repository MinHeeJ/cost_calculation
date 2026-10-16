package com.sicost.excelmaps;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.PermissionService;
import com.sicost.persistence.AuditContext;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates immutable identities and atomically records each mapping change in cd_hist. */
@Service
public class ExcelMapService {
    private static final Set<String> CREATE_FIELDS = Set.of(
        "excel_lbl_nm", "trns_cd_grp_id", "trns_cd", "map_role_cd", "rmrk_desc"
    );
    private static final Set<String> PATCH_FIELDS = Set.of("map_role_cd", "rmrk_desc", "use_yn");
    private final ExcelMapMapper mapper;
    private final AuditContext audit;
    private final PermissionService permissions;

    public ExcelMapService(ExcelMapMapper mapper, AuditContext audit, PermissionService permissions) {
        this.mapper = mapper;
        this.audit = audit;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<ExcelMapRow> getExcelMaps() {
        permissions.can("UI-27.read");
        return mapper.selectAll();
    }

    /** Uses the generated map_id directly; a duplicate race is rolled back with its history. */
    @Transactional
    public void createExcelMap(JsonNode body) {
        permissions.can("UI-27.create");
        validateObject(body, CREATE_FIELDS);
        var row = new ExcelMapRow();
        row.setExcelLblNm(text(body, "excel_lbl_nm", 100, true, "엑셀 글자를 입력하세요."));
        row.setTrnsCdGrpId(text(body, "trns_cd_grp_id", 30, true, "대응할 코드를 고르세요."));
        row.setTrnsCd(text(body, "trns_cd", 30, true, "대응할 코드를 고르세요."));
        row.setMapRoleCd(choice(body, "map_role_cd", Set.of("MAIN", "SUB")));
        row.setRmrkDesc(text(body, "rmrk_desc", 200, false, null));
        row.setUseYn("Y");
        String use = mapper.selectCodeUse(row.getTrnsCdGrpId(), row.getTrnsCd());
        if (use == null) {
            throw new ExcelMapException(404, "404", "trns_cd", "대응할 코드를 찾을 수 없습니다.");
        }
        if (!"Y".equals(use)) {
            throw invalid("trns_cd", "사용 중인 코드를 고르세요.");
        }
        if (mapper.countDuplicate(row) > 0) {
            throw duplicate();
        }
        try {
            mapper.insert(row, audit.actorId());
        } catch (DuplicateKeyException error) {
            throw duplicate();
        }
        mapper.insertHistory(row, "ADD", "excel_map." + row.getMapId(), null,
            row.getExcelLblNm(), audit.actorId());
    }

    /** Omitted memo preserves its value; explicit JSON null clears it. No-op writes have no history. */
    @Transactional
    public void updateExcelMap(long id, JsonNode body) {
        permissions.can("UI-27.update");
        validateObject(body, PATCH_FIELDS);
        if (body.isEmpty()) {
            throw invalid(null, "변경할 항목을 입력하세요.");
        }
        String role = body.has("map_role_cd") ? choice(body, "map_role_cd", Set.of("MAIN", "SUB")) : null;
        String use = body.has("use_yn") ? choice(body, "use_yn", Set.of("Y", "N")) : null;
        String memo = body.has("rmrk_desc") ? text(body, "rmrk_desc", 200, false, null) : null;
        var row = mapper.selectById(id);
        if (row == null) {
            throw new ExcelMapException(404, "404", "map_id", "대응을 찾을 수 없습니다.");
        }
        String oldRole = row.getMapRoleCd();
        String oldMemo = row.getRmrkDesc();
        String oldUse = row.getUseYn();
        if (role != null) {
            row.setMapRoleCd(role);
        }
        if (body.has("rmrk_desc")) {
            row.setRmrkDesc(memo);
        }
        if (use != null) {
            permissions.can("UI-27.delete");
            row.setUseYn(use);
        }
        if (Objects.equals(oldRole, row.getMapRoleCd()) && Objects.equals(oldMemo, row.getRmrkDesc())
            && Objects.equals(oldUse, row.getUseYn())) {
            return;
        }
        if (mapper.update(row) != 1) {
            throw new ExcelMapException(404, "404", "map_id", "대응을 찾을 수 없습니다.");
        }
        history(row, "map_role_cd", oldRole, row.getMapRoleCd(), "UPDATE");
        history(row, "rmrk_desc", oldMemo, row.getRmrkDesc(), "UPDATE");
        history(row, "use_yn", oldUse, row.getUseYn(), "Y".equals(row.getUseYn()) ? "ENABLE" : "DISABLE");
    }

    private void history(ExcelMapRow row, String field, String before, String after, String type) {
        if (!Objects.equals(before, after)) {
            mapper.insertHistory(row, type, "excel_map." + row.getMapId() + "." + field,
                before, after, audit.actorId());
        }
    }

    private void validateObject(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) {
            throw invalid(null, "객체 형식으로 입력해 주세요.");
        }
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw invalid(field, "변경할 수 없는 항목입니다.");
            }
        });
    }

    private String choice(JsonNode body, String field, Set<String> allowed) {
        String value = text(body, field, 10, true, "필수 항목입니다.");
        if (!allowed.contains(value)) {
            throw invalid(field, "허용된 값을 입력해 주세요.");
        }
        return value;
    }

    private String text(JsonNode body, String field, int max, boolean required, String message) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            if (required) {
                throw invalid(field, message);
            }
            return null;
        }
        if (!node.isTextual()) {
            throw invalid(field, "문자열로 입력해 주세요.");
        }
        String value = node.textValue();
        if (required && value.isBlank()) {
            throw invalid(field, message);
        }
        if (value.codePointCount(0, value.length()) > max) {
            throw invalid(field, max + "자 이하로 입력해 주세요.");
        }
        return value;
    }

    private ExcelMapException invalid(String field, String message) {
        return new ExcelMapException(400, "400", field, message);
    }

    private ExcelMapException duplicate() {
        return new ExcelMapException(409, "DUPLICATE_CODE", "excel_lbl_nm", "이미 있는 대응입니다.");
    }
}
