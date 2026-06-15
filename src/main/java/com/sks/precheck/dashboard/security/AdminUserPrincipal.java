package com.sks.precheck.dashboard.security;

import com.sks.precheck.dashboard.dto.AdminUserDto;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 인증 주체.
 *
 * 역할:
 * - 인증 성공 후 SecurityContext에 보관되는 사용자 정보를 TB_ADMIN_USER 기준으로 제공한다.
 *
 * 설계 이유:
 * - 잠금/비활성/만료 등 상태 판정은 AdminAuthenticationProvider에서 이미 검증을 마친 뒤
 *   인증 객체를 생성하므로, 이 클래스의 UserDetails 플래그는 항상 true로 둔다.
 * - 화면/인터셉터에서 ADMIN_USER_ID, USER_NAME, ROLE, 비밀번호 만료 정보 등에
 *   바로 접근할 수 있도록 원본 AdminUserDto를 그대로 보관한다.
 */
public class AdminUserPrincipal implements UserDetails {

    private final AdminUserDto adminUser;

    public AdminUserPrincipal(AdminUserDto adminUser) {
        this.adminUser = adminUser;
    }

    /**
     * 원본 계정 정보를 반환한다.
     *
     * @return TB_ADMIN_USER 매핑 DTO다.
     */
    public AdminUserDto getAdminUser() {
        return adminUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + adminUser.getRole()));
    }

    @Override
    public String getPassword() {
        return adminUser.getPassword();
    }

    @Override
    public String getUsername() {
        return adminUser.getLoginId();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
