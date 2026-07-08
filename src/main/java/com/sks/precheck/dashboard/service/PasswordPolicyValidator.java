package com.sks.precheck.dashboard.service;

import com.sks.precheck.dashboard.dto.AdminUserDto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 비밀번호 복잡도 정책 검증 유틸.
 *
 * 역할:
 * - 8__로그인_보안정책정의서.md 5-1장의 비밀번호 복잡도 규칙을 검증한다.
 * - 5-2장의 만료(90일) 판정 기준과, 계정 생성/초기화 시 "최초 로그인 강제 변경"을
 *   재현하기 위한 역산 일수(91일)를 상수로 제공한다.
 *
 * 설계 이유:
 * - 계정 생성(AccountService), 비밀번호 변경(PasswordService) 양쪽에서
 *   동일한 복잡도 규칙을 적용해야 하므로 정적 유틸로 분리했다.
 */
public final class PasswordPolicyValidator {

    /** 비밀번호 만료 기준 일수 (정책서 5-2). */
    public static final int PASSWORD_EXPIRE_DAYS = 90;

    /**
     * 계정 생성/비밀번호 초기화 시 PASSWORD_CHANGED_AT을 이 일수만큼 과거로 설정해
     * 최초 로그인 직후 만료 정책에 의해 강제 변경되도록 한다 (PASSWORD_EXPIRE_DAYS보다 1일 더 과거).
     */
    public static final int FORCE_CHANGE_BACKDATE_DAYS = PASSWORD_EXPIRE_DAYS + 1;

    /**
     * 비밀번호 변경이 실제로 반영된 후 적용할 PASSWORD_EXPIRE_YN 값을 role별로 정한다.
     *
     * SUPER_ADMIN은 90일 주기 만료 정책이 영구 면제이므로, 최초 로그인 강제 변경(생성/초기화 직후
     * 'Y' + 백데이트)을 완료한 시점부터는 'N'으로 되돌려 다시는 주기 체크에 걸리지 않게 한다.
     * ADMIN은 매 변경마다 'Y'를 유지해 다음 90일 주기가 그대로 적용되게 한다.
     *
     * @param role 계정 권한("SUPER_ADMIN" 또는 "ADMIN")이다.
     * @return SUPER_ADMIN이면 "N", 그 외에는 "Y"다.
     */
    public static String expireYnAfterChange(String role) {
        return "SUPER_ADMIN".equals(role) ? "N" : "Y";
    }

    /**
     * 만료 정책 적용 대상 계정의 잔여 일수를 계산한다.
     *
     * PasswordExpiryInterceptor(강제 리다이렉트/D-n 배너)와 PasswordController(변경 화면의
     * "취소 불가" 강제 여부 표시) 양쪽이 동일한 기준으로 판정해야 하므로 한 곳에 모았다.
     *
     * @param user 대상 계정이다.
     * @return 만료까지 남은 일수다. 정책 미적용 대상(PASSWORD_EXPIRE_YN != 'Y' 또는
     *         PASSWORD_CHANGED_AT 미기록, 계정 없음)이면 {@link Long#MAX_VALUE}를 반환해
     *         호출부가 별도 null/역할 분기 없이 "만료 아님"으로 취급할 수 있게 한다.
     */
    public static long daysRemaining(AdminUserDto user) {
        if (user == null || !"Y".equals(user.getPasswordExpireYn()) || user.getPasswordChangedAt() == null) {
            return Long.MAX_VALUE;
        }
        long daysSinceChange = Duration.between(user.getPasswordChangedAt(), LocalDateTime.now()).toDays();
        return PASSWORD_EXPIRE_DAYS - daysSinceChange;
    }

    /**
     * 만료 정책 적용 대상이면서 이미 만료(잔여일 0 이하)됐는지 판정한다.
     *
     * @param user 대상 계정이다.
     * @return 만료 정책이 적용되고 잔여일이 0 이하면 true다.
     */
    public static boolean isExpired(AdminUserDto user) {
        return daysRemaining(user) <= 0;
    }

    private static final int MIN_LENGTH = 10;
    private static final int MIN_CHAR_TYPE_COUNT = 3;
    private static final int REPEAT_OR_SEQUENCE_LENGTH = 3;

    private PasswordPolicyValidator() {
    }

    /**
     * 비밀번호 복잡도 규칙을 검증한다.
     *
     * @param password 검증할 평문 비밀번호다.
     * @param loginId 비밀번호 포함 여부를 검사할 로그인 ID다.
     * @return 위반 사항 메시지 목록이다. 비어있으면 통과다.
     */
    public static List<String> validate(String password, String loginId) {
        List<String> violations = new ArrayList<>();

        if (password == null || password.length() < MIN_LENGTH) {
            violations.add("비밀번호는 최소 " + MIN_LENGTH + "자 이상이어야 합니다.");
            return violations;
        }

        if (countCharTypes(password) < MIN_CHAR_TYPE_COUNT) {
            violations.add("영문 대문자, 영문 소문자, 숫자, 특수문자 중 " + MIN_CHAR_TYPE_COUNT + "종류 이상을 조합해야 합니다.");
        }

        if (hasRepeatedOrSequentialChars(password)) {
            violations.add("동일한 문자 또는 연속된 문자를 " + REPEAT_OR_SEQUENCE_LENGTH + "자 이상 사용할 수 없습니다.");
        }

        if (loginId != null && !loginId.isEmpty() && password.toLowerCase().contains(loginId.toLowerCase())) {
            violations.add("비밀번호에 아이디를 포함할 수 없습니다.");
        }

        return violations;
    }

    private static int countCharTypes(String password) {
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSpecial = true;
            }
        }

        int count = 0;
        if (hasUpper) count++;
        if (hasLower) count++;
        if (hasDigit) count++;
        if (hasSpecial) count++;
        return count;
    }

    private static boolean hasRepeatedOrSequentialChars(String password) {
        for (int i = 0; i <= password.length() - REPEAT_OR_SEQUENCE_LENGTH; i++) {
            char c1 = password.charAt(i);
            char c2 = password.charAt(i + 1);
            char c3 = password.charAt(i + 2);

            boolean allSame = c1 == c2 && c2 == c3;
            boolean ascending = (c2 - c1 == 1) && (c3 - c2 == 1);
            boolean descending = (c1 - c2 == 1) && (c2 - c3 == 1);

            if (allSame || ascending || descending) {
                return true;
            }
        }
        return false;
    }
}
