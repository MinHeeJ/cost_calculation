package com.sicost.partnersops;

import java.util.List;

/** Business failure retains structured exclusion reasons without exposing storage errors. */
public class PartnerActivityException extends RuntimeException {
    private final int status;
    private final String field;
    private final List<PartnerDisableResult.Exclusion> excluded;

    public PartnerActivityException(int status, String field, String message,
        List<PartnerDisableResult.Exclusion> excluded) {
        super(message);
        this.status = status;
        this.field = field;
        this.excluded = excluded;
    }

    public int status() {
        return status;
    }

    public String field() {
        return field;
    }

    public List<PartnerDisableResult.Exclusion> excluded() {
        return excluded;
    }
}
