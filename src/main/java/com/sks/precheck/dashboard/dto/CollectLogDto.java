package com.sks.precheck.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 수집 로그 원문 조회 DTO.
 *
 * 역할:
 * - 원본 로그 모달에서 정규화 로그 원문과 수집 메타 정보를 함께 보여줄 때 사용한다.
 *
 * 설계 이유:
 * - 분석 결과 화면에서 장애 추적 시 수집 시각, 원본 파일, 원문 로그를 한 번에 확인할 수 있어야 한다.
 */
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
