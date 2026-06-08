package com.sks.precheck.dashboard.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class PageResultDto<T> {
    private final List<T> items;
    private final int page;
    private final int pageSize;
    private final int totalCount;
    private final int totalPages;

    public PageResultDto(List<T> items, int page, int pageSize, int totalCount) {
        this.items = items;
        this.page = page;
        this.pageSize = pageSize;
        this.totalCount = totalCount;
        this.totalPages = pageSize > 0 ? (int) Math.ceil((double) totalCount / pageSize) : 0;
    }
}
