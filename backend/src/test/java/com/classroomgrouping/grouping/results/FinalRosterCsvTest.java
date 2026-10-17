package com.classroomgrouping.grouping.results;

import com.classroomgrouping.grouping.api.GroupingResults.*;
import com.classroomgrouping.grouping.api.ResultsUnavailable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinalRosterCsvTest {
    private Member member(String id, String name) { return new Member(UUID.randomUUID(), id, name, "excluded introduction"); }
    private Group group(int ordinal, Member... members) { return new Group(UUID.randomUUID(), ordinal, "Group " + ordinal, members.length, null, List.of(members)); }
    private Result result(Group... groups) { return new Result(UUID.randomUUID(), "FINALIZED", List.of(groups)); }
    private String text(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }

    @Test void exactBytesPreserveUnicodeAndIdsAndSortNumericGroupsThenTextualIds() {
        var snapshot = result(group(10, member("2", "Ten"), member("10", "Other")),
                group(2, member("002", "陳, \"Example\" 🧑🏽‍🎓"), member("0001", " First ")));
        byte[] actual = FinalRosterCsv.format(snapshot);
        assertEquals("\ufeffGroup name,Student ID,Student name\r\n"
                + "Group 2,0001,\" First \"\r\nGroup 2,002,\"陳, \"\"Example\"\" 🧑🏽‍🎓\"\r\n"
                + "Group 10,10,Other\r\nGroup 10,2,Ten\r\n", text(actual));
        assertArrayEquals(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf}, Arrays.copyOf(actual, 3));
        assertArrayEquals(actual, FinalRosterCsv.format(snapshot));
        assertFalse(text(actual).contains("excluded introduction"));
    }
    @Test void writerEscapesLineBreaksIndependentlyOfProductionIdentityPolicy() {
        assertEquals("\ufeffGroup name,Student ID,Student name\r\n\"Group\r\n2\",001,\"Line\nName\"\r\n",
                text(FinalRosterCsv.write(List.of(List.of("Group\r\n2", "001", "Line\nName")))));
        assertThrows(ResultsUnavailable.class, () -> FinalRosterCsv.format(result(group(2, member("001", "Good"), member("002", "Line\nName")))));
    }
    @Test void rejectsWholeSnapshotIncludingLastUnsafeRowAndMissingCells() {
        for (String unsafe : Arrays.asList(null, "", "\u200c\u200d\u00a0", "\ud800", "\udc00", "\tUnsafe", "\u00a0\u200d﹫Formula", "=1")) {
            assertThrows(ResultsUnavailable.class, () -> FinalRosterCsv.format(result(group(1, member("001", "Safe"), member("999", unsafe)))));
            assertThrows(ResultsUnavailable.class, () -> FinalRosterCsv.format(result(group(1, member("001", "Safe"), member(unsafe, "Safe")))));
        }
        var good = group(1, member("001", "Safe"), member("002", "Safe"));
        assertThrows(ResultsUnavailable.class, () -> FinalRosterCsv.format(new Result(UUID.randomUUID(), "OPEN", List.of(good))));
        assertThrows(ResultsUnavailable.class, () -> FinalRosterCsv.format(result()));
        assertThrows(ResultsUnavailable.class, () -> FinalRosterCsv.format(new Result(null, "FINALIZED", List.of(good))));
        assertThrows(ResultsUnavailable.class, () -> FinalRosterCsv.format(result(new Group(good.id(), 1, "=Group", 2, null, good.members()))));
        assertThrows(ResultsUnavailable.class, () -> FinalRosterCsv.format(result(new Group(good.id(), 1, "Group 1", 3, null, good.members()))));
        assertThrows(ResultsUnavailable.class, () -> FinalRosterCsv.format(result(good, good)));
    }
}
