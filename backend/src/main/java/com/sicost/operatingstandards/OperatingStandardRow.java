package com.sicost.operatingstandards;

/** 기존 기준값 컬럼을 명시적으로 매핑하는 영속 행이다. */
public class OperatingStandardRow {
    private String oprtStdId;
    private String aplyBgnYm;
    private String oprtStdNm;
    private java.math.BigDecimal numVal;
    private String txtVal;
    private String unitNm;
    private String usePlcDesc;
    private String oprtStdDesc;

    public String getOprtStdId() {
        return oprtStdId;
    }

    public void setOprtStdId(String value) {
        this.oprtStdId = value;
    }

    public String getAplyBgnYm() {
        return aplyBgnYm;
    }

    public void setAplyBgnYm(String value) {
        this.aplyBgnYm = value;
    }

    public String getOprtStdNm() {
        return oprtStdNm;
    }

    public void setOprtStdNm(String value) {
        this.oprtStdNm = value;
    }

    public java.math.BigDecimal getNumVal() {
        return numVal;
    }

    public void setNumVal(java.math.BigDecimal value) {
        this.numVal = value;
    }

    public String getTxtVal() {
        return txtVal;
    }

    public void setTxtVal(String value) {
        this.txtVal = value;
    }

    public String getUnitNm() {
        return unitNm;
    }

    public void setUnitNm(String value) {
        this.unitNm = value;
    }

    public String getUsePlcDesc() {
        return usePlcDesc;
    }

    public void setUsePlcDesc(String value) {
        this.usePlcDesc = value;
    }

    public String getOprtStdDesc() {
        return oprtStdDesc;
    }

    public void setOprtStdDesc(String value) {
        this.oprtStdDesc = value;
    }
}
