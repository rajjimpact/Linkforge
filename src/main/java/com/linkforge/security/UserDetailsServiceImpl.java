package com.linkforge.security;

import com.linkforge.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Loads user by UUID (stored as subject in JWT).
 * Uses UUID lookup to prevent email enumeration on token validation.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        try {
            return userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        } catch (IllegalArgumentException e) {
            // Try by email as fallback (for Spring Security form login)
            return userRepository.findByEmail(userId)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        }
    }
}
