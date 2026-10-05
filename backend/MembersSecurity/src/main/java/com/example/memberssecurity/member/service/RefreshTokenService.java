package com.example.memberssecurity.member.service;

import com.example.entitycom.entity.member.Members;
import com.example.entitycom.enums.Role;
import com.example.memberssecurity.member.repository.jpa.MemberRepository;
import com.example.memberssecurity.security.config.jwt.JWTUtils;
import com.example.memberssecurity.security.config.jwt.TokenPair;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class RefreshTokenService {
    private static final String KEY_PREFIX = "REFRESH_TOKEN:";
    private static final String PREV_PREFIX = "REFRESH_TOKEN_PREV:";
    // 탭 여러 개, 여러 서비스 동시 호출 등으로 같은 refresh가 동시에 들어올 때의 유예
    private static final Duration GRACE = Duration.ofSeconds(10);


    // 1.:  정상 회전 / 2: 방금 회전된 직전 토큰 (유예) / 0: 재사용 의심
    private static final RedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[4])
                return 1
               end 
            if redis.call('GET', KEYS[2]) == ARGV[1] then
                return 2
            end
            return 0
            
            """, Long.class);

    private final JWTUtils jwtUtils;
    private final RedisTemplate<String, String> redisTemplate;
    private final MemberRepository memberRepository;

    public RefreshTokenService(JWTUtils jwtUtils, @Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate, MemberRepository memberRepository) {
        this.jwtUtils = jwtUtils;
        this.redisTemplate = redisTemplate;
        this.memberRepository = memberRepository;
    }

    // 로그인 성공 시 (일반 소셜 Electron 공통)
    public TokenPair issue(Long memberKey, Role role) {
        String accessToken = jwtUtils.createAccessToken(memberKey, role);
        String refreshToken = jwtUtils.createRefreshToken(memberKey, role);
        redisTemplate.opsForValue().set(KEY_PREFIX + memberKey, refreshToken, JWTUtils.REFRESH_TTL);
        return new TokenPair(accessToken, refreshToken);
    }

    public Optional<TokenPair> rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return Optional.empty();

        Long memberKey;
        try {
            memberKey = JWTUtils.memberKey(jwtUtils.parse(refreshToken, JWTUtils.TYPE_REFRESH));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }

        Members member = memberRepository.findById(memberKey).orElse(null);
        if (member == null) {
            delete(memberKey);
            return Optional.empty();
        }
        String newAccess = jwtUtils.createAccessToken(memberKey, member.getRole());
        String newRefresh = jwtUtils.createRefreshToken(memberKey, member.getRole());

        Long result = redisTemplate.execute(ROTATE_SCRIPT, List.of(KEY_PREFIX + memberKey, PREV_PREFIX + memberKey), refreshToken, newRefresh, String.valueOf(JWTUtils.REFRESH_TTL.toMillis()), String.valueOf(GRACE.toMillis()));


        if (result != null && result == 1L) return Optional.of(new TokenPair(newAccess, newRefresh));
        if (result != null && result == 2L) return Optional.of(new TokenPair(newAccess, null));

        // 이미 폐기된 refresh가 다시 옴 = 탈취 의심 -> 이 회원의 세션 전체 폐기
        log.warn("폐기된 refresh token 재사용 감지 memberKey={}", memberKey);

        delete(memberKey);
        return Optional.empty();
    }

    public void delete(Long memberKey) {
        redisTemplate.delete(List.of(KEY_PREFIX + memberKey, PREV_PREFIX + memberKey));
    }

    /* 로그아웃: access는 jti 블랙리스트(남은 수명 동안), refresh는 Redis에서 삭제 → 둘 다 즉시 무효 */
    public void revoke(String accessToken, String refreshToken) {
        Long memberKey = null;
        if (accessToken != null) {
            try {
                Claims claims = jwtUtils.parse(accessToken, JWTUtils.TYPE_ACCESS);
                jwtUtils.blacklist(claims);
                memberKey = JWTUtils.memberKey(claims);
            } catch (ExpiredJwtException e) {
                memberKey = JWTUtils.memberKey(e.getClaims()); // 이미 만료된 access는 블랙리스트 불필요
            } catch (JwtException | IllegalArgumentException ignored) {
            }
        }
        if (memberKey == null && refreshToken != null) {
            try {
                memberKey = JWTUtils.memberKey(jwtUtils.parse(refreshToken, JWTUtils.TYPE_REFRESH));
            } catch (JwtException | IllegalArgumentException ignored) {
            }
        }
        if (memberKey != null) delete(memberKey);
    }
}
