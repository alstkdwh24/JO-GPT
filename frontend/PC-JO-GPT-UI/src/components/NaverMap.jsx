import { useEffect, useRef } from 'react';

// 네이버 지도 SDK URL (클라우드에서 발급받은 클라이언트 ID 사용)
const NAVER_SCRIPT_SRC = `https://maps.apigw.ntruss.com/openapi/v3/maps.js?ncpClientId=${import.meta.env.VITE_NAVER_MAP_CLIENT_ID}`;

// 스크립트 중복 삽입 방지 - 세 가지 경우를 처리
// 1. 이미 로드 완료 → 바로 resolve
// 2. script 태그는 있는데 로딩 중 → load 이벤트 대기
// 3. 아예 없음 → 새로 삽입
function loadNaverMapScript() {
    return new Promise((resolve) => {
        if (window.naver && window.naver.maps) { resolve(); return; }
        const existing = document.querySelector('script[src*="maps.apigw.ntruss.com"]');
        if (existing) { existing.addEventListener('load', resolve); return; }
        const script = document.createElement('script');
        script.src = NAVER_SCRIPT_SRC;
        script.onload = resolve;
        document.head.appendChild(script);
    });
}

export default function NaverMap({ coords, mapId }) {
    const mapRef = useRef(null);

    useEffect(() => {
        if (!coords) return;
        const x = parseFloat(coords.x); // 경도
        const y = parseFloat(coords.y); // 위도
        if (isNaN(x) || isNaN(y)) return;

        loadNaverMapScript().then(() => {
            if (!mapRef.current) return;
            const map = new window.naver.maps.Map(mapRef.current, {
                center: new window.naver.maps.LatLng(y, x),
                zoom: 16,
            });
            new window.naver.maps.Marker({ position: new window.naver.maps.LatLng(y, x), map });
        });
    }, [coords]);

    if (!coords) return null;

    return (
        <div
            ref={mapRef}
            id={mapId}
            style={{ width: '100%', height: '300px', borderRadius: '12px', marginTop: '10px', overflow: 'hidden' }}
        />
    );
}
