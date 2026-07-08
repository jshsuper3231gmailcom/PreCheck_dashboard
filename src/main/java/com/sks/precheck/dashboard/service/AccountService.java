package com.sks.precheck.dashboard.service;

import com.sks.precheck.dashboard.dto.AdminUserDto;
import com.sks.precheck.dashboard.mapper.AdminUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SUPER_ADMIN 계정관리 서비스.
 *
 * 역할:
 * - 8__로그인_보안정책정의서.md 4장(계정관리)에 정의된 계정 목록 조회, 계정 생성,
 *   잠금 해제, 활성/비활성 토글, 비밀번호 초기화를 처리한다.
 *
 * 설계 이유:
 * - 신규 계정/비밀번호 초기화는 "최초 로그인 강제 변경"을 위해 PASSWORD_CHANGED_AT을
 *   91일 전으로 백데이트하고 PASSWORD_EXPIRE_YN='Y'로 두는 동일한 규칙을 공유한다.
 * - 비밀번호 검증/이력 적재는 PasswordService의 로직을 그대로 재사용해
 *   정책 변경 시 한 곳만 수정하면 되게 한다.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordService passwordService;
    private final AdminAuditLogService auditLogService;

    /**
     * 전체 계정 목록을 조회한다.
     *
     * @return LOGIN_ID 기준으로 정렬된 계정 목록이다.
     */
    public List<AdminUserDto> listUsers() {
        return adminUserMapper.selectAll();
    }

    /**
     * 신규 계정을 생성한다.
     *
     * @param loginId 로그인 ID다. 중복이면 거부한다.
     * @param userName 사용자명이다.
     * @param role 권한("SUPER_ADMIN" 또는 "ADMIN")이다.
     * @param initialPassword 초기 비밀번호(평문)다. 복잡도 정책을 검증한다.
     * @param actorLoginId 생성을 수행한 SUPER_ADMIN의 LOGIN_ID다.
     * @param request 감사 로그 IP 추출용 요청이다.
     * @throws IllegalArgumentException LOGIN_ID 중복 또는 비밀번호 정책 위반 시다.
     */
    public void createUser(String loginId, String userName, String role, String initialPassword,
                            String actorLoginId, HttpServletRequest request) {
        if (adminUserMapper.selectByLoginId(loginId) != null) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        List<String> violations = PasswordPolicyValidator.validate(initialPassword, loginId);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", violations));
        }

        LocalDateTime now = LocalDateTime.now();
        AdminUserDto dto = new AdminUserDto();
        dto.setLoginId(loginId);
        dto.setPassword(passwordEncoder.encode(initialPassword));
        dto.setUserName(userName);
        dto.setRole(role);
        dto.setStatus("ACTIVE");
        dto.setLoginFailCount(0);
        dto.setPasswordChangedAt(now.minusDays(PasswordPolicyValidator.FORCE_CHANGE_BACKDATE_DAYS));
        dto.setPasswordExpireYn("Y");
        dto.setCreatedAt(now);
        adminUserMapper.insertAdminUser(dto);

        auditLogService.log(dto.getAdminUserId(), loginId, "USER_CREATE", actorLoginId, request, "계정 생성");
    }

    /**
     * 계정 잠금을 즉시 해제한다 (5분 자동 해제 대기 없이 SUPER_ADMIN이 수동 처리).
     *
     * @param adminUserId 대상 계정 ID다.
     * @param actorLoginId 해제를 수행한 SUPER_ADMIN의 LOGIN_ID다.
     * @param request 감사 로그 IP 추출용 요청이다.
     * @throws IllegalArgumentException 대상 계정이 없을 때다.
     */
    public void unlockUser(Long adminUserId, String actorLoginId, HttpServletRequest request) {
        AdminUserDto user = findById(adminUserId);
        adminUserMapper.unlockAccount(adminUserId);
        auditLogService.log(adminUserId, user.getLoginId(), "USER_UNLOCK", actorLoginId, request, "관리자 잠금 해제");
    }

    /**
     * 계정 활성/비활성 상태를 변경한다.
     *
     * @param adminUserId 대상 계정 ID다.
     * @param enable true면 ACTIVE로, false면 DISABLED로 변경한다.
     * @param actorLoginId 변경을 수행한 SUPER_ADMIN의 LOGIN_ID다.
     * @param request 감사 로그 IP 추출용 요청이다.
     * @throws IllegalArgumentException 대상 계정이 없을 때다.
     */
    public void setEnabled(Long adminUserId, boolean enable, String actorLoginId, HttpServletRequest request) {
        AdminUserDto user = findById(adminUserId);
        if (!enable && "SUPER_ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("SUPER_ADMIN 계정은 비활성화할 수 없습니다.");
        }
        String status = enable ? "ACTIVE" : "DISABLED";
        adminUserMapper.updateStatus(adminUserId, status);
        String actionType = enable ? "USER_ENABLE" : "USER_DISABLE";
        String description = enable ? "계정 활성화" : "계정 비활성화";
        auditLogService.log(adminUserId, user.getLoginId(), actionType, actorLoginId, request, description);
    }

    /**
     * 계정 비밀번호를 임시 비밀번호로 초기화한다 (다음 로그인 시 강제 변경).
     *
     * @param adminUserId 대상 계정 ID다.
     * @param tempPassword 임시 비밀번호(평문)다. 복잡도/재사용 정책을 검증한다.
     * @param actorLoginId 초기화를 수행한 SUPER_ADMIN의 LOGIN_ID다.
     * @param request 감사 로그 IP 추출용 요청이다.
     * @throws IllegalArgumentException 대상 계정이 없거나 비밀번호 정책 위반 시다.
     */
    public void resetPassword(Long adminUserId, String tempPassword, String actorLoginId, HttpServletRequest request) {
        AdminUserDto user = findById(adminUserId);

        passwordService.validateNewPassword(user, tempPassword);

        LocalDateTime passwordChangedAt = LocalDateTime.now().minusDays(PasswordPolicyValidator.FORCE_CHANGE_BACKDATE_DAYS);
        passwordService.recordHistoryAndUpdatePassword(user, tempPassword, passwordChangedAt, "Y");

        auditLogService.log(adminUserId, user.getLoginId(), "PASSWORD_RESET", actorLoginId, request, "비밀번호 초기화");
    }

    /**
     * 계정을 삭제한다. SUPER_ADMIN 계정은 삭제할 수 없다.
     *
     * @param adminUserId 삭제할 계정 ID다.
     * @param actorLoginId 삭제를 수행한 SUPER_ADMIN의 LOGIN_ID다.
     * @param request 감사 로그 IP 추출용 요청이다.
     * @throws IllegalArgumentException 대상 계정이 없거나 SUPER_ADMIN 계정일 때다.
     */
    public void deleteUser(Long adminUserId, String actorLoginId, HttpServletRequest request) {
        AdminUserDto user = findById(adminUserId);
        if ("SUPER_ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("SUPER_ADMIN 계정은 삭제할 수 없습니다.");
        }
        auditLogService.log(adminUserId, user.getLoginId(), "USER_DELETE", actorLoginId, request, "계정 삭제");
        adminUserMapper.deletePwdHistoryByUser(adminUserId);
        adminUserMapper.deleteAdminUser(adminUserId);
    }

    /**
     * 계정 ID로 계정을 조회한다.
     *
     * @param adminUserId 대상 계정 ID다.
     * @return 일치하는 계정이다.
     * @throws IllegalArgumentException 대상 계정이 없을 때다.
     */
    private AdminUserDto findById(Long adminUserId) {
        AdminUserDto user = adminUserMapper.selectById(adminUserId);
        if (user == null) {
            throw new IllegalArgumentException("대상 계정을 찾을 수 없습니다.");
        }
        return user;
    }
}
