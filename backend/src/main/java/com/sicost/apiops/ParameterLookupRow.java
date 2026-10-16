package com.sicost.apiops;

/** 공개 조회 XML resultMap의 nullable 저장 값을 보존한다. */
public class ParameterLookupRow {
    private String oprtStdId;
    private String aplyBgnYm;
    private String oprtStdNm;
    private java.math.BigDecimal numVal;
    private String txtVal;
    private String unitNm;
    private String usePlcDesc;

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
}
