package com.sks.precheck.dashboard.controller;

import com.sks.precheck.dashboard.dto.AdminUserDto;
import com.sks.precheck.dashboard.security.AdminUserPrincipal;
import com.sks.precheck.dashboard.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;

/**
 * SUPER_ADMIN 계정관리 화면/API 컨트롤러.
 *
 * 역할:
 * - `/admin/users` 화면을 제공하고, 계정 생성/잠금해제/활성화토글/비밀번호초기화 JSON API를 제공한다.
 *
 * 설계 이유:
 * - 이 경로는 SecurityConfig에서 `hasRole("SUPER_ADMIN")`으로 제한되므로,
 *   컨트롤러 내부에서는 별도 권한 체크 없이 AccountService로 위임한다.
 */
@Controller
@RequiredArgsConstructor
public class AdminUserController {

    private final AccountService accountService;

    /**
     * 계정관리 화면을 반환한다.
     *
     * @param model 계정 목록을 담는 모델이다.
     * @return 계정관리 화면 뷰 이름이다.
     */
    @GetMapping("/admin/users")
    public String users(Model model) {
        model.addAttribute("users", accountService.listUsers());
        return "admin/users";
    }

    /**
     * 신규 계정을 생성한다.
     *
     * @param req 로그인 ID/사용자명/권한/초기 비밀번호를 담은 요청이다.
     * @param principal 생성을 수행하는 SUPER_ADMIN이다.
     * @param request 감사 로그 IP 추출용 요청이다.
     * @return 생성 성공/실패 결과다.
     */
    @ResponseBody
    @PostMapping("/admin/users")
    public ApiResponse<Void> create(@RequestBody CreateUserRequest req,
                                     @AuthenticationPrincipal AdminUserPrincipal principal,
                                     HttpServletRequest request) {
        try {
            accountService.createUser(req.loginId(), req.userName(), req.role(), req.initialPassword(),
                    principal.getUsername(), request);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 계정 잠금을 즉시 해제한다.
     *
     * @param adminUserId 대상 계정 ID다.
     * @param principal 해제를 수행하는 SUPER_ADMIN이다.
     * @param request 감사 로그 IP 추출용 요청이다.
     * @return 처리 성공/실패 결과다.
     */
    @ResponseBody
    @PostMapping("/admin/users/{adminUserId}/unlock")
    public ApiResponse<Void> unlock(@PathVariable Long adminUserId,
                                     @AuthenticationPrincipal AdminUserPrincipal principal,
                                     HttpServletRequest request) {
        try {
            accountService.unlockUser(adminUserId, principal.getUsername(), request);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 계정을 활성화한다.
     *
     * @param adminUserId 대상 계정 ID다.
     * @param principal 변경을 수행하는 SUPER_ADMIN이다.
     * @param request 감사 로그 IP 추출용 요청이다.
     * @return 처리 성공/실패 결과다.
     */
    @ResponseBody
    @PostMapping("/admin/users/{adminUserId}/enable")
    public ApiResponse<Void> enable(@PathVariable Long adminUserId,
                                     @AuthenticationPrincipal AdminUserPrincipal principal,
                                     HttpServletRequest request) {
        try {
            accountService.setEnabled(adminUserId, true, principal.getUsername(), request);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 계정을 비활성화한다.
     *
     * @param adminUserId 대상 계정 ID다.
     * @param principal 변경을 수행하는 SUPER_ADMIN이다.
     * @param request 감사 로그 IP 추출용 요청이다.
     * @return 처리 성공/실패 결과다.
     */
    @ResponseBody
    @PostMapping("/admin/users/{adminUserId}/disable")
    public ApiResponse<Void> disable(@PathVariable Long adminUserId,
                                      @AuthenticationPrincipal AdminUserPrincipal principal,
                                      HttpServletRequest request) {
        try {
            accountService.setEnabled(adminUserId, false, principal.getUsername(), request);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 계정 비밀번호를 임시 비밀번호로 초기화한다.
     *
     * @param adminUserId 대상 계정 ID다.
     * @param req 임시 비밀번호를 담은 요청이다.
     * @param principal 초기화를 수행하는 SUPER_ADMIN이다.
     * @param request 감사 로그 IP 추출용 요청이다.
     * @return 처리 성공/실패 결과다.
     */
    @ResponseBody
    @PostMapping("/admin/users/{adminUserId}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long adminUserId,
                                            @RequestBody ResetPasswordRequest req,
                                            @AuthenticationPrincipal AdminUserPrincipal principal,
                                            HttpServletRequest request) {
        try {
            accountService.resetPassword(adminUserId, req.newPassword(), principal.getUsername(), request);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 계정 생성 요청 본문이다.
     */
    public record CreateUserRequest(String loginId, String userName, String role, String initialPassword) {
    }

    /**
     * 비밀번호 초기화 요청 본문이다.
     */
    public record ResetPasswordRequest(String newPassword) {
    }
}
