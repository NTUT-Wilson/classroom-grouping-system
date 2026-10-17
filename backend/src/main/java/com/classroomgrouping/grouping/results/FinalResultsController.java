package com.classroomgrouping.grouping.results;

import com.classroomgrouping.grouping.api.GroupingResults;
import com.classroomgrouping.grouping.api.ResultsUnavailable;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class FinalResultsController {
    private final GroupingResults results;
    public FinalResultsController(GroupingResults results) { this.results = results; }
    @GetMapping("/api/courses/{courseId}/results/roster")
    public GroupingResults.Result roster(Principal principal, @PathVariable UUID courseId) {
        return results.roster(principal, courseId);
    }
    @GetMapping("/api/courses/{courseId}/results/roster.csv")
    public ResponseEntity<byte[]> csv(Principal principal, @PathVariable UUID courseId) {
        var snapshot = results.roster(principal, courseId);
        byte[] body = FinalRosterCsv.format(snapshot);
        return ResponseEntity.ok().header("Content-Type", "text/csv;charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"classroom-groups-" + snapshot.activityId() + ".csv\"")
                .header("Cache-Control", "private, no-store").header("X-Content-Type-Options", "nosniff")
                .body(body);
    }
    @GetMapping("/api/courses/{courseId}/results/my-group")
    public GroupingResults.Result myGroup(Principal principal, @PathVariable UUID courseId) {
        return results.myGroup(principal, courseId);
    }
    @ExceptionHandler(ResultsUnavailable.class)
    public ResponseEntity<Map<String, Object>> unavailable() {
        return ResponseEntity.status(503).header("Cache-Control", "no-store")
                .header("Content-Type", "application/problem+json").body(Map.of(
                        "type", "about:blank", "status", 503, "title", "Final results temporarily unavailable",
                        "code", "RESULTS_UNAVAILABLE"));
    }
}
