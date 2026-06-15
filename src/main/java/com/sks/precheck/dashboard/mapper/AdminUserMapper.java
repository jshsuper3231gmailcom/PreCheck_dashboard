package com.sks.precheck.dashboard.mapper;

import com.sks.precheck.dashboard.dto.AdminUserDto;
import com.sks.precheck.dashboard.dto.AdminUserLogDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 계정/비밀번호 이력/감사 로그 MyBatis 매퍼.
 *
 * 역할:
 * - 로그인 인증, 비밀번호 정책, 계정관리(SUPER_ADMIN) 화면에 필요한
 *   TB_ADMIN_USER, TB_ADMIN_USER_PWD_HISTORY, TB_ADMIN_USER_LOG 접근을 제공한다.
 *
 * 설계 이유:
 * - 8__로그인_보안정책정의서.md / 9__로그인_DB정의서.md에 정의된 상태 전이를
 *   매퍼 단위로 명확히 분리해, 인증 Provider와 계정관리 서비스가 같은 메서드를 공유한다.
 */
@Mapper
public interface AdminUserMapper {

    /**
     * LOGIN_ID로 계정을 조회한다.
     *
     * @param loginId 로그인 ID다.
     * @return 일치하는 계정이다. 없으면 null이다.
     */
    AdminUserDto selectByLoginId(@Param("loginId") String loginId);

    /**
     * 계정관리 화면의 전체 계정 목록을 조회한다.
     *
     * @return LOGIN_ID 기준으로 정렬된 계정 목록이다.
     */
    List<AdminUserDto> selectAll();

    /**
     * 신규 계정을 생성한다.
     *
     * @param dto 생성할 계정 정보다. adminUserId는 SEQUENCE로 채워진다.
     */
    void insertAdminUser(AdminUserDto dto);

    /**
     * 로그인 성공 시 실패 카운트 초기화와 최종 로그인 시각을 갱신한다.
     *
     * @param adminUserId 대상 계정 ID다.
     * @param lastLoginAt 로그인 성공 시각이다.
     */
    void updateLoginSuccess(@Param("adminUserId") Long adminUserId, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    /**
     * 로그인 실패 시 실패 카운트를 갱신한다.
     *
     * @param adminUserId 대상 계정 ID다.
     * @param loginFailCount 갱신할 실패 카운트다.
     */
    void updateLoginFailCount(@Param("adminUserId") Long adminUserId, @Param("loginFailCount") int loginFailCount);

    /**
     * 5회 연속 실패로 계정을 잠근다 (STATUS=LOCKED, LOCKED_AT 기록).
     *
     * @param adminUserId 대상 계정 ID다.
     * @param lockedAt 잠금 처리 시각이다.
     */
    void lockAccount(@Param("adminUserId") Long adminUserId, @Param("lockedAt") LocalDateTime lockedAt);

    /**
     * 계정 잠금을 해제한다 (STATUS=ACTIVE, LOGIN_FAIL_COUNT=0, LOCKED_AT=NULL).
     * 자동 해제(5분 경과)와 SUPER_ADMIN 수동 해제 모두 사용한다.
     *
     * @param adminUserId 대상 계정 ID다.
     */
    void unlockAccount(@Param("adminUserId") Long adminUserId);

    /**
     * 계정 활성/비활성 상태를 변경한다 (ACTIVE ↔ DISABLED).
     *
     * @param adminUserId 대상 계정 ID다.
     * @param status 변경할 STATUS 값이다.
     */
    void updateStatus(@Param("adminUserId") Long adminUserId, @Param("status") String status);

    /**
     * 비밀번호와 비밀번호 변경 시각/만료 플래그를 갱신한다.
     *
     * @param adminUserId 대상 계정 ID다.
     * @param password 새 BCrypt 해시 비밀번호다.
     * @param passwordChangedAt 비밀번호 변경 시각이다.
     * @param passwordExpireYn 만료 정책 적용 여부('Y'/'N')다.
     */
    void updatePassword(
            @Param("adminUserId") Long adminUserId,
            @Param("password") String password,
            @Param("passwordChangedAt") LocalDateTime passwordChangedAt,
            @Param("passwordExpireYn") String passwordExpireYn
    );

    /**
     * 비밀번호 변경 이력을 적재한다.
     *
     * @param adminUserId 대상 계정 ID다.
     * @param password 적재할 BCrypt 해시 비밀번호다.
     * @param createdAt 적재 시각이다.
     */
    void insertPwdHistory(
            @Param("adminUserId") Long adminUserId,
            @Param("password") String password,
            @Param("createdAt") LocalDateTime createdAt
    );

    /**
     * 비밀번호 재사용 검증용 최근 이력 해시를 조회한다.
     *
     * @param adminUserId 대상 계정 ID다.
     * @param limit 조회할 최대 건수다.
     * @return 최신순으로 정렬된 BCrypt 해시 목록이다.
     */
    List<String> selectRecentPwdHistory(@Param("adminUserId") Long adminUserId, @Param("limit") int limit);

    /**
     * 계정 감사 로그 1건을 적재한다.
     *
     * @param dto 감사 로그 입력 모델이다.
     */
    void insertAdminUserLog(AdminUserLogDto dto);
}
