package com.example.memberssecurity.security.config.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Pattern;

@Slf4j
@Component

// 로그인 과정에서 생성도히는 OAuth2AuthorizationRequest를 세션 대신 Redis에 저장하고 브라우저에는 랜덤 ID만 httpOnly 쿠키로 저장하는 구현입니다.
public class RedisOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String COOKIE_NAME = "oauth2_auth_request";
    private static final String KEY_PREFIX = "OAUTH2_AUTH_REQ";
    private static final Duration TTL = Duration.ofSeconds(180);

    // 32 바이트 랜덤 -> Base64URL(패딩 없음) = 43자
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisTemplate<String, String> redisTemplate;

    // Spring Security가 제공하는 OAuth2 Mixin + 허용목록 기반 타입 처리
    // -> 자바 직렬화를  쓰지 않음
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));

    public RedisOAuth2AuthorizationRequestRepository(
            @Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static String newId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void writeCookie(HttpServletResponse response, String cookieValue, Duration ttl) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, cookieValue)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(ttl)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    }


    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String id = readId(request);
        if (id == null) return null;
        return fromJson(redisTemplate.opsForValue().get(KEY_PREFIX + id));
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {

        if (authorizationRequest == null) {
            String id = readId(request);
            if (id != null) redisTemplate.delete(KEY_PREFIX + id);
            writeCookie(response, "", Duration.ZERO);
            return;
        }
        String id = newId();
        try {
            String json = objectMapper.writeValueAsString(authorizationRequest);
            redisTemplate.opsForValue().set(KEY_PREFIX + id, json, Duration.ofSeconds(TTL.getSeconds()));
        } catch (Exception e) {
            throw new IllegalStateException("OAuth2 인가 요청 저장 실패", e);
        }
        writeCookie(response, id, TTL);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        String id = readId(request);
        if (id == null) return null;

        // 꺼내면서 동시에 삭제 -> 같은 state 재사용 (replay) 차단
        String json = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + id);
        writeCookie(response, "", Duration.ZERO);
        return fromJson(json);
    }

    private OAuth2AuthorizationRequest fromJson(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, OAuth2AuthorizationRequest.class);
        } catch (Exception e) {
            log.warn("OAuth2 인가 요청 복원 실패: {}", e.getMessage());
            return null;
        }
    }

    private String readId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                // 형식이 다르면 Redis 조회도 하지 않음
                return (value != null && ID_PATTERN.matcher(value).matches()) ? value : null;
            }
        }
        return null;
    }
}
