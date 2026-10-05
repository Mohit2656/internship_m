package edu.institution.ims.security;

import edu.institution.ims.user.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    public CustomUserDetailsService(UserRepository users) { this.users = users; }
    @Override public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findByEmailIgnoreCase(email).map(UserPrincipal::from).orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }
    public UserPrincipal loadById(Long id) { return users.findById(id).map(UserPrincipal::from).orElseThrow(() -> new UsernameNotFoundException("Account not found")); }
}

