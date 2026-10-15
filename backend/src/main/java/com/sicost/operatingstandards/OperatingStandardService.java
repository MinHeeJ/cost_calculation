package com.sicost.operatingstandards;

import com.sicost.common.BusinessException;
import com.sicost.persistence.AuditContext;
import com.sicost.common.PermissionService;
import com.sicost.common.ValidationException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 월별 기준값을 기존 자연키로 저장하고 PARAM 이력을 같은 트랜잭션에 남긴다. */
@Service
@Transactional(readOnly = true)
public class OperatingStandardService {
    private final OperatingStandardMapper mapper;
    private final AuditContext audit;
    private final PermissionService permissions;

    public OperatingStandardService(
        OperatingStandardMapper mapper,
        AuditContext audit,
        PermissionService permissions
    ) {
        this.mapper = mapper;
        this.audit = audit;
        this.permissions = permissions;
    }

    /** 서울 이번 달 이하의 최신값만 조회하며 미래 적용값은 전체 이력에만 노출한다. */
    public OperatingStandardsResult getOperatingStandards() {
        permissions.can("UI-27.read");
        String month = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
        return new OperatingStandardsResult(
            mapper.selectCurrent(month).stream().map(OperatingStandardView::from).toList(),
            mapper.selectLastClosedMonth()
        );
    }

    /** 키의 전체 월을 미래 포함 반환하며 읽기로 업무·감사 필드를 바꾸지 않는다. */
    public List<OperatingStandardView> getOperatingStandardHistory(String key) {
        permissions.can("UI-27.read");
        validateKey(key, 50);
        return history(key).stream().map(OperatingStandardView::from).toList();
    }

    /** 잠금 없이 해당 월만 저장한다. 이력 실패는 저장도 롤백하고 마감·계산은 수정하지 않는다. */
    @Transactional
    public void saveOperatingStandard(String key, SaveOperatingStandardRequest request) {
        permissions.can("UI-27.update");
        validateKey(key, 30);
        if (request == null || request.month() == null
            || !request.month().matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw invalid("aply_bgn_ym");
        }
        var rows = history(key);
        String month = request.month();
        // 요청월 이하 최신 메타가 없으면 가장 이른 행을 사용한다(D8).
        var source = rows.stream().filter(row -> row.getAplyBgnYm().compareTo(month) <= 0)
            .max(Comparator.comparing(OperatingStandardRow::getAplyBgnYm))
            .orElseGet(() -> rows.stream()
                .min(Comparator.comparing(OperatingStandardRow::getAplyBgnYm)).orElseThrow());
        boolean numeric = source.getNumVal() != null;
        if (numeric == (source.getTxtVal() != null)) {
            throw invalid("num_val");
        }
        validateValue(request, source, numeric);
        String closed = mapper.selectLastClosedMonth();
        if (closed != null && month.compareTo(closed) <= 0) {
            throw new BusinessException(409, "CLOSED_MONTH", "마감월 다음 달부터 적용할 수 있습니다.", "aply_bgn_ym");
        }
        var existing = rows.stream().filter(row -> row.getAplyBgnYm().equals(month)).findFirst().orElse(null);
        if (existing != null && equalNumber(existing.getNumVal(), request.number())
            && Objects.equals(existing.getTxtVal(), request.text())) {
            return;
        }
        var saved = new OperatingStandardRow();
        saved.setOprtStdId(key);
        saved.setAplyBgnYm(month);
        saved.setOprtStdNm(source.getOprtStdNm());
        saved.setNumVal(request.number());
        saved.setTxtVal(request.text());
        saved.setUnitNm(source.getUnitNm());
        saved.setUsePlcDesc(source.getUsePlcDesc());
        mapper.save(saved, audit.actorId());
        mapper.appendHistory(key, month, value(existing == null ? source : existing), value(saved), audit.actorId());
    }

    private List<OperatingStandardRow> history(String key) {
        var rows = mapper.selectHistory(key);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "404", "기준값을 찾을 수 없습니다.", "oprt_std_id");
        }
        return rows;
    }

    private void validateValue(SaveOperatingStandardRequest request, OperatingStandardRow source, boolean numeric) {
        String field = numeric ? "num_val" : "txt_val";
        if (request.numberPresent() == request.textPresent() || numeric != request.numberPresent()) {
            throw invalid(field);
        }
        if (numeric) {
            BigDecimal value = request.number();
            if (value == null || value.signum() < 0) {
                throw invalid(field);
            }
            BigDecimal effective = value.stripTrailingZeros();
            if (effective.scale() > 4 || effective.precision() - effective.scale() > 14) {
                throw invalid(field);
            }
            if (("%".equals(source.getUnitNm()) || "%p".equals(source.getUnitNm()))
                && value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw invalid(field);
            }
        } else if (request.text() == null || request.text().isBlank() || request.text().length() > 200) {
            throw invalid(field);
        }
    }

    private boolean equalNumber(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private String value(OperatingStandardRow row) {
        return row.getNumVal() == null ? row.getTxtVal() : row.getNumVal().stripTrailingZeros().toPlainString();
    }

    private void validateKey(String key, int max) {
        if (key == null || key.isBlank() || key.length() > max) {
            throw invalid("oprt_std_id");
        }
    }

    private ValidationException invalid(String field) {
        return new ValidationException(field, "올바른 값을 입력하세요.");
    }
}
