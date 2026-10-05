package com.example.jo_gpt_program.gpt.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Google AI Studio REST API를 직접 호출하는 EmbeddingModel 구현체.
 * spring-ai-google-genai-embedding 의 Vertex AI 전용 문제를 우회한다.
 */
@Slf4j
public class GeminiRestEmbeddingModel implements EmbeddingModel {

    private static final String EMBED_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent";

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiRestEmbeddingModel(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        try {
            List<Embedding> results = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                String text = texts.get(i);
                // ✅ 빈 값이면 임베딩 건너뜀
                if (text == null || text.isBlank()) {
                    log.warn("[GeminiRestEmbeddingModel] 빈 텍스트 건너뜀 index={}", i);
                    results.add(new Embedding(new float[dimensions()], i));
                    continue;
                }
                float[] vector = embedSingle(text);
                log.info("[임베딩 벡터 차원] size={}", vector.length);
                results.add(new Embedding(vector, i));
            }
            return new EmbeddingResponse(results);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Gemini embedding 호출 실패", e);
        }
    }

    private float[] embedSingle(String text) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("content")
                .putArray("parts")
                .addObject()
                .put("text", text);

        String json = objectMapper.writeValueAsString(body);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(EMBED_URL))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response =
                httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Gemini embedding API 오류 {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Gemini embedding 실패: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode valuesNode = root.path("embedding").path("values");
        float[] vector = new float[valuesNode.size()];
        for (int j = 0; j < valuesNode.size(); j++) {
            vector[j] = (float) valuesNode.get(j).asDouble();
        }
        return vector;
    }

    @Override
    public float[] embed(String text) {
        EmbeddingResponse response = call(new EmbeddingRequest(List.of(text), null));
        return response.getResults().get(0).getOutput();
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        EmbeddingResponse response = call(new EmbeddingRequest(texts, null));
        return response.getResults().stream()
                .map(Embedding::getOutput)
                .toList();
    }

    @Override
    public int dimensions() {
        // text-embedding-004 의 벡터 차원
        return 3072;
    }
}

