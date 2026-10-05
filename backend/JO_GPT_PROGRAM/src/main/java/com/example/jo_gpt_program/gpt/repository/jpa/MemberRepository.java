package com.example.jo_gpt_program.gpt.repository.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.entitycom.entity.member.Members;

@Repository("joGptMemberRepository")
public interface MemberRepository extends JpaRepository<Members, Long> {

    Optional<Members> findByMemberKey(Long memberKey);
}