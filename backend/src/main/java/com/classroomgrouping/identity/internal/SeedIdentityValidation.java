package com.classroomgrouping.identity.internal;

import com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount;
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
        if (value == null || value.isBlank()) invalid();
        for (int i = 0; i < value.length();) {
            char ch = value.charAt(i);
            if (Character.isSurrogate(ch) && !(Character.isHighSurrogate(ch)
                    && i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1)))) invalid();
            int cp = value.codePointAt(i);
            if (Character.isISOControl(cp) || (Character.getType(cp) == Character.FORMAT
                    && cp != 0x200c && cp != 0x200d)) invalid();
            i += Character.charCount(cp);
        }
        int first = value.codePoints().filter(cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp)
                && cp != 0x200c && cp != 0x200d)
                .findFirst().orElseThrow();
        if ("=+-@＝＋－＠﹦﹢﹣﹫".indexOf(first) >= 0) invalid();
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid roster account or unsafe student identity; no changes were committed"); }
}
