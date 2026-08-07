package com.sks.precheck.dashboard.service;

import com.sks.precheck.dashboard.config.InfoDataConfig;
import com.sks.precheck.dashboard.dto.AnalyzeResultDto;
import com.sks.precheck.dashboard.dto.SummaryDto;
import com.sks.precheck.dashboard.mapper.DashboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TODAY 현황 텍스트 보고서 생성 서비스.
 *
 * 역할:
 * - 대시보드 화면이 이미 조회하는 요약/카드/리소스/에러·경고 데이터를 재사용해 고정폭 텍스트 보고서로 조합한다.
 *
 * 설계 이유:
 * - 폐쇄망 내부 보고용으로 별도 문서 편집기 없이 메모장으로 바로 열람 가능한 텍스트 포맷을 쓴다.
 * - 화면 조회 로직(DashboardService)을 그대로 재사용하고, 보고서에만 필요한 전일/전전일 비교값 조회는
 *   이 클래스에서 DashboardMapper를 직접 호출해 처리한다(DashboardService는 화면 조합 책임만 유지).
 */
@Service
@RequiredArgsConstructor
public class ReportService {
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DIVIDER = "-".repeat(80);
    private static final String DOUBLE_DIVIDER = "=".repeat(80);
    private static final String NA = "N/A";

    private static final Set<String> STOCK_LOG_IDS =
            Set.of("MBSOSI_COUNT", "MBFOSI_COUNT", "MBCOSI_COUNT", "MBJISU_COUNT", "NXT_COUNT", "OPT_MAX_COUNT");
    private static final Set<String> OVERSEAS_LOG_IDS =
            Set.of("OS_BA_COUNT", "OS_NB_COUNT", "OS_HK_COUNT", "OS_SH_COUNT", "OS_SZ_COUNT");
    private static final Set<String> SERVICE_LOG_IDS =
            Set.of("AUTO_ORDER_ACNT", "CAP_REG_COUNT", "CAP2_REG_COUNT", "FREQ_CLUB_COUNT");
    private static final List<String> CONN_LOG_IDS = List.of("MAX_CONN_PREV", "HTS_MAX_CONN", "MTS_MAX_CONN");
    private static final List<String> RESOURCE_LOG_IDS = List.of("DISK_HOME", "MEM_USAGE");
    /** RESOURCE_LOG_IDS를 getResourceData() 응답의 카드 하위 키(disk/mem)로 옮기는 표다. */
    private static final Map<String, String> RESOURCE_METRIC_KEYS = Map.of("DISK_HOME", "disk", "MEM_USAGE", "mem");
    private static final Map<String, String> RESOURCE_LABELS = Map.of("DISK_HOME", "DISK", "MEM_USAGE", "MEM");
    /** 종목/해외종목/서비스/접속자 현황 라벨 열의 목표 표시폭이다(가장 긴 라벨 "서버자동주문계좌수" 기준). */
    private static final int LABEL_DISPLAY_WIDTH = 18;

    private final DashboardService dashboardService;
    private final DashboardMapper dashboardMapper;
    private final InfoDataConfig infoDataConfig;

    /**
     * TODAY 현황 기준 점검 보고서 전문을 생성한다.
     *
     * @return 고정폭 정렬 텍스트 보고서 전문이다.
     */
    public String generateDailyReport() {
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder();

        SummaryDto summary = dashboardService.getSummary();

        appendHeader(sb, today);
        appendTodaySummary(sb, summary);
        appendErrorWarningDetail(sb);
        appendCollectAnalyzeStatus(sb, summary);
        appendInfoGroup(sb, "4", "종목 현황", "전일", STOCK_LOG_IDS, today.minusDays(1));
        appendInfoGroup(sb, "5", "해외 종목 현황", "전일", OVERSEAS_LOG_IDS, today.minusDays(1));
        appendInfoGroup(sb, "6", "서비스 현황", "전일", SERVICE_LOG_IDS, today.minusDays(1));
        appendConnStatus(sb);
        appendResourceWarnings(sb);

        return sb.toString();
    }

    private void appendHeader(StringBuilder sb, LocalDate today) {
        sb.append(DOUBLE_DIVIDER).append('\n');
        sb.append(center("PreCheck 점검 보고서")).append('\n');
        sb.append(DOUBLE_DIVIDER).append('\n');
        sb.append(" 생성일시 : ").append(LocalDateTime.now().format(DISPLAY_DATETIME)).append('\n');
        sb.append(" 대상일자 : ").append(today.format(DISPLAY_DATE)).append(" (TODAY)").append('\n');
        sb.append(DOUBLE_DIVIDER).append('\n');
        sb.append('\n');
    }

    private void appendTodaySummary(StringBuilder sb, SummaryDto summary) {
        sb.append("[1] 오늘의 점검 현황\n").append(DIVIDER).append('\n');
        sb.append(String.format(" 에러      : %6d 건%n", summary.getErrorCnt()));
        sb.append(String.format(" 경고      : %6d 건%n", summary.getWarnCnt()));
        sb.append(String.format(" 정상      : %6d 건%n", summary.getNormalCnt()));
        sb.append(String.format(" 정보      : %6d 건%n", summary.getInfoCnt()));
        sb.append(String.format(" 미분석    : %6d 건%n", summary.getUnknownCnt()));
        sb.append(DIVIDER).append('\n').append('\n');
    }

    private void appendCollectAnalyzeStatus(StringBuilder sb, SummaryDto summary) {
        sb.append("[3] 서버 점검 현황 (수집 / 분석)\n").append(DIVIDER).append('\n');
        sb.append(String.format(" 수집 : 성공 %d / 전체 %d (%.2f %%)   실패 %d   SKIP %d%n",
                summary.getCollectSuccess(), summary.getCollectTotal(), summary.getCollectRatio(),
                summary.getCollectFail(), summary.getCollectSkip()));
        sb.append(String.format(" 분석 : 성공 %d / 전체 %d (%.2f %%)   실패 %d%n",
                summary.getAnalyzeSuccess(), summary.getAnalyzeTotal(), summary.getAnalyzeRatio(),
                summary.getAnalyzeFail()));
        sb.append(DIVIDER).append('\n').append('\n');
    }

    /**
     * 종목/해외종목/서비스 현황 섹션을 조립한다.
     *
     * @param no 섹션 번호 표기 문자열이다.
     * @param title 섹션 제목이다.
     * @param compareLabel 비교 기준일 표기(`전일`)다.
     * @param targetLogIds 이 섹션에 포함할 LOG_ID 집합이다.
     * @param compareDate 비교 대상 일자(ANALYZE_DATE 기준)다.
     */
    private void appendInfoGroup(StringBuilder sb, String no, String title, String compareLabel,
                                  Set<String> targetLogIds, LocalDate compareDate) {
        Map<String, Object> allInfoData = dashboardService.getAllInfoData();
        String compareDateStr = compareDate.format(YYYYMMDD);

        sb.append("[").append(no).append("] ").append(title).append(" (").append(compareLabel).append(" 대비)\n");
        sb.append(DIVIDER).append('\n');
        for (InfoDataConfig.InfoDataItem item : infoDataConfig.getInfoData()) {
            if (!targetLogIds.contains(item.getLogId())) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> current = (Map<String, Object>) allInfoData.get(item.getLogId());
            BigDecimal currentValue = current == null ? null : (BigDecimal) current.get("logValue");

            AnalyzeResultDto prevRow = dashboardMapper.selectInfoData(compareDateStr, item.getServerId(), item.getLogId());
            BigDecimal prevValue = prevRow == null ? null : prevRow.getLogValue();

            sb.append(formatCompareLine(item.getName(), currentValue, compareLabel, prevValue));
        }
        sb.append(DIVIDER).append('\n').append('\n');
    }

    private void appendConnStatus(StringBuilder sb) {
        Map<String, Object> allInfoData = dashboardService.getAllInfoData();

        sb.append("[7] 접속자 현황 (전전일 대비)\n").append(DIVIDER).append('\n');
        for (InfoDataConfig.InfoDataItem item : infoDataConfig.getInfoData()) {
            if (!CONN_LOG_IDS.contains(item.getLogId())) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> current = (Map<String, Object>) allInfoData.get(item.getLogId());
            BigDecimal currentValue = current == null ? null : (BigDecimal) current.get("logValue");
            LocalDateTime currentTimestamp = current == null ? null : (LocalDateTime) current.get("logTimestamp");

            BigDecimal prevValue = null;
            if (currentTimestamp != null) {
                LocalDate prevDate = currentTimestamp.toLocalDate().minusDays(1);
                AnalyzeResultDto prevRow = dashboardMapper.selectConnInfoData(
                        item.getServerId(), item.getLogId(), prevDate.atStartOfDay(), prevDate.plusDays(1).atStartOfDay());
                prevValue = prevRow == null ? null : prevRow.getLogValue();
            }

            sb.append(formatCompareLine(item.getName(), currentValue, "전전일", prevValue));
        }
        sb.append(DIVIDER).append('\n').append('\n');
    }

    private String formatCompareLine(String label, BigDecimal current, String compareLabel, BigDecimal prev) {
        String currentText = current == null ? NA : formatNumber(current);
        String changeText;
        if (current == null || prev == null || prev.compareTo(BigDecimal.ZERO) == 0) {
            changeText = NA;
        } else {
            BigDecimal rate = current.subtract(prev)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(prev, 2, RoundingMode.HALF_UP);
            String arrow = rate.signum() > 0 ? "▲" : rate.signum() < 0 ? "▼" : "-";
            changeText = String.format("%s %s, %s %s%%", compareLabel, formatNumber(prev), arrow, formatNumber(rate.abs()));
        }
        return String.format(" %s : %12s   (%s)%n", padDisplay(label, LABEL_DISPLAY_WIDTH), currentText, changeText);
    }

    /**
     * BigDecimal 표시값에서 불필요한 후행 0을 제거한다(예: 14107.000000 -> 14107).
     *
     * @param value 표시할 수치다.
     * @return 지수 표기 없이 후행 0을 제거한 문자열이다.
     */
    private String formatNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * 라벨을 지정한 표시폭까지 공백으로 채운다.
     *
     * 설계 이유:
     * - `%-Ns` 포맷은 문자 수 기준으로 채우는데, 한글은 고정폭 글꼴에서 영문 2배 폭을 차지해
     *   종목/해외종목/서비스/접속자 현황처럼 한글·영문 라벨이 섞이면 세로줄이 어긋난다.
     *   그래서 문자 수 대신 실제 표시폭을 계산해 채운다.
     *
     * @param text 채울 대상 라벨이다.
     * @param width 목표 표시폭이다.
     * @return 표시폭 기준으로 오른쪽에 공백을 채운 문자열이다.
     */
    private String padDisplay(String text, int width) {
        int pad = Math.max(0, width - displayWidth(text));
        return text + " ".repeat(pad);
    }

    /**
     * 한글 음절을 폭 2, 그 외 문자를 폭 1로 계산한 표시폭을 반환한다.
     */
    private int displayWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            width += (c >= 0xAC00 && c <= 0xD7A3) ? 2 : 1;
        }
        return width;
    }

    private void appendResourceWarnings(StringBuilder sb) {
        List<Map<String, Object>> cards = dashboardService.getResourceData();
        sb.append("[8] 서버별 리소스 경고/에러 (DISK / MEM)\n").append(DIVIDER).append('\n');

        boolean found = false;
        for (Map<String, Object> card : cards) {
            for (String logId : RESOURCE_LOG_IDS) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metric = (Map<String, Object>) card.get(RESOURCE_METRIC_KEYS.get(logId));
                if (metric == null) {
                    continue;
                }
                String level = (String) metric.get("analyzeLevel");
                if (!"경고".equals(level) && !"에러".equals(level)) {
                    continue;
                }
                found = true;
                BigDecimal value = (BigDecimal) metric.get("logValue");
                BigDecimal threshold = (BigDecimal) metric.get("thresholdValue");
                sb.append(String.format(" [%s] %-9s : %-8s (임계 %-8s, %s)%n",
                        card.get("serverId"), RESOURCE_LABELS.get(logId),
                        value == null ? NA : formatNumber(value),
                        threshold == null ? NA : formatNumber(threshold),
                        level));
            }
        }
        if (!found) {
            sb.append("   경고/에러 없음\n");
        }
        sb.append(DIVIDER).append('\n').append('\n');
    }

    private void appendErrorWarningDetail(StringBuilder sb) {
        String today = LocalDate.now().format(YYYYMMDD);
        List<String> excludeLogIds = infoDataConfig.getDetailExcludeLogIds();
        int total = dashboardMapper.countErrorWarning(today, null, null, excludeLogIds);
        List<AnalyzeResultDto> rows = total <= 0
                ? List.of()
                : dashboardMapper.selectErrorWarningList(today, null, 0, total, null, excludeLogIds);

        sb.append("[2] 에러/경고 상세 목록 (전체 ").append(total).append("건)\n").append(DIVIDER).append('\n');
        if (rows.isEmpty()) {
            sb.append("   해당 없음\n");
        } else {
            sb.append(String.format(" %-4s %-4s %-22s %-16s %s%n", "NO", "레벨", "서버구분", "LOG_ID", "내용"));
            int no = 1;
            for (AnalyzeResultDto row : rows) {
                sb.append(String.format(" %-4d %-4s %-22s %-16s %s%n",
                        no++, row.getAnalyzeLevel(), row.getServerId(), row.getLogId(),
                        row.getAnalyzeMessage() == null ? "" : row.getAnalyzeMessage()));
            }
        }
        sb.append(DIVIDER).append('\n').append('\n');
    }

    private String center(String text) {
        int padding = Math.max(0, (80 - text.length()) / 2);
        return " ".repeat(padding) + text;
    }
}
