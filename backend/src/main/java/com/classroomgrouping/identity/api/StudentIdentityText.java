package com.classroomgrouping.identity.api;

/** Shared, stateless safety policy for provisioned identities and exported cells. Values are never rewritten. */
public final class StudentIdentityText {
    private StudentIdentityText() {}

    public static void requireSafe(String value) {
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
                && cp != 0x200c && cp != 0x200d).findFirst().orElse(-1);
        if (first == -1 || "=+-@＝＋－＠﹦﹢﹣﹫".indexOf(first) >= 0) invalid();
    }

    private static void invalid() { throw new IllegalArgumentException("Unsafe or missing student identity text"); }
}
