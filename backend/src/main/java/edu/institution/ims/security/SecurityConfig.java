package edu.institution.ims.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.common.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception { return config.getAuthenticationManager(); }
    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> origins) {
        var config = new CorsConfiguration(); config.setAllowedOrigins(origins); config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept")); config.setAllowCredentials(true); config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/api/**", config); return source;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter, PasswordChangeRequiredFilter passwordChangeFilter, ObjectMapper mapper) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(cors -> {}).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/auth/login", "/actuator/health").permitAll()
                        .requestMatchers("/api/v1/auth/change-password").hasAnyRole("STUDENT", "MENTOR")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/mentor/**").hasRole("MENTOR")
                        .requestMatchers("/api/v1/student/**").hasRole("STUDENT")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> write(mapper, res, 401, "UNAUTHORIZED", "Authentication is required"))
                        .accessDeniedHandler((req, res, ex) -> write(mapper, res, 403, "FORBIDDEN", "You do not have permission to access this resource")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(passwordChangeFilter, JwtAuthenticationFilter.class).build();
    }
    private static void write(ObjectMapper mapper, HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), new ApiError(Instant.now(), status, code, message, null));
    }
}
