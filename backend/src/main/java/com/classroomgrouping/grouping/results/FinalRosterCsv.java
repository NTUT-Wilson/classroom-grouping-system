package com.classroomgrouping.grouping.results;

import com.classroomgrouping.grouping.api.GroupingResults;
import com.classroomgrouping.grouping.api.ResultsUnavailable;
import com.classroomgrouping.identity.api.StudentIdentityText;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/** Fully buffers the already authorized, complete snapshot before any response is created. */
final class FinalRosterCsv {
    private FinalRosterCsv() {}

    static byte[] format(GroupingResults.Result result) {
        if (result == null || result.activityId() == null || !"FINALIZED".equals(result.status())
                || result.groups().isEmpty()) throw new ResultsUnavailable();
        var rows = new ArrayList<List<String>>();
        var accounts = new HashSet<UUID>();
        var ordinals = new HashSet<Integer>();
        try {
            for (var group : result.groups().stream().sorted(Comparator.comparingInt(GroupingResults.Group::ordinal)).toList()) {
                if (group.ordinal() < 1 || !ordinals.add(group.ordinal()) || group.capacity() < 2
                        || group.members().size() != group.capacity()) throw new ResultsUnavailable();
                StudentIdentityText.requireSafe(group.name());
                // Validate the entire snapshot before sorting or writing any records.
                for (var member : group.members()) {
                    if (member.accountId() == null || !accounts.add(member.accountId())) throw new ResultsUnavailable();
                    StudentIdentityText.requireSafe(member.studentId());
                    StudentIdentityText.requireSafe(member.studentName());
                }
                for (var member : group.members().stream().sorted(Comparator.comparing(GroupingResults.Member::studentId)).toList())
                    rows.add(List.of(group.name(), member.studentId(), member.studentName()));
            }
        } catch (IllegalArgumentException unsafe) {
            throw new ResultsUnavailable();
        }
        return write(rows);
    }

    // The writer's dialect supports arbitrary RFC 4180 cells; production callers first enforce identity policy.
    static byte[] write(List<List<String>> rows) {
        var text = new StringWriter();
        text.write('\ufeff');
        try (var printer = new CSVPrinter(text, CSVFormat.RFC4180)) {
            printer.printRecord("Group name", "Student ID", "Student name");
            for (var row : rows) printer.printRecord(row);
        } catch (IOException failure) {
            throw new ResultsUnavailable();
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }
}
