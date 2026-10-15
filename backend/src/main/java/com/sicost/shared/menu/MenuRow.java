package com.sicost.shared.menu;

/** XML resultMap으로 nullable DB 필드를 명시적으로 받는 조회 행이다. */
public class MenuRow {
    private Long menuId;
    private Long parentId;
    private String name;
    private String screenId;
    private String icon;
    private String mobileYn;
    private Integer sortSeq;

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long value) {
        this.menuId = value;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long value) {
        this.parentId = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        this.name = value;
    }

    public String getScreenId() {
        return screenId;
    }

    public void setScreenId(String value) {
        this.screenId = value;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String value) {
        this.icon = value;
    }

    public String getMobileYn() {
        return mobileYn;
    }

    public void setMobileYn(String value) {
        this.mobileYn = value;
    }

    public Integer getSortSeq() {
        return sortSeq;
    }

    public void setSortSeq(Integer value) {
        this.sortSeq = value;
    }
}
