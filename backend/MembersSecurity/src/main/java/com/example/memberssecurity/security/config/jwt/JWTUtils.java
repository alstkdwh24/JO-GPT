package com.example.memberssecurity.security.config.jwt;

import com.example.entitycom.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class JWTUtils {
    // H - 3 : access는 짧게,  "영구 로그인"은 refresh 회전으로 구현
    public static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    public static final Duration REFRESH_TTL = Duration.ofDays(14);

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final String BLACKLIST_PREFIX = "BLACKLIST:";

    private final SecretKey secretKey;
    private final RedisTemplate<String, String> redisTemplate;

    public JWTUtils(@Value("${spring.jwt.secret}") String secret,
                    @Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.redisTemplate = redisTemplate;
    }

    public String createAccessToken(Long memberKey, Role role) {
        return build(memberKey, role, TYPE_ACCESS, ACCESS_TTL);
    }

    public String createRefreshToken(Long memberKey, Role role) {
        return build(memberKey, role, TYPE_REFRESH, REFRESH_TTL);
    }

    private String build(Long memberKey, Role role, String type, Duration ttl) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())   // jti: 블랙리스트 키로 사용
                .claim("memberId", memberKey)
                .claim("role", role.name())
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(secretKey)
                .compact();
    }

    /*서명 만료 종류를 한 번에 검증
     * H-4 :  type 클레임이 없거나 다르면 무조건 거부 (null 통과 없음)
     * 만료 시 ExpiredJwtException을 그대로 던짐 -> 호출 측에서 재발급 분기
     * */

    public Claims parse(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token).getPayload();

        if (!expectedType.equals(claims.get("type", String.class))) {
            throw new JwtException("Invalid token");
        }
        return claims;
    }

    public void blacklist(Claims claims) {
        long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remaining > 0) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + claims.getId(), "logout", remaining, TimeUnit.MILLISECONDS); // remaining은 ms 단위 (MICROSECONDS면 1000배 빨리 풀림)
        }
    }

    public static Long memberKey(Claims claims) {
        return claims.get("memberId", Long.class);
    }

    public static Role role(Claims claims) {
        return Role.valueOf(claims.get("role", String.class));
    }

    public boolean isBlacklisted(Claims claims) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + claims.getId()));
    }
}
