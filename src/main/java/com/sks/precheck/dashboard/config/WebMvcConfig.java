package com.sks.precheck.dashboard.config;

import com.sks.precheck.dashboard.security.PasswordExpiryInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 인터셉터 등록 설정.
 *
 * 역할:
 * - PasswordExpiryInterceptor를 인증이 필요한 화면 전반에 적용한다.
 *
 * 설계 이유:
 * - `/login`, `/logout`, `/password/change`, 정적 리소스는 비밀번호 만료 강제
 *   리다이렉트 대상에서 제외해야 강제 변경 화면 자체가 막히지 않는다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final PasswordExpiryInterceptor passwordExpiryInterceptor;

    @Override
        public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(passwordExpiryInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login", "/logout", "/password/change",
                        "/css/**", "/js/**", "/plugins/**", "/images/**", "/fonts/**"
                );
    }
}
