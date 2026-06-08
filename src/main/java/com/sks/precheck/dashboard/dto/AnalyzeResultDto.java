package com.sks.precheck.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 분석 결과 조회 DTO.
 *
 * 역할:
 * - 에러/경고 목록, 정상/정보/미분석 목록, 히스토리 그래프, 주요 데이터 카드가 공통으로 참조한다.
 *
 * 설계 이유:
 * - Dashboard는 TB_ANALYZE_RESULT를 여러 화면에서 재사용하므로, 분석 레벨과 임계치 정보를 함께 보관한다.
 * - `warningRatio`는 임계치 대비 근접도가 아니라 분석 결과 테이블에 저장된 warning_ratio 값을 그대로 담는다.
 */
@Data
public class AnalyzeResultDto {
    private Long analyzeResultId;
    private Long collectLogId;
    private String serverId;
    private String serverIp;
    private String logType;
    private String logId;
    private LocalDateTime logTimestamp;
    private String logContent;
    private BigDecimal logValue;
    private String analyzeLevel;
    private String analyzeMessage;
    private BigDecimal thresholdValue;
    private String thresholdOperator;
    private BigDecimal warningRatio;
    private String notifyYn;
    private LocalDateTime notifyAt;
    private String analyzeDate;
    private LocalDateTime analyzeDatetime;
    private String collectDate;
    private LocalDateTime createdAt;
}
