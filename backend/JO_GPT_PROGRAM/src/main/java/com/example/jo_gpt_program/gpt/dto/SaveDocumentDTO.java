package com.example.jo_gpt_program.gpt.dto;

import lombok.Getter;

@Getter
public class SaveDocumentDTO {
    private String context;
    private String source;    // ex) "user_input", "web", "manual"
    private String category;  // ex) "식당", "날씨", 일단
}
