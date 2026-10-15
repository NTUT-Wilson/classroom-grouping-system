package com.classroomgrouping.grouping.api;

public final class GroupingConflict extends RuntimeException {
    private final String code;
    public GroupingConflict(String code) {
        super(switch (code) {
            case "GROUP_NOT_EMPTY" -> "This group is no longer empty";
            case "ALREADY_GROUPED" -> "You already belong to a group in this activity.";
            case "ACTIVITY_CLOSED" -> "Grouping has closed. Refresh to check current status.";
            case "COMMAND_KEY_REUSED" -> "This command key was already used with different input.";
            default -> throw new IllegalArgumentException("Unknown grouping conflict");
        });
        this.code = code;
    }
    public String code() { return code; }
    public String safeMessage() { return getMessage(); }
}
