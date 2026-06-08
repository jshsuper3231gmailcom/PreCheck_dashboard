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

/**
 * Dashboard 화면과 조회 API 진입점을 제공하는 컨트롤러.
 *
 * 역할:
 * - 대시보드 HTML 진입 페이지를 반환한다.
 * - 화면이 주기적으로 호출하는 조회 API를 서비스로 위임한다.
 *
 * 설계 이유:
 * - Dashboard는 조회 전용이므로 각 API가 상태를 바꾸지 않게 얇은 위임 계층으로 유지한다.
 * - 예외는 공통 응답 형식으로 감싸 화면이 개별 실패를 식별할 수 있게 한다.
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    /**
     * 대시보드 메인 화면을 반환한다.
     *
     * @return Thymeleaf 템플릿 경로다.
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard/index";
    }

    /**
     * 상단 요약 스트립 조회 API.
     *
     * @return 수집/분석 요약과 실패 사유를 포함한 응답이다.
     */
    @ResponseBody
    @GetMapping("/dashboard/api/summary")
    public ApiResponse<SummaryDto> summary() {
        try {
            return ApiResponse.ok(dashboardService.getSummary());
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 주요 데이터 카드 조회 API.
     *
     * @return LOG_ID별 최신 분석 결과를 묶은 응답이다.
     */
    @ResponseBody
    @GetMapping("/dashboard/api/info-data")
    public ApiResponse<Map<String, Object>> infoData() {
        try {
            return ApiResponse.ok(dashboardService.getAllInfoData());
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 에러/경고 탭 목록 조회 API.
     *
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다.
     * @param page 화면 페이징 번호다.
     * @return 에러/경고 목록과 페이지 정보를 담은 응답이다.
     */
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

    /**
     * 정상/정보/미분석 탭 목록 조회 API.
     *
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다.
     * @param page 화면 페이징 번호다.
     * @return 정상/정보/미분석 목록과 페이지 정보를 담은 응답이다.
     */
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

    /**
     * 히스토리 그래프 조회 API.
     *
     * @param groupType 종목, 해외종목, 접속자 중 어떤 그래프를 조회할지 구분하는 값이다.
     * @return 시리즈별 시계열 데이터 응답이다.
     */
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

    /**
     * 서버별 리소스 도넛 차트 조회 API.
     *
     * @return 서버별 최신 리소스 분석 결과 응답이다.
     */
    @ResponseBody
    @GetMapping("/dashboard/api/resource")
    public ApiResponse<List<Map<String, Object>>> resource() {
        try {
            return ApiResponse.ok(dashboardService.getResourceData());
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 서버 리스트 카드 조회 API.
     *
     * @return 서버별 최근 수집/분석 시각과 에러/경고 건수 응답이다.
     */
    @ResponseBody
    @GetMapping("/dashboard/api/server-list")
    public ApiResponse<List<Map<String, Object>>> serverList() {
        try {
            return ApiResponse.ok(dashboardService.getServerList());
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 원본 정규화 로그 모달 조회 API.
     *
     * @param id 수집 로그 식별자다.
     * @return 원문 로그와 수집 메타 정보를 담은 응답이다.
     */
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
