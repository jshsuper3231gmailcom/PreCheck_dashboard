# 릴리스 노트 — 접속자 위젯 기준일을 수집일 → 로그 날짜로 전환

- **작성일**: 2026-07-31
- **대상 모듈**: `dashboard` 단독 재빌드 (collect / analyze / notify **소스 무변경**)
- **영향 화면**:
  - 대시보드 하단 **"접속자 현황"** 카드 — 좌측 3수치 + 우측 UC 60분 차트
  - 대시보드 하단 **History 카드 → "접속자수 현황" 탭**
  - **`/dashboard/history` 페이지 → "접속자 현황"** 차트
- **DB 스키마 변경**: 없음 (DDL·시퀀스·인덱스 변경 없음)
- **설정 파일 변경**: 없음 (`application*.yml`, conf 파일 전부 무변경)
- **재기동 필요**: `dashboard` 만
- **선행 조건**: 없음 (기존 데이터 그대로 사용, 마이그레이션 불필요)

---

## 1. 변경 배경

접속자 관련 조회가 전부 `TB_ANALYZE_RESULT.ANALYZE_DATE`(수집·분석일) 기준이었다. 접속자 로그 파일은 하루 늦게 수집되는 경우가 있어, **어제 로그를 오늘 수집·분석하면 `ANALYZE_DATE`가 오늘이 되어 어제 값이 오늘 지점에 찍혔다.**

화면 기준을 **로그 날짜(`LOG_TIMESTAMP`)** 로 바꾼다.

### 1-1. 기준일 정의 — 묶음별로 분리

최신 `LOG_TIMESTAMP`의 날짜를 기준일로 삼되, **생성 주기가 다른 두 묶음을 각각 계산**한다.

| 기준일 | 대상 LOG_ID | 생성 주기 | 사용처 |
|--------|-------------|-----------|--------|
| `D_card` | `MAX_CONN_PREV`, `HTS_MAX_CONN`, `MTS_MAX_CONN` | 하루 1건 | 카드 3수치, History 접속자 탭(일별·월별) |
| `D_uc` | `UC_TOTAL_COUNT`, `UC_HTS_COUNT`, `UC_MTS_COUNT` | 분 단위 상시 | UC 60분 차트 |

- 오늘 로그가 없으면 기준일은 **자동으로 어제(또는 그 이전)** 가 된다 — 별도 fallback 로직 없음
- 새 로그가 들어오는 즉시 기준일이 그날로 넘어간다
- 해당 묶음의 분석 결과가 아예 없으면 기준일은 null이고, 화면은 기존과 동일한 빈 상태(`-`)를 유지한다

**두 묶음을 하나로 합치면 안 되는 이유** — 운영 스케줄 기준:

```
collect: [주기|*|080000|1|235959]   ← 08:00~23:59, 1분 간격, 당일 날짜 파일
```

카드 로그가 23:10에 생성되는 환경에서 기준일을 6종 공유로 두면, 08:00부터 들어오는 UC 실시간 로그가 기준일을 오늘로 끌어올린다. 그러면 아직 오늘치가 없는 카드 3수치가 **08:00~23:10 내내 `-` 로 비게 된다.** 묶음별로 나누면 그 시간대에 `D_card` 가 어제로 유지되어 어제 값이 계속 보인다.

**동작 예시** (카드 로그가 매일 23:10 생성):

| 시점 | `D_card` | 카드 표시 | `D_uc` | UC 차트 |
|---|---|---|---|---|
| 7/31 00:00 ~ 23:10 | 7/30 | 7/30 23:10 값 | 7/31 | 7/31 실시간 |
| 7/31 23:10 이후 | 7/31 | 7/31 23:10 값 | 7/31 | 7/31 실시간 |

이 구간에서 카드 날짜와 UC 배지 날짜가 갈릴 수 있는데, 데이터 성격상 정상이며 배지로 구분한다(1-2 참조).

### 1-2. 확정된 설계 판단

| 항목 | 결정 | 근거 |
|------|------|------|
| 적용 범위 | **접속자 위젯만.** 종목/해외/서비스 차트는 기존 `ANALYZE_DATE` 유지 | 기존 쿼리(`selectHistoryData`, `selectInfoData`)를 건드리지 않고 접속자 전용 쿼리를 신설해, 다른 화면에 회귀가 생길 여지를 없앰 |
| 인덱스 | **생성하지 않음** (5-2 참조) | 정확성 영향 없는 성능 사안. `TB_ANALYZE_RESULT`는 현재도 PK 외 인덱스가 없어 기존과 동일 조건. 단, 쿼리는 인덱스를 탈 수 있는 형태로 작성 |
| 조회 조건 형태 | `LOG_TIMESTAMP >= from AND < to` **반개구간** | `TO_CHAR(LOG_TIMESTAMP,'YYYYMMDD') = ?` 처럼 컬럼에 함수를 씌우면 나중에 인덱스를 만들어도 타지 못함 |
| 기간 기준점 | History 기간(일별 `history-days`, 월별 11개월)의 **기준점도 오늘이 아니라 D** | 끝점만 D로 바꾸면 D가 과거일 때 조회 구간이 어긋남 |
| "60분"의 의미 | 기존과 동일 — **D 구간 안의 최신 `LOG_TIMESTAMP` 기준 직전 60분** (현재 시각 기준 아님) | 기존 동작 유지. 다만 날짜 경계를 넘어 섞이지 않도록 D 구간 안에서 계산하도록 제한 |
| 날짜 표기 | 카드 3수치 + UC 차트 **배지 4개에 `MM/DD HH:mm`** (예: `07/31 07:06`) | 기준일이 오늘이 아닐 수 있고 카드와 UC의 날짜가 서로 다를 수도 있어, 각 위젯이 어느 시점 데이터인지 화면에서 바로 드러나야 함. 기존 UC 배지 문구는 `TODAY (60분)` 고정이었음 |
| 카드 시각 표기 위치 | 수치 옆 `t-*` 자리를 비우고 **배지로 일원화** | 배지가 `MM/DD HH:mm` 을 전부 보여주므로 같은 정보가 두 곳에 중복됨. 접속자 카드 3종만 해당하고, 다른 주요 데이터 카드의 `t-*` 는 기존 동작 유지 |

---

## 2. 수정 파일 위치

총 4개 파일(신규 0 / 수정 4). **`dashboard` 모듈만 재빌드하면 된다.** 삭제·이름변경 없음.

```
precheck_collect/dashboard/
└── src/main/
    ├── java/com/sks/precheck/dashboard/
    │   ├── mapper/DashboardMapper.java          [수정] 메서드 3개 추가 + selectUcSparkData 시그니처 변경
    │   └── service/DashboardService.java        [수정] 상수 5 + 헬퍼 5 + 조회 메서드 4개 분기 추가
    └── resources/
        ├── mapper/DashboardMapper.xml           [수정] 신규 select 3개 + selectUcSparkData 2벌 수정
        └── templates/dashboard/index.html       [수정] 배지 4개 + JS 함수 2개 추가
```

diffstat: `4 files changed, 378 insertions(+), 50 deletions(-)`

### 2-1. 이관 대상 파일 경로 (복사 체크리스트)

`dashboard` 모듈 루트 기준 상대경로다. **이 4개만 운영 소스에 덮어쓰면 된다.**

| # | 모듈 루트 기준 경로 | 종류 |
|---|---------------------|------|
| 1 | `src/main/java/com/sks/precheck/dashboard/mapper/DashboardMapper.java` | 수정 |
| 2 | `src/main/java/com/sks/precheck/dashboard/service/DashboardService.java` | 수정 |
| 3 | `src/main/resources/mapper/DashboardMapper.xml` | 수정 |
| 4 | `src/main/resources/templates/dashboard/index.html` | 수정 |
| 5 | `docs/RELEASE_20260731_conn_widgets_log_date_basis.md` | 신규(문서, 이관 선택) |

프로젝트 루트(`Precheck_SKSCh1`) 기준 전체 경로:

```
precheck_collect/dashboard/src/main/java/com/sks/precheck/dashboard/mapper/DashboardMapper.java
precheck_collect/dashboard/src/main/java/com/sks/precheck/dashboard/service/DashboardService.java
precheck_collect/dashboard/src/main/resources/mapper/DashboardMapper.xml
precheck_collect/dashboard/src/main/resources/templates/dashboard/index.html
precheck_collect/dashboard/docs/RELEASE_20260731_conn_widgets_log_date_basis.md
```

개발 PC 절대경로(이번 작업 기준):

```
C:\Users\20200161\Desktop\PreCheck_work\Precheck_project\Precheck_SKSCh1\precheck_collect\dashboard\src\main\java\com\sks\precheck\dashboard\mapper\DashboardMapper.java
C:\Users\20200161\Desktop\PreCheck_work\Precheck_project\Precheck_SKSCh1\precheck_collect\dashboard\src\main\java\com\sks\precheck\dashboard\service\DashboardService.java
C:\Users\20200161\Desktop\PreCheck_work\Precheck_project\Precheck_SKSCh1\precheck_collect\dashboard\src\main\resources\mapper\DashboardMapper.xml
C:\Users\20200161\Desktop\PreCheck_work\Precheck_project\Precheck_SKSCh1\precheck_collect\dashboard\src\main\resources\templates\dashboard\index.html
C:\Users\20200161\Desktop\PreCheck_work\Precheck_project\Precheck_SKSCh1\precheck_collect\dashboard\docs\RELEASE_20260731_conn_widgets_log_date_basis.md
```

> **빌드 산출물은 이관 대상이 아니다.** 로컬에서 `build/resources/main/`, `bin/main/` 에 같은 파일을 복사해 뒀지만(IDE 실행용 stale 방지), 운영은 `src/` 만 반영하고 `clean build` 로 재생성한다.

### 2-1. 파일별 변경 내역

**`DashboardMapper.xml`** (`src/main/resources/mapper/DashboardMapper.xml`)

| 쿼리 | 구분 | 내용 |
|------|------|------|
| `selectLatestConnLogTimestamp` | 신규 | 접속자 6종 LOG_ID의 `MAX(LOG_TIMESTAMP)` — 기준일 D 산출 |
| `selectConnInfoData` | 신규 | 카드 3수치 1건. D 구간 내 최신 `LOG_TIMESTAMP`, 동일 시각 다건이면 `ANALYZE_RESULT_ID`(PK) 최대값으로 1건 확정 |
| `selectConnHistoryData` | 신규 | 접속자 시계열. 일별(index)·월별(history 페이지) 공용 |
| `selectUcSparkData` | 수정 | `ANALYZE_DATE = #{today}` 제거 → D 구간 파라미터로 교체. **postgresql / altibase 두 벌 모두** |
| `selectInfoData`, `selectHistoryData` | **무변경** | 종목/해외/서비스/리소스가 계속 사용 |

**`DashboardMapper.java`** (`src/main/java/com/sks/precheck/dashboard/mapper/DashboardMapper.java`) — 위 신규 3개 선언 추가, `selectUcSparkData(today, logId)` → `selectUcSparkData(logId, fromTs, toTs)`. `java.time.LocalDateTime` import 추가

**`DashboardService.java`** (`src/main/java/com/sks/precheck/dashboard/service/DashboardService.java`)

- 상수: `CONN_CARD_LOG_IDS`, `UC_SPARK_LOG_IDS`, `CONN_DEFAULT_SERVER_ID`, `MONTHLY_HISTORY_MONTHS`
- 헬퍼: `connCardBaseDate()`, `ucBaseDate()`, `latestLogDate()`, `logIdsByServer()`, `startOfDay()`, `startOfNextDay()`, `logDateOf()`
- `getAllInfoData()` — 접속자 3종만 `D_card` 기준 신규 쿼리로 분기 (응답 형식 변경 없음)
- `getUcSparkData()` — `D_uc` 구간 조회 (응답 형식 변경 없음)
- `getHistoryData()` — `conn` 그룹만 `D_card` 기준 기간 + 로그 날짜 키
- `getMonthlyHistoryAll()` — `conn` 그룹만 `D_card` 기준 기간 + 로그 날짜 키

**`index.html`** (`src/main/resources/templates/dashboard/index.html`)

- 카드 3라벨에 배지 span 추가 (`d-MAX_CONN_PREV` / `d-HTS_MAX_CONN` / `d-MTS_MAX_CONN`)
- UC 배지에 id 부여 (`ucSparkBadge`), 고정 문구 `TODAY (60분)` 제거
- JS 함수 `formatBaseTimestampBadge()`, `updateConnCardBadge()` 추가
- `updateInfoData()` — 접속자 3종은 배지에 `MM/DD HH:mm` 을 쓰고 기존 시각 자리(`t-*`)는 비움. 다른 카드는 기존 `formatTimestamp()` 경로 유지
- `updateUcSpark()` — 3개 시리즈의 마지막 로그 시각으로 배지를 `MM/DD HH:mm (60분)` 으로 갱신

---

## 3. 배포 절차

### 3-1. dashboard 재빌드·배포

```bash
# 1) 위 2절의 4개 파일을 운영 소스에 반영
# 2) 빌드
cd precheck_collect/dashboard
gradlew.bat clean build

# 3) 배포 후 기동
java -jar build/libs/dashboard-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

> **`clean build` 필수**: `index.html` 과 `DashboardMapper.xml` 이 JAR에 번들된다. `clean` 없이 소스만 바꾸면 화면·쿼리가 반영되지 않는다. "화면이 안 바뀐다" 시 `(Get-Item jar).LastWriteTime` 부터 확인할 것.

### 3-2. DB 작업

**없음.** DDL·시퀀스 변경 없고, 인덱스도 이번 릴리스에서는 생성하지 않는다(5-2 참조). 데이터 마이그레이션·백필도 불필요하다 — 기존 `LOG_TIMESTAMP` 값을 그대로 읽는다.

### 3-3. 설정 작업

**없음.** `application.yml` 의 `precheck.info-data`, `precheck.history-days`, `precheck.detail-exclude-log-ids` 모두 무변경이다.

> **다만 확인 1건**: 접속자 6종 LOG_ID의 서버구분이 운영 실제 값과 일치해야 한다. 로컬 기준 6종 모두 `pmaster2-마스터` 다.
> - 카드 3종의 서버구분은 `application.yml` 의 `precheck.info-data` 에서 읽는다
> - UC 3종은 `DashboardMapper.xml` 의 `selectUcSparkData` 에 **`'pmaster2-마스터'` 로 하드코딩**되어 있다 (이번 릴리스 이전부터 동일, 변경 없음)
> - 운영 서버구분이 다르면 이번 변경과 무관하게 기존에도 조회가 안 됐을 것이므로, 현재 화면에 값이 나오고 있다면 확인 불필요

---

## 4. 배포 후 검증

### 4-1. 화면 검증 (`http://<host>:8080`)

**접속자 현황 카드**

- [ ] 좌측 3수치(`전일 최대동시접속` / `전일 HTS` / `전일 MTS`) 라벨 옆에 **`MM/DD HH:mm` 배지** 표시 (예: `07/31 07:06`)
- [ ] 좌측 3수치의 **수치 옆 시각 표기는 비어 있어야 함** (배지로 일원화 — 같은 시각이 두 번 나오면 회귀)
- [ ] 우측 상단 배지가 `TODAY (60분)` 이 아니라 **`MM/DD HH:mm (60분)`** 로 표시
- [ ] **카드 배지 3개끼리는 같은 날짜** (`D_card` 공유 — 여기가 갈리면 회귀)
- [ ] 카드 배지와 UC 배지의 **날짜가 서로 다를 수 있음** — 카드 로그가 아직 오늘치가 없으면 정상 동작이다
- [ ] 60분 차트의 X축 마지막 시각이 UC 배지 시각과 일치
- [ ] 데이터가 없을 때: 카드 배지는 **숨김**, UC 배지는 `(60분)` 만, 수치는 `-`

**종목/해외/서비스 주요 데이터 카드 (회귀 확인)**

- [ ] 수치 옆 시각 표기(`t-*`)가 **기존대로 표시됨** — 접속자 카드만 배지로 옮겼으므로 나머지는 영향 없어야 함

**History (index 하단 카드)**

- [ ] "접속자수 현황" 탭의 X축 마지막 지점이 **기준일 D**와 일치
- [ ] "종목수 현황" / "해외종목수 현황" 탭은 **기존과 동일하게 동작**(회귀 없음)

**History 페이지 (`/dashboard/history`)**

- [ ] "접속자 현황" 차트의 마지막 월 지점이 D 기준
- [ ] "종목/해외종목/서비스 현황" 3개 차트는 **기존과 동일**

**공통**

- [ ] 라이트·다크 테마 양쪽에서 배지 가독성 확인
- [ ] 60초 자동 갱신 후에도 배지·차트 정상 재렌더

### 4-2. 핵심 시나리오 검증 (이번 릴리스의 목적)

**어제 로그가 오늘 수집된 상태**에서 확인해야 의미가 있다.

- [ ] 배지가 **어제 날짜**로 표시된다
- [ ] 카드 3수치가 어제 로그의 값이다
- [ ] 60분 차트가 어제 마지막 60분 구간을 그린다
- [ ] History 접속자 탭의 마지막 지점이 **어제 날짜**에 찍힌다 (기존에는 오늘에 찍혔음)

### 4-3. DB 대조 (조회 전용)

```sql
-- 기준일 D 확인 (화면 배지와 일치해야 함)
SELECT MAX(LOG_TIMESTAMP) AS BASE_TS
  FROM TB_ANALYZE_RESULT
 WHERE SERVER_ID = 'pmaster2-마스터'
   AND LOG_ID IN ('MAX_CONN_PREV','HTS_MAX_CONN','MTS_MAX_CONN',
                  'UC_TOTAL_COUNT','UC_HTS_COUNT','UC_MTS_COUNT');

-- 수집일과 로그 날짜가 어긋난 건이 실제로 있는지 (이번 변경의 대상)
SELECT ANALYZE_DATE,
       TO_CHAR(LOG_TIMESTAMP,'YYYYMMDD') AS LOG_DATE,
       LOG_ID, COUNT(*)
  FROM TB_ANALYZE_RESULT
 WHERE SERVER_ID = 'pmaster2-마스터'
   AND LOG_ID IN ('MAX_CONN_PREV','HTS_MAX_CONN','MTS_MAX_CONN')
   AND ANALYZE_DATE <> TO_CHAR(LOG_TIMESTAMP,'YYYYMMDD')
 GROUP BY 1,2,3
 ORDER BY 1 DESC;
```

### 4-4. API 검증

두 API 모두 **응답 형식은 변경되지 않았다.** `logTimestamp` 가 어느 날짜인지만 확인한다.

**`/dashboard/api/uc-spark`** — `D_uc` 구간(보통 오늘)의 마지막 60분

```json
{
  "UC_TOTAL_COUNT": [ { "logTimestamp": "2026-07-31T15:01:00", "logValue": 12345 } ],
  "UC_HTS_COUNT":   [ ... ],
  "UC_MTS_COUNT":   [ ... ]
}
```

**`/dashboard/api/info-data`** — 접속자 3종은 `D_card`(오늘치가 없으면 어제)

```json
{
  "MAX_CONN_PREV": { "logValue": 45678, "logTimestamp": "2026-07-30T23:10:00", "analyzeLevel": "정상", "thresholdValue": null, "thresholdOperator": null },
  "...": {}
}
```

- [ ] `info-data` 접속자 3종의 `logTimestamp` **날짜가 서로 동일** (`D_card` 공유)
- [ ] 각 `logTimestamp` 가 화면 배지의 `MM/DD HH:mm` 과 일치
- [ ] 4-3 SQL의 카드 3종 `MAX(LOG_TIMESTAMP)` 와 `info-data` 의 값이 일치

---

## 5. 영향 범위

### 5-1. API 응답 — **형식 무변경**

| API | 변경 |
|-----|------|
| `/dashboard/api/uc-spark` | **형식 변경 없음.** 조회 기준일만 `D_uc` 로 바뀜 |
| `/dashboard/api/info-data` | **형식 변경 없음.** 접속자 3종의 조회 기준(=`logTimestamp` 가 어느 날짜인지)만 바뀜 |
| `/dashboard/api/history?groupType=conn` | 형식 동일. `logDate` 값의 **의미**가 `ANALYZE_DATE` → 로그 날짜로 바뀜 |
| `/dashboard/api/monthly-history` | 형식 동일. `conn` 그룹의 `exactDate` **의미**만 로그 날짜로 바뀜 |

4개 API 모두 JSON 구조가 그대로이므로 다른 클라이언트가 있어도 깨지지 않는다. 바뀐 것은 **어느 날짜의 데이터가 담기는가** 뿐이다. 기존 소비자는 `index.html` / `history.html` 뿐이며 둘 다 함께 수정되었다.

### 5-2. 성능 — ⚠️ 이관 후 관찰 항목

`TB_ANALYZE_RESULT` 에는 **PK 외 인덱스가 없다**(`init_dev.sql` 기준). 기존 `ANALYZE_DATE` 조건도 Seq Scan이었으므로 **이번 변경으로 조건이 나빠지지는 않는다.** 단, 요청당 기준일 산출 쿼리가 **1건 추가**된다(`SELECT MAX(LOG_TIMESTAMP) ... WHERE SERVER_ID=? AND LOG_ID IN (3개)`). 카드 계열과 UC 계열이 각각 자기 API에서 1건씩 부담하므로 API 1회당 1건이다.

운영 데이터량에서 대시보드 응답이 느려지면 아래 인덱스를 추가하면 된다. 쿼리를 함수 없는 범위조건으로 작성해 두었으므로 **소스 수정 없이 바로 적용된다.**

```sql
CREATE INDEX IDX_ANALYZE_RESULT_SRV_LOG_TS
    ON TB_ANALYZE_RESULT (SERVER_ID, LOG_ID, LOG_TIMESTAMP);
```

> 이번 릴리스에 포함하지 않은 것은 정확성과 무관한 사안이고, 운영 테이블 인덱스 추가는 별도 승인 절차를 밟는 편이 낫다고 판단했기 때문이다.

### 5-3. 영향 없음 확인

- `collect` / `analyze` / `notify` — **소스 무변경**. 이번 변경은 조회 기준만 바꾼 것으로, 데이터를 쓰는 쪽은 그대로다
- 종목수 / 해외종목수 / 서비스 현황 차트 — 기존 `selectHistoryData` 를 그대로 쓰고 분기도 타지 않는다
- 서버별 분석 현황 바차트, 서버 리스트, 상단 요약 스트립, 점검현황 상세 목록 — 별개 쿼리
- 접속자 외 주요 데이터 카드(종목/해외/서비스) — `getAllInfoData` 안에서 기존 `selectInfoData` 경로 유지
- 로그인·비밀번호 정책·권한 — 무관

---

## 6. 롤백

`dashboard` 모듈 단독 롤백으로 충분하다. **이전 JAR로 되돌리고 재기동하면 즉시 원복된다.**

- DB 변경이 없으므로 되돌릴 데이터가 없다
- 설정 변경이 없으므로 conf/yml 원복도 불필요
- 롤백 시 화면은 `ANALYZE_DATE` 기준으로, UC 배지는 `TODAY (60분)` 고정 문구로 복귀한다

---

## 7. 인계 시 주의사항

1. **"60분"은 현재 시각 기준이 아니다.** `D_uc` 구간 안의 가장 최신 `LOG_TIMESTAMP` 를 끝점으로 한 직전 60분이다. 수집이 지연되면 차트가 과거 구간을 그리는 것이 정상 동작이며, 이제 배지 일시로 그 사실이 드러난다.
2. **배지 날짜가 오늘이 아니면 수집 지연 신호다.** 화면 버그가 아니라 로그 파일이 아직 당일 것이 아니라는 뜻이다. `TB_COLLECT_HISTORY` 를 함께 확인할 것. 단 카드 배지는 예외 — 3번 참조.
3. **카드 배지와 UC 배지의 날짜가 다른 것은 정상이다.** 기준일이 묶음별로 분리되어 있고(1-1), 카드 로그는 하루 1건이라 오늘치가 들어오기 전까지 어제 날짜를 유지한다. 오히려 **카드 배지 3개끼리 날짜가 갈리면** `D_card` 공유가 깨진 것이므로 회귀다.
4. **카드 3수치의 시각은 배지로 일원화했다.** 수치 옆 `t-*` 자리는 접속자 3종에 한해 비워둔다. 다른 주요 데이터 카드(종목/해외/서비스)는 기존 `formatTimestamp()` 표기를 그대로 쓴다.
5. **접속자 LOG_ID를 추가·변경할 때는 3곳을 함께 고쳐야 한다** — `DashboardService.CONN_CARD_LOG_IDS` / `UC_SPARK_LOG_IDS`, `application.yml` 의 `precheck.info-data`, `DashboardMapper.xml` 의 `selectUcSparkData` 하드코딩 서버구분. 특히 **어느 묶음에 넣느냐가 기준일 계산을 바꾼다** — 하루 1건이면 카드 계열, 분 단위면 UC 계열이다.
6. **`clean build` 없이 배포 금지.** 템플릿과 매퍼 XML이 JAR에 번들된다.
7. 인덱스는 의도적으로 제외했다(5-2). 느려지면 그때 추가하면 되고, 쿼리 수정은 필요 없다.

---

## 8. 검증 상태 (2026-07-31 로컬)

### 8-1. 통과한 항목

| 항목 | 상태 | 비고 |
|------|------|------|
| `gradlew.bat clean build` | ✅ **BUILD SUCCESSFUL** (8s, 8 tasks) | `compileJava` / `compileTestJava` / `bootJar` / `test` / `check` 전부 통과 |
| `gradlew.bat test` | ✅ 2/2 통과, 실패 0 · 에러 0 · 스킵 0 | 아래 8-2 참조 |
| Spring 컨텍스트 기동 | ✅ | `DashboardApplicationTests` 가 실제 PostgreSQL(`PreCheckPool-Test`)에 연결해 기동 |
| MyBatis 매퍼 XML 파싱 | ✅ | 컨텍스트 기동 시점에 검증됨 — XML 문법, `resultType` 클래스 해석, statement id 중복, `databaseId` 2벌 등록 |
| 리소스 3곳 동기화 | ✅ | `src/main/resources`, `build/resources/main`, `bin/main` |

**테스트 결과**

| 테스트 클래스 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `DashboardApplicationTests` | 1 | 0 | 0 | 0 |
| `ResourceDataShapeTest` | 1 | 0 | 0 | 0 |

> `ResourceDataShapeTest` 는 실DB로 `/dashboard/api/resource` 응답 형태를 검증한다. 이번 변경이 리소스 바차트에 회귀를 내지 않았음을 확인해 준다.

### 8-2. 컴파일 검증이 보장하는 것 / 못 하는 것

**보장한다**
- 신규 메서드 3개와 시그니처 변경(`selectUcSparkData`)이 호출부와 일치 — 시그니처 불일치는 `compileJava` 에서 잡힌다
- `DashboardMapper.xml` 이 정상 파싱되고 `resultType` 의 DTO 클래스가 모두 해석됨
- 신규 statement id 3개가 기존과 충돌하지 않음
- 컨텍스트 기동에 실패하지 않음(빈 생성, 설정 바인딩)

**보장하지 못한다** — 아래는 여전히 육안·실행 검증이 필요하다
- 신규 쿼리 3개의 **실제 실행 결과** (매퍼 메서드-statement 바인딩은 MyBatis가 호출 시점에 확인한다)
- 기준일 D 산출값의 정확성, 배지 4개의 날짜 일치
- 반개구간 조건이 날짜 경계에서 의도대로 잘리는지
- 화면 렌더링(배지 표시/숨김, 차트 X축)

### 8-3. 미검증 항목

| 항목 | 상태 | 필요한 것 |
|------|------|-----------|
| 신규 쿼리 3개 실행 | ⛔ **미검증** | 접속자 로그가 있는 DB에서 화면 조회 |
| **브라우저 화면 렌더링** (4-1) | ⛔ **미검증** | dashboard 기동 후 육안 확인 |
| **핵심 시나리오** (4-2) | ⛔ **미검증** | "어제 로그를 오늘 수집" 상태 재현 |

> **운영 이관 전 로컬에서 4-1 / 4-2 를 반드시 육안 확인할 것.** 특히 4-2는 이번 릴리스의 존재 이유이므로, `test_dataset` 의 로그 타임스탬프를 어제로 바꿔 수집·분석한 뒤 확인하는 것을 권장한다.
