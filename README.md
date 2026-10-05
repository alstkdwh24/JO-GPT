# JO-GPT

**Gemini 기반 AI 채팅 서비스 (백엔드 · 인증 서버 · 웹)**

Google Gemini API를 활용한 멀티모달 AI 채팅 서비스입니다.
텍스트 답변과 이미지 생성, 이전 대화 맥락 유지, 사용자별 RAG 검색, Google 서비스 연동을 지원하며,
인증은 별도 인증 서버(MembersSecurity)로 분리해 다른 서비스가 위임 검증하도록 구성했습니다.

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| AI 채팅 | Google Gemini 모델을 활용한 텍스트 답변 (허용된 모델만 선택 가능) |
| 이미지 생성 | `gemini-3.1-flash-image-preview` 모델로 텍스트 → 이미지 생성 |
| 멀티턴 대화 | 이전 대화 맥락을 AI에 전달하여 연속 대화 지원 |
| 채팅방 관리 | 채팅방 생성 / 목록 조회 / 삭제 / 대화 내역 조회 (본인 채팅방만 접근) |
| 채팅 검색 | 내 질문·AI 답변에 검색어가 들어간 채팅방 찾기 |
| 커스텀 프롬프트 | 요청별 시스템 프롬프트 적용 |
| RAG | AI 답변을 요약해 사용자별로 ChromaDB에 저장하고, 질문 시 **본인 문서만** 유사도 검색 |
| 학술 검색 | Tavily로 찾은 논문·웹 자료를 AI 답변에 반영 |
| Google 연동 | Gmail(조회/발송), Calendar(조회/등록), YouTube(자막 요약) 연동 |
| 소셜 로그인 | Google / Naver / Kakao / Github OAuth2 |
| 인증 | Access 30분 / Refresh 14일, Redis 기반 토큰 회전·재사용 감지, jti 블랙리스트 로그아웃 |
| SSE 알림 | AI 답변 완료 시 실시간 알림 |

---

## 기술 스택

### Backend
- Java 17
- Spring Boot 3.5.13
- Spring AI 1.1.5 (Google GenAI)
- Google GenAI SDK 1.37.0 (이미지 생성용 직접 호출)
- Spring Security + JWT (JJWT)
- OAuth2 (Google, Naver, Kakao, Github)
- JPA / Hibernate
- TSID (분산 환경 PK 생성)

### Database
- MySQL 8 — 채팅 데이터, 회원 정보 (채팅 내용 등은 AES-256-GCM으로 컬럼 암호화)
- Redis — Refresh Token, 블랙리스트, OAuth2 인가 요청, 로그인 실패 횟수
- ChromaDB — 벡터 DB (RAG용 임베딩 데이터 저장)

### Search & RAG Tools
- Tavily — 학술 및 웹 실시간 검색 API
- Gemini Embedding — 텍스트 벡터화

### Frontend
- React 18 (Vite)
- marked + DOMPurify (Markdown 렌더링)
- SSE (Server-Sent Events) — 실시간 알림

### Infra
- Docker (MySQL, Redis, ChromaDB 컨테이너)
- Python (YouTube 자막 추출용: `youtube-transcript-api`)

---

## 프로젝트 구조

```
JO-GPT/
├── backend/
│   ├── EntityCom/          # 공통 엔티티 및 DTO 모듈 (컬럼 암호화 컨버터 포함)
│   ├── MembersSecurity/    # 인증 서버 (JWT, OAuth2, 회원 관리, 토큰 검증 API)
│   └── JO_GPT_PROGRAM/     # AI 핵심 서비스 (Gemini, RAG, Google API, SSE)
├── frontend/
│   └── web-jogpt-ui/       # React 웹 클라이언트
└── docs/                   # 보안 강화 설계 문서
```

---

## 핵심 아키텍처 및 특이사항

**1. 인증 서버 분리와 위임 검증**
`MembersSecurity`가 로그인·토큰 발급·검증을 전담하고, `JO_GPT_PROGRAM`은 요청마다 `JwtDelegateFilter`로 `/auth/validate`를 호출해 인증을 위임합니다.
- Access Token 만료 시 Redis에 저장된 Refresh Token과 대조해 **회전**(Lua 스크립트로 원자적 처리)하고, 이미 폐기된 Refresh Token이 다시 오면 탈취로 보고 해당 회원의 세션 전체를 폐기합니다.
- 로그아웃 시 Access Token의 jti를 남은 수명만큼 Redis 블랙리스트에 등록해 즉시 무효화합니다.
- 토큰은 HttpOnly · Secure · SameSite=Lax 쿠키로만 전달하고, 응답 헤더로는 노출하지 않습니다.

**2. 하이브리드 지식 활용 (RAG + Scholar Search)**
- **RAG**: AI 답변을 3줄로 요약해 작성자(memberKey) 정보와 함께 ChromaDB에 저장합니다. 질문 시 본인 문서만 대상으로 유사도 검색을 해서, 다른 사용자의 대화가 섞이지 않습니다. 채팅방을 삭제하면 해당 문서도 함께 삭제됩니다.
- **학술 검색**: Tavily API로 최신 논문·기술 자료를 검색하고, RAG와 결합해 "내 대화 맥락 + 최신 자료"를 함께 참조하는 답변을 생성합니다.

**3. Spring AI의 이미지 생성 제한 우회**
Spring AI 1.1.5의 `GoogleGenAiChatOptions` 제약을 해결하기 위해, 이미지 생성 모델(`gemini-*-image-*`)은 Google GenAI SDK를 직접 호출하도록 구현했습니다. 응답에서 텍스트와 Base64 이미지를 분리하여 처리합니다.

**4. Google AI 에이전트 워크플로우**
사용자의 의도(Intent)를 파악하여 적절한 Google 서비스를 호출합니다.
- **의도 분류**: LLM이 메시지를 분석하여 `MAIL_SEND`, `CALENDAR_VIEW` 등으로 분류합니다.
- **서비스 연동**: OAuth2 Access Token을 사용하여 Gmail, Calendar API를 대행 호출합니다.
- **YouTube 요약**: Python 스크립트로 자막을 추출한 후, LLM이 요약합니다.
- **실시간 피드백**: SSE로 답변 완료를 알립니다.

---

## 보안

직접 보안 점검을 진행해 발견한 문제와 수정 내역을 문서로 정리했습니다.

- [SECURITY_CHANGES_2026-10-05.md](SECURITY_CHANGES_2026-10-05.md) — 수정 내역 (IDOR 차단, RAG 사용자 격리, SSRF 제거, 모델 허용목록, 토큰 관리, 로그인 실패 제한, AES-GCM 등)
- [SECURITY_ASSESSMENT_2026-10-05.md](SECURITY_ASSESSMENT_2026-10-05.md) — 보안 평가와 남은 위험
- [docs/SECURITY_HARDENING_H1-H7.md](docs/SECURITY_HARDENING_H1-H7.md) — 인증 구조 강화 설계

---

## 실행 방법

### 사전 요구사항
- Java 17
- Node.js 18+
- Docker

### 1. 인프라 실행
MySQL 8, Redis 7, ChromaDB 1.0을 Docker로 실행합니다. (로컬 compose 파일과 `.env`는 비밀값이 들어 있어 저장소에 포함하지 않습니다. 포트는 외부에 노출하지 않도록 `127.0.0.1`로 바인딩하는 것을 권장합니다.)

### 2. 환경 변수 설정
설정 파일(`application*.yml`)은 저장소에 포함하지 않습니다. 아래 값을 환경 변수 또는 시크릿으로 설정합니다.

```
gemini-key                       # Google Gemini API 키
joGptPw                          # MySQL / Redis 비밀번호
joGptSecret                      # JWT 서명 키 (32바이트 이상)
CHAT_ENCRYPT_KEY_V2              # 컬럼 암호화 키 (base64 32바이트, openssl rand -base64 32)
google-client-id / google-client-secret
kakao-client-id / kakao-secret
naver-client-id / naver-client-secret
github-client-id / github-client-secret
spring.cors.dev-origin           # 로컬 개발 시에만 http://localhost:5173
```

### 3. 백엔드 실행 순서
```
1. EntityCom 빌드
2. MembersSecurity 실행
3. JO_GPT_PROGRAM 실행
```

### 4. 프론트엔드 실행
```bash
cd frontend/web-jogpt-ui
npm install
npm run dev   # 포트 5173
```

---

## API 주요 엔드포인트

### JO_GPT_PROGRAM (로그인 필요, `/contents/notifications` 제외)

| Method | URL | 설명 |
|--------|-----|------|
| POST | `/contents/chatRoom/first` | 채팅방 생성 + 첫 AI 답변 |
| GET | `/contents/chattingList` | 내 채팅방 목록 조회 |
| GET | `/contents/chatRoom/{key}/messages` | 대화 내역 조회 (본인 채팅방만) |
| DELETE | `/contents/chatRoom/{key}` | 채팅방 삭제 (본인 채팅방만, RAG 문서 함께 삭제) |
| POST | `/contents/myContents` | 사용자 메시지 저장 |
| POST | `/contents/gptContents` | AI 답변 생성 (`X-Model` 헤더로 허용된 모델 선택) |
| POST | `/contents/getScholarContents` | 학술 검색 기반 답변 생성 |
| POST | `/contents/getRagScholarContents` | RAG + 학술 검색 하이브리드 답변 생성 |
| POST | `/contents/documents/search` | 내 RAG 문서 검색 |
| POST | `/contents/searchChatting` | 내 채팅 검색 (`{"search": "..."}`) |
| POST | `/contents/notifications` | SSE 알림 |
| GET | `/alert/connects` | SSE 연결 수립 |

### MembersSecurity (인증 서버)

| Method | URL | 설명 |
|--------|-----|------|
| POST | `/login/signUp` | 회원가입 |
| POST | `/login/auth/login` | 로그인 (15분 5회 실패 시 429) |
| GET | `/login/myInfo` | 내 정보 조회 |
| PUT | `/login/nickname` | 닉네임 변경 |
| GET | `/login/logout` | 로그아웃 (토큰 즉시 무효화) |
| POST | `/refreshToken/login/refresh` | Refresh Token 회전 |
| GET | `/auth/validate` | 다른 서비스용 토큰 검증 API |
| GET | `/oauth2/authorization/{provider}` | 소셜 로그인 시작 |
