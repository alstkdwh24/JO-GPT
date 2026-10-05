package com.example.jo_gpt_program.gpt.service;

import com.example.entitycom.entity.member.Members;
import com.example.jo_gpt_program.gpt.repository.jpa.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service( "userInfoService")
@Slf4j
public class UserInfoService {
    private final MemberRepository memberRepository;

    public UserInfoService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /* 유저 정보 불러오기 */
    public Members userInfoTwo(Long memberKey) {
        // Optional은 null 이 될수도 값이 있을수도 있음을 표현하는 컨테이너이다. memberKey로 멤버 정보 조회
        Members member = memberRepository.findByMemberKey(memberKey)
                .orElseThrow(() -> new RuntimeException("Member not found with key: " + memberKey));
        log.debug("member={}", member);
        return member;
    }
}
