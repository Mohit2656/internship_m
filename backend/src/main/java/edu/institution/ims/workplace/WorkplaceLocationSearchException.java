package edu.institution.ims.workplace;

import org.springframework.http.HttpStatus;

class WorkplaceLocationSearchException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    WorkplaceLocationSearchException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() { return status; }
    String code() { return code; }
}
