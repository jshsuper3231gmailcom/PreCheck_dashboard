package com.sks.precheck.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 관리자 계정 DTO.
 *
 * 역할:
 * - TB_ADMIN_USER 1행을 매핑하며, 로그인 인증과 계정관리 화면에서 공통으로 사용한다.
 *
 * 설계 이유:
 * - 정책서(8장)의 비밀번호 만료/잠금 판정에 필요한 상태값을 그대로 보관해
 *   인증 Provider와 계정관리 서비스가 동일한 모델을 사용하게 한다.
 */
@Data
public class AdminUserDto {
    private Long adminUserId;
    private String loginId;
    @JsonIgnore
    private String password;
    private String userName;
    private String role;
    private String status;
    private Integer loginFailCount;
    private LocalDateTime lockedAt;
    private LocalDateTime passwordChangedAt;
    private String passwordExpireYn;
    private String forcePwdChangeYn;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
