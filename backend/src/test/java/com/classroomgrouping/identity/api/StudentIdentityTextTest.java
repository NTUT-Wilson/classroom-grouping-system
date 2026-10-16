package com.classroomgrouping.identity.api;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudentIdentityTextTest {
    @Test void rejectsEveryFormulaPrefixAfterEffectiveWhitespaceAndJoiners() {
        "=+-@＝＋－＠﹦﹢﹣﹫".codePoints().forEach(cp -> {
            for (String prefix : new String[]{"", "  ", "\u00a0\u200c\u200d", "\u3000"})
                assertThrows(IllegalArgumentException.class, () -> StudentIdentityText.requireSafe(prefix + new String(Character.toChars(cp)) + "1"));
        });
    }
    @Test void rejectsEmptyMalformedControlsAndOtherFormatsButPreservesSafeValues() {
        for (String value : Arrays.asList(null, "", " ", "\u00a0", "\u200c\u200d", "\ud800", "\udc00", "A\ud800B", "A\udc00B",
                "a\rb", "a\nb", "a\tb", "a\u0000b", "a\u007fb", "a\u0085b", "a\ufeffb", "a\u202eb"))
            assertThrows(IllegalArgumentException.class, () -> StudentIdentityText.requireSafe(value));
        for (String value : new String[]{"0000123", " 陳, \"Example\" 🧑🏽‍🎓 ", "é Nguyễn", "\u00a0Name", "a\u200cb"})
            assertDoesNotThrow(() -> StudentIdentityText.requireSafe(value));
    }
}
