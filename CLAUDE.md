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

## Spring DevTools 주의 (템플릿 변경 시)

DevTools가 `src/main/resources/` 변경을 Thymeleaf에 **반영하지 않음**.
템플릿(`.html`) 수정 후 선택지:
- `gradlew.bat clean bootRun` — 전체 재빌드 (느림, 확실)
- `Copy-Item src\main\resources\templates\... build\resources\main\templates\...` — 빠른 수동 복사

Java 파일은 DevTools가 자동 재시작. Mapper XML은 수동 복사 후 DevTools가 자동 감지.

**JAR 실행 시 stale 주의**: `java -jar build/libs/*.jar` 는 빌드 시점 리소스를 번들. `src/` 의 html/css/js 를 고쳐도 `gradlew.bat build` 재빌드 없이는 반영 안 됨. "화면 변경이 안 보임" 1순위 의심 대상 — `(Get-Item jar).LastWriteTime` 으로 JAR 신선도부터 확인.

**IDE(Eclipse/STS) Run 시 `bin/main` stale 주의**: IDE로 직접 Run할 땐 `build/resources/main`이 아니라 `bin/main`에서 정적 리소스가 서빙됨. incremental build가 새로 추가한 정적 파일(js/css)을 `bin/main`에 복사 안 하는 경우 있음 — 파일이 통째로 빠져 404, 스크립트 미로드로 이어짐(예: 다크테마 토글 전혀 무반응). 의심되면 `find . -iname "<파일명>*"`로 `bin/main` vs `src/main/resources` vs `build/resources/main` 세 곳 존재·수정시각 비교. 해결은 IDE Project Refresh/Clean, 급하면 파일 수동 복사.

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

## 참조파일 스코프
- 현재 프로잭트 디렉토리 하위의 디렉토리 내용만 참고함
