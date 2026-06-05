package com.sks.precheck.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
