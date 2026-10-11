package edu.institution.ims.auth;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 128) String password, edu.institution.ims.user.Role role) {
    public LoginRequest(String email, String password) { this(email, password, null); }
    public LoginRequest {
        if (email != null) email = email.trim().toLowerCase(Locale.ROOT);
    }
    @Override public String toString() { return "LoginRequest[redacted]"; }
}
