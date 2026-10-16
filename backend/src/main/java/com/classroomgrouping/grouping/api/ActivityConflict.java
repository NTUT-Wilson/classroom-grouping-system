package com.classroomgrouping.grouping.api;

public final class ActivityConflict extends RuntimeException {
    private final String code;
    public ActivityConflict(String code) {
        super(switch (code) {
            case "ACTIVITY_EXISTS" -> "This course already has a grouping activity.";
            case "COMMAND_KEY_REUSED" -> "This command key was already used with different input.";
            case "ACTIVITY_CLOSED" -> "Grouping has closed. The deadline cannot be extended.";
            case "DEADLINE_NOT_LATER" -> "Enter a deadline later than the current saved deadline.";
            default -> throw new IllegalArgumentException("Unknown activity conflict");
        });
        this.code = code;
    }
    public String code() { return code; }
    public String safeMessage() { return getMessage(); }
}
