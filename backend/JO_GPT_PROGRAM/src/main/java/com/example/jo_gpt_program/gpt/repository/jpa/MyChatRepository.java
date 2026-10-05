package com.example.jo_gpt_program.gpt.repository.jpa;

import com.example.entitycom.entity.member.MyChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("myChatRepository")
public interface MyChatRepository extends JpaRepository <MyChat, Long>{


}
