package com.sicost.partnersops;

import java.util.List;

/** Distinguishes successful state transitions from unchanged business exclusions. */
public record PartnerDisableResult(
    List<Long> disabledIds,
    int disabledCount,
    int inUseCount,
    int alreadyDisabledCount,
    List<Exclusion> excluded
) {
    public record Exclusion(
        long id,
        String name,
        String reason,
        int ongoingProjectCount,
        int affiliatedDeveloperCount
    ) { }
}
