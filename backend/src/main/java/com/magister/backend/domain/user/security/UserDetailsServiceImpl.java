package com.magister.backend.domain.user.security;

import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.service.support.EmailNormalizer;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepository userRepository;

    /** 인증에 필요한 공개 프로필도 함께 전달해 비밀번호 확인 후 동일 사용자를 재조회하지 않는다. */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        User user =
                userRepository.findByEmail(EmailNormalizer.normalize(email))
                        .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return new AccountPrincipal(user.getId(), user.getEmail(), user.getName(), user.getPasswordHash());
    }
}
