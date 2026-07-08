package com.sks.precheck.dashboard.security;

import com.sks.precheck.dashboard.dto.AdminUserDto;
import com.sks.precheck.dashboard.mapper.AdminUserMapper;
import com.sks.precheck.dashboard.service.AdminAuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 관리자 계정 인증 Provider.
 *
 * 역할:
 * - 8__로그인_보안정책정의서.md 6장(로그인 실패/계정 잠금)의 상태 전이를
 *   인증 시점에 모두 처리한다: 자동 잠금 해제(5분), 잠금/비활성 계정 차단,
 *   비밀번호 검증, 실패 카운트 증가/잠금, 성공 시 카운트 초기화 및 최종 로그인 시각 갱신.
 * - 10장의 감사 로그(LOGIN_SUCCESS/LOGIN_FAIL/USER_LOCK/USER_UNLOCK)를 함께 기록한다.
 *
 * 설계 이유:
 * - 잔여 시도 횟수/잠금 해제 시각 등 구체 정보를 노출하지 않기 위해,
 *   사용자 미존재/비밀번호 불일치/잠금 사유와 무관하게 동일한 메시지를 사용한다
 *   (단, 잠긴 계정만 별도 메시지를 노출한다).
 */
@Component
@RequiredArgsConstructor
public class AdminAuthenticationProvider implements AuthenticationProvider {

    /** 정책서 6장: 5회 연속 실패 시 계정 잠금. */
    private static final int MAX_LOGIN_FAIL_COUNT = 5;

    /** 정책서 6장: 잠금 후 5분 경과 시 자동 해제. */
    private static final long AUTO_UNLOCK_MINUTES = 5;

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditLogService auditLogService;
    private final HttpServletRequest request;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String loginId = authentication.getName();
        String rawPassword = (String) authentication.getCredentials();

        AdminUserDto user = adminUserMapper.selectByLoginId(loginId);
        if (user == null) {
            auditLogService.log(null, loginId, "LOGIN_FAIL", null, request, "존재하지 않는 계정");
            throw new BadCredentialsException(AuthMessages.GENERIC_FAIL_MESSAGE);
        }

        autoUnlockIfExpired(user);

        if ("LOCKED".equals(user.getStatus())) {
            auditLogService.log(user.getAdminUserId(), loginId, "LOGIN_FAIL", null, request, "잠긴 계정 로그인 시도");
            throw new LockedException(AuthMessages.LOCKED_MESSAGE);
        }
        if ("DISABLED".equals(user.getStatus())) {
            auditLogService.log(user.getAdminUserId(), loginId, "LOGIN_FAIL", null, request, "비활성화된 계정 로그인 시도");
            throw new DisabledException(AuthMessages.DISABLED_MESSAGE);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            handleLoginFailure(user);
            throw new BadCredentialsException(AuthMessages.GENERIC_FAIL_MESSAGE);
        }

        adminUserMapper.updateLoginSuccess(user.getAdminUserId(), LocalDateTime.now());
        auditLogService.log(user.getAdminUserId(), loginId, "LOGIN_SUCCESS", null, request, null);

        AdminUserPrincipal principal = new AdminUserPrincipal(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /**
     * 잠금 후 5분이 경과한 계정을 자동으로 해제한다.
     *
     * @param user 조회된 계정이다. 해제 시 상태값을 메모리상에서도 갱신한다.
     */
    private void autoUnlockIfExpired(AdminUserDto user) {
        if (!"LOCKED".equals(user.getStatus()) || user.getLockedAt() == null) {
            return;
        }
        if (user.getLockedAt().plusMinutes(AUTO_UNLOCK_MINUTES).isAfter(LocalDateTime.now())) {
            return;
        }
        adminUserMapper.unlockAccount(user.getAdminUserId());
        auditLogService.log(user.getAdminUserId(), user.getLoginId(), "USER_UNLOCK", null, request, "5분 경과 자동 잠금 해제");
        user.setStatus("ACTIVE");
        user.setLoginFailCount(0);
        user.setLockedAt(null);
    }

    /**
     * 비밀번호 불일치 시 실패 카운트를 증가시키고, 5회 도달 시 계정을 잠근다.
     *
     * @param user 대상 계정이다.
     */
    private void handleLoginFailure(AdminUserDto user) {
        int failCount = user.getLoginFailCount() + 1;
        adminUserMapper.updateLoginFailCount(user.getAdminUserId(), failCount);

        if (failCount >= MAX_LOGIN_FAIL_COUNT) {
            adminUserMapper.lockAccount(user.getAdminUserId(), LocalDateTime.now());
            auditLogService.log(user.getAdminUserId(), user.getLoginId(), "USER_LOCK", null, request, "5회 연속 로그인 실패로 잠금");
        }

        auditLogService.log(user.getAdminUserId(), user.getLoginId(), "LOGIN_FAIL", null, request, "비밀번호 불일치");
    }
}
