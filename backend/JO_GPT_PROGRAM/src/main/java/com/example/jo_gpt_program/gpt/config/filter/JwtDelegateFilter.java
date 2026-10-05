package com.example.jo_gpt_program.gpt.config.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtDelegateFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${spring.memberSecurity.url}")
    private String memberSecurityUrl;

    // ✅ /connect/** 경로는 토큰 검증 건너뜀
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/connect/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String refreshToken = null;
        String token = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                // 기존 코드는 ACCESS_TOKEN을 찾으면 break 해서, 그 뒤에 있는 REFRESH_TOKEN을 못 읽었음
                if ("ACCESS_TOKEN".equals(cookie.getName())) {
                    token = cookie.getValue() == null ? null : cookie.getValue().trim();
                } else if ("REFRESH_TOKEN".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                }
            }
        }

        // 토큰이 하나도 없으면 MembersSecurity를 호출하지 않음 (요청마다 원격 호출되는 증폭 방지)
        if ((token == null || token.isEmpty()) && (refreshToken == null || refreshToken.isEmpty())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String validateUrl = memberSecurityUrl + "/auth/validate";
            log.debug("[JwtDelegateFilter] 검증 요청 URL={}", validateUrl);

            URL url = new URL(validateUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Cookie", "REFRESH_TOKEN=" + refreshToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int statusCode = conn.getResponseCode();

            // refresh 회전 시 ACCESS·REFRESH 쿠키 2개가 오므로 전부 전달
            // (getHeaderField는 마지막 1개만 줘서 새 refresh가 빠지면 다음 요청이 재사용 감지로 세션 폐기됨)
            java.util.List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
            if (setCookies != null) {
                setCookies.forEach(c -> response.addHeader("Set-Cookie", c));
                log.debug("[JwtDelegateFilter] 새 쿠키 {}개 브라우저에 전달", setCookies.size());
            }
            log.debug("[JwtDelegateFilter] 응답 코드={}", statusCode);

            if (statusCode == 200) {
                String body;
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    body = br.lines().collect(Collectors.joining());
                }
                log.debug("[JwtDelegateFilter] 응답 body={}", body);

                UserInfoDto userInfo = objectMapper.readValue(body, UserInfoDto.class);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userInfo, null, userInfo.getAuthorities()));

            } else {
                log.warn("[JwtDelegateFilter] 토큰 검증 실패 statusCode={}", statusCode);
                SecurityContextHolder.clearContext();
            }

            conn.disconnect();

        } catch (Exception e) {
            log.error("[JwtDelegateFilter] 검증 중 에러: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
