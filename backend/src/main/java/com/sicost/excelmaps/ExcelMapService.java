package com.sicost.excelmaps;

import com.sicost.common.BusinessException;
import com.sicost.common.PermissionService;
import com.sicost.common.ValidationException;
import com.sicost.persistence.AuditContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대응 주 행과 항목 이력을 같은 트랜잭션으로 저장하고 기존 업무 데이터에는 쓰지 않는다. */
@Service
public class ExcelMapService {
    private final ExcelMapMapper mapper;
    private final AuditContext audit;
    private final PermissionService permissions;

    public ExcelMapService(ExcelMapMapper mapper, AuditContext audit, PermissionService permissions) {
        this.mapper = mapper;
        this.audit = audit;
        this.permissions = permissions;
    }

    /** 편집 집합은 중지 행도 포함하며 DB의 표기·PK 순서를 보존한다. */
    @Transactional(readOnly = true)
    public List<ExcelMapView> getExcelMaps() {
        permissions.can("UI-27.read");
        return mapper.selectAll().stream().map(ExcelMapView::from).toList();
    }

    /** 생성 키로 ADD 이력을 연결한다. 이력 실패 시 신규 대응도 롤백된다. */
    @Transactional
    public void createExcelMap(CreateExcelMapRequest input) {
        permissions.can("UI-27.create");
        input.allowed(Set.of("excel_lbl_nm", "trns_cd_grp_id", "trns_cd", "map_role_cd", "rmrk_desc"));
        ExcelMapRow row = new ExcelMapRow();
        row.setExcelLblNm(input.text("excel_lbl_nm", 100, true, "엑셀 글자를 입력하세요."));
        row.setTrnsCdGrpId(input.text("trns_cd_grp_id", 30, true, "대응할 코드를 고르세요."));
        row.setTrnsCd(input.text("trns_cd", 30, true, "대응할 코드를 고르세요."));
        row.setMapRoleCd(role(input));
        row.setRmrkDesc(input.text("rmrk_desc", 200, false, "메모는 200자 이하로 입력하세요."));
        row.setUseYn("Y");
        row.setCreatedBy(audit.actorId());
        if (mapper.activeTarget(row.getTrnsCdGrpId(), row.getTrnsCd()) == 0) {
            throw new ValidationException("trns_cd", "대응할 코드를 고르세요.");
        }
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException ignored) {
            throw new BusinessException(409, "DUPLICATE_CODE", "이미 있는 대응입니다.", "excel_lbl_nm");
        }
        if (row.getMapId() == null) {
            throw new IllegalStateException("Generated map key was not returned");
        }
        mapper.history(row, "ADD", "excel_map." + row.getMapId(), null, row.getExcelLblNm(), audit.actorId());
    }

    /** 식별자는 불변이며 요청에서 실제 바뀐 필드만 UPDATE·이력으로 기록한다. */
    @Transactional
    public void updateExcelMap(long id, UpdateExcelMapRequest input) {
        permissions.can("UI-27.update");
        input.allowed(Set.of("map_role_cd", "rmrk_desc", "use_yn"));
        ExcelMapRow row = mapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "404", "대응을 찾을 수 없습니다.", "map_id");
        }
        var changes = new LinkedHashMap<String, String>();
        var before = new LinkedHashMap<String, String>();
        if (input.has("map_role_cd")) {
            change(changes, before, "map_role_cd", row.getMapRoleCd(), role(input));
        }
        if (input.has("rmrk_desc")) {
            change(changes, before, "rmrk_desc", row.getRmrkDesc(),
                input.text("rmrk_desc", 200, false, "메모는 200자 이하로 입력하세요."));
        }
        if (input.has("use_yn")) {
            permissions.can("UI-27.delete");
            String value = input.text("use_yn", 1, true, "사용 여부를 선택하세요.");
            if (!Set.of("Y", "N").contains(value)) {
                throw new ValidationException("use_yn", "사용 여부를 선택하세요.");
            }
            change(changes, before, "use_yn", row.getUseYn(), value);
        }
        if (changes.isEmpty()) {
            return;
        }
        if (mapper.update(id, changes) != 1) {
            throw new BusinessException(404, "404", "대응을 찾을 수 없습니다.", "map_id");
        }
        for (var entry : changes.entrySet()) {
            String type = "use_yn".equals(entry.getKey())
                ? ("N".equals(entry.getValue()) ? "DISABLE" : "ENABLE") : "UPDATE";
            mapper.history(row, type, "excel_map." + id + "." + entry.getKey(),
                before.get(entry.getKey()), entry.getValue(), audit.actorId());
        }
    }

    private String role(ExcelMapInput input) {
        String value = input.text("map_role_cd", 10, true, "주/보조를 선택하세요.");
        if (!Set.of("MAIN", "SUB").contains(value)) {
            throw new ValidationException("map_role_cd", "주/보조를 선택하세요.");
        }
        return value;
    }

    private void change(
        LinkedHashMap<String, String> changes,
        LinkedHashMap<String, String> before,
        String field,
        String oldValue,
        String newValue
    ) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.put(field, newValue);
            before.put(field, oldValue);
        }
    }
}
