package edu.institution.ims.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Arrays;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt; private final CustomUserDetailsService users; private final String cookieName;
    public JwtAuthenticationFilter(JwtService jwt, CustomUserDetailsService users, @Value("${app.jwt.cookie-name:ims_auth}") String cookieName) {
        this.jwt = jwt; this.users = users; this.cookieName = cookieName;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String token = request.getCookies() == null ? null : Arrays.stream(request.getCookies()).filter(c -> cookieName.equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null && jwt.valid(token)) {
            try {
                var principal = users.loadById(jwt.userId(token));
                if (principal.isEnabled()) SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            } catch (UsernameNotFoundException ignored) { }
        }
        chain.doFilter(request, response);
    }
}

