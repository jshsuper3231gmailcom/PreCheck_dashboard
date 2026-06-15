package com.sks.precheck.dashboard.service;

import com.sks.precheck.dashboard.dto.AdminUserLogDto;
import com.sks.precheck.dashboard.mapper.AdminUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 계정 감사 로그(TB_ADMIN_USER_LOG) 적재 서비스.
 *
 * 역할:
 * - 8__로그인_보안정책정의서.md 10장에 정의된 이벤트(LOGIN_SUCCESS/LOGIN_FAIL,
 *   PASSWORD_CHANGE/PASSWORD_RESET, USER_CREATE/USER_LOCK/USER_UNLOCK/USER_DISABLE/USER_ENABLE)를
 *   공통 형식으로 적재한다.
 *
 * 설계 이유:
 * - 클라이언트 IP 추출, ACTOR 처리(본인 행위는 NULL) 로직을 한 곳에 모아
 *   인증 Provider/계정관리 서비스/비밀번호 서비스가 동일하게 사용한다.
 */
@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AdminUserMapper adminUserMapper;

    /**
     * 감사 로그 1건을 적재한다.
     *
     * @param adminUserId 대상 계정 ID다. 미존재 계정 로그인 시도 시 null이다.
     * @param loginId 대상(또는 시도) LOGIN_ID다.
     * @param actionType 이벤트 종류다.
     * @param actorLoginId 수행자 LOGIN_ID다. 본인 행위(로그인, 본인 비밀번호 변경)는 null이다.
     * @param request 클라이언트 IP 추출용 요청이다. null이면 IP를 기록하지 않는다.
     * @param description 부가 설명이다.
     */
    public void log(Long adminUserId, String loginId, String actionType, String actorLoginId,
                     HttpServletRequest request, String description) {
        AdminUserLogDto dto = new AdminUserLogDto();
        dto.setAdminUserId(adminUserId);
        dto.setLoginId(loginId);
        dto.setActionType(actionType);
        dto.setActorLoginId(actorLoginId);
        dto.setClientIp(request != null ? extractClientIp(request) : null);
        dto.setDescription(description);
        dto.setCreatedAt(LocalDateTime.now());
        adminUserMapper.insertAdminUserLog(dto);
    }

    /**
     * 클라이언트 IP를 추출한다. 프록시를 통한 접속을 고려해 X-Forwarded-For를 우선한다.
     *
     * @param request 현재 요청이다.
     * @return 클라이언트 IP다.
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
