package com.sicost.partners;

/** Feature-local errors preserve the common envelope and camelCase field names. */
public class PartnerCatalogException extends RuntimeException {
    private final int status;
    private final String code;
    private final String field;

    public PartnerCatalogException(int status, String code, String field, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public int status() { return status; }
    public String code() { return code; }
    public String field() { return field; }
}
