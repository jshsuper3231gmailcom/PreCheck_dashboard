package com.sks.precheck.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Download 화면 목록 1건(폴더 또는 파일)을 표현하는 DTO.
 */
@Getter
@AllArgsConstructor
public class DownloadEntryDto {
    private String name;
    private boolean directory;
    private Long size;
    private LocalDateTime lastModified;
}
