package com.example.jo_gpt_program.gpt.service;

import com.example.entitycom.dto.MessageDTO;
import com.example.entitycom.entity.chat.ShowChat;
import com.example.entitycom.entity.gpt.GptChat;
import com.example.entitycom.entity.log.CreateTimeLogs;
import com.example.entitycom.entity.member.Members;
import com.example.entitycom.entity.member.MyChat;
import com.example.jo_gpt_program.gpt.config.filter.UserInfoDto;
import com.example.jo_gpt_program.gpt.dto.ChatMessageDTO;
import com.example.jo_gpt_program.gpt.dto.MyChatDTO;
import com.example.jo_gpt_program.gpt.dto.ShowChatDTO;
import com.example.jo_gpt_program.gpt.repository.jpa.*;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service("showChatService")
public class ShowChatService {

    private final ShowChatRepository showChatRepository;
    private final MemberRepository memberRepository;
    private final MyChatRepository myChatRepository;
    private final CreateTimeRepository createTimeRepository;

    private final GptChatRepository gptChatRepository;

    public ShowChatService(ShowChatRepository showChatRepository, MemberRepository memberRepository, MyChatRepository myChatRepository, CreateTimeRepository createTimeRepository, GptChatRepository gptChatRepository) {
        this.showChatRepository = showChatRepository;
        this.memberRepository = memberRepository;
        this.myChatRepository = myChatRepository;
        this.createTimeRepository = createTimeRepository;
        this.gptChatRepository = gptChatRepository;
    }

    private Members getMemberFromContext() {
        UserInfoDto userInfo = (UserInfoDto) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        Long memberKey = Long.parseLong(userInfo.getMemberId());
        return memberRepository.findByMemberKey(memberKey)
                .orElseThrow(() -> new RuntimeException("Member not found: " + memberKey));
    }

    /* 채팅방 생성 메서드 */
    /* 채팅방 만드는 메서드 */
    @Transactional
    public Long createChat(MyChatDTO dto) {
        Members members = getMemberFromContext();

        ShowChat showChat = ShowChat.builder()
                .members(members)
                .build();
        ShowChat showChat1 = showChatRepository.save(showChat);

        log.debug("showChat1={}", showChat1.getShowChatKey());
        MyChat myChat = MyChat.builder()
                .showChat(showChat1)
                .member(members)
                .myChatContents(dto.getMyChatContents())
                .build();
        myChatRepository.save(myChat);

        CreateTimeLogs createTimeLogs = CreateTimeLogs.builder()
                .showChat(showChat1)
                .build();
        createTimeRepository.save(createTimeLogs);
        log.debug("showChat={}", showChat1);

        return showChat1.getShowChatKey();
    }


    // 채팅방 번호 찾기 - 없으면 null 반환 (예외 대신)
    public ShowChat findShowNumber(Long showChatKey) {
        if (showChatKey == null) return null;
        Optional<ShowChat> showChatNumber = showChatRepository.findShowChatByShowChatKey(showChatKey);
        return showChatNumber.orElse(null);
    }

    public Set<ShowChatDTO> findShowChatNumber(Members members) {
        Set<ShowChat> showChats = showChatRepository.findByMembers(members);
        log.debug("showChatReal:{}", showChats.stream());
        return showChats.stream().map(chat -> ShowChatDTO.builder()
                        .showChatKey(chat.getShowChatKey())
                        .showChatRegistration(
                                chat.getCreateTimeLogs() != null && !chat.getCreateTimeLogs().isEmpty()
                                        ? chat.getCreateTimeLogs().stream()
                                        .map(log1 -> log1.getCreatedAt()) // CreateTimeLogs -> LocalDateTime으로 변환
                                        .filter(date -> date != null) // null 제거
                                        .max(Comparator.naturalOrder())  // 가장 최신 날짜
                                        .orElse(null) // 아무것도 없으면 null 반환

                                        : null)
                        // 채팅방 생성 시각
                        .showMyChatContents(chat.getMyChat() != null && !chat.getMyChat().isEmpty()
                                ? chat.getMyChat().iterator().next().getMyChatContents()
                                .replaceAll("\\s+", " ")
                                .substring(0, Math.min(50, chat.getMyChat().iterator().next().getMyChatContents().length()))
                                : null)
                        // 채팅방 목록에 미리보기로 보여줄 첫 번째 메시지 내용이다.

                        .build())
                .sorted(Comparator.comparing(ShowChatDTO::getShowChatRegistration,  // 정렬기준 : 날짜 필드
                        Comparator.nullsLast(Comparator.naturalOrder())  // null이면 맨 뒤로

                ).reversed()) // 최신순
                .collect(Collectors.toCollection(LinkedHashSet:: new));
    }
    /* 채팅방 삭제 */
    @Transactional
    public void deleteChat(String authHeader, Long showChatKey) {
        // 채팅방 삭제 메서드
        showChatRepository.deleteById(showChatKey);
    }


    /* 채팅방의 대화 내역 불러오기 (user + ai 메시지를 시간순 정렬) */
    @Transactional
    public List<ChatMessageDTO> getChatMessages(Long showChatKey) {
        // 채팅방 조회
        ShowChat showChat = showChatRepository.findShowChatByShowChatKey(showChatKey)
                .orElseThrow(() -> new RuntimeException("ShowChat not found: " + showChatKey));

        List<Object[]> entries = new ArrayList<>();
        // User 메시지 추가
        if (showChat.getMyChat() != null) {
            showChat.getMyChat().forEach(m -> entries
                    .add(new Object[] { m.getMyChatKey(), "user", m.getMyChatContents() }));
        }
        if (showChat.getGptChat() != null) {
            // AI 메시지 추가
            showChat.getGptChat().forEach(g -> entries
                    .add(new Object[] { g.getGptChatKey(), "ai", g.getGptChatContents() }));

        }
        // 시간순으로 정렬
        entries.sort(Comparator.comparingLong(e -> (Long) e[0]));

        return entries.stream()
                .map(e -> new ChatMessageDTO((String) e[1], (String) e[2]))
                .collect(Collectors.toList());
    }

    public List<GptChat> findShowRoom(MessageDTO dto) {
        List<GptChat> gptChat =gptChatRepository.findByGptChatContents(dto.getMessage());

        return gptChat;
    }
}
