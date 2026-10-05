package com.example.memberssecurity.security.config.filter;

import com.example.entitycom.entity.member.Members;
import com.example.entitycom.enums.Role;
import com.example.memberssecurity.member.service.RefreshTokenService;
import com.example.memberssecurity.security.config.dto.CustomUserDetails;
import com.example.memberssecurity.security.config.jwt.JWTUtils;
import com.example.memberssecurity.security.config.jwt.TokenCookies;
import com.example.memberssecurity.security.config.jwt.TokenPair;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

    private final JWTUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final TokenCookies tokenCookies;

    // /auth/validate는 컨트롤러가 직접 검증·회전함 (여기서도 회전하면 refresh가 두 번 회전됨)
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/auth/validate".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        log.debug("Request URI: {}", request.getRequestURI());

        String token = TokenCookies.resolveAccessToken(request);

        // 토큰이 없으면 다음 필터로 (permitAll 경로)
        if (token == null || token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // JWT 기본 형식 가드
        if (token.chars().filter(ch -> ch == '.').count() != 2) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 서명·만료·type(access) 한 번에 검증 — type 누락/불일치는 parse에서 거부
            Claims claims = jwtUtils.parse(token, JWTUtils.TYPE_ACCESS);

            // 블랙리스트 확인 (로그아웃된 jti)
            if (jwtUtils.isBlacklisted(claims)) {
                writeErrorResponse(response, "{\"code\":\"BLACKLISTED\",\"message\":\"로그아웃된 토큰입니다.\"}");
                return;
            }

            setAuthentication(JWTUtils.memberKey(claims), JWTUtils.role(claims));

        } catch (ExpiredJwtException e) {
            log.debug("JWT 만료 → refresh 회전 시도");

            // Redis에 저장된 refresh와 대조·회전 (재사용 감지 시 세션 전체 폐기)
            Optional<TokenPair> rotated = refreshTokenService.rotate(TokenCookies.read(request, TokenCookies.REFRESH));
            if (rotated.isEmpty()) {
                writeErrorResponse(response, "{\"code\":\"EXPIRED_TOKEN\",\"message\":\"토큰이 만료되었습니다.\"}");
                return;
            }

            tokenCookies.write(response, rotated.get());
            Claims newClaims = jwtUtils.parse(rotated.get().accessToken(), JWTUtils.TYPE_ACCESS);
            setAuthentication(JWTUtils.memberKey(newClaims), JWTUtils.role(newClaims));

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 검증 실패: {}", e.getMessage());
            writeErrorResponse(response, "{\"code\":\"INVALID_TOKEN\",\"message\":\"토큰이 유효하지 않습니다.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(Long memberId, Role role) {
        Members members = Members.builder()
                .memberId(String.valueOf(memberId))
                .memberKey(memberId)
                .role(role)
                .build();

        CustomUserDetails customUserDetails = new CustomUserDetails(members);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                customUserDetails, null, customUserDetails.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void writeErrorResponse(HttpServletResponse response, String message) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(message);
    }
}
