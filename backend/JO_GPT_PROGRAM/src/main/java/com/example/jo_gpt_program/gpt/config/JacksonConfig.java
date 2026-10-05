package com.example.jo_gpt_program.gpt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

// Jackson 숫자 길이 제한 완화 + 알 수 없는 필드 무시 + LocalDateTime 직렬화 설정
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    // Spring 컨텍스트에 ObjectMapper 빈이 여러 개일 경우 우선 사용하도록 지정
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder()
                        .maxNumberLength(50000)
                        .build());
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // FAIL_ON_UNKNOWN_PROPERTIES 비활성화 -> JSON에 모르는 필드가 있어도 에러 내지 말고 무시
        // API 응답에 예상치 못한 필드가 추가되도 안전하게 처리
        // LocalDateTime 등 Java 8 날짜/시간 타입 직렬화 지원
        mapper.registerModule(new JavaTimeModule());
        // LocalDateTime, LocalDate 같은 Java 8 날짜 타입을 JSON으로 직열화/역직렬화 지원
        // 이게 없으면 날짜 타입 변환 시 에러
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 날짜를 숫자 대신 문자열 형태로 줄력
        return mapper;
    }
}