# 릴리스 노트 — 대시보드 서버 모수 기준 변경 (최근 7일 제한)

- **작성일**: 2026-07-30
- **대상 모듈**: `dashboard` 단독 (collect / analyze / notify 무관)
- **영향 화면**: 디스크 현황(리소스 도넛), 수집서버 상세(서버 리스트 카드), 서버 선택 드롭다운
- **DB 스키마 변경**: 없음
- **설정 파일(conf / yml) 변경**: 없음
- **재기동 필요**: 예 (dashboard만)

---

## 1. 변경 배경

대시보드 "디스크 현황"과 "수집서버 상세"의 서버 모수가 `TB_COLLECT_HISTORY UNION TB_ANALYZE_HISTORY` **전체**(날짜 조건 없음)였다. 과거에 한 번이라도 수집된 서버는 폐기된 뒤에도 영구히 화면에 남았다.

모수를 `= 당일`로 좁히는 방식은 채택하지 않았다. collect / analyze는 작업 시작 직전에 이력을 `STATUS=FAIL, FAIL_REASON=IN_PROGRESS`로 선등록하므로(`collect/src/main/java/com/sks/precheck/collect/service/CollectService.java` Step 3), 수집에 실패한 서버는 당일 행이 남아 어차피 화면에 보인다. 반면 **스케줄러 자체가 기동하지 않아 이력이 0건인 장애**나 스케줄 시각이 아직 도래하지 않은 서버는 화면에서 통째로 사라져 "수집미완료" 상태를 판별할 수 없게 된다. 장애가 빈 화면으로 은폐되는 쪽이 더 위험하다고 판단했다.

→ **최근 7일(당일 포함) 이력 보유 서버**를 모수로 채택했다. 폐기 서버는 걸러지고, 당일 미시도 서버는 상태값만 비어 "수집미완료"로 남는다.

---

## 2. 수정 파일 위치

기준 루트: `precheck_collect/dashboard/`
총 4개 파일(소스 3 + 문서 1). 라인 번호는 2026-07-30 반영 시점 기준이다.

```
precheck_collect/dashboard/
├── src/main/java/com/sks/precheck/dashboard/
│   ├── mapper/DashboardMapper.java          [수정] 메서드 시그니처 2건
│   └── service/DashboardService.java        [수정] 상수 1 + 헬퍼 1 + 호출부 2 + 주석 2
├── src/main/resources/
│   └── mapper/DashboardMapper.xml           [수정] SQL 2건 + 주석 2
└── FLOW.md                                  [수정] 문서 함수표 2행
```

### ① `src/main/resources/mapper/DashboardMapper.xml`

핵심 SQL 변경. 두 곳 모두 UNION 서브쿼리에 날짜 조건을 추가했다.

**`selectServerList`** (수집서버 상세) — select L306, UNION 서브쿼리 L316~L318

```sql
-- 변경 전
SELECT SERVER_ID FROM TB_COLLECT_HISTORY
UNION
SELECT SERVER_ID FROM TB_ANALYZE_HISTORY

-- 변경 후
SELECT SERVER_ID FROM TB_COLLECT_HISTORY WHERE COLLECT_DATE >= #{sinceDate}
UNION
SELECT SERVER_ID FROM TB_ANALYZE_HISTORY WHERE ANALYZE_DATE >= #{sinceDate}
```

선행 주석 L294~L305에 모수 기준, "당일로 좁히면 스케줄러 미기동 장애가 은폐된다"는 판단 근거, 그리고 `sinceDate` 계산 위치(`DashboardService.serverPopulationSince()` / `SERVER_POPULATION_DAYS`)를 기재했다.

**`selectResourceData`** (디스크 현황) — select L401, UNION 서브쿼리 L409~L411

위와 완전히 동일한 블록을 적용했다. 선행 주석 L394~L400에 "모수 조건 변경 시 `selectServerList`와 반드시 함께 변경"이라는 경고를 기재했다.

> `databaseId="altibase"` 변형 쿼리는 이 두 select에 존재하지 않으므로 드라이버 분기 수정은 불필요하다.

### ② `src/main/java/com/sks/precheck/dashboard/mapper/DashboardMapper.java`

파라미터만 추가했다. 로직 변경 없음.

**L136 `selectServerList`**

```java
List<Map<String, Object>> selectServerList(
        @Param("today") String today,
        @Param("sinceDate") String sinceDate     // L138 추가
);
```

**L164 `selectResourceData`**

```java
List<Map<String, Object>> selectResourceData(
        @Param("today") String today,
        @Param("sinceDate") String sinceDate     // L166 추가
);
```

각각 Javadoc `@param sinceDate` 1줄을 추가했다(L133, L161).

### ③ `src/main/java/com/sks/precheck/dashboard/service/DashboardService.java`

기간 값의 **유일한 정의처**다.

**L57 — 상수 신설** (선행 주석 L52~L56에 채택 사유)

```java
private static final int SERVER_POPULATION_DAYS = 7;
```

**L508 — 헬퍼 신설** (기존 `today()` L495 바로 아래)

```java
private String serverPopulationSince() {
    return LocalDate.now().minusDays(SERVER_POPULATION_DAYS - 1L).format(YYYYMMDD);
}
```

`- 1L`은 오늘을 포함해 총 7일이 되게 하기 위한 것이다. 상수가 7이고 오늘이 `20260730`이면 `20260724`를 반환한다(20260724~20260730).

**L224 / L351 — 호출부**

```java
dashboardMapper.selectServerList(today(), serverPopulationSince());     // L224
dashboardMapper.selectResourceData(today(), serverPopulationSince());   // L351
```

**Javadoc 정정 2건**

- `getResourceData()` L345~L348 — 기존 "오늘 분석 이력이 있는 서버별 목록"은 실제 동작과 달랐다. LEFT JOIN이므로 오늘 DISK_HOME 분석 결과가 없는 서버도 수치 null로 포함되어 화면에 "분석없음"으로 표시된다. 실제 동작에 맞게 고쳤다.
- `serverPopulationSince()` L499~L507 — 기존 `@return`이 "SERVER_POPULATION_DAYS일 전 날짜"로 읽혀 7일 전(실제는 6일 전)으로 오독될 수 있었다. 계산식과 예시를 명시했다.

> 보존 기간 변경은 **L57 한 줄만** 수정하면 두 위젯에 동시 반영된다. 상수이므로 yml로는 변경할 수 없다.

### ④ `FLOW.md` (문서)

매퍼 함수표 2행을 갱신했다.

- `selectServerList` 파라미터 → `String today, String sinceDate`, 설명에 "최근 7일" 명시
- `selectResourceData` 파라미터 → `String today, String sinceDate`, 설명에 "서버 모수는 `selectServerList`와 동일" 명시

---

## 3. 배포 절차

1. 위 소스 4개 파일을 반영한다.
2. `dashboard/` 루트에서 빌드한다.
   ```bash
   gradlew.bat clean build
   ```
3. 산출물을 확인한다.
   ```
   dashboard/build/libs/dashboard-0.0.1-SNAPSHOT.jar   ← 이 파일만 교체
   ```
   `dashboard-0.0.1-SNAPSHOT-plain.jar`는 배포 대상이 아니다.
4. 기존 대시보드 프로세스를 정지한다.
5. JAR을 교체하고 기동한다.
   ```bash
   java -jar dashboard-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
   ```
6. `http://<운영주소>:8080` 접속 → 로그인 → 4장 검증을 수행한다.

DB 작업 없음. conf / yml 수정 없음. collect / analyze / notify는 정지·재기동이 불필요하다.

---

## 4. 배포 후 검증

### 4-1. 화면 검증

"수집서버 상세" 카드 수 == "디스크 현황" 도넛 수. 두 값은 항상 일치해야 한다. 불일치하면 두 쿼리 중 한쪽만 반영된 것이다.

### 4-2. DB 검증 (PostgreSQL, 조회 전용)

```sql
-- 변경 전 모수
SELECT COUNT(*) FROM (
  SELECT SERVER_ID FROM TB_COLLECT_HISTORY
  UNION
  SELECT SERVER_ID FROM TB_ANALYZE_HISTORY
) T;

-- 변경 후 모수 (당일 포함 7일)
SELECT COUNT(*) FROM (
  SELECT SERVER_ID FROM TB_COLLECT_HISTORY
   WHERE COLLECT_DATE >= TO_CHAR(CURRENT_DATE - 6, 'YYYYMMDD')
  UNION
  SELECT SERVER_ID FROM TB_ANALYZE_HISTORY
   WHERE ANALYZE_DATE >= TO_CHAR(CURRENT_DATE - 6, 'YYYYMMDD')
) T;

-- 이번 변경으로 화면에서 빠지는 서버 목록 (배포 전 확인 권장)
SELECT SERVER_ID FROM (
  SELECT SERVER_ID FROM TB_COLLECT_HISTORY
  UNION
  SELECT SERVER_ID FROM TB_ANALYZE_HISTORY
) A
WHERE SERVER_ID NOT IN (
  SELECT SERVER_ID FROM TB_COLLECT_HISTORY
   WHERE COLLECT_DATE >= TO_CHAR(CURRENT_DATE - 6, 'YYYYMMDD')
  UNION
  SELECT SERVER_ID FROM TB_ANALYZE_HISTORY
   WHERE ANALYZE_DATE >= TO_CHAR(CURRENT_DATE - 6, 'YYYYMMDD')
);
```

마지막 쿼리 결과에 **현재 운영 중인 서버가 포함되면 배포를 보류**한다. 해당 서버가 7일 이상 수집되지 않았다는 뜻이므로, 화면 문제가 아니라 수집 장애를 먼저 확인해야 한다.

---

## 5. 영향 범위

**영향 있음**

- 디스크 현황 도넛
- 수집서버 상세 카드
- 서버 선택 드롭다운 (같은 목록을 사용)

**영향 없음**

- 상단 요약 카운트
- 점검현황 상세(에러/경고 탭, 정상/정보/미분석 탭)
- 주요 데이터 카드, 히스토리 그래프, UC 스파크라인
- collect / analyze / notify 동작
- SMS 통보 대상 판정

대시보드는 조회 전용이므로 이 변경으로 DB 쓰기는 발생하지 않는다.

---

## 6. 롤백

JAR만 이전 버전으로 되돌리고 재기동한다. DB·설정 변경이 없으므로 데이터 정합성 이슈가 없으며, 롤백 즉시 이전 동작(전체 이력 모수)으로 복귀한다.

---

## 7. 인계 시 주의사항

- `selectServerList`와 `selectResourceData`는 **모수 조건이 항상 동일해야 한다**. 한쪽만 수정하면 카드 수와 도넛 수가 어긋난다. 해당 경고는 XML 주석에 명시해 두었다.
- 7일이 짧다고 판단되면 `DashboardService.SERVER_POPULATION_DAYS` 값만 조정한 뒤 재빌드한다. 상수이므로 yml 설정으로는 변경할 수 없다.
- 로컬 개발 환경에서는 `build/resources/main/mapper/DashboardMapper.xml`, `bin/main/mapper/DashboardMapper.xml`에도 XML을 복사해야 IDE Run 시 stale 리소스로 인한 미반영을 피할 수 있다(운영 이관과는 무관).

---

## 8. 검증 상태

- 로컬 빌드: `gradlew.bat build` 통과
- 운영 DB 대상 검증: 미수행 — 4-2 SQL은 운영 환경에서 직접 실행해야 한다.
