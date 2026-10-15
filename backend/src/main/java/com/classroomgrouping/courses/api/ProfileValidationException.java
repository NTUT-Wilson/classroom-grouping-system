package com.classroomgrouping.courses.api;

/** Fixed feedback; submitted introduction text must never enter an error response or log. */
public final class ProfileValidationException extends RuntimeException {
    public static final String MESSAGE = "Enter plain text of at most 500 characters, without invalid Unicode or null characters.";
    public ProfileValidationException() { super(MESSAGE); }
}
