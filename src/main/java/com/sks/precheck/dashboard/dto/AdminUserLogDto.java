package com.sks.precheck.dashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 계정 감사 로그 DTO.
 *
 * 역할:
 * - TB_ADMIN_USER_LOG insert 시 사용하는 입력 모델이다.
 *
 * 설계 이유:
 * - 8__로그인_보안정책정의서.md 10장의 감사 이벤트(LOGIN_SUCCESS/LOGIN_FAIL 등)를
 *   하나의 모델로 통일해 AdminAuditLogService에서 공통으로 insert 한다.
 */
@Data
public class AdminUserLogDto {
    private Long adminUserId;
    private String loginId;
    private String actionType;
    private String actorLoginId;
    private String clientIp;
    private String description;
    private LocalDateTime createdAt;
}
