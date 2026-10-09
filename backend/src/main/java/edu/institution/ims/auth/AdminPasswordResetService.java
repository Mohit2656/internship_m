package edu.institution.ims.auth;

import edu.institution.ims.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AdminPasswordResetService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    public AdminPasswordResetService(UserRepository users, PasswordEncoder encoder) { this.users = users; this.encoder = encoder; }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PasswordResetResponse reset(Long userId) {
        var user = users.findByIdForPasswordUpdate(userId).orElseThrow(() -> new PasswordResetException(HttpStatus.NOT_FOUND, "Account was not found"));
        if (!user.isActive() || (user.getRole() != Role.STUDENT && user.getRole() != Role.MENTOR))
            throw new PasswordResetException(HttpStatus.CONFLICT, "Only active student and mentor accounts are eligible for password reset");
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        String temporaryPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.resetPassword(encoder.encode(temporaryPassword));
        users.save(user);
        return new PasswordResetResponse(user.getId(), user.getEmail(), temporaryPassword, true);
    }
    public record PasswordResetResponse(Long userId, String email, String temporaryPassword, boolean mustChangePassword) {
        @Override public String toString() { return "PasswordResetResponse[redacted]"; }
    }
}
