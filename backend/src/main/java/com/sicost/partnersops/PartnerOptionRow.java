package com.sicost.partnersops;

/** Explicit persistence projection for partner read results. */
public class PartnerOptionRow {
    private Long id;
    private String no;
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public String getNo() {
        return no;
    }

    public void setNo(String value) {
        this.no = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        this.name = value;
    }
}
