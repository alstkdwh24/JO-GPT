package com.example.jo_gpt_program.gpt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MyChatDTO {
    private Long myChatKey;
    @JsonProperty("myChatContents") // JSON의 "gptContents"를 이 필드에 담겠다는 의미
    private String myChatContents;
    private String myChatImage;
    private LocalDateTime myChatRegistration;
    private Long showChatKey;
    private List<FilePartDTO> files;
    private String customPrompt; // ✅ X-Custom-Prompt 헤더 대신 body로 받기
    @Getter
    public static class FilePartDTO {
        private String name;
        private String mimeType;
        private String data;
        private String type;

        public String getData() {
            return this.data;
        }

        public String getMimeType() {
            return this.mimeType;
        }
    }

    public void changeMyChatContents(String myChatContents) {
        this.myChatContents = myChatContents;
    }

    public void changeMyChatImage(String myChatImage) {
        this.myChatImage = myChatImage;
    }

    public void changeMyChatRegistration(LocalDateTime myChatRegistration) {
        this.myChatRegistration = myChatRegistration;
    }

    public void myChatKey(Long myChatKey) {
        this.myChatKey = myChatKey;
    }

    // ✅ 채팅방 키 세팅 메서드 추가
    public void setShowChatKey(Long showChatKey) {
        this.showChatKey = showChatKey;
    }
}
