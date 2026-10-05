package com.example.memberssecurity.security.config.handler;

import com.example.entitycom.entity.member.Members;
import com.example.memberssecurity.member.service.MemberService;
import com.example.memberssecurity.member.service.RefreshTokenService;
import com.example.memberssecurity.security.config.dto.social.dto.*;
import com.example.memberssecurity.security.config.jwt.JWTUtils;
import com.example.memberssecurity.security.config.jwt.TokenCookies;
import com.example.memberssecurity.security.config.jwt.TokenPair;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JWTUtils jwtUtils;
    private final MemberService memberService;
    private final RefreshTokenService refreshTokenService;
    private final TokenCookies tokenCookies;

    @Value("${spring.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        log.debug("Authentication success: {}", authentication);
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid authentication type");
            return;
        }

        SocialUserInfo userInfo = getSocialUserInfo(oauthToken);
        MemberService.OAuthResult result = memberService.upsertOAuthUser(userInfo);
        Members member = result.member();
        log.debug("OAuth2 로그인 성공: member={}, isNew={}", member, result.isNew());

        // access 30분 + refresh 14일(Redis 저장·회전)
        TokenPair tokens = refreshTokenService.issue(member.getMemberKey(), member.getRole());
        tokenCookies.write(response, tokens);
        String accessToken = tokens.accessToken();
        String refreshToken = tokens.refreshToken();

        String tempMemberId = userInfo.getProvider() + "_" + userInfo.getProviderId();
        boolean needsNickname = result.isNew() || tempMemberId.equals(result.member().getNickname());

        String clientType = (String) request.getSession().getAttribute("client");
        boolean isElectron = "electron".equals(clientType);
        request.getSession().removeAttribute("client");

        String redirectUrl;
        if (isElectron) {
            redirectUrl = "jo-gpt://auth?token=" + accessToken + "&refreshtoken=" + refreshToken;
            if (needsNickname) {
                redirectUrl += "&needsNickname=true";
                if ("naver".equals(userInfo.getProvider())) {
                    String suggested = userInfo.getSuggestedNickname();
                    if (suggested != null && !suggested.isBlank()) {
                        redirectUrl += "&socialNickname=" + URLEncoder.encode(suggested, StandardCharsets.UTF_8);
                    }
                }
            }
        } else {
            redirectUrl = frontendUrl + (needsNickname ? "?needsNickname=true" : "");
            if (needsNickname) {
                redirectUrl += "&needsNickname=true";
                if ("naver".equals(userInfo.getProvider())) {
                    String suggested = userInfo.getSuggestedNickname();
                    if (suggested != null && !suggested.isBlank()) {
                        redirectUrl += "&socialNickname=" + URLEncoder.encode(suggested, StandardCharsets.UTF_8);
                    }
                }
            }
        }
        response.sendRedirect(redirectUrl);
        log.debug("OAuth2 로그인 후 리다이렉트 electron={}", isElectron); // 토큰은 로그에 남기지 않음
    }

    private static @NonNull SocialUserInfo getSocialUserInfo(OAuth2AuthenticationToken oauthToken) {
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();
        OAuth2User oauth2User = oauthToken.getPrincipal();
        assert oauth2User != null;
        Map<String, Object> attributes = oauth2User.getAttributes();

        return switch (registrationId.toLowerCase()) {
            case "google" -> new GoogleUserInfo(attributes);
            case "kakao" -> new KakaoUserInfo(attributes);
            case "naver" -> new NaverUserInfo(attributes);
            case "github" -> new GithubUserInfo(attributes);
            default -> throw new IllegalArgumentException("Unsupported provider: " + registrationId);
        };
    }
}
