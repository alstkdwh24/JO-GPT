import { useState } from 'react';
import CONFIG from '../config/config';
// 닉네임 관련 모달
export default function NicknameSetupModal({ socialNickname, onComplete }) {
    const [mode, setMode] = useState(socialNickname ? 'choose' : 'input');
    const [nickname, setNickname] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    // 나의 닉네임 저장
    const saveNickname = async (value) => {
        const trimmed = value.trim();
        if (!trimmed) { setError('닉네임을 입력해주세요.'); return; }
        if (trimmed.length < 2 || trimmed.length > 12) {
            setError('닉네임은 2자 이상 12자 이하로 입력해주세요.');
            return;
        }
        setLoading(true);
        setError('');
        try {
            const res = await fetch(`${CONFIG.API_BASE_URL}/login/nickname`, {
                headers: { 'Content-Type': 'application/json' },
                method: 'PUT',
                credentials: 'include',

                body: JSON.stringify({ nickname: trimmed })
            });
            if (res.ok) {
                onComplete(trimmed);
            } else {
                const msg = await res.text();
                setError(msg || '닉네임 설정에 실패했습니다.');
            }
        } catch {
            setError('서버 연결에 실패했습니다.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="nickname-overlay">
            <div className="nickname-modal">
                <h2 className="nickname-title fontFamily">닉네임 설정</h2>

                {mode === 'choose' && (
                    <>
                        <p className="nickname-desc">처음 로그인하셨습니다.<br />닉네임을 어떻게 설정하시겠어요?</p>
                        <button
                            className="nickname-btn"
                            style={{ marginBottom: '10px' }}
                            disabled={loading}
                            onClick={() => saveNickname(socialNickname)}
                        >
                            {loading ? '저장 중...' : `네이버 닉네임 사용 (${socialNickname})`}
                        </button>
                        <button
                            className="nickname-btn"
                            style={{ background: '#555' }}
                            disabled={loading}
                            onClick={() => { setMode('input'); setError(''); }}
                        >
                            새 닉네임 만들기
                        </button>
                        {error && <p className="nickname-error">{error}</p>}
                    </>
                )}

                {mode === 'input' && (
                    <>
                        <p className="nickname-desc">
                            {socialNickname ? '직접 닉네임을 입력해주세요.' : '처음 로그인하셨습니다.\n사용할 닉네임을 입력해주세요.'}
                        </p>
                        <input
                            className="nickname-input"
                            type="text"
                            placeholder="닉네임 (2~12자)"
                            value={nickname}
                            onChange={(e) => setNickname(e.target.value)}
                            onKeyDown={(e) => e.key === 'Enter' && saveNickname(nickname)}
                            maxLength={12}
                            autoFocus
                        />
                        {error && <p className="nickname-error">{error}</p>}
                        <div style={{ display: 'flex', gap: '8px' }}>
                            {socialNickname && (
                                <button
                                    className="nickname-btn"
                                    style={{ background: '#555', flex: 1 }}
                                    disabled={loading}
                                    onClick={() => { setMode('choose'); setError(''); }}
                                >
                                    뒤로
                                </button>
                            )}
                            <button
                                className="nickname-btn"
                                style={{ flex: 1 }}
                                onClick={() => saveNickname(nickname)}
                                disabled={loading}
                            >
                                {loading ? '저장 중...' : '완료'}
                            </button>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}