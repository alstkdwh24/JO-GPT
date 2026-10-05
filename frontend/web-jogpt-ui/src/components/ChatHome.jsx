import DOMPurify from 'dompurify';
import KakaoMap from './KakaoMap';
import {marked} from 'marked';
import {useEffect, useRef, useState} from 'react';
import CONFIG from '../config/config';
import {fetchWithRefresh} from '../config/tokenRefresh';

const CHUNK_SIZE = 4;
const TICK_MS = 18;

const MODEL_OPTIONS = [
    {label: 'Gemini 이미지 버전', value: 'gemini-3.1-flash-image-preview'},
    {label: "Gemini 3.5 버전", value: "gemini-3.5-flash"},
    {label: 'Gemini 3 버전', value: 'gemini-3-flash-preview'},


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
    const showChatRef = useRef(showChat); // ✅ showChat 즉시 참조용 ref

    // 리벤더는 React가 화면을 다시 그리는 것입니다.
    // useRef가 필요한 이유
    // 1. DOM에 직접 접근해야 할 때
    // 2. 타이머 ID처럼 저장만 하고 화면엔 안 보여줄 때
    // 3. 리벤더링 사이에도 값을 유지해야 할 때

    const [loadingStatus, setLoadingStatus] = useState('');

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
    const abortControllerRef = useRef(null); // 요청 취소용

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
        })
            .then(res => res.json())
            .then(data => {
                setMessages(data.map(m => ({role: m.role, content: m.content, images: []})));
                localStorage.setItem('showChat', selectedChatKey);
                setShowChat(String(selectedChatKey));
                showChatRef.current = String(selectedChatKey); // ✅ ref 즉시 업데이트
                if (onChatLoaded) onChatLoaded();
            })
            .catch(e => console.error('대화 내역 로드 실패:', e))
            .finally(() => setLoading(false));
    }, [selectedChatKey]);

    // handleKeyDowm 수정
    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            void handleSend();
        }

        // 위 화살표 - 이전입력 (커서가 맨 앞일 때만)
        if (e.key === 'ArrowUp') {
            if (e.target.selectionStart !== 0) return;
            e.preventDefault();
            if (inputHistoryRef.current.length === 0) return;
            const newIndex = Math.min(historyIndexRef.current + 1, inputHistoryRef.current.length - 1);
            historyIndexRef.current = newIndex;
            setInput(inputHistoryRef.current[newIndex]);
        }

        // 아래 화살표 - 다음 입력 (커서가 맨 끝일 때만)
        if (e.key === "ArrowDown") {
            if (e.target.selectionStart !== e.target.value.length) return;
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
        const el = e.target;
        el.style.height = 'auto';
        el.style.height = el.scrollHeight + 'px';
        // overflow-y 는 CSS max-height: 150px + overflow-y: auto 가 처리
    };
    const handleStop = () => {
        if (abortControllerRef.current) {
            abortControllerRef.current.abort();
            abortControllerRef.current = null;
        }
        clearInterval(streamIntervalRef.current);

        setMessages(prev => {
            const next = [...prev];
            const last = next[next.length - 1];

            if (last?.role === 'ai') {
                // AI 메시지가 있으면 교체
                next[next.length - 1] = { role: 'ai', content: '답변이 정지되었습니다.' };
                return next;
            } else {
                // AI 메시지 없으면 추가
                return [...next, { role: 'ai', content: '답변이 정지되었습니다.' }];
            }
        });
        setLoading(false);
        setLoadingStatus('');

    };



    const handleSend = async () => {
        const query = input.trim();
        if (!query || loading) return;
        abortControllerRef.current = new AbortController();

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
            if (messages.length === 0) { // ✅ ref로 즉시 체크
                await firstSend(myContent);
            } else {
                await continueSend(myContent, showChatRef.current); // ✅ ref 값 사용

            }
        } catch (e) {
            console.error(e);

        } finally {
            setLoading(false);
            setLoadingStatus('');
        }
    };


    const firstSend = async (myContent) => {
        // ✅ 채팅방 생성 + AI 응답 한번에 처리 (API 1번만 호출)
        const base = localStorage.getItem('CUSTOM_PROMPT')?.trim() || '';
        const context = await checkDocument(myContent);
        const customPrompt = [base, context].filter(Boolean).join('\n');

        setLoadingStatus('AI가 답변 생성 중...');
        const res = await fetchWithRefresh(`${CONFIG.API_CONTENTS_URL}/contents/chatRoom/first`, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'X-Model': selectedModel,

            },
            body: JSON.stringify({myChatContents: myContent, files: attachedFiles, customPrompt: customPrompt})
        });
        if (!res.ok) throw new Error(`채팅방 생성 실패: ${res.status}`);

        const data = await res.json(); // { chatKey, response } 한번에!
        localStorage.setItem('showChat', String(data.chatKey));
        setShowChat(String(data.chatKey));
        showChatRef.current = String(data.chatKey); // ✅ ref 즉시 업데이트

        // ✅ 받은 AI 응답 바로 스트리밍 표시
        await handleGptResponseText(data.response, data.chatKey);
    };


// 두번째 보내는 것
    const continueSend = async (myContent, chatKey) => {
        setLoadingStatus('문서 검색 중...');
        await postFetch(
            `${CONFIG.API_CONTENTS_URL}/contents/myContents`,
            {myChatContents: myContent, showChatKey: chatKey}
        );


        await fetchGptResponse(myContent, selectedModel, chatKey);
    };


    const postFetch = async (url, body) => {
        return await fetchWithRefresh(url, {
            method: 'POST',
            credentials: 'include',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body)
        });

    }



    // 문서에 있는지 확인
    const checkDocument = async (myContent) => {
        const query = Array.isArray(myContent) ? myContent.findLast(m => m.role === 'user')?.content ?? '' : myContent;
        if (!query) return null;
        // 1. 서버에 "이 질문과 관련된 문서 있어?" 라고 물어봄
        const res = await postFetch(
            `${CONFIG.API_CONTENTS_URL}/contents/documents/search`,
            {query: query}
        );
        // 2. 관련 문서가 있으면 반환, 없으면 null

        if (!res.ok) return null;
        const text = await res.text();
        return text || null;
    }
    /* gpt에 요청을 보내는 메서드*/
    const fetchGptResponse = async (myContent, model, chatKey) => {
        // RAG작업
        setLoadingStatus('문서 검색 중...');
        const context = await checkDocument(myContent);
        setLoadingStatus('AI가 답변 생성 중...');
        // 유저 프롬프트
        const base = localStorage.getItem('CUSTOM_PROMPT')?.trim() || '';
        // 유저 프롬프트 + RAG 작업으로 나온 결과를 프롬프트로 보낸다.
        const customPrompt = [base, context].filter(Boolean).join('\n');

        // GPT가 메시지를 받고 응답을 주는 요청
        const res = await fetchWithRefresh(`${CONFIG.API_CONTENTS_URL}/contents/gptContents`, {
            method: 'POST',
            // 서버의 토큰을 작동하게 하는 코드
            credentials: 'include',
            signal: abortControllerRef.current?.signal, // 추가

            // json은 데이터 그 자체
            headers: {
                'Content-Type': 'application/json',
                'X-Model': model,

            },
            // 제이슨 문자열로 변환
            body: JSON.stringify({myChatContents: myContent, showChatKey: chatKey, files: attachedFiles, customPrompt: customPrompt})
        });
        if (!res.ok) {
            setMessages(prev => [...prev, {role: 'ai', content: '서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.'}]);
            return;
        }
        setAttachedFiles([]); // 전송 후 첨부 파일 초기화

        const sendBrowserNotification = (content) => {
            if (document.hidden && 'Notification' in window && Notification.permission === 'granted') {
                new Notification('JO-GPT 답변 도착', {
                    body: content.replace(/[#*`>\-]/g, '').slice(0, 80),
                    icon: '/image/blueChatTing.png',
                });
            }
        };

        const gptText = await res.text();

        if (!abortControllerRef.current) return;

        if (gptText) {
            let fullContent = gptText;
            let images = [];
            try {
                const parsed = JSON.parse(gptText);
                if (parsed.text !== undefined && parsed.images !== undefined) {
                    fullContent = parsed.text;
                    images = parsed.images;
                }
                // eslint-disable-next-line no-unused-vars
            } catch (_e) {
                // eslint-disable-next-line no-empty
            }

            setMessages(prev => [...prev, {role: 'ai', content: '', images, streaming: true}]);

            let i = 0;
            clearInterval(streamIntervalRef.current);
            streamIntervalRef.current = setInterval(() => {
                i = Math.min(i + CHUNK_SIZE, fullContent.length);
                const chunk = fullContent.slice(0, i);
                const done = i >= fullContent.length;
                setMessages(prev => {
                    const next = [...prev];
                    next[next.length - 1] = {...next[next.length - 1], content: chunk, streaming: !done};
                    return next;
                });
                if (done) {
                    clearInterval(streamIntervalRef.current);
                    sendBrowserNotification(fullContent);
                }
            }, TICK_MS);

            if (onNotification) onNotification(fullContent.replace(/[#*`>\-]/g, '').slice(0, 50), chatKey);
        }
    };

    // ✅ AI 응답 텍스트를 스트리밍 표시하는 공통 함수
    const handleGptResponseText = async (gptText, chatKey) => {
        if (!gptText) return;
        let fullContent = gptText;
        // Google 연동 안내
        if (fullContent.trim() === "GOOGLE_NOT_CONNECTED") {
            setMessages(prev => [...prev, {
                role: "ai",
                content: "🔗 **Google 계정이 연동되지 않았어요!**\n\n- **Gmail**: 메일 조회 및 분석 발송\n- **YouTube**: 영상 요약 및 검색\n- **Google 캘린더**: 일정 조회 및 자동 등록\n\n설정에서 연동 후 다양한 기능을 이용해보세요!",
                showGoogleConnect: true
            }]);
            return;
        }

        let images = [];
        try {
            const parsed = JSON.parse(gptText);
            if (parsed.text !== undefined && parsed.images !== undefined) {
                fullContent = parsed.text;
                images = parsed.images;
            }
        } catch (_e) {
        }
        setAttachedFiles([]);
        setMessages(prev => [...prev, {role: 'ai', content: '', images, streaming: true}]);
        let i = 0;
        clearInterval(streamIntervalRef.current);
        streamIntervalRef.current = setInterval(() => {
            i = Math.min(i + CHUNK_SIZE, fullContent.length);
            const chunk = fullContent.slice(0, i);
            const done = i >= fullContent.length;
            setMessages(prev => {
                const next = [...prev];
                next[next.length - 1] = {...next[next.length - 1], content: chunk, streaming: !done};
                return next;
            });
            if (done) {
                clearInterval(streamIntervalRef.current);
            }
        }, TICK_MS);
        if (onNotification) onNotification(fullContent.slice(0, 50), chatKey);
    };

    useEffect(() => {
        if ('Notification' in window && Notification.permission === 'default') {
            Notification.requestPermission();
        }
    }, []);

    const handleFileSelect = async (e) => {
        const files = Array.from(e.target.files);
        const processed = await Promise.all(files.map(async (file) => {
            const base64 = await toBase64(file);
            return {
                name: file.name,
                mimeType: file.type,
                data: base64.split(',')[1],
                type: file.type.startsWith('image/') ? 'image' : 'file'
            };
        }));
        setAttachedFiles(prev => [...prev, ...processed]);
    };

    const toBase64 = (file) =>
        new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result);
            reader.onerror = (error) => reject(error);
            reader.readAsDataURL(file);
        });

    const handlePaste = (e) => {
        const items = Array.from(e.clipboardData?.items || []);
        const fileItems = items.filter(item => item.kind === 'file');
        if (fileItems.length === 0) return;

        e.preventDefault();
        const files = fileItems.map(item => item.getAsFile()).filter(Boolean);
        if (files.length === 0) return;

        files.forEach(file => {
            toBase64(file).then(base64 => {
                setAttachedFiles(prev => [...prev, {
                    name: file.name || `pasted-image-${Date.now()}.png`,
                    mimeType: file.type || 'image/png',
                    data: base64.split(',')[1],
                    type: file.type.startsWith('image/') ? 'image' : 'file'
                }]);
            });
        });
    };

    const handleVoice = () => {
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
        recognition.continuous = false;
        recognition.interimResults = false;
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
                                        style={{wordBreak: 'break-word', overflowWrap: 'break-word', width: '100%'}}
                                        dangerouslySetInnerHTML={{
                                            __html: DOMPurify.sanitize(
                                                marked.parse(
                                                    msg.content.replace(/\[\[MAP_START:[\s\S]*?:MAP_END]]/g, '').trim(),
                                                    {async: false}
                                                ))
                                        }}
                                    />
                                )}
                                {msg.showGoogleConnect && (
                                    <button
                                        className="google-connect-btn"
                                        onClick={() => window.location.href = CONFIG.AI_MEMBERSECURITY + "/connect/google"}
                                    >
                                        🔗 Google 계정 연동하기
                                    </button>
                                )}
                                {msg.content && msg.content.includes('[[MAP_START:') && !msg.streaming && (
                                    <KakaoMap places={(() => {
                                        try {
                                            const raw = msg.content.split('[[MAP_START:')[1].split(':MAP_END]]')[0];
                                            const parsed = JSON.parse(raw);
                                            return Array.isArray(parsed) ? parsed : [parsed];
                                        } catch {
                                            return null;
                                        }
                                    })()} mapId={`map-${i}`}/>
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
                            {/* ✅ 원형 아이콘을 wrapper로 감싸서 텍스트와 분리 */}
                            <div className="gemini-loader">
                                <div className="gemini-loader-icon">
                                    <div className="gemini-ring"></div>
                                    <span className="gemini-star">⬥</span>
                                </div>
                                {loadingStatus && (
                                    <span className="gemini-loader-text">{loadingStatus}</span>
                                )}
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
                                className={loading ? 'search-btn search-stop' : (messages.length > 0 ? 'search-real-hide' : 'search-btn search-real')}
                                onClick={loading ? handleStop : handleSend}
                            >
                                <i className={loading ? 'fa fa-stop' : 'fa fa-arrow-up'}></i>
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


