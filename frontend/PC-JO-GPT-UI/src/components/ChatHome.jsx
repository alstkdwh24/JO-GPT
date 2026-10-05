import DOMPurify from 'dompurify';
import NaverMap from './NaverMap';
import {marked} from 'marked';
import {useEffect, useRef, useState} from 'react';
import CONFIG from '../config/config';
import {fetchWithRefresh} from '../config/tokenRefresh';

const CHUNK_SIZE = 4;
const TICK_MS = 18;

const MODEL_OPTIONS = [
    {label: 'Gemini 3 Flash Image', value: 'gemini-3.1-flash-image-preview'},
    {label: 'GPT-4.5', value: 'gpt-4.5'},

];

export default function ChatHome({user, isActive, selectedChatKey, onChatLoaded, onNotification}) {

    // useRef - DOM/값 직접 참조 (리벤더링 없음, 화면에 안보이는 값)
    // textareaRef 채팅 입력창 (textarea) DOM에 직접 접근 - 포커스 주기, 높이 조절 등
    // chatContainerRsf 채팅 메시지 목록 컨테이너 - 새 메시지 올 때 자동 스크롤 처리용
    // streamIntervalRef AI 응답 스트리밍 타이머 ID 저장 - 중간에 취소할 때 clearInterval 에 씀
    // fileInputRef 숨겨진 <input type="file"> DOM 접근 - 버튼 클릭 시 파일 선택창 열기
    // recognitionRef - 음성인식 객체 저장 - 녹음 시작 / 중지 제어용


    // useState - 화면에 영향을 주는 상태 (리렌더링 있음, 화면에 보이는 값)
    // selectedModel - 현재 선택된 AI 모델
    // attachedFiles - 첨부된 파일 목록 - 파일 추가 제가 시 업데이트
    // inputHistory - 관거에 입력했던 메시지들 저장
    // historyIndex - 현재 히스토리에서 몇 번째를 보고 있는지

    // 그럼 왜 useRef와 useState로 나누었을까?? 성능 때문에 나누었습니다. 굳이 변경하지 않을 화면에 나타나지 않는 것 마저 리벤더링등을 하면 메모리 남비여서요

    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [loading, setLoading] = useState(false);
    const [showScrollBtn, setShowScrollBtn] = useState(false); // 스크롤 내리기 버튼
    const [showChat, setShowChat] = useState(() => {
        const stored = localStorage.getItem('showChat');
        if (stored && !/^\d+$/.test(stored.trim())) {
            localStorage.removeItem('showChat');
            return null;
        }
        return stored;
    });

    // 리벤더는 React가 화면을 다시 그리는 것입니다.
    // useRef가 필요한 이유
    // 1. DOM에 직접 접근해야 할 때
    // 2. 타이머 ID처럼 저장만 하고 화면엔 안 보여줄 때
    // 3. 리벤더링 사이에도 값을 유지해야 할 때


    const [selectedModel, setSelectedModel] = useState(MODEL_OPTIONS[0].value);
    const textareaRef = useRef(null);
    const chatContainerRef = useRef(null);
    const streamIntervalRef = useRef(null);
    const fileInputRef = useRef(null);
    const [isRecording, setIsRecording] = useState(false);
    const [attachedFiles, setAttachedFiles] = useState([]);
    const recognitionRef = useRef(null);
    // 입력 기록 저장용 ref (화면에 표시 안되므로 useRef 사용 → 스테일 클로저 방지)
    const inputHistoryRef = useRef([]);
    const historyIndexRef = useRef(-1);

    // 사용자가 위로 스크롤했는지 추적하는 ref
    const userScrolledUpRef = useRef(false);

    // 맨 아래로 스크롤 (chatContainer 기준)
    const scrollToBottom = () => {
        const el = chatContainerRef.current;
        if (!el) return;
        el.scrollTop = el.scrollHeight;
        userScrolledUpRef.current = false;
        setShowScrollBtn(false);
    };

    // chatContainer 스크롤 감지 → 위로 올리면 버튼 표시 / 아래 도달하면 버튼 숨기기
    useEffect(() => {
        const el = chatContainerRef.current;
        if (!el) return;
        const handleScroll = () => {
            const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 50;
            if (atBottom) {
                userScrolledUpRef.current = false;
                setShowScrollBtn(false);
            } else {
                userScrolledUpRef.current = true;
            }
        };
        el.addEventListener('scroll', handleScroll);
        return () => el.removeEventListener('scroll', handleScroll);
    }, []);

    // 스트리밍 중 새 메시지가 올 때 → 위로 올라가 있으면 버튼 표시
    useEffect(() => {
        if (userScrolledUpRef.current) {
            setShowScrollBtn(true);
        }
    }, [messages]);

    // 컴포넌트 마운트 시 스크롤 컨테이너에 overflow-anchor: none 직접 적용
    useEffect(() => {
        const el = chatContainerRef.current;
        if (el) el.style.overflowAnchor = 'none';
    }, []);

    useEffect(() => {
        return () => clearInterval(streamIntervalRef.current);
    }, []);

    useEffect(() => {
        if (!selectedChatKey) return;
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setLoading(true);
        fetchWithRefresh(`${CONFIG.API_CONTENTS_URL}/contents/chatRoom/${selectedChatKey}/messages`, {
            credentials: 'include'
            // fetch() 요청시 브라우저가 쿠키를 자동으로 함께 전송하도록 하는 옵션입니다.
            // 기본값은 'same-origin' 이라 다른 도메인으로 요청할때 쿠키가 안실려가는데, 'include'로 설정하면 cross-origin 요청에도 쿠키를 포함시킵니다.


        })
            .then(res => res.json())
            .then(data => {
                setMessages(data.map(m => ({role: m.role, content: m.content, images: []})));
                localStorage.setItem('showChat', selectedChatKey);
                setShowChat(String(selectedChatKey));
                if (onChatLoaded) onChatLoaded();
            })
            .catch(e => console.error('대화 내역 로드 실패:', e))
            .finally(() => setLoading(false));
    }, [selectedChatKey]);

    // handleKeyDowm 수정
    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }

        // 위 화살표 - 이전입력
        if (e.key === 'ArrowUp') {
            e.preventDefault();
            console.log('[ArrowUp] history:', inputHistoryRef.current, 'index:', historyIndexRef.current);
            if (inputHistoryRef.current.length === 0) return;
            const newIndex = Math.min(historyIndexRef.current + 1, inputHistoryRef.current.length - 1);
            historyIndexRef.current = newIndex;
            setInput(inputHistoryRef.current[newIndex]);
        }

        // 아래 화살표 - 다음 입력
        if (e.key === "ArrowDown") {
            e.preventDefault();
            if (historyIndexRef.current <= -1) return;
            const newIndex = historyIndexRef.current - 1;
            historyIndexRef.current = newIndex;
            if (newIndex === -1) {
                setInput('');
            } else {
                setInput(inputHistoryRef.current[newIndex]);
            }
        }
    }

    const handleInput = (e) => {
        setInput(e.target.value);
        e.target.style.height = 'auto';
        e.target.style.height = e.target.scrollHeight + 'px';
    };


    const handleSend = async () => {
        const query = input.trim();
        if (!query || loading) return;

        // 전송할 때 기록 저장
        inputHistoryRef.current = [query, ...inputHistoryRef.current];
        historyIndexRef.current = -1;
        if (!user) {

            alert('로그인 후 이용해주세요');
            return;
        }

        const myContent = query;
        setMessages(prev => [...prev, {role: 'user', content: myContent}]);
        setInput('');
        setLoading(true);
        setShowScrollBtn(false);
        // 전송 시에만 맨 아래로 스크롤
        setTimeout(() => {
            const el = chatContainerRef.current;
            if (el) el.scrollTop = el.scrollHeight;
            textareaRef.current?.focus();
        }, 0);


        if (textareaRef.current) textareaRef.current.style.height = 'auto';

        try {
            if (!showChat) {
                await firstSend(myContent);
            } else {
                await continueSend(myContent, showChat);

            }
        } catch (e) {
            console.error(e);
            setMessages(prev => [...prev, {role: 'ai', content: '오류가 발생했습니다.'}]);
        } finally {
            setLoading(false);
        }
    };


    const firstSend = async (myContent) => {
        const res = await fetchWithRefresh(`${CONFIG.API_CONTENTS_URL}/contents/chatRoom`, {
            method: 'POST',
            credentials: 'include',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({myChatContents: myContent})
        });
        if (!res.ok) throw new Error(`채팅방 생성 실패: ${res.status}`);
        const key = await res.text();
        localStorage.setItem('showChat', key);
        setShowChat(key);

        await fetchGptResponse(myContent, selectedModel, key);
    };



// 두번째 보내는 것
    const continueSend = async (myContent, chatKey) => {


        const res=await fetchWithRefresh(`${CONFIG.API_CONTENTS_URL}/contents/myContents`, {
            method: 'POST',
            credentials: 'include',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({myChatContents: myContent, showChatKey: chatKey})
        });
        if(res.ok){
            await fetchGptResponse(myContent, selectedModel, chatKey, myContent);

        }

    };
    // // 문서에 있는지 확인
    const checkDocument = async (myContent) => {
        const query = Array.isArray(myContent) ? myContent.findLast(m => m.role === 'user')?.content ?? '' : myContent;
        console.log('checkDocument', query);
        if(!query) return null;
        // 1. 서버에 "이 질문과 관련된 문서 있어?" 라고 물어봄
        const res = await fetchWithRefresh(
            `${CONFIG.API_CONTENTS_URL}/contents/documents/search`,
            {
                method: 'POST',
                credentials: 'include',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({query: query}) // 질문을 보내서 유사 문서 검색
            });
        console.log('checkDocument', res);
        // 2. 관련 문서가 있으면 반환, 없으면 null

        if (!res.ok) return null;
        const text = await res.text();
        return text || null;
    }
    /* gpt에 요청을 보내는 메서드*/
    const fetchGptResponse = async (myContent, model, chatKey) => {
        console.log('fetchGptResponse', myContent, model, chatKey);
        // RAG작업
        const context = await checkDocument(myContent);
        // 유저 프롬프트
        const base = localStorage.getItem('CUSTOM_PROMPT')?.trim() || '';
        // 유저 프롬프트 + RAG 작업으로 나온 결과를 프롬프트로 보낸다.
        const customPrompt = [base, context].filter(Boolean).join('\n');

        // GPT가 메시지를 받고 응답을 주는 요청
        const res = await fetchWithRefresh(`${CONFIG.API_CONTENTS_URL}/contents/gptContents`, {
            method: 'POST',
            // 서버의 토큰을 작동하게 하는 코드
            credentials: 'include',
            // json은 데이터 그 자체
            headers: {
                'Content-Type': 'application/json',
                'X-Model': model,
                ...(customPrompt && {'X-Custom-Prompt': encodeURIComponent(customPrompt)}),
            },
            // 제이슨 문자열로 변환
            body: JSON.stringify({myChatContents: myContent, showChatKey: chatKey, files: attachedFiles})
        });
        if (!res.ok) {
            setMessages(prev => [...prev, {role: 'ai', content: '서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.'}]);
            return;
        }
        setAttachedFiles([]); // 전송 후 첨부 파일 초기화

        // GPT 답변 알람 관련 메서드
        // Electron이면 네이티브 알림, 웹이면 브라우저 알림
        const sendBrowserNotification = (content) => {
            const body = content.replace(/[#*`>\-]/g, '').slice(0, 80);
            if (window.electronAPI?.isElectron) {
                // preload.js를 통해 메인 프로세스에 알림 요청
                window.electronAPI.showNotification('JO-GPT 답변 도착', body);
            } else if (document.hidden && 'Notification' in window && Notification.permission === 'granted') {
                new Notification('JO-GPT 답변 도착', { body, icon: '/image/blueChatTing.png' });
            }
        };
        // 다형성 처리 - 서버가 단순 텍스트만 줄수도 있고, 여러 형태의 것을 줄 수도 있다.
        // 일단 gpt 답변을 텍스트로 변환
        const gptText = await res.text();
        if (gptText) {
            let fullContent = gptText;
            // 이미지를 받기 위한 변수
            let images = [];
            try {

                // text로 변환이 된것은 맞지만 텍스트의 내용물이 JSON이여서 한번더 해석이 필요하다 text 의 글가자 JSON일 거니까
                const parsed = JSON.parse(gptText);
                if (parsed.text !== undefined && parsed.images !== undefined) {
                    // 파씽 된 것들중에 이미지 텍스트 나눔
                    fullContent = parsed.text;
                    images = parsed.images;
                }
                // eslint-disable-next-line no-unused-vars
            } catch (_e) {
                // eslint-disable-next-line no-empty
            }

            // 빈 메시지로 먼저 추가 후 타이핑 애니메이션 시작
            setMessages(prev => [...prev, {role: 'ai', content: '', images, streaming: true}]);

            let i = 0;
            clearInterval(streamIntervalRef.current); // 타이머 정리 streamIntervalRef.current를 통해 이전에 실행 중이던 타이머가 있으면 멈춤니다.
            streamIntervalRef.current = setInterval(() => {
                i = Math.min(i + CHUNK_SIZE, fullContent.length);
                const chunk = fullContent.slice(0, i);
                const done = i >= fullContent.length;
                // 상태 덮어쓰기: 배열의 맨 마지막 메시지를 찾아, 방금 잘라낸 chunk로 내용을 덮어씁니다. 이 과정이 빠르게 반복되며 타이핑되는 것처럼 보입니다.
                setMessages(prev => {
                    const next = [...prev];
                    next[next.length - 1] = {...next[next.length - 1], content: chunk, streaming: !done};
                    return next;
                });
                // 종료 처리
                if (done) {
                    clearInterval(streamIntervalRef.current);
                    sendBrowserNotification(fullContent);
                }
            }, TICK_MS);
            // 알림 처리
            fetchWithRefresh(`${CONFIG.API_CONTENTS_URL}/contents/notifications`, {
                method: 'POST',
                credentials: 'include',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({message: '"gpt 답변이 등록되었습니다."'})
            });
            if (onNotification) onNotification(fullContent.replace(/[#*`>\-]/g, '').slice(0, 50), chatKey);
        }
    };

// 이 코드의 장점
// 사용자 참여 유도 : 앱이 백그라운드에 있거나 다른 탭을 보고 있을 때도 중요한 정보를 실시간으로 전달가능
// 표준 API를 사용하여 추가 라이브러리 없이 구현 가능
// React 컴포넌트가 마운트되거나 업데이트될 떼 사이드 이펙트(Side Effect)를 수행하기위해 사용됩니다.
    useEffect(() => {
        // 브라우저가 데스크톱 알림을 지원하는지 확인하는 인터페이스
        if (window.electronAPI?.isElectron) return; // Electron은 메인 프로세스가 처리
        if (!('Notification' in window)) {
            // requestPermission() 사용자에게 알림 표시 권한을 명시작으로 요청하는 메서드. 결과 값으로 granted(허용), denied(거부), default(무응답) 중 하나를 반환합니다.
            console.log("이 브라우저는 알림을 지원하지 않습니다.");
            return;
        }

        // 2. 현재 권한 상태 확인 후 요청
        // 권한이 허용되지 않았을 때만 요청
        if (Notification.permission !== 'granted' && Notification.permission !== 'denied') {
            // requestPermission() 메서드를 사용하여 사용자에게 알림 권한을 요청합니다. 사용자가 허용하면 granted, 거부하면 denied, 아무런 응답이 없으면 default가 반환됩니다.
            Notification.requestPermission().then((permission) => {
                if (permission === 'granted') {
                    console.log("알림 권한이 허용되었습니다.");

                    new Notification("알림이 활성화되었습니다!", {
                        body: "이제 새로운 메시지가 도착하면 알림을 받을 수 있습니다.",
                        icon: '/favicon.ico'
                    });
                } else if (permission === 'denied') {
                    console.log("알림 권한이 거부되었습니다. 알림을 받을 수 없습니다.");
                }
            })
        }
    }, []); // 빈 배열을 넣어서 컴포넌트 마운트 시 1회만 실행
// 파일 이미지 처리:
    const handleFileSelect = async (e) => {

        // 파일 목록 배열 변환
        const files = Array.from(e.target.files);

        // 비동기 병렬 처리 - 모든 파일을 base64로 변환
        const processed = await Promise.all(files.map(async (file) => {
            const base64 = await toBase64(file);
            return {
                name: file.name,
                mimeType: file.type,
                data: base64.split(',')[1], // "data:image/png;base64,..."에서 실제 base64 데이터 부분만 추출
                type: file.type.startsWith('image/') ? 'image' : 'file'
            };
        }));

        // setAttachedFiles는 React에서 useState로 만든 상태를 바꾸는 Setter 함수. 첨부 파일 목록을 이걸로 바꿔줘 라고 요청하는 것입니다.
        // prev => ... 는 Previous의 약자로 수정전 파일 목록을 의미 React는 상태를 업데이트할 때 가장 최신의 상태값을 안전하게 가져오기 위해 이런 함수형 방식을 권장
        // [...prev, ...processed]는 기존 파일 목록(prev)과 새로 처리된 파일 목록(processed)을 합쳐서 새로운 배열을 만드는 코드입니다. 즉, 기존에 첨부된 파일들은 유지하면서 새로 선택한 파일들을 추가하는 형태입니다.
        setAttachedFiles(prev => [...prev, ...processed]);
    };

    const toBase64 = (file) =>
        // 약속이라고 보면 된다.
        new Promise((resolve, reject) => {
            // FileReader는 웹 브라우저에서 제공하는 API로, 파일을 읽어서 다양한 형식으로 변환할 수 있게 해주는 객체입니다. 여기서는 파일을 base64 문자열로 변환하기 위해 사용됩니다.
            const reader = new FileReader();
            // 성공 시
            reader.onload = () => resolve(reader.result);
            // 실패 시
            reader.onerror = (error) => reject(error);
            // 파일 읽기 시작
            reader.readAsDataURL(file);
        });
    // 클럽보드 이벤트 발생
    const handlePaste = (e) => {
        console.log('paste 이벤트 발생', e.clipboardData?.items);

        // e.clipboardData.items 는 배열처럼 보이지만 실제로는 유사 배열 객체입니다. 그리고 이걸 진짜 배열로 변환을 합니다.
        const items = Array.from(e.clipboardData?.items || []);
        console.log('items:', items.map(i => `kind=${i.kind} type=${i.type}`));
        // 항목들중 kind가 "file"인 것만 골라냅니다.
        const fileItems = items.filter(item => item.kind === 'file');
        if (fileItems.length === 0) return;

        e.preventDefault();
        // item.getAsFile() : DataTransferItem 객체를 자바스크립트에서 다룰수 있는 File 객체로 변환해주는 메서드입니다. 이과정을 거쳐야만 파일을 서비로 전송하거나 이미지 미리보기를 만들 수 있습니다.
        const files = fileItems.map(item => item.getAsFile()).filter(Boolean);
        if (files.length === 0) return;

        files.forEach(file => {
            // toBase64 결과 값은 보통 data:image/png;base64,iVBOR... 형식을 가진다.
            // .split(',')[1]은 앞의 메타 정보를 떼어내고 순수한 데이터 내용만 추출하기 위해 사용합니다.
            toBase64(file).then(base64 => {
                // 기존에 첨부된 파일들을 유지하면서, 새로운 파일 객체를 배열 끝에 추가합니다.
                setAttachedFiles(prev => [...prev, {
                    name: file.name || `pasted-image-${Date.now()}.png`,
                    mimeType: file.type || 'image/png',
                    data: base64.split(',')[1],
                    type: file.type.startsWith('image/') ? 'image' : 'file'
                }]);
            });
        });
    };

// 음성인식 처리:
    const handleVoice = () => {

        // 브라우저에서 사용자 목소리를 인식해 텍스트로 변환해주는 Web Speech API를 사용하기 위한 초기 설정 단계이다.
        // W3C 표준에 따른 음성 인식 인터페이스 이름입니다. (주로 최신 파이어폭스 등에서 사용) window.SpeechRecognition
        // window.webkitSpeechRecognition 구글 크롬, 사파리 등 'Webkit'엔진을 사용하는 브라우저에서 음성인식을 구현할 때 사용하는 이름입니다.
        const SpeechRecofnition = window.SpeechRecognition || window.webkitSpeechRecognition;

        if (!SpeechRecofnition) {
            alert("이 브라우저는 음성 인식을 지원하지 않습니다.");
            return;
        }

        if (isRecording) {
            recognitionRef.current?.stop();
            setIsRecording(false);
            return;
        }

        const recognition = new SpeechRecofnition();
        recognition.lang = 'ko-KR';
        // 연속성 x
        recognition.continuous = false;
        // 중간 결과 x
        recognition.interimResults = false;
        // 음식인식을 텍스트 데이터로 가져오는 역할을 한다.
        recognition.onresult = (e) => setInput(prev => prev + e.results[0][0].transcript);
        recognition.onerror = (e) => console.error("음성 인식 오류:", e);
        recognition.start();
        recognitionRef.current = recognition;
        setIsRecording(true);

    };


    return (
        <div
            className={`${isActive ? 'realContentActive' : 'realContent'}${messages.length > 0 ? ' chat-active' : ''}`}>
            <div ref={chatContainerRef}
                 className={`my-gemini-talk${messages.length > 0 ? ' my-gemini-talk-active' : ''}`}
                 style={{overflowAnchor: 'none'}}>
                {messages.map((msg, i) => (
                    msg.role === 'user' ? (
                        <div key={i} className="myContents">
                            <div id="myContent-myContent">
                                <div id="realMyContent">{msg.content}</div>
                            </div>
                        </div>
                    ) : (
                        <div key={i} className="gptContents">
                            <div id="geminiContent-geminiContent">
                                {msg.content && (
                                    <div
                                        id="realGeminiContent"
                                        className={msg.streaming ? 'streaming-cursor' : ''}
                                        dangerouslySetInnerHTML={{
                                            __html: DOMPurify.sanitize(
                                                marked.parse(msg.content.replace(/\[\[MAP:.*?\]\]/s, '').trim())
                                            )
                                        }}
                                    />
                                )}
                                {msg.content && msg.content.includes('[[MAP:') && !msg.streaming && (
                                    <NaverMap coords={(() => {
                                        try {
                                            const raw = msg.content.split('[[MAP:')[1].split(']]')[0];
                                            return JSON.parse(raw);
                                        } catch {
                                            return null;
                                        }
                                    })()} mapId={`map-${i}`} />
                                )}
                                {msg.images && msg.images.length > 0 && (
                                    <div className="gpt-image-wrap">
                                        {msg.images.map((img, idx) => (
                                            <img
                                                key={idx}
                                                src={`data:${img.mimeType};base64,${img.data}`}
                                                alt="AI 생성 이미지"
                                                className="gpt-generated-image"
                                            />
                                        ))}
                                    </div>
                                )}
                            </div>
                        </div>
                    )
                ))}
                {loading && (
                    <div className="loading" id="start-loading">
                        <div id="geminiContent-geminiContent">
                            <div className="gemini-loader">
                                <div className="gemini-ring"></div>
                                <span className="gemini-star">⬥</span>
                            </div>
                        </div>
                    </div>
                )}
            </div>

            {/* 새 답변 도착 버튼 */}
            {showScrollBtn && (
                <button className="scroll-down-btn" onClick={scrollToBottom}>
                    ↓ 새 답변이 도착했습니다
                </button>
            )}

            <div className="realBox">
                {messages.length === 0 && !loading && (
                    <div className="realBoxFont">
                        안녕하세요 <span id="userName">{user ? user.nickname : '사용자'}</span>님
                    </div>
                )}
                <div className="input-wrapper">
                    <label>
                        <textarea
                            ref={textareaRef}
                            className="fake-input"
                            placeholder="어떤것이든 물어보세요"
                            value={input}
                            onChange={handleInput}
                            onKeyDown={handleKeyDown}
                            onPaste={handlePaste}
                            autoFocus
                        />
                    </label>
                    <div className="search-div">
                        <div className="left-tools">
                            <button className="search-btn" onClick={() => fileInputRef.current.click()}>
                                <i className="fa fa-paperclip"></i>
                            </button>
                            <input
                                ref={fileInputRef}
                                type="file"
                                multiple
                                accept="image/*, audio/*, .pdf"
                                style={{display: 'none'}}
                                onChange={handleFileSelect}
                            />
                            <button className='search-btn' onClick={handleVoice}>
                                <i className={`fa ${isRecording ? 'fa-stop' : 'fa-microphone'}`}
                                   style={{color: isRecording ? '#e74c3c' : '#3A6BF5'}}/>
                            </button>
                            {attachedFiles.length > 0 && (
                                <div className='attached-preview'>
                                    {attachedFiles.map((file, idx) => (
                                        <span key={idx} className='attached-chip'>
                                            {file.type === 'image'
                                                ? <img src={`data:${file.mimeType};base64,${file.data}`} alt={file.name}
                                                       className="thumb"/>
                                                : <i className="fa fa-file"/>}
                                            <button
                                                onClick={() => setAttachedFiles(prev => prev.filter((_, j) => j !== idx))}>×</button>
                                        </span>
                                    ))}
                                </div>)}
                        </div>

                        <div className="middle-tools"></div>
                        <div className="search-result">
                            <div className="select-model-wrapper">
                                <select
                                    className="select-model"
                                    value={selectedModel}
                                    onChange={e => setSelectedModel(e.target.value)}
                                >
                                    {MODEL_OPTIONS.map(opt => (
                                        <option key={opt.value} value={opt.value}>{opt.label}</option>
                                    ))}
                                </select>
                            </div>
                            <button
                                className={messages.length > 0 ? 'search-real-hide' : 'search-btn search-real'}
                                onClick={handleSend}
                            >
                                <i className="fa fa-arrow-up"></i>
                            </button>
                        </div>
                    </div>
                </div>
                <p style={{fontFamily: 'normal', fontSize: '12px', color: '#666'}}>Gemini는 AI이며 인물 등에 관한 정보 제공 시 실수를 할 수
                    있습니다. 이 모델은 구글의 모델을 기반으로 합니다.</p>

            </div>

        </div>
    );
}


