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
        SummaryDto summary = dashboardMapper.selectSummary(today);
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
     * @return 에러/경고 레벨만 포함한 오늘 분석 결과 목록이다.
     */
    public List<AnalyzeResultDto> getErrorWarningList(String serverId, int page) {
        int resolvedPage = Math.max(page, 1);
        int offset = (resolvedPage - 1) * PAGE_SIZE;
        return dashboardMapper.selectErrorWarningList(today(), serverId, offset, PAGE_SIZE);
    }

    /**
     * 에러/경고 탭 전체 건수를 반환한다.
     *
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다. 비어 있으면 전체 서버를 조회한다.
     * @return 현재 필터 조건에 해당하는 오늘 에러/경고 건수다.
     */
    public int getErrorWarningCount(String serverId) {
        return dashboardMapper.countErrorWarning(today(), serverId);
    }

    /**
     * 에러/경고 탭용 페이지 결과를 구성한다.
     *
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다.
     * @param page 요청 페이지 번호다.
     * @return 목록과 전체 건수를 함께 담은 페이지 응답이다.
     */
    public PageResultDto<AnalyzeResultDto> getErrorWarningPage(String serverId, int page) {
        int resolvedPage = Math.max(page, 1);
        return new PageResultDto<>(getErrorWarningList(serverId, resolvedPage), resolvedPage, PAGE_SIZE, getErrorWarningCount(serverId));
    }

    /**
     * 정상/정보/미분석 탭 목록 조회용 분석 결과를 반환한다.
     *
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다. 비어 있으면 전체 서버를 조회한다.
     * @param page 대시보드 페이지 번호다. 1보다 작으면 첫 페이지로 보정한다.
     * @return 정상, 정보, 미분석 레벨만 포함한 오늘 분석 결과 목록이다.
     */
    public List<AnalyzeResultDto> getNormalInfoList(String serverId, int page) {
        int resolvedPage = Math.max(page, 1);
        int offset = (resolvedPage - 1) * PAGE_SIZE;
        return dashboardMapper.selectNormalInfoList(today(), serverId, offset, PAGE_SIZE);
    }

    /**
     * 정상/정보/미분석 탭 전체 건수를 반환한다.
     *
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다. 비어 있으면 전체 서버를 조회한다.
     * @return 현재 필터 조건에 해당하는 오늘 정상/정보/미분석 건수다.
     */
    public int getNormalInfoCount(String serverId) {
        return dashboardMapper.countNormalInfo(today(), serverId);
    }

    /**
     * 정상/정보/미분석 탭용 페이지 결과를 구성한다.
     *
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다.
     * @param page 요청 페이지 번호다.
     * @return 목록과 전체 건수를 함께 담은 페이지 응답이다.
     */
    public PageResultDto<AnalyzeResultDto> getNormalInfoPage(String serverId, int page) {
        int resolvedPage = Math.max(page, 1);
        return new PageResultDto<>(getNormalInfoList(serverId, resolvedPage), resolvedPage, PAGE_SIZE, getNormalInfoCount(serverId));
    }

    /**
     * 서버 리스트 카드에 필요한 서버별 요약 정보를 반환한다.
     *
     * 반환값 의미:
     * - 서버구분, 최근 수집/분석 시각, 에러/경고 건수를 함께 담은 화면용 목록이다.
     */
    public List<Map<String, Object>> getServerList() {
        List<Map<String, Object>> rows = dashboardMapper.selectServerList(today());
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
        String today = today();
        String startDate = LocalDate.parse(today, YYYYMMDD).minusDays(infoDataConfig.getHistoryDays() - 1L).format(YYYYMMDD);
        String endDate = today;

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
            Set<String> targets = Set.of("MAX_CONN_PREV", "HTS_MAX_CONN", "MTS_MAX_CONN");
            for (InfoDataConfig.InfoDataItem item : infoDataConfig.getInfoData()) {
                if (targets.contains(item.getLogId())) {
                    items.add(item);
                }
            }
        } else {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (InfoDataConfig.InfoDataItem item : items) {
            List<AnalyzeResultDto> rows = dashboardMapper.selectHistoryData(
                    startDate,
                    endDate,
                    item.getServerId(),
                    item.getLogId()
            );

            // ── Step 2. 날짜별 대표값 선정 ──
            // 같은 날짜에 여러 번 분석되더라도 히스토리 그래프는 일자별 대표값 1건만 보여줘야
            // 화면이 과밀해지지 않고 운영자가 마지막 분석 상태를 빠르게 확인할 수 있다.
            Map<String, AnalyzeResultDto> latestByDate = new TreeMap<>();
            for (AnalyzeResultDto row : rows) {
                String date = row.getAnalyzeDate();
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
            for (AnalyzeResultDto row : latestByDate.values()) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("logValue", row.getLogValue());
                point.put("logDate", row.getAnalyzeDate());
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
     * 서버별 리소스 도넛 차트용 수치를 반환한다.
     *
     * 반환값 의미:
     * - 오늘 분석 이력이 있는 서버별 최신 리소스 수치와 임계치를 담은 목록이다.
     */
    public List<Map<String, Object>> getResourceData() {
        return dashboardMapper.selectResourceData(today());
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
        Map<String, Object> result = new LinkedHashMap<>();
        for (InfoDataConfig.InfoDataItem item : infoDataConfig.getInfoData()) {
            AnalyzeResultDto row = dashboardMapper.selectInfoData(today, item.getServerId(), item.getLogId());
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
