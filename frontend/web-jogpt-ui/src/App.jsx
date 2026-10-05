import { useEffect, useState } from 'react';
import AlertModal from './components/AlertModal';
import ChatHome from './components/ChatHome';
import ChattingList from './components/ChattingList';
import LoginModal from './components/LoginModal';
import LoginView from './components/LoginView';
import NicknameSetupModal from './components/NicknameSetupModal';
import SignupModal from './components/SignupModal';
import SettingsView from './components/SettingsView';
import SideBar from './components/SideBar';
import TopBar from './components/TopBar';
import CONFIG from './config/config';

import './styles/alertModal.css';
import './styles/chatTing.css';
import './styles/chattingHome.css';
import './styles/GPT-Home.css';
import './styles/loginView.css';
import './styles/menuBar.css';
import './styles/modelJoin.css';
import './styles/settingsView.css';
import './styles/toastMessage.css';


export default function App() {
    // 사용자 인증 관련
    // user - 현재 로그인한 사용자 정보. null이면 비로그인 상태
    // showLogin - 로그인 모달 / 팝업 표시 여부
    // showLoginview -  로그인 전용 뷰 표시 여부, showLogin과 역할이 겹칠수 있음

    //소셜 로그인 닉네임 설정 관련
    // showNicknameSetup - 닉네임 설정 UI 표시 여부
    // socialNickname - 소셜 로그인 후 받아온 닉네임 (닉네임 설정 UI에서 초기값으로 사용)
    const [user, setUser] = useState(null);
    const [showLogin, setShowLogin] = useState(false);
    const [toast, setToast] = useState('');
    const [sidebarActive, setSidebarActive] = useState(false);
    const [currentView, setCurrentView] = useState('home');
    const [showAlert, setShowAlert] = useState(false);
    const [showLoginView, setShowLoginView] = useState(false);
    const [selectedChatKey, setSelectedChatKey] = useState(null);
    const [showNicknameSetup, setShowNicknameSetup] = useState(false);
    const [socialNickname, setSocialNickname] = useState('');
    const [pendingNicknameSetup, setPendingNicknameSetup] = useState(false);
    const [notifications, setNotifications] = useState([]); // 알림 목록
    const [showSignup, setShowSignup] = useState(false);
    const [chatKey, setChatKey] = useState(0);


    // 알림 추가 함수
    const handleNotification = (message, chatKey) => {
        setNotifications(prev => [...prev, {
            id: Date.now(),
            message,
            chatKey,
            time: new Date()
        }]);
    };

    /* 모바일 → 화면 전환 / PC → 기존 모달 */
    const handleLoginClick = () => {
        if (window.innerWidth <= 768) {
            setShowLoginView(true);
        } else {
            setShowLogin(true);
        }
    };

    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search);
        // needsNickname은 UrlParameter로 받아도 무관 (민감정보 아님)
        if (urlParams.get('needsNickname') === 'true') {
            setSocialNickname(urlParams.get('socialNickname') || '');
            setPendingNicknameSetup(true);

        }
        if (urlParams.has('error')) {
            setShowLogin(true);
        }
        // ✅ 구글 연동 완료 시 설정 화면 자동으로 열기
        if (urlParams.get('connected') === 'google') {
            setCurrentView('settings');
        }
        window.history.replaceState({}, '', '/'); // URL에서 쿼리 제거

        fetchMyInfo();
    }, []);

    // fetchMyInfo로 user가 세팅된 후 닉네임 모달 표시
    useEffect(() => {
        if (user && pendingNicknameSetup) {
            setShowNicknameSetup(true);
            setPendingNicknameSetup(false);
        }
    }, [user, pendingNicknameSetup]);

    // ✅ SSE 연결 - user 바뀔 때만 재연결 (의존성 배열에 user 추가!)
    useEffect(() => {
        console.log("1111111");

        if (!user) return; // 비로그인이면 연결 안 함

        const eventSource = new EventSource(
            `${CONFIG.API_CONTENTS_URL}/alert/connects`,
            { withCredentials: true } // 쿠키(JWT) 포함!
        );

        // 알림 수신 - chatKey는 onNotification에서 받아서 넣어줌
        eventSource.onmessage = (event) => {
            handleNotification(event.data, null);
        };

        eventSource.onerror = (error) => {
            console.error('SSE 연결 오류:', error);
            eventSource.close();
        };

        // 언마운트 or user 변경 시 연결 해제
        return () => {
            eventSource.close();
        };

    }, [user]); // ✅ user 바뀔 때만 재실행!

    const fetchMyInfo = async () => {

        try {
            const res = await fetch(`${CONFIG.API_BASE_URL}/login/myInfo`, {
                credentials: 'include',

            });

                const data = await res.json();
                setUser(data);

        } catch (e) {
            console.error('사용자 정보 로드 실패:', e);
        }
    };

    const handleLogout = async () => {
        try {
            await fetch(`${CONFIG.API_BASE_URL}/login/logout`, {
                method: 'GET',
                credentials: 'include'
            });
        } catch (e) {
            console.error('로그아웃 요청 실패:', e);
        }

        localStorage.removeItem('showChat');
        setUser(null);
        showToastMessage('로그아웃 되었습니다.');
    };

    const showToastMessage = (msg) => {
        setToast(msg);
        setTimeout(() => setToast(''), 2500);
    };
// 새 채팅 생성 함수 추가
    const handleNewChat = () => {
        localStorage.removeItem('showChat');  // ← 이전 채팅방 키 삭제!

        setSelectedChatKey(null);  // 선택된 채팅방 초기화
        setChatKey(prev => prev + 1);

        setCurrentView('home');    // 홈으로 이동
    }
    const renderContent = () => {
        switch (currentView) {
            case 'chat':
                return (
                    <div style={{ display: 'flex', flex: 1, justifyContent: 'center', overflow: 'auto' }}>
                        <ChattingList onChatSelect={(key) => { setSelectedChatKey(key); setCurrentView('home'); }} user={user} />

                    </div>
                );
            case 'settings':
                return (
                    <SettingsView
                        user={user}
                        onBack={() => setCurrentView('home')}
                    />
                );
            default:
                return <ChatHome key={chatKey} user={user} isActive={sidebarActive} selectedChatKey={selectedChatKey} onChatLoaded={() => setSelectedChatKey(null)} onChatSelect={(key) => { setSelectedChatKey(key); setCurrentView('home'); }} onNotification={handleNotification} />;
        }
    };

    return (
        <>
            <SideBar
                isActive={sidebarActive}
                onMenuClick={() => setSidebarActive(prev => !prev)}
                onHomeClick={handleNewChat}
                onChatClick={() => setCurrentView('chat')}
                onBellClick={() => setShowAlert(true)}
                onSettingsClick={() => setCurrentView('settings')}
            />
            <div className={sidebarActive ? 'contentActive' : 'content'}>
                <TopBar
                    user={user}
                    isActive={sidebarActive}
                    onLoginClick={handleLoginClick}
                    onSignupClick={() => setShowSignup(true)}
                    onLogout={handleLogout}
                    isSettings={currentView === 'settings'}
                    onSettingsBack={() => setCurrentView('home')}
                />
                {renderContent()}
            </div>

            {showLogin && <LoginModal onClose={() => setShowLogin(false)} onLoginComplete={(msg) => { setShowLogin(false); fetchMyInfo(); showToastMessage(msg); }} />}
            {showSignup && <SignupModal onClose={() => setShowSignup(false)} onSignupComplete={(msg) => { setShowSignup(false); showToastMessage(msg); }} />}
            {showLoginView && <LoginView onClose={() => setShowLoginView(false)} />}
            {showAlert && <AlertModal onClose={() => setShowAlert(false)} notifications={notifications} onChatSelect={(key) => { setSelectedChatKey(key); setCurrentView('home'); setShowAlert(false); }} />}
            {showNicknameSetup && (
                <NicknameSetupModal
                    socialNickname={socialNickname}
                    onComplete={(nick) => {
                        setShowNicknameSetup(false);
                        setSocialNickname('');
                        setUser(prev => prev ? { ...prev, nickname: nick } : prev);
                        fetchMyInfo();
                        showToastMessage('닉네임이 설정되었습니다. 환영합니다!');
                    }}
                />
            )}
            {toast && <div id="toast" className="show">{toast}</div>}
        </>
    );
}
