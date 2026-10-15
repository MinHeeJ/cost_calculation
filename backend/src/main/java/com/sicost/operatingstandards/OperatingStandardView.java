package com.sicost.operatingstandards;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** 기준값 조회의 snake_case 전송 계약이다. 전역 JSON 설정을 변경하지 않는다. */
public record OperatingStandardView(
    @JsonProperty("oprt_std_id") String oprtStdId,
    @JsonProperty("aply_bgn_ym") String aplyBgnYm,
    @JsonProperty("oprt_std_nm") String oprtStdNm,
    @JsonProperty("num_val") BigDecimal numVal,
    @JsonProperty("txt_val") String txtVal,
    @JsonProperty("unit_nm") String unitNm,
    @JsonProperty("use_plc_desc") String usePlcDesc
) {
    public static OperatingStandardView from(OperatingStandardRow row) {
        return new OperatingStandardView(
            row.getOprtStdId(),
            row.getAplyBgnYm(),
            row.getOprtStdNm(),
            row.getNumVal(),
            row.getTxtVal(),
            row.getUnitNm(),
            row.getUsePlcDesc()
        );
    }
}
