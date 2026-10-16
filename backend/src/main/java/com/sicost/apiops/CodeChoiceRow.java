package com.sicost.apiops;

/** 공개 조회 XML resultMap의 nullable 저장 값을 보존한다. */
public class CodeChoiceRow {
    private String code;
    private String name;
    private String attr1;
    private String attr2;
    private String attr3;

    public String getCode() {
        return code;
    }

    public void setCode(String value) {
        this.code = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        this.name = value;
    }

    public String getAttr1() {
        return attr1;
    }

    public void setAttr1(String value) {
        this.attr1 = value;
    }

    public String getAttr2() {
        return attr2;
    }

    public void setAttr2(String value) {
        this.attr2 = value;
    }

    public String getAttr3() {
        return attr3;
    }

    public void setAttr3(String value) {
        this.attr3 = value;
    }
}
