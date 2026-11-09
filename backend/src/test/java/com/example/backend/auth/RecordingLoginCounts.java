package com.example.backend.auth;

import com.example.backend.audit.domain.AuditRefusalReason;
import com.example.backend.auth.application.LoginCounts;
import java.util.ArrayList;
import java.util.List;

/**
 * A Login's counts for tests, standing in for the Micrometer meter: each count moved, in order,
 * spelled as the meter's series are — {@code password:success},
 * {@code password:refused:BAD_CREDENTIALS} — so a test asserts exactly which counts an ending
 * moved.
 */
public final class RecordingLoginCounts implements LoginCounts {

    private final List<String> moved = new ArrayList<>();

    @Override
    public void signedIn() {
        moved.add("password:success");
    }

    @Override
    public void refused(AuditRefusalReason reason) {
        moved.add("password:refused:" + reason.name());
    }

    /** Every count moved so far, in order. */
    public List<String> moved() {
        return List.copyOf(moved);
    }
}
