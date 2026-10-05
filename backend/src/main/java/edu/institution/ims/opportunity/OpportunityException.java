package edu.institution.ims.opportunity;

import org.springframework.http.HttpStatus;

public class OpportunityException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public OpportunityException(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
