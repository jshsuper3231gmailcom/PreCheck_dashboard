# CLAUDE.md

## 역할

관리자 전용 웹 대시보드. 포트 8080, Spring Security 로그인. `TB_ANALYZE_RESULT` / `TB_COLLECT_HISTORY` 등 조회 전용 — DB 쓰기 없음. 상세는 `FLOW.md`.

---

## 명령어

```bash
# 기본 프로파일: test (PostgreSQL localhost, port 8080)
gradlew.bat bootRun

# 빌드 / 테스트
gradlew.bat build
gradlew.bat test
```

접속: `http://localhost:8080`

---

## DB 초기화 (최초 1회)

```
src/main/resources/sql/init_dev.sql
```

테이블 DDL + 시퀀스 + 초기 admin 계정 포함. 로컬 PostgreSQL에 적용 후 기동.

---

## 핵심 gotcha

- **SSR + JS 폴링 혼합 구조**: Thymeleaf가 초기 페이지 렌더링, 브라우저가 `/dashboard/api/*` 엔드포인트를 60초 간격 폴링
- **`precheck.info-data`** (application.yml) 목록이 대시보드 카드 항목 결정 — 변경 후 재기동 필요
- **페이징 쿼리 두 벌**: `DashboardMapper.xml`의 에러/정상 목록 쿼리가 `databaseId="postgresql"`과 `databaseId="altibase"` 별도 존재 — `MyBatisConfig`가 드라이버 클래스명으로 분기
- **`PasswordExpiryInterceptor`**: 매 요청 시 90일 만료 체크, 만료 시 `/password/change` 강제 리다이렉트. D-7부터 헤더 경고 배너
- **로그인 잠금**: 5회 실패 → `LOCKED`, 5분 경과 후 다음 로그인 시도 시 자동 해제
- **SUPER_ADMIN**: 비밀번호 만료 정책 미적용 (`PASSWORD_EXPIRE_YN='N'`), 비활성화·삭제 불가
- **스케줄 파일 파싱**: 기동 시 1회 캐싱 (`DashboardService.init()`). 스케줄 파일 변경 시 재기동 필요

## 비밀번호 복잡도 정책

10자 이상, 대/소문자·숫자·특수문자 중 3종 이상, 연속/반복 3자 금지, 로그인 ID 포함 금지. 최근 2건(현재 + 이력 1건) 재사용 금지.

## 스택 참고

- Java 17, Spring Boot 3.3.12, MyBatis 3.0.3, Thymeleaf + AdminLTE
- Spring Security 6, BCrypt
- PostgreSQL (test) / Altibase (prod)
