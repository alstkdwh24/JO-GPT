package com.example.jo_gpt_program.gpt.repository.jpa;

import com.example.entitycom.entity.chat.ShowChat;
import com.example.entitycom.entity.member.Members;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface ShowChatRepository extends JpaRepository<ShowChat, Long> {


    Set<ShowChat> findByMembers(@Param("members") Members members);

    Optional<ShowChat> findShowChatByShowChatKey(Long showChatKey);
}
