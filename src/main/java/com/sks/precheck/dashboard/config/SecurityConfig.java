package com.sks.precheck.dashboard.config;

import com.sks.precheck.dashboard.security.AdminAuthenticationProvider;
import com.sks.precheck.dashboard.security.ApiAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정.
 *
 * 역할:
 * - 8__로그인_보안정책정의서.md 2,7,8장에 정의된 인증 방식, URL 접근 제어,
 *   세션/로그아웃 정책을 구성한다.
 *
 * 설계 이유:
 * - 정적 리소스와 `/login`은 인증 없이 허용하고, `/admin/users/**`는 SUPER_ADMIN으로 제한한다.
 * - 잠긴 계정은 로그인 실패 화면에서 별도 메시지(`error=locked`)로 구분한다
 *   (잔여 시도횟수/해제시각은 노출하지 않음).
 * - 동시(다중) 로그인은 전부 허용하므로 세션 동시 접속 제한을 설정하지 않는다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AdminAuthenticationProvider adminAuthenticationProvider;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authenticationProvider(adminAuthenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/plugins/**", "/images/**", "/fonts/**").permitAll()
                        .requestMatchers("/admin/users/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/password/change").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureHandler((request, response, exception) -> {
                            String errorType;
                            if (exception instanceof LockedException) {
                                errorType = "locked";
                            } else if (exception instanceof DisabledException) {
                                errorType = "disabled";
                            } else {
                                errorType = "bad";
                            }
                            response.sendRedirect(request.getContextPath() + "/login?error=" + errorType);
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(apiAuthenticationEntryPoint));

        return http.build();
    }
}
