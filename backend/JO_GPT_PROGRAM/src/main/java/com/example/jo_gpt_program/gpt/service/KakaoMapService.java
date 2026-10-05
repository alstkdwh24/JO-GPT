package com.example.jo_gpt_program.gpt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@Service("kakaoMapService")
public class KakaoMapService {

    @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
    private String kakaoApiKey;

    private final RestTemplate restTemplate;

    public KakaoMapService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String findMap(String query) {
        // 헤더에 카카오 api 첨가
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoApiKey);

        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://dapi.kakao.com/v2/local/search/keyword.json") // 카카오맵 uri
                .queryParam("query", query)
                .queryParam("size", 5)  // ← 최대 5개 결과
                .build()
                .encode()
                .toUri();
        // 카카오맵에 요청 보내기
        ResponseEntity<String> response = this.restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        try {
            // ObjectMapper 로 데이터 담고
            ObjectMapper mapper = new ObjectMapper();

            JsonNode root = mapper.readTree(response.getBody());  // HTTP 응답의 body (JSON 문자열) JSON 문자열 -> JsonNode 트리 구조로 변환
            JsonNode documents = root.path("documents");

            if (documents.isArray() && documents.size() > 0) {
                // 여러 장소 배열로 반환
                ArrayNode places = mapper.createArrayNode();
                for (JsonNode doc : documents) {
                    ObjectNode place = mapper.createObjectNode();
                    place.put("name", doc.path("place_name").asText());
                    place.put("x", doc.path("x").asText());       // 경도
                    place.put("y", doc.path("y").asText());       // 위도
                    place.put("address", doc.path("road_address_name").asText());
                    place.put("url", doc.path("place_url").asText());
                    places.add(place);
                }
                log.info("[KakaoAPI] 장소 {}개 추출 완료", places.size());
                return mapper.writeValueAsString(places);
            } else {
                log.warn("[KakaoAPI] documents 비어있음. 응답={}", response.getBody());
            }
        } catch (Exception e) {
            log.error("[KakaoAPI] 좌표 파싱 실패", e);
        }
        return null;
    }
}
