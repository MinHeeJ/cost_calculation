package com.sicost.codegroups;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.BusinessException;
import com.sicost.common.PermissionService;
import com.sicost.persistence.AuditContext;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 전체 집합 검증 후 코드와 항목별 이력을 원자 저장하며 기존 업무 참조를 변경하지 않는다. */
@Service
@Transactional(readOnly = true)
public class CodeGroupService {
    private final CodeGroupMapper mapper;
    private final AuditContext audit;
    private final PermissionService permissions;
    private static final Set<String> CODE_FIELDS = Set.of(
        "dtl_cd", "cd_nm", "sort_seq", "use_yn", "attr1_val", "attr2_val", "attr3_val", "aply_bgn_dt"
    );

    public CodeGroupService(CodeGroupMapper mapper, AuditContext audit, PermissionService permissions) {
        this.mapper = mapper;
        this.audit = audit;
        this.permissions = permissions;
    }

    public List<CodeGroupRow> getCodeGroups() {
        permissions.can("UI-27.read");
        return mapper.selectGroups();
    }

    public List<AdminCodeRow> getAdminCodes(String group) {
        permissions.can("UI-27.read");
        requireGroup(group);
        return mapper.selectCodes(group);
    }

    /** BIZ 그룹과 ADD 이력은 동일 트랜잭션이며 순서는 DB 전체 그룹의 최대값 다음이다. */
    @Transactional
    public void createCodeGroup(JsonNode body) {
        permissions.can("UI-27.create");
        object(body, Set.of("cd_grp_id", "cd_grp_nm", "attr1_nm", "attr2_nm", "attr3_nm"));
        String id = text(body, "cd_grp_id", 30, true, "그룹 코드를 입력하세요.");
        if (!id.matches("[A-Z_]{1,30}")) invalid("cd_grp_id", "그룹 코드는 영문 대문자와 _만 입력하세요.");
        var row = new CodeGroupRow();
        row.setCdGrpId(id);
        row.setCdGrpNm(text(body, "cd_grp_nm", 100, true, "그룹명을 입력하세요."));
        row.setAttr1Nm(text(body, "attr1_nm", 50, false, ""));
        row.setAttr2Nm(text(body, "attr2_nm", 50, false, ""));
        row.setAttr3Nm(text(body, "attr3_nm", 50, false, ""));
        if (mapper.selectGroup(id) != null) duplicate();
        try {
            mapper.insertGroup(row, audit.actorId());
        } catch (DuplicateKeyException error) {
            duplicate();
        }
        history(id, null, "ADD", null, null, row.getCdGrpNm());
    }

    /** 생략 필드는 유지하고 명시 null은 비운다. 모든 행을 검증하기 전에는 쓰지 않는다. */
    @Transactional
    public void saveCodes(String group, JsonNode body) {
        permissions.can("UI-27.update");
        CodeGroupRow parent = requireGroup(group);
        object(body, Set.of("codes"));
        JsonNode input = body.get("codes");
        if (input == null || !input.isArray()) invalid("codes", "전체 코드 목록을 입력하세요.");
        Map<String, AdminCodeRow> existing = new HashMap<>();
        mapper.selectCodes(group).forEach(row -> existing.put(row.getDtlCd(), row));
        Set<String> ids = new HashSet<>();
        List<Mutation> mutations = new ArrayList<>();
        boolean system = "SYSTEM".equals(parent.getCdGrpSeCd());
        for (JsonNode raw : input) {
            SaveCodeRow request = new SaveCodeRow(raw);
            JsonNode fields = request.fields();
            object(fields, CODE_FIELDS);
            String id = text(fields, "dtl_cd", 30, true, "코드를 입력하세요.");
            if (!id.matches("[A-Z0-9_]{1,30}")) invalid("dtl_cd", "코드 형식을 확인하세요.");
            if (!ids.add(id)) duplicate();
            AdminCodeRow before = existing.get(id);
            AdminCodeRow after = row(group, fields, before);
            Map<String, Object> changes = differences(before, after);
            if (system) {
                if (before != null && changes.containsKey("use_yn")) {
                    locked("시스템 코드는 사용 중지할 수 없습니다.");
                }
                if (before == null || changes.keySet().stream()
                    .anyMatch(key -> !Set.of("cd_nm", "sort_seq").contains(key))) {
                    locked("시스템 코드는 이름과 순서만 바꿀 수 있습니다.");
                }
            }
            mutations.add(new Mutation(before, after, changes));
        }
        if (!ids.containsAll(existing.keySet())) invalid("codes", "기존 코드를 목록에서 제외할 수 없습니다.");
        for (Mutation mutation : mutations) {
            var after = mutation.after();
            if (mutation.before() == null) {
                permissions.can("UI-27.create");
                after.setUseYn("Y");
                try {
                    mapper.insertCode(after, audit.actorId());
                } catch (DuplicateKeyException error) {
                    duplicate();
                }
                history(group, after.getDtlCd(), "ADD", null, null, after.getCdNm());
            } else if (!mutation.changes().isEmpty()) {
                if (mutation.changes().containsKey("use_yn")) permissions.can("UI-27.delete");
                mapper.updateCode(after, mutation.changes(), audit.actorId());
                Map<String, Object> old = values(mutation.before());
                for (var change : mutation.changes().entrySet()) {
                    String type = "use_yn".equals(change.getKey())
                        ? ("Y".equals(change.getValue()) ? "ENABLE" : "DISABLE") : "UPDATE";
                    history(group, after.getDtlCd(), type, change.getKey(),
                        string(old.get(change.getKey())), string(change.getValue()));
                }
            }
        }
    }

    private AdminCodeRow row(String group, JsonNode f, AdminCodeRow old) {
        var result = new AdminCodeRow();
        result.setCdGrpId(group);
        result.setDtlCd(f.get("dtl_cd").asText());
        result.setCdNm(text(f, "cd_nm", 100, true, "이름을 입력하세요."));
        if (!f.has("sort_seq") || !f.get("sort_seq").isIntegralNumber()
            || !f.get("sort_seq").canConvertToInt()) invalid("sort_seq", "정수를 입력하세요.");
        result.setSortSeq(f.get("sort_seq").intValue());
        String use = text(f, "use_yn", 1, true, "사용 여부를 입력하세요.");
        if (!Set.of("Y", "N").contains(use)) invalid("use_yn", "사용 여부는 Y 또는 N입니다.");
        result.setUseYn(use);
        result.setAttr1Val(optional(f, "attr1_val", old == null ? null : old.getAttr1Val()));
        result.setAttr2Val(optional(f, "attr2_val", old == null ? null : old.getAttr2Val()));
        result.setAttr3Val(optional(f, "attr3_val", old == null ? null : old.getAttr3Val()));
        result.setAplyBgnDt(old == null ? null : old.getAplyBgnDt());
        if (f.has("aply_bgn_dt")) {
            String date = text(f, "aply_bgn_dt", 10, false, "");
            try {
                if (date != null && !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                    invalid("aply_bgn_dt", "날짜 형식을 확인하세요.");
                }
                result.setAplyBgnDt(date == null ? null : LocalDate.parse(date));
            } catch (DateTimeParseException error) {
                invalid("aply_bgn_dt", "날짜 형식을 확인하세요.");
            }
        }
        return result;
    }

    private String optional(JsonNode fields, String key, String previous) {
        return fields.has(key) ? text(fields, key, 100, false, "") : previous;
    }

    private Map<String, Object> values(AdminCodeRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cd_nm", row.getCdNm());
        result.put("sort_seq", row.getSortSeq());
        result.put("use_yn", row.getUseYn());
        result.put("attr1_val", row.getAttr1Val());
        result.put("attr2_val", row.getAttr2Val());
        result.put("attr3_val", row.getAttr3Val());
        result.put("aply_bgn_dt", row.getAplyBgnDt());
        return result;
    }

    private Map<String, Object> differences(AdminCodeRow before, AdminCodeRow after) {
        var result = values(after);
        if (before != null) {
            var previous = values(before);
            result.entrySet().removeIf(entry -> Objects.equals(previous.get(entry.getKey()), entry.getValue()));
        }
        return result;
    }

    private CodeGroupRow requireGroup(String group) {
        if (group == null || group.isBlank() || group.length() > 30) invalid("group", "그룹을 확인하세요.");
        CodeGroupRow row = mapper.selectGroup(group);
        if (row == null) throw new BusinessException(404, "404", "그룹을 찾을 수 없습니다.", "group");
        return row;
    }

    private void history(String group, String code, String type, String item, String before, String after) {
        mapper.insertHistory(group, code, type, item, before, after, audit.actorId());
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static void object(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) invalid("body", "객체를 입력하세요.");
        body.fieldNames().forEachRemaining(key -> {
            if (!allowed.contains(key)) invalid(key, "허용되지 않는 항목입니다.");
        });
    }

    private static String text(JsonNode body, String field, int max, boolean required, String message) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            if (required) invalid(field, message);
            return null;
        }
        if (!node.isTextual()) invalid(field, "문자를 입력하세요.");
        String value = node.textValue();
        if (required && value.isBlank()) invalid(field, message);
        if (value.length() > max) invalid(field, "입력 길이를 확인하세요.");
        return value;
    }

    private static void invalid(String field, String message) {
        throw new BusinessException(400, "400", message, field);
    }

    private static void duplicate() {
        throw new BusinessException(409, "DUPLICATE_CODE", "이미 있는 코드입니다.", "dtl_cd");
    }

    private static void locked(String message) {
        throw new BusinessException(409, "SYSTEM_CODE_LOCKED", message, "codes");
    }

    private record Mutation(AdminCodeRow before, AdminCodeRow after, Map<String, Object> changes) { }
}
