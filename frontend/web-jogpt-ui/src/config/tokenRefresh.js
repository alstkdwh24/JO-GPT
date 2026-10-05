import CONFIG from "./config";
// 리프레쉬 토큰
export const fetchWithRefresh = async (url, options = {}) => {
    let res = await fetch(url, options);
    if (res.status === 401) {
        // Handle token refresh logic here
        const refreshResponse = await fetch(`${CONFIG.API_BASE_URL}/refreshToken/login/refresh`, {
            method: "POST",
            credentials: "include"
        });
        if (refreshResponse.ok) {
            // 토큰은 서버가 쿠키로 세팅 -> 프론트는 그냥 재시도
            res = await fetch(url, options);  // 재시도
        } else {

            window.location.href = '/';
        }
    }
    return res;
}