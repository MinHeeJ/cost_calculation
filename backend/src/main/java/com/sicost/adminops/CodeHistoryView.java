package com.sicost.adminops;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/** 이력의 snake_case wire를 기존 메뉴 DTO와 독립적으로 유지한다. */
public record CodeHistoryView(
    @JsonProperty("cd_hist_id") Long cdHistId,
    @JsonProperty("cd_grp_id") String cdGrpId,
    @JsonProperty("dtl_cd") String dtlCd,
    @JsonProperty("cd_chg_ty_cd") String cdChgTyCd,
    @JsonProperty("item_nm") String itemNm,
    @JsonProperty("bfr_val") String bfrVal,
    @JsonProperty("aft_val") String aftVal,
    @JsonProperty("chg_user_id") Long chgUserId,
    @JsonProperty("chg_at") LocalDateTime chgAt,
    @JsonProperty("cd_grp_nm") String cdGrpNm
) {
    public static CodeHistoryView from(CodeHistoryRow row) {
        return new CodeHistoryView(
            row.getCdHistId(),
            row.getCdGrpId(),
            row.getDtlCd(),
            row.getCdChgTyCd(),
            row.getItemNm(),
            row.getBfrVal(),
            row.getAftVal(),
            row.getChgUserId(),
            row.getChgAt(),
            row.getCdGrpNm()
        );
    }
}
