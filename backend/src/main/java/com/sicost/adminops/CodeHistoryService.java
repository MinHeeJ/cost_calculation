package com.sicost.adminops;

import com.sicost.common.BusinessException;
import com.sicost.common.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 이력 조회의 입력 검증 및 목록/count를 한 읽기 트랜잭션으로 묶는다. */
@Service
public class CodeHistoryService {
    private final CodeHistoryMapper mapper;

    public CodeHistoryService(CodeHistoryMapper mapper) {
        this.mapper = mapper;
    }

    /** PARAM은 실제 그룹 행이 없는 기준값 이력 식별자이며 별도 계정 조회는 하지 않는다. */
    @Transactional(readOnly = true)
    public CodeHistoryPage getCodeHistory(String group, int page, int size, String sort) {
        if (group != null && group.length() > 30) {
            throw new ValidationException("cd_grp_id", "그룹은 30자 이하여야 합니다.");
        }
        if (page < 1) {
            throw new ValidationException("page", "페이지는 1 이상이어야 합니다.");
        }
        if (size != 20 && size != 50 && size != 100) {
            throw new ValidationException("size", "페이지당 건수는 20, 50, 100 중에서 선택하세요.");
        }
        if (!"chg_at,desc".equals(sort)) {
            throw new ValidationException("sort", "변경 일시 최신순만 지원합니다.");
        }
        String filter = group == null || group.isEmpty() ? null : group;
        if (filter != null && !"PARAM".equals(filter) && !mapper.groupExists(filter)) {
            throw new BusinessException(404, "404", "코드 그룹이 없습니다.", "cd_grp_id");
        }
        long total = mapper.countHistory(filter);
        var items = mapper.selectHistory(filter, size, ((long) page - 1) * size)
            .stream().map(CodeHistoryView::from).toList();
        return new CodeHistoryPage(items, total, page, size);
    }
}
