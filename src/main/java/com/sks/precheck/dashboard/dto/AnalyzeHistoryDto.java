package com.sks.precheck.dashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnalyzeHistoryDto {
    private Long analyzeHistoryId;
    private String serverId;
    private String sourceFilePath;
    private String analyzeStatus;
    private Long lastAnalyzeLogId;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private Integer errorCount;
    private Integer warningCount;
    private String failReason;
    private LocalDateTime analyzeStartAt;
    private LocalDateTime analyzeEndAt;
    private String analyzeDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
