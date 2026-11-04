package com.classroomgrouping.courses.api;

public class AnnouncementValidationException extends RuntimeException {
    private final String field;
    public AnnouncementValidationException(String field) { this.field = field; }
    public String field() { return field; }
}
