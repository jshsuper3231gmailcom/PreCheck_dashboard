# 릴리스 노트 — 서버별 분석 현황 바차트 전환 + 메모리 사용률(MEM_USAGE) 신설

- **작성일**: 2026-07-30
- **대상 모듈**: `dashboard` 단독 재빌드 (collect / analyze / notify **소스 무변경**)
- **영향 화면**: "디스크 현황" → **"서버별 분석 현황"** 패널 (도넛차트 → 초밀도 리스트형 가로 바차트)
- **DB 스키마 변경**: 없음 (`MEM_USAGE`는 기존 테이블의 새 LOG_ID 값일 뿐, DDL·시퀀스 변경 없음)
- **설정 파일 변경**: **있음** — 운영 `PreCheck_AnalyzePolicy.conf`에 `MEM_USAGE` 정책 수동 추가 필요
- **재기동 필요**: `dashboard`(필수) + `analyze`(정책 conf 추가 시 필수, 아래 3-2 참조)
- **선행 조건**: 대상 서버가 `MEM_USAGE` 정규화 로그를 출력해야 함 (**미충족 시 MEM 바는 계속 "분석없음"**, 아래 1-2 참조)

---

## 1. 변경 배경

### 1-1. 화면 전환

기존 "디스크 현황" 패널은 서버 1대당 Chart.js 도넛차트 1개 구조였고, 표시 지표는 `DISK_HOME` 하나뿐이었다. 서버 수가 늘수록 세로 공간을 과도하게 차지하고, 메모리 사용률은 볼 수 없었다.

디자인 가이드(`시스템 디스크 사용률 차트 변경.pdf` v2.0)에 따라 **서버 1대 = 카드 1개(가로 바 2줄)** 초밀도 리스트형으로 전환한다. 30대 이상으로 늘어나도 세로 공간이 선형으로만 증가한다.

### 1-2. `MEM_USAGE` 신설 — ⚠️ 이관 시 최우선 확인 항목

착수 전 조사 결과 **메모리 사용률(수치) 지표가 시스템에 존재하지 않았다.** 기존 `MEM_*` LOG_ID는 전부 **존재형**이다.

| LOG_ID | 타입 | 용도 |
|--------|------|------|
| `MEM_CHECK`, `MEM_MBSOSI`, `MEM_MINI` | 존재 | 메모리 세그먼트 부존재 감시 (사용률 아님) |
| `DISK_HOME` | 수치 | home 디스크 사용률 (7대 전부 보유) |
| `DISK_DATA` | 수치 | data 디스크 사용률 (dlprem01 1대만 보유) |

그래서 `MEM_USAGE`(수치)를 신규 도입했다. **대시보드는 로그를 읽어 표시할 뿐, 로그를 만들어내지 못한다.**

> **대상 서버의 레거시 C 점검 프로그램이 아래 형식의 로그를 출력하도록 별도 반영되어야 한다.**
>
> ```
> @@@[yyyy/MM/dd HH:mm:ss.SSS][수치][MEM_USAGE]|메모리사용률|$85.3$@@@
> ```
>
> 이 반영 전까지 MEM 바는 점선 + "분석없음"으로 표시된다(화면은 정상 동작, 데이터만 비어 있음). DISK 바는 기존대로 정상 표시되므로 **단계적 이관이 가능하다.**

### 1-3. 확정된 설계 판단

| 항목 | 결정 | 근거 |
|------|------|------|
| 색상 | 가이드 색상(#0c1424/#141e33 남색) 대신 **기존 대시보드 테마 유지** | 이 패널만 톤이 달라져 주변 카드와 이질감 발생. 가이드에서는 레이아웃·치수만 채택 |
| 경고 판정 | 가이드의 "임계치의 90%" 대신 **DB의 `ANALYZE_LEVEL` 사용** | analyze가 정책 파일의 서버별 `WARNING_RATIO`로 이미 판정함(`NumericAnalyzer`: 경고구간 = `[threshold - threshold×ratio/100, threshold)`). 화면에서 재계산하면 같은 화면 상세 현황 테이블과 레벨이 어긋남 |
| 정렬 | **기존 `SERVER_ID` 순 유지** (심각도순 미채택) | 운영자가 서버 위치를 기억하는 편이 낫다는 판단 |

---

## 2. 수정 파일 위치

### 2-1. 운영 이관 대상 — `precheck_collect/dashboard/`

총 6개 파일(소스 5 + 문서 1). **이 모듈만 재빌드하면 된다.**

```
precheck_collect/dashboard/
├── src/main/java/com/sks/precheck/dashboard/
│   ├── mapper/DashboardMapper.java             [수정] selectResourceData 시그니처 (logIds 파라미터 추가)
│   └── service/DashboardService.java           [수정] 상수 2 + getResourceData 재작성 + 주석
├── src/main/resources/
│   ├── mapper/DashboardMapper.xml              [수정] selectResourceData SQL 전면 개편
│   ├── templates/dashboard/index.html          [수정] CSS 블록 교체 + 마크업 + updateResource 전면 교체
│   └── static/css/precheck-theme.css           [수정] 다크테마 카드 규칙 클래스명 교체
├── src/test/java/com/sks/precheck/dashboard/
│   └── ResourceDataShapeTest.java              [신규] API 응답 형태 회귀 테스트
└── FLOW.md                                     [수정] 문서 4개 항목
```

### 2-2. 운영 이관 **제외** — 로컬 테스트 전용

아래는 **운영에 복사하지 말 것.** 로컬 파이프라인 테스트 데이터 생성용이다.

```
Precheck_SKSCh1/
├── .claude/skills/logdatagen/scripts/
│   ├── precheck_log_common.js                  [수정] RESOURCE_LOG_IDS 상수 + MEM_USAGE 라벨/정책 자동보강
│   └── generate_today_logs.js                  [수정] 서버별 MEM_USAGE 로그 1건 생성
├── precheck_collect/analyze/analyze_sample/
│   └── PreCheck_AnalyzePolicy.conf             [자동수정] 로컬 샘플 conf. 운영 conf는 별도 경로(3-2 참조)
└── precheck_collect/test_dataset/*.log         [재생성] 로컬 테스트 로그
```

---

## 3. 배포 절차

### 3-1. dashboard 재빌드·배포

```bash
# 1) 위 2-1의 6개 파일을 운영 소스에 반영
# 2) 빌드
cd precheck_collect/dashboard
gradlew.bat clean build

# 3) 배포 후 기동
java -jar build/libs/dashboard-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

> **JAR stale 주의**: `index.html` / `precheck-theme.css` 는 JAR에 번들된다. `clean build` 없이 소스만 바꾸면 화면 변경이 반영되지 않는다. "화면이 안 바뀐다" 시 `(Get-Item jar).LastWriteTime` 부터 확인할 것.

### 3-2. analyze 정책 conf 추가 (`MEM_USAGE` 로그 반영 서버가 생긴 뒤)

운영 정책 파일 경로: **`/home/precheck/cfg/PreCheck_AnalyzePolicy.conf`**
(로컬 자동보강은 `analyze/analyze_sample/` 아래 샘플 파일에만 적용됐으므로 **운영은 수동 추가**해야 한다.)

서버별로 아래 형식 1줄씩 추가한다. 임계치·경고비율은 `DISK_HOME`을 미러링한 값이며 서버 사정에 맞게 조정 가능하다.

```
[<serverId>][MEM_USAGE][수치][<][90][20]
```

로컬 검증에 사용한 7대 기준 예시:

```
[dlprem01-테스트개발][MEM_USAGE][수치][<][90][20]
[pamoap01-자동주문][MEM_USAGE][수치][<][90][20]
[pqgetap1-시세포착1][MEM_USAGE][수치][<][90][20]
[pqgetap2-시세포착2][MEM_USAGE][수치][<][90][20]
[pjpsap01-주파수클럽][MEM_USAGE][수치][<][90][20]
[pmaster2-마스터][MEM_USAGE][수치][<][90][20]
[ddfep01-해외시세][MEM_USAGE][수치][<][90][20]
```

> **`analyze` 재기동 필수**: `PolicyLoader`가 `@PostConstruct`로 기동 시 1회만 로딩한다(`analyze/src/main/java/com/sks/precheck/analyze/config/PolicyLoader.java:46`). 리로드 주기가 없으므로 conf만 고치면 반영되지 않는다.
>
> 정책을 추가하지 않으면 `MEM_USAGE`는 `ANALYZE_LEVEL='미분석'`으로 저장되고, 화면에는 **수치는 표시되되 회색**으로 나온다(정상색 오인 방지).

### 3-3. DB 작업

**없음.** DDL·시퀀스·인덱스 변경 없다. `MEM_USAGE`는 `TB_COLLECT_LOG` / `TB_ANALYZE_RESULT`의 `LOG_ID` 컬럼에 들어가는 새 값일 뿐이다.

---

## 4. 배포 후 검증

### 4-1. 화면 검증 (`http://<host>:8080`)

- [ ] 패널 제목이 **"서버별 분석 현황"** 으로 표시
- [ ] 헤더 우측 요약줄 `전체 N대 · 에러 N · 경고 N · 정상 N · 분석없음 N`, 합계 = 카드 수
- [ ] 카드 구성: 좌측 서버ID(굵게)/한글명(작게), 중앙 DISK·MEM 가로 바 2줄, 우측 상태 배지
- [ ] 창 폭 축소 시 3열 → 2열 → 1열 자동 축소
- [ ] 임계치 마커(세로 눈금선)가 서버별 임계치 위치에 표시 — 임계치가 다른 서버는 마커 위치도 달라야 함
- [ ] 바 hover 시 `DISK 임계치 90 · 현재 83` 툴팁
- [ ] 하단 범례 5종(정상/경고/에러/분석없음/임계치 지점)
- [ ] **라이트·다크 테마 양쪽** 정상 (기존 테마 유지 결정이라 두 모드 다 확인 필요)
- [ ] 상태색이 **같은 화면 "점검현황 상세" 테이블의 레벨과 일치**
- [ ] 60초 자동 갱신 후에도 정상 재렌더

### 4-2. DB 검증 (조회 전용)

```sql
-- 서버당 DISK/MEM 2행이 나오는지
SELECT SERVER_ID, LOG_ID, LOG_VALUE, ANALYZE_LEVEL, THRESHOLD_VALUE
  FROM TB_ANALYZE_RESULT
 WHERE ANALYZE_DATE = TO_CHAR(SYSDATE, 'YYYYMMDD')   -- PostgreSQL: TO_CHAR(NOW(),'YYYYMMDD')
   AND LOG_ID IN ('DISK_HOME','MEM_USAGE')
 ORDER BY 1, 2;

-- 미분석(정책 누락) 서버 색출
SELECT SERVER_ID, LOG_ID
  FROM TB_ANALYZE_RESULT
 WHERE ANALYZE_DATE = TO_CHAR(NOW(),'YYYYMMDD')
   AND LOG_ID = 'MEM_USAGE'
   AND ANALYZE_LEVEL = '미분석';
```

### 4-3. API 검증

`/dashboard/api/resource` 응답이 **서버당 1개 객체**이고 `disk`/`mem` 하위 객체 + `id`/`name` 분리를 포함하는지 확인.

```json
{
  "serverId": "ddfep01-해외시세",
  "id": "ddfep01",
  "name": "해외시세",
  "noData": false,
  "disk": { "logId": "DISK_HOME", "logValue": 83.0, "analyzeLevel": "경고", "thresholdValue": 90.0, "logTimestamp": "..." },
  "mem":  { "logId": "MEM_USAGE", "logValue": 92.6, "analyzeLevel": "에러", "thresholdValue": 90.0, "logTimestamp": "..." }
}
```

---

## 5. 영향 범위

### 5-1. 응답 형식 **파괴적 변경(breaking change)**

`/dashboard/api/resource` 응답 구조가 바뀌었다. 이 API를 사용하는 다른 클라이언트가 있다면 함께 수정해야 한다(현재 확인된 소비자는 `index.html`의 `updateResource()` 하나뿐).

| | 이전 | 이후 |
|---|---|---|
| 행 단위 | 서버 1대 = 1행, `DISK_HOME` 고정 | 서버 1대 = 1건, 안에 `disk`/`mem` 하위 객체 |
| 필드 | `serverId`, `logValue`, `analyzeLevel`, `thresholdValue`, `logTimestamp` | `serverId`, `id`, `name`, `noData`, `disk{...}`, `mem{...}` |

### 5-2. `MEM_USAGE`가 다른 화면 요소에 미치는 영향 ⚠️

`MEM_USAGE`는 신규 수치 지표이므로 **점검현황 상세 테이블(에러/경고·정상/정보 탭)과 상단 요약 5개 카운트에도 자동 포함**된다. `DISK_HOME`과 동일한 동작이며, 서버 수 × 1건만큼 건수가 증가한다.

상세 목록에서 감추려면:

```yaml
# dashboard/src/main/resources/application.yml
precheck:
  detail-exclude-log-ids:
    - MEM_USAGE
```

`@ConfigurationProperties`라 **변경 후 dashboard 재기동 필요**. 이번 릴리스에는 적용하지 않았다(요약 카운트에도 잡히는 편이 맞다고 판단).

### 5-3. 영향 없음 확인

- `collect` / `analyze` / `notify` **소스 무변경** — 수치형 파서·분석기가 정책 기반 범용 구현이라 새 LOG_ID를 코드 수정 없이 처리한다
- 접속자 현황, History 그래프, 주요 데이터 카드, 서버 리스트, UC 스파크라인 — 별개 쿼리라 무영향
- 통보(notify) — `MEM_USAGE`에 정책을 넣으면 에러/경고 시 **SMS 통보 대상에 포함된다.** 통보를 원치 않으면 정책을 넣지 않거나 `PreCheck_NotifyTarget_List.conf`를 확인할 것

---

## 6. 롤백

`dashboard` 모듈 단독 롤백으로 충분하다. 이전 JAR로 되돌리고 재기동하면 도넛차트로 복귀한다.

- 정책 conf의 `MEM_USAGE` 줄은 **남겨둬도 무해하다** — 롤백된 화면은 `DISK_HOME`만 조회하므로 무시되고, 수집·분석 결과만 계속 쌓인다
- 상세 테이블의 `MEM_USAGE` 행까지 지우고 싶다면 5-2의 `detail-exclude-log-ids`를 사용하거나 정책 줄을 제거 후 analyze 재기동

---

## 7. 인계 시 주의사항

1. **`MEM_USAGE` 로그 생성이 대시보드 밖의 선행 작업이다.** (1-2 참조) 대상 서버 점검 프로그램 반영 일정과 대시보드 배포 일정을 맞출 필요는 없다 — 먼저 배포해도 MEM 바가 "분석없음"으로 뜰 뿐 DISK는 정상 동작한다.
2. **정책 conf 추가 후 analyze 재기동을 빠뜨리지 말 것.** (3-2) 재기동 없이는 계속 '미분석'으로 저장된다.
3. **`clean build` 없이 배포 금지.** (3-1) 템플릿·CSS가 JAR에 번들된다.
4. 서버별 임계치가 다르면 마커 위치도 달라진다 — 화면 검증 시 "마커 위치가 제각각"인 것은 정상이다.
5. `serverId`를 첫 `-` 기준으로 `id`/`name`으로 분리해 표시한다. 신규 서버 등록 시 `<식별자>-<한글명>` 명명 규칙을 지켜야 카드 표기가 깨지지 않는다(규칙에 안 맞으면 전체를 식별자로 표시하고 한글명은 공란).

---

## 8. 검증 상태 (2026-07-30 로컬)

| 항목 | 상태 | 비고 |
|------|------|------|
| `gradlew.bat compileJava` | ✅ | |
| `gradlew.bat test` 전체 | ✅ | `ResourceDataShapeTest` 포함 통과 |
| 테스트 로그 생성 | ✅ | 7개 서버 `MEM_USAGE` 정책 자동보강 + 로그 1건씩 생성 |
| collect (local) | ✅ | 7/7 수집 성공, 정규화 실패 0건 |
| analyze | ✅ | 7서버 × 2지표 = 14행, 상태 혼재(에러 2·경고 4·정상 8) |
| API 응답 형태 | ✅ | `ResourceDataShapeTest`로 실DB 검증(카드 중복 없음, disk/mem 항상 존재, id/name 분리, noData 일치) |
| **브라우저 화면 렌더링** | ⛔ **미검증** | 로컬 admin 비밀번호가 `init_dev.sql` 기본값(`!Sks8245`)과 불일치, `LOGIN_FAIL_COUNT=4`라 계정 잠금 위험으로 시도 중단. **운영 이관 전 로컬에서 육안 확인 권장** |
