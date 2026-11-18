package com.example.memberssecurity.security.config.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 로그아웃 블랙리스트 TTL이 토큰의 남은 수명과 같은지 확인한다.
 * 예전에 단위를 MICROSECONDS로 넘겨 30분 차단이 1.8초로 줄었던 버그(2ace494 V-10)의 회귀 테스트.
 */
class JWTUtilsBlacklistTtlTest {

    // 테스트 전용 서명 키(32바이트 이상). 운영 비밀값이 아니다.
    private static final String TEST_KEY = "test-only-signing-key-0123456789abcdef";
    private static final int TOKENS = 10;
    private static final long TOLERANCE_MS = 1_000;

    private ValueOperations<String, String> ops;
    private JWTUtils jwtUtils;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        jwtUtils = new JWTUtils(TEST_KEY, redis);
    }

    @Test
    void 남은_수명이_다른_토큰_10개_모두_만료_시점까지_블랙리스트에_남는다() {
        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unit = ArgumentCaptor.forClass(TimeUnit.class);
        long minBlockedMs = Long.MAX_VALUE;

        for (int i = 1; i <= TOKENS; i++) {
            long remainingMs = i * 3L * 60_000;          // 3분 ~ 30분(액세스 토큰 최대 수명)
            jwtUtils.blacklist(claims("jti-" + i, remainingMs));

            verify(ops).set(eq("BLACKLIST:jti-" + i), eq("logout"), ttl.capture(), unit.capture());
            long blockedMs = unit.getValue().toMillis(ttl.getValue());
            assertThat(blockedMs).isBetween(remainingMs - TOLERANCE_MS, remainingMs);
            minBlockedMs = Math.min(minBlockedMs, blockedMs);
        }

        // 30분 토큰을 MICROSECONDS로 넘겼다면: 1,800,000µs = 1.8초
        System.out.printf("[블랙리스트 TTL] 토큰 %d개 모두 남은 수명 -1초 이내로 차단, 가장 짧은 차단 %.1f분 (MICROSECONDS였다면 30분 토큰도 %.1f초)%n",
                TOKENS, minBlockedMs / 60_000.0, TimeUnit.MICROSECONDS.toMillis(30 * 60_000L) / 1000.0);
    }

    @Test
    void 이미_만료된_토큰은_블랙리스트에_넣지_않는다() {
        jwtUtils.blacklist(claims("expired", -1_000));
        verify(ops, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    private Claims claims(String jti, long remainingMs) {
        return Jwts.claims()
                .id(jti)
                .expiration(new Date(System.currentTimeMillis() + remainingMs))
                .build();
    }
}
