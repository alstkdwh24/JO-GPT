package com.example.jo_gpt_program.gpt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MemberPromptDTO {
    private Long promptKey;
    private String promptName;
    private String promptContent;
    private Boolean isActive;
}
