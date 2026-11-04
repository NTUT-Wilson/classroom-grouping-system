package com.classroomgrouping.courses.internal;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit shell-only entry point. Does not boot Spring, listen on HTTP, migrate, provision, or schedule work. */
public final class AnnouncementMaintenance {
    private AnnouncementMaintenance() {}
    public static void main(String[] args) {
        if (args.length != 2 || !"--compact".equals(args[0])) throw new IllegalArgumentException("Usage: --compact <maximum-courses:1..1000>");
        int maximum = Integer.parseInt(args[1]);
        if (maximum < 1 || maximum > 1000) throw new IllegalArgumentException("Maximum courses must be 1..1000");
        var source = new DriverManagerDataSource(required("SPRING_DATASOURCE_URL"), required("SPRING_DATASOURCE_USERNAME"), required("SPRING_DATASOURCE_PASSWORD"));
        var jdbc = new JdbcTemplate(source); var transactions = new TransactionTemplate(new JdbcTransactionManager(source));
        var courses = jdbc.query("SELECT DISTINCT course_id FROM course_announcement_outbox old WHERE EXISTS (SELECT 1 FROM course_announcement_outbox newer WHERE newer.source_id=old.source_id AND newer.revision>old.revision) ORDER BY course_id LIMIT ?", (rs,row) -> rs.getObject(1, UUID.class), maximum);
        int removed = 0;
        for (UUID course : courses) removed += transactions.execute(status -> {
            jdbc.query("SELECT id FROM course WHERE id=? FOR UPDATE", (rs,row) -> rs.getObject(1, UUID.class), course);
            return AnnouncementCompaction.compact(jdbc, course, 1000);
        });
        System.out.println("Announcement maintenance completed; courses=" + courses.size() + ", superseded events removed=" + removed);
    }
    private static String required(String name) {
        String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalArgumentException("Required environment variable: " + name); return value;
    }
}
