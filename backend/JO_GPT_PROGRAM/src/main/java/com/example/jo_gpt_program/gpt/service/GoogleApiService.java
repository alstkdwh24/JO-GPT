package com.example.jo_gpt_program.gpt.service;

import com.example.entitycom.entity.connect.ConnectedAccounts;
import com.example.jo_gpt_program.gpt.repository.jpa.ConnectedAccountsRepository;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleApiService {

    private final ConnectedAccountsRepository connectedAccountsRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    // Redis 키 생성
    private String redisKey(Long memberKey) {
        return "google:token:" + memberKey;
    }

    // memberKey 로 Google AccessToken 꺼내기 (Redis 캐싱 + 만료시 자동 갱신)
    public String getAccessToken(Long memberKey) {

        // 1단계: Redis 캐시에서 먼저 조회
        String cachedToken = (String) redisTemplate.opsForValue().get(redisKey(memberKey));
        if (cachedToken != null) {
            log.info("[GoogleApiService] Redis 캐시에서 토큰 반환 memberKey={}", memberKey);
            return cachedToken;
        }

        // 2단계: Redis 없으면 DB 조회
        ConnectedAccounts account = connectedAccountsRepository
                .findByMember_MemberKeyAndProvider(memberKey, "google")
                .orElseThrow(() -> new RuntimeException("Google account not connected"));

        // 3단계: 토큰 만료 체크 (만료 5분 전부터 미리 갱신)
        if (account.getTokenExpiry() == null ||
                account.getTokenExpiry().isBefore(LocalDateTime.now().plusMinutes(5))) {
            log.info("[GoogleApiService] accessToken 만료 - refresh_token으로 갱신 시도");
            account = refreshAccessToken(account);
        }

        // 4단계: Redis에 저장 (55분 TTL)
        redisTemplate.opsForValue().set(redisKey(memberKey), account.getAccessToken(), 55, TimeUnit.MINUTES);
        log.info("[GoogleApiService] Redis에 토큰 캐싱 완료 memberKey={}", memberKey);

        return account.getAccessToken();
    }

    // refresh_token 으로 새 accessToken 발급 후 DB + Redis 갱신
    @Transactional
    private ConnectedAccounts refreshAccessToken(ConnectedAccounts account) {
        try {
            String refreshToken = account.getRefreshToken();
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new RuntimeException("refresh_token 없음 - 재연동 필요");
            }

            // Google OAuth2 토큰 갱신 요청
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("refresh_token", refreshToken);
            params.add("grant_type", "refresh_token");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://oauth2.googleapis.com/token",
                    request,
                    Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body == null || !body.containsKey("access_token")) {
                throw new RuntimeException("토큰 갱신 실패: 응답 없음");
            }

            String newAccessToken = (String) body.get("access_token");
            int expiresIn = body.containsKey("expires_in") ? (Integer) body.get("expires_in") : 3600;
            LocalDateTime newExpiry = LocalDateTime.now().plusSeconds(expiresIn);

            // refresh_token 은 새로 받았을 때만 업데이트 (없으면 기존 유지)
            String newRefreshToken = body.containsKey("refresh_token")
                    ? (String) body.get("refresh_token")
                    : account.getRefreshToken();

            // DB 업데이트
            account.updateTokens(newAccessToken, newRefreshToken, newExpiry);
            connectedAccountsRepository.save(account);

            // Redis 캐시도 즉시 갱신 (55분 TTL)
            redisTemplate.opsForValue().set(
                    redisKey(account.getMember().getMemberKey()),
                    newAccessToken,
                    55, TimeUnit.MINUTES
            );

            log.info("[GoogleApiService] 토큰 갱신 완료! 만료: {}", newExpiry);
            return account;

        } catch (Exception e) {
            log.error("[GoogleApiService] 토큰 갱신 실패: {}", e.getMessage());
            throw new RuntimeException("Google 토큰 갱신 실패 - 재연동 필요: " + e.getMessage());
        }
    }

    // 연동 해제 시 Redis 캐시도 삭제
    public void clearTokenCache(Long memberKey) {
        redisTemplate.delete(redisKey(memberKey));
        log.info("[GoogleApiService] Redis 토큰 캐시 삭제 memberKey={}", memberKey);
    }

    // GoogleCredential 생성
    public HttpCredentialsAdapter getCredentials(Long memberKey) {
        // accessToken 꺼내기 (Redis 캐싱 + 만료시 자동 갱신 포함)
        GoogleCredentials googleCredential = GoogleCredentials.create(
                new AccessToken(getAccessToken(memberKey), null)
        );
        // 인증 객체를 HTTP 요청에 붙일 수 있게 포장
        return new HttpCredentialsAdapter(googleCredential);
    }
}