package com.classroomgrouping.grouping.api;

public final class GroupingValidationException extends RuntimeException {
    private final String field;
    public GroupingValidationException(String field) {
        super(switch (field) {
            case "commandKey" -> "Provide a valid command key.";
            case "fields" -> "Provide only the fields allowed for this grouping command.";
            default -> throw new IllegalArgumentException("Unknown grouping field");
        });
        this.field = field;
    }
    public String field() { return field; }
    public String code() { return "INVALID_GROUPING"; }
    public String safeMessage() { return getMessage(); }
}
