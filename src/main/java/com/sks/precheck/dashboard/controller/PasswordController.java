package com.sks.precheck.dashboard.controller;

import com.sks.precheck.dashboard.dto.AdminUserDto;
import com.sks.precheck.dashboard.mapper.AdminUserMapper;
import com.sks.precheck.dashboard.security.AdminUserPrincipal;
import com.sks.precheck.dashboard.service.PasswordPolicyValidator;
import com.sks.precheck.dashboard.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 본인 비밀번호 변경 화면 컨트롤러.
 *
 * 역할:
 * - `/password/change` 화면을 제공하고, 8__로그인_보안정책정의서.md 5장의
 *   복잡도/재사용 정책을 적용해 비밀번호 변경을 처리한다.
 *
 * 설계 이유:
 * - 만료/최초 로그인으로 강제 진입한 경우 "취소 불가"이므로, 만료 여부를 화면에 전달해
 *   취소 버튼 표시 여부를 결정한다.
 * - 변경 직후 SecurityContext의 인증 정보를 최신 상태로 교체해
 *   PasswordExpiryInterceptor가 갱신된 PASSWORD_CHANGED_AT을 즉시 인식하게 한다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PasswordController {

    private final AdminUserMapper adminUserMapper;
    private final PasswordService passwordService;

    /**
     * 비밀번호 변경 화면을 반환한다.
     *
     * @param principal 현재 로그인한 사용자다.
     * @param model 화면 표시용 모델이다.
     * @return 비밀번호 변경 화면 뷰 이름이다.
     */
    @GetMapping("/password/change")
    public String form(@AuthenticationPrincipal AdminUserPrincipal principal, Model model) {
        AdminUserDto user = adminUserMapper.selectByLoginId(principal.getUsername());
        model.addAttribute("userName", user.getUserName());
        model.addAttribute("forced", isExpired(user));
        return "password/change";
    }

    /**
     * 비밀번호 변경을 처리한다.
     *
     * @param principal 현재 로그인한 사용자다.
     * @param currentPassword 현재 비밀번호다.
     * @param newPassword 신규 비밀번호다.
     * @param confirmPassword 신규 비밀번호 확인 값이다.
     * @param request 감사 로그 IP 추출용 요청이다.
     * @param model 검증 실패 시 오류 메시지를 담는 모델이다.
     * @param redirectAttributes 변경 성공 메시지를 대시보드로 전달한다.
     * @return 성공 시 대시보드로 리다이렉트하고, 실패 시 입력 화면을 다시 보여준다.
     */
    @PostMapping("/password/change")
    public String change(@AuthenticationPrincipal AdminUserPrincipal principal,
                          @RequestParam("currentPassword") String currentPassword,
                          @RequestParam("newPassword") String newPassword,
                          @RequestParam("confirmPassword") String confirmPassword,
                          HttpServletRequest request,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        log.info("[PasswordController] POST /password/change called. user={}", principal != null ? principal.getUsername() : "NULL");
        AdminUserDto user = adminUserMapper.selectByLoginId(principal.getUsername());
        log.info("[PasswordController] DB user loaded: loginId={}, passwordChangedAt={}", user.getLoginId(), user.getPasswordChangedAt());

        try {
            passwordService.changeOwnPassword(user, currentPassword, newPassword, confirmPassword, request);
            log.info("[PasswordController] changeOwnPassword succeeded for user={}", user.getLoginId());
        } catch (Exception e) {
            log.warn("[PasswordController] changeOwnPassword failed for user={}: {}", user.getLoginId(), e.getMessage(), e);
            String msg = e.getMessage();
            model.addAttribute("errorMessage", msg != null ? msg : "처리 중 오류가 발생했습니다.");
            model.addAttribute("userName", user.getUserName());
            model.addAttribute("forced", isExpired(user));
            return "password/change";
        }

        refreshAuthentication(adminUserMapper.selectByLoginId(user.getLoginId()));
        redirectAttributes.addFlashAttribute("successMessage", "비밀번호가 변경되었습니다.");
        log.info("[PasswordController] Redirecting to /dashboard after password change for user={}", user.getLoginId());
        return "redirect:/dashboard";
    }

    /**
     * 비밀번호 변경 후 SecurityContext의 인증 정보를 최신 계정 상태로 교체한다.
     *
     * @param refreshedUser 변경된 비밀번호 정보가 반영된 최신 계정이다.
     */
    private void refreshAuthentication(AdminUserDto refreshedUser) {
        AdminUserPrincipal newPrincipal = new AdminUserPrincipal(refreshedUser);
        Authentication newAuth = new UsernamePasswordAuthenticationToken(newPrincipal, null, newPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }

    /**
     * 비밀번호 만료(90일) 여부를 판정한다.
     *
     * @param user 대상 계정이다.
     * @return 만료 정책이 적용되고 90일이 경과했으면 true다.
     */
    private boolean isExpired(AdminUserDto user) {
        return PasswordPolicyValidator.isExpired(user);
    }
}
