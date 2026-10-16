package com.sicost.partnersops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.PermissionService;
import com.sicost.persistence.AuditContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Partner activity reads and atomic soft-disable/reactivation without changing any domain relation. */
@Service
public class PartnerActivityService {
    private final PartnerActivityMapper mapper;
    private final AuditContext audit;
    private final PermissionService permissions;
    private final Clock clock;

    public PartnerActivityService(
        PartnerActivityMapper mapper,
        AuditContext audit,
        PermissionService permissions,
        @Qualifier("partnerActivityClock") Clock clock
    ) {
        this.mapper = mapper;
        this.audit = audit;
        this.permissions = permissions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PartnerOptionRow> getPartnerOptions(String kind) {
        permissions.can("UI-15.read");
        if (!"CUST".equals(kind) && !"VEND".equals(kind)) {
            throw invalid("kind", "고객사 또는 협력사를 선택하세요.");
        }
        return mapper.selectOptions(kind);
    }

    @Transactional(readOnly = true)
    public List<PartnerAssignmentRow> getPartnerAssignments(long id) {
        permissions.can("UI-15.read");
        LocalDate today = today();
        requirePartner(id);
        return mapper.selectAssignments(id, today);
    }

    @Transactional(readOnly = true)
    public List<PartnerHistoryRow> getPartnerHistory(long id) {
        permissions.can("UI-15.read");
        requirePartner(id);
        return mapper.selectHistory(id);
    }

    /** Validate every identity before any DML; exclusions do not split the transaction. */
    @Transactional
    public PartnerDisableResult disablePartners(JsonNode body) {
        permissions.can("UI-15.delete");
        LocalDate today = today();
        if (body == null || !body.isObject() || body.size() != 1
            || !body.path("ids").isArray() || body.path("ids").isEmpty()) {
            throw invalid("ids", "사용 중지할 거래처를 선택하세요.");
        }
        var ids = new LinkedHashSet<Long>();
        for (JsonNode value : body.path("ids")) {
            if (!value.isIntegralNumber() || !value.canConvertToLong()) {
                throw invalid("ids", "거래처 ID는 정수여야 합니다.");
            }
            ids.add(value.longValue());
        }
        var rows = new ArrayList<PartnerStatusRow>();
        for (long id : ids) {
            rows.add(requirePartner(id));
        }
        var possible = new ArrayList<Long>();
        var excluded = new ArrayList<PartnerDisableResult.Exclusion>();
        int already = 0;
        int inUse = 0;
        for (PartnerStatusRow row : rows) {
            long id = row.getId();
            if ("N".equals(row.getUseYn())) {
                already++;
                excluded.add(new PartnerDisableResult.Exclusion(id, row.getPtnrNm(),
                    "이미 중지되어 제외", 0, 0));
                continue;
            }
            int projects = mapper.countOngoingProjects(id, today);
            int people = mapper.countAffiliatedDevelopers(id, today);
            if (projects > 0 || people > 0) {
                inUse++;
                var reasons = new ArrayList<String>();
                if (projects > 0) {
                    reasons.add("진행 프로젝트 " + projects + "건");
                }
                if (people > 0) {
                    reasons.add("소속 인력 " + people + "명");
                }
                excluded.add(new PartnerDisableResult.Exclusion(id, row.getPtnrNm(),
                    String.join(" / ", reasons), projects, people));
            } else {
                possible.add(id);
            }
        }
        if (ids.size() == 1 && inUse == 1) {
            throw new PartnerActivityException(409, null, "거래가 남아 사용 중지할 수 없습니다.", excluded);
        }
        for (long id : possible) {
            changeUse(id, "N");
            mapper.insertHistory(id, "DISABLE", null, null, null, audit.actorId());
        }
        return new PartnerDisableResult(List.copyOf(possible), possible.size(), inUse, already,
            List.copyOf(excluded));
    }

    /** Repeated activation must not issue UPDATE: the DB trigger owns updated_at. */
    @Transactional
    public Map<String, Object> enablePartner(long id) {
        permissions.can("UI-15.delete");
        PartnerStatusRow row = requirePartner(id);
        if ("N".equals(row.getUseYn())) {
            changeUse(id, "Y");
            mapper.insertHistory(id, "UPDATE", "use_yn", "N", "Y", audit.actorId());
            row = requirePartner(id);
        }
        return detail(row);
    }

    private void changeUse(long id, String useYn) {
        if (mapper.updateUse(id, useYn, audit.actorId()) != 1) {
            throw new PartnerActivityException(404, "id", "거래처를 찾을 수 없습니다.", null);
        }
    }

    private PartnerStatusRow requirePartner(long id) {
        PartnerStatusRow row = mapper.selectPartner(id);
        if (row == null) {
            throw new PartnerActivityException(404, "id", "거래처를 찾을 수 없습니다.", null);
        }
        return row;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
    }

    /** Explicit per-kind projection preserves required nullable keys but omits inapplicable sensitive keys. */
    private Map<String, Object> detail(PartnerStatusRow row) {
        var result = new LinkedHashMap<String, Object>();
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

    private PartnerActivityException invalid(String field, String message) {
        return new PartnerActivityException(400, field, message, null);
    }
}
