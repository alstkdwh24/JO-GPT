# JO-GPT | AI Universal Controller

Google Gemini API를 활용한 멀티모달 AI 채팅 서비스입니다.  
텍스트 답변과 이미지 생성, 이전 대화 맥락 유지, RAG 기반 검색 등을 지원합니다.

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| AI 채팅 | Google Gemini 모델을 활용한 텍스트 답변 |
| 이미지 생성 | `gemini-3.1-flash-image-preview` 모델로 텍스트 → 이미지 생성 |
| 멀티턴 대화 | 이전 대화 맥락을 AI에 전달하여 연속 대화 지원 |
| 채팅방 관리 | 채팅방 생성 / 목록 조회 / 삭제 / 히스토리 불러오기 |
| 커스텀 프롬프트 | 사용자별 시스템 프롬프트 저장 및 활성화 |
| RAG | ChromaDB 벡터 DB 기반 문서 검색 후 AI 답변 |
| 학술 검색 | 논문 검색 결과를 AI 답변에 반영 |
| Google 연동 | Gmail(조회/발송), Calendar(조회/등록), YouTube(요약) 연동 |
| 소셜 로그인 | Google / Naver / Kakao / Github OAuth2 |
| JWT 인증 | 토큰 기반 Stateless 인증 |
| SSE 알림 | AI 답변 완료 시 실시간 알림 |

---

## 기술 스택

### Backend
- Java 17
- Spring Boot 3.5.13
- Spring AI 1.1.5 (Google GenAI)
- Google GenAI SDK 1.37.0 (이미지 생성용 직접 호출)
- Spring Security + JWT
- OAuth2 (Google, Naver, Kakao, Github)
- JPA / Hibernate
- TSID (분산 환경 PK 생성)

### Database
- MySQL 8 — 채팅 데이터, 회원 정보
- Redis — 토큰 관리
- ChromaDB — 벡터 DB (RAG용 임베딩 데이터 저장)

### Search & RAG Tools
- Tavily — 학술 및 웹 실시간 검색 API
- HuggingFace/Spring AI Embedding — 텍스트 벡터화

### Frontend
- React 18 (Vite)
- marked + DOMPurify (Markdown 렌더링)
- SSE (Server-Sent Events) — 실시간 응답 스트리밍

### Infra
- Docker (MySQL, ChromaDB 컨테이너)
- Python (YouTube 자막 추출용: `youtube-transcript-api`)

---

## 프로젝트 구조

```
AI_Universal_Controller/
├── backend/
│   ├── EntityCom/          # 공통 엔티티 및 DTO 모듈
│   ├── MembersSecurity/    # 인증/인가 모듈 (JWT, OAuth2, 멤버 관리)
│   └── JO_GPT_PROGRAM/     # AI 핵심 서비스 (Gemini, RAG, Google API, SSE)
└── frontend/
    └── web-jogpt-ui/       # React 웹 클라이언트
```

---

## 핵심 아키텍처 및 특이사항

**1. 하이브리드 지식 활용 (RAG + Scholar Search)**  
단순한 AI 답변을 넘어, 내부 문서와 외부 최신 지식을 결합합니다.
- **RAG (Retrieval-Augmented Generation)**: 사용자가 업로드한 문서나 대화 맥락을 3줄 요약하여 ChromaDB에 저장합니다. 질문 시 유사도 검색(Similarity Search)을 통해 관련 컨텍스트를 추출하여 AI에게 전달합니다.
- **학술 검색 (Scholar Search)**: Tavily API를 연동하여 웹상의 최신 논문이나 기술 자료를 검색합니다. RAG와 결합하여 "내 문서 + 최신 학술 정보"를 동시에 참조하는 답변 생성이 가능합니다.

**2. Spring AI의 이미지 생성 제한 우회**  
Spring AI 1.1.5의 `GoogleGenAiChatOptions` 제약을 해결하기 위해, 이미지 생성 모델(`gemini-*-image-*`)은 Google GenAI SDK를 직접 호출하도록 구현했습니다. 응답에서 텍스트와 Base64 이미지를 분리하여 처리합니다.

**3. Google AI 에이전트 워크플로우**  
사용자의 의도(Intent)를 파악하여 적절한 Google 서비스를 호출하는 에이전트 루프를 가집니다.
- **의도 분류**: LLM이 메시지를 분석하여 `MAIL_SEND`, `CALENDAR_VIEW` 등으로 분류합니다.
- **서비스 연동**: OAuth2 Access Token을 사용하여 Gmail, Calendar API를 대행 호출합니다.
- **YouTube 요약**: Python 스크립트로 자막을 추출한 후, 대량의 텍스트를 LLM이 요약하여 전달합니다.
- **실시간 피드백**: SSE(Server-Sent Events)를 통해 긴 생성 시간 동안 사용자에게 실시간 상태를 알립니다.

---

## 실행 방법

### 사전 요구사항
- Java 17
- Node.js 18+
- Docker

### 1. 인프라 실행
```bash
# MySQL + ChromaDB
docker-compose -f backend/JO_GPT_PROGRAM/mysql_container.yml up -d
```

### 2. 환경 변수 설정
`application.yml`에서 아래 변수를 환경 변수 또는 시크릿으로 설정합니다.

```
gemini-key          # Google Gemini API 키
joGptPw             # MySQL / Redis 비밀번호
joGptSecret         # JWT 시크릿
expiration_time     # JWT 만료 시간
google-client-id / google-client-secret
kakao-client-id / kakao-secret
naver-client-id / naver-client-secret
github-client-id / github-client-secret
```

### 3. 백엔드 실행 순서
```
1. EntityCom 빌드
2. MembersSecurity 실행
3. JO_GPT_PROGRAM 실행 (포트 8082)
```

### 4. 프론트엔드 실행
```bash
cd frontend/web-jogpt-ui
npm install
npm run dev   # 포트 5173
```

---

## API 주요 엔드포인트

| Method | URL | 설명 |
|--------|-----|------|
| POST | `/contents/chatRoom` | 채팅방 생성 |
| GET | `/contents/chattingList` | 채팅방 목록 조회 |
| DELETE | `/contents/chatRoom/{key}` | 채팅방 삭제 |
| GET | `/contents/chatRoom/{key}/messages` | 대화 내역 조회 |
| POST | `/contents/myContents` | 유저 메시지 저장 |
| POST | `/contents/gptContents` | AI 답변 생성 (모델 선택 가능) |
| GET | `/contents/myPrompts` | 프롬프트 목록 조회 |
| POST | `/contents/myPrompts` | 프롬프트 저장 |
| PATCH | `/contents/myPrompts/{key}/activate` | 프롬프트 활성화 |
| POST | `/contents/scholarContents` | 학술 검색 기반 답변 생성 |
| POST | `/contents/ragScholarContents` | RAG + 학술 검색 하이브리드 답변 생성 |
| POST | `/contents/ragContents` | RAG 기반 답변 생성 |
| POST | `/contents/saveDocument` | RAG용 문서 저장 (요약 포함) |
| GET | `/alert/connect` | SSE 연결 수립 |
| GET | `/auth/connected-accounts` | 소셜 계정 연동 현황 조회 |
