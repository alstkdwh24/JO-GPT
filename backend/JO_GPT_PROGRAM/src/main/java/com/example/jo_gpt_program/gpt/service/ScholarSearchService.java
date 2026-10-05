package com.example.jo_gpt_program.gpt.service;

import com.example.jo_gpt_program.gpt.dto.MyChatDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
// 학술지 검색
public class ScholarSearchService {

    private final RestTemplate restTemplate;
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ChatMemory chatMemory;

    @Value("${tavily.api-key}")
    private String apiKey;

    @Value("${tavily.url}")
    private String apiUrl;

    @org.springframework.beans.factory.annotation.Value("${spring.search.key}")
    private String searchKey;

    @org.springframework.beans.factory.annotation.Value("${spring.search.cx}")
    private String searchCx;

    public ScholarSearchService(RestTemplate restTemplate, ChatClient chatClient, VectorStore vectorStore, ChatMemory chatMemory) {
        this.restTemplate = restTemplate;
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.chatMemory = chatMemory;
    }

    // Google Custom Search API
    public String searchNews(String query) {
        try {
            String url = UriComponentsBuilder
                    .fromUriString("https://www.googleapis.com/customsearch/v1")
                    .queryParam("key", searchKey)
                    .queryParam("cx", searchCx)
                    .queryParam("q", query)
                    .queryParam("num", 5)
                    .queryParam("lr", "lang_ko")
                    .build()
                    .toString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");

            if (items == null || items.isEmpty()) return "";

            return items.stream()
                    .map(item -> String.format("제목: %s\n요약: %s\n링크: %s",
                            item.getOrDefault("title", ""),
                            item.getOrDefault("snippet", ""),
                            item.getOrDefault("link", "")))
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            log.error("웹 검색 실패: {}", e.getMessage());
            return "";
        }
    }

    // 학술 검색 메서드
    public String search(String query) {
        try {
            // url관련
            String url = UriComponentsBuilder
                    // 기본 url 설정
                    .fromUriString("https://api.semanticscholar.org/graph/v1/paper/search")
                    // ? query = 스프링 AI 추가 검색어 추
                    .queryParam("query", query)
                    // 어떤 정보를 받을지 지정하는 파라미터이다 title: 논문 제목, abstract: 논문 요약, authors: 저자 목록, year:
                    // 출판 년도, url: 논문 링크
                    .queryParam("field", "title,abstract,authors,year,url")
                    .queryParam("limit", 5) // 논문 5개 반환 (현재 설정)
                    .build()
                    .toString();
            // 요청을 해서 응답을 받기
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            // 바디만 꺼내기
            List<Map<String, Object>> papers = (List<Map<String, Object>>) response.getBody().get("data");

            if (papers == null || papers.isEmpty())
                return "";

            return papers.stream()
                    .map(p -> String.format("제목: %s\n요약: %s\n년도: %s\n링크: %s",
                            p.getOrDefault("title", "없음"),
                            p.getOrDefault("abstract", "없음"),
                            p.getOrDefault("year", "없음"),
                            p.getOrDefault("url", "없음")))
                    .collect(Collectors.joining("\n---\n"));

        } catch (Exception e) {
            log.error("Semantic Scholar 검색 실패: {}", e.getMessage());
            return "";
        }
    }
    // ------------------- 학술검색 + 뉴스 + AI 답변 -------------------
    public String sendWithScholar(MyChatDTO dto, String model, String customPrompt) {
        String scholarResults = this.search(dto.getMyChatContents());
        String newsResults = this.searchNews(dto.getMyChatContents());

        String systemPrompt = """
                아래 정보를 참고해서 답변하세요.
                검색 결과가 없으면 알고 있는 내용으로 답변하세요.

                [논문 검색 결과]
                %s

                [최신 뉴스]
                %s

                [추가 지침]
                %s
                """.formatted(
                scholarResults.isEmpty() ? "검색 결과 없음" : scholarResults,
                newsResults.isEmpty() ? "뉴스 없음" : newsResults,
                customPrompt != null ? customPrompt : "친절하고 학술적으로 답변하세요.");

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(dto.getMyChatContents())
                // GoogleGenAiChatOptions는 Spring AI에서 Google GenAI 모델을 사용할 때 옵션을 설정하는 클래스입니다.
                // 모델 선택, 온도, 최대 토큰 수 등 다양한 옵션을 설정할 수 있습니다.
                .options(GoogleGenAiChatOptions.builder()
                        .model(model)
                        .temperature(0.7)
                        .maxOutputTokens(6024)
                        .topP(0.9)
                        .topK(100)
                        .build())
                .call()
                .content();

        chatMemory.add(dto.getShowChatKey().toString(),
                UserMessage.builder().text(response).build());
        return response;
    }

    // ------------------- 학술검색 + RAG 답변 -------------------
    public String sendWithRagAndScholar(MyChatDTO dto, String model, String customPrompt) {
        // 1단계 - 벡터 DB에서 유사 문서 검색
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(dto.getMyChatContents())
                        .topK(3) // 유사한 문서 3개만
                        .similarityThreshold(0.9) // 90% 이상 유사하면 중복으로 판단

                        .build());
        // 문서들을 하나의 문자열로 합치기
        String ragContext = docs.stream()
                // 각 문서에서 텍스트만 추출
                .map(Document::getText)
                // 문서 사이에 구분선 삽입
                .collect(Collectors.joining("\n---\n"));
        // 학술 검색 + 뉴스 검색
        String scholarResults = this.search(dto.getMyChatContents());
        String newsResults = this.searchNews(dto.getMyChatContents());
        // 시스템 프롬프트 조립
        String systemPrompt = """
                        아래 정보를 참고해서 답변하세요.

                        [RAG 검색 결과]
                        %s

                        [학술 검색 결과]
                        %s

                        [최신 뉴스]
                        %s

                        [추가 지침]
                        %s
                        """.formatted(
                        ragContext.isEmpty() ? "검색 결과 없음" : ragContext,
                        scholarResults.isEmpty() ? "검색 결과 없음" : scholarResults,
                        newsResults.isEmpty() ? "뉴스 없음" : newsResults,
                        customPrompt != null ? customPrompt : "친절하고 학술적으로 답변하세요.");

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(dto.getMyChatContents())
                .options(GoogleGenAiChatOptions.builder()
                        .model(model)
                        .temperature(0.7)
                        .maxOutputTokens(6024)
                        .topP(0.9)
                        .topK(100)
                        .build())
                .call()
                .content();
        chatMemory.add(dto.getShowChatKey().toString(),
                UserMessage.builder().text(response).build());

        return response;
    }

    public String searchWithTavily(String query){
        if(query.length() > 400) return "";
        RestTemplate restTemplate = new RestTemplate();

        // 1. HTTP 헤더 설정 (우리는 JSON형식으로 대화할 것이라고 선언)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 2. 요청 Body 설정
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("api_key", apiKey);
        requestBody.put("query", query);         // 검색할 단어
        requestBody.put("search_depth", "basic"); // basic(빠름) 또는 advanced(깊고 정확함) 선택
        requestBody.put("include_answer", false);


        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        try{
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST,request,String.class);

            return response.getBody();
        }catch(Exception e){

            log.error("Tavily 검색 실패: {}", e.getMessage());
            return "";
        }
    }
}
