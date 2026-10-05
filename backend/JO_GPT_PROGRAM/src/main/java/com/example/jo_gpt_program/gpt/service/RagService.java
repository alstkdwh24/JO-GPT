package com.example.jo_gpt_program.gpt.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service("ragService")
public class RagService {
    private int documentCount = 0; // 추가!

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagService(VectorStore vectorStore,  ChatClient chatClient ) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }
// 문서 저장 메서드
    
    // memberKey: 문서 주인. 검색 때 본인 문서만 찾도록 metadata에 저장 (없으면 저장 안 함 — 주인 없는 문서는 아무도 못 찾게)
    public void saveDocument(String context, String source, String category, Long id, Long memberKey) {
        if (memberKey == null) {
            log.warn("[RAG 저장 생략] memberKey 없음 entityId={}", id);
            return;
        }
        String summary = chatClient.prompt()

                .user("다음 내용을 3줄로 요약해줘 검색에 잘 걸리도록 면사 키워드 등으로:\n\n" + context)
                .call()// AI한테 요청을 보내는 것
                .content(); // 응답 객체에서 텍스트만 꺼내는 것
        log.info("summary: {}", summary);
        // 벡터 DB에 문서를 저장하는 메서드, RAG에서 검색할 수 있도록 텍스트를 벡터로 변환하여 저장합니다.
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", source);
        metadata.put("category", category);
        metadata.put("saveAt", LocalDateTime.now().toString());
        metadata.put("originalLength", context.length()); // 원본 길이만 기록
        metadata.put("entityId", String.valueOf(id)); // 추가
        log.info("metadata: {}", metadata);
        log.info("VectorStore 구현체: {}", vectorStore.getClass().getName());


        assert summary != null;
        try {
            vectorStore.add(List.of(new Document(summary, metadata)));
            log.info("[RAG 저장 완료] documentCount={}", ++documentCount);
        } catch (Exception e) {
            log.error("[RAG 저장 실패] 에러={}", e.getMessage(), e);
        }
        documentCount++;
    }

    /* 임베딩 */

    // ----------------------------------- RAG: 문서 기반 답변
    // -----------------------------------


    // 본인(memberKey) 문서만 검색 — 다른 사용자의 대화 요약이 섞이지 않게
    public String findDocument(String query, Long memberKey) {
        if (query == null || query.isBlank() || memberKey == null) return "";

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(5)
                        .filterExpression(memberFilter(memberKey)).build());

        if(docs.isEmpty()) return ""; // 결과가 없으면 만환
        log.debug("문서갯수: {}", docs.size());  // ← 이렇게 해야 해요!
        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
    }

    public static Filter.Expression memberFilter(Long memberKey) {
        return new FilterExpressionBuilder().eq("memberKey", String.valueOf(memberKey)).build();
    }

    /* 채팅방 삭제 시 그 방의 AI 답변(entityId = GptChatKey)으로 저장한 벡터 문서도 삭제 */
    public void deleteByEntityIds(List<Long> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) return;
        try {
            vectorStore.delete(new FilterExpressionBuilder()
                    .in("entityId", entityIds.stream().map(String::valueOf).toArray())
                    .build());
        } catch (Exception e) {
            log.error("[RAG 삭제 실패] entityIds={} 에러={}", entityIds, e.getMessage(), e);
        }
    }

}
