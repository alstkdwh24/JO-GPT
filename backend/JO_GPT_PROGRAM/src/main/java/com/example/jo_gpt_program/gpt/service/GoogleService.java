package com.example.jo_gpt_program.gpt.service;

import com.example.jo_gpt_program.gpt.dto.MyChatDTO;
import com.example.jo_gpt_program.gpt.repository.jpa.ConnectedAccountsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

@Service
@Slf4j
public class GoogleService {

    private final ChatModel chatModel;
    private final CalendarService calendarService;
    private final GmailService gmailService;
    private final YouTubeSummaryService youtubeSummaryService;
    private final ConnectedAccountsRepository connectedAccountsRepository;

    public GoogleService(ChatModel chatModel, CalendarService calendarService, GmailService gmailService,
                         YouTubeSummaryService youtubeSummaryService, ConnectedAccountsRepository connectedAccountsRepository) {
        this.chatModel = chatModel;
        this.calendarService = calendarService;
        this.gmailService = gmailService;
        this.youtubeSummaryService = youtubeSummaryService;
        this.connectedAccountsRepository = connectedAccountsRepository;
    }

    // AI로 사용자 의도 분류 (Google API 호출 여부 판단)
    private String classifyIntent(String userMessage) {
        String prompt = "다음 중 하나만 출력해. 절대 다른 말 하지 마.\n" +
                "CALENDAR_ADD / CALENDAR_VIEW / MAIL_SEND / MAIL_VIEW / YOUTUBE / NONE\n\n" +
                "규칙:\n" +
                "- 회의, 미팅, 약속, 일정 잡기, 스케줄 등록 → CALENDAR_ADD\n" +
                "- 일정 확인, 오늘/이번주 일정, 뭐 있어 → CALENDAR_VIEW\n" +
                "- 메일/이메일 보내기 → MAIL_SEND\n" +
                "- 메일/이메일 확인, 최근 메일 → MAIL_VIEW\n" +
                "- 유튜브 요약, youtube URL → YOUTUBE\n" +
                "- 그 외 → NONE\n\n" +
                "메시지: " + userMessage;
        return modelCall(chatModel, prompt).trim().toUpperCase();
    }

    // 구글 서비스 처리 메서드
    public String handleGoogleAction(String userMessage, Long memberKey, List<MyChatDTO.FilePartDTO> files, List<MyChatDTO.FilePartDTO> dtoFiles) {

        // 유튜브는 URL 있으면 바로 처리 (intent 분류 전)
        String youtubeUrl = extractUrl(userMessage);
        if (youtubeUrl != null && (youtubeUrl.contains("youtube") || youtubeUrl.contains("youtu.be"))) {
            try {
                return youtubeSummaryService.summarize(youtubeUrl);
            } catch (Exception e) {
                log.error("[handleGoogleAction] 유튜브 요약 실패: {}", e.getMessage());
                return "유튜브 요약 중 오류가 발생했어요: " + e.getMessage();
            }
        }

        // AI로 의도 파악
        String intent = classifyIntent(userMessage);
        log.info("[handleGoogleAction] intent={}", intent);

        // intent 정리
        intent = intent.replace(".", "").replace(":", "").replace(" ", "_").trim();
        log.info("[handleGoogleAction] intent={}", intent);

        // NONE 또는 Google 관련 아니면 → Gemini 일반 답변
        if (!intent.contains("CALENDAR") && !intent.contains("MAIL") && !intent.contains("YOUTUBE")) {
            return null;
        }

        // 유튜브 요약
        if (intent.contains("YOUTUBE")) {
            if (youtubeUrl == null) {
                return "유튜브 URL을 함께 입력해주세요!\n예시: \"https://youtu.be/abc123 요약해줘\"";
            }
            try {
                return youtubeSummaryService.summarize(youtubeUrl);
            } catch (Exception e) {
                log.error("[handleGoogleAction] 유튜브 요약 실패: {}", e.getMessage());
                return "유튜브 요약 중 오류가 발생했어요: " + e.getMessage();
            }
        }

        // 최근 메일 조회
        if (intent.contains("MAIL_VIEW")) {
            try {
                List<String> emails = gmailService.getRecentEmails(memberKey);
                if (emails.isEmpty()) return "최근 메일이 없어요!";
                StringBuilder sb = new StringBuilder("📬 최근 메일 목록이에요!\n\n");
                for (int i = 0; i < emails.size(); i++) {
                    sb.append(i + 1).append(". ").append(emails.get(i)).append("\n");
                }
                return sb.toString();
            } catch (Exception e) {
                return handleGoogleError("[handleGoogleAction] 메일 조회 실패", e, "메일 조회");
            }
        }

        // 메일 발송
        if (intent.contains("MAIL_SEND")) {
            try {
                // 연동된 내 이메일 주소 조회 (나한테, 나에게 같은 1인칭 처리용)
                String myEmail = connectedAccountsRepository
                        .findByMember_MemberKeyAndProvider(memberKey, "google")
                        .map(a -> a.getProviderEmail() != null ? a.getProviderEmail() : "")
                        .orElse("");

                String extractPrompt = "사용자 메시지에서 이메일 정보를 추출하세요.\n" +
                        "형식: 이메일주소|제목|내용\n" +
                        "반드시 위 형식으로만 답해. | 기호는 정확히 2개여야 해.\n" +
                        "중요: 받는 사람이 \"나\", \"나한테\", \"나에게\", \"내 메일\", \"내게\" 등 1인칭이면 " +
                        "이메일 주소를 \"" + myEmail + "\" 으로 사용해.\n" +
                        "사용자 메시지: " + userMessage;

                String extracted = modelCall(chatModel, extractPrompt);
                log.info("[handleGoogleAction] 메일 파싱 결과: [{}]", extracted);
                String[] parts = extracted.trim().split("\\|");
                if (parts.length < 3) {
                    return "메일 정보를 파악하지 못했어요.\n예시: \"hong@gmail.com 에게 제목: 안녕 내용: 반가워 라고 메일 보내줘\"";
                }

                if (files != null && !files.isEmpty()) {
                    // 첨부파일 있을 때
                    MyChatDTO.FilePartDTO file = files.get(0);
                    byte[] fileData = Base64.getDecoder().decode(file.getData());
                    gmailService.sendEmailWithAttachment(
                            memberKey, parts[0].trim(), parts[1].trim(), parts[2].trim(),
                            fileData, file.getName(), file.getMimeType()
                    );
                    return "✅ 파일 첨부해서 발송했어요!\n받는 사람: " + parts[0].trim()
                            + "\n첨부파일: " + file.getName();
                } else {
                    // 첨부파일 없을 때
                    gmailService.sendEmail(memberKey, parts[0].trim(), parts[1].trim(), parts[2].trim());
                    return "✅ 메일을 발송했어요!\n받는 사람: " + parts[0].trim() + "\n제목: " + parts[1].trim();
                }
            } catch (Exception e) {
                return handleGoogleError("[handleGoogleAction] 메일 발송 실패", e, "메일 발송");
            }
        }

        // 캘린더 일정 조회
        if (intent.contains("CALENDAR_VIEW")) {
            try {
                List<String> events = calendarService.getEvents(memberKey);
                if (events.isEmpty()) return "등록된 일정이 없어요!";
                StringBuilder sb = new StringBuilder("📅 가까운 일정이에요!\n\n");
                for (int i = 0; i < events.size(); i++) {
                    sb.append(i + 1).append(". ").append(events.get(i)).append("\n");
                }
                return sb.toString();
            } catch (Exception e) {
                return handleGoogleError("[handleGoogleAction] 캘린더 조회 실패", e, "캘린더 조회");
            }
        }

        // 캘린더 일정 추가
        if (intent.contains("CALENDAR_ADD")) {
            try {
                String today = java.time.LocalDate.now().toString();
                String extractPrompt = "사용자 메시지에서 일정 정보를 추출하세요.\n" +
                        "형식: 제목|시작시간|종료시간\n" +
                        "시간 형식은 반드시 ISO 8601 형식으로: 예) 2025-06-01T09:00:00+09:00\n" +
                        "오늘 날짜는 " + today + " 이야. 내일/모레 등 상대적 날짜도 계산해서 넣어줘.\n" +
                        "종료시간이 없으면 시작시간 1시간 후로 설정해줘.\n" +
                        "반드시 위 형식으로만 답해. | 기호는 정확히 2개여야 해.\n" +
                        "사용자 메시지: " + userMessage;
                String extracted = modelCall(chatModel, extractPrompt);
                log.info("[handleGoogleAction] 일정 파싱 결과: [{}]", extracted);
                String[] parts = extracted.trim().split("\\|");
                if (parts.length < 3) {
                    return "일정 정보를 파악하지 못했어요.\n예시: \"내일 오전 10시에 팀 미팅 일정 추가해줘\"";
                }
                calendarService.createEvent(memberKey, parts[0].trim(), parts[1].trim(), parts[2].trim());
                return "✅ 일정을 추가했어요!\n제목: " + parts[0].trim() + "\n시작: " + parts[1].trim();
            } catch (Exception e) {
                return handleGoogleError("[handleGoogleAction] 일정 추가 실패", e, "일정 추가");
            }
        }

        return null;
    }

    // 에러 처리 공통 메서드
    private String handleGoogleError(String logMsg, Exception e, String actionName) {
        log.error("{}: {}", logMsg, e.getMessage());
        String errMsg = e.getMessage() != null ? e.getMessage() : "";
        if (errMsg.contains("not connected") || errMsg.contains("401") || errMsg.contains("token")) {
            return "GOOGLE_NOT_CONNECTED";
        }
        return actionName + " 중 오류가 발생했어요: " + errMsg;
    }

    // 메시지에서 URL 주소만 뽑아주는 것
    private String extractUrl(String message) {
        String[] words = message.split("\\s+");
        for (String word : words) {
            if (word.startsWith("http://") || word.startsWith("https://")) {
                return word;
            }
        }
        return null;
    }

    // LLM 모델 호출
    private String modelCall(ChatModel chatModel, String prompt) {
        ChatClient independentClient = ChatClient.builder(chatModel).build();
        return independentClient.prompt()
                .options(GoogleGenAiChatOptions.builder()
                        .maxOutputTokens(200)
                        .build())
                .user(prompt)
                .call()
                .content();
    }
}