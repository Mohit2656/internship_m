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
    public LoginResult login(LoginRequest request) {
        var user = users.findByEmailIgnoreCase(request.email()).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!user.isActive()) throw new InactiveAccountException();
        var authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        var principal = (UserPrincipal) authentication.getPrincipal();
        return new LoginResult(jwt.create(principal), response(principal));
    }
    public CurrentUserResponse response(UserPrincipal principal) {
        String label = switch (principal.role()) { case "ADMIN" -> "III Cell Incharge"; case "STUDENT" -> "Student"; case "HOD" -> "HOD"; case "MENTOR" -> "Mentor"; default -> principal.role(); };
        return new CurrentUserResponse(principal.id(), principal.email(), principal.role(), label, principal.mustChangePassword());
    }
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) throw new IllegalArgumentException("Passwords do not match");
        var user = users.findById(principal.id()).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        user.changePassword(encoder.encode(request.newPassword()));
        users.save(user);
    }
    public record LoginResult(String token, CurrentUserResponse user) {}
}
