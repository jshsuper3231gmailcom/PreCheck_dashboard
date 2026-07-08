package com.sks.precheck.dashboard.controller;

import com.sks.precheck.dashboard.config.InfoDataConfig;
import com.sks.precheck.dashboard.dto.AnalyzeResultDto;
import com.sks.precheck.dashboard.dto.CollectLogDto;
import com.sks.precheck.dashboard.dto.PageResultDto;
import com.sks.precheck.dashboard.dto.SummaryDto;
import com.sks.precheck.dashboard.security.AdminUserPrincipal;
import com.sks.precheck.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
 * - 실패 시 원본 예외 메시지(DB/드라이버 상세 포함 가능)를 클라이언트에 그대로 내려보내지 않고,
 *   서버 로그에는 스택트레이스를 남기고 화면에는 일반화된 메시지만 전달한다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;
    private final InfoDataConfig infoDataConfig;

    /**
     * 조회 API 공통 예외 처리 래퍼.
     *
     * @param apiName 로그 식별용 API 이름이다.
     * @param supplier 실제 조회 로직이다.
     * @return 성공 시 데이터를 담은 응답, 실패 시 일반화된 오류 메시지를 담은 응답이다.
     */
    private <T> ApiResponse<T> handle(String apiName, Supplier<T> supplier) {
        try {
            return ApiResponse.ok(supplier.get());
        } catch (Exception e) {
            log.error("[DashboardController] {} 조회 중 오류", apiName, e);
            return ApiResponse.fail("조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 대시보드 메인 화면을 반환한다.
     *
     * @param principal 현재 로그인한 사용자다.
     * @param model 헤더에 표시할 사용자 정보를 담는 모델이다.
     * @return Thymeleaf 템플릿 경로다.
     */
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AdminUserPrincipal principal, Model model) {
        model.addAttribute("loginUserName", principal.getAdminUser().getUserName());
        model.addAttribute("loginUserRole", principal.getAdminUser().getRole());
        model.addAttribute("refreshIntervalSeconds", infoDataConfig.getRefreshIntervalSeconds());
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
        return handle("summary", dashboardService::getSummary);
    }

    /**
     * 주요 데이터 카드 조회 API.
     *
     * @return LOG_ID별 최신 분석 결과를 묶은 응답이다.
     */
    @ResponseBody
    @GetMapping("/dashboard/api/info-data")
    public ApiResponse<Map<String, Object>> infoData() {
        return handle("info-data", dashboardService::getAllInfoData);
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
        return handle("error-list", () -> dashboardService.getErrorWarningPage(serverId, page));
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
        return handle("normal-list", () -> dashboardService.getNormalInfoPage(serverId, page));
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
        return handle("history", () -> dashboardService.getHistoryData(groupType));
    }

    /**
     * 서버별 리소스 도넛 차트 조회 API.
     *
     * @return 서버별 최신 리소스 분석 결과 응답이다.
     */
    @ResponseBody
    @GetMapping("/dashboard/api/resource")
    public ApiResponse<List<Map<String, Object>>> resource() {
        return handle("resource", dashboardService::getResourceData);
    }

    /**
     * 서버 리스트 카드 조회 API.
     *
     * @return 서버별 최근 수집/분석 시각과 에러/경고 건수 응답이다.
     */
    @ResponseBody
    @GetMapping("/dashboard/api/server-list")
    public ApiResponse<List<Map<String, Object>>> serverList() {
        return handle("server-list", dashboardService::getServerList);
    }

    /**
     * UC 실시간 접속자수 스파크라인 조회 API.
     *
     * @return 오늘 UC_TOTAL_COUNT / UC_HTS_COUNT / UC_MTS_COUNT 시계열 응답이다.
     */
    @ResponseBody
    @GetMapping("/dashboard/api/uc-spark")
    public ApiResponse<Map<String, Object>> ucSpark() {
        return handle("uc-spark", dashboardService::getUcSparkData);
    }

    /**
     * History 페이지 화면을 반환한다.
     */
    @GetMapping("/dashboard/history")
    public String history(@AuthenticationPrincipal AdminUserPrincipal principal, Model model) {
        model.addAttribute("loginUserName", principal.getAdminUser().getUserName());
        model.addAttribute("loginUserRole", principal.getAdminUser().getRole());
        return "dashboard/history";
    }

    /**
     * History 페이지용 전체 그룹 월별 시계열 조회 API.
     */
    @ResponseBody
    @GetMapping("/dashboard/api/monthly-history")
    public ApiResponse<Map<String, Object>> monthlyHistory() {
        return handle("monthly-history", dashboardService::getMonthlyHistoryAll);
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
        return handle("raw-log", () -> dashboardService.getRawLog(id));
    }
}
