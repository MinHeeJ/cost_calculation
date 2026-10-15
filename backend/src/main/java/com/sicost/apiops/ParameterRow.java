package com.sicost.apiops;

import java.math.BigDecimal;

/** 명시적 XML resultMap으로 nullable DB 값을 받는 소비 API 전용 행이다. */
public class ParameterRow {
    private String key;
    private String month;
    private String name;
    private BigDecimal number;
    private String text;
    private String unit;
    private String usage;

    public String getKey() {
        return key;
    }

    public void setKey(String value) {
        this.key = value;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String value) {
        this.month = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        this.name = value;
    }

    public BigDecimal getNumber() {
        return number;
    }

    public void setNumber(BigDecimal value) {
        this.number = value;
    }

    public String getText() {
        return text;
    }

    public void setText(String value) {
        this.text = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String value) {
        this.unit = value;
    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String value) {
        this.usage = value;
    }
}
