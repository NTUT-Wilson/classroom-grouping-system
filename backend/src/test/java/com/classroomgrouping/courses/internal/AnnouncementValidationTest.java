package com.classroomgrouping.courses.internal;

import com.classroomgrouping.courses.api.AnnouncementValidationException;
import com.classroomgrouping.courses.api.CourseAnnouncementsController;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementValidationTest {
    @Test void codePointsAndExactPlainTextPermitSupplementaryCharactersWithoutNormalizing() {
        assertDoesNotThrow(()->DatabaseCourseAnnouncements.validate("😀".repeat(150),"title",150));
        assertDoesNotThrow(()->DatabaseCourseAnnouncements.validate("😀".repeat(10000),"body",10000));
        assertDoesNotThrow(()->DatabaseCourseAnnouncements.validate("  <script>plain</script>\r\n# markdown e\u0301  ","body",10000));
        assertThrows(AnnouncementValidationException.class,()->DatabaseCourseAnnouncements.validate("😀".repeat(151),"title",150));
        assertThrows(AnnouncementValidationException.class,()->DatabaseCourseAnnouncements.validate("e\u0301".repeat(76),"title",150));
    }
    @Test void rejectsNullBlankNulAndMalformedSurrogates() {
        for(String invalid:Arrays.asList(null,""," \t\r\n","\u0085\u00a0\ufeff","\u001c\u001d\u001e\u001f","\ud800","\udc00","a\ud800b","a\u0000b"))
            assertThrows(AnnouncementValidationException.class,()->DatabaseCourseAnnouncements.validate(invalid,"body",10000));
        for(int cp=0;cp<=0x10ffff;cp++) if(Character.isWhitespace(cp)||Character.isSpaceChar(cp)||cp==0x85||cp==0xfeff) {
            String whitespace=new String(Character.toChars(cp));
            assertThrows(AnnouncementValidationException.class,()->DatabaseCourseAnnouncements.validate(whitespace,"title",150));
        }
    }
    @Test void strictDtosRejectUnknownFieldsAndRedactContentFromDiagnosticStrings() {
        var mapper=JsonMapper.builder().build();
        for(Class<?> type: new Class<?>[]{CourseAnnouncementsController.Creation.class,CourseAnnouncementsController.Edit.class,CourseAnnouncementsController.Deletion.class})
            assertThrows(RuntimeException.class,()->mapper.readValue("{\"extra\":\"secret-content\"}",type));
        var request=mapper.readValue("{\"title\":\"secret-content\",\"body\":\"secret-body\"}",CourseAnnouncementsController.Creation.class);
        assertFalse(request.toString().contains("secret"));
    }
    @Test void maintenanceRequiresExplicitBoundedCommandBeforeAccessingAnyDatabase() {
        for(String[] args:new String[][]{{},{"--compact"},{"--compact","0"},{"--compact","1001"},{"--serve","1"},{"--compact","1","extra"}})
            assertThrows(IllegalArgumentException.class,()->AnnouncementMaintenance.main(args));
    }
}
