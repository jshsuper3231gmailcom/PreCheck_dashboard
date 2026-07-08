package com.sks.precheck.dashboard.security;

/**
 * 로그인 실패 안내 메시지 상수.
 *
 * 역할:
 * - 8__로그인_보안정책정의서.md 6장: 잔여 시도횟수/잠금 사유를 노출하지 않기 위해
 *   잠금/비활성/그 외 실패를 각각 고정 문구로 통일한다.
 *
 * 설계 이유:
 * - {@link AdminAuthenticationProvider}(인증 실패 시 예외 메시지)와
 *   {@link com.sks.precheck.dashboard.controller.LoginController}(로그인 화면 안내 문구)
 *   양쪽이 동일한 문구를 써야 하므로, 문구 변경 시 한 곳만 고치도록 상수로 분리했다.
 */
public final class AuthMessages {

    public static final String LOCKED_MESSAGE = "계정이 잠겼습니다. 관리자에게 문의하세요.";
    public static final String DISABLED_MESSAGE = "비활성화된 계정입니다. 관리자에게 문의하세요.";
    public static final String GENERIC_FAIL_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";

    private AuthMessages() {
    }
}
