# JO-GPT UI (Electron)

JO-GPT 프로젝트의 데스크탑 프론트엔드 어플리케이션입니다. Electron을 사용하여 구현되었으며, 피그마(Figma) 디자인 시스템을 기반으로 한 브랜드 컬러와 UI 레이아웃을 포함하고 있습니다.

## 🎨 Design Reference
- **Figma File ID**: `ndxUXgCCR6jz97nyZsoeeE`
- **Main Colors**:
  - Sidebar Background: `#DDEBFF`
  - Content Background: `#F6FAFF`
  - Highlight/Text: `black` (#000000)
  - Sidebar Add Icon: `black`

## 🚀 Getting Started

### Prerequisites
- Node.js (Latest LTS recommended)
- npm

### Installation
```bash
# 디렉토리 이동
cd frontend/PC-JO-GPT-UI

# 의존성 설치
npm install
```

### Running the App
```bash
# 개발 모드 실행
npm start
```

## 📁 Project Structure
- `index.html`: 메인 레이아웃 및 UI 구조
- `index.js`: Electron 메인 프로세스 설정 (창 크기, 웹 환경 설정)
- `css/`: 스타일시트 (브랜드 컬러 및 반응형 레이아웃 포함)
- `js/`: 인터랙션 및 서버 연동 스크립트
- `image/`: UI에 사용되는 로고 및 아이콘 리소스

## 🛠 Features
- **Sidebar**: 60px 고정 너비, 주요 메뉴(메뉴, 추가, 채팅, 설정) 포함
- **Main Content**: 반응형 입력창(Textarea 높이 자동 조절) 및 모델 선택 기능
- **Login Modal**: 카카오, 네이버, 구글 등 소셜 로그인 지원을 위한 독립형 모달 UI
- **Responsive Design**: 화면 너비에 따른 레이아웃 최적화 (670px 미만 대응)
