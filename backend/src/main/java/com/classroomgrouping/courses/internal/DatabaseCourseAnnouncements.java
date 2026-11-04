package com.classroomgrouping.courses.internal;

import com.classroomgrouping.courses.api.*;
import com.classroomgrouping.identity.api.SessionActors;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
class DatabaseCourseAnnouncements implements CourseAnnouncements {
    private final JdbcTemplate jdbc;
    private final SessionActors actors;
    DatabaseCourseAnnouncements(JdbcTemplate jdbc, SessionActors actors) { this.jdbc = jdbc; this.actors = actors; }

    // Course row first, then fresh persisted identity/access, receipt, source, catalog, outbox.
    // Shared locks keep reads mutation-free and synchronize with owner maintenance and roster provisioning.
    private SessionActors.Actor access(Principal principal, UUID course, boolean teacher, boolean write) {
        actors.require(principal);
        if (jdbc.query("SELECT id FROM course WHERE id=? FOR " + (write ? "UPDATE" : "SHARE"),
                (rs, row) -> rs.getObject(1, UUID.class), course).isEmpty()) throw new CourseNotAccessible();
        return authorize(principal, course, teacher);
    }
    private SessionActors.Actor authorize(Principal principal, UUID course, boolean teacher) {
        var actor = actors.require(principal);
        String relation = switch (actor.role()) {
            case "TEACHER" -> "course_teacher_assignment";
            case "STUDENT" -> { if (teacher) throw new CourseNotAccessible(); yield "course_enrollment"; }
            default -> throw new CourseNotAccessible();
        };
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM " + relation
                + " WHERE course_id=? AND account_id=?)", Boolean.class, course, actor.accountId()))) throw new CourseNotAccessible();
        return actor;
    }
    public Page list(Principal principal, UUID course, Integer requested, String cursor) {
        var actor = access(principal, course, false, false);
        int limit = requested == null ? 20 : requested;
        if (limit < 1 || limit > 50) throw new AnnouncementValidationException("limit");
        Instant before = null; UUID beforeId = null;
        if (cursor != null) {
            try {
                if (cursor.length() > 400) throw new IllegalArgumentException();
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
                if (parts.length != 4 || !parts[0].equals(actor.accountId().toString()) || !parts[1].equals(course.toString())) throw new IllegalArgumentException();
                before = Instant.parse(parts[2]); beforeId = UUID.fromString(parts[3]);
                if (before.isBefore(Instant.parse("-4712-01-01T00:00:00Z")) || !before.isBefore(Instant.parse("+294277-01-01T00:00:00Z"))) throw new IllegalArgumentException();
            } catch (RuntimeException bad) { throw new AnnouncementValidationException("cursor"); }
        }
        List<Detail> rows = before == null ? jdbc.query("SELECT * FROM course_announcement WHERE course_id=? AND deleted_at IS NULL ORDER BY published_at DESC,id DESC LIMIT ?",
                (rs, row) -> project(rs), course, limit + 1)
                : jdbc.query("SELECT * FROM course_announcement WHERE course_id=? AND deleted_at IS NULL AND (published_at,id)<(?,?) ORDER BY published_at DESC,id DESC LIMIT ?",
                (rs, row) -> project(rs), course, Timestamp.from(before), beforeId, limit + 1);
        String next = null;
        if (rows.size() > limit) {
            rows = List.copyOf(rows.subList(0, limit)); var last = rows.getLast();
            next = Base64.getUrlEncoder().withoutPadding().encodeToString((actor.accountId() + "|" + course + "|" + last.publishedAt() + "|" + last.id()).getBytes(StandardCharsets.UTF_8));
        }
        return new Page(rows, next);
    }
    public Detail detail(Principal principal, UUID course, UUID id) {
        access(principal, course, false, false); return current(course, id);
    }
    private Detail current(UUID course, UUID id) {
        var rows = jdbc.query("SELECT * FROM course_announcement WHERE course_id=? AND id=? AND deleted_at IS NULL", (rs, row) -> project(rs), course, id);
        if (rows.isEmpty()) throw new AnnouncementNotAvailable(); return rows.getFirst();
    }
    private static Detail project(ResultSet rs) throws SQLException {
        return new Detail(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class), rs.getString("title"), rs.getString("body"),
                rs.getTimestamp("published_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getLong("revision"));
    }
    public Acknowledgement create(Principal principal, UUID course, UUID key, String title, String body) {
        return mutate(principal, course, null, key, null, title, body, Command.CREATE);
    }
    public Acknowledgement edit(Principal principal, UUID course, UUID id, UUID key, Long revision, String title, String body) {
        return mutate(principal, course, id, key, revision, title, body, Command.EDIT);
    }
    public Acknowledgement delete(Principal principal, UUID course, UUID id, UUID key, Long revision) {
        return mutate(principal, course, id, key, revision, null, null, Command.DELETE);
    }
    private Acknowledgement mutate(Principal principal, UUID course, UUID id, UUID key, Long expected, String title, String body, Command command) {
        var actor = access(principal, course, true, true);
        if (key == null) throw new AnnouncementValidationException("commandKey");
        if (command != Command.CREATE && (expected == null || expected < 1)) throw new AnnouncementValidationException("expectedRevision");
        if (command != Command.DELETE) { validate(title, "title", 150); validate(body, "body", 10000); }
        String fingerprint = digest(command.name(), course.toString(), Objects.toString(id, ""), Objects.toString(expected, ""), Objects.toString(title, ""), Objects.toString(body, ""));
        UUID target = command == Command.CREATE ? UUID.randomUUID() : id;
        long revision = command == Command.CREATE ? 1 : expected + 1;
        if (revision < 1) throw new AnnouncementConflict("ANNOUNCEMENT_REVISION_CONFLICT");
        int claimed = jdbc.update("INSERT INTO course_announcement_receipt(actor_id,course_id,command,command_key,fingerprint,source_id,revision,deleted) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                actor.accountId(), course, command.name(), key, fingerprint, target, revision, command == Command.DELETE);
        // A competing cross-course receipt may have waited independently of this course lock.
        authorize(principal, course, true);
        if (claimed == 0) {
            var saved = jdbc.queryForMap("SELECT * FROM course_announcement_receipt WHERE actor_id=? AND command=? AND command_key=?", actor.accountId(), command.name(), key);
            if (!fingerprint.equals(saved.get("fingerprint"))) throw new AnnouncementConflict("COMMAND_KEY_REUSED");
            return new Acknowledgement((UUID) saved.get("source_id"), ((Number) saved.get("revision")).longValue(), (Boolean) saved.get("deleted"));
        }
        if (command == Command.CREATE) {
            jdbc.update("INSERT INTO course_announcement(id,course_id,title,body,revision) VALUES (?,?,?,?,1)", target, course, title, body);
        } else {
            var current = current(course, target);
            if (current.revision() != expected) throw new AnnouncementConflict("ANNOUNCEMENT_REVISION_CONFLICT");
            int changed = command == Command.DELETE
                    ? jdbc.update("UPDATE course_announcement SET title=NULL,body=NULL,revision=?,updated_at=clock_timestamp(),deleted_at=clock_timestamp() WHERE id=? AND course_id=? AND revision=? AND deleted_at IS NULL", revision, target, course, expected)
                    : jdbc.update("UPDATE course_announcement SET title=?,body=?,revision=?,updated_at=clock_timestamp() WHERE id=? AND course_id=? AND revision=? AND deleted_at IS NULL", title, body, revision, target, course, expected);
            if (changed != 1) throw new AnnouncementConflict("ANNOUNCEMENT_REVISION_CONFLICT");
        }
        jdbc.update("INSERT INTO course_announcement_catalog(course_id,revision) VALUES (?,1) ON CONFLICT(course_id) DO UPDATE SET revision=course_announcement_catalog.revision+1", course);
        jdbc.update("INSERT INTO course_announcement_outbox(event_id,course_id,source_id,revision,change_kind) VALUES (?,?,?,?,?)", UUID.randomUUID(), course, target, revision, command.name());
        AnnouncementCompaction.compact(jdbc, course, 64);
        return new Acknowledgement(target, revision, command == Command.DELETE);
    }
    public Status status(Principal principal, UUID course, UUID key, Command command) {
        var actor = access(principal, course, true, false);
        if (command == null) throw new AnnouncementValidationException("command");
        var rows = jdbc.query("SELECT source_id,revision,deleted FROM course_announcement_receipt WHERE actor_id=? AND course_id=? AND command=? AND command_key=?",
                (rs, row) -> new Acknowledgement(rs.getObject(1, UUID.class), rs.getLong(2), rs.getBoolean(3)), actor.accountId(), course, command.name(), key);
        return new Status(!rows.isEmpty(), rows.isEmpty() ? null : rows.getFirst());
    }
    static void validate(String text, String field, int max) {
        if (text == null || text.codePointCount(0, text.length()) > max || text.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x85 || c == 0xfeff)) throw new AnnouncementValidationException(field);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0 || Character.isLowSurrogate(c)) throw new AnnouncementValidationException(field);
            if (Character.isHighSurrogate(c) && (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))) throw new AnnouncementValidationException(field);
        }
    }
    private static String digest(String... values) {
        try {
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            for (String value : values) { byte[] encoded = value.getBytes(StandardCharsets.UTF_8); out.writeInt(encoded.length); out.write(encoded); }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
