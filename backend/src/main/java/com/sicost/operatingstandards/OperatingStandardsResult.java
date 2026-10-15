package com.sicost.operatingstandards;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 현재 기준값 전체 집합과 서버가 조회한 마지막 마감월을 함께 전달한다. */
public record OperatingStandardsResult(
    List<OperatingStandardView> items,
    @JsonProperty("last_closed_ym") String lastClosedYm
) {}
