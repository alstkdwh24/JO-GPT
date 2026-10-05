package com.example.jo_gpt_program.gpt.service;

import com.example.entitycom.entity.gpt.GptChat;
import com.example.jo_gpt_program.gpt.dto.MyChatDTO;
import com.example.jo_gpt_program.gpt.dto.SaveResultResponse;
import com.example.jo_gpt_program.gpt.repository.jpa.GptChatRepository;
import com.example.jo_gpt_program.gpt.repository.jpa.ShowChatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service("geminiService")
public class GeminiService {
    @Value("${spring.llm.key}")
    private String geminiApiKey;
    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final RagService ragService;

    private final ShowChatRepository showChatRepository;
    private final GptChatRepository gptChatRepository;
    private final ScholarSearchService scholarSearchService;


    private final VectorStore vectorStore;

    private final KakaoMapService kakaoMapService;
    private final GoogleService googleService;

    private final AlertService alertService;


    private static final List<String> MAP_KEYWORDS = List.of("카페", "커피숍", "커피전문점", "맛집", "식당", "음식점", "레스토랑", "한식당", "고깃집", "분식", "분식집", "술집", "이자카야", "바", "펍", "포차", "빵집", "베이커리", "디저트", "아이스크림", "공원", "해변", "해수욕장", "계곡", "산", "호수", "폭포", "섬", "쇼핑몰", "백화점", "마트", "시장", "아울렛", "박물관", "미술관", "갤러리", "전시관", "전시회", "도서관", "스터디카페", "독서실", "헬스장", "피트니스", "수영장", "볼링장", "당구장", "호텔", "모텔", "펜션", "게스트하우스", "리조트", "놀이공원", "워터파크", "동물원", "식물원", "병원", "약국", "은행", "편의점", "영화관", "공연장", "극장", "어디", "위치", "장소", "지도", "근처", "주변", "찾아줘", "알려줘");

    public GeminiService(ChatClient chatClient, ChatModel chatModel, ChatMemory chatMemory, RagService ragService, ShowChatRepository showChatRepository, GptChatRepository gptChatRepository, ScholarSearchService scholarSearchService, VectorStore vectorStore, KakaoMapService kakaoMapService, GoogleService googleService, AlertService alertService) {
        this.chatClient = chatClient;
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.ragService = ragService;
        this.showChatRepository = showChatRepository;
        this.gptChatRepository = gptChatRepository;
        this.scholarSearchService = scholarSearchService;
        this.vectorStore = vectorStore;
        this.kakaoMapService = kakaoMapService;

        this.googleService = googleService;
        this.alertService = alertService;
    }

    private String extractLocationWithAI(String userMessage) {
        String keywords = String.join(", ", MAP_KEYWORDS);
        String prompt = """
                다음 문장에서 지도 검색할 장소명을 추출하세요.
                장소 유형 키워드: %s
                
                규칙:
                1. 지역명 + 장소유형 → 둘 다 반환  (예: 홍대 카페)
                2. 장소명만 있으면 → 그대로 반환  (예: 스타벅스)
                3. 장소유형만 있으면 → CURRENT_LOCATION:장소유형  (예: CURRENT_LOCATION:카페)
                4. 특정 불가 → NONE
                5. 특정 지역/역 언급 시 → 반드시 그 지역 장소만 반환
                6. 조사/어미(에서, 의, 으로, 쪽 등) 제거, 행정구역(시/구/동)은 유지
                
                결과만 반환, 설명 금지.
                문장: %s
                """.formatted(keywords, userMessage);

        try {
            String result = this.modelCall(chatModel, prompt);
            if (result == null) return "NONE";
            String cleaned = result.trim().replaceAll("[\"':\\[\\]]", "").trim();
            return cleaned.isEmpty() ? "NONE" : cleaned;
        } catch (Exception e) {
            log.error("[extractLocationWithAI] AI 추출 실패: {}", e.getMessage());
            return "NONE";
        }
    }

    // llm 모델과 설정 가져오기
    private String modelCall(ChatModel chatModel, String prompt) {
        ChatClient independentClient = ChatClient.builder(chatModel).build();
        return independentClient.prompt().options(GoogleGenAiChatOptions.builder().maxOutputTokens(200).build()).user(prompt).call().content();
    }

    // 키워드가 포함되면 작동
    private boolean isMapRequest(String message) {
        return MAP_KEYWORDS.stream().anyMatch(message::contains);
    }

    public String sendGeminiAI(MyChatDTO dto, String model, String customPrompt, Long memberKey) {
        if (memberKey != null) {
            String googleResult = googleService.handleGoogleAction(dto.getMyChatContents(), memberKey, dto.getFiles(), dto.getFiles());
            if (googleResult != null) return googleResult;
        }
        UserMessage message = this.userMessageGet(dto);
        String webResults = scholarSearchService.searchWithTavily(dto.getMyChatContents());
        String ragResult = (customPrompt != null && !customPrompt.isBlank()) ? "" : ragService.findDocument(dto.getMyChatContents());
        String systemPrompt = getString(customPrompt, webResults, ragResult);
        String conversationId = dto.getShowChatKey() != null ? dto.getShowChatKey().toString() : null;
        List<Message> history = conversationId != null ? chatMemory.get(conversationId) : List.of();

        log.info("[DEBUG] conversationId={}, historySize={}", conversationId, history.size());
        history.forEach(msg -> log.info("[DEBUG] history msg - type={}, text={}", msg.getMessageType(), msg.getText()));

        if (model.contains("image")) {
            return sendGeminiImageDirect(dto.getMyChatContents(), dto.getFiles(), model, systemPrompt, dto.getShowChatKey(), conversationId, history, message, ragResult, memberKey);
        }
        String response = this.sendMessage(history, message, model, systemPrompt);
        log.info("[LLM 원본 응답] response={}", response);
        response = this.mapLoad(dto.getMyChatContents(), response);
        SaveResultResponse saveResultResponse = this.saveResult(conversationId, message, response, dto.getShowChatKey());
        final String finalResponse = response;
        // Promise.all()과 동일
        // 둘다 끝날때까지 대기
        CompletableFuture.allOf(
                // 별도 쓰레드에서 코드를 비동기로 실행시키는 메서드
                CompletableFuture.runAsync(() -> {
            try {
                alertService.sendAlert(memberKey, finalResponse);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }), CompletableFuture.runAsync(() -> saveToVectorStore(ragResult, finalResponse, saveResultResponse.id()))).join();
        return response;
    }

    private String sendGeminiImageDirect(String userMessage, List<MyChatDTO.FilePartDTO> files, String model, String systemText, Long showChatKey, String conversationId, List<Message> history, UserMessage message, String ragResult, Long memberKey) {
        List<Part> parts = buildParts(userMessage, files);
        List<Content> contents = buildContents(history, parts);
        Client client = Client.builder().apiKey(geminiApiKey).build();
        Content systemInstruction = systemInstruction(systemText);
        GenerateContentConfig config = generateContentConfigs(systemInstruction);
        GenerateContentResponse response = client.models.generateContent(model, contents, config);

        StringBuilder textBuilder = new StringBuilder();
        List<Map<String, String>> images = new ArrayList<>();
        showFile(textBuilder, images, response);

        String textContent = textBuilder.toString();
        log.info("[ImageGen] 결과 - text길이={}, 이미지수={}", textContent.length(), images.size());
        textContent = mapLoad(userMessage, textContent);
        UserMessage imageUserMessage = UserMessage.builder().text(userMessage).build();
        SaveResultResponse saveResultResponse =  saveResult(conversationId, imageUserMessage, textContent, showChatKey);
        final String finalTextContent = textContent;
        try {
            alertService.sendAlert(memberKey, finalTextContent);
        }catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.info( "saveResultResponse.id{}" ,saveResultResponse.id() );
        saveToVectorStore(ragResult, textContent, saveResultResponse.id());

        if (!images.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("text", textContent);
            result.put("images", images);
            try {
                return new ObjectMapper().writeValueAsString(result);
            } catch (Exception e) {
                log.error("이미지 응답 직렬화 실패", e);
            }
        }
        return textContent;
    }

    private SaveResultResponse saveResult(String conversationId, UserMessage message, String response, Long showChatKey) {
        if (response == null || conversationId == null) {
            SaveResultResponse saveResultResponse = new SaveResultResponse(response, null);
            log.warn("[saveResult] 응답이 null이거나 conversationId가 없습니다.");
            return saveResultResponse;
        }
        chatMemory.add(conversationId, message);
        String cleanResponse = response.replaceAll("\\[\\[MAP_START:.*?:MAP_END\\]\\]", "").trim();
        chatMemory.add(conversationId, new AssistantMessage(cleanResponse));
        GptChat gptChat = GptChat.builder().gptChatContents(response).showChat(showChatRepository.findById(showChatKey).orElse(null)).build();
        GptChat gptChatSaved = gptChatRepository.save(gptChat);
        SaveResultResponse saveResultResponse = new SaveResultResponse(response, gptChatSaved.getGptChatKey());

        log.info("[saveResult] 응답 길이={}", response.length());
        return saveResultResponse;
    }

    private String mapLoad(String userMessage, String response) {
        if (isMapRequest(userMessage)) {
            String location = extractLocationWithAI(userMessage);
            log.info("[search_map] AI 추출 결과={}", location);
            if (location.equals("NONE") || location.isBlank()) {
                // 장소 특정 불가 → 지도 표시 안 함
            } else if (location.startsWith("CURRENT_LOCATION:")) {
                String placeType = location.split(":")[1];
                response = response + "\n[[MAP_START:CURRENT:" + placeType + ":MAP_END]]";
            } else {
                String coords = kakaoMapService.findMap(location);
                log.info("[search_map] 좌표 결과={}", coords);
                if (coords != null) {
                    response = response + "\n[[MAP_START:" + coords + ":MAP_END]]";
                }
            }
        }
        return response;
    }

    private String sendMessage(List<Message> history, UserMessage message, String model, String systemPrompt) {
        List<Message> allMessages = new ArrayList<>(history);
        allMessages.add(message);
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().model(model).temperature(0.7).maxOutputTokens(6024).topP(0.9).topK(100).build();
        return chatClient.prompt().system(systemPrompt).messages(allMessages).options(options).call().content();
    }

    private UserMessage userMessageGet(MyChatDTO dto) {
        if (dto.getFiles() != null && !dto.getFiles().isEmpty()) {
            List<Media> mediaList = dto.getFiles().stream().filter(f -> f.getMimeType() != null).map(f -> new Media(MimeTypeUtils.parseMimeType(f.getMimeType()), new ByteArrayResource(Base64.getDecoder().decode(f.getData())))).toList();
            return UserMessage.builder().text(dto.getMyChatContents()).media(mediaList).build();
        } else {
            return UserMessage.builder().text(dto.getMyChatContents()).build();
        }
    }

    private static @NonNull String getString(String customPrompt, String webResults, String ragResult) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String basePrompt = customPrompt != null && !customPrompt.isBlank() ? customPrompt : """
                당신은 JO-GPT 어시스턴트입니다.
                항상 한국어로 친절하게 답변하세요.
                이전 대화 내역을 기억하고 대화 맥락을 유지하며 답변하세요.
                """;
        String datePrompt = """
                # 시스템 지침: 현재 날짜 및 답변 출처 규칙
                
                ## 1. 현재 날짜 정보
                - 오늘은 **%s** 입니다.
                
                ## 2. 답변 생성 규칙 (필수 준수)
                1.  **최신 정보 우선:** 최신 정보가 필요한 질문은 반드시 **아래 웹 검색 결과**를 최우선으로 사용하여 답변하세요.
                2.  **검색 결과 부재 시:**
                    - 웹 검색 결과가 없거나 불충분한 경우에만 학습 데이터를 기반으로 답변하세요.
                    - 이 경우, 반드시 답변 끝에 **`"정확한 최신 정보는 직접 확인이 필요합니다."`**라는 문구를 토씨 하나 틀리지 말고 포함하세요.
                3.  **정보 정확성:** 답변을 작성하기 전에 제공하는 정보가 정확한지 다시 한번 스스로 검증하세요.
                """.formatted(today);
        return webResults.isBlank() ? ragResult.isBlank() ? basePrompt + datePrompt : ragResult + basePrompt + datePrompt : ragResult.isBlank() ? basePrompt + datePrompt + webResults : basePrompt + datePrompt + webResults + ragResult;
    }

    private void showFile(StringBuilder textBuilder, List<Map<String, String>> images, GenerateContentResponse response) {
        response.candidates().orElse(List.of()).forEach(candidate -> candidate.content().ifPresent(content -> content.parts().orElse(List.of()).forEach(part -> {
            log.info("[ImageGen] part - text={}, inlineData={}", part.text().isPresent(), part.inlineData().isPresent());
            part.text().ifPresent(textBuilder::append);
            part.inlineData().ifPresent(blob -> blob.data().ifPresent(data -> {
                log.info("[ImageGen] 이미지 추출 성공 mimeType={}, size={}bytes", blob.mimeType().orElse("unknown"), data.length);
                Map<String, String> img = new LinkedHashMap<>();
                img.put("mimeType", blob.mimeType().orElse("image/png"));
                img.put("data", Base64.getEncoder().encodeToString(data));
                images.add(img);
            }));
        })));
    }

    private GenerateContentConfig generateContentConfigs(Content systemInstruction) {
        return GenerateContentConfig.builder().systemInstruction(systemInstruction).responseModalities("TEXT", "IMAGE").maxOutputTokens(2048).build();
    }

    private Content systemInstruction(String systemText) {
        return Content.builder().role("system").parts(Part.fromText(systemText)).build();
    }

    private List<Content> buildContents(List<Message> history, List<Part> parts) {
        List<Content> contents = new ArrayList<>();
        for (Message msg : history) {
            String role = (msg instanceof AssistantMessage) ? "model" : "user";
            contents.add(Content.builder().role(role).parts(Part.fromText(msg.getText())).build());
        }
        contents.add(Content.builder().role("user").parts(parts).build());
        return contents;
    }

    private List<Part> buildParts(String userMessage, List<MyChatDTO.FilePartDTO> files) {
        List<Part> parts = new ArrayList<>();
        parts.add(Part.fromText(userMessage));
        if (files != null) {
            files.stream().filter(f -> f.getMimeType() != null && f.getData() != null).forEach(f -> parts.add(Part.fromBytes(Base64.getDecoder().decode(f.getData()), f.getMimeType())));
        }
        return parts;
    }

    // 벡터 db에 저장 로직
    private void saveToVectorStore(String ragResult, String llmAnswer, Long id) {
        log.info("[saveToVectorStore 호출됨] ...");

        if (llmAnswer == null || llmAnswer.isBlank()) {
            log.warn("[saveToVectorStore] llmAnswer가 비어있어 저장 생략");
            return;
        }

        String context = ragResult == null || ragResult.isBlank() ? llmAnswer : "\n\n[RAG 검색 결과]\n" + ragResult + "\n\n[AI 답변]\n" + llmAnswer;
        ragService.saveDocument(context, "chat", "대화", id);
    }


}