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

@Service
@RequiredArgsConstructor
public class DashboardService {
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int PAGE_SIZE = 10;

    private final DashboardMapper dashboardMapper;
    private final InfoDataConfig infoDataConfig;

    private int collectTotalFromSchedule;
    private int analyzeTotalFromSchedule;

    @PostConstruct
    public void init() {
        this.collectTotalFromSchedule = parseScheduleServerCount(infoDataConfig.getCollectSchedulePath());
        this.analyzeTotalFromSchedule = parseScheduleServerCount(infoDataConfig.getAnalyzeSchedulePath());
    }

    public SummaryDto getSummary() {
        String today = today();
        SummaryDto summary = dashboardMapper.selectSummary(today);
        if (summary == null) {
            summary = new SummaryDto();
        }

        summary.setCollectTotal(collectTotalFromSchedule);
        summary.setAnalyzeTotal(analyzeTotalFromSchedule);

        summary.setCollectRatio(ratio(summary.getCollectSuccess(), summary.getCollectTotal()));
        summary.setAnalyzeRatio(ratio(summary.getAnalyzeSuccess(), summary.getAnalyzeTotal()));
        return summary;
    }

    public List<AnalyzeResultDto> getErrorWarningList(String serverId, int page) {
        int resolvedPage = Math.max(page, 1);
        int offset = (resolvedPage - 1) * PAGE_SIZE;
        return dashboardMapper.selectErrorWarningList(today(), serverId, offset, PAGE_SIZE);
    }

    public int getErrorWarningCount(String serverId) {
        return dashboardMapper.countErrorWarning(today(), serverId);
    }

    public PageResultDto<AnalyzeResultDto> getErrorWarningPage(String serverId, int page) {
        int resolvedPage = Math.max(page, 1);
        return new PageResultDto<>(getErrorWarningList(serverId, resolvedPage), resolvedPage, PAGE_SIZE, getErrorWarningCount(serverId));
    }

    public List<AnalyzeResultDto> getNormalInfoList(String serverId, int page) {
        int resolvedPage = Math.max(page, 1);
        int offset = (resolvedPage - 1) * PAGE_SIZE;
        return dashboardMapper.selectNormalInfoList(today(), serverId, offset, PAGE_SIZE);
    }

    public int getNormalInfoCount(String serverId) {
        return dashboardMapper.countNormalInfo(today(), serverId);
    }

    public PageResultDto<AnalyzeResultDto> getNormalInfoPage(String serverId, int page) {
        int resolvedPage = Math.max(page, 1);
        return new PageResultDto<>(getNormalInfoList(serverId, resolvedPage), resolvedPage, PAGE_SIZE, getNormalInfoCount(serverId));
    }

    public List<Map<String, Object>> getServerList() {
        return dashboardMapper.selectServerList(today());
    }

    public List<Map<String, Object>> getHistoryData(String groupType) {
        String today = today();
        String startDate = LocalDate.parse(today, YYYYMMDD).minusDays(infoDataConfig.getHistoryDays() - 1L).format(YYYYMMDD);
        String endDate = today;

        List<InfoDataConfig.InfoDataItem> items = new ArrayList<>();
        if ("stock".equalsIgnoreCase(groupType)) {
            Set<String> targets = Set.of(
                    "MBSOSI_COUNT", "MBFOSI_COUNT", "MBCOSI_COUNT", "MBJISU_COUNT", "NXT_COUNT", "OPT_MAX_COUNT"
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
            
            // 날짜별로 그룹화하여 각 날짜마다 가장 최신 데이터만 선택
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
                    // 같은 날짜의 데이터 중 가장 최신 데이터(logTimestamp가 더 큰 것) 유지
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

    public List<Map<String, Object>> getResourceData() {
        return dashboardMapper.selectResourceData(today());
    }

    public Map<String, Object> getAllInfoData() {
        String today = today();
        Map<String, Object> result = new LinkedHashMap<>();
        for (InfoDataConfig.InfoDataItem item : infoDataConfig.getInfoData()) {
            AnalyzeResultDto row = dashboardMapper.selectInfoData(today, item.getServerId(), item.getLogId());
            Map<String, Object> value = new HashMap<>();
            if (row != null) {
                value.put("logValue", row.getLogValue());
                value.put("logTimestamp", row.getLogTimestamp());
            } else {
                value.put("logValue", null);
                value.put("logTimestamp", null);
            }
            result.put(item.getLogId(), value);
        }
        return result;
    }

    public CollectLogDto getRawLog(Long collectLogId) {
        return dashboardMapper.selectRawLog(collectLogId);
    }

    private String today() {
        return LocalDate.now().format(YYYYMMDD);
    }

    private double ratio(int numerator, int denominator) {
        if (denominator == 0) {
            return 0D;
        }
        return Math.round((numerator * 100.0D / denominator) * 100.0D) / 100.0D;
    }

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
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    continue;
                }
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
}

