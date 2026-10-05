package com.example.jo_gpt_program.gpt.config;

import com.example.jo_gpt_program.gpt.config.redis.RedisChatMemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
@Slf4j
@Configuration
public class AiConfig {



    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(10)
                .build();
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(@Value("${spring.llm.key}") String apiKey) {
        return new GeminiRestEmbeddingModel(apiKey);
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("당신은 JO-GPT 어시스턴트입니다. 항상 한국어로 친절하게 답변하세요.")
                .build();
    }


}
