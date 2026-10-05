package com.example.jo_gpt_program.gpt.service;

import com.example.entitycom.entity.chat.ShowChat;
import com.example.entitycom.entity.log.CreateTimeLogs;
import com.example.entitycom.entity.member.Members;
import com.example.entitycom.entity.member.MyChat;
import com.example.jo_gpt_program.gpt.dto.MyChatDTO;
import com.example.jo_gpt_program.gpt.dto.ShowChatDTO;
import com.example.jo_gpt_program.gpt.repository.jpa.MemberRepository;
import com.example.jo_gpt_program.gpt.repository.jpa.MyChatRepository;
import jakarta.transaction.Transactional;
import com.example.jo_gpt_program.gpt.config.filter.UserInfoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;
@Slf4j
@Service("chatMysqlService")
public class ChatMysqlService {

    private final MyChatRepository myChatRepository;
    private final MemberRepository memberRepository;
    private final ShowChatService showChatService;

    public ChatMysqlService(MyChatRepository myChatRepository, MemberRepository memberRepository, ShowChatService showChatService) {
        this.myChatRepository = myChatRepository;
        this.memberRepository = memberRepository;
        this.showChatService = showChatService;
    }

    public MyChat saveChat(Members members, ShowChat showChat, String myChatContents, String myChatImage) {
        MyChat chat = MyChat.builder()
                .member(members)
                .showChat(showChat)
                .myChatContents(myChatContents)
                .myChatImage(myChatImage)
                .createTimeLogs(CreateTimeLogs.builder().build())
                .build();
        return myChatRepository.save(chat);
    }

    @Transactional
    public String myChat(MyChatDTO dto, Members member) {
        Members members = memberRepository.findByMemberKey(member.getMemberKey())
                .orElseThrow(() -> new RuntimeException("Member not found: " + member.getMemberKey()));

        // showChatKey가 없거나 DB에 없으면 null 반환
        ShowChat showChat = showChatService.findShowNumber(dto.getShowChatKey());
        if (showChat == null) {
            log.warn("ShowChat not found for key: {}, localStorage를 초기화해주세요.", dto.getShowChatKey());
            return null;
        }

        log.debug("showChatTwo={}", showChat);
        MyChat chat = this.saveChat(members, showChat, dto.getMyChatContents(), dto.getMyChatImage());
        return chat.getMyChatContents();
    }

    /* 채팅 리스트 불러오기 */
    @Transactional
    public Set<ShowChatDTO> getChattingList() {
        UserInfoDto userInfo = (UserInfoDto) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long memberKey = Long.parseLong(userInfo.getMemberId());
        Members members = memberRepository.findByMemberKey(memberKey)
                .orElseThrow(() -> new RuntimeException("Member not found: " + memberKey));
        Set<ShowChatDTO> showChatDTOS = showChatService.findShowChatNumber(members);
        return showChatDTOS;
    }

    public String userInfo(Long memberKey, MyChatDTO dto) {
        Members member = memberRepository.findByMemberKey(memberKey)
                .orElseThrow(() -> new RuntimeException("Member not found: " + memberKey));
        log.debug("member={}", member);
        return myChat(dto, member);
    }
}
