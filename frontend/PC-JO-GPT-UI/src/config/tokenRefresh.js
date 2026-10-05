import CONFIG from './config';

// 401 응답 시 토큰 갱신 후 재시도하는 fetch 래퍼
export async function fetchWithRefresh(url, options = {}) {
    let res = await fetch(url, options);
    if (res.status === 401) {
        const refreshed = await fetch(`${CONFIG.API_BASE_URL}/login/refresh`, {
            method: 'POST',
            credentials: 'include',
        });
        if (refreshed.ok) {
            res = await fetch(url, options);
        }
    }
    return res;
}
