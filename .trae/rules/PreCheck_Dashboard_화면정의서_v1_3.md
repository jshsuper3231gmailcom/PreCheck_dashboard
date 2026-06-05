# PreCheck Dashboard 화면 정의서 v1.3

> 작성일: 2026-05-29 / 수정일: 2026-06-02  
> 참조 문서: 프로그램 명세서, 로그포맷정의서, 로그수집DB정의서, 로그분석DB정의서  
> 사용 기술: Spring Boot 3.x / Thymeleaf / AdminLTE 4.x / Chart.js / Bootstrap 5

> 📌 **v1.3 변경 이력**
> 1. 2번 요약 섹션 UI 개편 — **가로 스트립(Horizontal Strip)** 1개 카드로 통합
>    - 분석 결과(에러/경고/정상/정보/미분석) + 수집/분석 현황 + FAIL/SKIP 을 1개 카드 안에 압축
>    - 기존 카드 10개 → 카드 1개로 축소, 한 줄 스캔으로 전체 현황 파악 가능
> 2. 3번 정보성 중요 데이터 UI 개편 — **그룹별 리스트 카드** 3개로 통합
>    - 기존 info-box 13개 → 카드 3개(종목/서비스/접속자) 리스트 형태로 변경
>    - 항목명 + 수치 + 수집시각을 한 줄에 표시, 그룹 헤더로 구분

> 📌 **v1.2 변경 이력**
> 1. 분석 결과 → Top-border Card, 수집/분석 현황 → Progress Inline Bar 로 변경
> 2. 6번 서버별 리소스 현황 → 4번 아래 배치

> 📌 **v1.1 변경 이력**
> 1. AdminLTE 4.x (Bootstrap 5 기반) 으로 확정
> 2. 분석 서버수 조회 기준 명확화 — `ANALYZE_DATE = 오늘`
> 3. 히스토리 SQL `LOG_TYPE` 조건 주석 추가
> 4. 수집 실패 `FAIL | SKIP` 별도 표시
> 5. 리소스 도넛차트 — `DISK_HOME` 동적 표시
> 6. 정보성 날짜 표시 — `LOG_TIMESTAMP` 기준

---

## 0. 공통 정의

### 0-1. 기술 스택

| 항목 | 기술 | 비고 |
|---|---|---|
| Backend | Spring Boot 3.x | Java 17, MyBatis |
| 화면 템플릿 | Thymeleaf | AdminLTE HTML에 적용 |
| UI 프레임워크 | AdminLTE 4.x | Bootstrap 5 기반 (Bootstrap 4 클래스 사용 불가) |
| 차트 | Chart.js 4.x | AdminLTE 4.x 내장 |
| 아이콘 | Bootstrap Icons 1.x | AdminLTE 4.x 기본 아이콘셋 |

> ⚠️ **AdminLTE 버전 주의**
> - AdminLTE 3.x = Bootstrap 4 기반 → `mr-*`, `ml-*`, `font-weight-bold` 클래스 사용
> - AdminLTE 4.x = Bootstrap 5 기반 → `me-*`, `ms-*`, `fw-bold` 클래스 사용
> - 두 버전의 클래스를 혼용하면 레이아웃이 깨지므로 반드시 4.x 기준으로 통일

### 0-2. 자동 갱신

| 항목 | 내용 |
|---|---|
| 갱신 주기 | 5분 (300초) 고정 |
| 갱신 방식 | JavaScript fetch (AJAX), 페이지 새로고침 없음 |
| 갱신 범위 | 전체 데이터 (요약카드, 목록, 차트 모두 갱신) |
| 카운트다운 표시 | 상단 우측에 MM:SS 형식으로 남은 시간 표시 |

### 0-3. 데이터 조회 기준

| 항목 | 기준 |
|---|---|
| 오늘 기준 | COLLECT_DATE / ANALYZE_DATE = 오늘 날짜 (yyyyMMdd) |
| 전체 수집 서버수 | PreCheck_CollectLogs_Schedule.conf 에서 # 제외 + 유효 라인 기준 서버구분 DISTINCT count, 설정 yml파일에서 conf파일위치 설정함 |
| 전체 분석 서버수 | PreCheck_AnalyzeLogs_Schedule.conf 에서 # 제외 + 유효 라인 기준 서버구분 DISTINCT count, 설정 yml파일에서 conf파일위치 설정함 |
| 수집 완료 서버수 | TB_COLLECT_HISTORY에서 **COLLECT_DATE = 오늘** + COLLECT_STATUS = 'SUCCESS' DISTINCT SERVER_ID |
| 분석 완료 서버수 | TB_ANALYZE_HISTORY에서 **ANALYZE_DATE = 오늘** + ANALYZE_STATUS = 'SUCCESS' DISTINCT SERVER_ID |

> ℹ️ **ANALYZE_DATE vs ANALYZE_TARGET_DATE 구분**
> - `ANALYZE_DATE` = 분석 서버가 실행된 날짜 (오늘) ← Dashboard 조회 기준
> - `ANALYZE_TARGET_DATE` = 분석 대상 로그의 수집 날짜 (전날일 수도 있음)
> - Dashboard는 **"오늘 분석 서버가 실행됐는가"** 를 보는 것이므로 `ANALYZE_DATE` 기준 사용

### 0-4. 정보성 데이터 서버+LOG_ID 매핑표 (v1.3 확정)

> ℹ️ 서버구분에 (확정 필요) 표시된 항목은 운영 환경 투입 전 담당자 확정 필요

#### 3번 영역 - 중요 정보성 데이터

| No | 표시명 | 서버구분 | LOG_ID | LOG_TYPE | 비고 |
|---|---|---|---|---|---|
| 1 | 주식 종목수 | dlprem01-테스트개발 (확정 필요) | `MBSOSI_COUNT` | 수치 | 오늘 최신값 |
| 2 | 파생 종목수 | dlprem01-테스트개발 (확정 필요) | `MBFOSI_COUNT` | 수치 | 오늘 최신값 |
| 3 | 상품 종목수 | dlprem01-테스트개발 (확정 필요) | `MBCOSI_COUNT` | 수치 | 오늘 최신값 |
| 4 | 업종 종목수 | dlprem01-테스트개발 (확정 필요) | `MBJISU_COUNT` | 수치 | 오늘 최신값 |
| 5 | NXT 종목수 | dlprem01-테스트개발 (확정 필요) | `NXT_COUNT` | 수치 | 오늘 최신값 |
| 6 | 옵션결제월별 최대수 | dlprem01-테스트개발 (확정 필요) | `OPT_MAX_COUNT` | 수치 | 오늘 최신값, 수집 1건 = 값 1개 |
| 7 | 서버자동주문 계좌수 | pamoap01-자동주문 (확정 필요) | `AUTO_ORDER_ACNT` | 수치 | 오늘 최신값 |
| 8 | 시세포착1 등록수 | pqgetap1-시세포착1 (확정 필요) | `CAP_REG_COUNT` | 수치 | 오늘 최신값 |
| 9 | 시세포착2 등록수 | pqgetap2-시세포착2 (확정 필요) | `CAP2_REG_COUNT` | 수치 | 오늘 최신값 |
| 10 | 주파수클럽 사용수 | pjpsap01-주파수클럽 (확정 필요) | `FREQ_CLUB_COUNT` | 수치 | 오늘 최신값 |
| 11 | 전일 최대동시접속 | pmaster2-마스터 (확정 필요) | `MAX_CONN_PREV` | 수치 | 전일 최대값 |
| 12 | HTS 최대동시접속 | pmaster2-마스터 (확정 필요) | `HTS_MAX_CONN` | 수치 | 전일 최대값 |
| 13 | MTS 최대동시접속 | pmaster2-마스터 (확정 필요) | `MTS_MAX_CONN` | 수치 | 전일 최대값 |

#### 5번 영역 - 히스토리 그래프

| 그래프 그룹 | 표시항목 | LOG_ID | 집계방식 |
|---|---|---|---|
| 종목수 그룹 | 주식 종목수 | `MBSOSI_COUNT` | 수집 일시 전체 포인트 |
| 종목수 그룹 | 파생 종목수 | `MBFOSI_COUNT` | 수집 일시 전체 포인트 |
| 종목수 그룹 | 상품 종목수 | `MBCOSI_COUNT` | 수집 일시 전체 포인트 |
| 종목수 그룹 | 업종 종목수 | `MBJISU_COUNT` | 수집 일시 전체 포인트 |
| 종목수 그룹 | NXT 종목수 | `NXT_COUNT` | 수집 일시 전체 포인트 |
| 종목수 그룹 | 옵션결제월 최대수 | `OPT_MAX_COUNT` | 수집 일시 전체 포인트 |
| 접속자수 그룹 | 전일 최대동시접속 | `MAX_CONN_PREV` | 수집 일시 전체 포인트 |
| 접속자수 그룹 | HTS 최대동시접속 | `HTS_MAX_CONN` | 수집 일시 전체 포인트 |
| 접속자수 그룹 | MTS 최대동시접속 | `MTS_MAX_CONN` | 수집 일시 전체 포인트 |

#### 6번 영역 - 서버별 리소스

| 방식 | 내용 |
|---|---|
| 서버 목록 | TB_ANALYZE_HISTORY에서 ANALYZE_DATE=오늘 DISTINCT SERVER_ID (코드 고정 아님) |
| 표시 조건 | 해당 서버에 오늘 `DISK_HOME` LOG_ID 데이터가 존재하면 도넛차트 표시 |
| 복수 건 처리 | 오늘 `DISK_HOME` 이 여러 건이면 `LOG_TIMESTAMP` 기준 **가장 최신 1건** 사용 |
| 데이터 없음 | `DISK_HOME` 데이터가 없는 서버는 "분석 없음" 표시 |
| 표시 LOG_ID | `DISK_HOME` 고정 (모든 서버 통일) |
| 수치 단위 | % (0~100) 고정 |

> ⚠️ **DISK_HOME LOG_ID 통일 원칙**
> - axistuja-자동주문 서버의 기존 `DISK_USAGE` → `DISK_HOME` 으로 수집 스크립트 수정 필요
> - 신규 서버 추가 시 반드시 `DISK_HOME` 으로 LOG_ID 지정

### 0-5. 분석 레벨 색상 정의 (전체 화면 공통)

> ⚠️ Bootstrap 5(AdminLTE 4.x) 기준 클래스명 사용 — `badge-*` → `text-bg-*` 변경됨

| 분석 레벨 | 색상 | Bootstrap 5 클래스 | 사용 위치 |
|---|---|---|---|
| 에러 | 빨강 | `bg-danger` / `text-bg-danger` / `table-danger` | 전체 |
| 경고 | 노랑 | `bg-warning` / `text-bg-warning` / `table-warning` | 전체 |
| 정상 | 초록 | `bg-success` / `text-bg-success` / `table-success` | 전체 |
| 정보 | 하늘 | `bg-info` / `text-bg-info` | 전체 |
| 미분석 | 회색 | `bg-secondary` / `text-bg-secondary` | 전체 |

> ℹ️ Bootstrap 5: `<span class="badge text-bg-danger">` ← 이걸 사용

---

## 1. 상단 네비게이션 바

### 1-1. AdminLTE 컴포넌트
- **사용 컴포넌트**: `main-header` Navbar
- **레이아웃**: 좌측 타이틀 / 우측 시간 정보

### 1-2. 표시 항목

| 위치 | 항목 | 표시 형식 | 갱신 방식 |
|---|---|---|---|
| 좌측 | 시스템명 | 채널 서버 점검 Dashboard | 고정 |
| 우측 | 현재 날짜+시간 | YYYY/MM/DD HH:mm:ss | 1초마다 JS 갱신 |
| 우측 | 마지막 갱신 시각 | 마지막갱신: HH:mm:ss | AJAX 갱신 후 업데이트 |
| 우측 | 자동갱신 카운트다운 | 다음갱신: MM:SS | 1초마다 카운트다운 |

### 1-3. 카운트다운 동작
```
5분(300초) → 5:00 → ... → 0:00 → AJAX 갱신 실행 → 다시 5:00 시작
```

### 1-4. AdminLTE 4.x HTML 구조 (Bootstrap 5 기준)
```html
<!-- Bootstrap 4 → 5 주요 변경사항
     mr-* → me-*  (margin-end)
     ml-* → ms-*  (margin-start)
     font-weight-bold → fw-bold
     text-muted → text-body-secondary
-->
<nav class="app-header navbar navbar-expand bg-body">
  <!-- 좌측: 타이틀 -->
  <ul class="navbar-nav">
    <li class="nav-item">
      <span class="nav-link fw-bold">
        <i class="bi bi-server me-2"></i>채널 서버 점검 Dashboard
      </span>
    </li>
  </ul>
  <!-- 우측: 시간 정보 -->
  <ul class="navbar-nav ms-auto">
    <li class="nav-item">
      <span class="nav-link">
        <i class="bi bi-clock me-1"></i>
        <span id="currentTime">2026/06/02 09:00:00</span>
      </span>
    </li>
    <li class="nav-item">
      <span class="nav-link text-body-secondary">
        마지막갱신: <span id="lastUpdated">-</span>
      </span>
    </li>
    <li class="nav-item">
      <span class="nav-link text-info">
        다음갱신: <span id="countdown">05:00</span>
      </span>
    </li>
  </ul>
</nav>
```

---

## 2. 요약 현황 (가로 스트립)

### 2-1. 컴포넌트 구성 (v1.3 변경)

> ✅ 기존 카드 10개 → **Bootstrap 5 Card 1개** 안에 상단/하단 2줄로 통합

| 구역 | 위치 | 내용 | 구분선 |
|---|---|---|---|
| **상단 (분석 결과)** | 카드 위쪽 | 에러·경고·정상·정보·미분석 건수+% | 세로 구분선으로 5등분 |
| **하단 (수집/분석 현황)** | 카드 아래쪽 | 수집현황·분석현황·수집실패·수집제외·분석실패 | 세로 구분선으로 5등분, 상단과 가는 가로선으로 분리 |

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  에러 3 (14%)  │  경고 2 (9%)  │  정상 12 (57%)  │  정보 3 (14%)  │  미분석 1 (5%)  │
│────────────────────────────────────────────────────────────────────────────────│  ← 얇은 구분선
│  수집 5/6  ▓▓░  │ 분석 5/6 ▓▓░  │ 수집실패 FAIL 1 │  수집제외 SKIP 0 │  분석실패 0  │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

### 2-2. 상단 — 분석 결과 (5칸)

- **레이아웃**: `d-flex` 5등분, 세로 구분선(`border-end`)으로 분리
- **각 칸 구성**: 레벨 배지 + 건수(큰 숫자) + 비율(%)
- **색상 강조**: 건수 숫자에 레벨 색상 적용

| 칸 | 배지 | 건수 색상 | 데이터 출처 |
|---|---|---|---|
| 에러 | `text-bg-danger` | `text-danger` | TB_ANALYZE_RESULT |
| 경고 | `text-bg-warning` | `text-warning` (어두운 톤) | TB_ANALYZE_RESULT |
| 정상 | `text-bg-success` | `text-success` | TB_ANALYZE_RESULT |
| 정보 | `text-bg-info` | `text-info` (어두운 톤) | TB_ANALYZE_RESULT |
| 미분석 | `text-bg-secondary` | `text-secondary` | TB_ANALYZE_RESULT |

> ⚠️ 미분석 1건 이상이면 해당 칸 하단에 "정책 파일 업데이트 필요" 경고 문구 표시

---

### 2-3. 하단 — 수집/분석 현황 (5칸)

- **레이아웃**: 상단과 동일하게 5등분, `border-top`으로 상단과 구분
- **수집/분석 현황**: 아이콘 + `완료N/전체N` + 미니 진행률 바 (`height: 3px`)
- **실패/제외**: 0건이면 초록 체크 + "이상 없음", 1건 이상이면 건수 + 빨간 강조

| 칸 | 내용 | 색상 | 아이콘 | 데이터 출처 |
|---|---|---|---|---|
| 수집 현황 | 완료N/전체N + 진행률 바 | `#0dcaf0` (하늘) | `bi-download` | TB_COLLECT_HISTORY |
| 분석 현황 | 완료N/전체N + 진행률 바 | `#198754` (초록) | `bi-bar-chart-line` | TB_ANALYZE_HISTORY |
| 수집 실패(FAIL) | N건 또는 이상없음 | `#dc3545` / 초록 | `bi-exclamation-circle` | TB_COLLECT_HISTORY |
| 수집 제외(SKIP) | N건 또는 이상없음 | `#6c757d` / 초록 | `bi-slash-circle` | TB_COLLECT_HISTORY |
| 분석 실패 | N건 또는 이상없음 | `#ffc107` / 초록 | `bi-exclamation-triangle` | TB_ANALYZE_HISTORY |

> ℹ️ **FAIL vs SKIP 차이**
> - `FAIL`: 수집 시도했으나 실패 (재시도 3회 모두 실패) → 운영자 즉시 확인 필요
> - `SKIP`: 파일 사이즈 초과로 수집 자체를 건너뜀 → TB_COLLECT_EXCLUDE에 영구 제외 등록됨

---

### 2-4. HTML 구조 (Bootstrap 5)

```html
<div class="card mb-3">
  <div class="card-body p-0">

    <!-- 상단: 분석 결과 5칸 -->
    <div class="d-flex">

      <!-- 에러 칸 -->
      <div class="flex-fill p-3 border-end text-center">
        <span class="badge text-bg-danger mb-1">에러</span>
        <div class="fw-bold text-danger" style="font-size:24px;"
             th:text="${summary.errorCnt}">3</div>
        <div class="text-body-secondary" style="font-size:11px;">
          <span th:text="${summary.errorRatio}">14.3</span>%
        </div>
      </div>

      <!-- 경고 칸 -->
      <div class="flex-fill p-3 border-end text-center">
        <span class="badge text-bg-warning mb-1">경고</span>
        <div class="fw-bold" style="font-size:24px; color:#856404;"
             th:text="${summary.warnCnt}">2</div>
        <div class="text-body-secondary" style="font-size:11px;">
          <span th:text="${summary.warnRatio}">9.5</span>%
        </div>
      </div>

      <!-- 정상 칸 -->
      <div class="flex-fill p-3 border-end text-center">
        <span class="badge text-bg-success mb-1">정상</span>
        <div class="fw-bold text-success" style="font-size:24px;"
             th:text="${summary.normalCnt}">12</div>
        <div class="text-body-secondary" style="font-size:11px;">
          <span th:text="${summary.normalRatio}">57.1</span>%
        </div>
      </div>

      <!-- 정보 칸 -->
      <div class="flex-fill p-3 border-end text-center">
        <span class="badge text-bg-info mb-1">정보</span>
        <div class="fw-bold" style="font-size:24px; color:#055160;"
             th:text="${summary.infoCnt}">3</div>
        <div class="text-body-secondary" style="font-size:11px;">
          <span th:text="${summary.infoRatio}">14.3</span>%
        </div>
      </div>

      <!-- 미분석 칸 -->
      <div class="flex-fill p-3 text-center">
        <span class="badge text-bg-secondary mb-1">미분석</span>
        <div class="fw-bold text-secondary" style="font-size:24px;"
             th:text="${summary.unknownCnt}">1</div>
        <div class="text-body-secondary" style="font-size:11px;">
          <span th:text="${summary.unknownRatio}">4.8</span>%
        </div>
        <!-- 미분석 1건 이상 경고 문구 -->
        <div th:if="${summary.unknownCnt > 0}"
             class="text-danger mt-1" style="font-size:10px;">
          정책 파일 업데이트 필요
        </div>
      </div>

    </div><!-- /상단 -->

    <!-- 구분선 -->
    <hr class="my-0">

    <!-- 하단: 수집/분석 현황 5칸 -->
    <div class="d-flex">

      <!-- 수집 현황 -->
      <div class="flex-fill p-3 border-end">
        <div class="d-flex align-items-center gap-2 mb-1">
          <i class="bi bi-download text-info" style="font-size:14px;"></i>
          <span class="text-body-secondary" style="font-size:11px;">수집 현황</span>
        </div>
        <div class="d-flex align-items-baseline gap-1">
          <span class="fw-bold text-info" style="font-size:16px;"
                th:text="${summary.collectSuccess}">5</span>
          <span class="text-body-secondary" style="font-size:12px;">
            / <span th:text="${summary.collectTotal}">6</span>
          </span>
        </div>
        <div class="progress mt-2" style="height:3px;">
          <div class="progress-bar bg-info"
               th:style="'width:' + ${summary.collectRatio} + '%'"></div>
        </div>
      </div>

      <!-- 분석 현황 -->
      <div class="flex-fill p-3 border-end">
        <div class="d-flex align-items-center gap-2 mb-1">
          <i class="bi bi-bar-chart-line text-success" style="font-size:14px;"></i>
          <span class="text-body-secondary" style="font-size:11px;">분석 현황</span>
        </div>
        <div class="d-flex align-items-baseline gap-1">
          <span class="fw-bold text-success" style="font-size:16px;"
                th:text="${summary.analyzeSuccess}">5</span>
          <span class="text-body-secondary" style="font-size:12px;">
            / <span th:text="${summary.analyzeTotal}">6</span>
          </span>
        </div>
        <div class="progress mt-2" style="height:3px;">
          <div class="progress-bar bg-success"
               th:style="'width:' + ${summary.analyzeRatio} + '%'"></div>
        </div>
      </div>

      <!-- 수집 실패 FAIL -->
      <div class="flex-fill p-3 border-end">
        <div class="d-flex align-items-center gap-2 mb-1">
          <i class="bi bi-exclamation-circle text-danger" style="font-size:14px;"></i>
          <span class="text-body-secondary" style="font-size:11px;">수집 실패 FAIL</span>
        </div>
        <!-- 0건일 때 -->
        <div th:if="${summary.failCnt == 0}"
             class="d-flex align-items-center gap-1 text-success" style="font-size:12px;">
          <i class="bi bi-check-circle"></i> 이상 없음
        </div>
        <!-- 1건 이상일 때 -->
        <div th:if="${summary.failCnt > 0}"
             class="fw-bold text-danger" style="font-size:16px;"
             th:text="${summary.failCnt} + '건'">1건</div>
      </div>

      <!-- 수집 제외 SKIP -->
      <div class="flex-fill p-3 border-end">
        <div class="d-flex align-items-center gap-2 mb-1">
          <i class="bi bi-slash-circle text-secondary" style="font-size:14px;"></i>
          <span class="text-body-secondary" style="font-size:11px;">수집 제외 SKIP</span>
        </div>
        <div th:if="${summary.skipCnt == 0}"
             class="d-flex align-items-center gap-1 text-success" style="font-size:12px;">
          <i class="bi bi-check-circle"></i> 이상 없음
        </div>
        <div th:if="${summary.skipCnt > 0}"
             class="fw-bold text-secondary" style="font-size:16px;"
             th:text="${summary.skipCnt} + '건'">0건</div>
      </div>

      <!-- 분석 실패 -->
      <div class="flex-fill p-3">
        <div class="d-flex align-items-center gap-2 mb-1">
          <i class="bi bi-exclamation-triangle" style="font-size:14px; color:#856404;"></i>
          <span class="text-body-secondary" style="font-size:11px;">분석 실패</span>
        </div>
        <div th:if="${summary.analyzeFail == 0}"
             class="d-flex align-items-center gap-1 text-success" style="font-size:12px;">
          <i class="bi bi-check-circle"></i> 이상 없음
        </div>
        <div th:if="${summary.analyzeFail > 0}"
             class="fw-bold" style="font-size:16px; color:#856404;"
             th:text="${summary.analyzeFail} + '건'">0건</div>
      </div>

    </div><!-- /하단 -->

  </div><!-- /card-body -->
</div><!-- /card -->
```

### 2-5. 조회 SQL

```sql
-- ============================================================
-- 수집 현황 조회
-- 전체 수집 서버수(TOTAL)는 스케쥴 conf 기준(0-3 참고)으로 별도 산정
-- COLLECT_STATUS 허용값: 'SUCCESS' / 'FAIL' / 'SKIP'
-- FAIL: 수집 시도했으나 실패 (재시도 3회 모두 실패)
-- SKIP: 파일 사이즈 초과로 수집 제외 처리됨
-- ============================================================
SELECT
    COUNT(DISTINCT CASE WHEN COLLECT_STATUS = 'SUCCESS' THEN SERVER_ID END) AS SUCCESS_SERVER,
    SUM(CASE WHEN COLLECT_STATUS = 'FAIL' THEN 1 ELSE 0 END)                AS FAIL_CNT,
    SUM(CASE WHEN COLLECT_STATUS = 'SKIP' THEN 1 ELSE 0 END)                AS SKIP_CNT
FROM TB_COLLECT_HISTORY
WHERE COLLECT_DATE = #{today};

-- ============================================================
-- 분석 현황 조회
-- 전체 분석 서버수(TOTAL)는 스케쥴 conf 기준(0-3 참고)으로 별도 산정
-- ANALYZE_DATE = 오늘 (분석 서버가 오늘 실행된 이력 기준)
-- ANALYZE_TARGET_DATE(분석 대상 날짜)와 다를 수 있음 — ANALYZE_DATE 기준 사용
-- ============================================================
SELECT
    COUNT(DISTINCT CASE WHEN ANALYZE_STATUS = 'SUCCESS' THEN SERVER_ID END) AS SUCCESS_SERVER,
    SUM(CASE WHEN ANALYZE_STATUS = 'FAIL' THEN 1 ELSE 0 END)                AS FAIL_CNT
FROM TB_ANALYZE_HISTORY
WHERE ANALYZE_DATE = #{today};

-- ============================================================
-- 분석 결과 레벨별 카운트
-- Altibase 구버전에서 SUM(...) OVER() 윈도우 함수 미지원
-- → 서브쿼리 방식으로 대체
-- ============================================================
SELECT
    A.ANALYZE_LEVEL,
    A.CNT,
    ROUND(A.CNT * 100.0 / B.TOTAL_CNT, 1) AS RATIO
FROM (
    SELECT ANALYZE_LEVEL, COUNT(*) AS CNT
    FROM TB_ANALYZE_RESULT
    WHERE ANALYZE_DATE = #{today}
    GROUP BY ANALYZE_LEVEL
) A,
(
    SELECT COUNT(*) AS TOTAL_CNT
    FROM TB_ANALYZE_RESULT
    WHERE ANALYZE_DATE = #{today}
) B;
```

---

## 3. 정보성 중요 데이터

### 3-1. 컴포넌트 구성 (v1.3 변경)

> ✅ 기존 info-box 13개 → **Bootstrap 5 Card 3개** (그룹별 리스트 형태)

| 카드 | 그룹명 | 항목 수 | 레이아웃 |
|---|---|---|---|
| 카드 1 | 종목 현황 | 6개 | `col-lg-4` |
| 카드 2 | 서비스 현황 | 4개 | `col-lg-4` |
| 카드 3 | 접속자 현황 (전일 기준) | 3개 | `col-lg-4` |

```
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ 종목 현황        │ │ 서비스 현황       │ │ 접속자 현황      │
│─────────────────│ │─────────────────│ │─────────────────│
│ ● 주식종목수     │ │ ● 자동주문계좌수  │ │ ● 전일최대접속   │
│   1,234,567 09:30│ │   2,341    09:30│ │   18,432  전일  │
│ ● 파생종목수     │ │ ● 시세포착1등록수 │ │ ● HTS 최대접속  │
│   89,432    09:30│ │   1,872    09:30│ │   12,104  전일  │
│ ● 상품종목수     │ │ ● 시세포착2등록수 │ │ ● MTS 최대접속  │
│   12,810    09:30│ │   1,650    09:30│ │   6,328   전일  │
│ ... (6개)       │ │ ● 주파수클럽사용수│ └─────────────────┘
└─────────────────┘ │   312      09:30│
                    └─────────────────┘
```

---

### 3-2. 각 카드 항목 구성

**카드 1 — 종목 현황 (6개)**

| 항목명 | 서버구분 | LOG_ID | 비고 |
|---|---|---|---|
| 주식 종목수 | (확정 필요) | MBSOSI_COUNT | 오늘 최신값 |
| 파생 종목수 | (확정 필요) | MBFOSI_COUNT | 오늘 최신값 |
| 상품 종목수 | (확정 필요) | MBCOSI_COUNT | 오늘 최신값 |
| 업종 종목수 | (확정 필요) | MBJISU_COUNT | 오늘 최신값 |
| NXT 종목수 | (확정 필요) | NXT_COUNT | 오늘 최신값 |
| 옵션결제월별 최대수 | (확정 필요) | OPT_MAX_COUNT | 오늘 최신값 |

**카드 2 — 서비스 현황 (4개)**

| 항목명 | 서버구분 | LOG_ID | 비고 |
|---|---|---|---|
| 서버자동주문 계좌수 | (확정 필요) | AUTO_ORDER_ACNT | 오늘 최신값 |
| 시세포착1 등록수 | (확정 필요) | CAP_REG_COUNT | 오늘 최신값 |
| 시세포착2 등록수 | (확정 필요) | CAP2_REG_COUNT | 오늘 최신값 |
| 주파수클럽 사용수 | (확정 필요) | FREQ_CLUB_COUNT | 오늘 최신값 |

**카드 3 — 접속자 현황 (3개, 전일 기준)**

| 항목명 | 서버구분 | LOG_ID | 비고 |
|---|---|---|---|
| 전일 최대동시접속 | pmaster2-마스터 | MAX_CONN_PREV | 전일 최대값 |
| HTS 최대동시접속 | pmaster2-마스터 | HTS_MAX_CONN | 전일 최대값 |
| MTS 최대동시접속 | pmaster2-마스터 | MTS_MAX_CONN | 전일 최대값 |

---

### 3-3. 각 행(row) 표시 규칙

| 항목 | 표시 내용 | 비고 |
|---|---|---|
| 색상 점(dot) | 항목별 고정 색상 원형 아이콘 (`8px`) | 항목마다 다른 색상으로 시각 구분 |
| 항목명 | 한국어 항목명 | `text-body-secondary`, `font-size: 12px` |
| 수치 | LOG_VALUE (천단위 콤마) | `fw-bold`, `font-size: 13px` |
| 수집 시각 | LOG_TIMESTAMP 기준 | 오늘 → `HH:mm`, 아니면 → `MM/DD HH:mm` |
| 값 없을 때 | `-` 표시 | `text-body-secondary` |

---

### 3-4. HTML 구조 (Bootstrap 5)

```html
<div class="row g-3 mb-3">

  <!-- 카드 1: 종목 현황 -->
  <div class="col-lg-4">
    <div class="card h-100">
      <div class="card-header py-2">
        <span class="fw-bold" style="font-size:13px;">종목 현황</span>
      </div>
      <div class="card-body p-0">
        <ul class="list-group list-group-flush">

          <!-- 항목 행: 주식 종목수 -->
          <li class="list-group-item d-flex align-items-center py-2 px-3 gap-2">
            <span style="width:8px;height:8px;border-radius:50%;
                         background:#0d6efd;flex-shrink:0;"></span>
            <span class="text-body-secondary flex-fill" style="font-size:12px;">
              주식 종목수
            </span>
            <span class="fw-bold" style="font-size:13px;"
                  th:text="${stockCount ?: '-'}">1,234,567</span>
            <span class="text-body-secondary ms-2" style="font-size:11px;min-width:48px;text-align:right;"
                  th:text="${stockTime}">09:30</span>
          </li>

          <!-- 나머지 항목도 동일한 패턴 반복 -->
          <li class="list-group-item d-flex align-items-center py-2 px-3 gap-2">
            <span style="width:8px;height:8px;border-radius:50%;
                         background:#fd7e14;flex-shrink:0;"></span>
            <span class="text-body-secondary flex-fill" style="font-size:12px;">파생 종목수</span>
            <span class="fw-bold" style="font-size:13px;"
                  th:text="${derivCount ?: '-'}">89,432</span>
            <span class="text-body-secondary ms-2" style="font-size:11px;min-width:48px;text-align:right;"
                  th:text="${derivTime}">09:30</span>
          </li>

          <!-- ... 상품/업종/NXT/옵션 항목 동일 패턴 ... -->

        </ul>
      </div>
    </div>
  </div>

  <!-- 카드 2: 서비스 현황 (동일 패턴) -->
  <div class="col-lg-4">
    <div class="card h-100">
      <div class="card-header py-2">
        <span class="fw-bold" style="font-size:13px;">서비스 현황</span>
      </div>
      <div class="card-body p-0">
        <ul class="list-group list-group-flush">
          <!-- 자동주문/시세포착1/시세포착2/주파수클럽 동일 패턴 -->
        </ul>
      </div>
    </div>
  </div>

  <!-- 카드 3: 접속자 현황 (동일 패턴) -->
  <div class="col-lg-4">
    <div class="card h-100">
      <div class="card-header py-2">
        <span class="fw-bold" style="font-size:13px;">접속자 현황
          <small class="text-body-secondary fw-normal ms-1" style="font-size:11px;">전일 기준</small>
        </span>
      </div>
      <div class="card-body p-0">
        <ul class="list-group list-group-flush">
          <!-- 전일/HTS/MTS 동일 패턴 -->
        </ul>
      </div>
    </div>
  </div>

</div>
```

### 3-5. 조회 SQL

```sql
-- ============================================================
-- 오늘 특정 서버+LOG_ID 최신값 조회 (Altibase/PostgreSQL 공통 호환)
-- LOG_TIMESTAMP 기준 최신 1건
-- ============================================================
SELECT LOG_VALUE, LOG_CONTENT, LOG_TIMESTAMP
FROM (
    SELECT LOG_VALUE, LOG_CONTENT, LOG_TIMESTAMP
    FROM TB_ANALYZE_RESULT
    WHERE ANALYZE_DATE = #{today}
      AND SERVER_ID    = #{serverId}
      AND LOG_ID       = #{logId}
    ORDER BY LOG_TIMESTAMP DESC
)
WHERE ROWNUM = 1;
-- ※ PostgreSQL: ORDER BY LOG_TIMESTAMP DESC LIMIT 1 사용 가능

-- ============================================================
-- 수집 시각 표시 로직 (Java/Thymeleaf 처리)
-- LOG_TIMESTAMP 날짜가 오늘이면 → "HH:mm"
-- LOG_TIMESTAMP 날짜가 오늘이 아니면 → "MM/DD HH:mm"
-- ============================================================
```

---

## 4. 서버별 에러/경고 상세 내역

### 4-1. AdminLTE 컴포넌트
- **사용 컴포넌트**: `card` + `nav-tabs` + `table`
- **레이아웃**: 전체 너비 (col-12)

### 4-2. 필터 구성

| 필터 | 타입 | 옵션 |
|---|---|---|
| 서버 선택 | 드롭다운 | 전체 / 서버별 목록 (TB_ANALYZE_RESULT DISTINCT SERVER_ID) |
| 레벨 탭 | 탭 | [에러/경고] [정상/정보/미분석] |

### 4-2-1. 페이징 정책

| 항목 | 내용 |
|---|---|
| 페이지당 표시 건수 | 10건 고정 |
| 페이지 표시 방식 | AdminLTE `pagination` 컴포넌트 사용 |
| 페이지 번호 표시 | 이전(«) · 1 · 2 · 3 · ... · 다음(») |
| 총 건수 표시 | 테이블 상단 우측에 "총 N건 (N페이지 중 M페이지)" 표시 |
| 서버 필터 변경 시 | 1페이지로 초기화 |
| 탭 전환 시 | 1페이지로 초기화 |
| 자동 갱신(AJAX) 시 | 1페이지로 초기화 |

### 4-3. 테이블 컬럼 정의

| 컬럼명 | DB 컬럼 | 표시 형식 | 비고 |
|---|---|---|---|
| 시각 | LOG_TIMESTAMP | HH:mm:ss | |
| 서버 | SERVER_ID | 텍스트 (굵게) | SERVER_IP를 바로 아래 소문자 회색으로 표시 |
| 서버 IP | SERVER_IP | 소문자 회색 텍스트 | 서버 컬럼 셀 내 2행 표시 (별도 컬럼 아님) |
| LOG_ID | LOG_ID | `<code>` 태그 | 클릭 시 accordion |
| 입력타입 | LOG_TYPE | 배지(badge) | 색상 구분 |
| 레벨 | ANALYZE_LEVEL | 배지(badge) | 색상 구분 |
| 분석 메시지 | ANALYZE_MESSAGE | 텍스트 | |
| 임계치 정보 | THRESHOLD_VALUE + THRESHOLD_OPERATOR | 수치형만 표시 | |
| 원본 로그 | COLLECT_LOG_ID | `[보기]` 버튼 | 클릭 시 TB_COLLECT_LOG 원본 내용 모달 팝업 표시 |

> 서버 컬럼 표시 형식 예시:
> ```
> dlprem01-테스트개발
> 192.168.210.121
> ```

### 4-3-1. 원본 로그 [보기] 모달 정의

`[보기]` 버튼 클릭 시 `COLLECT_LOG_ID`로 TB_COLLECT_LOG를 조회하여 모달 팝업 표시

#### 모달 표시 항목

| 항목명 | DB 컬럼 | 표시 형식 |
|---|---|---|
| 수집 로그 ID | COLLECT_LOG_ID | 숫자 |
| 수집 일시 | COLLECT_DATETIME | YYYY/MM/DD HH:mm:ss |
| 로그 원본 시각 | LOG_TIMESTAMP | YYYY/MM/DD HH:mm:ss.SSS |
| 수집 파일 경로 | SOURCE_FILE_PATH | 텍스트 |
| 원본 로그 전문 | RAW_LOG | `<pre>` 태그로 그대로 출력 |

#### 조회 SQL

```sql
-- 원본 로그 모달 조회 (Altibase/PostgreSQL 공통 호환)
SELECT
    COLLECT_LOG_ID,
    COLLECT_DATETIME,
    LOG_TIMESTAMP,
    SOURCE_FILE_PATH,
    RAW_LOG
FROM TB_COLLECT_LOG
WHERE COLLECT_LOG_ID = #{collectLogId};
```

### 4-4. 임계치 대비 % 계산 (수치형 경고만)

```
임계치 대비 % = |LOG_VALUE - THRESHOLD_VALUE| / THRESHOLD_VALUE * 100
예) 임계치 90, 현재값 85 → (90-85)/90*100 = 5.6% 근접 (20%)
-> 20%는 WARNING_RATIO
```

### 4-5. Accordion (LOG_CONTENT 펼침)

#### 수치 타입 예제

```
[행 클릭 전]
─────────────────────────────────────────────────
09:10:01 | dlprem01-테스트개발 | DISK_HOME | 수치 | 에러 | home디스크 95 < 80 | 80

[행 클릭 후 - 아래로 펼쳐짐]
─────────────────────────────────────────────────
09:10:01 | dlprem01-테스트개발 | DISK_HOME | 수치 | 에러 | home디스크 95 < 80 | 80
  ↓ 펼쳐짐
  📄 LOG_CONTENT: dlprem01-테스트개발 - DISK_HOME
  📊 수집값: 95 / 임계치: 80 / 연산자: <
  🕐 원본 수집시각: 2026/05/29 09:10:01.123
─────────────────────────────────────────────────

[행 클릭 전]
─────────────────────────────────────────────────
09:10:01 | dlprem01-테스트개발 | DISK_HOME | 수치 | 경고 | home디스크 75 < 80 | 80

[행 클릭 후 - 아래로 펼쳐짐]
─────────────────────────────────────────────────
09:10:01 | dlprem01-테스트개발 | DISK_HOME | 수치 | 경고 | home디스크 75 < 80 | 80
  ↓ 펼쳐짐
  📄 LOG_CONTENT: dlprem01-테스트개발 - DISK_HOME
  📊 수집값: 75 / 임계치: 80 / 연산자: < / 임계치 대비 6.25% 근접 (WARNING_RATIO : 20%)
  🕐 원본 수집시각: 2026/05/29 09:10:01.123
─────────────────────────────────────────────────

```

#### 문구 타입 예제

```
[행 클릭 전]
─────────────────────────────────────────────────
09:15:22 | pwwfep01-운영 | SVC_STATUS | 문구 | 에러 | connection fail(키워드: fail)

[행 클릭 후 - 아래로 펼쳐짐]
─────────────────────────────────────────────────
09:15:22 | pwwfep01-운영 | SVC_STATUS | 문구 | 에러 | connection fail(키워드: fail)
  ↓ 펼쳐짐
  📄 LOG_CONTENT: pwwfep01-운영 - SVC_STATUS
  📝 수집값: connection fail(키워드: fail)
  🕐 원본 수집시각: 2026/05/29 09:15:22.456
─────────────────────────────────────────────────
```

#### 존재 타입 예제

```
[행 클릭 전]
─────────────────────────────────────────────────
09:20:05 | pamoap01-운영 | LOCK_FILE | 존재 | 에러 | MBSOSI.txt 없음

[행 클릭 후 - 아래로 펼쳐짐]
─────────────────────────────────────────────────
09:20:05 | pamoap01-운영 | LOCK_FILE | 존재 | 에러 | MBSOSI.txt 없음
  ↓ 펼쳐짐
  📄 LOG_CONTENT: pamoap01-운영 - LOCK_FILE
  🔍 수집값: MBSOSI.txt 없음
  🕐 원본 수집시각: 2026/05/29 09:20:05.789
─────────────────────────────────────────────────
```

#### 날짜 타입 예제

```
[행 클릭 전]
─────────────────────────────────────────────────
09:25:33 | pmaster2-운영 | CERT_EXPIRE | 날짜 | 에러 | 인증서 날짜 2026/06/01

[행 클릭 후 - 아래로 펼쳐짐]
─────────────────────────────────────────────────
09:25:33 | pmaster2-운영 | CERT_EXPIRE | 날짜 | 에러 | 인증서 날짜 2026/06/01
  ↓ 펼쳐짐
  📄 LOG_CONTENT: pmaster2-운영 - CERT_EXPIRE
  📅 수집값: 인증서 날짜 2026/06/01 (오늘 날짜: 2026/06/02)
  🕐 원본 수집시각: 2026/05/29 09:25:33.012
─────────────────────────────────────────────────
```

#### 정보 타입 예제

```
[행 클릭 전]
─────────────────────────────────────────────────
09:30:11 | pwwfep01-운영 | MBSOSI_COUNT | 정보 | 정보 | 주식 종목수 1,234,567

[행 클릭 후 - 아래로 펼쳐짐]
─────────────────────────────────────────────────
09:30:11 | pwwfep01-운영 | MBSOSI_COUNT | 정보 | 정보 | 주식 종목수 1,234,567
  ↓ 펼쳐짐
  📄 LOG_CONTENT: pwwfep01-운영 - MBSOSI_COUNT
  ℹ️ 수집값: 주식 종목수 1,234,567
  🕐 원본 수집시각: 2026/05/29 09:30:11.234
─────────────────────────────────────────────────
```

#### 미분석 타입 예제

```
[행 클릭 전]
─────────────────────────────────────────────────
09:35:44 | dlprem01-테스트개발 | NEW_LOG_ID | 수치 | 미분석 | [미분석][NEW_LOG_ID] 분석 정책 미등록

[행 클릭 후 - 아래로 펼쳐짐]
─────────────────────────────────────────────────
09:35:44 | dlprem01-테스트개발 | NEW_LOG_ID | 수치 | 미분석 | [미분석][NEW_LOG_ID] 분석 정책 미등록
  ↓ 펼쳐짐
  📄 LOG_CONTENT: dlprem01-테스트개발 - NEW_LOG_ID
  ⚠️ 분석 정책 파일에 등록되지 않은 LOG_ID - 운영자 확인 및 정책 파일 업데이트 필요
  🕐 원본 수집시각: 2026/05/29 09:35:44.567
─────────────────────────────────────────────────
```

> ⚠️ 미분석 행은 `table-secondary` (회색) 배경으로 표시하여 시각적으로 구분

### 4-6. 입력타입별 배지 색상

| 입력타입 | AdminLTE 클래스 |
|---|---|
| 수치 | `badge-primary` (파랑) |
| 문구 | `badge-success` (초록) |
| 존재 | `badge-danger` (빨강) |
| 날짜 | `badge-warning` (노랑) |
| 정보 | `badge-info` (하늘) |

### 4-7. 입력타입(LOG_TYPE) 정의 및 표시 규칙

| 입력타입 | 정의 | 분석 방식 | 임계치 정보 표시 | 임계치 대비% 표시 |
|---|---|---|---|---|
| 수치 | 숫자(%, 건수, 개수 등) 형태의 수집값 | THRESHOLD_VALUE · THRESHOLD_OPERATOR로 수치 비교 | O | O (경고 시만) |
| 문구 | 텍스트·문자열 형태의 수집값 | 에러 키워드 포함 여부 비교 (콤마 구분 키워드 목록) | O (에러 키워드 목록) | X |
| 존재 | 감시 대상(파일·프로세스·서비스 등)의 부존재 감지 | 로그 발생 자체가 에러 (항상 에러 고정) | X | X |
| 날짜 | 날짜 형태의 수집값 | 로그 내 yyyy/MM/dd 날짜가 오늘과 일치하면 정상, 다르면 에러 | X | X |
| 정보 | 에러/경고 판단 없이 참고용으로만 수집되는 값 | 분석 없음 (ANALYZE_LEVEL 항상 '정보' 고정) | X | X |

#### 입력타입별 상세 정의

**수치**
- LOG_VALUE가 숫자(정수/실수)로 저장됨
- THRESHOLD_OPERATOR(`<`, `>`, `<=`, `>=`, `=`)와 THRESHOLD_VALUE를 사용해 에러/경고 판별
- 임계치 대비%는 수치형 경고 건에만 계산·표시 (accordion 상세에 표시)

**문구**
- LOG_CONTENT가 문자열 형태로 저장됨
- 정책 파일에 정의된 에러 키워드(콤마 구분) 중 하나라도 포함되면 에러, 없으면 정상
- 임계치 정보 컬럼에는 매칭된 에러 키워드를 표시
- 수치 비교가 없으므로 임계치 대비% 미표시

**존재**
- 감시 대상(파일·프로세스 등)이 **없을 때만** 수집 로그가 발생하는 구조
- 로그가 수집된다는 것 자체가 감시 대상의 부존재를 의미 → **항상 에러 고정**
- 임계치 비교 없으므로 임계치 정보·임계치 대비% 모두 미표시

**날짜**
- LOG_CONTENT에 포함된 yyyy/MM/dd 형식 날짜가 오늘 날짜와 일치하면 정상, 다르면 에러
- 임계치 비교 없으므로 임계치 정보·임계치 대비% 모두 미표시

**정보**
- 에러/경고 판단 없이 참고용(모니터링·수집 기록)으로만 수집되는 데이터
- ANALYZE_LEVEL이 항상 '정보'로 고정되며 임계치 비교를 수행하지 않음
- 임계치 정보·임계치 대비% 모두 미표시

### 4-8. 조회 SQL

```sql
-- 파라미터: #{startRow} = (pageNo-1)*10 + 1, #{endRow} = pageNo*10

-- 에러/경고 목록 (페이징 포함 - Altibase/PostgreSQL 공통 호환 ROWNUM 방식)
SELECT *
FROM (
    SELECT ROWNUM AS RN, T.*
    FROM (
        SELECT
            ANALYZE_RESULT_ID,
            SERVER_ID,
            SERVER_IP,
            LOG_ID,
            LOG_TYPE,
            LOG_TIMESTAMP,
            ANALYZE_LEVEL,
            ANALYZE_MESSAGE,
            THRESHOLD_VALUE,
            THRESHOLD_OPERATOR,
            WARNING_RATIO,
            NOTIFY_YN,
            COLLECT_LOG_ID
        FROM TB_ANALYZE_RESULT
        WHERE ANALYZE_DATE  = #{today}
          AND ANALYZE_LEVEL IN ('에러', '경고')
          AND SERVER_ID     = #{serverId}   -- '전체' 선택 시 조건 제거
        ORDER BY
            CASE ANALYZE_LEVEL WHEN '에러' THEN 1 WHEN '경고' THEN 2 END,
            LOG_TIMESTAMP DESC
    ) T
    WHERE ROWNUM <= #{endRow}
)
WHERE RN >= #{startRow};

-- 에러/경고 총 건수 (페이지 계산용)
SELECT COUNT(*)
FROM TB_ANALYZE_RESULT
WHERE ANALYZE_DATE  = #{today}
  AND ANALYZE_LEVEL IN ('에러', '경고')
  AND SERVER_ID     = #{serverId};

-- 정상/정보/미분석 목록 (페이징 포함 - Altibase/PostgreSQL 공통 호환 ROWNUM 방식)
SELECT *
FROM (
    SELECT ROWNUM AS RN, T.*
    FROM (
        SELECT
            ANALYZE_RESULT_ID,
            SERVER_ID,
            SERVER_IP,
            LOG_ID,
            LOG_TYPE,
            LOG_TIMESTAMP,
            ANALYZE_LEVEL,
            ANALYZE_MESSAGE,
            THRESHOLD_VALUE,
            THRESHOLD_OPERATOR,
            WARNING_RATIO,
            NOTIFY_YN,
            COLLECT_LOG_ID
        FROM TB_ANALYZE_RESULT
        WHERE ANALYZE_DATE  = #{today}
          AND ANALYZE_LEVEL IN ('정상', '정보', '미분석')
          AND SERVER_ID     = #{serverId}
        ORDER BY LOG_TIMESTAMP DESC
    ) T
    WHERE ROWNUM <= #{endRow}
)
WHERE RN >= #{startRow};

-- 정상/정보/미분석 총 건수 (페이지 계산용)
SELECT COUNT(*)
FROM TB_ANALYZE_RESULT
WHERE ANALYZE_DATE  = #{today}
  AND ANALYZE_LEVEL IN ('정상', '정보', '미분석')
  AND SERVER_ID     = #{serverId};
```

### 4-9. NOTIFY_YN 통보 상태 시각화

#### 표시 위치
- 레벨 탭이 [에러/경고]일 때만 표시 (통보 대상은 에러/경고만 해당)
- `레벨` 배지 옆에 아이콘으로 표시

#### 표시 규칙

| NOTIFY_YN | 표시 아이콘 | 색상 | 툴팁 |
|---|---|---|---|
| N (미통보) | `fa-bell` (종 아이콘) | `text-danger` (빨강) | "SMS 미통보" |
| Y (통보완료) | `fa-check-circle` | `text-muted` (회색) | "SMS 통보완료" |

#### 화면 예시

```
[레벨 탭: 에러/경고]
─────────────────────────────────────────────────────────────────────
시각     | 서버           | LOG_ID    | 타입 | 레벨          | 분석 메시지       | 임계치 | 원본
─────────────────────────────────────────────────────────────────────
09:10:01 | dlprem01-테스트개발 | DISK_HOME | 수치 | [에러] 🔔     | home디스크 95 < 80 | 80 <  | [보기]
         | 192.168.210.121   |            |      |               |                    |       |
09:15:22 | pwwfep01-운영      | SVC_STATUS | 문구 | [에러] ✔(회색)| connection fail    |       | [보기]
         | 192.168.210.201   |            |      |               |                    |       |
─────────────────────────────────────────────────────────────────────
🔔 = NOTIFY_YN='N' (미통보, 빨간색)    ✔ = NOTIFY_YN='Y' (통보완료, 회색)
```

#### 조회 시 NOTIFY_YN 포함 처리
- `SELECT` 시 `NOTIFY_YN` 컬럼 반드시 포함 (4-8 SQL에 포함됨)
- 레벨 탭이 [정상/정보]이면 NOTIFY_YN 아이콘 미표시

---

## 5. 히스토리 그래프

### 5-1. AdminLTE 컴포넌트
- **사용 컴포넌트**: `card` + `Chart.js Line Chart`
- **레이아웃**: 전체 너비 (col-12)
- **그래프 전환**: 탭으로 그룹 전환 (종목수 / 접속자수)

### 5-2. X축 / Y축 정의

| 항목 | 내용 |
|---|---|
| X축 | 수집 일시 전체 포인트 (LOG_TIMESTAMP, 최대 1주일) |
| X축 표시 형식 | MM/DD HH:mm |
| Y축 | 수치값 (LOG_VALUE) |
| 조회 범위 | 오늘로부터 7일 전 ~ 오늘 |
| 데이터 포인트 | 모든 수집 건 표시 (집계 없음) |

### 5-3. 그래프 그룹 구성

**탭 1: 종목수 현황**

| 항목명 | LOG_ID | 선 색상 |
|---|---|---|
| 주식 종목수 | MBSOSI_COUNT | #4472C4 (파랑) |
| 파생 종목수 | MBFOSI_COUNT | #ED7D31 (주황) |
| 상품 종목수 | MBCOSI_COUNT | #A9D18E (연초록) |
| 업종 종목수 | MBJISU_COUNT | #FF0000 (빨강) |
| NXT 종목수 | NXT_COUNT | #7030A0 (보라) |
| 옵션결제월별 최대수 | OPT_MAX_COUNT | #00B0F0 (하늘) |

**탭 2: 접속자수 현황**

| 항목명 | LOG_ID | 선 색상 |
|---|---|---|
| 전일 최대동시접속 | MAX_CONN_PREV | #4472C4 (파랑) |
| HTS 최대동시접속 | HTS_MAX_CONN | #ED7D31 (주황) |
| MTS 최대동시접속 | MTS_MAX_CONN | #A9D18E (연초록) |

### 5-4. 조회 SQL

```sql
-- ============================================================
-- 1주일 히스토리 조회 (서버+LOG_ID 지정)
-- ANALYZE_DATE 기준으로 최대 7일치 조회
--
-- ⚠️ LOG_TYPE 조건 주의사항:
--   현재 히스토리 그래프 대상 항목:
--     종목수 그룹 (MBSOSI_COUNT 등) → LOG_TYPE = '수치'
--     접속자수 그룹 (MAX_CONN 등)   → LOG_TYPE = '수치'
-- ============================================================
SELECT
    LOG_VALUE,
    LOG_TIMESTAMP,
    ANALYZE_DATE
FROM TB_ANALYZE_RESULT
WHERE SERVER_ID    = #{serverId}
  AND LOG_ID       = #{logId}
  AND ANALYZE_DATE >= #{startDate}   -- 오늘 기준 7일 전 (yyyyMMdd)
  AND ANALYZE_DATE <= #{today}
  AND LOG_TYPE     = '수치'          
ORDER BY LOG_TIMESTAMP ASC;
-- ※ X축은 LOG_TIMESTAMP 전체 포인트 표시 (집계 없음)
-- ※ 하루에 여러 건 수집되어도 모두 표시됨
```

---

## 6. 서버별 리소스 현황

### 6-1. AdminLTE 컴포넌트
- **사용 컴포넌트**: `card` + `Chart.js Doughnut Chart`
- **레이아웃**: 서버당 도넛차트 1개, 한 줄에 4개 (`col-lg-3`)

### 6-2. 서버 목록 결정 방식

> ℹ️ 코드 고정이 아닌 **동적 조회** 방식 사용
> 서버가 추가되어도 코드 수정 없이 자동으로 반영됨

```
① TB_ANALYZE_HISTORY에서 오늘 분석된 DISTINCT SERVER_ID 목록 조회
② 각 서버별로 오늘 DISK_HOME LOG_ID 데이터 존재 여부 확인
③ 데이터 있으면 → 도넛차트 표시
   데이터 없으면 → "분석 없음" 텍스트 표시
```

### 6-3. 도넛 차트 표시 규칙

```
[도넛 차트 구성]
- 사용 영역 : LOG_VALUE % (채워진 색상)
- 여유 영역 : (100 - LOG_VALUE) % (회색)
- 중앙 텍스트: N% 표시

[색상 기준 - ANALYZE_LEVEL 기반]
- 정상(ANALYZE_LEVEL='정상') : #198754 (초록)
- 경고(ANALYZE_LEVEL='경고') : #ffc107 (노랑)
- 에러(ANALYZE_LEVEL='에러') : #dc3545 (빨강)
- 데이터 없음                : 회색 점선 원 + "분석 없음" 텍스트
```

### 6-4. 표시 형식

```
┌────────────────────┐
│  dlprem01-테스트개발│  ← SERVER_ID
│   ┌─────────┐     │
│   │  ████   │     │  ← 도넛차트 (색상=ANALYZE_LEVEL)
│   │ ██80%██ │     │  ← 중앙: LOG_VALUE%
│   │  ████   │     │
│   └─────────┘     │
│  DISK_HOME  80%   │  ← LOG_ID + LOG_VALUE
│  임계치: 90%       │  ← THRESHOLD_VALUE
│  로그: 09:30       │  ← LOG_TIMESTAMP (오늘이면 HH:mm)
└────────────────────┘

[데이터 없는 서버]
┌────────────────────┐
│  newserver-신규     │
│   ┌─────────┐     │
│   │  - - -  │     │  ← 회색 점선
│   │ 분석없음│     │
│   └─────────┘     │
│  DISK_HOME         │
└────────────────────┘
```

### 6-5. 조회 SQL

```sql
-- ============================================================
-- STEP 1: 오늘 분석된 서버 목록 조회
-- ============================================================
SELECT DISTINCT SERVER_ID
FROM TB_ANALYZE_HISTORY
WHERE ANALYZE_DATE = #{today}
ORDER BY SERVER_ID;

-- ============================================================
-- STEP 2: 서버별 DISK_HOME 최신값 조회
-- LOG_ID = 'DISK_HOME' 고정
-- LOG_TYPE = '수치' (DISK_HOME은 반드시 수치형으로 수집)
-- ANALYZE_DATE = 오늘 기준 최신 1건
-- Altibase/PostgreSQL 공통 호환 - ROWNUM 방식
-- ============================================================
SELECT LOG_VALUE, ANALYZE_LEVEL, THRESHOLD_VALUE, LOG_TIMESTAMP
FROM (
    SELECT LOG_VALUE, ANALYZE_LEVEL, THRESHOLD_VALUE, LOG_TIMESTAMP
    FROM TB_ANALYZE_RESULT
    WHERE ANALYZE_DATE = #{today}
      AND SERVER_ID    = #{serverId}
      AND LOG_ID       = 'DISK_HOME'
      AND LOG_TYPE     = '수치'
    ORDER BY LOG_TIMESTAMP DESC
)
WHERE ROWNUM = 1;
-- ※ PostgreSQL에서는: ORDER BY LOG_TIMESTAMP DESC LIMIT 1 사용 가능
-- ※ 결과 없으면(NULL) → 해당 서버는 "분석 없음" 표시
```

---

## 7. 수집대상 서버 리스트

### 7-1. AdminLTE 컴포넌트
- **사용 컴포넌트**: `card` + `list-group`
- **레이아웃**: 전체 너비 (col-12)

### 7-2. 서버 상태 정의

| 상태 | 조건 | 색상 | 아이콘 | AdminLTE 클래스 |
|---|---|---|---|---|
| 정상 | 수집+분석 완료, ANALYZE_LEVEL 에러/경고 없음 | 초록 | `fa-circle` | `text-success` |
| 경고 | 수집+분석 완료, ANALYZE_LEVEL 경고 있음 | 노랑 | `fa-circle` | `text-warning` |
| 에러 | 수집 실패(FAIL) 또는 ANALYZE_LEVEL 에러 있음 | 빨강 | `fa-circle` | `text-danger` |
| 수집미완료 | 오늘 TB_COLLECT_HISTORY에 해당 서버 없음 | 진회색 | `fa-circle` | `text-muted` |

> ⚠️ 상태 우선순위: 에러 > 경고 > 정상 > 수집미완료  
> ℹ️ 미분석 건수는 서버 상태 판별에 영향을 주지 않음 (2번 요약 카드에서만 별도 표시)

### 7-3. 리스트 항목 구성

```
[각 서버 항목 표시]
● dlprem01-테스트개발        수집: 09:30  분석: 09:35  에러: 2건  경고: 1건
● orderserver-자동주문       수집: 10:00  분석: 10:05  정상
○ newserver-신규서버          수집 미완료 (스케줄 미도래)
```

| 항목 | 표시 내용 |
|---|---|
| 상태 아이콘 | 색상 원형 아이콘 |
| 서버명 | SERVER_ID |
| 마지막 수집 시각 | TB_COLLECT_HISTORY 최신 COLLECT_END_AT |
| 마지막 분석 시각 | TB_ANALYZE_HISTORY 최신 ANALYZE_END_AT |
| 에러/경고 건수 | TB_ANALYZE_RESULT 오늘 집계 |

### 7-4. 조회 SQL

```sql
-- 서버별 상태 종합 조회
SELECT
    S.SERVER_ID,
    CH.LAST_COLLECT_AT,
    CH.COLLECT_STATUS,
    AH.LAST_ANALYZE_AT,
    AH.ANALYZE_STATUS,
    AR.ERROR_CNT,
    AR.WARNING_CNT
FROM (
    SELECT DISTINCT SERVER_ID FROM TB_COLLECT_HISTORY
) S
LEFT JOIN (
    SELECT SERVER_ID,
           MAX(COLLECT_END_AT) AS LAST_COLLECT_AT,
           CASE
               WHEN SUM(CASE WHEN COLLECT_STATUS = 'FAIL' THEN 1 ELSE 0 END) > 0 THEN 'FAIL'
               WHEN SUM(CASE WHEN COLLECT_STATUS = 'SKIP' THEN 1 ELSE 0 END) > 0 THEN 'SKIP'
               ELSE 'SUCCESS'
           END AS COLLECT_STATUS
    FROM TB_COLLECT_HISTORY
    WHERE COLLECT_DATE = #{today}
    GROUP BY SERVER_ID
) CH ON S.SERVER_ID = CH.SERVER_ID
LEFT JOIN (
    SELECT SERVER_ID,
           MAX(ANALYZE_END_AT) AS LAST_ANALYZE_AT,
           CASE
               WHEN SUM(CASE WHEN ANALYZE_STATUS = 'FAIL'    THEN 1 ELSE 0 END) > 0 THEN 'FAIL'
               WHEN SUM(CASE WHEN ANALYZE_STATUS = 'PARTIAL' THEN 1 ELSE 0 END) > 0 THEN 'PARTIAL'
               ELSE 'SUCCESS'
           END AS ANALYZE_STATUS
    FROM TB_ANALYZE_HISTORY
    WHERE ANALYZE_DATE = #{today}
    GROUP BY SERVER_ID
) AH ON S.SERVER_ID = AH.SERVER_ID
LEFT JOIN (
    SELECT SERVER_ID,
           SUM(CASE WHEN ANALYZE_LEVEL = '에러'  THEN 1 ELSE 0 END) AS ERROR_CNT,
           SUM(CASE WHEN ANALYZE_LEVEL = '경고'  THEN 1 ELSE 0 END) AS WARNING_CNT
    FROM TB_ANALYZE_RESULT
    WHERE ANALYZE_DATE = #{today}
    GROUP BY SERVER_ID
) AR ON S.SERVER_ID = AR.SERVER_ID
ORDER BY
    CASE
        WHEN CH.COLLECT_STATUS = 'FAIL' OR AR.ERROR_CNT > 0 THEN 1
        WHEN AR.WARNING_CNT > 0                              THEN 2
        WHEN CH.COLLECT_STATUS = 'SUCCESS'                   THEN 3
        ELSE 4
    END;
```

---

## 8. 전체 페이지 레이아웃

```
┌──────────────────────────────────────────────────────────────────┐
│ [1. 상단 NavBar]                                                  │
│ 채널 서버 점검 Dashboard       현재시간  마지막갱신  다음갱신     │
├──────────────────────────────────────────────────────────────────┤
│ [2. 요약 현황 — 가로 스트립 카드 1개]                             │
│ 에러 3(14%) │ 경고 2(9%) │ 정상 12(57%) │ 정보 3(14%) │ 미분석 1(5%)│
│─────────────────────────────────────────────────────────────────│
│ 수집 5/6 ▓▓░│ 분석 5/6 ▓▓░│ FAIL 1건    │ SKIP 0 이상없│ 분석실패 이상없│
├──────────────────────────────────────────────────────────────────┤
│ [3. 정보성 중요 데이터 — 그룹별 리스트 카드 3개]                   │
│ ┌──종목 현황──┐ ┌──서비스 현황──┐ ┌──접속자 현황──┐             │
│ │● 주식 1,234K│ │● 자동주문 2,341│ │● 전일 18,432 │             │
│ │● 파생  89K  │ │● 시세포착1 1,872│ │● HTS  12,104 │             │
│ │● 상품  12K  │ │● 시세포착2 1,650│ │● MTS   6,328 │             │
│ │● 업종  1,024│ │● 주파수클럽 312│ └──────────────┘             │
│ │● NXT  54K   │ └───────────────┘                               │
│ │● 옵션   480 │                                                  │
│ └─────────────┘                                                  │
├──────────────────────────────────────────────────────────────────┤
│ [4. 에러/경고 상세 목록]            (전체 너비 col-12)             │
│ [서버선택▼] [에러/경고 탭] [정상/정보/미분석 탭]                   │
│ 시각|서버/IP|LOG_ID|타입|레벨|메시지|임계치|원본                   │
│ ↓ 행 클릭 시 accordion 펼침                                      │
├──────────────────────────────────────────────────────────────────┤
│ [6. 서버별 리소스 현황]             (전체 너비 col-12)  ← 4번 아래│
│ [dlprem01 도넛] [pwwfep01 도넛] [pamoap01 도넛] [pmaster2 도넛]  │
│   ▲ DISK_HOME 존재하는 서버만 동적 표시                           │
├──────────────────────────────────────────────────────────────────┤
│ [5. 히스토리 그래프]                (전체 너비 col-12)             │
│ [종목수 현황 탭] [접속자수 현황 탭]                                │
│ X축: LOG_TIMESTAMP 전체 포인트 / Y축: 수치값 / 최대 1주일          │
├──────────────────────────────────────────────────────────────────┤
│ [7. 수집대상 서버 리스트]           (전체 너비 col-12)             │
│ ● dlprem01      수집: 09:30  분석: 09:35  에러: 2  경고: 1        │
│ ● orderserver   수집: 10:00  분석: 10:05  정상                    │
│ ○ newserver     수집 미완료                                       │
└──────────────────────────────────────────────────────────────────┘
```

---

## 9. Spring Boot Controller / API 설계

### 9-1. 페이지 Controller

| URL | 메서드 | 설명 | 반환 |
|---|---|---|---|
| `/dashboard` | GET | 메인 대시보드 페이지 | Thymeleaf HTML |

### 9-2. AJAX API (자동 갱신용)

| URL | 메서드 | 설명 | 반환 |
|---|---|---|---|
| `/dashboard/api/summary` | GET | 요약카드 데이터 (수집/분석/레벨별) | JSON |
| `/dashboard/api/info-data` | GET | 정보성 중요 데이터 (13개 항목) | JSON |
| `/dashboard/api/error-list` | GET | 에러/경고 목록 (페이징) | JSON |
| `/dashboard/api/normal-list` | GET | 정상/정보/미분석 목록 (페이징) | JSON |
| `/dashboard/api/history` | GET | 히스토리 그래프 데이터 | JSON |
| `/dashboard/api/resource` | GET | 리소스 도넛차트 (서버 동적 조회) | JSON |
| `/dashboard/api/server-list` | GET | 서버 리스트 상태 | JSON |
| `/dashboard/api/raw-log/{collectLogId}` | GET | 원본 로그 모달 조회 | JSON |

### 9-3. API 공통 파라미터

| 파라미터 | 설명 | 기본값 | 예시 |
|---|---|---|---|
| `date` | 조회 날짜 | 오늘 (서버 기준) | `20260602` |
| `serverId` | 서버 필터 | 전체 (조건 없음) | `dlprem01-테스트개발` |
| `page` | 페이지 번호 | 1 | `2` |

### 9-4. Bootstrap 5 Thymeleaf 적용 시 유의사항

```html
<!-- ⚠️ Bootstrap 4 → 5 클래스 변경 주요 목록 -->

<!-- 간격 -->
mr-* → me-*    (margin-end)
ml-* → ms-*    (margin-start)
pr-* → pe-*    (padding-end)
pl-* → ps-*    (padding-start)

<!-- 텍스트 -->
font-weight-bold → fw-bold
font-weight-normal → fw-normal
text-muted → text-body-secondary

<!-- 폼 -->
form-group → (삭제됨, 그냥 div 사용)
custom-select → form-select
form-control-file → form-control

<!-- 배지 -->
<span class="badge badge-danger">에러</span>     ← Bootstrap 4
<span class="badge text-bg-danger">에러</span>   ← Bootstrap 5 ✅
```

---

## 10. DB 타입 규칙 (Altibase + PostgreSQL 공통)

### Java ↔ DB 타입 매핑
| DB 타입 | Java 타입 | 비고 |
|---|---|---|
| NUMERIC(19,0) | Long | PK 등 정수 |
| NUMERIC(18,6) | BigDecimal | 수치형 로그값 |
| CHAR(1) | String | "Y" 또는 "N" 만 허용 |
| VARCHAR(n) | String | 최대 VARCHAR(4000) |
| TIMESTAMP | LocalDateTime | |

### 금지 타입
| 금지 | 대체 |
|---|---|
| BOOLEAN | CHAR(1) ('Y'/'N') |
| TEXT | VARCHAR(4000) 이하 |
| BIGINT | NUMERIC(19,0) |
| SERIAL / AUTO_INCREMENT | SEQUENCE 객체 |
| CLOB | VARCHAR(4000) 이하 |

### 기타 DB 규칙
- PK는 SEQUENCE 객체로 생성, MyBatis에서 nextval 호출 후 파라미터로 전달
- DB 레벨 FK 제약 생성 금지, 정합성은 애플리케이션에서 관리

## 11. 개발 순서 (추천)

```
Phase 1: 기반 구성
  ① Spring Boot 프로젝트 생성 + DB 연결 확인
  ② AdminLTE HTML 정적 파일 복사 및 기본 레이아웃 구성
  ③ 1번 상단 NavBar (정적 HTML + JS 시계/카운트다운)

Phase 2: 핵심 데이터
  ④ 2번 요약카드 (MyBatis SQL + Thymeleaf)
  ⑤ 4번 에러/경고 목록 테이블 + Accordion
  ⑥ 7번 서버 리스트

Phase 3: 시각화
  ⑦ 6번 리소스 도넛차트
  ⑧ 5번 히스토리 그래프 (Line Chart)
  ⑨ 3번 정보성 데이터 카드

Phase 4: 완성
  ⑩ 전체 AJAX 자동 갱신 적용
  ⑪ 서버+LOG_ID 매핑표 실제 값으로 확정 및 적용
  ⑫ 테스트 및 UI 세부 조정
```
