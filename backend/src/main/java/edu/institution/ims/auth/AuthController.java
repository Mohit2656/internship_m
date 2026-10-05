package edu.institution.ims.auth;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth; private final String cookieName; private final boolean secure; private final long expiration;
    public AuthController(AuthService auth, @Value("${app.jwt.cookie-name:ims_auth}") String cookieName, @Value("${app.jwt.cookie-secure:false}") boolean secure, @Value("${app.jwt.expiration-seconds:28800}") long expiration) {
        this.auth = auth; this.cookieName = cookieName; this.secure = secure; this.expiration = expiration;
    }
    @PostMapping("/login") public ResponseEntity<CurrentUserResponse> login(@Valid @RequestBody LoginRequest request) {
        var result = auth.login(request);
        var cookie = ResponseCookie.from(cookieName, result.token()).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(Duration.ofSeconds(expiration)).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(result.user());
    }
    @GetMapping("/me") public CurrentUserResponse me(@AuthenticationPrincipal UserPrincipal principal) { return auth.response(principal); }
    @PostMapping("/change-password") public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(principal, request); return ResponseEntity.noContent().build();
    }
    @PostMapping("/logout") public ResponseEntity<Void> logout() {
        var cookie = ResponseCookie.from(cookieName, "").httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(Duration.ZERO).build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }
}
