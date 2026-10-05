import CONFIG from '../config/config';

// Electron 환경에서는 외부 브라우저로 소셜 로그인 → 딥링크(jo-gpt://)로 토큰 수신
export default function LoginModal({ onClose }) {
    const handleSocialLogin = (provider) => {
        // Electron 환경이면 ?client=electron 붙여서 백엔드에 알림
        const clientParam = window.electronAPI?.isElectron ? '?client=electron' : '';
        const url = `${CONFIG.API_BASE_URL}/oauth2/authorization/${provider}${clientParam}`;

        if (window.electronAPI?.isElectron) {
            // Electron: 외부 브라우저로 열기 (OAuth2 보안 정책 때문에 내부 창에서는 막힘)
            window.electronAPI.openExternal(url);
            onClose(); // 모달 닫기
        } else {
            // 웹: 현재 창에서 이동
            window.location.href = url;
        }
    };

    return (
        <div className="modelLogin" style={{ display: 'flex' }} onClick={onClose}>
            <div className="modelJoin" onClick={(e) => e.stopPropagation()}>
                <div className="modelJoinTitle">
                    <h2 className="modelJoinTitleH2 fontFamily">로그인 또는 회원가입</h2>
                    <p className="modelJoinTitleP fontFamily">소셜 로그인을 통해 쉽게 로그인하세요</p>
                </div>
                <div className="modelJoinSocial">
                    <div className="socialJoin-kakao" onClick={() => handleSocialLogin('kakao')}>
                        <div className="socialJoins-round">
                            <img src="/image/kakaoLogo.png" className="login-logo" alt="카카오로고" />
                        </div>
                        <div className="socialJoins-name">카카오 로그인</div>
                    </div>
                    <div className="socialJoin-naver" onClick={() => handleSocialLogin('naver')}>
                        <div className="socialJoins-round">
                            <img src="/image/naver_login.png" alt="네이버 로그인" className="login-logo" />
                        </div>
                        <div className="socialJoins-name">네이버 로그인</div>
                    </div>
                    <div className="socialJoin-google" onClick={() => handleSocialLogin('google')}>
                        <div className="socialJoins-round">
                            <img src="/image/google_login.png" alt="구글 로그인" className="login-logo login-google" />
                        </div>
                        <div className="socialJoins-name">구글 로그인</div>
                    </div>
                    <div className="socialJoin-github" onClick={() => handleSocialLogin('github')}>
                        <div className="socialJoins-round">
                            <img src="/image/GitHub_login.svg" alt="깃허브 로그인" className="login-logo" />
                        </div>
                        <div className="socialJoins-name">깃허브 로그인</div>
                    </div>
                    <div className="socialJoin-self">
                        <div className="socialJoins-round">
                            <img alt="JO-GPT_login" src="/image/JO-GPT_login.png" className="login-logo" />
                        </div>
                        <div className="socialJoins-name">JO-GPT 회원가입</div>
                    </div>
                </div>
            </div>
        </div>
    );
}
