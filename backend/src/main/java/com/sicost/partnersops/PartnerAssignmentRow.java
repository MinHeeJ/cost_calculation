package com.sicost.partnersops;

/** Explicit persistence projection for partner read results. */
public class PartnerAssignmentRow {
    private String projectName;
    private String developerName;
    private String affiliationName;
    private String jobName;
    private java.math.BigDecimal assignmentRate;
    private java.time.LocalDate endDate;

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String value) {
        this.projectName = value;
    }

    public String getDeveloperName() {
        return developerName;
    }

    public void setDeveloperName(String value) {
        this.developerName = value;
    }

    public String getAffiliationName() {
        return affiliationName;
    }

    public void setAffiliationName(String value) {
        this.affiliationName = value;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String value) {
        this.jobName = value;
    }

    public java.math.BigDecimal getAssignmentRate() {
        return assignmentRate;
    }

    public void setAssignmentRate(java.math.BigDecimal value) {
        this.assignmentRate = value;
    }

    public java.time.LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(java.time.LocalDate value) {
        this.endDate = value;
    }
}
