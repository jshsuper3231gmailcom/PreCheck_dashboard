package com.sks.precheck.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Download 목록 조회 응답.
 *
 * 설계 이유:
 * - 서버가 정규화한 현재 경로를 함께 내려 화면이 사용자 입력을 그대로 다음 요청에
 *   재사용하지 않고 서버 값을 신뢰하게 한다.
 */
@Getter
@AllArgsConstructor
public class DownloadListDto {
    private String path;
    private List<DownloadEntryDto> entries;
}
