package com.example.memberssecurity.member.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/*
 * 로그인 무차별 대입 방지 — 아이디별 실패 횟수를 Redis에 기록
 * 15분 안에 5번 실패하면 그 아이디는 15분 동안 로그인 차단 (첫 실패 시점부터 15분)
 * IP 기준은 nginx 뒤라 remoteAddr이 전부 같고 X-Forwarded-For는 위조 가능해서 쓰지 않음
 */
@Service
public class LoginAttemptService {

    private static final String KEY_PREFIX = "LOGIN_FAIL:";
    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    private final RedisTemplate<String, String> redisTemplate;

    public LoginAttemptService(@Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isBlocked(String memberId) {
        if (memberId == null || memberId.isBlank()) return false;
        String count = redisTemplate.opsForValue().get(KEY_PREFIX + memberId);
        return count != null && Long.parseLong(count) >= MAX_FAILURES;
    }

    public void onFailure(String memberId) {
        if (memberId == null || memberId.isBlank()) return;
        String key = KEY_PREFIX + memberId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
    }

    public void onSuccess(String memberId) {
        if (memberId == null || memberId.isBlank()) return;
        redisTemplate.delete(KEY_PREFIX + memberId);
    }
}
