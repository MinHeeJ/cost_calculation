package com.sicost.shared.monthclose;

/** XML resultMap으로 nullable DB 필드를 명시적으로 받는 조회 행이다. */
public class MonthCloseRow {
    private String month;
    private String status;

    public String getMonth() {
        return month;
    }

    public void setMonth(String value) {
        this.month = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        this.status = value;
    }
}
