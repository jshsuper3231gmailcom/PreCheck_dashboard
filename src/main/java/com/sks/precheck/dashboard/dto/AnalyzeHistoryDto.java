package com.sks.precheck.dashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 분석 실행 이력 조회 DTO.
 *
 * 역할:
 * - 서버별 분석 성공/실패 상태와 마지막 분석 위치, 집계 건수를 표현한다.
 *
 * 설계 이유:
 * - Dashboard는 분석 결과뿐 아니라 "분석이 실제로 돌았는지"도 함께 보여줘야 하므로 실행 이력 구조를 분리한다.
 */
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
