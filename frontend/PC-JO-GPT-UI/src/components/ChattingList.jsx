import { useEffect, useState } from 'react';
import CONFIG from '../config/config';

export default function ChattingList({ onChatSelect, user }) {

    const [chatList, setChatList] = useState([]);
    const [search, setSearch] = useState('');

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

    // 시간 포맷
    const formatTime = (dateStr) => {
        const d = new Date(dateStr);
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        const hours = String(d.getHours()).padStart(2, '0');
        const minutes = String(d.getMinutes()).padStart(2, '0');
        return `${month}/${day} ${hours}:${minutes}`;
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
    // 채팅 검색 (아직 미완)
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
