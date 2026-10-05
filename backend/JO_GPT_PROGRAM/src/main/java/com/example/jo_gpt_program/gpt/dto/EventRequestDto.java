package com.example.jo_gpt_program.gpt.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EventRequestDto {
    private String title;
    private String start;  // "2026-05-29T10:00:00+09:00" 형식
    private String end;    // "2026-05-29T11:00:00+09:00" 형식
}