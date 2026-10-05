package com.example.jo_gpt_program.gpt.config;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

// 클라이언트가 X-Model 헤더로 넘긴 모델명을 허용목록으로 검증
// (로그인 사용자가 비싼 모델을 마음대로 골라 내 Gemini 키로 비용을 폭증시키지 못하게)
@Component
public class LlmModelPolicy {

    // 기존 컨트롤러들의 defaultValue와 동일하게 유지 (X-Model 없이 호출하던 동작이 바뀌지 않도록)
    private static final String DEFAULT_MODEL = "gemini-2.0-flash";

    // web-jogpt-ui ChatHome.jsx MODEL_OPTIONS와 맞출 것
    private static final Set<String> ALLOWED_MODELS = Set.of(
            DEFAULT_MODEL,
            "gemini-3.1-flash-image-preview",
            "gemini-3.5-flash",
            "gemini-3-flash-preview"
    );

    public String resolve(String requested) {
        if (requested == null || requested.isBlank()) return DEFAULT_MODEL;
        if (!ALLOWED_MODELS.contains(requested)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "허용되지 않은 모델입니다.");
        }
        return requested;
    }
}
