import CONFIG from '../config/config';

export default function LoginView({ onClose }) {
    const handleSocialLogin = (url) => {
        window.location.href = url;
    };

    return (
        <div className="lv-wrap">
            <div className="lv-header">
                <button className="lv-back-btn" onClick={onClose}>
                    <i className="fa fa-arrow-left"></i>
                </button>
                <span className="lv-header-title">로그인</span>
            </div>

            <div className="lv-body">
                <div className="lv-title-section">
                    <h2 className="lv-title">로그인 또는 회원가입</h2>
                    <p className="lv-subtitle">소셜 로그인을 통해 쉽게 로그인하세요</p>
                </div>

                <div className="lv-social-list">
                    <button className="lv-social-btn lv-kakao" onClick={() => handleSocialLogin(`${CONFIG.API_BASE_URL}/oauth2/authorization/kakao`)}>
                        <div className="lv-social-icon">
                            <img src="/image/kakaoLogo.png" alt="카카오" className="lv-social-img" />
                        </div>
                        <span>카카오 로그인</span>
                    </button>

                    <button className="lv-social-btn lv-naver" onClick={() => handleSocialLogin(`${CONFIG.API_BASE_URL}/oauth2/authorization/naver`)}>
                        <div className="lv-social-icon">
                            <img src="/image/naver_login.png" alt="네이버" className="lv-social-img" />
                        </div>
                        <span>네이버 로그인</span>
                    </button>

                    <button className="lv-social-btn lv-google" onClick={() => handleSocialLogin(`${CONFIG.API_BASE_URL}/oauth2/authorization/google`)}>
                        <div className="lv-social-icon">
                            <img src="/image/google_login.png" alt="구글" className="lv-social-img" />
                        </div>
                        <span>구글 로그인</span>
                    </button>

                    <button className="lv-social-btn lv-facebook" onClick={() => handleSocialLogin(`${CONFIG.API_BASE_URL}/oauth2/authorization/facebook`)}>
                        <div className="lv-social-icon">
                            <img src="/image/facebook_login.png" alt="페이스북" className="lv-social-img" />
                        </div>
                        <span>페이스북 로그인</span>
                    </button>

                    <button className="lv-social-btn lv-github" onClick={() => handleSocialLogin(`${CONFIG.API_BASE_URL}/oauth2/authorization/github`)}>
                        <div className="lv-social-icon">
                            <img src="/image/GitHub_login.svg" alt="깃허브" className="lv-social-img" />
                        </div>
                        <span>깃허브 로그인</span>
                    </button>

                    <button className="lv-social-btn lv-self">
                        <div className="lv-social-icon">
                            <img src="/image/JO-GPT_login.png" alt="JO-GPT" className="lv-social-img" />
                        </div>
                        <span>JO-GPT 회원가입</span>
                    </button>
                </div>
            </div>
        </div>
    );
}