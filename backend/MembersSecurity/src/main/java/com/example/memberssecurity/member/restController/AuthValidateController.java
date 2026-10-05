package com.example.memberssecurity.member.restController;

import com.example.memberssecurity.member.dto.UserInfoDto;
import com.example.memberssecurity.member.service.RefreshTokenService;
import com.example.memberssecurity.security.config.jwt.JWTUtils;
import com.example.memberssecurity.security.config.jwt.TokenCookies;
import com.example.memberssecurity.security.config.jwt.TokenPair;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

// 다른 백엔드 서비스(JO_GPT 등)의 JwtDelegateFilter가 요청마다 호출하는 토큰 검증 API
@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthValidateController {

    private final JWTUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final TokenCookies tokenCookies;

    public AuthValidateController(JWTUtils jwtUtils, RefreshTokenService refreshTokenService, TokenCookies tokenCookies) {
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
        this.tokenCookies = tokenCookies;
    }

    @GetMapping("/validate")
    public ResponseEntity<UserInfoDto> validate(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = TokenCookies.resolveAccessToken(request);

        // 위임 필터는 access가 없으면 "Bearer null"을 보냄 → refresh 경로로
        if (accessToken != null && !"null".equals(accessToken)) {
            try {
                Claims claims = jwtUtils.parse(accessToken, JWTUtils.TYPE_ACCESS);
                if (jwtUtils.isBlacklisted(claims)) {
                    return ResponseEntity.status(401).build();
                }
                return ResponseEntity.ok(buildUserInfoDto(claims));
            } catch (ExpiredJwtException e) {
                // 만료 → 아래에서 refresh 회전
            } catch (JwtException | IllegalArgumentException e) {
                return ResponseEntity.status(401).build();
            }
        }

        // Redis에 저장된 refresh와 대조·회전 (예전처럼 서명만 보고 재발급하지 않음)
        Optional<TokenPair> rotated = refreshTokenService.rotate(TokenCookies.read(request, TokenCookies.REFRESH));
        if (rotated.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        tokenCookies.write(response, rotated.get());
        return ResponseEntity.ok(buildUserInfoDto(jwtUtils.parse(rotated.get().accessToken(), JWTUtils.TYPE_ACCESS)));
    }

    private UserInfoDto buildUserInfoDto(Claims claims) {
        return UserInfoDto.builder()
                .memberId(String.valueOf(JWTUtils.memberKey(claims)))
                .role(JWTUtils.role(claims))
                .build();
    }
}
