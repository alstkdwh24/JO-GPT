import { useState } from "react";
import CONFIG from "../config/config";

export default function LoginModal({ onClose, onLoginComplete }) {
    const [showJoGpt, setShowJoGpt] = useState(false);
    const [memberId, setMemberId] = useState("");
    const [password, setPassword] = useState("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    const handleSocialLogin = (url) => {
        window.location.href = url;
    };

    const handleJoGptLogin = async () => {
        if (!memberId.trim() || !password.trim()) {
            setError("아이디와 비밀번호를 입력해주세요.");
            return;
        }
        setLoading(true);
        setError("");
        try {
            const res = await fetch(`${CONFIG.API_BASE_URL}/login/auth/login`, {
                method: "POST",
                    headers: { "Content-Type": "application/json" },
                credentials: "include",
                body: JSON.stringify({ memberId: memberId.trim(), userPw: password.trim() }),
            });
            if (res.ok) {
                onClose();
                if (onLoginComplete) onLoginComplete("로그인 되었습니다.");
            } else {
                setError("아이디 또는 비밀번호가 올바르지 않습니다.");
            }
        } catch {
            setError("서버 연결에 실패했습니다.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="modelLogin" style={{ display: "flex" }} onClick={onClose}>
            <div className={`modelJoin${showJoGpt ? " modelJoin--jogpt" : ""}`} onClick={(e) => e.stopPropagation()}>

                {!showJoGpt ? (
                    <>
                        <div className="modelJoinTitle">
                            <h2 className="modelJoinTitleH2 fontFamily" id="fontFamily">로그인</h2>
                            <p className="modelJoinTitleP fontFamily">소셜 로그인을 통해 쉽게 로그인하세요</p>
                        </div>
                        <div className="modelJoinSocial">
                            <div className="socialJoin-kakao" onClick={() => handleSocialLogin(`${CONFIG.API_BASE_URL}/oauth2/authorization/kakao`)}>
                                <div className="socialJoins-round">
                                    <img src="/image/kakaoLogo.png" className="login-logo" alt="카카오로고" />
                                </div>
                                <div className="socialJoins-name">카카오 로그인</div>
                            </div>
                            <div className="socialJoin-naver" onClick={() => handleSocialLogin(`${CONFIG.API_BASE_URL}/oauth2/authorization/naver`)}>
                                <div className="socialJoins-round">
                                    <img src="/image/naver_login.png" alt="네이버 로그인" className="login-logo" />
                                </div>
                                <div className="socialJoins-name">네이버 로그인</div>
                            </div>
                            <div className="socialJoin-google" onClick={() => handleSocialLogin(`${CONFIG.API_BASE_URL}/oauth2/authorization/google`)}>
                                <div className="socialJoins-round">
                                    <img src="/image/google_login.png" alt="구글 로그인" className="login-logo login-google" />
                                </div>
                                <div className="socialJoins-name">구글 로그인</div>
                            </div>
                            <div className="socialJoin-facebook" onClick={() => handleSocialLogin(`${CONFIG.API_BASE_URL}/oauth2/authorization/facebook`)}>
                                <div className="socialJoins-round">
                                    <img src="/image/facebook_login.png" alt="페이스북 로그인" className="login-logo" />
                                </div>
                                <div className="socialJoins-name">페이스북 로그인</div>
                            </div>
                            <div className="socialJoin-github" onClick={() => handleSocialLogin(`${CONFIG.API_BASE_URL}/oauth2/authorization/github`)}>
                                <div className="socialJoins-round">
                                    <img src="/image/GitHub_login.svg" alt="깃허브 로그인" className="login-logo" />
                                </div>
                                <div className="socialJoins-name">깃허브 로그인</div>
                            </div>
                            <div className="socialJoin-self" onClick={() => setShowJoGpt(true)}>
                                <div className="socialJoins-round">
                                    <img alt="JO-GPT_login" src="/image/JO-GPT_login.png" className="login-logo" />
                                </div>
                                <div className="socialJoins-name">JO-GPT 로그인</div>
                            </div>
                        </div>
                    </>
                ) : (
                    <>
                        <div className="modelJoinTitle">
                            <h2 className="modelJoinTitleH2 fontFamily">JO-GPT 로그인</h2>
                            <p className="modelJoinTitleP fontFamily">아이디와 비밀번호를 입력해주세요</p>
                        </div>
                        <div className="signup-form" style={{ padding: "0 36px 36px" }}>
                            <input
                                className="nickname-input"
                                type="text"
                                placeholder="아이디"
                                value={memberId}
                                onChange={(e) => setMemberId(e.target.value)}
                                autoFocus
                            />
                            <input
                                className="nickname-input"
                                type="password"
                                placeholder="비밀번호"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                onKeyDown={(e) => e.key === "Enter" && handleJoGptLogin()}
                            />
                            {error && <p className="nickname-error">{error}</p>}
                            <div style={{ display: "flex", gap: "8px" }}>
                                <button
                                    className="nickname-btn"
                                    style={{ background: "#555", flex: 1 }}
                                    onClick={() => { setShowJoGpt(false); setError(""); }}
                                    disabled={loading}
                                >
                                    뒤로
                                </button>
                                <button
                                    className="nickname-btn"
                                    style={{ flex: 1 }}
                                    onClick={handleJoGptLogin}
                                    disabled={loading}
                                >
                                    {loading ? "로그인 중..." : "로그인"}
                                </button>
                            </div>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}