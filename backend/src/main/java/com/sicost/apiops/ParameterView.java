package com.sicost.apiops;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** 요청 월에 적용되는 한 행을 기존 메뉴의 JSON 규칙과 독립적으로 직렬화한다. */
public record ParameterView(
    @JsonProperty("oprt_std_id") String key,
    @JsonProperty("aply_bgn_ym") String month,
    @JsonProperty("oprt_std_nm") String name,
    @JsonProperty("num_val") BigDecimal number,
    @JsonProperty("txt_val") String text,
    @JsonProperty("unit_nm") String unit,
    @JsonProperty("use_plc_desc") String usage
) {
}
