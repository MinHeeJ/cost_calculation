package com.sicost.codegroups;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.PermissionService;
import com.sicost.common.ValidationException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 전체집합 검증을 먼저 끝내고 코드 변경과 항목별 이력을 원자적으로 저장한다. */
@Service
@Transactional(readOnly = true)
public class CodeGroupService {
    private static final Set<String> OPTIONAL = Set.of("attr1_val", "attr2_val", "attr3_val", "aply_bgn_dt");
    private static final Set<String> CODE_FIELDS = Set.of(
        "dtl_cd", "cd_nm", "sort_seq", "use_yn", "attr1_val", "attr2_val", "attr3_val", "aply_bgn_dt");
    private final CodeGroupMapper mapper;
    private final AuditContext audit;
    private final PermissionService permissions;

    public CodeGroupService(CodeGroupMapper mapper, AuditContext audit, PermissionService permissions) {
        this.mapper = mapper;
        this.audit = audit;
        this.permissions = permissions;
    }

    public List<CodeGroupRow> groups() {
        permissions.can("UI-27.read");
        return mapper.selectGroups();
    }

    public List<CodeDetailRow> codes(String group) {
        permissions.can("UI-27.read");
        requireGroup(group);
        return mapper.selectCodes(group);
    }

    /** BIZ 그룹만 추가하며 입력받은 처리자나 유형으로 서버 정책을 덮어쓸 수 없다. */
    @Transactional
    public void createGroup(JsonNode body) {
        permissions.can("UI-27.create");
        object(body, Set.of("cd_grp_id", "cd_grp_nm", "attr1_nm", "attr2_nm", "attr3_nm"));
        String id = text(body, "cd_grp_id", 30, true);
        if (!id.matches("[A-Z_]{1,30}")) invalid("cd_grp_id", "영문 대문자와 _만 입력하세요.");
        var values = new HashMap<String, Object>();
        values.put("cd_grp_id", id);
        values.put("cd_grp_nm", text(body, "cd_grp_nm", 100, true));
        for (int i = 1; i <= 3; i++) {
            String key = "attr" + i + "_nm";
            values.put(key, text(body, key, 50, false));
        }
        if (mapper.selectGroup(id) != null) duplicate();
        values.put("actor", audit.actorId());
        mapper.insertGroup(values);
        history(id, null, "ADD", null, null, values.get("cd_grp_nm"));
    }

    /** 생략은 유지, 명시 null은 비우기다. no-op은 감사 trigger조차 실행하지 않는다. */
    @Transactional
    public void saveCodes(String group, JsonNode body) {
        permissions.can("UI-27.update");
        var header = requireGroup(group);
        object(body, Set.of("codes"));
        JsonNode input = body.get("codes");
        if (input == null || !input.isArray()) invalid("codes", "전체 코드 목록을 입력하세요.");
        var current = new LinkedHashMap<String, Map<String, Object>>();
        for (var row : mapper.selectCodes(group)) current.put(row.getDtlCd(), values(row));
        var seen = new HashSet<String>();
        var prepared = new ArrayList<Map<String, Object>>();
        for (var node : input) {
            object(node, CODE_FIELDS);
            String code = text(node, "dtl_cd", 30, true);
            if (!code.matches("[A-Z0-9_]{1,30}")) invalid("dtl_cd", "코드 형식을 확인하세요.");
            if (!seen.add(code)) duplicate();
            var row = new LinkedHashMap<String, Object>();
            row.put("dtl_cd", code);
            row.put("cd_nm", text(node, "cd_nm", 100, true));
            JsonNode sort = node.get("sort_seq");
            if (sort == null || !sort.isIntegralNumber() || !sort.canConvertToInt()) {
                invalid("sort_seq", "정수를 입력하세요.");
            }
            row.put("sort_seq", sort.intValue());
            String use = text(node, "use_yn", 1, true);
            if (!Set.of("Y", "N").contains(use)) invalid("use_yn", "Y 또는 N을 입력하세요.");
            row.put("use_yn", use);
            var old = current.get(code);
            for (String field : OPTIONAL) {
                Object value = old == null ? null : old.get(field);
                if (node.has(field)) {
                    if (field.equals("aply_bgn_dt")) value = date(node.get(field));
                    else value = text(node, field, 100, false);
                }
                row.put(field, value);
            }
            if ("SYSTEM".equals(header.getCdGrpSeCd())) {
                if (old != null && !Objects.equals(use, old.get("use_yn"))) {
                    locked("시스템 코드는 사용 중지할 수 없습니다.");
                }
                if (old == null || OPTIONAL.stream().anyMatch(key -> !Objects.equals(row.get(key), old.get(key)))) {
                    locked("시스템 코드는 이름과 순서만 바꿀 수 있습니다.");
                }
            }
            prepared.add(row);
        }
        if (!seen.containsAll(current.keySet())) invalid("codes", "기존 코드를 모두 포함하세요.");
        // All rows are validated before any write, including forbidden changes on the last row.
        for (var row : prepared) {
            String code = (String) row.get("dtl_cd");
            var old = current.get(code);
            row.put("group", group);
            row.put("actor", audit.actorId());
            if (old == null) {
                permissions.can("UI-27.create");
                row.put("use_yn", "Y");
                mapper.insertCode(row);
                history(group, code, "ADD", null, null, row.get("cd_nm"));
            } else {
                var changes = new LinkedHashMap<String, Object>();
                for (String key : CODE_FIELDS) {
                    if (!key.equals("dtl_cd") && !Objects.equals(old.get(key), row.get(key))) {
                        changes.put(key, row.get(key));
                    }
                }
                if (changes.isEmpty()) continue;
                if (changes.containsKey("use_yn")) permissions.can("UI-27.delete");
                row.put("changes", changes);
                row.put("updatedAt", audit.updatedAt());
                mapper.updateCode(row);
                for (var change : changes.entrySet()) {
                    String type = change.getKey().equals("use_yn")
                        ? ("Y".equals(change.getValue()) ? "ENABLE" : "DISABLE") : "UPDATE";
                    history(group, code, type, change.getKey(), old.get(change.getKey()), change.getValue());
                }
            }
        }
    }

    public record HistoryPage(List<CodeHistoryRow> items, long total, int page, int size) { }

    public HistoryPage historyPage(String group, int page, int size, String sort) {
        permissions.can("UI-27.read");
        if (group != null && group.length() > 30) invalid("cd_grp_id", "30자 이하로 입력하세요.");
        if (page < 1) invalid("page", "1 이상을 입력하세요.");
        if (!Set.of(20, 50, 100).contains(size)) invalid("size", "20, 50, 100 중 선택하세요.");
        if (!"chg_at,desc".equals(sort)) invalid("sort", "최신순만 사용할 수 있습니다.");
        return new HistoryPage(mapper.selectHistory(group, size, ((long) page - 1) * size),
            mapper.countHistory(group), page, size);
    }

    private CodeGroupRow requireGroup(String group) {
        if (group == null || group.isBlank() || group.length() > 30) invalid("group", "그룹을 확인하세요.");
        var found = mapper.selectGroup(group);
        if (found == null) throw new CodeGroupException(404, "404", "그룹을 찾을 수 없습니다.");
        return found;
    }

    private Map<String, Object> values(CodeDetailRow row) {
        var values = new HashMap<String, Object>();
        values.put("cd_nm", row.getCdNm());
        values.put("sort_seq", row.getSortSeq());
        values.put("use_yn", row.getUseYn());
        values.put("attr1_val", row.getAttr1Val());
        values.put("attr2_val", row.getAttr2Val());
        values.put("attr3_val", row.getAttr3Val());
        values.put("aply_bgn_dt", row.getAplyBgnDt());
        return values;
    }

    private void history(String group, String code, String type, String item, Object before, Object after) {
        var values = new HashMap<String, Object>();
        values.put("group", group);
        values.put("code", code);
        values.put("type", type);
        values.put("item", item);
        values.put("before", before == null ? null : before.toString());
        values.put("after", after == null ? null : after.toString());
        values.put("actor", audit.actorId());
        mapper.insertHistory(values);
    }

    private LocalDate date(JsonNode node) {
        if (node.isNull()) return null;
        if (!node.isTextual() || !node.asText().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            invalid("aply_bgn_dt", "YYYY-MM-DD 형식으로 입력하세요.");
        }
        try {
            return LocalDate.parse(node.asText());
        } catch (DateTimeParseException ignored) {
            throw new ValidationException("aply_bgn_dt", "유효한 날짜를 입력하세요.");
        }
    }

    private void object(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) invalid("body", "객체를 입력하세요.");
        node.fieldNames().forEachRemaining(key -> {
            if (!allowed.contains(key)) invalid(key, "허용되지 않은 항목입니다.");
        });
    }

    private String text(JsonNode node, String key, int max, boolean required) {
        JsonNode value = node.get(key);
        String result = value == null || value.isNull() ? null : value.isTextual() ? value.textValue() : null;
        if (value != null && !value.isNull() && !value.isTextual()) invalid(key, "문자열을 입력하세요.");
        if (required && (result == null || result.isBlank())) {
            String message = key.equals("dtl_cd") ? "코드를 입력하세요."
                : key.equals("cd_nm") ? "이름을 입력하세요." : "필수 항목입니다.";
            invalid(key, message);
        }
        if (result != null && result.length() > max) invalid(key, max + "자 이하로 입력하세요.");
        return result;
    }

    private void invalid(String field, String message) {
        throw new ValidationException(field, message);
    }

    private void duplicate() {
        throw new CodeGroupException(409, "DUPLICATE_CODE", "이미 있는 코드입니다.");
    }

    private void locked(String message) {
        throw new CodeGroupException(409, "SYSTEM_CODE_LOCKED", message);
    }
}
