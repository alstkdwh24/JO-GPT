export default function AlertModal({ onClose, notifications = [], onChatSelect }) {

    const formatTime = (date) => {
        const d = new Date(date);  // 입력값 (타임스탬프, 문자열, Date객체 등)을 Date 객체로 변환
        const hours = String(d.getHours()).padStart(2, '0'); // 로컬시간을 추출하고, 한자리 숫자일때 앞에 '0'을 추가
        const minutes = String(d.getMinutes()).padStart(2, '0'); // 분을 추가하고 마찮가지로 한자리 숫자일때 '0'을 추가
        return `${hours}:${minutes}`;
    };

    const handleNotificationClick = (noti) => {
        if (noti.chatKey) {
            onChatSelect?.(noti.chatKey); //옵셔널 체이닐 - onChatSelect가 함수로 전달되었을 때만 호출 그리고 null이어도 에러 발생은 안함, 없으면 에러 무시 긜고 어떤 채팅방을 열지 식별하는 키 값을 부모 컴포넌트에 전달
        }
    };

    return (
        <div className="tpl-toast-alert show" onClick={onClose}>
            <div className="toast-alert" onClick={(e) => e.stopPropagation()}>
                <div className="toast-alert-header">
                    <h1>알림</h1>
                    <button className="toast-alert-close" onClick={onClose}>✕</button>
                </div>
                <div className="toast-alert-body">
                    {notifications.length === 0 ? (
                        <div className="toast-alert-empty">알림이 없습니다.</div>
                    ) : (
                        notifications.map((noti) => (
                            <div
                                key={noti.id}
                                className={`toast-alert-item ${noti.chatKey ? 'clickable' : ''}`}
                                onClick={() => handleNotificationClick(noti)}
                            >
                                <div className="toast-alert-item-icon">🔔</div>
                                <div className="toast-alert-item-content">
                                    <div className="toast-alert-item-message">{noti.message}</div>
                                    <div className="toast-alert-item-time">{formatTime(noti.time)}</div>
                                </div>
                                {noti.chatKey && (
                                    <div className="toast-alert-item-arrow">›</div>
                                )}
                            </div>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
}