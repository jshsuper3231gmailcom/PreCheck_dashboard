package com.sks.precheck.dashboard.service;

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
