package com.sks.precheck.dashboard.security;

import com.sks.precheck.dashboard.dto.AdminUserDto;
import com.sks.precheck.dashboard.service.PasswordPolicyValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 비밀번호 만료(90일) 정책 인터셉터.
 *
 * 역할:
 * - 8__로그인_보안정책정의서.md 5-2장: 만료된 계정은 로그인 직후부터
 *   `/password/change` 외 화면/API 접근을 차단하고 강제 리다이렉트한다.
 * - 만료 7일 전부터는 모델 속성으로 "D-n" 경고를 전달해 헤더 배너에 표시한다.
 *
 * 설계 이유:
 * - `/login`, `/logout`, `/password/change`, 정적 리소스는 WebMvcConfig에서
 *   인터셉터 대상에서 제외하여, 강제 변경 화면 자체가 차단되지 않도록 한다.
 */
@Component
public class PasswordExpiryInterceptor implements HandlerInterceptor {

    /** 만료 D-7일부터 경고 배너를 표시한다. */
    private static final long WARNING_DAYS_BEFORE = 7;

    /** 헤더 배너에서 참조하는 모델 속성명 (남은 일수, D-n). */
    public static final String WARNING_ATTRIBUTE = "pwdExpireWarningDays";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof AdminUserPrincipal principal)) {
            return true;
        }

        AdminUserDto user = principal.getAdminUser();
        if (!"Y".equals(user.getPasswordExpireYn())) {
            return true;
        }

        long daysSinceChange = Duration.between(user.getPasswordChangedAt(), LocalDateTime.now()).toDays();
        long daysRemaining = PasswordPolicyValidator.PASSWORD_EXPIRE_DAYS - daysSinceChange;

        if (daysRemaining <= 0) {
            response.sendRedirect(request.getContextPath() + "/password/change");
            return false;
        }

        if (daysRemaining <= WARNING_DAYS_BEFORE) {
            request.setAttribute(WARNING_ATTRIBUTE, daysRemaining);
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        if (modelAndView == null || modelAndView.getViewName() == null || modelAndView.getViewName().startsWith("redirect:")) {
            return;
        }
        Object warningDays = request.getAttribute(WARNING_ATTRIBUTE);
        if (warningDays != null) {
            modelAndView.addObject(WARNING_ATTRIBUTE, warningDays);
        }
    }
}
