package com.magister.backend.domain.user.security;

import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepository users;
    @Override
    public UserDetails loadUserByUsername(String email) {
        com.magister.backend.domain.user.entity.User user = users.findByEmail(UserService.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return User.withUsername(user.getEmail()).password(user.getPasswordHash()).roles("USER").build();
    }
}
