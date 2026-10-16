package com.sicost.operatingstandards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.sicost.common.CurrentUser;
import com.sicost.common.PermissionService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 적용월별 값과 PARAM 이력을 한 트랜잭션으로 보존한다. 잠금이나 계산 재실행은 하지 않는다. */
@Service
@Transactional(readOnly = true)
public class OperatingStandardService {
    private final OperatingStandardMapper mapper;
    private final CurrentUser currentUser;
    private final PermissionService permissions;

    public OperatingStandardService(
        OperatingStandardMapper mapper, CurrentUser currentUser, PermissionService permissions
    ) {
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.permissions = permissions;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CurrentStandards(List<OperatingStandardView> items, String lastClosedYm) {}

    public CurrentStandards getOperatingStandards() {
        permissions.can("UI-27.read");
        String month = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
        return new CurrentStandards(
            mapper.selectCurrent(month).stream().map(OperatingStandardView::from).toList(), mapper.selectLastClosed()
        );
    }

    public List<OperatingStandardView> getOperatingStandardHistory(String key) {
        permissions.can("UI-27.read");
        return history(key).stream().map(OperatingStandardView::from).toList();
    }

    private List<OperatingStandardRow> history(String key) {
        if (key == null || key.isBlank() || key.length() > 50) {
            throw invalid("oprt_std_id");
        }
        var rows = mapper.selectHistory(key);
        if (rows.isEmpty()) {
            throw new OperatingStandardException(404, "404", "oprt_std_id", "기준값을 찾을 수 없습니다.");
        }
        return rows;
    }

    /** 요청월 이하 최신 메타(없으면 최초 메타)를 복사하고 마감/종류/정밀도를 저장 전에 검사한다. */
    @Transactional
    public void saveOperatingStandard(String key, JsonNode body) {
        permissions.can("UI-27.update");
        if (key.length() > 30) {
            throw invalid("oprt_std_id");
        }
        if (body == null || !body.isObject()) {
            throw invalid("aply_bgn_ym");
        }
        var names = body.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!Set.of("aply_bgn_ym", "num_val", "txt_val").contains(name)) {
                throw invalid(name);
            }
        }
        JsonNode monthNode = body.path("aply_bgn_ym");
        if (!monthNode.isTextual() || !monthNode.textValue().matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw invalid("aply_bgn_ym");
        }
        String month = monthNode.textValue();
        var rows = history(key);
        var source = rows.stream().filter(r -> r.getAplyBgnYm().compareTo(month) <= 0)
            .max(Comparator.comparing(OperatingStandardRow::getAplyBgnYm))
            .orElseGet(() -> rows.stream().min(Comparator.comparing(OperatingStandardRow::getAplyBgnYm)).orElseThrow());
        boolean numeric = source.getNumVal() != null;
        if (numeric == (source.getTxtVal() != null)) {
            throw invalid("num_val");
        }
        if (body.has("num_val") && body.has("txt_val")) {
            throw invalid("num_val");
        }
        BigDecimal number = null;
        String text = null;
        if (numeric) {
            JsonNode node = body.path("num_val");
            if (!node.isNumber()) {
                throw invalid("num_val");
            }
            number = node.decimalValue();
            if (number.signum() < 0 || number.scale() > 4
                || number.compareTo(new BigDecimal("99999999999999.9999")) > 0
                || (("%".equals(source.getUnitNm()) || "%p".equals(source.getUnitNm()))
                    && number.compareTo(new BigDecimal("100")) > 0)) {
                throw invalid("num_val");
            }
        } else {
            JsonNode node = body.path("txt_val");
            if (!node.isTextual() || node.textValue().isBlank() || node.textValue().length() > 200) {
                throw invalid("txt_val");
            }
            text = node.textValue();
        }
        String closed = mapper.selectLastClosed();
        if (closed != null && month.compareTo(closed) <= 0) {
            throw new OperatingStandardException(409, "CLOSED_MONTH", "aply_bgn_ym", "마감된 월은 변경할 수 없습니다.");
        }
        var existing = rows.stream().filter(r -> month.equals(r.getAplyBgnYm())).findFirst();
        String before = value(existing.orElse(source));
        String after = numeric ? number.stripTrailingZeros().toPlainString() : text;
        if (existing.isPresent() && before.equals(after)) {
            return;
        }
        var target = new OperatingStandardRow();
        target.setOprtStdId(key);
        target.setAplyBgnYm(month);
        target.setOprtStdNm(source.getOprtStdNm());
        target.setUnitNm(source.getUnitNm());
        target.setUsePlcDesc(source.getUsePlcDesc());
        target.setOprtStdDesc(source.getOprtStdDesc());
        target.setNumVal(number);
        target.setTxtVal(text);
        mapper.upsert(target, currentUser.userId());
        mapper.insertHistory(key, month, before, after, currentUser.userId());
    }

    private String value(OperatingStandardRow row) {
        return row.getNumVal() != null ? row.getNumVal().stripTrailingZeros().toPlainString() : row.getTxtVal();
    }

    private OperatingStandardException invalid(String field) {
        return new OperatingStandardException(400, "400", field, "올바른 값을 입력하세요.");
    }
}
