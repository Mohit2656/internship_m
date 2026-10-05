package edu.institution.ims.security;

import edu.institution.ims.user.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

public record UserPrincipal(Long id, String email, String password, boolean active, String role, boolean mustChangePassword) implements UserDetails {
    public static UserPrincipal from(User user) { return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.isActive(), user.getRole().name(), user.isMustChangePassword()); }
    @Override public Collection<SimpleGrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_" + role)); }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return active; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
