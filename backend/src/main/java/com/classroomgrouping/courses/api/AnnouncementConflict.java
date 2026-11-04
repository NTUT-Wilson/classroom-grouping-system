package com.classroomgrouping.courses.api;

public class AnnouncementConflict extends RuntimeException {
    private final String code;
    public AnnouncementConflict(String code) { this.code = code; }
    public String code() { return code; }
}
