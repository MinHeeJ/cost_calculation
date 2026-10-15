package com.sicost.operatingstandards;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.sicost.common.ValidationException;
import java.math.BigDecimal;

/** 제출 여부를 유지하여 반대 종류의 명시 null 제출도 거부하는 입력이다. */
public class SaveOperatingStandardRequest {
    private String month;
    private BigDecimal number;
    private String text;
    private boolean numberPresent;
    private boolean textPresent;

    @JsonSetter("aply_bgn_ym")
    public void setMonth(String value) {
        month = value;
    }

    @JsonSetter("num_val")
    public void setNumber(BigDecimal value) {
        numberPresent = true;
        number = value;
    }

    @JsonSetter("txt_val")
    public void setText(String value) {
        textPresent = true;
        text = value;
    }

    @JsonAnySetter
    public void unknown(String key, Object value) {
        throw new ValidationException(key, "허용되지 않는 항목입니다.");
    }

    public String month() {
        return month;
    }

    public BigDecimal number() {
        return number;
    }

    public String text() {
        return text;
    }

    public boolean numberPresent() {
        return numberPresent;
    }

    public boolean textPresent() {
        return textPresent;
    }
}
