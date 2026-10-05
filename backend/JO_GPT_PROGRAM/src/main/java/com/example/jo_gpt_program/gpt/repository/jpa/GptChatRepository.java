package com.example.jo_gpt_program.gpt.repository.jpa;

import com.example.entitycom.entity.gpt.GptChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GptChatRepository extends JpaRepository<GptChat, Long> {
    List<GptChat> findByGptChatContents(String message);
}