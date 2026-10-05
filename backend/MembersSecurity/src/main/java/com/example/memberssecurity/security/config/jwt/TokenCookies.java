package com.example.memberssecurity.security.config.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class TokenCookies {

    public static final String ACCESS = "ACCESS_TOKEN";
    public static final String REFRESH = "REFRESH_TOKEN";

    // 프론트와 API가 같은 사이트(agentcloudllm.me)면 Lax.
    // 프론트가 다른 사이트(*.vercel.app 등)에서 쿠키로 API를 호출한다면 "None"으로 바꿔야 쿠키가 전송됨
    private static final String SAME_SITE = "Lax";

    public static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value == null || value.isBlank()) ? null : value.trim();
            }
        }
        return null;
    }

    // Electron은 Authorization 헤더, 웹은 쿠키
    public static String resolveAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            if (!token.isEmpty()) return token;
        }
        return read(request, ACCESS);
    }

    public void write(HttpServletResponse response, TokenPair tokenPair) {
        // access 쿠키도 refresh 수명만큼 유지 - 만료된 access가 있어야 필터가 재발급을 시도할수 있음
        // (실제 ㅁ만료 판단은 쿠키가 아니라 JWT exp가 함)

        add(response, ACCESS, tokenPair.accessToken(), JWTUtils.REFRESH_TTL);

        if (tokenPair.refreshToken() != null) {
            add(response, REFRESH, tokenPair.refreshToken(), JWTUtils.REFRESH_TTL);
        }
    }

    public void clear(HttpServletResponse response) {
        add(response, ACCESS, "", Duration.ZERO);
        add(response, REFRESH, "", Duration.ZERO);
    }

    private void add(HttpServletResponse response, String name, String value, Duration maxAge) {

        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite(SAME_SITE)
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    }

}
