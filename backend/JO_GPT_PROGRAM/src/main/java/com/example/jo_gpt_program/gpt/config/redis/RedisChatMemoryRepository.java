package com.example.jo_gpt_program.gpt.config.redis;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class RedisChatMemoryRepository implements ChatMemoryRepository {
    // 데이터가 깨지거나 하기 때문에 직렬화 방식은 통일해야 한다.
    private final RedisTemplate<String, String> redisTemplate;


    public RedisChatMemoryRepository(RedisTemplate<String, String> redisTemplate, RedisTemplate<String, Object> objectRedisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String getKey(String conversationId) {
        return "chat:memory:" + conversationId;
    }
    // 모든 채팅방 ID 목록 반환
    @Override
    public @NonNull List<String> findConversationIds() {
        // 저장된 모든 Key 조회 (선택사항)
        return List.of();
    }


    // 대화 조회
    @Override
    public @NonNull List<Message> findByConversationId(String conversationId) {
        List<String> messages = redisTemplate.opsForList().range(getKey(conversationId), 0, -1);
       log.debug("메모리 메시지 기억 내역 {}",messages);
        if (messages == null) return List.of();
        return messages.stream()
                .map(m -> {
                    String[] parts = m.split(":", 2);
                    if (parts[0].equals("USER")) {
                        return (Message) new UserMessage(parts[1]);
                    } else {
                        return (Message) new AssistantMessage(parts[1]);
                    }
                })
                .toList();
    }

        // 대화 저장



    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        messages.forEach(m -> log.debug("타입={}, 내용={}",
                m.getMessageType().name(), m.getText()));
        String key = getKey(conversationId);  // ← 통일!
        redisTemplate.delete(key);
        // Message 객체 대신 텍스트만 저장
        List<String> texts = messages.stream()
                .map(m -> m.getMessageType().name() + ":" + m.getText())
                .toList();
        log.debug("saveAll 호출됨 key={}, messages={}", key, texts);

        redisTemplate.opsForList().rightPushAll(key, texts);
        redisTemplate.expire(key, Duration.ofHours(1));

    }
    // 대화 삭제

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(getKey(conversationId));  // ← 통일!
    }
}
