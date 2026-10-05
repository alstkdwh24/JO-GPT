package com.example.jo_gpt_program;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
@SpringBootTest
@ActiveProfiles("test")  // ← 이거 추가! application-test.yml 읽어요

class JoGptProgramApplicationTests {
    @Autowired
    private VectorStore vectorStore;
    @Autowired

    private ChromaApi chromaApi;


    // @Autowired 대신 생성자 주입 사용

    @Test
    void contextLoads() {

        vectorStore.add(List.of(
                new Document("vwqcwkvpnjqwopvwqcv"),
                new Document("vkjbwevqwvqwlkvqwjkvwjqk")
        ));
        List<Document> results =vectorStore.similaritySearch(
                SearchRequest.builder().query("테스트")

                        .topK(10)
                        .build()

        );
        System.out.println("검색된 문서 수: " + results.size());
        for (Document doc : results) {
            System.out.println("ID: " + doc.getId());
            System.out.println("내용: " + doc.getText());
            System.out.println("메타데이터: " + doc.getMetadata());
            System.out.println("---");
        }
    }


}
