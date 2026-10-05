package edu.institution.ims.mentor;

public class MentorAccountConflictException extends RuntimeException {
    private final String code;
    public MentorAccountConflictException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}
