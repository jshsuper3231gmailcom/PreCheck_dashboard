package com.sks.precheck.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CollectLogDto {
    private Long collectLogId;
    private String serverId;
    private String serverIp;
    private String logType;
    private String logId;
    private LocalDateTime logTimestamp;
    private String logContent;
    private BigDecimal logValue;
    private String rawLog;
    private String sourceFilePath;
    private LocalDateTime collectDatetime;
    private String collectDate;
    private LocalDateTime createdAt;
}
