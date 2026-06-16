# PreCheck Dashboard 로그인 DB 정의서 v1.0

---

## 1. 문서 목적

이 문서는 `8__로그인_보안정책정의서.md`에 정의된 로그인/계정 정책을 구현하기 위한
DB 테이블 구조를 정의한다.

테스트 환경은 **PostgreSQL**, 운영 환경은 **Altibase**를 사용하며
`4__로그수집_DB정의서.md`의 DB 호환성 원칙을 동일하게 따른다.

| 원칙 | 내용 |
|---|---|
| PK 생성 방식 | SEQUENCE 객체 사용 |
| 문자열 타입 | VARCHAR(n) 사용, TEXT 사용 금지 |
| 날짜/시간 타입 | TIMESTAMP 사용 |
| 정수 타입 | NUMERIC(p,s) 사용, BIGINT/SERIAL 사용 금지 |
| 논리값 타입 | CHAR(1) 사용 ('Y'/'N'), BOOLEAN 사용 금지 |

---

## 2. SEQUENCE 설계

```sql
-- ============================================================
-- SEQUENCE 정의 (로그인/계정 관련)
-- ============================================================

-- 관리자 계정 테이블용 시퀀스
CREATE SEQUENCE SEQ_ADMIN_USER
    START WITH 1
    INCREMENT BY 1;

-- 비밀번호 변경 이력 테이블용 시퀀스
CREATE SEQUENCE SEQ_ADMIN_USER_PWD_HISTORY
    START WITH 1
    INCREMENT BY 1;

-- 계정 관련 감사 로그 테이블용 시퀀스
CREATE SEQUENCE SEQ_ADMIN_USER_LOG
    START WITH 1
    INCREMENT BY 1;
```

---

## 3. 테이블 설계

### 3-1. TB_ADMIN_USER (관리자 계정 테이블)

**역할**: 로그인 인증, 계정 상태(잠금/비활성화), 비밀번호 정책(만료/이력 비교) 기준 정보 저장

```sql
-- ============================================================
-- TB_ADMIN_USER : 관리자 계정 테이블
-- ============================================================
-- [설계 의도]
--   - 로그인 인증(Spring Security UserDetails)의 기준 테이블
--   - 비밀번호 만료(90일), 재사용 금지, 로그인 실패 잠금 정책의
--     상태값을 함께 관리
--
-- [상태값 정의]
--   ROLE   : 'SUPER_ADMIN'(계정관리 가능) | 'ADMIN'(일반)
--   STATUS : 'ACTIVE'(정상) | 'LOCKED'(로그인 실패로 잠금) | 'DISABLED'(비활성화)
-- ============================================================

CREATE TABLE TB_ADMIN_USER (

    -- --------------------------------------------------------
    -- PK
    -- --------------------------------------------------------
    ADMIN_USER_ID       NUMERIC(19, 0)  NOT NULL,
    -- 관리자 계정 고유 ID (SEQ_ADMIN_USER로 자동 생성)

    -- --------------------------------------------------------
    -- 계정 기본 정보
    -- --------------------------------------------------------
    LOGIN_ID            VARCHAR(50)     NOT NULL,
    -- 로그인 ID (중복 불가)

    PASSWORD            VARCHAR(200)    NOT NULL,
    -- BCrypt 해시 비밀번호

    USER_NAME           VARCHAR(50)     NOT NULL,
    -- 사용자 실명 (화면 표시용)

    ROLE                VARCHAR(20)     NOT NULL,
    -- 'SUPER_ADMIN' | 'ADMIN'

    -- --------------------------------------------------------
    -- 계정 상태
    -- --------------------------------------------------------
    STATUS              VARCHAR(10)     NOT NULL,
    -- 'ACTIVE' | 'LOCKED' | 'DISABLED'

    LOGIN_FAIL_COUNT    NUMERIC(2, 0)   NOT NULL,
    -- 연속 로그인 실패 횟수, 로그인 성공 시 0으로 초기화
    -- 5회 도달 시 STATUS='LOCKED', LOCKED_AT 기록

    LOCKED_AT           TIMESTAMP,
    -- 잠금 처리 시각 (자동 해제 5분 판정 기준)

    -- --------------------------------------------------------
    -- 비밀번호 정책 관련
    -- --------------------------------------------------------
    PASSWORD_CHANGED_AT TIMESTAMP       NOT NULL,
    -- 마지막 비밀번호 변경 시각 (90일 만료 판정 기준)

    PASSWORD_EXPIRE_YN  CHAR(1)         NOT NULL,
    -- 'Y' : 90일 만료 정책 적용 (일반 계정)
    -- 'N' : 만료 정책 예외 (SUPER_ADMIN 시드 계정)

    FORCE_PWD_CHANGE_YN CHAR(1)         NOT NULL,
    -- 'Y' : 다음 로그인 시 비밀번호 강제 변경 대상
    --       (계정 생성 시 기본값 'Y', 관리자 비밀번호 초기화 시 'Y' 설정)
    -- 'N' : 강제 변경 불필요 (비밀번호 변경 완료 시 'N'으로 갱신)

    -- --------------------------------------------------------
    -- 로그인 이력
    -- --------------------------------------------------------
    LAST_LOGIN_AT       TIMESTAMP,
    -- 마지막 로그인 성공 시각

    -- --------------------------------------------------------
    -- 공통
    -- --------------------------------------------------------
    CREATED_AT          TIMESTAMP       NOT NULL,
    UPDATED_AT          TIMESTAMP,

    CONSTRAINT PK_ADMIN_USER PRIMARY KEY (ADMIN_USER_ID),
    CONSTRAINT UQ_ADMIN_USER_LOGIN_ID UNIQUE (LOGIN_ID)
);
```

---

### 3-2. TB_ADMIN_USER_PWD_HISTORY (비밀번호 변경 이력 테이블)

**역할**: 비밀번호 변경 시 "최근 2개(전/전전) 재사용 금지" 검증용 이력 저장

```sql
-- ============================================================
-- TB_ADMIN_USER_PWD_HISTORY : 비밀번호 변경 이력 테이블
-- ============================================================
-- [설계 의도]
--   - 비밀번호 변경 시점마다 BCrypt 해시를 적재
--   - 신규 비밀번호 설정 시, 해당 계정의 최근 2건 해시와
--     BCrypt matches() 비교하여 재사용 여부 판정
-- ============================================================

CREATE TABLE TB_ADMIN_USER_PWD_HISTORY (

    PWD_HISTORY_ID      NUMERIC(19, 0)  NOT NULL,
    -- 이력 고유 ID (SEQ_ADMIN_USER_PWD_HISTORY로 자동 생성)

    ADMIN_USER_ID       NUMERIC(19, 0)  NOT NULL,
    -- TB_ADMIN_USER.ADMIN_USER_ID 참조

    PASSWORD            VARCHAR(200)    NOT NULL,
    -- 변경 시점의 BCrypt 해시 비밀번호

    CREATED_AT          TIMESTAMP       NOT NULL,
    -- 변경(이력 적재) 시각

    CONSTRAINT PK_ADMIN_USER_PWD_HISTORY PRIMARY KEY (PWD_HISTORY_ID),
    CONSTRAINT FK_PWD_HISTORY_ADMIN_USER FOREIGN KEY (ADMIN_USER_ID)
        REFERENCES TB_ADMIN_USER (ADMIN_USER_ID)
);

-- 계정별 최근 이력 조회 성능을 위한 인덱스
CREATE INDEX IDX_PWD_HISTORY_USER_CREATED
    ON TB_ADMIN_USER_PWD_HISTORY (ADMIN_USER_ID, CREATED_AT);
```

---

### 3-3. TB_ADMIN_USER_LOG (계정 감사 로그 테이블)

**역할**: 로그인 성공/실패, 비밀번호 변경, 계정 생성/잠금/해제/비활성화 등 감사 이력 저장

```sql
-- ============================================================
-- TB_ADMIN_USER_LOG : 계정 감사 로그 테이블
-- ============================================================
-- [설계 의도]
--   - 8__로그인_보안정책정의서.md 10장(감사 로그) 범위의 이벤트를 기록
--   - 로그인 실패는 계정이 존재하지 않아도 LOGIN_ID(시도값)를 남김
--     -> ADMIN_USER_ID는 NULL 허용
--
-- [ACTION_TYPE 정의]
--   LOGIN_SUCCESS / LOGIN_FAIL
--   PASSWORD_CHANGE / PASSWORD_RESET
--   USER_CREATE / USER_LOCK / USER_UNLOCK / USER_DISABLE / USER_ENABLE
-- ============================================================

CREATE TABLE TB_ADMIN_USER_LOG (

    ADMIN_USER_LOG_ID   NUMERIC(19, 0)  NOT NULL,
    -- 감사 로그 고유 ID (SEQ_ADMIN_USER_LOG로 자동 생성)

    ADMIN_USER_ID       NUMERIC(19, 0),
    -- 대상 계정 ID (TB_ADMIN_USER.ADMIN_USER_ID), 미존재 계정 로그인 시도 시 NULL

    LOGIN_ID            VARCHAR(50)     NOT NULL,
    -- 대상(또는 시도) LOGIN_ID

    ACTION_TYPE         VARCHAR(20)     NOT NULL,
    -- 'LOGIN_SUCCESS' | 'LOGIN_FAIL' | 'PASSWORD_CHANGE' | 'PASSWORD_RESET'
    -- | 'USER_CREATE' | 'USER_LOCK' | 'USER_UNLOCK' | 'USER_DISABLE' | 'USER_ENABLE'

    ACTOR_LOGIN_ID      VARCHAR(50),
    -- 수행자 LOGIN_ID (SUPER_ADMIN의 계정관리 작업 시),
    -- 본인 행위(로그인, 본인 비밀번호 변경)인 경우 NULL

    CLIENT_IP           VARCHAR(45),
    -- 요청 클라이언트 IP (IPv4/IPv6)

    DESCRIPTION         VARCHAR(500),
    -- 부가 설명 (예: "5회 연속 실패로 잠금")

    CREATED_AT          TIMESTAMP       NOT NULL,
    -- 이벤트 발생 시각

    CONSTRAINT PK_ADMIN_USER_LOG PRIMARY KEY (ADMIN_USER_LOG_ID)
);

-- 계정별/일자별 조회 성능을 위한 인덱스
CREATE INDEX IDX_ADMIN_USER_LOG_USER_CREATED
    ON TB_ADMIN_USER_LOG (ADMIN_USER_ID, CREATED_AT);
```

---

## 4. 스키마 변경 이력 (ALTER TABLE)

기존 환경(이미 테이블 생성 완료)에 컬럼 추가 시 아래 DDL 실행.

```sql
-- ============================================================
-- v1.1 : FORCE_PWD_CHANGE_YN 컬럼 추가
-- ============================================================
-- 기존 계정(SUPER_ADMIN 시드)은 강제 변경 불필요 → 기본값 'N'으로 추가
-- 신규 계정 생성 / 비밀번호 초기화 시 애플리케이션에서 'Y'로 설정
-- ============================================================

ALTER TABLE TB_ADMIN_USER
    ADD FORCE_PWD_CHANGE_YN CHAR(1) DEFAULT 'N' NOT NULL;
```

---

## 5. 초기 데이터 (SUPER_ADMIN 시드 계정)

```sql
-- ============================================================
-- 초기 SUPER_ADMIN 계정
-- ============================================================
-- LOGIN_ID  : admin
-- PASSWORD  : !Sks8245 (BCrypt 해시값으로 저장, 아래는 예시 placeholder)
--             실제 적용 시 BCryptPasswordEncoder로 생성한 해시로 교체
-- PASSWORD_EXPIRE_YN='N' : 만료 정책 예외 (8__로그인_보안정책정의서.md 11장)
-- ============================================================

INSERT INTO TB_ADMIN_USER (
    ADMIN_USER_ID, LOGIN_ID, PASSWORD, USER_NAME, ROLE, STATUS,
    LOGIN_FAIL_COUNT, LOCKED_AT,
    PASSWORD_CHANGED_AT, PASSWORD_EXPIRE_YN, FORCE_PWD_CHANGE_YN,
    LAST_LOGIN_AT, CREATED_AT, UPDATED_AT
) VALUES (
    nextval('SEQ_ADMIN_USER'), 'admin', '{bcrypt-hash-of-!Sks8245}', '시스템관리자', 'SUPER_ADMIN', 'ACTIVE',
    0, NULL,
    CURRENT_TIMESTAMP, 'N', 'N',
    NULL, CURRENT_TIMESTAMP, NULL
);
```
