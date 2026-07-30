package com.sks.precheck.dashboard;

import com.sks.precheck.dashboard.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 서버별 분석 현황(리소스 바차트) 응답 형태 검증.
 *
 * 검증 이유:
 * - selectResourceData가 서버 × 지표 조합으로 행을 주고 서비스가 서버 1건으로 묶는 구조라,
 *   VALUES 파생 테이블 CROSS JOIN과 그룹핑이 어긋나면 카드가 중복되거나 지표가 빠진다.
 * - 화면은 disk/mem 하위 맵이 항상 존재한다고 가정하고 렌더링하므로 그 계약을 여기서 고정한다.
 *
 * 전제: 로컬 PostgreSQL(test 프로파일)이 떠 있어야 한다. 오늘자 분석 결과가 없어도
 * 형태 검증은 성립하도록 값 자체는 단언하지 않는다.
 */
@SpringBootTest
class ResourceDataShapeTest {

    @Autowired
    private DashboardService dashboardService;

    @Test
    void 서버별로_disk와_mem_지표가_항상_채워진다() {
        List<Map<String, Object>> rows = dashboardService.getResourceData();
        assertNotNull(rows);

        // 서버 모수가 비어 있으면(이력 없는 새 환경) 검증할 대상이 없으므로 여기서 끝낸다.
        if (rows.isEmpty()) {
            return;
        }

        long distinctServers = rows.stream().map(r -> r.get("serverId")).distinct().count();
        assertEquals(rows.size(), distinctServers, "서버 1대당 정확히 1건이어야 한다(카드 중복 방지)");

        for (Map<String, Object> row : rows) {
            String serverId = String.valueOf(row.get("serverId"));

            assertNotNull(row.get("id"), serverId + ": id가 없다");
            assertNotNull(row.get("name"), serverId + ": name이 없다");
            assertInstanceOf(Boolean.class, row.get("noData"), serverId + ": noData가 boolean이 아니다");

            // serverId는 `ddfep01-해외시세` 형태이므로 첫 `-` 기준으로 분리되어야 한다.
            int sep = serverId.indexOf('-');
            if (sep >= 0) {
                assertEquals(serverId.substring(0, sep), row.get("id"));
                assertEquals(serverId.substring(sep + 1), row.get("name"));
            }

            boolean anyValue = false;
            for (String metricKey : List.of("disk", "mem")) {
                Object raw = row.get(metricKey);
                assertNotNull(raw, serverId + ": " + metricKey + " 지표 맵이 없다");
                assertInstanceOf(Map.class, raw);

                @SuppressWarnings("unchecked")
                Map<String, Object> metric = (Map<String, Object>) raw;
                // 값이 없어도 키는 존재해야 화면이 분기 없이 렌더링할 수 있다.
                assertTrue(metric.containsKey("logValue"), serverId + ": " + metricKey + " logValue 키 누락");
                assertTrue(metric.containsKey("analyzeLevel"), serverId + ": " + metricKey + " analyzeLevel 키 누락");
                assertTrue(metric.containsKey("thresholdValue"), serverId + ": " + metricKey + " thresholdValue 키 누락");
                assertNotNull(metric.get("logId"), serverId + ": " + metricKey + " logId 누락");

                if (metric.get("logValue") != null) {
                    anyValue = true;
                }
            }

            // noData는 "두 지표 모두 값이 없음"과 일치해야 한다(점선 카드 표기 기준).
            assertEquals(!anyValue, row.get("noData"), serverId + ": noData 판정이 지표 값과 어긋난다");
        }

        // 오늘자 분석 결과가 실제로 있는 환경이라면 최소 1대는 값이 채워져 있어야 한다.
        boolean anyServerHasData = rows.stream().anyMatch(r -> Boolean.FALSE.equals(r.get("noData")));
        assertFalse(rows.isEmpty() && anyServerHasData);
    }
}
