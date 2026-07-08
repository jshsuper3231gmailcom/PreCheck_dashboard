package com.sks.precheck.dashboard.service;

import com.sks.precheck.dashboard.dto.AdminUserDto;
import com.sks.precheck.dashboard.mapper.AdminUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 비밀번호 변경/검증 서비스.
 *
 * 역할:
 * - 8__로그인_보안정책정의서.md 5장(비밀번호 정책)의 복잡도/재사용 금지 규칙을 적용해
 *   본인 비밀번호 변경(/password/change)을 처리한다.
 * - 계정 생성/비밀번호 초기화(AccountService)에서도 동일한 검증/이력 적재 로직을 재사용한다.
 *
 * 설계 이유:
 * - "최근 2개(직전 + 그 이전) 비밀번호 재사용 금지"는 현재 PASSWORD 값과
 *   TB_ADMIN_USER_PWD_HISTORY의 최신 1건을 비교하는 것으로 동일하게 구현된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordService {

    /** "최근 2개" 중 현재 비밀번호를 제외한 이력 비교 건수. */
    private static final int RECENT_HISTORY_CHECK_COUNT = 1;

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditLogService auditLogService;

    /**
     * 본인 비밀번호를 변경한다.
     *
     * @param user 현재 로그인한 사용자다.
     * @param currentPassword 현재 비밀번호(평문)다.
     * @param newPassword 신규 비밀번호(평문)다.
     * @param confirmPassword 신규 비밀번호 확인 값이다.
     * @param request 감사 로그에 기록할 클라이언트 IP 추출용 요청이다.
     * @throws IllegalArgumentException 현재 비밀번호 불일치, 확인값 불일치, 정책 위반 시다.
     */
    public void changeOwnPassword(AdminUserDto user, String currentPassword, String newPassword,
                                   String confirmPassword, HttpServletRequest request) {
        log.info("[PasswordService] changeOwnPassword start. user={} adminUserId={}", user.getLoginId(), user.getAdminUserId());
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("[PasswordService] currentPassword mismatch for user={}", user.getLoginId());
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("신규 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
        }

        validateNewPassword(user, newPassword);
        log.info("[PasswordService] validation passed for user={}", user.getLoginId());

        LocalDateTime now = LocalDateTime.now();
        recordHistoryAndUpdatePassword(user, newPassword, now, PasswordPolicyValidator.expireYnAfterChange(user.getRole()));
        log.info("[PasswordService] password updated in DB for user={} at {}", user.getLoginId(), now);

        auditLogService.log(user.getAdminUserId(), user.getLoginId(), "PASSWORD_CHANGE", null, request, "본인 비밀번호 변경");
        log.info("[PasswordService] audit log inserted for user={}", user.getLoginId());
    }

    /**
     * 신규 비밀번호의 복잡도(5-1)와 재사용 금지(5-3) 규칙을 검증한다.
     *
     * @param user 대상 계정이다. 복잡도 검증 시 LOGIN_ID 포함 여부에 사용한다.
     * @param newPassword 검증할 신규 비밀번호(평문)다.
     * @throws IllegalArgumentException 정책 위반 시 위반 사유를 포함해 던진다.
     */
    public void validateNewPassword(AdminUserDto user, String newPassword) {
        List<String> violations = PasswordPolicyValidator.validate(newPassword, user.getLoginId());
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", violations));
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("최근 사용한 비밀번호는 재사용할 수 없습니다.");
        }

        List<String> recentHistory = adminUserMapper.selectRecentPwdHistory(user.getAdminUserId(), RECENT_HISTORY_CHECK_COUNT);
        for (String oldHash : recentHistory) {
            if (passwordEncoder.matches(newPassword, oldHash)) {
                throw new IllegalArgumentException("최근 사용한 비밀번호는 재사용할 수 없습니다.");
            }
        }
    }

    /**
     * 기존 비밀번호를 이력에 적재하고, 새 비밀번호로 갱신한다.
     *
     * @param user 대상 계정이다.
     * @param newPassword 신규 비밀번호(평문)다.
     * @param passwordChangedAt 비밀번호 변경 시각으로 기록할 값이다.
     * @param passwordExpireYn 갱신할 만료 정책 적용 여부('Y'/'N')다.
     */
    @Transactional
    public void recordHistoryAndUpdatePassword(AdminUserDto user, String newPassword,
                                                LocalDateTime passwordChangedAt, String passwordExpireYn) {
        adminUserMapper.insertPwdHistory(user.getAdminUserId(), user.getPassword(), LocalDateTime.now());
        String newHash = passwordEncoder.encode(newPassword);
        adminUserMapper.updatePassword(user.getAdminUserId(), newHash, passwordChangedAt, passwordExpireYn);
    }
}
