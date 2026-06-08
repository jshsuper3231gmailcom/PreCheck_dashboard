# PreCheck Dashboard — Trae 바이브 코딩 스크립트

---

## 📌 사용 방법

이 파일은 **4단계로 나눠서** Trae에 순서대로 입력해.
한 단계가 완료(파일 생성/컴파일 성공)되면 다음 단계로 넘어가.

---

---
---
# ═══════════════════════════════════════
# STEP 1 — 프로젝트 뼈대 + 공통 설정
# ═══════════════════════════════════════
---
---

```
아래 스펙으로 Spring Boot Dashboard 프로젝트를 세팅해줘.

## 프로젝트 기본 정보
- groupId    : com.sks.precheck
- artifactId : dashboard
- Java       : 17
- Spring Boot: 3.3.x
- Build      : Gradle (Groovy DSL)
- Packaging  : Jar

## 의존성 (build.gradle)
- spring-boot-starter-web
- spring-boot-starter-thymeleaf
- mybatis-spring-boot-starter:3.0.3
- postgresql (runtimeOnly)
- lombok (compileOnly + annotationProcessor)
- spring-boot-devtools (developmentOnly)

## 디렉토리 구조 생성
src/main/java/com/sks/precheck/dashboard/
├── controller/
├── service/
├── mapper/
└── dto/

src/main/resources/
├── mapper/          (MyBatis XML 위치)
├── static/
│   ├── css/
│   ├── js/
│   └── plugins/
├── templates/
│   └── dashboard/
├── application.yml
├── application-dev.yml
└── application-prod.yml

## application.yml (공통)
server:
  port: 8080
  servlet:
    encoding:
      charset: UTF-8
      enabled: true
      force: true

spring:
  profiles:
    active: dev
  thymeleaf:
    cache: false
    encoding: UTF-8
    prefix: classpath:/templates/
    suffix: .html
  messages:
    encoding: UTF-8

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.sks.precheck.dashboard.dto
  configuration:
    map-underscore-to-camel-case: true
    jdbc-type-for-null: NULL
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

logging:
  level:
    com.sks.precheck: DEBUG
    org.mybatis: DEBUG
    org.springframework.web: INFO

## application-dev.yml (개발 환경 - PostgreSQL)
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/precheck
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5
      minimum-idle: 2
      connection-timeout: 30000
      pool-name: PreCheckPool-Dev

## application-prod.yml (운영 환경 - Altibase, 나중에 채움)
# Altibase 드라이버는 수동 추가 필요 (libs/Altibase.jar)
spring:
  datasource:
    url: jdbc:Altibase://192.168.x.x:20300/precheck
    username: precheck
    password: (운영 비밀번호)
    driver-class-name: Altibase.jdbc.driver.AltibaseDriver
    hikari:
      maximum-pool-size: 10
      pool-name: PreCheckPool-Prod
mybatis:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl

## 동작 확인용 임시 파일 생성
- DashboardController.java: GET "/" → "dashboard/index" 반환
- templates/dashboard/index.html: "PreCheck Dashboard 기동 확인" 텍스트만 있는 최소 HTML
- DashboardApplication.java: @SpringBootApplication 메인 클래스

## 완료 기준
- ./gradlew bootRun 실행 시 오류 없이 기동
- http://localhost:8080 접속 시 "PreCheck Dashboard 기동 확인" 텍스트 출력
```

---
---
# ═══════════════════════════════════════
# STEP 2 — DB 테이블 + DTO + MyBatis 세팅
# ═══════════════════════════════════════
---
---

```
PreCheck Dashboard에서 사용할 DB 구조와 MyBatis 설정을 만들어줘.

## DB 테이블 정보 (조회 전용, INSERT/UPDATE 없음)

### TB_ANALYZE_RESULT (분석 결과 — 핵심 조회 테이블)
| 컬럼명              | 타입               | 설명                                          |
|---------------------|--------------------|-----------------------------------------------|
| ANALYZE_RESULT_ID   | NUMERIC(19,0) PK   | 분석 결과 고유 ID                              |
| COLLECT_LOG_ID      | NUMERIC(19,0)      | 원본 수집 로그 ID (TB_COLLECT_LOG 참조)        |
| SERVER_ID           | VARCHAR(100)       | 서버 구분명 (예: dlprem01-테스트개발)          |
| SERVER_IP           | VARCHAR(15)        | 서버 IP                                       |
| LOG_TYPE            | VARCHAR(10)        | 입력 타입: '문구','정보','날짜','수치','존재'  |
| LOG_ID              | VARCHAR(30)        | 로그 식별 코드 (예: DISK_HOME)                |
| LOG_TIMESTAMP       | TIMESTAMP          | 원본 로그 시각                                |
| LOG_CONTENT         | VARCHAR(4000)      | 원본 로그 내용                                |
| LOG_VALUE           | NUMERIC(18,6)      | 수치형 로그값 (수치 타입만 값 있음)           |
| ANALYZE_LEVEL       | VARCHAR(10)        | 분석 레벨: '정상','경고','에러','정보','미분석'|
| ANALYZE_MESSAGE     | VARCHAR(2000)      | 분석 결과 메시지                              |
| THRESHOLD_VALUE     | NUMERIC(18,6)      | 임계치 (수치형만)                             |
| THRESHOLD_OPERATOR  | VARCHAR(5)         | 연산자: '>','>=','<','<=','=' (수치형만)     |
| WARNING_RATIO       | NUMERIC(5,2)       | 경고 근접 비율 % (경고 레벨만)               |
| NOTIFY_YN           | CHAR(1)            | SMS 통보 여부: 'Y'/'N'                       |
| NOTIFY_AT           | TIMESTAMP          | 통보 완료 일시                                |
| ANALYZE_DATE        | VARCHAR(8)         | 분석 날짜 yyyyMMdd (핵심 필터)               |
| ANALYZE_DATETIME    | TIMESTAMP          | 분석 서버 INSERT 일시                         |
| COLLECT_DATE        | VARCHAR(8)         | 원본 수집 날짜                                |
| CREATED_AT          | TIMESTAMP          | 행 생성 일시                                  |

### TB_ANALYZE_HISTORY (분석 실행 이력)
| 컬럼명                | 타입              | 설명                                    |
|-----------------------|-------------------|-----------------------------------------|
| ANALYZE_HISTORY_ID    | NUMERIC(19,0) PK  | 이력 고유 ID                            |
| SERVER_ID             | VARCHAR(100)      | 서버 구분명                             |
| SOURCE_FILE_PATH      | VARCHAR(500)      | 분석 대상 파일 경로 (파일 단위 분석 시) |
| ANALYZE_STATUS        | VARCHAR(10)       | 'SUCCESS','FAIL','PARTIAL'             |
| LAST_ANALYZE_LOG_ID   | NUMERIC(19,0)     | 마지막으로 분석한 COLLECT_LOG_ID       |
| TOTAL_COUNT           | NUMERIC(10,0)     | 처리한 총 로그 건수                     |
| SUCCESS_COUNT         | NUMERIC(10,0)     | 분석 성공 건수                          |
| FAIL_COUNT            | NUMERIC(10,0)     | 분석 실패 건수                          |
| ERROR_COUNT           | NUMERIC(10,0)     | 에러 레벨 판정 건수                     |
| WARNING_COUNT         | NUMERIC(10,0)     | 경고 레벨 판정 건수                     |
| FAIL_REASON           | VARCHAR(1000)     | 실패 사유                               |
| ANALYZE_START_AT      | TIMESTAMP         | 분석 시작 일시                          |
| ANALYZE_END_AT        | TIMESTAMP         | 분석 종료 일시                          |
| ANALYZE_DATE          | VARCHAR(8)        | 분석 실행 날짜 yyyyMMdd                |
| CREATED_AT            | TIMESTAMP         | 행 생성 일시                            |
| UPDATED_AT            | TIMESTAMP         | 행 수정 일시                            |

### TB_COLLECT_HISTORY (수집 실행 이력)
| 컬럼명             | 타입              | 설명                                    |
|--------------------|-------------------|-----------------------------------------|
| COLLECT_HISTORY_ID | NUMERIC(19,0) PK  | 이력 고유 ID                            |
| SERVER_ID          | VARCHAR(100)      | 서버 구분명                             |
| SOURCE_FILE_PATH   | VARCHAR(500)      | 수집 대상 파일 경로                     |
| COLLECT_STATUS     | VARCHAR(10)       | 'SUCCESS','FAIL','SKIP'               |
| COLLECTED_COUNT    | NUMERIC(10,0)     | 수집 건수                               |
| RETRY_COUNT        | NUMERIC(5,0)      | 재시도 횟수                             |
| FAIL_REASON        | VARCHAR(1000)     | 실패 사유                               |
| COLLECT_START_AT   | TIMESTAMP         | 수집 시작 일시                          |
| COLLECT_END_AT     | TIMESTAMP         | 수집 종료 일시                          |
| COLLECT_DATE       | VARCHAR(8)        | 수집 날짜 yyyyMMdd                     |
| CREATED_AT         | TIMESTAMP         | 행 생성 일시                            |

### TB_COLLECT_LOG (수집 로그 — 원본 로그 모달 조회용)
| 컬럼명             | 타입              | 설명                    |
|--------------------|-------------------|-------------------------|
| COLLECT_LOG_ID     | NUMERIC(19,0) PK  | 수집 로그 고유 ID       |
| SERVER_ID          | VARCHAR(100)      | 서버 구분명             |
| SERVER_IP          | VARCHAR(15)       | 서버 IP                 |
| LOG_TYPE           | VARCHAR(10)       | 입력 타입               |
| LOG_ID             | VARCHAR(30)       | 로그 식별 코드          |
| LOG_TIMESTAMP      | TIMESTAMP         | 원본 로그 시각          |
| LOG_CONTENT        | VARCHAR(4000)     | 원본 로그 내용          |
| LOG_VALUE          | NUMERIC(18,6)     | 수치형 로그값           |
| RAW_LOG            | VARCHAR(4000)     | 원본 로그 전문          |
| SOURCE_FILE_PATH   | VARCHAR(500)      | 수집 파일 경로          |
| COLLECT_DATETIME   | TIMESTAMP         | 수집 일시               |
| COLLECT_DATE       | VARCHAR(8)        | 수집 날짜 yyyyMMdd     |
| CREATED_AT         | TIMESTAMP         | 행 생성 일시            |

## 만들어야 할 것

### 1. DTO 클래스 (src/main/java/com/sks/precheck/dashboard/dto/)
- AnalyzeResultDto.java  — TB_ANALYZE_RESULT 매핑, @Data Lombok 사용
- AnalyzeHistoryDto.java — TB_ANALYZE_HISTORY 매핑
- CollectHistoryDto.java — TB_COLLECT_HISTORY 매핑
- CollectLogDto.java     — TB_COLLECT_LOG 매핑 (원본 로그 모달용)
- SummaryDto.java        — 요약 카드용 집계 결과 DTO
  - int errorCnt, warnCnt, normalCnt, infoCnt, unknownCnt
  - double errorRatio, warnRatio, normalRatio, infoRatio, unknownRatio
  - int collectSuccess, collectTotal, collectFail, collectSkip
  - int analyzeSuccess, analyzeTotal, analyzeFail
  - double collectRatio, analyzeRatio

### 2. MyBatis Mapper 인터페이스 (src/main/java/com/sks/precheck/dashboard/mapper/)
- DashboardMapper.java
  @Mapper 어노테이션 적용, 메서드 시그니처만 선언:
  - SummaryDto selectSummary(@Param("today") String today)
  - List<AnalyzeResultDto> selectErrorWarningList(...)
  - List<AnalyzeResultDto> selectNormalInfoList(...)
  - int countErrorWarning(...)
  - int countNormalInfo(...)
  - List<Map<String,Object>> selectServerList(@Param("today") String today)
  - List<AnalyzeResultDto> selectHistoryData(...)
  - List<Map<String,Object>> selectResourceData(@Param("today") String today)
  - AnalyzeResultDto selectInfoData(@Param("today") String today, @Param("serverId") String serverId, @Param("logId") String logId)
  - CollectLogDto selectRawLog(@Param("collectLogId") Long collectLogId)

### 3. MyBatis XML (src/main/resources/mapper/DashboardMapper.xml)
위 Mapper 메서드에 대응하는 SQL 작성.
아래 규칙 준수:
- ANALYZE_DATE, COLLECT_DATE 조건은 VARCHAR(8) 문자열 비교
- 페이징: Altibase = LIMIT offset, size / PostgreSQL = LIMIT size OFFSET offset
  → MyBatis XML에서 databaseId 또는 주석으로 양쪽 버전 모두 작성
- map-underscore-to-camel-case=true 이므로 resultType에서 자동 매핑됨
- SummaryDto 조회는 서브쿼리 방식으로 비율 계산 (윈도우 함수 미사용)

### 4. PostgreSQL DDL 스크립트 생성 (src/main/resources/sql/init_dev.sql)
위 4개 테이블 + SEQUENCE 생성 DDL
테스트 데이터 INSERT 10~20건 포함 (오늘 날짜 기준)

## 완료 기준
- DashboardMapper의 모든 메서드가 XML과 1:1 매핑되어 컴파일 오류 없음
- init_dev.sql로 PostgreSQL 스키마 생성 가능
```

---
---
# ═══════════════════════════════════════
# STEP 3 — Service + Controller + API
# ═══════════════════════════════════════
---
---

```
PreCheck Dashboard의 Service, Controller, AJAX API를 구현해줘.

## DashboardService.java
(src/main/java/com/sks/precheck/dashboard/service/)

### 역할
- DashboardMapper를 주입받아 비즈니스 로직 처리
- 오늘 날짜는 LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
- conf 파일 파싱으로 전체 서버수 계산 (아래 설명 참고)

### conf 파일 파싱 규칙
application.yml에 아래 설정 추가:
precheck:
  collect-schedule-path: ${user.home}/cfg/PreCheck_CollectLogs_Schedule.conf
  analyze-schedule-path: ${user.home}/cfg/PreCheck_AnalyzeLogs_Schedule.conf

파싱 로직:
- 파일을 라인 단위로 읽음
- # 으로 시작하는 라인 제외
- 빈 라인 제외
- 형식: [서버구분][나머지...] 에서 첫 번째 [] 안의 값이 서버구분
- 서버구분 DISTINCT count = 전체 서버수
- @PostConstruct로 기동 시 1회 파싱 후 int 값으로 캐싱
- 파일이 없으면 0으로 처리 (예외 발생 금지)

### 구현할 메서드
- SummaryDto getSummary()
  - DB에서 레벨별 건수/비율, 수집완료/실패/SKIP 건수 조회
  - conf 캐싱값으로 collectTotal, analyzeTotal 채움
  - collectRatio = collectSuccess * 100.0 / collectTotal (0이면 0)

- List<AnalyzeResultDto> getErrorWarningList(String serverId, int page)
  - page 기본값 1, pageSize 10
  - offset = (page-1) * 10

- int getErrorWarningCount(String serverId)

- List<AnalyzeResultDto> getNormalInfoList(String serverId, int page)

- int getNormalInfoCount(String serverId)

- List<Map<String,Object>> getServerList()

- List<Map<String,Object>> getHistoryData(String groupType)
  - groupType="stock": MBSOSI_COUNT, MBFOSI_COUNT, MBCOSI_COUNT, MBJISU_COUNT, NXT_COUNT, OPT_MAX_COUNT
  - groupType="conn": MAX_CONN_PREV, HTS_MAX_CONN, MTS_MAX_CONN
  - 위 LOG_ID 목록과 서버구분은 InfoDataConfig 클래스에서 관리 (아래 참고)
  - 7일치 데이터를 각 LOG_ID별로 Map에 담아 반환
    반환 형식: [{logId: "MBSOSI_COUNT", data: [{logValue, logTimestamp}, ...]}, ...]

- List<Map<String,Object>> getResourceData()
  - TB_ANALYZE_HISTORY에서 오늘 DISTINCT SERVER_ID 조회
  - 각 서버별 DISK_HOME 최신값 조회 (LOG_TYPE='수치')
  - 반환: [{serverId, logValue, analyzeLevel, thresholdValue, logTimestamp}, ...]

- Map<String,Object> getAllInfoData()
  - InfoDataConfig의 13개 항목을 각각 selectInfoData로 조회
  - 반환: {"MBSOSI_COUNT": {logValue, logTimestamp}, ...}

- CollectLogDto getRawLog(Long collectLogId)

### InfoDataConfig.java
(src/main/java/com/sks/precheck/dashboard/config/)

application.yml에서 서버+LOG_ID 매핑을 주입받는 설정 클래스:

precheck:
  info-data:
    - name: 주식종목수
      serverId: dlprem01-테스트개발
      logId: MBSOSI_COUNT
    - name: 파생종목수
      serverId: dlprem01-테스트개발
      logId: MBFOSI_COUNT
    - name: 상품종목수
      serverId: dlprem01-테스트개발
      logId: MBCOSI_COUNT
    - name: 업종종목수
      serverId: dlprem01-테스트개발
      logId: MBJISU_COUNT
    - name: NXT종목수
      serverId: dlprem01-테스트개발
      logId: NXT_COUNT
    - name: 옵션결제월최대수
      serverId: dlprem01-테스트개발
      logId: OPT_MAX_COUNT
    - name: 서버자동주문계좌수
      serverId: axistuja-자동주문
      logId: AUTO_ORDER_ACNT
    - name: 시세포착1등록수
      serverId: pqgetap1-시세포착1
      logId: CAP_REG_COUNT
    - name: 시세포착2등록수
      serverId: pqgetap2-시세포착2
      logId: CAP2_REG_COUNT
    - name: 주파수클럽사용수
      serverId: pjpsap01-주파수클럽
      logId: FREQ_CLUB_COUNT
    - name: 전일최대동시접속
      serverId: pmaster2-마스터
      logId: MAX_CONN_PREV
    - name: HTS최대동시접속
      serverId: pmaster2-마스터
      logId: HTS_MAX_CONN
    - name: MTS최대동시접속
      serverId: pmaster2-마스터
      logId: MTS_MAX_CONN

@ConfigurationProperties(prefix = "precheck") + List<InfoDataItem> infoData 로 매핑

## DashboardController.java
(src/main/java/com/sks/precheck/dashboard/controller/)

### 페이지 엔드포인트
GET /dashboard → templates/dashboard/index.html 반환
  model에 초기 데이터 없이 렌더링 (AJAX로 모두 갱신)

### AJAX API 엔드포인트 (@ResponseBody JSON 반환)
GET  /dashboard/api/summary       → getSummary()
GET  /dashboard/api/info-data     → getAllInfoData()
GET  /dashboard/api/error-list    → ?serverId=&page=
GET  /dashboard/api/normal-list   → ?serverId=&page=
GET  /dashboard/api/history       → ?groupType=stock|conn
GET  /dashboard/api/resource      → getResourceData()
GET  /dashboard/api/server-list   → getServerList()
GET  /dashboard/api/raw-log/{id}  → getRawLog(id)

### 응답 공통 래퍼 ApiResponse<T>
{ success: true/false, data: T, message: "" }
오류 시 success=false, message에 오류 내용

## 완료 기준
- http://localhost:8080/dashboard/api/summary 호출 시 JSON 응답 반환
- 컴파일 오류 없음
```

---
---
# ═══════════════════════════════════════
# STEP 4 — AdminLTE HTML 화면 구현
# ═══════════════════════════════════════
---
---

```
PreCheck Dashboard HTML 화면을 AdminLTE 4.x (Bootstrap 5) 기반으로 구현해줘.

## 사전 준비
AdminLTE 4.x (Bootstrap 5) 정적 파일을 아래 경로에 복사했다고 가정:
- src/main/resources/static/css/adminlte.min.css
- src/main/resources/static/js/adminlte.min.js
- src/main/resources/static/plugins/bootstrap/bootstrap.bundle.min.js
- src/main/resources/static/plugins/bootstrap-icons/bootstrap-icons.css
- Chart.js는 CDN 사용: https://cdn.jsdelivr.net/npm/chart.js

## 파일: templates/dashboard/index.html

### 전체 레이아웃 (순서대로 구현)

─────────────────────────────────────
[1] 상단 NavBar
─────────────────────────────────────
- 좌측: "채널 서버 점검 Dashboard" (bi-server 아이콘)
- 우측:
  - 현재 날짜+시간 id="currentTime" (YYYY/MM/DD HH:mm:ss, 1초 갱신)
  - 마지막 갱신 id="lastUpdated" (초기값: "-")
  - 자동갱신 카운트다운 id="countdown" (초기값: "05:00", MM:SS 형식)
- AdminLTE 4.x app-header 클래스 사용

─────────────────────────────────────
[2] 요약 현황 — 가로 스트립 카드 1개
─────────────────────────────────────
Bootstrap 5 Card 1개, card-body p-0:

상단 (d-flex, 5등분, border-end 구분선):
- 에러    : badge text-bg-danger   + 큰 숫자 text-danger  + % (id: errCnt, errRatio)
- 경고    : badge text-bg-warning  + 숫자 color:#856404   + % (id: warnCnt, warnRatio)
- 정상    : badge text-bg-success  + 숫자 text-success    + % (id: normCnt, normRatio)
- 정보    : badge text-bg-info     + 숫자 color:#055160   + % (id: infoCnt, infoRatio)
- 미분석  : badge text-bg-secondary + 숫자 text-secondary + % (id: unknCnt, unknRatio)
  → 미분석 1건 이상 시 하단에 text-danger "정책 파일 업데이트 필요" 표시

hr.my-0 구분선

하단 (d-flex, 5등분, border-end 구분선):
- 수집 현황 : bi-download text-info    + 완료N/전체N + progress bar bg-info height:3px
- 분석 현황 : bi-bar-chart-line text-success + 완료N/전체N + progress bar bg-success
- 수집실패FAIL : bi-exclamation-circle text-danger + N건 (0이면 bi-check-circle "이상 없음")
- 수집제외SKIP : bi-slash-circle text-secondary    + N건 (0이면 bi-check-circle "이상 없음")
- 분석실패      : bi-exclamation-triangle           + N건 (0이면 bi-check-circle "이상 없음")

id: colSuccess, colTotal, colRatio, anSuccess, anTotal, anRatio,
    failCnt, skipCnt, anFailCnt

─────────────────────────────────────
[3] 정보성 중요 데이터 — 카드 3개 (col-lg-4)
─────────────────────────────────────
각 카드: card h-100, card-header, card-body p-0, list-group list-group-flush

카드1 "종목 현황":
● 주식 종목수       (id: v-MBSOSI_COUNT, t-MBSOSI_COUNT)
● 파생 종목수       (id: v-MBFOSI_COUNT, t-MBFOSI_COUNT)
● 상품 종목수       (id: v-MBCOSI_COUNT, t-MBCOSI_COUNT)
● 업종 종목수       (id: v-MBJISU_COUNT, t-MBJISU_COUNT)
● NXT 종목수        (id: v-NXT_COUNT,    t-NXT_COUNT)
● 옵션결제월 최대수 (id: v-OPT_MAX_COUNT, t-OPT_MAX_COUNT)

카드2 "서비스 현황":
● 서버자동주문 계좌수  (id: v-AUTO_ORDER_ACNT, t-AUTO_ORDER_ACNT)
● 시세포착1 등록수    (id: v-CAP_REG_COUNT, t-CAP_REG_COUNT)
● 시세포착2 등록수    (id: v-CAP2_REG_COUNT, t-CAP2_REG_COUNT)
● 주파수클럽 사용수   (id: v-FREQ_CLUB_COUNT, t-FREQ_CLUB_COUNT)

카드3 "접속자 현황" (header에 "전일 기준" 소문자 표시):
● 전일 최대동시접속  (id: v-MAX_CONN_PREV, t-MAX_CONN_PREV)
● HTS 최대동시접속  (id: v-HTS_MAX_CONN, t-HTS_MAX_CONN)
● MTS 최대동시접속  (id: v-MTS_MAX_CONN, t-MTS_MAX_CONN)

각 행: 색상 dot(8px) + 항목명(text-body-secondary) + 수치(fw-bold) + 시각(text-body-secondary)
값 없으면 "-" 표시

─────────────────────────────────────
[4] 에러/경고 상세 목록 — 전체 너비
─────────────────────────────────────
card > card-header:
- 좌측: 서버 선택 드롭다운 id="serverFilter" (전체 + 서버목록, AJAX 후 갱신)
- 좌측: 탭 버튼 [에러/경고] [정상/정보/미분석] (id: tabError, tabNormal)
- 우측: "총 N건 (M/P 페이지)" id="pageInfo"

table thead: 시각 | 서버 | LOG_ID | 타입 | 레벨 | 분석 메시지 | 임계치 | 원본

tbody id="resultTbody":
- 에러/경고 탭: ANALYZE_LEVEL별 배경색 table-danger/table-warning
- 행 클릭: 해당 행 다음에 accordion 상세 토글
  수치형: 수집값 / 임계치 / 연산자 / 임계치대비% (경고만)
  문구형: 로그 내용 / 매칭 키워드
  존재형: 로그 내용 (항상 에러)
  날짜형: 로그 내용 / 날짜 비교 결과
  정보형: 로그 내용
  미분석: 경고 메시지
- 레벨 배지 옆 아이콘: N=bi-bell-fill text-danger / Y=bi-check-circle text-body-secondary (에러/경고 탭만)
- 배지: text-bg-* 클래스 사용 (Bootstrap 5)
- [보기] 버튼: 모달 팝업으로 원본 로그 표시

페이지네이션 id="pagination": « 이전 1 2 3 ... 다음 »

원본 로그 모달 id="rawLogModal":
- 수집 로그 ID / 수집 일시 / 로그 원본 시각 / 수집 파일 경로 / 원본 로그 전문(pre 태그)

─────────────────────────────────────
[6] 서버별 리소스 현황 — 4번 바로 아래
─────────────────────────────────────
id="resourceSection"
card > card-body
서버당 도넛차트 1개씩 동적 생성 (Chart.js Doughnut)
- 초록(정상) / 노랑(경고) / 빨강(에러): ANALYZE_LEVEL 기준
- 중앙 텍스트: LOG_VALUE%
- 하단: DISK_HOME + LOG_VALUE% + 임계치 + LOG_TIMESTAMP
- 데이터 없는 서버: "분석없음" 회색 점선 원

─────────────────────────────────────
[5] 히스토리 그래프 — 전체 너비
─────────────────────────────────────
card > card-header:
- 탭: [종목수 현황 id="tabStock"] [접속자수 현황 id="tabConn"]

canvas id="historyChart"
Chart.js Line Chart:
- X축: LOG_TIMESTAMP (MM/DD HH:mm 형식)
- Y축: LOG_VALUE
- 종목수 탭: MBSOSI_COUNT(#4472C4), MBFOSI_COUNT(#ED7D31), MBCOSI_COUNT(#A9D18E),
             MBJISU_COUNT(#FF0000), NXT_COUNT(#7030A0), OPT_MAX_COUNT(#00B0F0)
- 접속자수 탭: MAX_CONN_PREV(#4472C4), HTS_MAX_CONN(#ED7D31), MTS_MAX_CONN(#A9D18E)
- 탭 전환 시 Chart.js 데이터 교체 (destroy 후 재생성)

─────────────────────────────────────
[7] 수집대상 서버 리스트 — 전체 너비
─────────────────────────────────────
card > list-group id="serverList"
각 서버 항목:
- 아이콘: bi-circle-fill(정상/경고/에러) / bi-circle(수집미완료)
- 색상: text-success / text-warning / text-danger / text-body-secondary
- 서버명 + 수집 시각 + 분석 시각 + 에러 건수(text-bg-danger) + 경고 건수(text-bg-warning)

─────────────────────────────────────
## JavaScript 자동 갱신 (파일 하단 <script>)
─────────────────────────────────────
전역 변수:
- let currentPage = 1
- let currentTab = 'error'  ('error' | 'normal')
- let currentServerId = ''
- let historyChart = null
- let currentGroupType = 'stock'
- let countdown = 300

함수:
- startClock()       → 1초마다 현재 시각 갱신
- startCountdown()   → 1초마다 countdown-- → 00:00 되면 refreshAll() + 리셋
- refreshAll()       → 모든 AJAX API 동시 호출 후 DOM 업데이트
  → fetch('/dashboard/api/summary').then(updateSummary)
  → fetch('/dashboard/api/info-data').then(updateInfoData)
  → fetch('/dashboard/api/error-list?serverId=..&page='+currentPage).then(updateTable)
  → fetch('/dashboard/api/resource').then(updateResource)
  → fetch('/dashboard/api/history?groupType='+currentGroupType).then(updateHistory)
  → fetch('/dashboard/api/server-list').then(updateServerList)
  → 갱신 후 id="lastUpdated" 현재 시각으로 업데이트

- updateSummary(data)     → id 기반으로 DOM 업데이트, 미분석 경고 표시
- updateInfoData(data)    → v-LOG_ID / t-LOG_ID id 기반 업데이트, 시각은 오늘이면 HH:mm 아니면 MM/DD HH:mm
- updateTable(data)       → tbody 재생성, accordion 이벤트 재바인딩
- updateResource(data)    → 도넛차트 동적 생성 (서버별 canvas 생성)
- updateHistory(data)     → historyChart 데이터 교체
- updateServerList(data)  → 서버 리스트 재생성

페이지 버튼 클릭:
- currentPage 변경 후 error-list 또는 normal-list 재호출
- 자동갱신 시에는 currentPage 유지

탭 전환:
- currentTab 변경 + currentPage = 1 + 테이블 재조회
- 히스토리 탭 전환: currentGroupType 변경 + 히스토리 재조회

서버 필터 변경:
- currentServerId 변경 + currentPage = 1 + 테이블 재조회

페이지 로드 시:
- startClock()
- startCountdown()
- refreshAll()  ← 즉시 1회 실행

## 완료 기준
- http://localhost:8080/dashboard 접속 시 전체 화면 렌더링
- 5분 카운트다운 정상 동작 (05:00 → 00:00)
- 에러/경고 탭 전환 + 페이지네이션 + 현재 페이지 유지 정상 동작
- 히스토리 탭 전환 시 Chart.js 데이터 교체 정상 동작
- 원본 로그 [보기] 버튼 클릭 시 모달 팝업 정상 표시
```

---

## 📌 전체 완료 후 체크리스트

```
□ STEP 1: ./gradlew bootRun 기동 확인
□ STEP 2: init_dev.sql 실행 + Mapper 컴파일 확인
□ STEP 3: /dashboard/api/summary JSON 응답 확인
□ STEP 4: /dashboard 전체 화면 렌더링 확인
□ 5분 카운트다운 → 자동 갱신 동작 확인
□ 에러/경고 탭 + 페이지네이션 + 현재 페이지 유지 확인
□ 히스토리 그래프 탭 전환 확인
□ 원본 로그 모달 확인
□ application-prod.yml Altibase 연결 설정 (운영 투입 전)
```
