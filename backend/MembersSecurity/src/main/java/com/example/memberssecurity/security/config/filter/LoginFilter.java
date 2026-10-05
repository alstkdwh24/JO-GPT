package com.example.memberssecurity.security.config.filter;

import com.example.entitycom.enums.Role;
import com.example.memberssecurity.member.service.LoginAttemptService;
import com.example.memberssecurity.member.service.RefreshTokenService;
import com.example.memberssecurity.security.config.dto.CustomUserDetails;
import com.example.memberssecurity.security.config.jwt.JWTUtils;
import com.example.memberssecurity.security.config.jwt.TokenCookies;
import com.example.memberssecurity.security.config.jwt.TokenPair;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

@Slf4j
public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private final JWTUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final TokenCookies tokenCookies;
    private final LoginAttemptService loginAttemptService;

    public LoginFilter(AuthenticationManager authenticationManager,
                       JWTUtils jwtUtils, RefreshTokenService refreshTokenService, TokenCookies tokenCookies,
                       LoginAttemptService loginAttemptService) {
        this.refreshTokenService = refreshTokenService;
        this.tokenCookies = tokenCookies;
        this.loginAttemptService = loginAttemptService;
        setFilterProcessesUrl("/api/auth/login");
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
    }
    /*
     * 로그인 요청 시 사용자 인증 처리
     */

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {
        String memberId = request.getParameter("memberId");
        String password = request.getParameter("password");

        // 무차별 대입 방지: 15분 안에 5번 실패한 아이디는 비밀번호 확인 자체를 안 함
        if (loginAttemptService.isBlocked(memberId)) {
            throw new LockedException("로그인 시도가 너무 많습니다.");
        }

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(memberId,
                password);

        return authenticationManager.authenticate(authenticationToken);
    }

    /*
     * 로그인 성공 시 JWT 토큰 발급
     */

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                                            Authentication auth) throws IOException {
        CustomUserDetails customUserDetails = (CustomUserDetails) auth.getPrincipal();
        loginAttemptService.onSuccess(request.getParameter("memberId"));

        assert customUserDetails != null;
        Long memberId = customUserDetails.getMemberId();


        // 사용자 역할 (Role) 조회
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        GrantedAuthority grantedAuthority = iterator.next();

        Role role = Role.valueOf(grantedAuthority.getAuthority());
        // access 30분 + refresh 14일(Redis 저장·회전)
        TokenPair tokens = refreshTokenService.issue(memberId, role);
        tokenCookies.write(response, tokens);
        // 토큰은 HttpOnly 쿠키로만 전달 (응답 헤더에 실으면 JS가 읽을 수 있어 HttpOnly 의미가 없어짐)
        // ===== 여기부터가 중요 =====
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String jsonResponse = """
                {
                  "message": "로그인 성공",
                  "memberId": "%s",
                  "role": "%s"
                
                }
                """.formatted(memberId, role);

        response.getWriter().write(jsonResponse);
    }

    /*
     * 로그인 실패 시 401 응답 반환
     */

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                              AuthenticationException failed) throws AuthenticationException {
        if (failed instanceof LockedException) {
            response.setStatus(429); // Too Many Requests — 차단 중엔 실패 횟수를 더 늘리지 않음
            response.setHeader("Retry-After", String.valueOf(LoginAttemptService.WINDOW.toSeconds()));
            return;
        }
        loginAttemptService.onFailure(request.getParameter("memberId"));
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 Unauthorized 응답
    }
}
