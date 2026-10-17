package com.classroomgrouping.identity.internal;

import com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount;
import com.classroomgrouping.identity.api.StudentIdentityText;
import java.nio.charset.StandardCharsets;

final class SeedIdentityValidation {
    private SeedIdentityValidation() {}
    static void validate(SeedAccount account) {
        if (account.username() == null || account.username().isBlank() || account.username().length() > 100
                || account.password() == null || account.password().length() < 12
                || account.password().getBytes(StandardCharsets.UTF_8).length > 72
                || !("TEACHER".equals(account.role()) || "STUDENT".equals(account.role()))) invalid();
        if ("STUDENT".equals(account.role())) {
            text(account.studentId()); text(account.studentName());
        } else if (account.studentId() != null || account.studentName() != null) invalid();
    }
    // Reject controls/format characters and formula prefixes, including full-width locale forms.
    // Leading whitespace is inspected, never trimmed or normalized in stored data.
    static void text(String value) {
        StudentIdentityText.requireSafe(value);
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid roster account or unsafe student identity; no changes were committed"); }
}
