package edu.institution.ims.monthlyprogress;

import org.springframework.http.HttpStatus;

public class MonthlyProgressException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public MonthlyProgressException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
