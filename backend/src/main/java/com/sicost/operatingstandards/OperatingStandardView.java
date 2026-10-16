package com.sicost.operatingstandards;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;

/** 기존 API의 camelCase와 분리된 기준값 공개 투영이다. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OperatingStandardView(
    String oprtStdId,
    String aplyBgnYm,
    String oprtStdNm,
    BigDecimal numVal,
    String txtVal,
    String unitNm,
    String usePlcDesc
) {
    public static OperatingStandardView from(OperatingStandardRow row) {
        return new OperatingStandardView(
            row.getOprtStdId(), row.getAplyBgnYm(), row.getOprtStdNm(), row.getNumVal(),
            row.getTxtVal(), row.getUnitNm(), row.getUsePlcDesc()
        );
    }
}
