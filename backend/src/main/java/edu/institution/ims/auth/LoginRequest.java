package edu.institution.ims.auth;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 128) String password) {
    public LoginRequest {
        if (email != null) email = email.trim().toLowerCase(Locale.ROOT);
    }
}
