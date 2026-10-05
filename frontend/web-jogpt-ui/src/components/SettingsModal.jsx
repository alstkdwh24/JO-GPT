import { useState, useEffect } from 'react';
import axios from 'axios';
import CONFIG from '../config/config.js';

const MEMBER_SECURITY_URL = CONFIG.AI_MEMBERSECURITY;

export default function SettingsModal({ onClose, user }) {
    const [connectedList, setConnectedList] = useState([]);

    useEffect(() => {
        axios.get(MEMBER_SECURITY_URL + '/connect/list', { withCredentials: true })
            .then(res => setConnectedList(res.data))
            .catch(() => setConnectedList([]));
    }, []);

    const isConnected = (provider) => connectedList.some(a => a.provider === provider);

    const handleConnect = (provider) => {
        window.location.href = MEMBER_SECURITY_URL + '/connect/' + provider;
    };

    return (
        <div className="settings-overlay" onClick={onClose}>
            <div className="settings-modal" onClick={(e) => e.stopPropagation()}>
                <div className="settings-header">
                    <h2 className="settings-title">설정</h2>
                    <button className="settings-close-btn" onClick={onClose}>
                        <i className="fa fa-times"></i>
                    </button>
                </div>

                <div className="settings-body">
                    {user && (
                        <div className="settings-section">
                            <div className="settings-section-label">계정</div>
                            <div className="settings-item">
                                <span className="settings-item-name">사용자</span>
                                <span className="settings-item-value">{user.name}</span>
                            </div>
                            <div className="settings-item">
                                <span className="settings-item-name">이메일</span>
                                <span className="settings-item-value">{user.email ?? '-'}</span>
                            </div>
                        </div>
                    )}

                    {/* 연동 서비스 섹션 */}
                    <div className="settings-section">
                        <div className="settings-section-label">연동 서비스</div>
                        {[{ key: 'google', label: 'Google', icon: 'G', desc: '메일·캘린더 기능 사용' }].map(({ key, label, icon, desc }) => {
                            const connected = isConnected(key);
                            return (
                                <div key={key} className="sv-connect-item">
                                    <div className="sv-connect-icon">{icon}</div>
                                    <div className="sv-connect-info">
                                        <span className="sv-connect-label">{label}</span>
                                        <span className="sv-connect-desc">
                                            {connected ? '연동됨' : '미연동 — ' + desc + ' 불가'}
                                        </span>
                                    </div>
                                    <button
                                        className={'sv-connect-btn' + (connected ? ' sv-connect-btn--on' : '')}
                                        onClick={() => !connected && handleConnect(key)}
                                        disabled={connected}
                                    >
                                        {connected ? '연동됨 ✓' : '연동'}
                                    </button>
                                </div>
                            );
                        })}
                    </div>

                    <div className="settings-section">
                        <div className="settings-section-label">일반</div>
                        <div className="settings-item">
                            <span className="settings-item-name">테마</span>
                            <span className="settings-item-value">라이트</span>
                        </div>
                        <div className="settings-item">
                            <span className="settings-item-name">언어</span>
                            <span className="settings-item-value">한국어</span>
                        </div>
                    </div>

                    <div className="settings-section">
                        <div className="settings-section-label">정보</div>
                        <div className="settings-item">
                            <span className="settings-item-name">버전</span>
                            <span className="settings-item-value">1.0.4</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}