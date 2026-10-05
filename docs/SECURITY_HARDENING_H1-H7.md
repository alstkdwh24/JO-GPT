# 보안 강화 가이드: H-1 ~ H-7

> 작성일: 2026-10-04
> 대상: `MembersSecurity`, `JO_GPT_PROGRAM`, `EntityCom`, `docker/`, Electron(`frontend/PC-JO-GPT-UI`)

## 목차

1. [개요와 설계 방향](#1-개요와-설계-방향)
2. [H-3, H-4: JWTUtils](#2-h-3-h-4-jwtutils)
3. [TokenPair, TokenCookies](#3-tokenpair-tokencookies)
4. [H-2: RefreshTokenService](#4-h-2-refreshtokenservice)
5. [H-1~H-4: JWTFilter](#5-h-1h-4-jwtfilter)
6. [H-1: TokenLogoutHandler와 SecurityConfig](#6-h-1-tokenlogouthandler와-securityconfig)
7. [로그인 3곳, AuthValidateController, RefreshController](#7-로그인-3곳-authvalidatecontroller-refreshcontroller)
8. [JO_GPT의 JwtDelegateFilter](#8-jo_gpt의-jwtdelegatefilter)
9. [H-5: Electron 로그인을 일회용 코드와 PKCE로 교체](#9-h-5-electron-로그인을-일회용-코드와-pkce로-교체)
10. [H-6: AesEncryptConverter (AES-GCM)](#10-h-6-aesencryptconverter-aes-gcm)
11. [H-7: Docker 포트 바인딩](#11-h-7-docker-포트-바인딩)
12. [적용 순서와 체크리스트](#12-적용-순서와-체크리스트)

---

## 1. 개요와 설계 방향

| ID | 문제 | 해결 |
|---|---|---|
| H-1 | 로그아웃해도 토큰이 블랙리스트에 등록되지 않음 | 로그아웃 핸들러가 요청에서 토큰을 직접 꺼내 jti를 블랙리스트에 등록하고 refresh를 폐기 |
| H-2 | JWTFilter와 `/auth/validate`가 Redis 대조 없이 재발급 | 재발급은 `RefreshTokenService.rotate()` 한 곳에서만 처리. Lua 스크립트로 원자적 회전 |
| H-3 | access token 유효기간 400일 | access 30분, refresh 14일 |
| H-4 | `type` 클레임이 없는 토큰도 통과 | `JWTUtils.parse(token, expectedType)`로 엄격하게 검사 |
| H-5 | Electron 리다이렉트 URL에 토큰 노출, 토큰을 로그에 기록 | 일회용 code와 PKCE로 교환, 토큰 로그 삭제 |
| H-6 | AES 기본 키 하드코딩, IV 고정 | AES-GCM과 랜덤 IV, 키가 없으면 기동 실패, 레거시 데이터 재암호화 |
| H-7 | ES, Chroma, MySQL 포트가 외부에 열려 있음 | `127.0.0.1` 바인딩, ES 인증 활성화, MySQL 볼륨 추가 |

### 왜 H-1~H-4를 한 번에 고치는가

토큰 생성 코드가 6곳, 쿠키 생성 코드가 8곳에 흩어져 있었습니다. 그래서 한 군데를 고치면 다른 곳이 구멍으로 남습니다. H-2가 생긴 원인도 이것입니다. 아래처럼 책임을 세 클래스로 모읍니다.

```
JWTUtils            : 토큰 생성·파싱·type 검사·블랙리스트 (jti 기반)
RefreshTokenService : 발급(issue) / 회전(rotate) / 폐기(delete)  ← 재발급 경로는 여기 하나뿐
TokenCookies        : 쿠키 쓰기·읽기·삭제 (SameSite 포함)
JWTFilter / AuthValidateController / RefreshController / LogoutHandler : 위 세 개만 호출
```

> ⚠️ **배포 영향:** 기존 400일 토큰에는 `type`과 `jti` 클레임이 없어서, 배포하는 순간 **모든 사용자가 한 번 다시 로그인해야 합니다.** 기존 토큰이 로그에 남았을 가능성(H-5)도 있으니 `spring.jwt.secret`도 새 랜덤 값(32바이트 이상)으로 바꾸길 권합니다.

---

## 2. H-3, H-4: JWTUtils

`security/config/jwt/JWTUtils.java` 전체 교체

```java
package com.example.memberssecurity.security.config.jwt;

import com.example.entitycom.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
public class JWTUtils {

    // H-3: access는 짧게, "영구 로그인"은 refresh 회전으로 구현
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

    /**
     * 서명·만료·종류를 한 번에 검증.
     * H-4: type 클레임이 없거나 다르면 무조건 거부 (null 통과 없음)
     * 만료 시 ExpiredJwtException을 그대로 던짐 → 호출 측에서 재발급 분기
     */
    public Claims parse(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!expectedType.equals(claims.get("type", String.class))) {
            throw new JwtException("토큰 종류가 올바르지 않습니다.");
        }
        return claims;
    }

    // H-1: 토큰 원문 대신 jti를 키로 저장 (키 길이·메모리 절약, 로그에 토큰 원문이 남지 않음)
    public void blacklist(Claims accessClaims) {
        long remaining = accessClaims.getExpiration().getTime() - System.currentTimeMillis();
        if (remaining > 0) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + accessClaims.getId(), "logout",
                    remaining, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isBlacklisted(Claims accessClaims) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + accessClaims.getId()));
    }
}
```

---

## 3. TokenPair, TokenCookies

### `security/config/jwt/TokenPair.java` (새 파일)

```java
package com.example.memberssecurity.security.config.jwt;

// refreshToken이 null이면 access만 재발급된 경우 (동시 요청 유예 구간)
public record TokenPair(String accessToken, String refreshToken) {}
```

### `security/config/jwt/TokenCookies.java` (새 파일)

> `ResponseCookie`는 생성자가 private입니다. 반드시 `ResponseCookie.from(name, value)` 빌더로 만들어야 합니다.

```java
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

    public void write(HttpServletResponse response, TokenPair pair) {
        // access 쿠키도 refresh 수명만큼 유지 — 만료된 access가 있어야 필터가 재발급을 시도할 수 있음
        // (실제 만료 판단은 쿠키가 아니라 JWT exp가 함)
        add(response, ACCESS, pair.accessToken(), JWTUtils.REFRESH_TTL);
        if (pair.refreshToken() != null) {
            add(response, REFRESH, pair.refreshToken(), JWTUtils.REFRESH_TTL);
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
}
```

---

## 4. H-2: RefreshTokenService

`member/service/RefreshTokenService.java` 전체 교체. 재발급은 이 경로 하나뿐입니다.

### 동시 요청 문제

access 토큰이 만료되는 순간 브라우저가 API를 3개 동시에 부르면, 같은 refresh 토큰으로 회전 요청도 3개가 동시에 들어옵니다. 단순히 GET으로 비교하고 SET으로 저장하면 두 번째 요청이 "재사용"으로 판정되어 **정상 사용자가 로그아웃**됩니다. 그래서 다음 두 가지를 둡니다.

- Lua 스크립트로 비교와 교체를 **원자적**으로 처리
- 직전 토큰에 **10초 유예**

```java
package com.example.memberssecurity.member.service;

import com.example.entitycom.entity.member.Members;
import com.example.entitycom.enums.Role;
import com.example.memberssecurity.member.repository.jpa.MemberRepository;
import com.example.memberssecurity.security.config.jwt.JWTUtils;
import com.example.memberssecurity.security.config.jwt.TokenPair;
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

    // 1: 정상 회전 / 2: 방금 회전된 직전 토큰(유예) / 0: 재사용 의심
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

    public RefreshTokenService(JWTUtils jwtUtils,
                               @Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate,
                               MemberRepository memberRepository) {
        this.jwtUtils = jwtUtils;
        this.redisTemplate = redisTemplate;
        this.memberRepository = memberRepository;
    }

    // 로그인 성공 시 (일반·소셜·Electron 공통)
    public TokenPair issue(Long memberKey, Role role) {
        String access = jwtUtils.createAccessToken(memberKey, role);
        String refresh = jwtUtils.createRefreshToken(memberKey, role);
        redisTemplate.opsForValue().set(KEY_PREFIX + memberKey, refresh, JWTUtils.REFRESH_TTL);
        return new TokenPair(access, refresh);
    }

    public Optional<TokenPair> rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return Optional.empty();

        Long memberKey;
        try {
            memberKey = jwtUtils.parse(refreshToken, JWTUtils.TYPE_REFRESH).get("memberId", Long.class);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }

        // role·탈퇴 여부는 토큰 클레임이 아니라 DB 기준 (강등·정지 즉시 반영)
        Members member = memberRepository.findById(memberKey).orElse(null);
        if (member == null) {
            delete(memberKey);
            return Optional.empty();
        }

        String newAccess = jwtUtils.createAccessToken(memberKey, member.getRole());
        String newRefresh = jwtUtils.createRefreshToken(memberKey, member.getRole());

        Long result = redisTemplate.execute(ROTATE_SCRIPT,
                List.of(KEY_PREFIX + memberKey, PREV_PREFIX + memberKey),
                refreshToken, newRefresh,
                String.valueOf(JWTUtils.REFRESH_TTL.toMillis()), String.valueOf(GRACE.toMillis()));

        if (result != null && result == 1L) return Optional.of(new TokenPair(newAccess, newRefresh));
        if (result != null && result == 2L) return Optional.of(new TokenPair(newAccess, null));

        // 이미 폐기된 refresh가 다시 옴 = 탈취 의심 → 이 회원의 세션 전체 폐기
        log.warn("폐기된 refresh token 재사용 감지 memberKey={}", memberKey);
        delete(memberKey);
        return Optional.empty();
    }

    public void delete(Long memberKey) {
        redisTemplate.delete(List.of(KEY_PREFIX + memberKey, PREV_PREFIX + memberKey));
    }
}
```

---

## 5. H-1~H-4: JWTFilter

`security/config/filter/JWTFilter.java`

```java
@Slf4j
@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

    private final JWTUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final TokenCookies tokenCookies;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String accessToken = TokenCookies.resolveAccessToken(request);
        if (accessToken == null) {
            filterChain.doFilter(request, response);   // permitAll 경로는 그대로 통과
            return;
        }

        try {
            Claims claims = jwtUtils.parse(accessToken, JWTUtils.TYPE_ACCESS);
            if (jwtUtils.isBlacklisted(claims)) {
                writeError(response, "BLACKLISTED", "로그아웃된 토큰입니다.");
                return;
            }
            setAuthentication(claims);

        } catch (ExpiredJwtException e) {
            // H-2: 재발급은 RefreshTokenService.rotate() 한 곳에서만
            Optional<TokenPair> rotated = refreshTokenService.rotate(TokenCookies.read(request, TokenCookies.REFRESH));
            if (rotated.isEmpty()) {
                writeError(response, "EXPIRED_TOKEN", "토큰이 만료되었습니다.");
                return;
            }
            tokenCookies.write(response, rotated.get());
            setAuthentication(jwtUtils.parse(rotated.get().accessToken(), JWTUtils.TYPE_ACCESS));

        } catch (JwtException | IllegalArgumentException e) {
            writeError(response, "INVALID_TOKEN", "토큰이 유효하지 않습니다.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(Claims claims) {
        Long memberKey = claims.get("memberId", Long.class);
        Members members = Members.builder()
                .memberId(String.valueOf(memberKey))
                .memberKey(memberKey)
                .role(Role.valueOf(claims.get("role", String.class)))
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(members);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    private void writeError(HttpServletResponse response, String code, String message) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
```

---

## 6. H-1: TokenLogoutHandler와 SecurityConfig

### 현재 로그아웃이 동작하지 않는 근본 원인

`LogoutFilter`는 `JWTFilter`보다 **먼저** 실행됩니다. 그래서 로그아웃 시점에는 `authentication`이 항상 null이고, `invalidateToken(authentication)`은 바로 return합니다. 토큰은 **요청에서 직접** 꺼내야 합니다.

### `security/config/handler/TokenLogoutHandler.java` (새 파일)

```java
package com.example.memberssecurity.security.config.handler;

@Component
@RequiredArgsConstructor
public class TokenLogoutHandler implements LogoutHandler {

    private final JWTUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final TokenCookies tokenCookies;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        Long memberKey = null;

        String accessToken = TokenCookies.resolveAccessToken(request);
        if (accessToken != null) {
            try {
                Claims claims = jwtUtils.parse(accessToken, JWTUtils.TYPE_ACCESS);
                jwtUtils.blacklist(claims);   // 남은 수명 동안 access 차단
                memberKey = claims.get("memberId", Long.class);
            } catch (JwtException | IllegalArgumentException ignored) {
                // 이미 만료된 access는 블랙리스트 불필요
            }
        }
        if (memberKey == null) {
            try {
                String refresh = TokenCookies.read(request, TokenCookies.REFRESH);
                if (refresh != null) {
                    memberKey = jwtUtils.parse(refresh, JWTUtils.TYPE_REFRESH).get("memberId", Long.class);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
            }
        }

        if (memberKey != null) refreshTokenService.delete(memberKey);   // refresh 폐기 → 재발급 불가
        tokenCookies.clear(response);
    }
}
```

### `SecurityConfig` 변경 부분

```java
    private final TokenCookies tokenCookies;               // 추가
    private final TokenLogoutHandler tokenLogoutHandler;   // 추가
...
            corsConfiguration.setExposedHeaders(List.of());   // H-5: Authorization 헤더를 JS에 노출하지 않음
...
                .addFilterBefore(new JWTFilter(jwtUtils, refreshTokenService, tokenCookies),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new LoginFilter(authenticationManager(), refreshTokenService, tokenCookies),
                        JWTFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .logout(logout -> logout
                        // M-2: GET 로그아웃(<img src>로 강제 로그아웃) 차단
                        .logoutRequestMatcher(new AntPathRequestMatcher("/login/logout", "POST"))
                        .addLogoutHandler(tokenLogoutHandler)
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_NO_CONTENT)))
```

- `MemberController`의 `@GetMapping("/logout")`은 **지웁니다.** `LogoutFilter`와 경로가 같아서 겹치는 데다 GET 방식입니다.
- 프론트의 로그아웃 호출도 `POST`로 바꿔야 합니다.

---

## 7. 로그인 3곳, AuthValidateController, RefreshController

### 로그인 3곳: 발급을 `issue()` 하나로 통일

`LoginFilter.successfulAuthentication`, `MemberController.login` 공통

```java
TokenPair pair = refreshTokenService.issue(memberKey, role);
tokenCookies.write(response, pair);
// response.addHeader("Authorization", ...) 는 삭제 (H-5)
```

`OAuth2LoginSuccessHandler`(웹)는 [9장](#9-h-5-electron-로그인을-일회용-코드와-pkce로-교체)을 참고하세요.

### `AuthValidateController`: 다른 백엔드 서비스(JO_GPT 등)가 호출하는 검증 API

```java
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthValidateController {

    private final JWTUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final TokenCookies tokenCookies;

    @GetMapping("/validate")
    public ResponseEntity<UserInfoDto> validate(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = TokenCookies.resolveAccessToken(request);
        if (accessToken != null) {
            try {
                Claims claims = jwtUtils.parse(accessToken, JWTUtils.TYPE_ACCESS);
                if (jwtUtils.isBlacklisted(claims)) return ResponseEntity.status(401).build();
                return ResponseEntity.ok(toDto(claims));
            } catch (ExpiredJwtException e) {
                // 아래 재발급으로
            } catch (JwtException | IllegalArgumentException e) {
                return ResponseEntity.status(401).build();
            }
        }

        return refreshTokenService.rotate(TokenCookies.read(request, TokenCookies.REFRESH))
                .map(pair -> {
                    tokenCookies.write(response, pair);
                    return ResponseEntity.ok(toDto(jwtUtils.parse(pair.accessToken(), JWTUtils.TYPE_ACCESS)));
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    private UserInfoDto toDto(Claims claims) {
        return UserInfoDto.builder()
                .memberId(String.valueOf(claims.get("memberId", Long.class)))
                .role(Role.valueOf(claims.get("role", String.class)))
                .build();
    }
}
```

### `RefreshController`

```java
    @PostMapping("/login/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        return refreshTokenService.rotate(TokenCookies.read(request, TokenCookies.REFRESH))
                .map(pair -> {
                    tokenCookies.write(response, pair);
                    return ResponseEntity.noContent().<Void>build();   // 토큰을 body에 싣지 않음 (H-5)
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
```

> 프론트가 기존 `/refreshToken/login/refresh` 응답 body에서 토큰을 읽고 있었다면 함께 수정해야 합니다.

---

## 8. JO_GPT의 JwtDelegateFilter

인증 서버에 검증을 위임하는 필터에 적용합니다.

재발급하면 쿠키가 **2개**(access, refresh) 내려옵니다. 그런데 기존 코드는 `getHeaderField("Set-Cookie")`로 **첫 번째 쿠키만** 브라우저에 전달합니다. 이대로면 refresh 쿠키가 갱신되지 않아서, 다음 재발급 때 "재사용"으로 판정되고 로그아웃됩니다.

```java
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);   // "Bearer null" 방지
            }
            if (refreshToken != null && !refreshToken.isEmpty()) {
                conn.setRequestProperty("Cookie", "REFRESH_TOKEN=" + refreshToken);
            }
...
            List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
            if (setCookies != null) {
                setCookies.forEach(c -> response.addHeader("Set-Cookie", c));   // 전부 전달
                // 쿠키 값(토큰)은 로그에 남기지 않음 (H-5)
            }
```

---

## 9. H-5: Electron 로그인을 일회용 코드와 PKCE로 교체

### 지금의 문제

`jo-gpt://auth?token=...&refreshtoken=...`처럼 커스텀 스킴에 토큰을 실으면, 같은 스킴을 등록한 다른 앱이 토큰을 가로챌 수 있습니다. 토큰이 OS 로그와 브라우저 기록에도 남습니다.

### 흐름

```
Electron main            MembersSecurity                 소셜 로그인
     │ verifier 생성             │                              │
     │ challenge=S256(verifier)  │                              │
     ├── open /oauth2/authorization/google?client=electron&code_challenge=… ──▶
     │                           │◀────────── 로그인 완료 ──────────┤
     │◀── jo-gpt://auth?code=… (60초, 1회용) ─┤                     │
     ├── POST /auth/electron/token {code, codeVerifier} ──▶        │
     │◀── {accessToken, refreshToken} ────────┤                     │
```

URL에는 60초짜리 일회용 `code`만 싣습니다. 토큰은 그 코드를 교환할 때만 발급합니다. PKCE의 `code_verifier`는 로그인을 시작한 앱만 알고 있어서, 코드를 가로챈 앱은 교환할 수 없습니다.

### `ClientTypeFilter`

```java
public class ClientTypeFilter extends OncePerRequestFilter {

    // S256 challenge = base64url(sha256(verifier)), 패딩 없음 = 43자
    private static final Pattern CHALLENGE = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/oauth2/authorization/")
                && "electron".equals(request.getParameter("client"))) {
            String challenge = request.getParameter("code_challenge");
            if (challenge == null || !CHALLENGE.matcher(challenge).matches()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "code_challenge가 필요합니다.");
                return;
            }
            HttpSession session = request.getSession();
            session.setAttribute("client", "electron");
            session.setAttribute("code_challenge", challenge);
        }
        filterChain.doFilter(request, response);
    }
}
```

### `ElectronLoginCodeService` (새 파일)

```java
@Service
public class ElectronLoginCodeService {

    private static final String PREFIX = "ELECTRON_CODE:";
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisTemplate<String, String> redisTemplate;

    public ElectronLoginCodeService(@Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String issue(Long memberKey, String codeChallenge) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisTemplate.opsForValue().set(PREFIX + code, memberKey + "|" + codeChallenge, TTL);
        return code;
    }

    // 성공하면 memberKey, 실패하면 empty. 코드는 한 번 쓰면 무조건 삭제
    public Optional<Long> exchange(String code, String codeVerifier) {
        if (code == null || codeVerifier == null) return Optional.empty();
        String stored = redisTemplate.opsForValue().getAndDelete(PREFIX + code);
        if (stored == null) return Optional.empty();

        String[] parts = stored.split("\\|", 2);
        String expected = parts[1];
        String actual = s256(codeVerifier);
        // 타이밍 공격 방지용 상수시간 비교
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                                   actual.getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        return Optional.of(Long.valueOf(parts[0]));
    }

    private static String s256(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

### `ElectronAuthController` (새 파일, `/auth/**`는 이미 permitAll)

```java
@RestController
@RequestMapping("/auth/electron")
@RequiredArgsConstructor
public class ElectronAuthController {

    private final ElectronLoginCodeService codeService;
    private final RefreshTokenService refreshTokenService;
    private final MemberRepository memberRepository;

    public record ExchangeRequest(String code, String codeVerifier) {}
    public record RefreshRequest(String refreshToken) {}

    @PostMapping("/token")
    public ResponseEntity<TokenPair> exchange(@RequestBody ExchangeRequest body) {
        return codeService.exchange(body.code(), body.codeVerifier())
                .flatMap(memberRepository::findById)
                .map(m -> ResponseEntity.ok(refreshTokenService.issue(m.getMemberKey(), m.getRole())))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    // Electron은 쿠키가 없으므로 body로 refresh
    @PostMapping("/refresh")
    public ResponseEntity<TokenPair> refresh(@RequestBody RefreshRequest body) {
        return refreshTokenService.rotate(body.refreshToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
```

### `OAuth2LoginSuccessHandler`의 웹과 Electron 분기

```java
        HttpSession session = request.getSession(false);
        boolean isElectron = session != null && "electron".equals(session.getAttribute("client"));

        if (isElectron) {
            String challenge = (String) session.getAttribute("code_challenge");
            session.removeAttribute("client");
            session.removeAttribute("code_challenge");

            // 토큰은 여기서 발급하지 않음 — URL에는 일회용 코드만
            String code = electronLoginCodeService.issue(member.getMemberKey(), challenge);
            String redirectUrl = "jo-gpt://auth?code=" + code + (needsNickname ? "&needsNickname=true" : "");
            response.sendRedirect(redirectUrl);
            return;
        }

        // 웹: 쿠키로만 전달
        tokenCookies.write(response, refreshTokenService.issue(member.getMemberKey(), member.getRole()));
        response.sendRedirect(frontendUrl + (needsNickname ? "?needsNickname=true" : ""));
        // log.debug("... token: {}", accessToken) 삭제
```

### Electron `frontend/PC-JO-GPT-UI/index.js` (main 프로세스)

```js
const crypto = require('crypto');
let codeVerifier = null;

// 로그인 시작은 main 프로세스에서 (verifier가 렌더러에 노출되지 않게)
function startLogin(provider) {
    codeVerifier = crypto.randomBytes(32).toString('base64url');
    const challenge = crypto.createHash('sha256').update(codeVerifier).digest('base64url');
    shell.openExternal(`${API_BASE}/oauth2/authorization/${provider}?client=electron&code_challenge=${challenge}`);
}

// handleCustomProtocol 안에서
const code = urlObj.searchParams.get('code');
if (code && codeVerifier) {
    const res = await fetch(`${API_BASE}/auth/electron/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code, codeVerifier }),
    });
    codeVerifier = null;
    if (!res.ok) return;
    const { accessToken, refreshToken } = await res.json();
    // refreshToken은 safeStorage.encryptString()으로 암호화해 디스크에 저장, 렌더러엔 access만
    mainWindow.webContents.send('auth-success', accessToken);
}
```

> 백엔드와 Electron을 **동시에 배포**해야 합니다. 백엔드만 먼저 나가면 Electron 로그인이 깨집니다.

### 토큰을 찍는 로그 삭제 대상

| 위치 | 내용 |
|---|---|
| `OAuth2LoginSuccessHandler:107` | `"Redirected to bridge page with token: {}"` |
| `AuthValidateController:39` | `"token: {}"` |
| JO_GPT `JwtDelegateFilter:80` | Set-Cookie 값(= 토큰) 기록 |
| `OAuth2LoginSuccessHandler:46` | `"Authentication success: {}"`: 소셜 계정의 이메일 같은 속성이 그대로 찍힘 |

---

## 10. H-6: AesEncryptConverter (AES-GCM)

`EntityCom/.../converter/AesEncryptConverter.java` 전체 교체

새 데이터는 `v2:` 접두어를 붙인 GCM 방식으로 씁니다. 읽을 때는 기존 CBC 데이터도 읽을 수 있게 해서, 배포 중에도 서비스가 멈추지 않게 합니다.

```java
package com.example.entitycom.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Converter
public class AesEncryptConverter implements AttributeConverter<String, String> {

    private static final String V2_PREFIX = "v2:";
    private static final int IV_LENGTH = 12;      // GCM 권장
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    // 키가 없으면 앱 기동 자체를 실패시킴 (하드코딩 기본키로 조용히 돌아가는 것 방지)
    private static final SecretKeySpec KEY = loadKey();

    // ── 레거시(CBC) 복호화 전용: 재암호화 마이그레이션 끝나면 이 블록 통째로 삭제 ──
    private static final byte[] LEGACY_KEY = legacyBytes(
            System.getenv().getOrDefault("CHAT_ENCRYPT_KEY", "JoGptDefaultKey1234567890123456"), 32);
    private static final byte[] LEGACY_IV = legacyBytes(
            System.getenv().getOrDefault("CHAT_ENCRYPT_IV", "JoGptDefaultIV12"), 16);

    private static SecretKeySpec loadKey() {
        String b64 = System.getenv("CHAT_ENCRYPT_KEY_V2");
        if (b64 == null || b64.isBlank()) {
            throw new IllegalStateException("CHAT_ENCRYPT_KEY_V2 환경변수가 없습니다. (openssl rand -base64 32)");
        }
        byte[] key = Base64.getDecoder().decode(b64);
        if (key.length != 32) {
            throw new IllegalStateException("CHAT_ENCRYPT_KEY_V2는 Base64 인코딩된 32바이트여야 합니다.");
        }
        return new SecretKeySpec(key, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);                      // 매번 새 IV → 같은 평문도 다른 암호문
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return V2_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return null;
        if (dbValue.startsWith(V2_PREFIX)) {
            try {
                byte[] in = Base64.getDecoder().decode(dbValue.substring(V2_PREFIX.length()));
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(TAG_BITS, in, 0, IV_LENGTH));
                byte[] plain = cipher.doFinal(in, IV_LENGTH, in.length - IV_LENGTH);
                return new String(plain, StandardCharsets.UTF_8);
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                // v2는 GCM 무결성 검증 실패 = 변조 또는 키 불일치 → 조용히 넘기지 않음
                throw new IllegalStateException("복호화 실패 (데이터 변조 또는 키 불일치)", e);
            }
        }
        return legacyDecrypt(dbValue);
    }

    private static String legacyDecrypt(String cipherText) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(LEGACY_KEY, "AES"), new IvParameterSpec(LEGACY_IV));
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return cipherText;   // 암호화 도입 전 평문 데이터 (마이그레이션 후 제거)
        }
    }

    private static byte[] legacyBytes(String value, int length) {
        byte[] result = new byte[length];
        byte[] src = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(src, 0, result, 0, Math.min(src.length, length));
        return result;
    }
}
```

### 기존 데이터 재암호화 (한 번만 실행)

Hibernate는 엔티티의 **자바 값**이 바뀌었을 때만 UPDATE를 날립니다. 기존 레거시 행은 읽어도 값이 같아서 자동으로 재암호화되지 않습니다. 그래서 컬럼 값을 직접 읽어 다시 쓰는 작업이 따로 필요합니다.

```java
@Slf4j
@Component
@Profile("encrypt-migration")      // --spring.profiles.active=encrypt-migration 으로 1회 실행
@RequiredArgsConstructor
public class EncryptMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final AesEncryptConverter converter = new AesEncryptConverter();

    // {테이블, PK, 암호화 컬럼}
    private static final List<String[]> TARGETS = List.of(
            new String[]{"connected_accounts", "account_key", "access_token"},
            new String[]{"connected_accounts", "account_key", "refresh_token"},
            new String[]{"gpt_chat", "Gpt_chat_key", "Gpt_chat_contents"},
            new String[]{"members", "member_key", "nickname"},
            new String[]{"my_chat", "my_chat_key", "my_chat_contents"});

    @Override
    public void run(ApplicationArguments args) {
        for (String[] t : TARGETS) {
            String select = "SELECT %s, %s FROM %s WHERE %s IS NOT NULL AND %s NOT LIKE 'v2:%%'"
                    .formatted(t[1], t[2], t[0], t[2], t[2]);
            String update = "UPDATE %s SET %s = ? WHERE %s = ?".formatted(t[0], t[2], t[1]);

            int count = 0;
            for (Map<String, Object> row : jdbc.queryForList(select)) {
                String plain = converter.convertToEntityAttribute((String) row.get(t[2]));
                jdbc.update(update, converter.convertToDatabaseColumn(plain), row.get(t[1]));
                count++;
            }
            log.info("재암호화 완료 {}.{}: {}건", t[0], t[2], count);
        }
    }
}
```

### 순서

1. `openssl rand -base64 32`로 새 키를 만들어 `CHAT_ENCRYPT_KEY_V2`에 설정합니다. 비밀번호 관리자 등에 **백업**해 두세요. 키를 잃으면 데이터를 복구할 수 없습니다.
2. DB를 백업합니다.
3. 배포한 뒤 `encrypt-migration` 프로파일로 1회 실행합니다.
4. 남은 레거시 행이 0건인지 확인합니다.
   ```sql
   SELECT COUNT(*) FROM gpt_chat WHERE Gpt_chat_contents NOT LIKE 'v2:%';
   ```
5. `LEGACY_*` 블록과 `legacyDecrypt`를 지웁니다.

> `members.nickname`이 기존에는 고정 IV라서 같은 닉네임이 같은 암호문이 됐습니다. 이제는 랜덤 IV라 DB에서 닉네임으로 검색하거나 유니크 제약을 걸 수 없습니다. 현재 레포지토리에는 닉네임으로 조회하는 쿼리가 없어서 영향은 없습니다. 나중에 "닉네임 중복 확인"이 필요해지면 HMAC 해시 컬럼을 따로 두면 됩니다.

---

## 11. H-7: Docker 포트 바인딩

### `docker/mysql_container.yml`

```yaml
services:
  mysql:
    image: mysql:9.1
    ports:
      - "127.0.0.1:3307:3306"          # 호스트 로컬에서만 접근
    environment:
      - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - MYSQL_USER=${MYSQL_USER}
      - MYSQL_PASSWORD=${MYSQL_PASSWORD}
      - MYSQL_DATABASE=${MYSQL_DATABASE}
      # MYSQL_ALLOW_EMPTY_PASSWORD 제거 — 빈 root 비번 허용 여지 없앰
    volumes:
      - "./conf.d:/etc/mysql/conf.d:ro"
      - "./mysql_data:/var/lib/mysql"  # 컨테이너 재생성 시 데이터 유실 방지
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h localhost -u root -p\"$$MYSQL_ROOT_PASSWORD\""]

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.15.0
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=true            # 인증 켜기
      - xpack.security.http.ssl.enabled=false  # 로컬 바인딩 + 내부 통신이라 HTTP 유지
      - ELASTIC_PASSWORD=${ES_PASSWORD}
    volumes:
      - "./es_data:/usr/share/elasticsearch/data"
    ports:
      - "127.0.0.1:9200:9200"
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    ports:
      - "127.0.0.1:6379:6379"
    command: ["redis-server", "--acllog-max-len", "128", "--requirepass", "${RedisPw}"]
    restart: unless-stopped

  chroma:
    image: chromadb/chroma:1.0.0
    ports:
      - "127.0.0.1:8000:8000"          # Chroma는 인증이 없으니 외부에 절대 노출 금지
    volumes:
      - "./chroma_data:/data"
    environment:
      - IS_PERSISTENT=TRUE
      - PERSIST_DIRECTORY=/data
      - TZ=Asia/Seoul
    restart: unless-stopped
```

### 루트 `docker-compose.yml`

Redis를 `"6379:6379"`에서 `"127.0.0.1:6379:6379"`로 바꿉니다.

> **Docker는 UFW 같은 호스트 방화벽 규칙을 우회합니다.** UFW에서 9200을 막아도 `"9200:9200"`이면 외부에서 접근됩니다. 반드시 `127.0.0.1:` 바인딩으로 막아야 합니다. 앱도 Docker로 띄운다면 `ports`를 아예 빼고 compose 내부 네트워크(`http://elasticsearch:9200`)로만 통신하는 게 가장 안전합니다.

---

## 12. 적용 순서와 체크리스트

| 순서 | 항목 | 이유 |
|---|---|---|
| 1 | **H-7** | 설정만 바꾸면 되고, 지금 이 순간에도 외부에 열려 있어서 가장 급함 |
| 2 | **H-3 → H-4 → H-1 → H-2** | 구조를 한 번에 교체. 배포하면 전체 사용자가 한 번 다시 로그인 |
| 3 | **H-5** | 백엔드와 Electron 동시 배포 필요 |
| 4 | **H-6** | DB 백업 → 마이그레이션 → 확인 → 레거시 코드 삭제 |

### 배포 전 체크리스트

- [ ] `spring.jwt.secret`을 새 랜덤 값으로 교체 (32바이트 이상)
- [ ] `CHAT_ENCRYPT_KEY_V2` 생성, 설정, 백업
- [ ] `ES_PASSWORD` 설정
- [ ] 프론트 로그아웃 호출을 `POST /login/logout`으로 변경
- [ ] 프론트가 `/refreshToken/login/refresh` 응답 body의 토큰을 쓰고 있는지 확인
- [ ] 프론트가 API와 다른 사이트라면 `TokenCookies.SAME_SITE`를 `"None"`으로 변경
- [ ] 토큰을 찍는 로그 5곳 삭제
- [ ] 일반 로그인, 소셜 로그인, Electron 로그인, 로그아웃, 30분 뒤 자동 재발급을 수동으로 테스트
- [ ] 로그아웃 후 이전 access token으로 API를 호출하면 401이 나는지 확인
- [ ] 외부에서 `curl http://<서버IP>:9200`, `:8000`, `:3307`가 연결되지 않는지 확인
