package com.sicost.operatingstandards;

import java.math.BigDecimal;

/** 기존 기준값 테이블을 명시적 XML resultMap으로 읽는 행이다. */
public class OperatingStandardRow {
    private String oprtStdId;
    private String aplyBgnYm;
    private String oprtStdNm;
    private BigDecimal numVal;
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

    public BigDecimal getNumVal() {
        return numVal;
    }

    public void setNumVal(BigDecimal value) {
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
