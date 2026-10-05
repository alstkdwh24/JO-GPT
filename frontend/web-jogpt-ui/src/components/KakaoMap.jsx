import {useEffect, useRef} from 'react';

const KAKAO_SCRIPT_SRC = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${import.meta.env.VITE_KAKAO_MAP_JS_KEY}&autoload=false`;

function loadKakaoMapScript() {
    return new Promise((resolve) => {
        if (window.kakao && window.kakao.maps && window.kakao.maps.readyState === 2) {
            resolve();
            return;
        }
        if (window.kakao && window.kakao.maps) {
            window.kakao.maps.load(resolve);
            return;
        }
        const existing = document.querySelector('script[src*="dapi.kakao.com"]');
        if (existing) {
            existing.addEventListener('load', () => window.kakao.maps.load(resolve));
            return;
        }
        const script = document.createElement('script');
        script.src = KAKAO_SCRIPT_SRC;
        script.onload = () => window.kakao.maps.load(resolve);
        document.head.appendChild(script);
    });
}

// places = 배열 [{name, x, y, address, url}, ...]
export default function KakaoMap({places, mapId}) {
    const mapRef = useRef(null);

    useEffect(() => {
        if (!places || places.length === 0) return;

        loadKakaoMapScript().then(() => {
            if (!mapRef.current) return;

            // 첫 번째 장소 기준으로 지도 중심 설정
            const first = places[0];
            const centerX = parseFloat(first.x);
            const centerY = parseFloat(first.y);

            const map = new window.kakao.maps.Map(mapRef.current, {
                center: new window.kakao.maps.LatLng(centerY, centerX),
                level: 5,
            });

            // 모든 장소에 마커 찍기
            places.forEach((place) => {
                const x = parseFloat(place.x);
                const y = parseFloat(place.y);
                if (isNaN(x) || isNaN(y)) return;

                const marker = new window.kakao.maps.Marker({
                    position: new window.kakao.maps.LatLng(y, x),
                    map: map,
                });

                // 인포윈도우 (마커 위 말풍선)
                const infowindow = new window.kakao.maps.InfoWindow({
                    content: `<div style="padding:5px;font-size:12px;white-space:nowrap;">${place.name}</div>`,
                });

                // 마커 hover 시 인포윈도우 표시
                window.kakao.maps.event.addListener(marker, 'mouseover', () => {
                    infowindow.open(map, marker);
                });
                window.kakao.maps.event.addListener(marker, 'mouseout', () => {
                    infowindow.close();
                });

                // 마커 클릭 시 카카오맵으로 이동
                window.kakao.maps.event.addListener(marker, 'click', () => {
                    window.open(place.url || `https://map.kakao.com/link/map/${y},${x}`, '_blank');
                });
            });
        });
    }, [places]);

    if (!places || places.length === 0) return null;

    return (
        <>
            <div
                ref={mapRef}
                id={mapId}
                style={{
                    width: '100%',
                    height: '350px',
                    borderRadius: '12px',
                    marginTop: '10px',
                }}
            />
            {/* 장소 목록 */}
            <div style={{marginTop: '8px', display: 'flex', flexDirection: 'column', gap: '4px'}}>
                {places.map((place, idx) => (
                    <a
                        key={idx}
                        href={place.url || `https://map.kakao.com/link/map/${place.y},${place.x}`}
                        target="_blank"
                        rel="noreferrer"
                        style={{
                            fontSize: '13px',
                            color: '#3A6BF5',
                            textDecoration: 'none',
                            padding: '2px 0',
                        }}
                    >
                        📍 {place.name} {place.address && `— ${place.address}`}
                    </a>
                ))}
            </div>
        </>
    );
}
