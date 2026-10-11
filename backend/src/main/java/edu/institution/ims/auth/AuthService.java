package edu.institution.ims.auth;

import edu.institution.ims.exception.InactiveAccountException;
import edu.institution.ims.security.*;
import edu.institution.ims.user.UserRepository;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users; private final AuthenticationManager authenticationManager; private final JwtService jwt; private final PasswordEncoder encoder;
    public AuthService(UserRepository users, AuthenticationManager authenticationManager, JwtService jwt, PasswordEncoder encoder) { this.users = users; this.authenticationManager = authenticationManager; this.jwt = jwt; this.encoder = encoder; }
    @org.springframework.transaction.annotation.Transactional
    public LoginResult login(LoginRequest request) {
        var user = users.findByEmailForPasswordUpdate(request.email()).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!user.isActive()) throw new InactiveAccountException();
        var authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        var principal = (UserPrincipal) authentication.getPrincipal();
        var current = users.findByIdForPasswordUpdate(principal.id()).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!current.isActive() || !encoder.matches(request.password(), current.getPasswordHash())) throw new BadCredentialsException("Invalid credentials");
        if (request.role() != null && request.role() != current.getRole())
            throw new org.springframework.security.access.AccessDeniedException("Account does not match the selected login role");
        principal = UserPrincipal.from(current);
        return new LoginResult(jwt.create(principal), response(principal));
    }
    public CurrentUserResponse response(UserPrincipal principal) {
        String label = switch (principal.role()) { case "ADMIN" -> "III Cell Incharge"; case "STUDENT" -> "Student"; case "HOD" -> "HOD"; case "MENTOR" -> "Mentor"; default -> principal.role(); };
        return new CurrentUserResponse(principal.id(), principal.email(), principal.role(), label, principal.mustChangePassword());
    }
    @org.springframework.transaction.annotation.Transactional
    public String changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) throw new IllegalArgumentException("Passwords do not match");
        var user = users.findByIdForPasswordUpdate(principal.id()).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (user.getTokenVersion() != principal.tokenVersion()) throw new BadCredentialsException("Invalid credentials");
        if (request.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new IllegalArgumentException("Password must contain at most 72 UTF-8 bytes");
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) throw new IllegalArgumentException("Choose a password different from your current password");
        user.changePassword(encoder.encode(request.newPassword()));
        users.save(user);
        return jwt.create(UserPrincipal.from(user));
    }
    public record LoginResult(String token, CurrentUserResponse user) { @Override public String toString() { return "LoginResult[redacted]"; } }
}
