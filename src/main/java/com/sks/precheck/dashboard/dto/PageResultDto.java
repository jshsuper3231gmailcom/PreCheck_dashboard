package com.sks.precheck.dashboard.dto;

import lombok.Getter;

import java.util.List;

/**
 * 대시보드 목록 조회용 페이지 응답 DTO.
 *
 * 역할:
 * - 현재 페이지 데이터와 전체 건수, 전체 페이지 수를 함께 전달한다.
 *
 * 설계 이유:
 * - 에러/경고 목록과 정상/정보/미분석 목록이 같은 페이징 구조를 공유하도록 공통 DTO로 분리했다.
 */
@Getter
public class PageResultDto<T> {
    private final List<T> items;
    private final int page;
    private final int pageSize;
    private final int totalCount;
    private final int totalPages;

    /**
     * 페이지 응답을 생성한다.
     *
     * @param items 현재 페이지에 포함된 목록이다.
     * @param page 현재 페이지 번호다.
     * @param pageSize 한 페이지에 보여줄 건수다.
     * @param totalCount 전체 조회 건수다.
     */
    public PageResultDto(List<T> items, int page, int pageSize, int totalCount) {
        this.items = items;
        this.page = page;
        this.pageSize = pageSize;
        this.totalCount = totalCount;
        this.totalPages = pageSize > 0 ? (int) Math.ceil((double) totalCount / pageSize) : 0;
    }
}
