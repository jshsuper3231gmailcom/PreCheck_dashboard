package com.sks.precheck.dashboard.service;

import com.sks.precheck.dashboard.config.InfoDataConfig;
import com.sks.precheck.dashboard.dto.AnalyzeResultDto;
import com.sks.precheck.dashboard.dto.CollectLogDto;
import com.sks.precheck.dashboard.dto.PageResultDto;
import com.sks.precheck.dashboard.dto.SummaryDto;
import com.sks.precheck.dashboard.mapper.DashboardMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dashboard 조회용 업무 조합 서비스.
 *
 * 역할:
 * - 대시보드 화면이 필요로 하는 수집, 분석, 통보 조회 데이터를 조합한다.
 * - 수집/분석 스케줄 정의서 기준 서버 수를 별도로 계산해 요약 분모를 보정한다.
 *
 * 설계 이유:
 * - Dashboard는 조회 전용이므로 화면 단위 응답을 서비스에서 한 번에 조합하는 편이 단순하다.
 * - 수집/분석 성공 건수의 분모를 DB 이력이 아니라 스케줄 등록 서버 수로 맞춰야 명세와 일치한다.
 *
 * 운영상 주의점:
 * - 스케줄 파일이 없거나 포맷이 맞지 않으면 예외를 확산하지 않고 0건으로 처리한다.
 * - 이 클래스는 상태를 변경하지 않으며, 화면용 집계와 표시 기준만 제공한다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int PAGE_SIZE = 10;
    /**
     * 서버 리스트/리소스 바차트의 서버 모수로 인정할 수집·분석 이력 조회 구간이다(오늘 포함 7일).
     * 오늘 하루로 좁히면 스케줄러 미기동처럼 이력이 아예 없는 장애가 빈 화면으로 감춰지고,
     * 제한을 두지 않으면 폐기된 서버가 영구히 남는다.
     */
    private static final int SERVER_POPULATION_DAYS = 3;

    /** 리소스 패널이 서버마다 조회할 지표 LOG_ID다. */
    private static final List<String> RESOURCE_LOG_IDS = List.of("DISK_HOME", "MEM_USAGE");

    /**
     * 리소스 지표 LOG_ID를 화면 응답 키로 옮기는 표다. 화면은 LOG_ID가 아니라 이 키(disk/mem)로
     * 카드 안의 바를 그리므로, 지표를 늘릴 때는 RESOURCE_LOG_IDS와 이 표를 함께 늘려야 한다.
     */
    private static final Map<String, String> RESOURCE_METRIC_KEYS = Map.of(
            "DISK_HOME", "disk",
            "MEM_USAGE", "mem"
    );

    /** 접속자 카드 3수치(전일 최대동시접속/HTS/MTS)의 LOG_ID다. */
    private static final List<String> CONN_CARD_LOG_IDS = List.of("MAX_CONN_PREV", "HTS_MAX_CONN", "MTS_MAX_CONN");

    /** UC 실시간 접속자수 60분 차트의 LOG_ID다. */
    private static final List<String> UC_SPARK_LOG_IDS = List.of("UC_TOTAL_COUNT", "UC_HTS_COUNT", "UC_MTS_COUNT");

    /**
     * 접속자 LOG_ID가 info-data 설정에 없을 때 사용할 서버구분이다.
     * UC 실시간 3종은 카드 항목이 아니라 설정에서 서버를 못 찾는 경우가 있어 기본값이 필요하다.
     */
    private static final String CONN_DEFAULT_SERVER_ID = "pmaster2-마스터";

    /** History 접속자 탭 월별 그래프가 거슬러 올라가는 개월 수다(기준일 포함 12개월). */
    private static final int MONTHLY_HISTORY_MONTHS = 11;

    private final DashboardMapper dashboardMapper;
    private final InfoDataConfig infoDataConfig;

    private int collectTotalFromSchedule;
    private int analyzeTotalFromSchedule;
    private Map<String, String> collectScheduleMap = Map.of();
    private Map<String, String> analyzeScheduleMap = Map.of();

    /**
     * 애플리케이션 시작 시 스케줄 정의서에서 수집/분석 대상 서버 수를 미리 계산한다.
     *
     * 설계 이유:
     * - 대시보드 요약 비율은 매 요청마다 파일을 다시 읽기보다 기동 시 1회 계산하는 편이 안정적이다.
     * - 파일 포맷 오류가 있더라도 화면 조회 자체는 계속 가능해야 하므로 초기 캐시 방식으로 분리했다.
     */
    @PostConstruct
    public void init() {
        this.collectTotalFromSchedule = parseScheduleServerCount(infoDataConfig.getCollectSchedulePath());
        this.analyzeTotalFromSchedule = parseScheduleServerCount(infoDataConfig.getAnalyzeSchedulePath());
        this.collectScheduleMap = parseScheduleMap(infoDataConfig.getCollectSchedulePath());
        this.analyzeScheduleMap = parseScheduleMap(infoDataConfig.getAnalyzeSchedulePath());
    }

    /**
     * 오늘 기준 요약 영역에 필요한 집계값을 반환한다.
     *
     * 처리 순서:
     * - DB에서 오늘 수집/분석 결과를 조회한다.
     * - 스케줄 정의서 기준 전체 서버 수를 분모로 보정한다.
     * - 실패/제외 사유를 툴팁 표시용 문자열 목록으로 정리한다.
     *
     * 반환값 의미:
     * - 상단 요약 스트립과 하단 수집/분석 현황에 바로 바인딩할 수 있는 화면용 집계 결과다.
     */
    public SummaryDto getSummary() {
        String today = today();
        SummaryDto summary = dashboardMapper.selectSummary(today, infoDataConfig.getDetailExcludeLogIds());
        if (summary == null) {
            summary = new SummaryDto();
        }

        // ── Step 1. 명세 기준 분모 보정 ──
        // 수집/분석 성공률은 실행 이력 건수가 아니라 스케줄에 등록된 서버 수를 분모로 사용해야
        // 운영자가 "오늘 몇 대 중 몇 대가 정상 처리됐는지"를 명세와 같은 기준으로 해석할 수 있다.
        summary.setCollectTotal(collectTotalFromSchedule);
        summary.setAnalyzeTotal(analyzeTotalFromSchedule);

        summary.setCollectRatio(ratio(summary.getCollectSuccess(), summary.getCollectTotal()));
        summary.setAnalyzeRatio(ratio(summary.getAnalyzeSuccess(), summary.getAnalyzeTotal()));

        // ── Step 2. 장애 사유 정리 ──
        // 실패/제외 사유는 건수만으로는 원인 추적이 어려우므로 서버별 메시지 목록으로 함께 내려준다.
        summary.setCollectFailReasons(formatFailReasons(dashboardMapper.selectCollectFailReasons(today, "FAIL")));
        summary.setCollectSkipReasons(formatFailReasons(dashboardMapper.selectCollectFailReasons(today, "SKIP")));
        summary.setAnalyzeFailReasons(formatFailReasons(dashboardMapper.selectAnalyzeFailReasons(today, "FAIL")));
        return summary;
    }

    /**
     * 장애 사유 목록을 화면 툴팁에 맞는 문자열 형태로 정리한다.
     *
     * 무시 조건:
     * - 실패 사유가 비어 있으면 운영상 의미가 없으므로 목록에서 제외한다.
     *
     * 반환값 의미:
     * - `서버구분: 실패사유` 형식의 문자열 목록이다.
     */
    private List<String> formatFailReasons(List<Map<String, Object>> rows) {
        List<String> reasons = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object serverId = row.get("serverId");
            Object failReason = row.get("failReason");
            if (failReason == null) {
                continue;
            }
            reasons.add(serverId + ": " + failReason);
        }
        return reasons;
    }

    /**
     * 에러/경고 탭 목록 조회용 분석 결과를 반환한다.
     *
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다. 비어 있으면 전체 서버를 조회한다.
     * @param page 대시보드 페이지 번호다. 1보다 작으면 첫 페이지로 보정한다.
     * @param keyword LOG_ID/분석메세지 통합 검색 키워드다. 비어 있으면 전체를 조회한다.
     * @return 에러/경고 레벨만 포함한 오늘 분석 결과 목록이다.
     */
    public List<AnalyzeResultDto> getErrorWarningList(String serverId, int page, String keyword) {
        int resolvedPage = Math.max(page, 1);
        int offset = (resolvedPage - 1) * PAGE_SIZE;
        return dashboardMapper.selectErrorWarningList(today(), serverId, offset, PAGE_SIZE, keyword, infoDataConfig.getDetailExcludeLogIds());
    }

    /**
     * 에러/경고 탭 전체 건수를 반환한다.
     *
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다. 비어 있으면 전체 서버를 조회한다.
     * @param keyword LOG_ID/분석메세지 통합 검색 키워드다. 비어 있으면 전체를 조회한다.
     * @return 현재 필터 조건에 해당하는 오늘 에러/경고 건수다.
     */
    public int getErrorWarningCount(String serverId, String keyword) {
        return dashboardMapper.countErrorWarning(today(), serverId, keyword, infoDataConfig.getDetailExcludeLogIds());
    }

    /**
     * 에러/경고 탭용 페이지 결과를 구성한다.
     *
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다.
     * @param page 요청 페이지 번호다.
     * @param keyword LOG_ID/분석메세지 통합 검색 키워드다.
     * @return 목록과 전체 건수를 함께 담은 페이지 응답이다.
     */
    public PageResultDto<AnalyzeResultDto> getErrorWarningPage(String serverId, int page, String keyword) {
        int resolvedPage = Math.max(page, 1);
        return new PageResultDto<>(getErrorWarningList(serverId, resolvedPage, keyword), resolvedPage, PAGE_SIZE, getErrorWarningCount(serverId, keyword));
    }

    /**
     * 정상/정보/미분석 탭 목록 조회용 분석 결과를 반환한다.
     *
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다. 비어 있으면 전체 서버를 조회한다.
     * @param page 대시보드 페이지 번호다. 1보다 작으면 첫 페이지로 보정한다.
     * @param keyword LOG_ID/분석메세지 통합 검색 키워드다. 비어 있으면 전체를 조회한다.
     * @return 정상, 정보, 미분석 레벨만 포함한 오늘 분석 결과 목록이다.
     */
    public List<AnalyzeResultDto> getNormalInfoList(String serverId, int page, String keyword) {
        int resolvedPage = Math.max(page, 1);
        int offset = (resolvedPage - 1) * PAGE_SIZE;
        return dashboardMapper.selectNormalInfoList(today(), serverId, offset, PAGE_SIZE, keyword, infoDataConfig.getDetailExcludeLogIds());
    }

    /**
     * 정상/정보/미분석 탭 전체 건수를 반환한다.
     *
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다. 비어 있으면 전체 서버를 조회한다.
     * @param keyword LOG_ID/분석메세지 통합 검색 키워드다. 비어 있으면 전체를 조회한다.
     * @return 현재 필터 조건에 해당하는 오늘 정상/정보/미분석 건수다.
     */
    public int getNormalInfoCount(String serverId, String keyword) {
        return dashboardMapper.countNormalInfo(today(), serverId, keyword, infoDataConfig.getDetailExcludeLogIds());
    }

    /**
     * 정상/정보/미분석 탭용 페이지 결과를 구성한다.
     *
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다.
     * @param page 요청 페이지 번호다.
     * @param keyword LOG_ID/분석메세지 통합 검색 키워드다.
     * @return 목록과 전체 건수를 함께 담은 페이지 응답이다.
     */
    public PageResultDto<AnalyzeResultDto> getNormalInfoPage(String serverId, int page, String keyword) {
        int resolvedPage = Math.max(page, 1);
        return new PageResultDto<>(getNormalInfoList(serverId, resolvedPage, keyword), resolvedPage, PAGE_SIZE, getNormalInfoCount(serverId, keyword));
    }

    /**
     * 서버 리스트 카드에 필요한 서버별 요약 정보를 반환한다.
     *
     * 반환값 의미:
     * - 서버구분, 최근 수집/분석 시각, 에러/경고 건수를 함께 담은 화면용 목록이다.
     * - 모수는 최근 SERVER_POPULATION_DAYS일 내 수집/분석 이력이 있는 서버이며,
     *   그중 오늘 이력이 없는 서버는 상태값이 비어 화면에서 "수집미완료"로 표시된다.
     */
    public List<Map<String, Object>> getServerList() {
        List<Map<String, Object>> rows = dashboardMapper.selectServerList(today(), serverPopulationSince());
        for (Map<String, Object> row : rows) {
            String serverId = String.valueOf(row.get("serverId"));
            row.put("collectSchedule", collectScheduleMap.getOrDefault(serverId, "-"));
            row.put("analyzeSchedule", analyzeScheduleMap.getOrDefault(serverId, "-"));
        }
        return rows;
    }

    /**
     * 히스토리 그래프용 시계열 데이터를 반환한다.
     *
     * 처리 순서:
     * - 화면 그룹 타입에 맞는 주요 데이터 항목만 선별한다.
     * - 조회 기간 내 분석 결과를 읽는다.
     * - 같은 날짜에 여러 건이 있으면 가장 마지막 분석 결과만 남긴다.
     *
     * 실패/무시 조건:
     * - 지원하지 않는 그룹 타입이면 빈 목록을 반환한다.
     * - 날짜가 없는 데이터는 일자 기준 그래프에 포함할 수 없으므로 무시한다.
     *
     * @param groupType `stock`, `overseas`, `conn` 중 하나다.
     * @return 그래프 시리즈별 데이터 목록이다.
     */
    public List<Map<String, Object>> getHistoryData(String groupType) {
        List<InfoDataConfig.InfoDataItem> items = new ArrayList<>();
        // ── Step 1. 그룹별 대상 LOG_ID 확정 ──
        // 정보성 카드는 업무상 의미가 다른 묶음으로 나뉘므로, 화면 탭에 맞는 LOG_ID만 선별해야
        // 그래프가 서로 다른 도메인 수치를 섞지 않는다.
        if ("stock".equalsIgnoreCase(groupType)) {
            Set<String> targets = Set.of(
                    "MBSOSI_COUNT", "MBFOSI_COUNT", "MBCOSI_COUNT", "MBJISU_COUNT", "NXT_COUNT", "OPT_MAX_COUNT"
            );
            for (InfoDataConfig.InfoDataItem item : infoDataConfig.getInfoData()) {
                if (targets.contains(item.getLogId())) {
                    items.add(item);
                }
            }
        } else if ("overseas".equalsIgnoreCase(groupType)) {
            Set<String> targets = Set.of(
                    "OS_BA_COUNT", "OS_NB_COUNT", "OS_HK_COUNT", "OS_SH_COUNT", "OS_SZ_COUNT"
            );
            for (InfoDataConfig.InfoDataItem item : infoDataConfig.getInfoData()) {
                if (targets.contains(item.getLogId())) {
                    items.add(item);
                }
            }
        } else if ("conn".equalsIgnoreCase(groupType)) {
            for (InfoDataConfig.InfoDataItem item : infoDataConfig.getInfoData()) {
                if (CONN_CARD_LOG_IDS.contains(item.getLogId())) {
                    items.add(item);
                }
            }
        } else {
            return List.of();
        }

        // ── Step 1-2. 조회 기준일 확정 ──
        // 접속자 탭만 수집·분석일이 아니라 로그 날짜를 기준으로 삼는다. 접속자 로그는 어제 파일이
        // 오늘 수집되는 경우가 있어, 수집일 기준으로 잡으면 어제 값이 오늘 지점에 찍힌다.
        boolean isConn = "conn".equalsIgnoreCase(groupType);
        LocalDate baseDate = isConn ? connCardBaseDate() : LocalDate.parse(today(), YYYYMMDD);
        if (baseDate == null) {
            return List.of();
        }
        LocalDate startDate = baseDate.minusDays(infoDataConfig.getHistoryDays() - 1L);

        List<Map<String, Object>> result = new ArrayList<>();
        for (InfoDataConfig.InfoDataItem item : items) {
            List<AnalyzeResultDto> rows = isConn
                    ? dashboardMapper.selectConnHistoryData(
                            item.getServerId(),
                            item.getLogId(),
                            startOfDay(startDate),
                            startOfNextDay(baseDate))
                    : dashboardMapper.selectHistoryData(
                            startDate.format(YYYYMMDD),
                            baseDate.format(YYYYMMDD),
                            item.getServerId(),
                            item.getLogId());

            // ── Step 2. 날짜별 대표값 선정 ──
            // 같은 날짜에 여러 번 분석되더라도 히스토리 그래프는 일자별 대표값 1건만 보여줘야
            // 화면이 과밀해지지 않고 운영자가 마지막 분석 상태를 빠르게 확인할 수 있다.
            Map<String, AnalyzeResultDto> latestByDate = new TreeMap<>();
            for (AnalyzeResultDto row : rows) {
                String date = isConn ? logDateOf(row) : row.getAnalyzeDate();
                if (date == null) {
                    continue;
                }

                AnalyzeResultDto existing = latestByDate.get(date);
                if (existing == null) {
                    latestByDate.put(date, row);
                } else {
                    // 같은 날짜에서는 가장 마지막 로그 시각만 남겨야 재분석 이후 상태가 우선된다.
                    LocalDateTime currentTimestamp = row.getLogTimestamp();
                    LocalDateTime existingTimestamp = existing.getLogTimestamp();

                    if (currentTimestamp != null && existingTimestamp != null) {
                        if (currentTimestamp.isAfter(existingTimestamp)) {
                            latestByDate.put(date, row);
                        }
                    } else if (currentTimestamp != null) {
                        latestByDate.put(date, row);
                    }
                }
            }

            // ── Step 3. 화면 전송 형식으로 변환 ──
            // 차트 컴포넌트는 날짜와 수치만 필요하므로 상세 필드를 줄여 응답을 단순하게 유지한다.
            List<Map<String, Object>> data = new ArrayList<>();
            for (Map.Entry<String, AnalyzeResultDto> dateEntry : latestByDate.entrySet()) {
                AnalyzeResultDto row = dateEntry.getValue();
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("logValue", row.getLogValue());
                point.put("logDate", dateEntry.getKey());
                point.put("logTimestamp", row.getLogTimestamp());
                data.add(point);
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("logId", item.getLogId());
            entry.put("data", data);
            result.add(entry);
        }
        return result;
    }

    /**
     * 서버별 리소스 바차트용 수치를 서버 1대 = 1건으로 묶어 반환한다.
     *
     * 설계 이유:
     * - 조회 쿼리는 서버 × 지표 조합으로 행을 주지만 화면은 서버 단위 카드를 그린다. 화면에서
     *   행을 다시 묶게 하면 렌더링 코드에 집계 책임이 섞이므로 서비스에서 형태를 맞춰 보낸다.
     * - serverId는 `ddfep01-해외시세`처럼 식별자와 한글명이 붙어 있어, 카드가 두 줄로 나눠
     *   표시할 수 있도록 첫 `-` 기준으로 id/name을 분리해 함께 내려준다.
     *
     * 반환값 의미:
     * - 서버 모수는 getServerList()와 동일한 기준(최근 SERVER_POPULATION_DAYS일)을 쓴다.
     * - 각 건은 serverId, id, name, noData와 지표별 하위 맵(disk/mem)을 가진다.
     * - 모수에 든 서버는 오늘 분석 결과가 없어도 수치가 null인 채로 포함되며, 화면에서
     *   "분석없음"으로 표시된다(카드 개수와 서버 리스트 카드 개수를 맞추기 위함).
     * - 데이터 부재와 값 0을 구분해야 하므로 미조회 지표는 값을 0으로 채우지 않고 null로 남긴다.
     */
    public List<Map<String, Object>> getResourceData() {
        List<Map<String, Object>> rows =
                dashboardMapper.selectResourceData(today(), serverPopulationSince(), RESOURCE_LOG_IDS);

        // ── Step 1. 서버별로 지표 행을 모은다(쿼리가 SERVER_ID 순으로 주므로 입력 순서를 유지한다) ──
        Map<String, Map<String, Object>> byServer = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String serverId = row.get("serverId") == null ? "" : String.valueOf(row.get("serverId"));
            String metricKey = RESOURCE_METRIC_KEYS.get(String.valueOf(row.get("logId")));
            if (metricKey == null) {
                continue;
            }

            Map<String, Object> card = byServer.computeIfAbsent(serverId, id -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("serverId", id);
                // serverId에 `-`가 없으면 전체를 식별자로 보고 표시명은 비운다.
                int sep = id.indexOf('-');
                created.put("id", sep < 0 ? id : id.substring(0, sep));
                created.put("name", sep < 0 ? "" : id.substring(sep + 1));
                return created;
            });

            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("logId", row.get("logId"));
            metric.put("logValue", row.get("logValue"));
            metric.put("analyzeLevel", row.get("analyzeLevel"));
            metric.put("thresholdValue", row.get("thresholdValue"));
            metric.put("logTimestamp", row.get("logTimestamp"));
            card.put(metricKey, metric);
        }

        // ── Step 2. 누락 지표 자리를 채우고 서버 전체의 데이터 부재 여부를 판정한다 ──
        // 지표 하위 맵이 아예 없으면 화면이 매번 존재 여부를 확인해야 하므로 값이 null인 껍데기를 넣어둔다.
        for (Map<String, Object> card : byServer.values()) {
            boolean noData = true;
            for (String logId : RESOURCE_LOG_IDS) {
                String metricKey = RESOURCE_METRIC_KEYS.get(logId);
                @SuppressWarnings("unchecked")
                Map<String, Object> metric = (Map<String, Object>) card.get(metricKey);
                if (metric == null) {
                    metric = new LinkedHashMap<>();
                    metric.put("logId", logId);
                    metric.put("logValue", null);
                    metric.put("analyzeLevel", null);
                    metric.put("thresholdValue", null);
                    metric.put("logTimestamp", null);
                    card.put(metricKey, metric);
                }
                if (metric.get("logValue") != null) {
                    noData = false;
                }
            }
            card.put("noData", noData);
        }

        return new ArrayList<>(byServer.values());
    }

    /**
     * 주요 데이터 카드에 바인딩할 최신 분석 결과를 LOG_ID 기준으로 정리한다.
     *
     * 설계 이유:
     * - 카드마다 조회 API를 따로 두면 화면 초기 로딩 시 요청 수가 늘어나므로 한 번에 조합한다.
     *
     * 무시 조건:
     * - 대상 LOG_ID의 오늘 분석 결과가 없으면 각 값은 null로 두고 화면에서 `-`로 표시한다.
     *
     * 반환값 의미:
     * - key는 LOG_ID, value는 수치/시각/분석 레벨/임계치 등 카드 표시용 속성 맵이다.
     */
    public Map<String, Object> getAllInfoData() {
        String today = today();
        // 접속자 카드 3수치만 수집일이 아니라 로그 날짜 기준으로 조회한다(나머지 카드는 기존 기준 유지).
        LocalDate connBase = connCardBaseDate();
        Map<String, Object> result = new LinkedHashMap<>();
        for (InfoDataConfig.InfoDataItem item : infoDataConfig.getInfoData()) {
            AnalyzeResultDto row;
            if (CONN_CARD_LOG_IDS.contains(item.getLogId())) {
                row = connBase == null ? null : dashboardMapper.selectConnInfoData(
                        item.getServerId(), item.getLogId(), startOfDay(connBase), startOfNextDay(connBase));
            } else {
                row = dashboardMapper.selectInfoData(today, item.getServerId(), item.getLogId());
            }
            Map<String, Object> value = new HashMap<>();
            // ── Step 1. 조회 결과 정규화 ──
            // 화면은 데이터 부재와 값 0을 구분해야 하므로, 미조회 상태는 명시적으로 null을 유지한다.
            if (row != null) {
                value.put("logValue", row.getLogValue());
                value.put("logTimestamp", row.getLogTimestamp());
                value.put("analyzeLevel", row.getAnalyzeLevel());
                value.put("thresholdValue", row.getThresholdValue());
                value.put("thresholdOperator", row.getThresholdOperator());
            } else {
                value.put("logValue", null);
                value.put("logTimestamp", null);
                value.put("analyzeLevel", null);
                value.put("thresholdValue", null);
                value.put("thresholdOperator", null);
            }
            result.put(item.getLogId(), value);
        }
        return result;
    }

    /**
     * UC 실시간 접속자수 시계열을 3개 LOG_ID 기준으로 반환한다.
     *
     * 처리 순서:
     * - 접속자 기준일(최신 로그 날짜)을 구한다.
     * - 그 하루 구간 안에서만 LOG_ID별 시계열을 조회한다(구간 안 최신 시각 기준 직전 60분).
     *
     * 반환값 의미:
     * - key는 LOG_ID(`UC_TOTAL_COUNT` / `UC_HTS_COUNT` / `UC_MTS_COUNT`),
     *   value는 시간순 정렬된 `{logTimestamp, logValue}` 목록이다.
     * - 화면 배지에 쓰는 기준 일시는 마지막 지점의 `logTimestamp`이므로 별도 필드로 내리지 않는다.
     */
    public Map<String, Object> getUcSparkData() {
        LocalDate baseDate = ucBaseDate();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String logId : UC_SPARK_LOG_IDS) {
            result.put(logId, baseDate == null
                    ? List.of()
                    : dashboardMapper.selectUcSparkData(logId, startOfDay(baseDate), startOfNextDay(baseDate)));
        }
        return result;
    }

    /**
     * History 페이지용 전체 그룹 월별 시계열 데이터를 반환한다.
     *
     * 처리 순서:
     * - 그룹별 기준일에서 11개월 전 1일부터 기준일까지(최대 12개월)를 조회 기간으로 확정한다.
     * - stock / overseas / service / conn 4개 그룹 각각에 대해 infoData 항목을 선별한다.
     * - 각 LOG_ID별 DB 조회 결과를 월(YYYYMM) 단위로 집계하고, 같은 달 내 최신 LOG_TIMESTAMP 기준 1건만 남긴다.
     *
     * 기준일 차이:
     * - conn 그룹만 로그 날짜(접속자 최신 LOG_TIMESTAMP의 날짜)를 기준일로 쓴다. 나머지 3개 그룹은
     *   기존대로 오늘 날짜와 ANALYZE_DATE 기준을 유지한다.
     *
     * 반환값 구조:
     * - key: 그룹명("stock"/"overseas"/"service"/"conn")
     * - value: [{logId, data: [{yyyyMM, logValue, exactDate}]}] 형태의 시리즈 목록이다.
     */
    public Map<String, Object> getMonthlyHistoryAll() {
        String today = today();
        String startDate = LocalDate.parse(today, YYYYMMDD).minusMonths(MONTHLY_HISTORY_MONTHS).withDayOfMonth(1).format(YYYYMMDD);

        // 접속자 그룹 전용 기준일과 조회 구간(로그 날짜 기준). 접속자 데이터가 없으면 null이다.
        LocalDate connBase = connCardBaseDate();
        LocalDate connStart = connBase == null
                ? null
                : connBase.minusMonths(MONTHLY_HISTORY_MONTHS).withDayOfMonth(1);

        Map<String, Set<String>> groups = new LinkedHashMap<>();
        groups.put("stock",    new HashSet<>(Set.of("MBSOSI_COUNT", "MBFOSI_COUNT", "MBCOSI_COUNT", "MBJISU_COUNT", "NXT_COUNT", "OPT_MAX_COUNT")));
        groups.put("overseas", new HashSet<>(Set.of("OS_BA_COUNT", "OS_NB_COUNT", "OS_HK_COUNT", "OS_SH_COUNT", "OS_SZ_COUNT")));
        groups.put("service",  new HashSet<>(Set.of("AUTO_ORDER_ACNT", "CAP_REG_COUNT", "CAP2_REG_COUNT", "FREQ_CLUB_COUNT")));
        groups.put("conn",     new HashSet<>(CONN_CARD_LOG_IDS));

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
            String groupName = entry.getKey();
            Set<String> targets = entry.getValue();
            boolean isConn = "conn".equals(groupName);

            List<Map<String, Object>> seriesList = new ArrayList<>();
            for (InfoDataConfig.InfoDataItem item : infoDataConfig.getInfoData()) {
                if (!targets.contains(item.getLogId())) {
                    continue;
                }
                if (isConn && connBase == null) {
                    continue;
                }

                List<AnalyzeResultDto> rows = isConn
                        ? dashboardMapper.selectConnHistoryData(
                                item.getServerId(),
                                item.getLogId(),
                                startOfDay(connStart),
                                startOfNextDay(connBase))
                        : dashboardMapper.selectHistoryData(
                                startDate, today, item.getServerId(), item.getLogId());

                // 일별 최신 1건 선정 (같은 날짜, 최신 LOG_TIMESTAMP 우선)
                Map<String, AnalyzeResultDto> latestByDate = new TreeMap<>();
                for (AnalyzeResultDto row : rows) {
                    String date = isConn ? logDateOf(row) : row.getAnalyzeDate();
                    if (date == null || date.length() < 8) {
                        continue;
                    }
                    AnalyzeResultDto existing = latestByDate.get(date);
                    if (existing == null) {
                        latestByDate.put(date, row);
                    } else {
                        LocalDateTime curr = row.getLogTimestamp();
                        LocalDateTime prev = existing.getLogTimestamp();
                        if (curr != null && (prev == null || curr.isAfter(prev))) {
                            latestByDate.put(date, row);
                        }
                    }
                }

                List<Map<String, Object>> data = new ArrayList<>();
                for (Map.Entry<String, AnalyzeResultDto> dateEntry : latestByDate.entrySet()) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("logValue", dateEntry.getValue().getLogValue());
                    point.put("exactDate", dateEntry.getKey());
                    data.add(point);
                }

                Map<String, Object> seriesEntry = new LinkedHashMap<>();
                seriesEntry.put("logId", item.getLogId());
                seriesEntry.put("data", data);
                seriesList.add(seriesEntry);
            }
            result.put(groupName, seriesList);
        }
        return result;
    }

    /**
     * 원본 로그 모달 조회용 수집 로그 1건을 반환한다.
     *
     * @param collectLogId 원본 로그 원문을 확인할 대상 수집 로그 식별자다.
     * @return 원본 정규화 로그와 수집 메타 정보를 포함한 상세 데이터다.
     */
    public CollectLogDto getRawLog(Long collectLogId) {
        return dashboardMapper.selectRawLog(collectLogId);
    }

    /**
     * 오늘 날짜를 분석/수집 테이블의 일자 컬럼 형식에 맞춰 반환한다.
     *
     * @return `yyyyMMdd` 형식의 오늘 날짜 문자열이다.
     */
    private String today() {
        return LocalDate.now().format(YYYYMMDD);
    }

    /**
     * 접속자 카드 3수치(전일 최대동시접속/HTS/MTS)의 기준일을 반환한다.
     *
     * 설계 이유:
     * - 카드 3종은 하루 1건만 생성되는 반면 UC 실시간 3종은 분 단위로 쌓인다. 두 묶음을 하나의
     *   기준일로 묶으면 UC가 기준일을 오늘로 끌어올려, 오늘치 카드 로그가 아직 안 들어온 시간대에
     *   카드가 통째로 빈 값이 된다. 그래서 기준일을 묶음별로 나눠 산출한다.
     * - 카드 3종끼리는 같은 기준일을 공유하므로 카드 안에서 수치별 날짜가 갈리지 않는다.
     *
     * @return 카드 3종의 최신 로그 날짜이며, 해당 분석 결과가 없으면 null이다.
     */
    private LocalDate connCardBaseDate() {
        return latestLogDate(CONN_CARD_LOG_IDS);
    }

    /**
     * UC 실시간 접속자수 60분 차트의 기준일을 반환한다.
     *
     * @return UC 3종의 최신 로그 날짜이며, 해당 분석 결과가 없으면 null이다.
     */
    private LocalDate ucBaseDate() {
        return latestLogDate(UC_SPARK_LOG_IDS);
    }

    /**
     * 주어진 LOG_ID 묶음의 최신 로그 날짜를 반환한다.
     *
     * 설계 이유:
     * - 수집·분석일(ANALYZE_DATE)이 아니라 로그 날짜를 화면 기준으로 삼기 위한 공통 계산이다.
     *   오늘 로그가 아직 없으면 자연히 어제(또는 그 이전)가 기준일이 되고, 새 로그가 들어오는
     *   즉시 기준일이 그날로 넘어간다.
     * - 조회 쿼리는 서버 1개 단위로 받으므로, info-data 설정에서 서버가 갈려 있어도 계산이 깨지지
     *   않도록 서버별로 묶어 각각 조회한 뒤 최댓값을 취한다(보통 1개 서버로 묶인다).
     *
     * @param logIds 기준일 산출 대상 LOG_ID 목록이다.
     * @return 최신 `LOG_TIMESTAMP`의 날짜이며, 조회 결과가 없으면 null이다.
     */
    private LocalDate latestLogDate(List<String> logIds) {
        LocalDateTime latest = null;
        for (Map.Entry<String, List<String>> entry : logIdsByServer(logIds).entrySet()) {
            LocalDateTime candidate =
                    dashboardMapper.selectLatestConnLogTimestamp(entry.getKey(), entry.getValue());
            if (candidate != null && (latest == null || candidate.isAfter(latest))) {
                latest = candidate;
            }
        }
        return latest == null ? null : latest.toLocalDate();
    }

    /**
     * LOG_ID 목록을 info-data 설정의 서버구분별로 묶어 반환한다.
     *
     * @param logIds 묶을 대상 LOG_ID 목록이다.
     * @return 서버구분을 키, 해당 서버의 LOG_ID 목록을 값으로 하는 맵이다.
     */
    private Map<String, List<String>> logIdsByServer(List<String> logIds) {
        Map<String, String> serverIdByLogId = new HashMap<>();
        for (InfoDataConfig.InfoDataItem item : infoDataConfig.getInfoData()) {
            serverIdByLogId.put(item.getLogId(), item.getServerId());
        }

        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String logId : logIds) {
            String serverId = serverIdByLogId.getOrDefault(logId, CONN_DEFAULT_SERVER_ID);
            grouped.computeIfAbsent(serverId, key -> new ArrayList<>()).add(logId);
        }
        return grouped;
    }

    /**
     * 로그 날짜 하루를 반개구간 [00:00, 다음날 00:00)으로 변환한 시작 시각을 반환한다.
     *
     * 설계 이유:
     * - `TO_CHAR(LOG_TIMESTAMP, 'YYYYMMDD') = ?` 처럼 컬럼에 함수를 씌우면 인덱스를 못 타므로,
     *   조회 조건은 항상 시각 범위 비교로 만든다.
     */
    private LocalDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay();
    }

    /**
     * 로그 날짜 하루 구간의 끝 시각(다음 날 00:00, 미포함)을 반환한다.
     */
    private LocalDateTime startOfNextDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay();
    }

    /**
     * 분석 결과의 로그 시각에서 로그 날짜(`yyyyMMdd`)를 뽑아낸다.
     *
     * @param row 분석 결과 1건이다.
     * @return 로그 날짜 문자열이며, 로그 시각이 없으면 null이다.
     */
    private String logDateOf(AnalyzeResultDto row) {
        LocalDateTime timestamp = row.getLogTimestamp();
        return timestamp == null ? null : timestamp.toLocalDate().format(YYYYMMDD);
    }

    /**
     * 서버 모수 판정에 사용할 조회 구간 시작 일자를 반환한다.
     *
     * 오늘을 포함해 SERVER_POPULATION_DAYS일이 되도록 (SERVER_POPULATION_DAYS - 1)일을 뺀다.
     * 예: 상수가 7이고 오늘이 20260730이면 20260724를 반환한다(20260724~20260730, 7일).
     *
     * @return 조회 구간 시작일의 `yyyyMMdd` 문자열이다.
     */
    private String serverPopulationSince() {
        return LocalDate.now().minusDays(SERVER_POPULATION_DAYS - 1L).format(YYYYMMDD);
    }

    /**
     * 비율을 소수점 둘째 자리까지 계산한다.
     *
     * 실패/무시 조건:
     * - 분모가 0이면 화면 오류 대신 0으로 반환한다.
     *
     * @param numerator 성공 또는 대상 건수다.
     * @param denominator 전체 서버 수 또는 전체 건수다.
     * @return 백분율 기준 비율 값이다.
     */
    private double ratio(int numerator, int denominator) {
        if (denominator == 0) {
            return 0D;
        }
        return Math.round((numerator * 100.0D / denominator) * 100.0D) / 100.0D;
    }

    /**
     * 스케줄 정의서에서 중복을 제거한 서버 수를 계산한다.
     *
     * 설계 이유:
     * - 동일 서버가 여러 파일 또는 여러 주기로 등록될 수 있어도 분모는 서버 수 기준이어야 한다.
     *
     * 실패/무시 조건:
     * - 경로가 비어 있거나 파일이 없으면 0을 반환한다.
     * - 빈 줄, skip 처리(`#`) 줄, 포맷이 맞지 않는 줄은 무시한다.
     * - 파일 읽기 실패도 대시보드 조회를 막지 않도록 0으로 처리한다.
     *
     * @param schedulePath 수집 또는 분석 스케줄 정의서 절대경로다.
     * @return 유효한 스케줄 라인에서 추출한 고유 서버 수다.
     */
    private int parseScheduleServerCount(String schedulePath) {
        if (schedulePath == null || schedulePath.isBlank()) {
            return 0;
        }

        Path path = Path.of(schedulePath);
        if (!Files.exists(path)) {
            return 0;
        }

        Set<String> serverIds = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // ── Step 1. 운영상 무시 대상 제거 ──
                // 공백 줄과 skip 줄은 실제 수집/분석 대상이 아니므로 분모 계산에서 제외한다.
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    continue;
                }

                // ── Step 2. 서버구분만 추출 ──
                // 정의서 포맷 전체를 검증하기보다 첫 번째 대괄호 블록만 사용하면,
                // 분모 산정에 필요한 서버구분만 안정적으로 복원할 수 있다.
                int open = trimmed.indexOf('[');
                int close = trimmed.indexOf(']');
                if (open != 0 || close <= open + 1) {
                    continue;
                }
                String serverId = trimmed.substring(open + 1, close).trim();
                if (!serverId.isEmpty()) {
                    serverIds.add(serverId);
                }
            }
        } catch (IOException ignored) {
            return 0;
        }
        return serverIds.size();
    }

    private static final Pattern SCHEDULE_BRACKET_PATTERN = Pattern.compile("\\[([^\\[\\]]*)\\]");
    private static final String[] DAY_NAMES = {"일", "월", "화", "수", "목", "금", "토"};

    /**
     * 스케줄 정의서에서 서버별 수집/분석 주기를 사람이 읽기 쉬운 문자열로 변환한다.
     *
     * 처리 순서:
     * - 각 줄의 대괄호 그룹 중 첫 번째는 서버구분, 마지막은 주기 기술로 본다.
     * - 주기 기술을 {@link #formatScheduleSpec}로 변환해 서버구분에 매핑한다.
     *
     * 실패/무시 조건:
     * - 경로가 비어 있거나 파일이 없으면 빈 맵을 반환한다.
     * - 빈 줄, skip 처리(`#`) 줄, 포맷이 맞지 않는 줄은 무시한다.
     */
    private Map<String, String> parseScheduleMap(String schedulePath) {
        if (schedulePath == null || schedulePath.isBlank()) {
            return Map.of();
        }

        Path path = Path.of(schedulePath);
        if (!Files.exists(path)) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                List<String> groups = new ArrayList<>();
                Matcher matcher = SCHEDULE_BRACKET_PATTERN.matcher(trimmed);
                while (matcher.find()) {
                    groups.add(matcher.group(1).trim());
                }
                if (groups.size() < 2) {
                    continue;
                }

                String serverId = groups.get(0);
                if (serverId.isEmpty()) {
                    continue;
                }

                String formatted = formatScheduleSpec(groups.get(groups.size() - 1));
                if (formatted != null) {
                    result.put(serverId, formatted);
                }
            }
        } catch (IOException ignored) {
            return Map.of();
        }
        return result;
    }

    /**
     * 주기 기술(`배치|요일|시작시간` 또는 `주기|요일|시작시간|간격|종료시간`)을 화면 표시용 문자열로 변환한다.
     *
     * @return 포맷이 맞지 않으면 {@code null}
     */
    private String formatScheduleSpec(String spec) {
        String[] parts = spec.split("\\|");
        if (parts.length < 3) {
            return null;
        }

        String dayText = formatDayCode(parts[1].trim());
        String startTime = formatTime(parts[2].trim());
        if (dayText == null || startTime == null) {
            return null;
        }

        String type = parts[0].trim();
        if ("배치".equals(type)) {
            return dayText + " " + startTime + " 1회";
        }
        if ("주기".equals(type) && parts.length >= 5) {
            String interval = parts[3].trim();
            String endTime = formatTime(parts[4].trim());
            if (endTime == null) {
                return null;
            }
            return dayText + " " + startTime + "~" + endTime + " (" + interval + "분 간격)";
        }
        return null;
    }

    private String formatDayCode(String dayCode) {
        if ("*".equals(dayCode) || "0-6".equals(dayCode)) {
            return "매일";
        }
        if (dayCode.matches("[0-6]")) {
            return DAY_NAMES[Integer.parseInt(dayCode)] + "요일";
        }
        if (dayCode.matches("[0-6]-[0-6]")) {
            String[] range = dayCode.split("-");
            int start = Integer.parseInt(range[0]);
            int end = Integer.parseInt(range[1]);
            if (start <= end) {
                return DAY_NAMES[start] + "~" + DAY_NAMES[end];
            }
        }
        return null;
    }

    private String formatTime(String hhmmss) {
        if (!hhmmss.matches("\\d{6}")) {
            return null;
        }
        return hhmmss.substring(0, 2) + ":" + hhmmss.substring(2, 4) + ":" + hhmmss.substring(4, 6);
    }
}
