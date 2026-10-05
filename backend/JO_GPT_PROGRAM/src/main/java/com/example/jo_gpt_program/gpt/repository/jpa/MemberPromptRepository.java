package com.example.jo_gpt_program.gpt.repository.jpa;

import com.example.entitycom.entity.member.MemberPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberPromptRepository extends JpaRepository<MemberPrompt, Long> {

}
