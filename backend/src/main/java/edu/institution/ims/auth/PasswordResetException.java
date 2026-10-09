package edu.institution.ims.auth;
import org.springframework.http.HttpStatus;
public class PasswordResetException extends RuntimeException {
    private final HttpStatus status;
    public PasswordResetException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus getStatus() { return status; }
}
