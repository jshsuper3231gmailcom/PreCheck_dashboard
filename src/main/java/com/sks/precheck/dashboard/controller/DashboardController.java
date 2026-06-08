package com.sks.precheck.dashboard.controller;

import com.sks.precheck.dashboard.dto.AnalyzeResultDto;
import com.sks.precheck.dashboard.dto.CollectLogDto;
import com.sks.precheck.dashboard.dto.PageResultDto;
import com.sks.precheck.dashboard.dto.SummaryDto;
import com.sks.precheck.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard/index";
    }

    @ResponseBody
    @GetMapping("/dashboard/api/summary")
    public ApiResponse<SummaryDto> summary() {
        try {
            return ApiResponse.ok(dashboardService.getSummary());
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @ResponseBody
    @GetMapping("/dashboard/api/info-data")
    public ApiResponse<Map<String, Object>> infoData() {
        try {
            return ApiResponse.ok(dashboardService.getAllInfoData());
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @ResponseBody
    @GetMapping("/dashboard/api/error-list")
    public ApiResponse<PageResultDto<AnalyzeResultDto>> errorList(
            @RequestParam(value = "serverId", required = false) String serverId,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page
    ) {
        try {
            return ApiResponse.ok(dashboardService.getErrorWarningPage(serverId, page));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @ResponseBody
    @GetMapping("/dashboard/api/normal-list")
    public ApiResponse<PageResultDto<AnalyzeResultDto>> normalList(
            @RequestParam(value = "serverId", required = false) String serverId,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page
    ) {
        try {
            return ApiResponse.ok(dashboardService.getNormalInfoPage(serverId, page));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @ResponseBody
    @GetMapping("/dashboard/api/history")
    public ApiResponse<List<Map<String, Object>>> history(
            @RequestParam("groupType") String groupType
    ) {
        try {
            return ApiResponse.ok(dashboardService.getHistoryData(groupType));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @ResponseBody
    @GetMapping("/dashboard/api/resource")
    public ApiResponse<List<Map<String, Object>>> resource() {
        try {
            return ApiResponse.ok(dashboardService.getResourceData());
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @ResponseBody
    @GetMapping("/dashboard/api/server-list")
    public ApiResponse<List<Map<String, Object>>> serverList() {
        try {
            return ApiResponse.ok(dashboardService.getServerList());
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @ResponseBody
    @GetMapping("/dashboard/api/raw-log/{id}")
    public ApiResponse<CollectLogDto> rawLog(@PathVariable("id") Long id) {
        try {
            return ApiResponse.ok(dashboardService.getRawLog(id));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}
