package com.example.jo_gpt_program.gpt.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ShowChatDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long showChatKey;

    private String showMyChatContents;

    private LocalDateTime showChatRegistration;

}
