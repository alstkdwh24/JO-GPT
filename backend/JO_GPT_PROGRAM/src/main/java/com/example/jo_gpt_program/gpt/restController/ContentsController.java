package com.example.jo_gpt_program.gpt.restController;

import com.example.entitycom.dto.MessageDTO;
import com.example.jo_gpt_program.gpt.config.LlmModelPolicy;
import com.example.jo_gpt_program.gpt.config.filter.UserInfoDto;
import com.example.jo_gpt_program.gpt.dto.ChatMessageDTO;
import com.example.jo_gpt_program.gpt.dto.MyChatDTO;
import com.example.jo_gpt_program.gpt.dto.ShowChatDTO;
import com.example.jo_gpt_program.gpt.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/contents")
@Slf4j
public class ContentsController {

    private final String geminiKey;

    private final ShowChatService showChatService;

    private final MyAuthService myAuthService;

    private final GeminiService geminiService;
    private final GoogleApiService googleApiService;
    private final RagService ragService;
    private final ScholarSearchService scholarSearchService;
    private final ChatMysqlService chatMysqlService;
    private final LlmModelPolicy llmModelPolicy;

    // static 메서드 사용 시, 생성자 사용 불가
    public ContentsController(@Value("${spring.llm.key}") String geminiKey,
                              ShowChatService showChatService, MyAuthService myAuthService, GeminiService geminiService, GoogleApiService googleApiService, RagService ragService, ScholarSearchService scholarSearchService, ChatMysqlService chatMysqlService, LlmModelPolicy llmModelPolicy) {

        this.geminiKey = geminiKey;
        this.showChatService = showChatService;

        this.myAuthService = myAuthService;
        this.geminiService = geminiService;
        this.googleApiService = googleApiService;
        this.ragService = ragService;
        this.scholarSearchService = scholarSearchService;
        this.chatMysqlService = chatMysqlService;
        this.llmModelPolicy = llmModelPolicy;
    }
    // 나의 메시지를 llm에 보내고 db에 저장하는 메서드
    @PostMapping("/myContents")
    public ResponseEntity<String> getMyContents(@RequestBody MyChatDTO dto) {
        // Object 객체 생성
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserInfoDto userInfo)) {
            return ResponseEntity.status(401).build();
        }
        // memberKey 키를 반환
        Long memberKey = Long.parseLong(userInfo.getMemberId());

        // mysql 만들기
        String success = chatMysqlService.userInfo(memberKey, dto);
        return ResponseEntity.ok(success);
    }
    private Long getMemberKey() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        // 현재 서버 메모리에 저장된 보안 정보 창고
        // getContext() 현재 요청의 보안 컨텍스트 꺼내기
        // getAuthentication()  인증 정보 꺼내기
        // getPrincipal() 그 중에서 누구인지 꺼내기
        if (principal instanceof UserInfoDto userInfo) {
            return Long.parseLong(userInfo.getMemberId());
        }
        return null;
    }

    /* Gemini 호출 — Authorization으로 멤버 식별, DB의 활성 프롬프트 자동 적용 */
    @PostMapping("/gptContents")
    public ResponseEntity<String> getGptContents(@RequestBody MyChatDTO dto,
                                                 @RequestHeader(value = "X-Model", required = false) String requestedModel) {


        String model = llmModelPolicy.resolve(requestedModel);
        Long memberKey = getMemberKey();
        // 남의 채팅방 대화기록을 읽거나 AI 응답을 써 넣지 못하게 LLM 호출 전에 주인 확인
        showChatService.checkOwner(dto.getShowChatKey());
        //  customPrompt를 헤더 대신 body(dto)에서 꺼냄 → 헤더 크기 초과 문제 해결
        String response = geminiService.sendGeminiAI(dto, model, dto.getCustomPrompt(), memberKey);
        return ResponseEntity.ok(response);
    }



    //  채팅방 생성 + AI 응답 한번에 처리 (첫 메시지 로딩 속도 개선)
    @PostMapping("/chatRoom/first")
    public ResponseEntity<Map<String, Object>> createChatRoomAndGetResponse(
            @RequestBody MyChatDTO dto,
            @RequestHeader(value = "X-Model", required = false) String requestedModel) {

        String model = llmModelPolicy.resolve(requestedModel);

        // 1. 채팅방 생성
        Long showChatKey = showChatService.createChat(dto);

        // 2. DTO에 채팅방 키 세팅
        dto.setShowChatKey(showChatKey);

        // 3. AI 응답 요청 ( customPrompt를 헤더 대신 body(dto)에서 꺼냄)
        String response = geminiService.sendGeminiAI(dto, model, dto.getCustomPrompt(), getMemberKey());

        // 4. 채팅방 키 + AI 응답 한번에 반환
        return ResponseEntity.ok(Map.of(
                "chatKey", String.valueOf(showChatKey),
                "response", response
        ));
    }

    // 여기서는 엔티티를 넣는 것보다는 DTO필드를 넣으면 된다 조인한 데이터가 필요하다면 DTO에 넣으면 된다.
    // 채팅방 목록 조회 API
    @GetMapping("/chattingList")
    public ResponseEntity<Set<ShowChatDTO>> getChattingList() {
        Set<ShowChatDTO> showChatList = chatMysqlService.getChattingList();
        return ResponseEntity.ok(showChatList);
    }

    /* 채팅방 대화 내역 조회 */
    @GetMapping("/chatRoom/{showChatKey}/messages")
    public ResponseEntity<List<ChatMessageDTO>> getChatHistory(
            @PathVariable Long showChatKey) {
        List<ChatMessageDTO> messages = showChatService.getChatMessages(showChatKey);
        return ResponseEntity.ok(messages);
    }

    /* 채팅방 삭제 */
    @DeleteMapping("/chatRoom/{showChatKey}")
    public ResponseEntity<Void> deleteChatRoom(@PathVariable Long showChatKey) {
        showChatService.deleteChat(showChatKey);
        return ResponseEntity.ok().build();
    }

    // SSE를 이용한 실시간 알림 API
    @PostMapping(value = "/notifications", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getNotifications(@RequestBody MessageDTO messageDTO) {
        String message = messageDTO.getMessage();
        SseEmitter emitter = new SseEmitter(60_000L);
        try {
            if (message != null) {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(message));
            }
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    // 학술 검색 + AI 답변
    @PostMapping("/getScholarContents")
    public ResponseEntity<String> postMethodName(@RequestBody MyChatDTO dto,
                                                 @RequestHeader(value = "X-Model", required = false) String requestedModel) {
        String model = llmModelPolicy.resolve(requestedModel);
        showChatService.checkOwner(dto.getShowChatKey());
        String response = scholarSearchService.sendWithScholar(dto, model, dto.getCustomPrompt());
        return ResponseEntity.ok(response);
    }

    // RAG + 학술 검색 동시 적용
    @PostMapping("/getRagScholarContents")
    public ResponseEntity<String> getGptRagScholarContents(
            @RequestBody MyChatDTO dto,
            @RequestHeader(value = "X-Model", required = false) String requestedModel) {
        String model = llmModelPolicy.resolve(requestedModel);
        showChatService.checkOwner(dto.getShowChatKey());
        String response = scholarSearchService.sendWithRagAndScholar(dto, model, dto.getCustomPrompt(), getMemberKey());
        return ResponseEntity.ok(response);
    }


    // 문서 찾는 로직
    @PostMapping("/documents/search")
    public ResponseEntity<String> findDocument(@RequestBody Map<String, String> body){
        try {
            String context = ragService.findDocument(body.get("query"), getMemberKey());
            return ResponseEntity.ok(context);
        } catch (Exception e) {
            return ResponseEntity.ok("");
        }
    }

    // 채팅방 검색 — 검색어가 대화 내용 일부라 URL(로그)에 남지 않게 body로 받음
    @PostMapping("/searchChatting")
    public ResponseEntity<Set<ShowChatDTO>> searchChatting(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(showChatService.searchMyChats(body.get("search")));
    }

}

