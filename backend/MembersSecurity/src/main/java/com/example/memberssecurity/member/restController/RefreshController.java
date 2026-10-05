package com.example.memberssecurity.member.restController;

import com.example.memberssecurity.member.service.RefreshTokenService;
import com.example.memberssecurity.security.config.jwt.TokenCookies;
import com.example.memberssecurity.security.config.jwt.TokenPair;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/refreshToken")
public class RefreshController {

    private final RefreshTokenService refreshTokenService;
    private final TokenCookies tokenCookies;

    @PostMapping("/login/refresh")
    public ResponseEntity<String> refreshToken(HttpServletRequest request, HttpServletResponse response) {

        // Redis에 저장된 refresh와 대조·회전 (재사용 감지 시 세션 전체 폐기)
        Optional<TokenPair> rotated = refreshTokenService.rotate(TokenCookies.read(request, TokenCookies.REFRESH));
        if (rotated.isEmpty()) return ResponseEntity.status(401).build();
        tokenCookies.write(response, rotated.get());
        return ResponseEntity.ok(rotated.get().accessToken());
    }
}
