import { useState } from "react";
import CONFIG from "../config/config";

export default function SignupModal({ onClose, onSignupComplete }) {
    const [step, setStep] = useState(1); // 1: 아이디/비번, 2: 닉네임
    const [userId, setUserId] = useState("");
    const [userPw, setUserPw] = useState("");
    const [nickname, setNickname] = useState("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    // 1단계 → 2단계
    const handleNext = () => {
        if (!userId.trim()) { setError("아이디를 입력해주세요."); return; }
        if (!userPw.trim()) { setError("비밀번호를 입력해주세요."); return; }
        setError("");
        setStep(2);
    };

    // 2단계 → 회원가입 완료
    const handleComplete = async () => {
        if (!nickname.trim() || nickname.trim().length < 2 || nickname.trim().length > 12) {
            setError("닉네임은 2자 이상 12자 이하로 입력해주세요.");
            return;
        }
        setLoading(true);
        setError("");
        try {
            const res = await fetch(`${CONFIG.API_BASE_URL}/login/signUp`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                credentials: "include",
                body: JSON.stringify({
                    memberId:  userId.trim(),
                    userPw: userPw.trim(),
                    nickname: nickname.trim(),
                }),
            });
            if (res.ok) {
                onClose();
                onSignupComplete("회원가입이 완료되었습니다.");
            } else {
                const msg = await res.text();
                setError(msg || "회원가입에 실패했습니다.");
            }
        } catch {
            setError("서버 연결에 실패했습니다.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="modelLogin" style={{ display: "flex" }} onClick={onClose}>
            <div className="modelJoin modelJoin--jogpt" onClick={(e) => e.stopPropagation()}>

                {step === 1 && (
                    <>
                        <div className="modelJoinTitle">
                            <h2 className="modelJoinTitleH2 fontFamily">회원가입</h2>
                            <p className="modelJoinTitleP fontFamily">아이디와 비밀번호를 입력해주세요</p>
                        </div>
                        <div className="signup-form">
                            <input
                                className="nickname-input"
                                type="text"
                                placeholder="아이디"
                                value={userId}
                                onChange={(e) => setUserId(e.target.value)}
                                autoFocus
                            />
                            <input
                                className="nickname-input"
                                type="password"
                                placeholder="비밀번호"
                                value={userPw}
                                onChange={(e) => setUserPw(e.target.value)}
                                onKeyDown={(e) => e.key === "Enter" && handleNext()}
                            />
                            {error && <p className="nickname-error">{error}</p>}
                            <button className="nickname-btn" onClick={handleNext} disabled={loading}>
                                다음
                            </button>
                        </div>
                    </>
                )}

                {step === 2 && (
                    <>
                        <div className="modelJoinTitle">
                            <h2 className="modelJoinTitleH2 fontFamily">닉네임 설정</h2>
                            <p className="modelJoinTitleP fontFamily">사용할 닉네임을 입력해주세요 (2~12자)</p>
                        </div>
                        <div className="signup-form">
                            <input
                                className="nickname-input"
                                type="text"
                                placeholder="닉네임 (2~12자)"
                                value={nickname}
                                onChange={(e) => setNickname(e.target.value)}
                                onKeyDown={(e) => e.key === "Enter" && handleComplete()}
                                maxLength={12}
                                autoFocus
                            />
                            {error && <p className="nickname-error">{error}</p>}
                            <div style={{ display: "flex", gap: "8px" }}>
                                <button
                                    className="nickname-btn"
                                    style={{ background: "#555", flex: 1 }}
                                    onClick={() => { setStep(1); setError(""); }}
                                    disabled={loading}
                                >
                                    뒤로
                                </button>
                                <button
                                    className="nickname-btn"
                                    style={{ flex: 1 }}
                                    onClick={handleComplete}
                                    disabled={loading}
                                >
                                    {loading ? "처리 중..." : "완료"}
                                </button>
                            </div>
                        </div>
                    </>
                )}

            </div>
        </div>
    );
}