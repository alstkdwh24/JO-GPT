# 보안 수정 내역 (2026-10-05)

JO_GPT · MembersSecurity · EntityCom · 인프라(docker compose) · 프론트(web-jogpt-ui)

- **빌드**: `EntityCom`, `MembersSecurity`, `JO_GPT_PROGRAM` 모두 `compileJava` 통과
- **미검증**: 실행 테스트(서버 기동·브라우저·통합 테스트)는 하지 않음 → 맨 아래 [배포 전 체크리스트](#배포-전-체크리스트) 참고

---

## 요약

| # | 문제 | 조치 | 상태 |
|---|---|---|---|
| 1 | `/contents/**` 전체 로그인 없이 접근 가능 | 인증 필수로 변경 (`/contents/notifications`만 공개) | ✅ |
| 2 | 남의 채팅방 조회·삭제·검색 가능 (번호만 바꾸면 됨) | 채팅방 소유자 확인, 아니면 403 | ✅ |
| 3 | 남의 채팅방에 메시지·AI 응답 써 넣기 가능 | `/myContents`, `/gptContents`, 학술검색 2종에 소유자 확인 | ✅ |
| 4 | 채팅 검색 미완성 (프론트 POST ↔ 백엔드 GET 불일치) | 본인 채팅방 부분 검색 구현 (백엔드+프론트) | ✅ |
| 5 | `/contents/crawl` SSRF (내부 ES·Chroma 조회 가능) | API 삭제 | ✅ |
| 6 | `X-Model` 헤더로 아무 모델이나 지정 (요금 폭탄) | 허용목록(`LlmModelPolicy`) 검증, 아니면 400 | ✅ |
| 7 | CORS에 `http://` 주소 + 운영에 `localhost:5173` | `http://` 제거, localhost는 설정값(`spring.cors.dev-origin`)으로 | ✅ |
| 8 | ES(9200)·Chroma(8000)·MySQL(3307) 외부 노출 (ES·Chroma 무인증) | `127.0.0.1` 바인딩 | ✅ |
| 9 | RAG에 사용자 구분 없음 → 남의 대화 요약 열람·프롬프트 혼입 | 저장 시 `memberKey` 기록, 검색 시 본인 것만 | ✅ |
| 10 | 토큰: access 400일, 로그아웃 무효, refresh Redis 대조 없음 | 작업 중이던 리팩터링 완성 (access 30분 / refresh 14일 회전·재사용 감지 / jti 블랙리스트) | ✅ |
| 11 | 로그인 무차별 대입 제한 없음 | 아이디별 15분 5회 실패 시 429 | ✅ |
| 12 | AES 하드코딩 키 + 고정 IV | AES-256-GCM + 무작위 IV (v2), 기존 데이터 하위 호환 | ✅ (환경변수 설정 필요) |

---

## 1. `/contents/**` 인증 필수

**파일**: [JoGptSecurityConfig.java](backend/JO_GPT_PROGRAM/src/main/java/com/example/jo_gpt_program/gpt/config/JoGptSecurityConfig.java)

```java
// 전
.requestMatchers("/contents/**", "/auth/**", "/connect/**").permitAll()

// 후
.requestMatchers("/contents/notifications").permitAll()   // 받은 메시지를 되돌려주기만 함
.requestMatchers("/auth/**", "/connect/**").permitAll()
```

- 로그인 안 하면 401. Gemini 키 무단 호출(`/gptContents` 등), 문서 검색이 막힘
- 프론트(web-jogpt-ui)는 이미 `credentials: 'include'`로 쿠키를 보내고 있어 수정 불필요

## 2·3. 채팅방 소유자 확인

**파일**: [ShowChatService.java](backend/JO_GPT_PROGRAM/src/main/java/com/example/jo_gpt_program/gpt/service/ShowChatService.java), [ChatMysqlService.java](backend/JO_GPT_PROGRAM/src/main/java/com/example/jo_gpt_program/gpt/service/ChatMysqlService.java), [ContentsController.java](backend/JO_GPT_PROGRAM/src/main/java/com/example/jo_gpt_program/gpt/restController/ContentsController.java)

```java
/* 채팅방 조회 + 현재 로그인 사용자가 주인인지 확인 (아니면 403) */
private ShowChat findOwnedShowChat(Long showChatKey) {
    ShowChat showChat = showChatRepository.findShowChatByShowChatKey(showChatKey)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ...));
    Long memberKey = getMemberKeyFromContext();
    if (showChat.getMembers() == null || !memberKey.equals(showChat.getMembers().getMemberKey())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    return showChat;
}

/* body로 받은 showChatKey 검증 (null이면 통과 — 새 대화) */
public void checkOwner(Long showChatKey) { ... }
```

| API | 적용 |
|---|---|
| `GET /chatRoom/{key}/messages` | `getChatMessages()` → `findOwnedShowChat` |
| `DELETE /chatRoom/{key}` | `deleteChat()` → `findOwnedShowChat` (안 쓰던 `authHeader` 파라미터 제거) |
| `POST /myContents` | `ChatMysqlService.myChat()`에서 주인 아니면 403 |
| `POST /gptContents`, `/getScholarContents`, `/getRagScholarContents` | 컨트롤러에서 **LLM 호출 전에** `checkOwner()` — 남의 방 대화기록(chatMemory)이 프롬프트로 새는 것도 차단 |

**채팅방 삭제 시 함께 정리** (지운 대화가 검색·프롬프트에 남지 않게)
- 그 방의 RAG 문서 삭제: `ragService.deleteByEntityIds(gptChatKeys)`
- 대화 메모리 삭제: `chatMemory.clear(showChatKey)`

## 4. 채팅 검색 구현

**백엔드**: `POST /contents/searchChatting`, body `{ "search": "검색어" }` → `Set<ShowChatDTO>` (채팅 목록과 같은 형식, 최신순)

```java
@Transactional
public Set<ShowChatDTO> searchMyChats(String keyword) {
    Members members = getMemberFromContext();
    String q = keyword == null ? "" : keyword.trim().toLowerCase();
    Set<ShowChat> matched = showChatRepository.findByMembers(members).stream()
            .filter(chat -> q.isEmpty() || containsKeyword(chat, q))   // 내 질문 또는 AI 응답에 포함
            .collect(Collectors.toSet());
    return toShowChatDTOs(matched);
}
```

- 내용이 AES 암호화라 DB `LIKE` 불가 → 본인 채팅방만 불러와 복호화 후 메모리에서 필터
- 검색어(=대화 내용 일부)가 URL·nginx 로그에 남지 않도록 GET 쿼리가 아니라 POST body
- 정확히 일치만 찾던 기존 `findShowRoom()` / `GptChatRepository.findByGptChatContents()` 삭제

**프론트**: [ChattingList.jsx](frontend/web-jogpt-ui/src/components/ChattingList.jsx)
- 입력 멈춘 뒤 300ms 디바운스 → 결과로 목록 교체, 빈 검색어면 전체 목록
- 늦게 온 이전 응답이 최신 결과를 덮어쓰지 않게 취소 플래그
- 첫 렌더링에는 검색 호출 생략 (목록 API와 중복 방지)

## 5. `/crawl` 삭제 (SSRF)

**파일**: [ContentsController.java](backend/JO_GPT_PROGRAM/src/main/java/com/example/jo_gpt_program/gpt/restController/ContentsController.java)

- `POST /contents/crawl` 및 jsoup·IOException import 삭제
- ⚠️ `frontend/web-jogpt-ui/src/components/DocumentUpload.jsx`는 이 API와 이미 없는 `/saveDocument`를 호출하는 **어디서도 import하지 않는 컴포넌트**. 삭제 권한이 거부돼 남겨 둠 → 직접 삭제 권장

## 6. X-Model 허용목록

**파일**: [LlmModelPolicy.java](backend/JO_GPT_PROGRAM/src/main/java/com/example/jo_gpt_program/gpt/config/LlmModelPolicy.java) (신규)

```java
private static final String DEFAULT_MODEL = "gemini-2.0-flash";   // X-Model 없을 때 (기존 기본값 유지)
private static final Set<String> ALLOWED_MODELS = Set.of(
        DEFAULT_MODEL,
        "gemini-3.1-flash-image-preview",
        "gemini-3.5-flash",
        "gemini-3-flash-preview"          // web-jogpt-ui ChatHome.jsx MODEL_OPTIONS와 맞출 것
);
```

- `/gptContents`, `/chatRoom/first`, `/getScholarContents`, `/getRagScholarContents` 4곳 적용, 허용 안 된 값은 400
- PC-JO-GPT-UI의 `gpt-4.5` 옵션은 원래도 Gemini API에서 동작 안 하던 값 → 이제 400

## 7. CORS

| 파일 | 변경 |
|---|---|
| [JoGptSecurityConfig.java](backend/JO_GPT_PROGRAM/src/main/java/com/example/jo_gpt_program/gpt/config/JoGptSecurityConfig.java) | `http://agentcloudllm.me` 제거, `http://localhost:5173` → `devOrigin` |
| [SecurityConfig.java](backend/MembersSecurity/src/main/java/com/example/memberssecurity/security/config/security/SecurityConfig.java) | `http://agentcloudllm.me:5173` → `https://agentcloudllm.me:5173` |

```java
@Value("${spring.cors.dev-origin:}")   // 운영: 비워 둠 / 로컬: http://localhost:5173
private String devOrigin;
```

> 로컬 개발 시 JO_GPT 설정에 `spring.cors.dev-origin=http://localhost:5173` 추가 필요

## 8. DB·검색엔진 포트 외부 노출 차단

**파일**: `docker/mysql_container.yml`, `docker-compose.yml`

```yaml
mysql:          - "127.0.0.1:3307:3306"   # 전: "3307:3306"
elasticsearch:  - "127.0.0.1:9200:9200"   # 전: "9200:9200"  (xpack.security=false)
chroma:         - "127.0.0.1:8000:8000"   # 전: "8000:8000"  (무인증)
redis(root):    - "127.0.0.1:6379:6379"   # 전: "6379:6379"
```

> 반영하려면 컨테이너 재생성 필요: `docker compose -f docker/mysql_container.yml up -d`

## 9. RAG 사용자 분리

**파일**: [RagService.java](backend/JO_GPT_PROGRAM/src/main/java/com/example/jo_gpt_program/gpt/service/RagService.java), [GeminiService.java](backend/JO_GPT_PROGRAM/src/main/java/com/example/jo_gpt_program/gpt/service/GeminiService.java), [ScholarSearchService.java](backend/JO_GPT_PROGRAM/src/main/java/com/example/jo_gpt_program/gpt/service/ScholarSearchService.java), [ContentsController.java](backend/JO_GPT_PROGRAM/src/main/java/com/example/jo_gpt_program/gpt/restController/ContentsController.java)

```java
// 저장: metadata에 주인 기록 (memberKey 없으면 저장 안 함)
metadata.put("memberKey", String.valueOf(memberKey));

// 검색: 본인 문서만
SearchRequest.builder().query(query).topK(5).filterExpression(memberFilter(memberKey)).build();

public static Filter.Expression memberFilter(Long memberKey) {
    return new FilterExpressionBuilder().eq("memberKey", String.valueOf(memberKey)).build();
}
```

| 호출 위치 | 변경 |
|---|---|
| `GeminiService.sendGeminiAI` | `findDocument(query, memberKey)`, `saveToVectorStore(..., memberKey)` |
| `ScholarSearchService.sendWithRagAndScholar` | `memberKey` 파라미터 추가 + 필터 |
| `POST /contents/documents/search` | `findDocument(query, getMemberKey())` |

> 기존에 저장된 문서는 `memberKey`가 없어 **아무 검색에도 안 걸림** (유출은 막히지만 데이터는 남아 있음). Chroma에서 `source == "chat"`인 기존 문서 삭제 권장

## 10. 토큰 관리 (MembersSecurity)

MembersSecurity에 **사용자가 진행 중이던 리팩터링**(컴파일 에러 38개 상태)이 있었고, 그 설계를 그대로 따라 호출부를 맞춰 완성함.

**설계 (기존 작업분)**: [JWTUtils.java](backend/MembersSecurity/src/main/java/com/example/memberssecurity/security/config/jwt/JWTUtils.java), [RefreshTokenService.java](backend/MembersSecurity/src/main/java/com/example/memberssecurity/member/service/RefreshTokenService.java), [TokenCookies.java](backend/MembersSecurity/src/main/java/com/example/memberssecurity/security/config/jwt/TokenCookies.java)
- access 30분 / refresh 14일, 모든 토큰에 `jti`·`type` 클레임
- refresh는 Redis에 저장, 사용할 때마다 회전 (Lua 스크립트로 원자적), 10초 유예
- 폐기된 refresh 재사용 감지 시 그 회원 세션 전체 폐기
- 쿠키: HttpOnly · Secure · SameSite=Lax

**이번에 고친 버그**

```java
// JWTUtils.blacklist — 블랙리스트가 1000배 빨리 풀림 (30분 → 약 1.8초) = 로그아웃 무효
TimeUnit.MICROSECONDS  →  TimeUnit.MILLISECONDS

// 컴파일 에러: 필드 타입 불일치
RedisTemplate<String, Object>  →  RedisTemplate<String, String>
```

**새로 맞춘 호출부**

| 파일 | 변경 |
|---|---|
| [JWTFilter.java](backend/MembersSecurity/src/main/java/com/example/memberssecurity/security/config/filter/JWTFilter.java) | `parse(token, TYPE_ACCESS)` → 블랙리스트 확인. 만료 시 `refreshTokenService.rotate()`(Redis 대조)로만 재발급. `/auth/validate`는 필터 제외(이중 회전 방지) |
| [AuthValidateController.java](backend/MembersSecurity/src/main/java/com/example/memberssecurity/member/restController/AuthValidateController.java) | 같은 방식. 예전엔 refresh **서명만 보고** 400일짜리 access를 재발급했음 |
| [LoginFilter.java](backend/MembersSecurity/src/main/java/com/example/memberssecurity/security/config/filter/LoginFilter.java), [MemberController.java](backend/MembersSecurity/src/main/java/com/example/memberssecurity/member/restController/MemberController.java) | 로그인 시 `refreshTokenService.issue()` + `tokenCookies.write()` |
| [OAuth2LoginSuccessHandler.java](backend/MembersSecurity/src/main/java/com/example/memberssecurity/security/config/handler/OAuth2LoginSuccessHandler.java) | 같음. `expiration_time` 의존 제거, **토큰을 debug 로그에 찍던 것 제거** |
| [RefreshController.java](backend/MembersSecurity/src/main/java/com/example/memberssecurity/member/restController/RefreshController.java) | `rotate()` 사용 |
| `SecurityConfig` 로그아웃 핸들러, `MemberController.logout` | `refreshTokenService.revoke()` (신규) + `tokenCookies.clear()` |

```java
/* 로그아웃: access는 jti 블랙리스트(남은 수명 동안), refresh는 Redis에서 삭제 → 둘 다 즉시 무효 */
public void revoke(String accessToken, String refreshToken) { ... }
```

**JO_GPT 위임 필터 수정** — [JwtDelegateFilter](backend/JO_GPT_PROGRAM/src/main/java/com/example/jo_gpt_program/gpt/config/filter/JwtDelegateFilter.java)

```java
// 전: Set-Cookie 1개만 전달 → 회전 시 새 refresh가 빠져 10초 뒤 "재사용 감지"로 세션 전체 폐기
String setCookie = conn.getHeaderField("Set-Cookie");
response.setHeader("Set-Cookie", setCookie);

// 후: 전부 전달
List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
setCookies.forEach(c -> response.addHeader("Set-Cookie", c));
```

## 11. 로그인 rate limit

**파일**: [LoginAttemptService.java](backend/MembersSecurity/src/main/java/com/example/memberssecurity/member/service/LoginAttemptService.java) (신규)

- Redis `LOGIN_FAIL:{memberId}` 카운터, **15분 안에 5회 실패 → 15분 차단**
- 차단 중이면 비밀번호 확인 자체를 안 하고 `429 Too Many Requests` + `Retry-After`
- 성공 시 카운터 삭제
- 적용: `LoginFilter`(`/api/auth/login`), `MemberController`(`/login/auth/login`)
- IP 기준은 쓰지 않음 (nginx 뒤라 `remoteAddr`가 전부 같고, `X-Forwarded-For`는 위조 가능)

## 12. AES 컬럼 암호화

**파일**: [AesEncryptConverter.java](backend/EntityCom/src/main/java/com/example/entitycom/converter/AesEncryptConverter.java)
**대상 컬럼**: 채팅 내용(`gpt_chat`, `my_chat`), 닉네임, 연동 계정 access/refresh 토큰

| | 전 | 후 (v2) |
|---|---|---|
| 알고리즘 | AES-CBC | AES-256-GCM (위변조 검증) |
| IV | 고정 (코드 기본값) | 값마다 무작위 12바이트 |
| 키 | 코드에 하드코딩된 기본값 (환경변수 미설정 상태였음) | 환경변수 `CHAT_ENCRYPT_KEY_V2` |
| 저장 형식 | `base64(암호문)` | `v2:` + `base64(IV ‖ 암호문+태그)` |

- 기존 데이터: 예전 키로 그대로 복호화됨 → 다시 저장될 때 v2로 바뀜
- `CHAT_ENCRYPT_KEY_V2` 미설정 시: 서비스가 멈추지 않도록 **경고 로그 + 예전 방식으로 저장** (= 설정 전까지 강화 효과 없음)
- v2는 같은 평문도 매번 다른 암호문 → 암호화 컬럼으로 `findByXxx` 같은 = 조회 불가 (현재 그런 쿼리 없음 확인)

---

## 배포 전 체크리스트

- [ ] **`CHAT_ENCRYPT_KEY_V2` 환경변수 설정** (모든 백엔드 서비스 동일 값): `openssl rand -base64 32`
- [ ] **docker compose 재생성**으로 포트 바인딩 반영 + 운영 서버 방화벽에서 3307·9200·8000 외부 차단 확인
- [ ] 로컬 개발 설정에 `spring.cors.dev-origin=http://localhost:5173` 추가 (JO_GPT)
- [ ] MembersSecurity 리팩터링 **커밋** (지금은 미커밋 상태)
- [ ] 배포 후 기존 로그인 사용자는 **재로그인 필요** (예전 400일 토큰은 `jti`·회전 정보가 없어 refresh 회전 불가)
- [ ] Chroma의 기존 `source == "chat"` 문서(주인 없음) 삭제
- [ ] 실행 확인: 로그인 → 30분 후 자동 재발급 → 로그아웃 후 이전 토큰 401 → 로그인 5회 실패 시 429
- [ ] 실행 확인: 다른 계정으로 남의 채팅방 조회·삭제·메시지 쓰기 → 403, 채팅 검색 동작
- [ ] (선택) `DocumentUpload.jsx` 삭제, 프론트 로그인 화면에서 429 메시지 표시

## 남은 위험 (이번에 안 고침)

| 항목 | 내용 |
|---|---|
| Electron 로그인 | `jo-gpt://auth?token=...&refreshtoken=...` 처럼 **토큰이 딥링크 URL에 노출**. 일회용 코드 교환 방식으로 바꾸는 게 정석 (Electron 앱 수정 필요) |
| Redis 장애 시 | 블랙리스트·refresh·rate limit이 모두 Redis 의존 → Redis가 죽으면 로그인·재발급 실패 |
| 채팅 검색 성능 | 채팅방이 많아지면 메모리 필터 + N+1 로딩으로 느려질 수 있음 |
