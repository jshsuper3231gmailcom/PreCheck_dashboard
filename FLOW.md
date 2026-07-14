# dashboard 개발자 참고 문서

> 최신 소스 기준 재작성본. 이 문서는 `precheck_collect/dashboard` 모듈만 다룬다. `.trae/rules/`의 설계 문서 사본은 별도 파일이며 여기서는 다루지 않는다.

## 1. 프로젝트 개요

### 목적 및 역할

`dashboard`는 PreCheck 시스템의 **관리자 전용 조회 웹 대시보드**다. `collect`가 수집하고 `analyze`가 판정한 결과(`TB_COLLECT_LOG`, `TB_COLLECT_HISTORY`, `TB_ANALYZE_RESULT`, `TB_ANALYZE_HISTORY`)를 같은 DB에서 직접 읽어 Thymeleaf 화면으로 시각화한다. REST 호출 없이 DB를 직접 조회하는 구조이며, **점검 파이프라인 데이터에 대해서는 쓰기 작업이 없다**(조회 전용).

단, 이 원칙에는 예외가 하나 있다 — 대시보드 자체의 **로그인/계정 관리 기능**은 `TB_ADMIN_USER`, `TB_ADMIN_USER_PWD_HISTORY`, `TB_ADMIN_USER_LOG` 3개 테이블에 대해 읽기·쓰기를 모두 수행한다(로그인 성공/실패 카운트 갱신, 계정 잠금/해제, 비밀번호 변경/이력 적재, 감사 로그 적재 등). 즉 "collect/analyze가 쓴 파이프라인 데이터는 읽기만 한다"는 원칙이지, 모듈 전체가 DB에 아무것도 쓰지 않는다는 뜻은 아니다.

화면은 크게 세 갈래다.
1. **메인 대시보드**(`/dashboard`) — 상단 요약, 주요 데이터 카드, 리소스 도넛차트, 서버 리스트, 에러/경고·정상/정보 탭, UC 실시간 접속자 스파크라인을 60초 주기로 갱신한다.
2. **히스토리 페이지**(`/dashboard/history`) — 최근 12개월 월별 시계열(종목/해외종목/서비스/접속자)을 보여준다.
3. **로그인/계정 관리**(`/login`, `/password/change`, `/admin/users`) — Spring Security 기반 인증, 비밀번호 정책, SUPER_ADMIN 전용 계정관리 화면.

### 기술 스택

`build.gradle` 기준 실제 의존성:

| 구분 | 라이브러리 |
|------|-----------|
| 프레임워크 | Spring Boot 3.3.12 (Java 17 toolchain) |
| 웹/템플릿 | `spring-boot-starter-web`, `spring-boot-starter-thymeleaf` |
| 보안 | `spring-boot-starter-security`, `thymeleaf-extras-springsecurity6` |
| DB 접근 | `mybatis-spring-boot-starter:3.0.3` (XML 매퍼) |
| DB 드라이버 | `postgresql`(runtimeOnly) — Altibase 드라이버는 gradle 의존성에 없고 운영 배포 시 별도 추가 필요 <!-- TODO: 확인 필요 --> |
| 코드 생성 | Lombok (compileOnly + annotationProcessor) |
| 개발 편의 | `spring-boot-devtools` (developmentOnly) |
| 테스트 | `spring-boot-starter-test` (JUnit 5) |
| 프런트엔드 | AdminLTE(정적 리소스로 번들), Bootstrap 5 + bootstrap-icons, Chart.js(정적 JS로 로드, npm 의존성 아님) |

group: `com.sks.precheck`, version: `0.0.1-SNAPSHOT`.

### 실행 방식

HTTP 서버(port 8080)로 상시 기동하는 일반 웹 애플리케이션이다. `collect`/`analyze`/`notify`와 달리 `@Scheduled` 배치가 없다 — 화면 갱신은 **브라우저 JS의 60초 폴링**으로 이루어진다.

```bash
gradlew.bat bootRun                     # 기본 프로파일 test (PostgreSQL localhost)
gradlew.bat bootRun --args="--spring.profiles.active=prod"   # Altibase 운영
java -jar build/libs/dashboard-0.0.1-SNAPSHOT.jar
```

최초 1회 `src/main/resources/sql/init_dev.sql`을 PostgreSQL에 적용해 DDL·시퀀스·초기 SUPER_ADMIN 계정(`admin`)을 생성해야 한다.

---

## 2. 데이터 흐름

### 전체 흐름도

```
[브라우저]
   │  GET /dashboard  (최초 진입, 인증 필요)
   ▼
[SecurityConfig / AdminAuthenticationProvider]  ← 미인증 시 /login 리다이렉트
   │
   ▼
[PasswordExpiryInterceptor]  ← 비밀번호 90일 만료 시 /password/change 강제 이동
   │
   ▼
[DashboardController.dashboard()]
   │  Model에 로그인 사용자명/권한/refreshIntervalSeconds 담아 Thymeleaf 렌더
   ▼
[templates/dashboard/index.html]  (SSR, 최초 페이지)
   │
   │  이후 브라우저 JS가 REFRESH_INTERVAL_SEC(기본 300, application.yml precheck.refresh-interval-seconds
   │  로 60 지정 시 60초) 주기로 아래 API들을 병렬 호출
   ▼
[DashboardController.summary/info-data/error-list/normal-list/history/resource/server-list/uc-spark]
   │  각 API는 handle() 공통 래퍼로 예외를 감싸 ApiResponse<T>(success/data/message) 형태로 응답
   ▼
[DashboardService]  ── 업무 로직 조합 (오늘 날짜 계산, 스케줄 conf 기반 분모 보정, 페이징 등)
   │
   ▼
[DashboardMapper (MyBatis XML)]  ── databaseId(postgresql/altibase)로 분기된 SQL 실행
   │
   ▼
[PostgreSQL(test/local) / Altibase(prod)]  ── TB_COLLECT_LOG, TB_COLLECT_HISTORY,
   │                                          TB_ANALYZE_RESULT, TB_ANALYZE_HISTORY 조회
   ▼
[JSON 응답] → 브라우저가 Chart.js/DOM 갱신 (페이지 전체 리로드 없음)
```

### 주요 시나리오별 흐름

**① 로그인**
```
POST /login (username, password)
  → SecurityConfig의 formLogin → AdminAuthenticationProvider.authenticate()
    → AdminUserMapper.selectByLoginId()
    → 계정 없음/잠김/비활성/비밀번호 불일치 시 각각 AuthMessages 상수로 통일된 예외
    → 5회 연속 실패 시 자동 잠금(LOCKED, LOCKED_AT 기록), 5분 경과 시 다음 로그인 시도에서 자동 해제
    → 성공 시 LOGIN_FAIL_COUNT=0, LAST_LOGIN_AT 갱신 + AdminAuditLogService로 LOGIN_SUCCESS/LOGIN_FAIL 감사 로그 적재
  → 성공: /dashboard로 리다이렉트, 실패: /login?error=locked|disabled|bad
```

**② 60초 폴링 갱신 (메인 대시보드)**
```
index.html의 startCountdown()이 1초마다 카운트다운
  → 0이 되면 refreshAll() 호출
    → Promise.all([ /dashboard/api/summary, /dashboard/api/info-data,
                     /dashboard/api/error-list 또는 /normal-list(현재 탭),
                     /dashboard/api/resource, /dashboard/api/history?groupType=...,
                     /dashboard/api/server-list, /dashboard/api/uc-spark ])
    → 각 응답으로 DOM/Chart.js 갱신, countdown을 REFRESH_INTERVAL_SEC로 리셋
```
`refresh-interval-seconds`는 `application.yml`의 `precheck.refresh-interval-seconds`(기본 60초로 운영 설정, `InfoDataConfig` 기본값 자체는 300)를 서버가 `dashboard()` 컨트롤러에서 모델에 담아 Thymeleaf 인라인 JS(`REFRESH_INTERVAL_SEC`)로 내려준다.

**③ 비밀번호 만료 강제 변경**
```
인증된 모든 요청 → PasswordExpiryInterceptor.preHandle()
  → SecurityContext의 principal로 AdminUserMapper.selectByLoginId() 재조회(세션 캐시 아님)
  → PasswordPolicyValidator.daysRemaining() 계산
    - PASSWORD_EXPIRE_YN != 'Y' 또는 미기록 → 통과(Long.MAX_VALUE)
    - 잔여일 <= 0 → /password/change로 302 리다이렉트, 화면 처리 중단
    - 잔여일 <= 7 → request attribute로 D-n 경고를 넘겨 postHandle()에서 뷰 모델에 주입(헤더 배너)
```

**④ 계정관리 (SUPER_ADMIN 전용)**
```
GET/POST /admin/users/** (SecurityConfig가 hasRole("SUPER_ADMIN")으로 제한)
  → AdminUserController → AccountService → AdminUserMapper (CRUD) + AdminAuditLogService(감사 로그)
  → 계정 생성/비밀번호 초기화는 PASSWORD_CHANGED_AT을 91일 전으로 백데이트 + PASSWORD_EXPIRE_YN='Y'로
    설정해 "최초 로그인 시 강제 변경"을 재현한다.
```

**⑤ 원본 로그 확인 (모달)**
```
사용자가 에러/경고 목록에서 특정 행 클릭
  → GET /dashboard/api/raw-log/{id}
  → DashboardService.getRawLog() → DashboardMapper.selectRawLog()
  → TB_COLLECT_LOG에서 원본 @@@...@@@ 정규화 로그 원문(RAW_LOG)과 수집 메타를 모달에 표시
```

---

## 3. 디렉토리 및 파일 구조

### 디렉토리 역할

```
dashboard/
├── src/main/java/com/sks/precheck/dashboard/
│   ├── DashboardApplication.java     진입점 (@SpringBootApplication + @ConfigurationPropertiesScan)
│   ├── config/                       Spring 설정 클래스 (Security, MyBatis, WebMvc, 파일 기반 프로퍼티)
│   ├── controller/                   화면/API 컨트롤러 + 공통 응답 래퍼
│   ├── dto/                          MyBatis resultType / 화면 바인딩용 DTO (Lombok @Data)
│   ├── mapper/                       MyBatis @Mapper 인터페이스 (SQL은 resources/mapper/*.xml)
│   ├── security/                     Spring Security 인증 Provider, Principal, 인터셉터, 메시지 상수
│   └── service/                      업무 로직 (조회 조합, 계정관리, 비밀번호 정책, 감사 로그)
├── src/main/resources/
│   ├── application.yml                공통 설정 (서버, Thymeleaf, MyBatis, precheck.* 커스텀 프로퍼티)
│   ├── application-test.yml           로컬 PostgreSQL 프로파일
│   ├── application-prod.yml           운영 Altibase 프로파일
│   ├── mapper/                        MyBatis 매퍼 XML (DashboardMapper.xml, AdminUserMapper.xml)
│   ├── sql/init_dev.sql               최초 1회 DDL + 샘플 데이터 + 초기 SUPER_ADMIN 계정
│   ├── templates/                     Thymeleaf 뷰 (dashboard/, admin/, password/, login.html)
│   └── static/                        css/js/plugins (AdminLTE, Bootstrap, bootstrap-icons, Chart.js 연동 스크립트)
├── src/test/java/.../DashboardApplicationTests.java   컨텍스트 로딩 스모크 테스트만 존재
├── architectui-html-free/             참고용 템플릿 데모 리소스(디자인 참고 자료, 애플리케이션에서 미사용) <!-- TODO: 확인 필요 -->
├── .trae/rules/                       설계 문서 동기화 사본 (마스터는 프로젝트 루트 context_org/) — 이 문서에서 다루지 않음
├── cookies2.txt / cookies3.txt / csrf.html / csrf2.html / csrf3.html   수동 테스트 중 생성된 임시 산출물로 보임 <!-- TODO: 확인 필요, 정리 대상일 수 있음 -->
├── hs_err_pid*.log / replay_pid*.log  JVM 크래시 덤프 잔여 파일로 보임 <!-- TODO: 확인 필요 -->
└── build.gradle / settings.gradle / gradlew(.bat)
```

### 주요 파일 목록

| 파일 | 역할 한 줄 요약 |
|------|----------------|
| `config/InfoDataConfig.java` | `precheck.*` 프로퍼티 바인딩 (스케줄 conf 경로, 새로고침 주기, 주요 데이터 카드 목록) |
| `config/MyBatisConfig.java` | PostgreSQL/Altibase `databaseId` 매핑 (`postgresql`/`altibase`) |
| `config/PasswordEncoderConfig.java` | `BCryptPasswordEncoder` 빈 (순환참조 회피 위해 SecurityConfig에서 분리) |
| `config/SecurityConfig.java` | 인증 방식, URL 접근 제어, 로그인 실패 핸들링, 로그아웃 정책 |
| `config/WebMvcConfig.java` | `PasswordExpiryInterceptor` 등록 및 제외 경로 설정 |
| `controller/DashboardController.java` | 대시보드/히스토리 화면 + 조회 API 9종 |
| `controller/LoginController.java` | `/login` 화면, 실패 사유별 메시지 매핑 |
| `controller/PasswordController.java` | `/password/change` 화면 및 변경 처리 |
| `controller/AdminUserController.java` | `/admin/users` 화면 및 계정관리 API 6종 (SUPER_ADMIN 전용) |
| `controller/ApiResponse.java` | 조회 API 공통 응답 래퍼 (`success`/`data`/`message`) |
| `security/AdminAuthenticationProvider.java` | 로그인 인증 + 실패카운트/잠금/자동해제 처리 |
| `security/AdminUserPrincipal.java` | `UserDetails` 구현체, `AdminUserDto` 원본 보관 |
| `security/ApiAuthenticationEntryPoint.java` | 미인증 요청 처리 (`/dashboard/api/**`는 401 JSON, 그 외는 `/login` 리다이렉트) |
| `security/AuthMessages.java` | 로그인 실패 안내 문구 상수 |
| `security/PasswordExpiryInterceptor.java` | 비밀번호 90일 만료 체크 + D-7 경고 배너 |
| `service/DashboardService.java` | 대시보드 화면 전체 조회 데이터 조합 (요약/카드/히스토리/리소스/서버리스트/스파크라인) |
| `service/AccountService.java` | 계정 생성/잠금해제/활성화토글/삭제/비밀번호초기화 (SUPER_ADMIN 화면 지원) |
| `service/AdminAuditLogService.java` | `TB_ADMIN_USER_LOG` 감사 로그 적재 공통 로직 |
| `service/PasswordPolicyValidator.java` | 비밀번호 복잡도/만료/재사용 규칙 정적 유틸 |
| `service/PasswordService.java` | 본인 비밀번호 변경 처리 + 검증/이력 적재 |
| `mapper/DashboardMapper.java` + `resources/mapper/DashboardMapper.xml` | 조회 전용 SQL (요약, 목록, 히스토리, 리소스, 원본로그) |
| `mapper/AdminUserMapper.java` + `resources/mapper/AdminUserMapper.xml` | 계정/비밀번호이력/감사로그 CRUD SQL |
| `resources/sql/init_dev.sql` | 전체 DDL + 시퀀스 + 샘플 데이터 + 초기 admin 계정 |
| `resources/templates/dashboard/index.html` | 메인 대시보드 SSR 뷰 + 폴링/차트 JS (약 2,300줄) |
| `resources/templates/dashboard/history.html` | 월별 히스토리 뷰 + 조회 JS |
| `resources/templates/login.html` | 로그인 화면 |
| `resources/templates/password/change.html` | 비밀번호 변경 화면 |
| `resources/templates/admin/users.html` | 계정관리 화면 |
| `resources/static/js/precheck-theme.js` | 라이트/다크 테마 토글 + FOUC 방지 |
| `resources/static/js/chart-spike.js` | Chart.js 크로스헤어(스파이크 라인) 전역 플러그인 |
| `resources/static/js/security.js` | 화면 우클릭/복사/개발자도구 단축키 차단 |

---

## 4. 소스별 주요 함수/메서드

### `DashboardApplication.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `main` | `String[] args` | `void` | Spring Boot 애플리케이션 기동 (`@ConfigurationPropertiesScan`으로 `InfoDataConfig` 자동 바인딩) |

### `config/InfoDataConfig.java`
Lombok `@Data`로 getter/setter 자동 생성. `@ConfigurationProperties(prefix = "precheck")`.

| 필드 | 타입 | 설명 |
|------|------|------|
| `collectSchedulePath` | `String` | 수집 스케줄 conf 절대경로 |
| `analyzeSchedulePath` | `String` | 분석 스케줄 conf 절대경로 |
| `historyDays` | `int` (기본 7) | 메인 대시보드 히스토리 그래프 조회 기간 |
| `refreshIntervalSeconds` | `int` (기본 300) | 화면 자동 갱신 주기 (실제 운영값은 `application.yml`에서 60으로 override) |
| `infoData` | `List<InfoDataItem>` | 주요 데이터 카드 표시 목록 (name/serverId/logId 조합) |
| `InfoDataItem` (nested `@Data`) | - | 카드 1건: 표시명, 조회 대상 서버구분, LOG_ID |

### `config/MyBatisConfig.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `databaseIdProvider` | 없음 | `VendorDatabaseIdProvider` | PostgreSQL→`postgresql`, Altibase→`altibase` 매핑 빈 등록 |

### `config/PasswordEncoderConfig.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `passwordEncoder` | 없음 | `PasswordEncoder` | `BCryptPasswordEncoder` 빈 등록 |

### `config/SecurityConfig.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `filterChain` | `HttpSecurity http` | `SecurityFilterChain` | URL별 접근 제어, 커스텀 `AuthenticationProvider` 등록, 로그인 실패 핸들러(잠김/비활성/일반 구분), 로그아웃 정책 구성 |

### `config/WebMvcConfig.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `addInterceptors` | `InterceptorRegistry registry` | `void` | `PasswordExpiryInterceptor`를 `/login`, `/logout`, `/password/change`, 정적 리소스를 제외한 모든 경로에 등록 |

### `controller/DashboardController.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `handle` (private) | `String apiName, Supplier<T> supplier` | `ApiResponse<T>` | 조회 API 공통 예외 처리 래퍼. 실패 시 서버 로그만 상세 기록, 클라이언트엔 일반화된 메시지 반환 |
| `dashboard` | `AdminUserPrincipal principal, Model model` | `String` | `/dashboard` — 메인 화면 반환, 로그인 사용자명/권한/새로고침주기 모델 바인딩 |
| `summary` | 없음 | `ApiResponse<SummaryDto>` | `/dashboard/api/summary` — 상단 요약 스트립 |
| `infoData` | 없음 | `ApiResponse<Map<String,Object>>` | `/dashboard/api/info-data` — 주요 데이터 카드 (LOG_ID별 최신 분석 결과) |
| `errorList` | `String serverId, int page` | `ApiResponse<PageResultDto<AnalyzeResultDto>>` | `/dashboard/api/error-list` — 에러/경고 탭 페이지 목록 |
| `normalList` | `String serverId, int page` | `ApiResponse<PageResultDto<AnalyzeResultDto>>` | `/dashboard/api/normal-list` — 정상/정보/미분석 탭 페이지 목록 |
| `history` | `String groupType` | `ApiResponse<List<Map<String,Object>>>` | `/dashboard/api/history` — 종목/해외종목/접속자 그래프 시계열 (groupType: stock/overseas/conn) |
| `resource` | 없음 | `ApiResponse<List<Map<String,Object>>>` | `/dashboard/api/resource` — 서버별 리소스 도넛차트 데이터 |
| `serverList` | 없음 | `ApiResponse<List<Map<String,Object>>>` | `/dashboard/api/server-list` — 서버별 최근 수집/분석 시각 + 에러/경고 건수 |
| `ucSpark` | 없음 | `ApiResponse<Map<String,Object>>` | `/dashboard/api/uc-spark` — UC 접속자수 스파크라인(TOTAL/HTS/MTS) |
| `history` (오버로드) | `AdminUserPrincipal principal, Model model` | `String` | `/dashboard/history` — 히스토리 화면 반환 |
| `monthlyHistory` | 없음 | `ApiResponse<Map<String,Object>>` | `/dashboard/api/monthly-history` — 히스토리 화면용 12개월 월별 시계열 (stock/overseas/service/conn 4그룹) |
| `rawLog` | `Long id` | `ApiResponse<CollectLogDto>` | `/dashboard/api/raw-log/{id}` — 원본 정규화 로그 모달 조회 |

### `controller/LoginController.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `login` | `String error, Model model` | `String` | `/login` — 실패 구분값(locked/disabled/그외)에 따라 `AuthMessages` 문구를 모델에 담아 로그인 화면 반환 |

### `controller/PasswordController.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `form` | `AdminUserPrincipal principal, Model model` | `String` | `/password/change` GET — 강제 변경 여부(`forced`)와 사용자명 표시 |
| `change` | `AdminUserPrincipal, String currentPassword, String newPassword, String confirmPassword, HttpServletRequest, Model, RedirectAttributes` | `String` | `/password/change` POST — 검증 후 변경, 성공 시 SecurityContext 인증정보 즉시 갱신 후 `/dashboard`로 리다이렉트 |
| `refreshAuthentication` (private) | `AdminUserDto refreshedUser` | `void` | 변경 직후 세션의 `Authentication` 객체를 최신 계정 정보로 교체 (stale principal로 인한 리다이렉트 루프 방지) |
| `isExpired` (private) | `AdminUserDto user` | `boolean` | `PasswordPolicyValidator.isExpired()` 위임 |

### `controller/AdminUserController.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `users` | `AdminUserPrincipal principal, Model model` | `String` | `/admin/users` GET — 계정 목록 화면 |
| `create` | `CreateUserRequest req, AdminUserPrincipal, HttpServletRequest` | `ApiResponse<Void>` | `POST /admin/users` — 신규 계정 생성 |
| `unlock` | `Long adminUserId, AdminUserPrincipal, HttpServletRequest` | `ApiResponse<Void>` | `POST /admin/users/{id}/unlock` — 잠금 즉시 해제 |
| `enable` | `Long adminUserId, AdminUserPrincipal, HttpServletRequest` | `ApiResponse<Void>` | `POST /admin/users/{id}/enable` — 계정 활성화 |
| `disable` | `Long adminUserId, AdminUserPrincipal, HttpServletRequest` | `ApiResponse<Void>` | `POST /admin/users/{id}/disable` — 계정 비활성화 (SUPER_ADMIN 불가) |
| `delete` | `Long adminUserId, AdminUserPrincipal, HttpServletRequest` | `ApiResponse<Void>` | `POST /admin/users/{id}/delete` — 계정 삭제 (SUPER_ADMIN 불가) |
| `resetPassword` | `Long adminUserId, ResetPasswordRequest, AdminUserPrincipal, HttpServletRequest` | `ApiResponse<Void>` | `POST /admin/users/{id}/reset-password` — 임시 비밀번호로 초기화(다음 로그인 강제 변경) |
| `CreateUserRequest` (record) | `loginId, userName, role, initialPassword` | - | 계정 생성 요청 바디 |
| `ResetPasswordRequest` (record) | `newPassword` | - | 비밀번호 초기화 요청 바디 |

### `controller/ApiResponse.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `ok` (static) | `T data` | `ApiResponse<T>` | 성공 응답 생성 (`success=true`) |
| `fail` (static) | `String message` | `ApiResponse<T>` | 실패 응답 생성 (메시지 비었으면 `"ERROR"` 기본값) |

### `security/AdminAuthenticationProvider.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `authenticate` | `Authentication authentication` | `Authentication` | 계정 조회 → 자동잠금해제 → 잠김/비활성 차단 → 비밀번호 검증 → 성공/실패 처리 + 감사 로그 |
| `supports` | `Class<?> authentication` | `boolean` | `UsernamePasswordAuthenticationToken` 지원 여부 |
| `autoUnlockIfExpired` (private) | `AdminUserDto user` | `void` | 잠금 후 5분 경과 시 자동 해제 (DB 갱신 + 메모리 객체도 즉시 갱신) |
| `handleLoginFailure` (private) | `AdminUserDto user` | `void` | 실패 카운트 증가, 5회 도달 시 잠금 처리 |

### `security/AdminUserPrincipal.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `getAdminUser` | 없음 | `AdminUserDto` | 원본 계정 DTO 반환 |
| `getAuthorities` | 없음 | `Collection<? extends GrantedAuthority>` | `ROLE_{role}` 단일 권한 반환 |
| `getPassword`/`getUsername` | 없음 | `String` | `UserDetails` 표준 구현 |
| `isAccountNonExpired`/`isAccountNonLocked`/`isCredentialsNonExpired`/`isEnabled` | 없음 | `boolean` | 항상 `true` (실제 판정은 `AdminAuthenticationProvider`에서 인증 이전에 완료) |

### `security/ApiAuthenticationEntryPoint.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `commence` | `HttpServletRequest, HttpServletResponse, AuthenticationException` | `void` | `/dashboard/api/**` 요청은 401 JSON, 그 외는 `/login` 리다이렉트 |

### `security/PasswordExpiryInterceptor.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `preHandle` | `HttpServletRequest, HttpServletResponse, Object handler` | `boolean` | 만료(잔여일<=0) 시 `/password/change`로 리다이렉트하고 체인 중단, D-7 이내면 request attribute에 경고일수 저장 |
| `postHandle` | `..., ModelAndView modelAndView` | `void` | 경고일수를 뷰 모델에 주입 (redirect 뷰는 제외) |

### `service/DashboardService.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `init` (`@PostConstruct`) | 없음 | `void` | 기동 시 수집/분석 스케줄 conf를 1회 파싱해 서버 수·스케줄 맵 캐싱 |
| `getSummary` | 없음 | `SummaryDto` | 오늘 분석레벨별 건수/비율 + 스케줄 기준 분모 보정 + 실패/제외 사유 목록 |
| `formatFailReasons` (private) | `List<Map<String,Object>> rows` | `List<String>` | `서버구분: 실패사유` 문자열 목록으로 변환 |
| `getErrorWarningList` | `String serverId, int page` | `List<AnalyzeResultDto>` | 에러/경고 탭 목록 (페이지 계산 포함) |
| `getErrorWarningCount` | `String serverId` | `int` | 에러/경고 탭 전체 건수 |
| `getErrorWarningPage` | `String serverId, int page` | `PageResultDto<AnalyzeResultDto>` | 목록+건수 조합 |
| `getNormalInfoList` | `String serverId, int page` | `List<AnalyzeResultDto>` | 정상/정보/미분석 탭 목록 |
| `getNormalInfoCount` | `String serverId` | `int` | 정상/정보/미분석 탭 전체 건수 |
| `getNormalInfoPage` | `String serverId, int page` | `PageResultDto<AnalyzeResultDto>` | 목록+건수 조합 |
| `getServerList` | 없음 | `List<Map<String,Object>>` | 서버별 최근 수집/분석 시각 + 에러/경고 건수 + 캐싱된 스케줄 표시문자열 병합 |
| `getHistoryData` | `String groupType` | `List<Map<String,Object>>` | stock/overseas/conn 그룹별 LOG_ID 시계열, 날짜별 최신값만 남김 |
| `getResourceData` | 없음 | `List<Map<String,Object>>` | 서버별 리소스(DISK_HOME) 최신 분석 결과 |
| `getAllInfoData` | 없음 | `Map<String,Object>` | `infoData` 설정 목록 전체에 대해 LOG_ID별 최신 분석 결과 조합 (없으면 null 유지) |
| `getUcSparkData` | 없음 | `Map<String,Object>` | UC_TOTAL/HTS/MTS_COUNT 오늘 시계열 |
| `getMonthlyHistoryAll` | 없음 | `Map<String,Object>` | 히스토리 페이지용 12개월치 stock/overseas/service/conn 4그룹 월별 데이터 |
| `getRawLog` | `Long collectLogId` | `CollectLogDto` | 원본 정규화 로그 1건 조회 |
| `today` (private) | 없음 | `String` | `yyyyMMdd` 형식 오늘 날짜 |
| `ratio` (private) | `int numerator, int denominator` | `double` | 백분율 계산 (분모 0이면 0) |
| `parseScheduleServerCount` (private) | `String schedulePath` | `int` | 스케줄 conf에서 중복 제거된 서버 수 계산 |
| `parseScheduleMap` (private) | `String schedulePath` | `Map<String,String>` | 서버별 스케줄 표현식을 사람이 읽기 쉬운 문자열로 매핑 |
| `formatScheduleSpec` (private) | `String spec` | `String` | `배치\|요일\|시작시각` / `주기\|요일\|시작\|간격\|종료` 포맷 변환 |
| `formatDayCode` (private) | `String dayCode` | `String` | 요일 코드(`*`, `0-6`, 단일, 범위)를 한글 표기로 변환 |
| `formatTime` (private) | `String hhmmss` | `String` | `HHmmss` → `HH:mm:ss` |

### `service/AccountService.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `listUsers` | 없음 | `List<AdminUserDto>` | 전체 계정 목록 (LOGIN_ID 정렬) |
| `createUser` | `loginId, userName, role, initialPassword, actorLoginId, HttpServletRequest` | `void` | 신규 계정 생성, 91일 백데이트로 최초 로그인 강제 변경 유도, 감사 로그 적재 |
| `unlockUser` | `Long adminUserId, String actorLoginId, HttpServletRequest` | `void` | 잠금 즉시 해제 (SUPER_ADMIN 수동) |
| `setEnabled` | `Long adminUserId, boolean enable, String actorLoginId, HttpServletRequest` | `void` | 활성/비활성 전환 (SUPER_ADMIN은 비활성화 불가) |
| `resetPassword` | `Long adminUserId, String tempPassword, String actorLoginId, HttpServletRequest` | `void` | 임시 비밀번호로 초기화 (검증 + 이력 적재 재사용) |
| `deleteUser` | `Long adminUserId, String actorLoginId, HttpServletRequest` | `void` | 계정 삭제 (SUPER_ADMIN 불가, 비밀번호 이력도 함께 삭제) |
| `findById` (private) | `Long adminUserId` | `AdminUserDto` | 대상 계정 조회, 없으면 `IllegalArgumentException` |

### `service/AdminAuditLogService.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `log` | `Long adminUserId, String loginId, String actionType, String actorLoginId, HttpServletRequest, String description` | `void` | 감사 로그 1건 적재 (`TB_ADMIN_USER_LOG`) |
| `extractClientIp` (private) | `HttpServletRequest request` | `String` | `X-Forwarded-For` 우선, 없으면 `getRemoteAddr()` |

### `service/PasswordPolicyValidator.java` (정적 유틸)
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `expireYnAfterChange` (static) | `String role` | `String` | 변경 후 만료정책 적용 여부 (SUPER_ADMIN → `"N"`, 그 외 → `"Y"`) |
| `daysRemaining` (static) | `AdminUserDto user` | `long` | 만료까지 잔여일 (정책 미적용이면 `Long.MAX_VALUE`) |
| `isExpired` (static) | `AdminUserDto user` | `boolean` | 잔여일 <= 0 여부 |
| `validate` (static) | `String password, String loginId` | `List<String>` | 복잡도(10자↑, 3종 이상 조합, 연속/반복 3자 금지, ID 포함 금지) 위반 목록 |
| `countCharTypes` (private static) | `String password` | `int` | 대/소문자·숫자·특수문자 종류 수 |
| `hasRepeatedOrSequentialChars` (private static) | `String password` | `boolean` | 3자 연속 동일/오름차순/내림차순 여부 |

### `service/PasswordService.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `changeOwnPassword` | `AdminUserDto user, String currentPassword, String newPassword, String confirmPassword, HttpServletRequest` | `void` | 현재 비밀번호 검증 → 확인값 일치 → 정책 검증 → 갱신 + 감사 로그 |
| `validateNewPassword` | `AdminUserDto user, String newPassword` | `void` | 복잡도 + 현재값/최근 1건 이력 재사용 금지 검증 |
| `recordHistoryAndUpdatePassword` (`@Transactional`) | `AdminUserDto user, String newPassword, LocalDateTime passwordChangedAt, String passwordExpireYn` | `void` | 기존 비밀번호를 이력에 적재 후 새 해시로 갱신 |

### `mapper/DashboardMapper.java` (조회 전용)
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `selectSummary` | `String today` | `SummaryDto` | 분석레벨별 집계 + 수집/분석 성공 현황 (3개 서브쿼리 CROSS JOIN) |
| `selectCollectFailReasons` | `String today, String status` | `List<Map<String,Object>>` | 수집 실패/제외 사유 (FAIL/SKIP) |
| `selectAnalyzeFailReasons` | `String today, String status` | `List<Map<String,Object>>` | 분석 실패 사유 (FAIL) |
| `selectErrorWarningList` | `String today, String serverId, int offset, int pageSize` | `List<AnalyzeResultDto>` | 에러/경고 페이지 목록 (databaseId별 LIMIT 문법 분기) |
| `selectNormalInfoList` | `String today, String serverId, int offset, int pageSize` | `List<AnalyzeResultDto>` | 정상/정보/미분석 페이지 목록 |
| `countErrorWarning` | `String today, String serverId` | `int` | 에러/경고 전체 건수 |
| `countNormalInfo` | `String today, String serverId` | `int` | 정상/정보/미분석 전체 건수 |
| `selectServerList` | `String today` | `List<Map<String,Object>>` | 수집+분석 이력 합집합 기준 서버별 상태 요약 |
| `selectHistoryData` | `String startDate, String endDate, String serverId, String logId` | `List<AnalyzeResultDto>` | 기간 내 분석 결과 (히스토리 그래프 원본) |
| `selectResourceData` | `String today` | `List<Map<String,Object>>` | 서버별 DISK_HOME 최신 리소스 수치 |
| `selectInfoData` | `String today, String serverId, String logId` | `AnalyzeResultDto` | 카드 1건에 대응하는 최신 분석 결과 단건 |
| `selectUcSparkData` | `String today, String logId` | `List<Map<String,Object>>` | UC 접속자수 최근 60분 시계열 (pmaster2-마스터 고정) |
| `selectRawLog` | `Long collectLogId` | `CollectLogDto` | 원본 정규화 로그 1건 |

### `mapper/AdminUserMapper.java`
| 함수명 | 파라미터 | 반환값 | 설명 |
|--------|----------|--------|------|
| `selectByLoginId` | `String loginId` | `AdminUserDto` | LOGIN_ID로 계정 조회 |
| `selectById` | `Long adminUserId` | `AdminUserDto` | PK로 계정 조회 |
| `selectAll` | 없음 | `List<AdminUserDto>` | 전체 계정 목록 (LOGIN_ID 정렬) |
| `insertAdminUser` | `AdminUserDto dto` | `void` | 계정 생성 (databaseId별 시퀀스 채번 분기) |
| `updateLoginSuccess` | `Long adminUserId, LocalDateTime lastLoginAt` | `void` | 로그인 성공 시 실패카운트 초기화 + 최종 로그인 시각 갱신 |
| `updateLoginFailCount` | `Long adminUserId, int loginFailCount` | `void` | 실패 카운트 갱신 |
| `lockAccount` | `Long adminUserId, LocalDateTime lockedAt` | `void` | 계정 잠금 (STATUS='LOCKED') |
| `unlockAccount` | `Long adminUserId` | `void` | 잠금 해제 (자동/수동 공용) |
| `updateStatus` | `Long adminUserId, String status` | `void` | ACTIVE ↔ DISABLED 전환 |
| `updatePassword` | `Long adminUserId, String password, LocalDateTime passwordChangedAt, String passwordExpireYn` | `void` | 비밀번호/변경시각/만료플래그 갱신 |
| `insertPwdHistory` | `Long adminUserId, String password, LocalDateTime createdAt` | `void` | 비밀번호 변경 이력 적재 |
| `selectRecentPwdHistory` | `Long adminUserId, int limit` | `List<String>` | 재사용 검증용 최근 해시 목록 |
| `deleteAdminUser` | `Long adminUserId` | `void` | 계정 삭제 |
| `deletePwdHistoryByUser` | `Long adminUserId` | `void` | 계정의 비밀번호 이력 전체 삭제 |
| `insertAdminUserLog` | `AdminUserLogDto dto` | `void` | 감사 로그 1건 적재 |

### DTO (Lombok `@Data`/`@Getter`, 필드만 요약)

| 파일 | 주요 필드 |
|------|-----------|
| `dto/AdminUserDto.java` | adminUserId, loginId, password(`@JsonIgnore`), userName, role, status, loginFailCount, lockedAt, passwordChangedAt, passwordExpireYn, lastLoginAt, createdAt, updatedAt |
| `dto/AdminUserLogDto.java` | adminUserId, loginId, actionType, actorLoginId, clientIp, description, createdAt |
| `dto/AnalyzeHistoryDto.java` | analyzeHistoryId, serverId, sourceFilePath, analyzeStatus, lastAnalyzeLogId, totalCount, successCount, failCount, errorCount, warningCount, failReason, analyzeStartAt/EndAt, analyzeDate, createdAt/updatedAt |
| `dto/AnalyzeResultDto.java` | analyzeResultId, collectLogId, serverId, serverIp, logType, logId, logTimestamp, logContent, logValue(`BigDecimal`), analyzeLevel, analyzeMessage, thresholdValue/Operator, warningRatio, notifyYn/At, analyzeDate/Datetime, collectDate, createdAt |
| `dto/CollectHistoryDto.java` | collectHistoryId, serverId, sourceFilePath, collectStatus, collectedCount, retryCount, failReason, collectStartAt/EndAt, collectDate, createdAt |
| `dto/CollectLogDto.java` | collectLogId, serverId, serverIp, logType, logId, logTimestamp, logContent, logValue, rawLog, sourceFilePath, collectDatetime, collectDate, createdAt |
| `dto/PageResultDto.java` | items, page, pageSize, totalCount, totalPages(생성자에서 계산) — 생성자 `PageResultDto(List<T>, int, int, int)` |
| `dto/SummaryDto.java` | errorCnt/warnCnt/normalCnt/infoCnt/unknownCnt + 각 Ratio, collectSuccess/Total/Fail/Skip, analyzeSuccess/Total/Fail, collectRatio/analyzeRatio, collectFailReasons/collectSkipReasons/analyzeFailReasons(`List<String>`) |

---

## 5. 리소스 및 DB 환경

### DB 연결 정보

| 프로파일 | DB | URL | 계정 | 비고 |
|----------|----|----|------|------|
| `test`(기본, 로컬 개발) | PostgreSQL | `jdbc:postgresql://localhost:5432/postgres` | `postgres` / `[REDACTED]` | HikariCP `PreCheckPool-Test`, max 5 / min-idle 2 |
| `prod`(운영) | Altibase | `jdbc:Altibase://192.168.x.x:20300/precheck` (실제 IP는 마스킹 표기됨) | `precheck` / `[REDACTED]`(설정 파일에 `(운영 비밀번호)`로 플레이스홀더만 있음) | HikariCP `PreCheckPool-Prod`, max 10. MyBatis 로그는 `NoLoggingImpl`로 무음 처리 |

DB 벤더 분기는 `MyBatisConfig.databaseIdProvider()`가 담당하며, 매퍼 XML에서 `databaseId="postgresql"` / `databaseId="altibase"`로 SQL을 나눈다(주로 `LIMIT` 문법 차이, 시퀀스 채번 방식 차이). `collect`/`analyze`의 `SequenceHelper.java`와 달리 dashboard는 별도 헬퍼 클래스 없이 각 매퍼 XML에서 `nextval('SEQ_x')`(PostgreSQL) / `SEQ_x.NEXTVAL FROM DUAL`(Altibase)로 직접 분기한다.

### 사용 테이블/엔티티 목록

| 테이블 | 접근 방식 | 대시보드 내 용도 |
|--------|-----------|------------------|
| `TB_COLLECT_LOG` | **읽기 전용** | 원본 정규화 로그 모달 (`selectRawLog`) |
| `TB_COLLECT_HISTORY` | **읽기 전용** | 수집 성공/실패/제외 집계, 서버 리스트, 실패 사유 |
| `TB_ANALYZE_RESULT` | **읽기 전용** | 에러/경고·정상/정보/미분석 목록, 요약 집계, 히스토리 그래프, 리소스 도넛, 주요 데이터 카드, UC 스파크라인 (대시보드의 핵심 조회 테이블) |
| `TB_ANALYZE_HISTORY` | **읽기 전용** | 분석 성공/실패 집계, 서버 리스트, 실패 사유 |
| `TB_ADMIN_USER` | **읽기+쓰기** | 로그인 인증, 계정관리(생성/잠금/활성화/삭제), 비밀번호 변경 |
| `TB_ADMIN_USER_PWD_HISTORY` | **읽기+쓰기** | 비밀번호 재사용 금지 검증, 변경 이력 적재 |
| `TB_ADMIN_USER_LOG` | **쓰기 전용**(조회 API 없음) | 로그인/계정관리/비밀번호변경 감사 로그 적재 |

시퀀스: `SEQ_COLLECT_LOG`/`SEQ_COLLECT_HISTORY`/`SEQ_ANALYZE_RESULT`/`SEQ_ANALYZE_HISTORY`는 dashboard가 조회만 하므로 채번하지 않는다. dashboard가 직접 채번하는 시퀀스는 `SEQ_ADMIN_USER`, `SEQ_ADMIN_USER_PWD_HISTORY`, `SEQ_ADMIN_USER_LOG` 3개뿐이다(`init_dev.sql`에서 함께 생성).

### 외부 리소스

| 리소스 | 경로(설정값) | 용도 |
|--------|-------------|------|
| 수집 스케줄 conf | `precheck.collect-schedule-path` (`application.yml`에 절대경로로 하드코딩됨 — 개발자 로컬 경로 `C:\Users\20200161\...`) | `DashboardService.init()`에서 수집 대상 서버 수/스케줄 표시문자열 캐싱 |
| 분석 스케줄 conf | `precheck.analyze-schedule-path` (마찬가지로 절대경로 하드코딩) | 분석 대상 서버 수/스케줄 표시문자열 캐싱 |

<!-- TODO: 확인 필요 — application.yml의 collect-schedule-path/analyze-schedule-path가 특정 개발자 로컬 절대경로로 고정돼 있어, 다른 환경(다른 PC, 운영 서버)에서는 파일이 없어 스케줄 캐시가 0/빈 값으로 폴백될 수 있다. 배포 시 환경별 override 필요. -->

---

## 6. 설정 파일 분석

### `src/main/resources/application.yml` (공통, 모든 프로파일 공유)

| 항목 | 기본값 | 설명 |
|------|--------|------|
| `server.port` | `8080` | HTTP 포트 |
| `server.servlet.encoding.*` | UTF-8, force=true | 요청/응답 인코딩 강제 |
| `server.servlet.session.timeout` | `9h` | 세션 타임아웃 |
| `server.servlet.session.cookie.http-only` | `true` | JS에서 쿠키 접근 차단 |
| `server.servlet.session.cookie.secure` | `false` | HTTPS 전용 아님 (로컬/사내망 전제) <!-- TODO: 확인 필요, 운영 HTTPS 적용 시 true로 변경 필요할 수 있음 --> |
| `server.servlet.session.cookie.same-site` | `lax` | CSRF 완화 |
| `spring.profiles.active` | `test` | 기본 활성 프로파일 |
| `spring.thymeleaf.cache` | `false` | 템플릿 캐시 비활성 (개발 편의) |
| `spring.thymeleaf.prefix/suffix` | `classpath:/templates/` / `.html` | 뷰 리졸버 경로 |
| `mybatis.mapper-locations` | `classpath:mapper/*.xml` | 매퍼 XML 위치 |
| `mybatis.type-aliases-package` | `com.sks.precheck.dashboard.dto` | resultType 별칭 패키지 |
| `mybatis.configuration.map-underscore-to-camel-case` | `true` | 스네이크케이스 컬럼 ↔ 카멜케이스 필드 자동 매핑 |
| `mybatis.configuration.log-impl` | `StdOutImpl` | SQL 콘솔 로깅 (공통 설정, prod에서 override) |
| `logging.level.com.sks.precheck` / `org.mybatis` | `DEBUG` | 상세 로그 (prod 프로파일에 별도 override 없음 — 운영에도 DEBUG 유지됨) <!-- TODO: 확인 필요, 운영 로그 레벨 재검토 여지 --> |
| `precheck.refresh-interval-seconds` | `60` | 화면 자동 갱신 주기(초) |
| `precheck.collect-schedule-path` / `analyze-schedule-path` | 로컬 절대경로 | 수집/분석 스케줄 conf 위치 |
| `precheck.history-days` | `60` | 메인 대시보드 히스토리 그래프 조회 기간(일) |
| `precheck.info-data` | 21개 항목 | 주요 데이터 카드 목록 (name/server-id/log-id), 예: 주식종목수·파생종목수·NXT종목수·해외시세(미국/홍콩/상해/심천)·최대동시접속·UC 접속자수 등 |

### `src/main/resources/application-test.yml` (로컬/테스트, 기본 프로파일)

| 항목 | 기본값 | 설명 |
|------|--------|------|
| `spring.datasource.driver-class-name` | `org.postgresql.Driver` | PostgreSQL JDBC 드라이버 |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/postgres` | 로컬 PostgreSQL |
| `spring.datasource.username` | `postgres` | - |
| `spring.datasource.password` | `[REDACTED]` | 평문 저장돼 있음 (로컬 전용 파일이라는 전제) |
| `spring.datasource.hikari.maximum-pool-size` | `5` | 최대 커넥션 |
| `spring.datasource.hikari.minimum-idle` | `2` | 최소 유지 커넥션 |
| `spring.datasource.hikari.connection-timeout` | `30000`(ms) | 커넥션 획득 대기 |
| `spring.datasource.hikari.idle-timeout` | `600000`(ms) | 유휴 커넥션 유지 |
| `spring.datasource.hikari.max-lifetime` | `1800000`(ms) | 커넥션 최대 수명 |
| `spring.datasource.hikari.pool-name` | `PreCheckPool-Test` | 풀 식별명 |

### `src/main/resources/application-prod.yml` (운영)

| 항목 | 기본값 | 설명 |
|------|--------|------|
| `spring.datasource.url` | `jdbc:Altibase://192.168.x.x:20300/precheck` | 운영 Altibase (IP가 파일 자체에 `x.x`로 마스킹돼 있어 실제 배포 시 치환 필요로 보임) <!-- TODO: 확인 필요 --> |
| `spring.datasource.username` | `precheck` | - |
| `spring.datasource.password` | `[REDACTED]` (파일에 `(운영 비밀번호)` 플레이스홀더) | 실제 배포 시 별도 주입 필요 |
| `spring.datasource.driver-class-name` | `Altibase.jdbc.driver.AltibaseDriver` | Altibase JDBC 드라이버 (build.gradle 의존성에는 미포함 — 운영 클래스패스에 별도 추가 필요로 보임) <!-- TODO: 확인 필요 --> |
| `spring.datasource.hikari.maximum-pool-size` | `10` | 최대 커넥션 |
| `spring.datasource.hikari.pool-name` | `PreCheckPool-Prod` | 풀 식별명 |
| `mybatis.configuration.log-impl` | `NoLoggingImpl` | 운영 SQL 로그 무음 처리 (test의 `StdOutImpl` override) |

### 프로파일별 차이 요약

| 구분 | test(기본) | prod |
|------|-----------|------|
| DB | PostgreSQL localhost | Altibase 사내망 |
| MyBatis SQL 로그 | 콘솔 출력(`StdOutImpl`) | 무음(`NoLoggingImpl`) |
| HikariCP 풀 크기 | 5 | 10 |
| 매퍼 XML 분기 | `databaseId="postgresql"` 사용 | `databaseId="altibase"` 사용 (LIMIT 문법, 시퀀스 채번 방식 상이) |
