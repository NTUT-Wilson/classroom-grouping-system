package com.classroomgrouping.identity.internal;

import com.classroomgrouping.identity.api.AccountProvisioning.SeedAccount;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeedIdentityValidationTest {
    @Test void preservesLeadingZerosAndSafeUnicodeVerbatim() {
        assertDoesNotThrow(() -> SeedIdentityValidation.validate(new SeedAccount(
                "synthetic", "synthetic-password", "STUDENT", "00001234", "陳, \"Example\" 🧑🏽‍🎓")));
        assertDoesNotThrow(() -> SeedIdentityValidation.text("é Nguyễn"));
    }
    @Test void rejectsFormulaPrefixesControlsAndLocaleVariantsWithoutRewriting() {
        for (String unsafe : new String[]{"=SUM(1)", "+1", "-1", "@SUM(1)", "  =1", "\u00a0＠SUM(1)",
                "＝1", "＋1", "－1", "﹦1", "\t123", "12\n34", "\r123", "12\u000034", "\ufeff123", "\ud800"}) {
            assertThrows(IllegalArgumentException.class, () -> SeedIdentityValidation.text(unsafe));
        }
    }
}
