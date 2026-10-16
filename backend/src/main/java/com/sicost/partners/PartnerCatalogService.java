package com.sicost.partners;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.PermissionService;
import com.sicost.persistence.AuditContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Partner catalog invariants and column-level audit writes share the service transaction. */
@Service
public class PartnerCatalogService {
    private static final Map<String, String> COLUMNS = Map.ofEntries(
        Map.entry("ptnrSeCd", "ptnr_se_cd"), Map.entry("ptnrNm", "ptnr_nm"),
        Map.entry("ceoNm", "ceo_nm"), Map.entry("indtyCd", "indty_cd"),
        Map.entry("chrgNm", "chrg_nm"), Map.entry("chrgTelNo", "chrg_tel_no"),
        Map.entry("txinvEmailAddr", "txinv_email_addr"), Map.entry("bankCd", "bank_cd"),
        Map.entry("acntNo", "acnt_no"), Map.entry("dpstrNm", "dpstr_nm")
    );
    private static final Set<String> SORTS = Set.of(
        "ptnr_no", "ptnr_se_cd", "ptnr_nm", "biz_reg_no", "ceo_nm", "indty_cd", "chrg_nm",
        "chrg_tel_no", "use_yn", "ongoing_project_count", "current_developer_count",
        "txinv_email_addr", "bank_account_display"
    );
    private final PartnerCatalogMapper mapper;
    private final AuditContext audit;
    private final PermissionService permissions;
    private final Clock clock;

    public PartnerCatalogService(PartnerCatalogMapper mapper, AuditContext audit,
        PermissionService permissions, @Qualifier("partnerCatalogClock") Clock clock) {
        this.mapper = mapper;
        this.audit = audit;
        this.permissions = permissions;
        this.clock = clock;
    }

    public record PartnerPage(List<PartnerListItem> items, long total, int page, int size, String sort) { }

    /** Validated SQL sort expressions are server-owned; count uses the identical filter fragment. */
    @Transactional(readOnly = true)
    public PartnerPage getPartners(String kind, String useYn, String q, int page, int size, String sort) {
        permissions.can("UI-15.read");
        LocalDate today = LocalDate.now(clock);
        if (kind != null && !Set.of("CUST", "VEND", "BOTH").contains(kind)) {
            throw invalid("kind", "구분을 확인해 주세요.");
        }
        if (!Set.of("Y", "N", "ALL").contains(useYn)) {
            throw invalid("useYn", "사용 여부를 확인해 주세요.");
        }
        if (page < 1) {
            throw invalid("page", "페이지는 1 이상입니다.");
        }
        if (!Set.of(20, 50, 100).contains(size)) {
            throw invalid("size", "페이지 크기는 20, 50, 100입니다.");
        }
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !SORTS.contains(parts[0]) || !Set.of("asc", "desc").contains(parts[1])) {
            throw invalid("sort", "정렬 항목과 방향을 확인해 주세요.");
        }
        String order = switch (parts[0]) {
            case "current_developer_count" -> "assigned_developer_count";
            case "bank_account_display" -> "payment_account_summary";
            default -> parts[0];
        };
        String search = q == null || q.isBlank() ? null : q.trim();
        String use = "ALL".equals(useYn) ? null : useYn;
        return new PartnerPage(mapper.list(kind, use, search, today, order, parts[1], size,
            ((long) page - 1) * size), mapper.count(kind, use, search), page, size, sort);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPartner(long id) {
        permissions.can("UI-15.read");
        return detail(required(id));
    }

    /** Reserve the identity first so the initial INSERT already satisfies ptnr_no NOT NULL. */
    @Transactional
    public Map<String, Object> createPartner(JsonNode body) {
        permissions.can("UI-15.create");
        PartnerCatalogRow row = normalize(body, null);
        String number = text(body, "bizRegNo", 12, true);
        if (!number.matches("([0-9]{10}|[0-9]{3}-[0-9]{2}-[0-9]{5})")) {
            throw invalid("bizRegNo", "사업자번호 10자리를 입력해 주세요. (예: 123-45-67890)");
        }
        String digits = number.replace("-", "");
        row.setBizRegNo(digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5));
        String existing = mapper.duplicateName(row.getBizRegNo());
        if (existing != null) {
            throw duplicate(existing);
        }
        long id = mapper.nextId();
        row.setId(id);
        row.setNo("C-" + String.format(java.util.Locale.ROOT, "%03d", id));
        row.setUseYn("Y");
        // ON CONFLICT keeps PostgreSQL's transaction usable for the race-winner name lookup.
        if (mapper.insert(row, audit.actorId()) == 0) {
            throw duplicate(mapper.duplicateName(row.getBizRegNo()));
        }
        mapper.history(id, "INSERT", null, null, null, audit.actorId());
        return detail(required(id));
    }

    /** Identity and existing relationships never enter the writable column allowlist. */
    @Transactional
    public Map<String, Object> updatePartner(long id, JsonNode body) {
        permissions.can("UI-15.update");
        PartnerCatalogRow before = required(id);
        PartnerCatalogRow after = normalize(body, before);
        Map<String, Object> oldValues = detail(before);
        Map<String, Object> newValues = detail(after);
        Map<String, String> changes = new LinkedHashMap<>();
        for (var field : COLUMNS.entrySet()) {
            String oldValue = (String) oldValues.get(field.getKey());
            String newValue = (String) newValues.get(field.getKey());
            if (!Objects.equals(oldValue, newValue)) {
                changes.put(field.getValue(), newValue);
            }
        }
        if (changes.isEmpty()) {
            return detail(before);
        }
        if (mapper.update(id, changes, audit.actorId()) != 1) {
            throw missing();
        }
        for (var field : COLUMNS.entrySet()) {
            if (changes.containsKey(field.getValue())) {
                String oldValue = (String) oldValues.get(field.getKey());
                String newValue = changes.get(field.getValue());
                if ("acntNo".equals(field.getKey())) {
                    oldValue = tail(oldValue);
                    newValue = tail(newValue);
                }
                mapper.history(id, "UPDATE", field.getValue(), oldValue, newValue, audit.actorId());
            }
        }
        return detail(required(id));
    }

    private PartnerCatalogRow normalize(JsonNode body, PartnerCatalogRow old) {
        if (body == null || !body.isObject()) {
            throw invalid(null, "객체 형식으로 입력해 주세요.");
        }
        PartnerCatalogRow row = new PartnerCatalogRow();
        row.setPtnrSeCd(text(body, "ptnrSeCd", 10, true));
        if (!Set.of("CUST", "VEND", "BOTH").contains(row.getPtnrSeCd())) {
            throw invalid("ptnrSeCd", "구분을 골라 주세요.");
        }
        row.setPtnrNm(text(body, "ptnrNm", 50, true));
        row.setCeoNm(text(body, "ceoNm", 20, false));
        row.setChrgNm(text(body, "chrgNm", 20, false));
        row.setChrgTelNo(text(body, "chrgTelNo", 20, false));
        row.setIndtyCd(text(body, "indtyCd", 30, false));
        code("INDUSTRY", "indtyCd", row.getIndtyCd(), old == null ? null : old.getIndtyCd());
        if (!"VEND".equals(row.getPtnrSeCd())) {
            row.setTxinvEmailAddr(text(body, "txinvEmailAddr", 100, true));
            if (!row.getTxinvEmailAddr().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
                throw invalid("txinvEmailAddr", "이메일 형식이 맞지 않습니다. (예: tax@company.co.kr)");
            }
        }
        if (!"CUST".equals(row.getPtnrSeCd())) {
            row.setBankCd(text(body, "bankCd", 30, true));
            row.setAcntNo(text(body, "acntNo", 20, true));
            if (!row.getAcntNo().matches("[0-9-]{8,20}")) {
                throw invalid("acntNo", "계좌번호를 숫자와 하이픈(-)으로 입력해 주세요.");
            }
            row.setDpstrNm(text(body, "dpstrNm", 30, true));
            code("BANK", "bankCd", row.getBankCd(), old == null ? null : old.getBankCd());
        }
        return row;
    }

    private void code(String group, String field, String value, String previous) {
        if (value != null && !Objects.equals(value, previous) && !"Y".equals(mapper.codeUse(group, value))) {
            throw invalid(field, "사용 중인 코드를 골라 주세요.");
        }
    }

    private String text(JsonNode body, String field, int max, boolean required) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            if (required) {
                throw invalid(field, requiredMessage(field));
            }
            return null;
        }
        if (!node.isTextual()) {
            throw invalid(field, "문자열로 입력해 주세요.");
        }
        String value = node.textValue().trim();
        if (required && value.isBlank()) {
            throw invalid(field, requiredMessage(field));
        }
        if (value.codePointCount(0, value.length()) > max) {
            throw invalid(field, max + "자 이하로 입력해 주세요.");
        }
        return value.isEmpty() ? null : value;
    }

    private String requiredMessage(String field) {
        return switch (field) {
            case "ptnrSeCd" -> "구분을 골라 주세요.";
            case "ptnrNm" -> "상호를 입력해 주세요.";
            case "txinvEmailAddr" -> "고객사는 세금계산서 이메일이 꼭 필요합니다.";
            case "bankCd" -> "은행을 골라 주세요.";
            case "dpstrNm" -> "예금주를 입력해 주세요.";
            case "acntNo" -> "계좌번호를 숫자와 하이픈(-)으로 입력해 주세요.";
            default -> "사업자번호 10자리를 입력해 주세요. (예: 123-45-67890)";
        };
    }

    private PartnerCatalogRow required(long id) {
        PartnerCatalogRow row = mapper.selectById(id);
        if (row == null) {
            throw missing();
        }
        return row;
    }

    /** Structural omission is explicit, independent of the application's global null inclusion. */
    private Map<String, Object> detail(PartnerCatalogRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("no", row.getNo());
        result.put("ptnrSeCd", row.getPtnrSeCd());
        result.put("ptnrNm", row.getPtnrNm());
        result.put("bizRegNo", row.getBizRegNo());
        result.put("useYn", row.getUseYn());
        result.put("ceoNm", row.getCeoNm());
        result.put("indtyCd", row.getIndtyCd());
        result.put("chrgNm", row.getChrgNm());
        result.put("chrgTelNo", row.getChrgTelNo());
        result.put("kindName", row.getKindName());
        result.put("industryName", row.getIndustryName());
        if (!"VEND".equals(row.getPtnrSeCd())) {
            result.put("txinvEmailAddr", row.getTxinvEmailAddr());
        }
        if (!"CUST".equals(row.getPtnrSeCd())) {
            result.put("bankCd", row.getBankCd());
            result.put("acntNo", row.getAcntNo());
            result.put("dpstrNm", row.getDpstrNm());
            result.put("bankName", row.getBankName());
        }
        return result;
    }

    private static String tail(String value) {
        return value == null ? null : value.substring(Math.max(0, value.length() - 4));
    }

    private PartnerCatalogException duplicate(String name) {
        return new PartnerCatalogException(409, "DUPLICATE_BIZ_NO", "bizRegNo",
            "이미 등록된 사업자번호입니다. (" + name + ")");
    }

    private PartnerCatalogException missing() {
        return new PartnerCatalogException(404, "404", "id", "거래처를 찾을 수 없습니다.");
    }

    private PartnerCatalogException invalid(String field, String message) {
        return new PartnerCatalogException(400, "400", field, message);
    }
}
