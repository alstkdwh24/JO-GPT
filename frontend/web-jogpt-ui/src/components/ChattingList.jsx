import { useEffect, useRef, useState } from 'react';
import CONFIG from '../config/config';

export default function ChattingList({ onChatSelect, user }) {

    const [chatList, setChatList] = useState([]);
    const [search, setSearch] = useState('');
    const isFirstSearch = useRef(true);

    useEffect(() => {
        loadChattingList();
    }, []);
    // 채팅 리스트 로딩
    const loadChattingList = async () => {
        if (user) {
            try {
                const res = await fetch(`${CONFIG.API_CONTENTS_URL}/contents/chattingList`, {
                    method: 'GET',
                    credentials: 'include'

                });
                if (res.ok) {
                    const data = await res.json();
                    setChatList(data);
                }
            } catch (e) {
                console.error('채팅 목록 로드 실패:', e);
            }
        } else {
            setChatList([
                { showMyChatContents: '내 물음', showChatRegistration: new Date().toISOString(), mock: true },
                { showMyChatContents: '내 물음', showChatRegistration: new Date().toISOString(), mock: true },
                { showMyChatContents: '내 물음', showChatRegistration: new Date().toISOString(), mock: true },
            ]);
        }
    };

    // 시간 포맷 - 한국 시간(Asia/Seoul)으로 변환
    const formatTime = (dateStr) => {
        const d = new Date(dateStr);
        return d.toLocaleString('ko-KR', {
            timeZone: 'Asia/Seoul',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        }).replace('. ', '/')
            .replace("." ,"");
    };

    // 채팅 삭제 메서드
    const handleDelete = async (e, key) => {
        e.stopPropagation();
        if (!user) return;
        try {
            await fetch(`${CONFIG.API_CONTENTS_URL}/contents/chatRoom/${key}`, {
                method: 'DELETE',
                credentials: 'include'

            });
            setChatList(prev => prev.filter(item => String(item.showChatKey) !== String(key)));
        } catch (e) {
            console.error('채팅 삭제 실패:', e);
        }
    };
    // 채팅 검색 - 입력이 멈추고 300ms 뒤에 요청, 결과로 목록을 교체 (빈 검색어면 전체 목록)
    useEffect(() => {
        // 첫 렌더링은 loadChattingList가 목록을 불러오므로 건너뜀
        if (isFirstSearch.current) {
            isFirstSearch.current = false;
            return;
        }
        if (!user) return;
        let cancelled = false; // 이전 검색 응답이 늦게 와서 최신 결과를 덮어쓰지 않게
        const timer = setTimeout(async () => {
            try {
                const res = await fetch(`${CONFIG.API_CONTENTS_URL}/contents/searchChatting`, {
                    method: 'POST',
                    credentials: 'include',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({ search })
                });
                if (res.ok && !cancelled) {
                    setChatList(await res.json());
                }
            } catch (e) {
                console.error('채팅 검색 실패:', e);
            }
        }, 300);
        return () => {
            cancelled = true;
            clearTimeout(timer);
        };
    }, [search]);

    const handleSearchInput = (e) => {
        setSearch(e.target.value);
        e.target.style.height = 'auto';
        e.target.style.height = e.target.scrollHeight + 'px';
    };


    return (
        <div className="chatting-container">
            <div id="title-chatting">
                <div id="chatting-icon-container">
                    <img src="/image/blueChatTing.png" alt="Chat Icon" id="chatting-icon" />
                    <h1>채팅</h1>
                </div>
            </div>
            <div id="search-chatting">
                <label id="search-input">
                    <i className="fa fa-search" id="search-icon"></i>
                    <textarea
                        placeholder="대화 내용 검색"
                        id="search-chatting-input"
                        rows={1}
                        value={search}
                        onChange={handleSearchInput}
                    />
                </label>
            </div>
            <div id="chatting-list">
                {chatList.map((item, i) => (
                    <div
                        className="chatting-list-container"
                        key={i}
                        onClick={() => !item.mock && onChatSelect?.(item.showChatKey)}
                        style={{ cursor: item.mock ? 'default' : 'pointer' }}
                    >
                        <img
                            className="chatting-icon"
                            src={item.mock ? '/image/Gemini_chat.png' : '/image/blueChatTing.png'}
                            alt="채팅 아이콘"
                        />
                        <div className="chatting-list-word">
                            {item.showMyChatContents}

                            <div className="chatting-list-time">
                                <i className="fa fa-clock-o chatting-list-time-icon"></i>
                                <span className="chatting-list-time-text">
                                    {item.mock ? '오전 10:00' : formatTime(item.showChatRegistration)}
                                </span>
                            </div>
                        </div>
                        {!item.mock && (
                            <button
                                className="chatting-delete-btn"
                                onClick={(e) => handleDelete(e, item.showChatKey)}

                            >
                                <i className="fa fa-trash"></i>
                            </button>
                        )}
                    </div>
                ))}
            </div>
        </div>
    );
}
