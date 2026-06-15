package com.sks.precheck.dashboard.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sks.precheck.dashboard.controller.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;

/**
 * 미인증 요청에 대한 진입점 처리.
 *
 * 역할:
 * - 8__로그인_보안정책정의서.md 8장: `/dashboard/api/**` 호출은 401 JSON으로 응답하고,
 *   그 외 페이지 요청은 `/login`으로 리다이렉트한다.
 *
 * 설계 이유:
 * - 화면 자동 갱신 API가 세션 만료 시 페이지 전체 리다이렉트가 아닌 JSON 오류로
 *   응답받아야 프런트엔드에서 일관되게 처리할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String API_PATH_PATTERN = "/dashboard/api/**";

    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        if (pathMatcher.match(API_PATH_PATTERN, request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail("인증이 필요합니다.")));
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login");
    }
}
