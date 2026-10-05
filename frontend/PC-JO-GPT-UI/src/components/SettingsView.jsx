import { useState } from 'react';


const PROMPTS_KEY = 'CUSTOM_PROMPTS';
const ACTIVE_KEY = 'CUSTOM_PROMPT';

function loadPrompts() {
    try { return JSON.parse(localStorage.getItem(PROMPTS_KEY)) || []; }
    catch { return []; }
}

function PromptListPage({ onBack }) {
    const [prompts, setPrompts] = useState(loadPrompts);
    const [activeText, setActiveText] = useState(localStorage.getItem(ACTIVE_KEY) || '');

    const handleApply = (text) => {
        localStorage.setItem(ACTIVE_KEY, text);
        setActiveText(text);
    };

    const handleRelease = () => {
        localStorage.removeItem(ACTIVE_KEY);
        setActiveText('');
    };

    const handleDelete = (idx) => {
        const target = prompts[idx];
        const updated = prompts.filter((_, i) => i !== idx);
        setPrompts(updated);
        localStorage.setItem(PROMPTS_KEY, JSON.stringify(updated));
        if (target.text === activeText) {
            localStorage.removeItem(ACTIVE_KEY);
            setActiveText('');
        }
    };

    return (
        <div className="sv-wrap">
            <div className="sv-body">
                <div className="sv-section">
                    <button className="sv-list-back-btn" onClick={onBack}>
                        <i className="fa fa-arrow-left"></i>
                        <span>설정으로 돌아가기</span>
                    </button>
                </div>
                <div className="sv-section">
                    <div className="sv-section-title">저장된 프롬프트</div>
                    {prompts.length === 0 ? (
                        <p className="sv-no-prompts">저장된 프롬프트가 없습니다.</p>
                    ) : (
                        <div className="sv-prompt-list">
                            {prompts.map((p, i) => (
                                <div
                                    key={i}
                                    className={`sv-prompt-item${p.text === activeText ? ' sv-prompt-item--active' : ''}`}
                                >
                                    <div className="sv-prompt-item-info">
                                        <div className="sv-prompt-item-header">
                                            <span className="sv-prompt-item-name">{p.name}</span>
                                            {p.text === activeText && (
                                                <span className="sv-applied-badge">적용 중</span>
                                            )}
                                        </div>
                                        <span className="sv-prompt-item-preview">{p.text}</span>
                                    </div>
                                    <div className="sv-prompt-item-actions">
                                        {p.text === activeText ? (
                                            <button className="sv-apply-btn sv-apply-btn--on" onClick={handleRelease}>
                                                해제
                                            </button>
                                        ) : (
                                            <button className="sv-apply-btn" onClick={() => handleApply(p.text)}>
                                                적용
                                            </button>
                                        )}
                                        <button className="sv-delete-btn" onClick={() => handleDelete(i)}>
                                            <i className="fa fa-trash"></i>
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

export default function SettingsView({ onBack, user }) {
    const [view, setView] = useState('main');
    const [newName, setNewName] = useState('');
    const [newText, setNewText] = useState('');
    const [saved, setSaved] = useState(false);

    if (view === 'prompts') {
        return <PromptListPage onBack={() => setView('main')} />;
    }

    const activePrompt = localStorage.getItem(ACTIVE_KEY) || '';

    const handleSave = () => {
        if (!newText.trim()) return;
        const name = newName.trim() || `프롬프트 ${new Date().toLocaleString('ko-KR')}`;
        const prompts = loadPrompts();
        prompts.push({ name, text: newText.trim() });
        localStorage.setItem(PROMPTS_KEY, JSON.stringify(prompts));
        setNewName('');
        setNewText('');
        setSaved(true);
        setTimeout(() => setSaved(false), 1800);
    };


    return (
        <div className="sv-wrap">
            <div className="sv-body">

                {/* 계정 섹션 */}
                {user && (
                    <div className="sv-section">
                        <div className="sv-section-title">계정</div>
                        <div className="sv-item">
                            <span className="sv-item-label">사용자</span>
                            <span className="sv-item-value">{user.name}</span>
                        </div>
                        {user.email && (
                            <div className="sv-item">
                                <span className="sv-item-label">이메일</span>
                                <span className="sv-item-value">{user.email}</span>
                            </div>
                        )}
                    </div>
                )}

                {/* 프롬프트 설정 섹션 */}
                <div className="sv-section">
                    <div className="sv-section-title">프롬프트 설정</div>

                    {/* 현재 적용 중 */}
                    {activePrompt ? (
                        <div className="sv-prompt-preview">
                            <span className="sv-prompt-preview-label">현재 적용 중</span>
                            <span className="sv-prompt-preview-text">{activePrompt}</span>

                        </div>
                    ) : (
                        <p className="sv-section-desc">적용 중인 프롬프트가 없습니다.</p>
                    )}

                    {/* 목록 이동 버튼 */}
                    <button className="sv-goto-list-btn" onClick={() => setView('prompts')}>
                        <span>저장된 프롬프트 목록</span>
                        <i className="fa fa-chevron-right"></i>
                    </button>

                    {/* 새 프롬프트 추가 */}
                    <div className="sv-add-form">
                        <div className="sv-section-title">새 프롬프트 추가</div>
                        <input
                            className="sv-add-name-input"
                            placeholder="프롬프트 이름 (선택)"
                            value={newName}
                            onChange={e => setNewName(e.target.value)}
                        />
                        <textarea
                            className="sv-prompt-textarea"
                            placeholder={"예시:\n너는 친절한 한국어 어시스턴트야.\n항상 마크다운 형식으로 답변해줘.\n코드는 반드시 설명과 함께 작성해줘."}
                            value={newText}
                            onChange={e => setNewText(e.target.value)}
                            rows={5}
                        />
                        <div className="sv-prompt-actions">
                            <button
                                className={`sv-save-btn${saved ? ' sv-save-btn--saved' : ''}`}
                                onClick={handleSave}
                            >
                                {saved ? '저장됨 ✓' : '저장'}
                            </button>
                        </div>
                    </div>
                </div>

                {/* 정보 섹션 */}
                <div className="sv-section">
                    <div className="sv-section-title">정보</div>
                    <div className="sv-item">
                        <span className="sv-item-label">버전</span>
                        <span className="sv-item-value">1.0.0</span>
                    </div>
                </div>

            </div>
        </div>
    );
}