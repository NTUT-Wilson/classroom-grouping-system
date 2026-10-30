package com.classroomgrouping.grouping.api;

public final class GroupingValidationException extends RuntimeException {
    private final String field;
    public GroupingValidationException(String field) {
        super(switch (field) {
            case "commandKey" -> "Provide a valid command key.";
            case "fields" -> "Provide only the fields allowed for this grouping command.";
            case "decision" -> "Choose a response allowed for this request.";
            case "recipientId" -> "Provide a valid recipient account ID.";
            case "targetMemberId" -> "Choose a different current member of this group.";
            case "cursor" -> "Provide a valid history cursor for this course and account.";
            case "limit" -> "Choose a page size between 1 and 50.";
            default -> throw new IllegalArgumentException("Unknown grouping field");
        });
        this.field = field;
    }
    public String field() { return field; }
    public String code() { return "INVALID_GROUPING"; }
    public String safeMessage() { return getMessage(); }
}
