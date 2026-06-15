package com.sks.precheck.dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 인코더 설정.
 *
 * 역할:
 * - BCryptPasswordEncoder 빈을 제공한다.
 *
 * 설계 이유:
 * - PasswordEncoder를 SecurityConfig에 두면 AdminAuthenticationProvider(PasswordEncoder 의존) ->
 *   SecurityConfig(AdminAuthenticationProvider 의존) 순환 참조가 발생하므로
 *   별도 설정 클래스로 분리한다.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
