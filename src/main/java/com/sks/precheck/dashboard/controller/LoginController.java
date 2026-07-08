package com.sks.precheck.dashboard.controller;

import com.sks.precheck.dashboard.security.AuthMessages;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 로그인 화면 컨트롤러.
 *
 * 역할:
 * - `/login` 화면을 반환하고, 인증 실패 사유에 따라 안내 메시지를 구성한다.
 *
 * 설계 이유:
 * - 8__로그인_보안정책정의서.md 6장: 잠긴 계정은 별도 메시지를 노출하고,
 *   그 외 실패(존재하지 않는 ID/비밀번호 불일치)는 동일한 일반 메시지로 통일해
 *   잔여 시도 횟수/잠금 정보를 노출하지 않는다.
 * - 메시지 문구는 {@link AuthMessages}에서 가져온다(AdminAuthenticationProvider와 공유).
 */
@Controller
public class LoginController {

    /**
     * 로그인 화면을 반환한다.
     *
     * @param error 인증 실패 시 전달되는 구분값("locked" 또는 그 외)이다.
     * @param model 화면에 표시할 오류 메시지를 담는다.
     * @return 로그인 화면 뷰 이름이다.
     */
    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error, Model model) {
        if (error != null) {
            String message = "locked".equals(error) ? AuthMessages.LOCKED_MESSAGE
                    : "disabled".equals(error) ? AuthMessages.DISABLED_MESSAGE
                    : AuthMessages.GENERIC_FAIL_MESSAGE;
            model.addAttribute("errorMessage", message);
        }
        return "login";
    }
}
