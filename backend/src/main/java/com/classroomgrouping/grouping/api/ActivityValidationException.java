package com.classroomgrouping.grouping.api;

/** Only predefined, safe field feedback is exposed. */
public final class ActivityValidationException extends RuntimeException {
    private final String field;
    public ActivityValidationException(String field) {
        super(switch (field) {
            case "instructions" -> "Enter nonblank plain text of at most 5000 characters, without invalid Unicode or null characters.";
            case "deadline" -> "Enter a deadline strictly in the future with a timezone offset.";
            case "capacities" -> "Enter a nonempty list of whole numbers of at least 2, totaling all enrolled students (at least 2).";
            case "commandKey" -> "Provide a valid command key.";
            case "fields" -> "Provide only the fields allowed for this activity command.";
            default -> throw new IllegalArgumentException("Unknown activity field");
        });
        this.field = field;
    }
    public String field() { return field; }
    public String code() { return "INVALID_ACTIVITY"; }
    public String safeMessage() { return getMessage(); }
}
