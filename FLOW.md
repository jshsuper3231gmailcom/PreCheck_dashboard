# PreCheck Dashboard 개발자 참고 문서

> 생성일: 2026-06-16 | 대상 경로: `precheck_collect/dashboard`

---

## 1. 프로젝트 개요

### 목적 및 역할

PreCheck Dashboard는 서버 로그 수집·분석 결과를 실시간으로 시각화하는 **관리자 전용 웹 대시보드**다.
`collect` 프로젝트가 서버에서 수집한 로그와 `analyze` 프로젝트가 분석한 결과를 DB에서 읽어,
에러/경고/정상 분포, 서버별 수집·분석 현황, 주요 지표 카드, 히스토리 그래프, 리소스 도넛 차트 등을 표시한다.
또한 관리자 계정 생성·잠금 해제·비밀번호 정책 등 보안 기능을 포함한다.

### 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.3.12 |
| View | Thymeleaf + AdminLTE (Bootstrap 기반) |
| Security | Spring Security 6 |
| ORM | MyBatis 3.0.3 |
| DB (테스트) | PostgreSQL 5432 |
| DB (운영) | Altibase (JDBC: `Altibase.jdbc.driver.AltibaseDriver`) |
| Build | Gradle 8 |
| 기타 | Lombok, HikariCP, Spring DevTools |

### 실행 방식

**웹 서버** — `DashboardApplication.main()` 진입점, 기본 포트 `8080`.

```bash
# 테스트 환경 (PostgreSQL)
./gradlew bootRun --args='--spring.profiles.active=test'

# 운영 환경 (Altibase)
./gradlew bootRun --args='--spring.profiles.active=prod'
```

기동 시 `DashboardService.init()`가 수집/분석 스케줄 정의서(`.conf`)를 파싱해 서버 수를 캐싱한다.

---

## 2. 데이터 흐름

### 전체 흐름도

```
[외부 시스템]
  collect 프로젝트 ──→ TB_COLLECT_LOG        (수집 로그 원문)
                   ──→ TB_COLLECT_HISTORY    (수집 실행 이력)
  analyze 프로젝트 ──→ TB_ANALYZE_RESULT     (분석 결과)
                   ──→ TB_ANALYZE_HISTORY    (분석 실행 이력)

[Dashboard 브라우저]
  브라우저 → GET /login
           ↓ Spring Security (AdminAuthenticationProvider)
           ↓ 인증 성공 → 세션 발급
  브라우저 → GET /dashboard
           ↓ PasswordExpiryInterceptor (비밀번호 만료 체크)
           ↓ DashboardController
           ↓ DashboardService
           ↓ DashboardMapper (MyBatis XML)
           ↓ DB (TB_ANALYZE_RESULT, TB_COLLECT_HISTORY, ...)
           ↑ JSON 응답 → 브라우저 주기적 폴링 (refreshIntervalSeconds: 60초)
```

### 주요 시나리오별 흐름

#### 대시보드 초기 로드

```
GET /dashboard
  → DashboardController.dashboard()
  → Thymeleaf: dashboard/index.html 렌더링
  → 브라우저가 다음 API를 비동기 호출:
      /dashboard/api/summary      → 상단 요약 스트립
      /dashboard/api/info-data    → 주요 데이터 카드
      /dashboard/api/server-list  → 서버 리스트 카드
      /dashboard/api/resource     → 리소스 도넛 차트
      /dashboard/api/error-list   → 에러/경고 탭 목록
      /dashboard/api/uc-spark     → UC 실시간 접속자 스파크라인
```

#### 로그인 / 잠금 처리 흐름

```
POST /login (Spring Security form-login)
  → AdminAuthenticationProvider.authenticate()
  → adminUserMapper.selectByLoginId()
  → 잠금 자동 해제 체크 (5분 경과 시)
  → 비밀번호 BCrypt 비교
  → 실패: loginFailCount++ → 5회 시 LOCKED 상태 전환
  → 성공: loginSuccess 갱신, 감사 로그 적재
  → PasswordExpiryInterceptor: 90일 만료 시 /password/change 강제 리다이렉트
```

#### 비밀번호 변경 흐름

```
POST /password/change
  → PasswordController.change()
  → PasswordService.changeOwnPassword()
      ├─ 현재 비밀번호 BCrypt 검증
      ├─ PasswordPolicyValidator.validate() (복잡도 검사)
      ├─ 최근 2건 재사용 금지 (현재 비밀번호 + TB_ADMIN_USER_PWD_HISTORY 1건)
      └─ adminUserMapper.insertPwdHistory() + updatePassword()
  → SecurityContext 인증 정보 즉시 갱신 (stale principal 방지)
  → redirect:/dashboard
```

---

## 3. 디렉토리 및 파일 구조

### 디렉토리 역할

```
dashboard/
├── src/main/java/com/sks/precheck/dashboard/
│   ├── config/          설정 바인딩 클래스 (InfoData, MyBatis, Security, WebMvc 등)
│   ├── controller/      HTTP 요청 진입점 (화면/API 라우팅)
│   ├── dto/             DB ↔ Java 데이터 전달 객체
│   ├── mapper/          MyBatis Mapper 인터페이스
│   ├── security/        인증 Provider, Principal, 인터셉터
│   └── service/         비즈니스 로직
├── src/main/resources/
│   ├── application.yml         기본 설정 (서버 포트, 세션, MyBatis)
│   ├── application-test.yml    PostgreSQL 테스트 환경
│   ├── application-prod.yml    Altibase 운영 환경
│   ├── mapper/                 MyBatis XML 쿼리 파일
│   ├── sql/init_dev.sql        테이블 DDL + 초기 데이터
│   ├── static/                 AdminLTE CSS/JS
│   └── templates/              Thymeleaf HTML 템플릿
└── architectui-html-free/      UI 참고용 HTML 템플릿 (정적 파일)
```

### 주요 파일 목록

| 파일 | 역할 |
|------|------|
| `DashboardApplication.java` | Spring Boot 진입점 |
| `config/InfoDataConfig.java` | `precheck.*` 설정 바인딩 (스케줄 경로, 카드 항목 목록) |
| `config/SecurityConfig.java` | URL 권한 매핑, form-login, 세션 설정 |
| `config/WebMvcConfig.java` | 인터셉터 등록, 정적 리소스 경로 설정 |
| `config/MyBatisConfig.java` | MyBatis DataSource 구성 |
| `config/PasswordEncoderConfig.java` | BCryptPasswordEncoder Bean 등록 |
| `controller/DashboardController.java` | 대시보드 화면 + 8개 조회 API |
| `controller/AdminUserController.java` | SUPER_ADMIN 계정관리 화면 + 6개 API |
| `controller/LoginController.java` | 로그인 화면, 실패 메시지 처리 |
| `controller/PasswordController.java` | 비밀번호 변경 화면 + 처리 |
| `controller/ApiResponse.java` | 공통 JSON 응답 래퍼 (`{ok, data, message}`) |
| `service/DashboardService.java` | 대시보드 조회 로직 집합, 스케줄 파싱 캐시 |
| `service/AccountService.java` | 계정 생성/잠금해제/활성화/삭제 |
| `service/PasswordService.java` | 비밀번호 변경/검증/이력 적재 |
| `service/PasswordPolicyValidator.java` | 복잡도 규칙 정적 유틸 |
| `service/AdminAuditLogService.java` | 감사 로그 적재 공통 서비스 |
| `security/AdminAuthenticationProvider.java` | 로그인 인증, 잠금 처리 |
| `security/AdminUserPrincipal.java` | Spring Security UserDetails 구현체 |
| `security/PasswordExpiryInterceptor.java` | 90일 만료 체크, D-7 경고 배너 |
| `security/ApiAuthenticationEntryPoint.java` | 미인증 API 요청 시 401 JSON 반환 |
| `mapper/DashboardMapper.java` | 대시보드 조회 Mapper 인터페이스 |
| `mapper/AdminUserMapper.java` | 계정 관련 CRUD Mapper 인터페이스 |
| `resources/mapper/DashboardMapper.xml` | 대시보드 조회 SQL |
| `resources/mapper/AdminUserMapper.xml` | 계정 관련 SQL |
| `resources/sql/init_dev.sql` | 테이블 DDL + 시퀀스 + 초기 데이터 |
| `templates/login.html` | 로그인 화면 |
| `templates/dashboard/index.html` | 대시보드 메인 화면 |
| `templates/dashboard/history.html` | History 페이지 — 4개 그룹 월별 선차트 (v1.6 신규) |
| `templates/admin/users.html` | 계정 관리 화면 |
| `templates/password/change.html` | 비밀번호 변경 화면 |

---

## 4. 소스별 주요 함수/메서드

### DashboardController

| 메서드 | HTTP | URL | 반환 | 설명 |
|--------|------|-----|------|------|
| `dashboard()` | GET | `/dashboard` | `String` (View) | 대시보드 메인 화면 반환, 사용자명/권한/갱신주기 모델 바인딩 |
| `summary()` | GET | `/dashboard/api/summary` | `ApiResponse<SummaryDto>` | 상단 요약 스트립 (에러/경고/정상 건수, 수집/분석 성공률) |
| `infoData()` | GET | `/dashboard/api/info-data` | `ApiResponse<Map>` | 주요 데이터 카드 (LOG_ID별 최신 분석값) |
| `errorList()` | GET | `/dashboard/api/error-list` | `ApiResponse<PageResultDto>` | 에러/경고 탭 목록 (서버 필터, 페이징) |
| `normalList()` | GET | `/dashboard/api/normal-list` | `ApiResponse<PageResultDto>` | 정상/정보/미분석 탭 목록 (서버 필터, 페이징) |
| `history()` | GET | `/dashboard/api/history` | `ApiResponse<List<Map>>` | 히스토리 그래프 시계열 (`stock`/`overseas`/`conn`) |
| `resource()` | GET | `/dashboard/api/resource` | `ApiResponse<List<Map>>` | 서버별 DISK_HOME 도넛 차트 데이터 |
| `serverList()` | GET | `/dashboard/api/server-list` | `ApiResponse<List<Map>>` | 서버 카드 (최근 수집/분석 시각, 에러/경고 건수) |
| `ucSpark()` | GET | `/dashboard/api/uc-spark` | `ApiResponse<Map>` | UC 실시간 접속자 스파크라인 (UC_TOTAL/HTS/MTS) |
| `rawLog()` | GET | `/dashboard/api/raw-log/{id}` | `ApiResponse<CollectLogDto>` | 원본 로그 모달 조회 |
| `history()` | GET | `/dashboard/history` | `String` (View) | History 페이지 화면 반환 (v1.6 신규) |
| `monthlyHistory()` | GET | `/dashboard/api/monthly-history` | `ApiResponse<Map>` | History 페이지용 4개 그룹 월별 12개월 시계열 일괄 반환 (v1.6 신규) |

### AdminUserController

| 메서드 | HTTP | URL | 설명 |
|--------|------|-----|------|
| `users()` | GET | `/admin/users` | 계정 목록 화면 (SUPER_ADMIN 전용) |
| `create()` | POST | `/admin/users` | 신규 계정 생성 |
| `unlock()` | POST | `/admin/users/{id}/unlock` | 계정 잠금 수동 해제 |
| `enable()` | POST | `/admin/users/{id}/enable` | 계정 활성화 |
| `disable()` | POST | `/admin/users/{id}/disable` | 계정 비활성화 |
| `delete()` | POST | `/admin/users/{id}/delete` | 계정 삭제 (SUPER_ADMIN 제외) |
| `resetPassword()` | POST | `/admin/users/{id}/reset-password` | 임시 비밀번호로 초기화 (다음 로그인 시 강제 변경) |

### DashboardService

| 메서드 | 반환 | 설명 |
|--------|------|------|
| `init()` | `void` | `@PostConstruct` — 수집/분석 스케줄 파일 파싱, 서버 수 캐싱 |
| `getSummary()` | `SummaryDto` | 오늘 요약 집계 (스케줄 기준 분모 보정 포함) |
| `getErrorWarningPage()` | `PageResultDto<AnalyzeResultDto>` | 에러/경고 탭 페이지 결과 |
| `getNormalInfoPage()` | `PageResultDto<AnalyzeResultDto>` | 정상/정보/미분석 탭 페이지 결과 |
| `getHistoryData()` | `List<Map>` | 그룹 타입별 히스토리 시계열 (일자별 마지막 값만 선정) |
| `getResourceData()` | `List<Map>` | 서버별 DISK_HOME 최신 리소스 수치 |
| `getAllInfoData()` | `Map<String, Object>` | LOG_ID별 최신 분석 결과 (카드 표시용) |
| `getUcSparkData()` | `Map<String, Object>` | 오늘 UC 접속자 시계열 3종 |
| `getMonthlyHistoryAll()` | `Map<String, Object>` | History 페이지용 4개 그룹(stock/overseas/service/conn) 월별 12개월 시계열 — Java-side YYYYMM 그룹핑, selectHistoryData 재사용 (v1.6 신규) |
| `getRawLog()` | `CollectLogDto` | 수집 로그 원문 단건 조회 |
| `parseScheduleServerCount()` | `int` | 스케줄 파일에서 고유 서버 수 추출 |
| `parseScheduleMap()` | `Map<String,String>` | 서버별 수집/분석 주기 문자열 매핑 |

### AccountService

| 메서드 | 설명 |
|--------|------|
| `listUsers()` | 전체 계정 목록 조회 |
| `createUser()` | 신규 계정 생성 (중복 체크, 복잡도 검증, 최초 강제 변경 설정) |
| `unlockUser()` | 계정 잠금 해제 + 감사 로그 |
| `setEnabled()` | 계정 활성/비활성 전환 (SUPER_ADMIN 비활성화 불가) |
| `resetPassword()` | 임시 비밀번호 초기화 + 강제 변경 플래그 설정 |
| `deleteUser()` | 계정 삭제 (SUPER_ADMIN 삭제 불가, 비밀번호 이력도 삭제) |

### PasswordService

| 메서드 | 설명 |
|--------|------|
| `changeOwnPassword()` | 본인 비밀번호 변경 (현재 비밀번호 검증 → 정책 검증 → 이력 적재 → 갱신) |
| `validateNewPassword()` | 복잡도 + 재사용 금지 검증 (현재 비밀번호 + 최근 이력 1건) |
| `recordHistoryAndUpdatePassword()` | 기존 비밀번호 이력 적재 후 새 비밀번호로 갱신 (`@Transactional`) |

### PasswordPolicyValidator (정적 유틸)

| 메서드/상수 | 설명 |
|-------------|------|
| `PASSWORD_EXPIRE_DAYS = 90` | 만료 기준 일수 |
| `FORCE_CHANGE_BACKDATE_DAYS = 91` | 최초 로그인 강제 변경을 위한 역산 일수 |
| `validate(password, loginId)` | 복잡도 규칙 검증: 10자 이상, 3종 이상 조합, 연속/반복 3자 금지, ID 포함 금지 |

### AdminAuthenticationProvider

| 메서드 | 설명 |
|--------|------|
| `authenticate()` | 로그인 인증 전체 흐름 처리 |
| `autoUnlockIfExpired()` | 잠금 후 5분 경과 시 자동 해제 |
| `handleLoginFailure()` | 실패 카운트 증가, 5회 시 LOCKED 상태 전환 |

### PasswordExpiryInterceptor

| 메서드 | 설명 |
|--------|------|
| `preHandle()` | 매 요청 시 비밀번호 만료 여부 확인, 만료 시 `/password/change` 리다이렉트, D-7 경고 주입 |
| `postHandle()` | 만료 경고 일수를 모델에 바인딩 (헤더 배너 표시용) |

### AdminAuditLogService

| 메서드 | 설명 |
|--------|------|
| `log()` | 감사 로그 1건 적재 (ACTION_TYPE, ACTOR, CLIENT_IP, 설명) |
| `extractClientIp()` | `X-Forwarded-For` 우선, 없으면 `remoteAddr` |

---

## 5. 리소스 및 DB 환경

### DB 연결 정보

| 환경 | Driver | URL | 계정 |
|------|--------|-----|------|
| 테스트 (`-test`) | `org.postgresql.Driver` | `jdbc:postgresql://localhost:5432/postgres` | `postgres` |
| 운영 (`-prod`) | `Altibase.jdbc.driver.AltibaseDriver` | `jdbc:Altibase://192.168.x.x:20300/precheck` | `precheck` |

> 운영 비밀번호는 `application-prod.yml`에 `(운영 비밀번호)` 플레이스홀더로 기재. 실제 값은 별도 관리.

### 커넥션 풀 (HikariCP)

| 환경 | 최대 풀 크기 | 풀 이름 |
|------|-------------|---------|
| 테스트 | 5 | `PreCheckPool-Test` |
| 운영 | 10 | `PreCheckPool-Prod` |

### 사용 테이블 목록

| 테이블 | PK/시퀀스 | 역할 |
|--------|----------|------|
| `TB_ANALYZE_RESULT` | `SEQ_ANALYZE_RESULT` | 분석 결과 (에러/경고/정상/정보/미분석 레벨, 임계치, 알림 여부) |
| `TB_ANALYZE_HISTORY` | `SEQ_ANALYZE_HISTORY` | 분석 실행 이력 (서버별 성공/실패/건수) |
| `TB_COLLECT_HISTORY` | `SEQ_COLLECT_HISTORY` | 수집 실행 이력 (서버별 성공/실패/SKIP) |
| `TB_COLLECT_LOG` | `SEQ_COLLECT_LOG` | 수집 로그 원문 및 정규화 값 |
| `TB_ADMIN_USER` | `SEQ_ADMIN_USER` | 관리자 계정 (비밀번호/만료/잠금 상태 포함) |
| `TB_ADMIN_USER_PWD_HISTORY` | `SEQ_ADMIN_USER_PWD_HISTORY` | 비밀번호 변경 이력 (재사용 금지 판정용) |
| `TB_ADMIN_USER_LOG` | `SEQ_ADMIN_USER_LOG` | 감사 로그 (로그인/비밀번호 변경/계정관리 이벤트) |

### TB_ANALYZE_RESULT 주요 컬럼

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `SERVER_ID` | VARCHAR(100) | 서버 구분 식별자 (예: `dlprem01-테스트개발`) |
| `LOG_TYPE` | VARCHAR(10) | 로그 유형 (`수치`/`비교`/`시간`) |
| `LOG_ID` | VARCHAR(30) | 로그 항목 ID (예: `DISK_HOME`, `MBSOSI_COUNT`) |
| `LOG_VALUE` | NUMERIC(18,6) | 수치형 로그 값 |
| `ANALYZE_LEVEL` | VARCHAR(10) | 분석 레벨 (`에러`/`경고`/`정상`/`정보`/`미분석`) |
| `THRESHOLD_VALUE` | NUMERIC(18,6) | 임계치 |
| `THRESHOLD_OPERATOR` | VARCHAR(5) | 임계치 연산자 (`<`, `>=` 등) |
| `WARNING_RATIO` | NUMERIC(5,2) | 경고 시 임계치 대비 비율 |
| `NOTIFY_YN` | CHAR(1) | 알림 발송 여부 |
| `ANALYZE_DATE` | VARCHAR(8) | 분석 일자 (`yyyyMMdd` 형식) |

### 외부 리소스

| 리소스 | 위치 | 역할 |
|--------|------|------|
| 수집 스케줄 정의서 | `application.yml: precheck.collect-schedule-path` | 수집 대상 서버 수 파악, 서버별 수집 주기 표시 |
| 분석 스케줄 정의서 | `application.yml: precheck.analyze-schedule-path` | 분석 대상 서버 수 파악, 서버별 분석 주기 표시 |

> 스케줄 파일은 기동 시 1회 파싱하여 메모리에 캐싱. 파일 변경 시 재기동 필요.

---

## 6. 설정 파일 분석

### `src/main/resources/application.yml` (기본 설정)

| 항목 | 기본값 | 설명 |
|------|--------|------|
| `server.port` | `8080` | HTTP 서버 포트 |
| `server.servlet.session.timeout` | `9h` | 세션 유지 시간 (9시간) |
| `server.servlet.session.cookie.http-only` | `true` | XSS 방어용 HttpOnly 쿠키 |
| `server.servlet.session.cookie.secure` | `false` | HTTPS 전용 여부 (운영 시 `true` 권장) |
| `server.servlet.session.cookie.same-site` | `lax` | CSRF 방어 SameSite 설정 |
| `spring.profiles.active` | `test` | 기본 활성 프로파일 |
| `spring.thymeleaf.cache` | `false` | 템플릿 캐시 비활성 (개발 편의) |
| `mybatis.mapper-locations` | `classpath:mapper/*.xml` | MyBatis XML 매퍼 위치 |
| `mybatis.configuration.map-underscore-to-camel-case` | `true` | DB 컬럼명 자동 camelCase 변환 |
| `mybatis.configuration.log-impl` | `StdOutImpl` | MyBatis 쿼리 콘솔 출력 (운영에선 NoLogging) |
| `precheck.refresh-interval-seconds` | `60` | 대시보드 자동 갱신 주기(초) |
| `precheck.history-days` | `60` | 히스토리 그래프 조회 기간(일) |
| `precheck.collect-schedule-path` | (절대경로) | 수집 스케줄 정의서 파일 경로 |
| `precheck.analyze-schedule-path` | (절대경로) | 분석 스케줄 정의서 파일 경로 |
| `precheck.info-data` | (목록) | 주요 데이터 카드 항목 (name/serverId/logId 조합) |

### `src/main/resources/application-test.yml` (PostgreSQL 테스트)

| 항목 | 값 | 설명 |
|------|-----|------|
| `spring.datasource.driver-class-name` | `org.postgresql.Driver` | PostgreSQL JDBC 드라이버 |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/postgres` | 로컬 PostgreSQL 연결 URL |
| `spring.datasource.username` | `postgres` | DB 사용자명 |
| `hikari.maximum-pool-size` | `5` | 최대 커넥션 수 |
| `hikari.minimum-idle` | `2` | 최소 유지 커넥션 수 |
| `hikari.connection-timeout` | `30000` | 커넥션 획득 대기시간(30초) |
| `hikari.idle-timeout` | `600000` | 유휴 커넥션 유지시간(10분) |
| `hikari.max-lifetime` | `1800000` | 커넥션 최대 수명(30분) |

### `src/main/resources/application-prod.yml` (Altibase 운영)

| 항목 | 값 | 설명 |
|------|-----|------|
| `spring.datasource.url` | `jdbc:Altibase://192.168.x.x:20300/precheck` | Altibase 운영 DB URL |
| `spring.datasource.driver-class-name` | `Altibase.jdbc.driver.AltibaseDriver` | Altibase JDBC 드라이버 (별도 jar 필요) |
| `hikari.maximum-pool-size` | `10` | 최대 커넥션 수 |
| `mybatis.configuration.log-impl` | `NoLoggingImpl` | 운영 환경 MyBatis 로그 비활성 |

### `src/main/resources/mapper/DashboardMapper.xml` 주요 쿼리

| 쿼리 ID | 대상 테이블 | 설명 |
|---------|------------|------|
| `selectSummary` | `TB_ANALYZE_RESULT`, `TB_COLLECT_HISTORY`, `TB_ANALYZE_HISTORY` | 오늘 기준 레벨별 건수 + 수집/분석 성공률 집계 |
| `selectErrorWarningList` | `TB_ANALYZE_RESULT` | 에러/경고 탭 목록 (PostgreSQL: `LIMIT…OFFSET`, Altibase: `LIMIT offset,size`) |
| `selectNormalInfoList` | `TB_ANALYZE_RESULT` | 정상/정보/미분석 탭 목록 |
| `selectServerList` | `TB_ANALYZE_HISTORY`, `TB_COLLECT_HISTORY`, `TB_ANALYZE_RESULT` | 서버별 최근 수집/분석 시각 + 에러/경고 건수 |
| `selectHistoryData` | `TB_ANALYZE_RESULT` | 기간별 LOG_ID 시계열 데이터 |
| `selectResourceData` | `TB_ANALYZE_HISTORY`, `TB_ANALYZE_RESULT` | 서버별 DISK_HOME 최신값 |
| `selectInfoData` | `TB_ANALYZE_RESULT` | serverId + logId 기준 오늘 최신 분석 결과 1건 |
| `selectUcSparkData` | `TB_ANALYZE_RESULT` | 오늘 최근 60분 UC 접속자 시계열 |
| `selectRawLog` | `TB_COLLECT_LOG` | 수집 로그 원문 단건 |

> **주의**: `selectErrorWarningList`, `selectNormalInfoList`는 `databaseId="postgresql"`/`"altibase"` 두 벌이 존재. MyBatisConfig에서 `databaseId`를 드라이버 클래스명으로 판별.

### `gradle/wrapper/gradle-wrapper.properties`

| 항목 | 값 |
|------|-----|
| `distributionUrl` | Gradle 8.14.5 배포 URL |

---

## 7. 보안 정책 요약 (8__로그인_보안정책정의서.md 기준)

| 정책 | 내용 |
|------|------|
| 로그인 실패 잠금 | 5회 연속 실패 시 `LOCKED` 상태 전환 |
| 자동 잠금 해제 | 잠금 후 5분 경과 시 다음 로그인 시도 시 자동 해제 |
| 비밀번호 복잡도 | 10자 이상, 대/소문자·숫자·특수문자 중 3종 이상, 연속/반복 3자 금지, 로그인 ID 포함 금지 |
| 비밀번호 만료 | 90일 경과 시 강제 변경, D-7부터 경고 배너 표시 |
| 재사용 금지 | 현재 비밀번호 + 직전 1개 이력 재사용 불가 |
| SUPER_ADMIN 예외 | 비밀번호 만료 정책 미적용 (`PASSWORD_EXPIRE_YN='N'`) |
| 감사 로그 | 로그인 성공/실패, 비밀번호 변경/초기화, 계정 생성/잠금/해제/활성화/삭제 기록 |
| 로그인 실패 메시지 | 잠긴 계정만 별도 메시지, 나머지는 동일 일반 메시지 (정보 노출 방지) |
