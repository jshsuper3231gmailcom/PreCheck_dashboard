package com.sks.precheck.dashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CollectHistoryDto {
    private Long collectHistoryId;
    private String serverId;
    private String sourceFilePath;
    private String collectStatus;
    private Integer collectedCount;
    private Integer retryCount;
    private String failReason;
    private LocalDateTime collectStartAt;
    private LocalDateTime collectEndAt;
    private String collectDate;
    private LocalDateTime createdAt;
}
