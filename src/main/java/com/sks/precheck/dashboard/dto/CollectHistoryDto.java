package com.sks.precheck.dashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 수집 실행 이력 조회 DTO.
 *
 * 역할:
 * - 서버별 수집 성공, 실패, 제외 상태와 재시도 결과를 표현한다.
 *
 * 설계 이유:
 * - 수집 이력은 마지막 라인번호, 재시도, 영구 제외 판단과 연결되는 운영 정보라 분석 결과와 분리해 관리한다.
 */
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
