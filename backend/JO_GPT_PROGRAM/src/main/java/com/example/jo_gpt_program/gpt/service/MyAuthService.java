package com.example.jo_gpt_program.gpt.service;

import com.example.entitycom.entity.member.Members;
import com.example.jo_gpt_program.gpt.config.filter.UserInfoDto;
import com.example.jo_gpt_program.gpt.repository.jpa.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service("myAuthService")
public class MyAuthService {

    private final MemberRepository memberRepository;
    private final UserInfoService userInfoService;

    public MyAuthService(MemberRepository memberRepository, UserInfoService userInfoService) {
        this.memberRepository = memberRepository;
        this.userInfoService = userInfoService;
    }

    // 인증정보 가져오기
    public Members myInfo(Members members) {
        Optional<Members> membersKey = memberRepository.findByMemberKey(members.getMemberKey());
        return membersKey.orElseThrow(() -> new RuntimeException(
                "Member not found with key: " + members.getMemberKey()));
    }

    /* SecurityContextHolder에서 인증된 사용자 정보 추출 */
    public Members getMemberFromContext() {
        UserInfoDto userInfo = (UserInfoDto) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        Long memberKey = Long.parseLong(userInfo.getMemberId());
        return userInfoService.userInfoTwo(memberKey);
    }
}
