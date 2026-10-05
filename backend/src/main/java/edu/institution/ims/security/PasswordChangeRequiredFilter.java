package edu.institution.ims.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.common.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;

@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    public PasswordChangeRequiredFilter(ObjectMapper mapper) { this.mapper = mapper; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/") || path.equals("/api/v1/auth/me") || path.equals("/api/v1/auth/logout") || path.equals("/api/v1/auth/change-password");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                && ("STUDENT".equals(principal.role()) || "MENTOR".equals(principal.role())) && principal.mustChangePassword()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getWriter(), new ApiError(Instant.now(), 403, "PASSWORD_CHANGE_REQUIRED", "Change your temporary password before continuing", null));
            return;
        }
        chain.doFilter(request, response);
    }
}
