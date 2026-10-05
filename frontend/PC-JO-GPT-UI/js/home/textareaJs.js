
document.addEventListener('DOMContentLoaded', () => {

    // 토큰 유효성 검사 함수
    function getValidToken() {
        const raw = localStorage.getItem('ACCESS_TOKEN');
        const token = (raw || '').trim();
        // JWT 형식: header.payload.signature (점 2개)
        if (!token || token.split('.').length !== 3) {
            localStorage.removeItem('ACCESS_TOKEN'); // 오염된 값 정리
            localStorage.removeItem("showChat");
            return null;
        }
        else {
            return token;
        }
    }
    let showChat = localStorage.getItem('showChat');
    let realBoxFont = document.querySelector('.realBoxFont');
    const myGemini = document.querySelector(".my-gemini-talk")
    let realBox = document.querySelector('.realBox');
    const gptGeminiTalk = document.querySelector('.my-gemini-talk');
    const tpl = document.getElementById('tpl-gpt-content');
    const myGeminiTalk = document.querySelector('.my-gemini-talk');
    const tplLoading = document.getElementById('tpl-loading');
    const token = getValidToken();
    console.log("tokenssss {}", token);

    console.log(CONFIG.CUSTOM_SCHEME);
    /*textarea 내용 전송*/
    let textarea = document.querySelector('.fake-input');
    if (textarea) {
        textarea.focus();
    }
    let searchBtn = document.querySelector('.search-real');

    searchBtn.onclick = (event) => {
        if (searchBtn.classList.contains('search-real-hide')) {
            /*첫번째랑 구분할 필요가 있습니다.*/
            twoSendContents(showChat)
        } else {
            sendContents();
        }
    }
    textarea.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            if (searchBtn.classList.contains('search-real-hide')) {
                /*첫번째랑 구분할 필요가 있습니다.*/
                twoSendContents(showChat)
            } else {
                sendContents();
            }
        }
    })

    function sendContents() {

        if (!textarea.value.trim()) {
            return;
        }
        const token = getValidToken();
        console.log("tokensssss:", token);


        if (token != null) {
            textarea.style.height = 'auto'; // 높이 초기화 추가
            myGemini.style.gap = '1rem';
            myGemini.style.height = '80%';
            myGemini.style.width = '44rem';
            myGemini.style.display = 'flex';
            myGemini.style.flexDirection = 'column';
            myGemini.style.justifyContent = 'flex-start';
            realBox.style.display = 'flex';
            realBox.style.alignItems = 'center';
            realBox.style.justifyContent = 'center';
            realBox.style.minHeight = '1rem';
            realBoxFont.style.display = "none";
            realBoxFont.style.height = '0px';
            textarea.focus(); // 전송 후 다시 포커스
            realBoxFont.textContent = "";


            const myContents = textarea.value;
            //말풍선 생성
            MyContents(myContents);
            textarea.value = "";
            sendContentsAjax(myContents, token)
        } else {
            alert("로그인 후 이용해주세요");

        }
    }

    //내 대화를 말풍선으로 보여주기 위한
    function MyContents(myContents) {
        const myGeminiTalk = document.querySelector('.my-gemini-talk');
        const tpl = document.getElementById('tpl-my-content');
        const clone = tpl.content.cloneNode(true);
        clone.querySelector('#realMyContent').textContent = myContents; // ✅ XSS 안전    console.log("myContent {}", myContents);
        myGeminiTalk.appendChild(clone);
    }

    //gpt 대화를 말품선으로 보여주기 위한
    function GPTContents(gptContents) {
        //template 요소의 content를 복제하여 새로운 노드 생성
        const clone = tpl.content.cloneNode(true)
        //DOMPurity는 xss(크로스 사이트 스크립팅) 공격을 방지하기 위한 HTML 새니타이저 라이브러리입니다. 쉽게 말해 악성 스크립트를 제거해주는 도구 입니다.
        clone.querySelector('#realGeminiContent').innerHTML = DOMPurify.sanitize(marked.parse(gptContents)) // 마크다운은 innerHTML 필요
        gptGeminiTalk.appendChild(clone);
    }

    /*로딩 보여주기*/
    function showLoading() {

        const clone = tplLoading.content.cloneNode(true);
        myGeminiTalk.appendChild(clone);
        document.getElementById('start-loading').scrollIntoView({ behavior: 'smooth' });
    }

    /*로딩 제거*/
    function hideLoading() {
        const loading = document.getElementById('start-loading');
        if (loading) {
            loading.remove();
        }
    }

    /*ajax 연속 코드 가독성 살린 것 내가 쓴 메시지 보내고 gpt응답받는 코드*/
    async function sendContentsAjax(myContents, token) {
        if (!token) {
            alert("로그인 후 이용해주세요");
            return;
        }
        try {
            const response = await fetchSendMessage(token, myContents);
            if (response) {
                searchBtn.classList.remove('search-real');
                searchBtn.classList.add('search-real-hide');
            }
        } catch (e) {
            hideLoading();
            console.error(e);
        }
    }

    async function fetchSendMessage(token, myContents) {
        console.log("request token =", myContents, "dotCount=", (token || "").split(".").length - 1);

        console.log("fetchSendMessage 토큰:", token);
        showLoading();
        // 3. 채팅방 생성
        const response2 = await fetch(CONFIG.API_CONTENTS_URL + '/contents/chatRoom', {
            method: "POST",
            headers: {
                'Authorization': `Bearer ` + token,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ myChatContents: myContents })
        })
        const showChatKey = await response2.text(); // json() 대신 text()
        console.log("showChatKey {}", showChatKey);
        localStorage.setItem('showChat', showChatKey);
        return gptResponse(myContents, token)
    }

    /*채팅방 만들어지고 메시지 보낼때*/
    function twoSendContents(showChat) {
        console.log("showChatss {}", showChat);
        const token = getValidToken();


        if (!textarea.value.trim()) {
            return;
        }
        if (token != null) {
            textarea.focus(); // 전송 후 다시 포커스
            realBoxFont.textContent = "";

            const myContents = textarea.value;
            //말풍선 생성
            MyContents(myContents);
            textarea.value = "";
            twoSendContentsAjax(token, myContents, showChat).then(response => {
                return "success";
            }).catch(error => {
                return "error"
            });
        }
    }

    async function twoSendContentsAjax(token, myContents, showChat) {

        showLoading();
        const response3 = await fetch(CONFIG.API_CONTENTS_URL + '/contents/myContents', {
            method: "POST",
            headers: {
                'Authorization': `Bearer ${token}`, // 키 이름은 Authorization, 값은 Bearer 한 칸 띄우고 토큰
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                myChatContents: myContents
                , showChatKey: showChat
            })
        });
        let gptAnswer = response3.json();
        console.log("gptAnswer {}", gptAnswer);
        if (!response3.ok) {
            throw new Error(`HTTP error! status: ${response3.status}`);
        }
        const response = gptResponse(myContents, token).then(response => {
            hideLoading();
            return response;
        }).catch(error => {
            return "error"
        });
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
    }

    async function gptResponse(myContents, token) {
        console.log("gptResponse 토큰:", token);
        const response = await fetch(CONFIG.API_CONTENTS_URL + '/contents/gptContents', {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ myChatContents: myContents })

        });
        const data = await response.json(); // 본문을 JSON으로 파씽을 해야됨
        // 1. 먼저 필요한 데이터를 꺼냄
        const gptText = data.candidates[0].content.parts[0].text;
        if (!gptText) {
            hideLoading();
            console.log("GPT 응답 형식 오류:", data);
            return;
        }
        const realMessage = "\"gpt 답변이 등록되었습니다.\"";
        GPTContents(gptText);
        await fetch(CONFIG.API_CONTENTS_URL + '/contents/notifications', {
            method: "POST",
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'  // 이것도 필요
            },
            body: JSON.stringify({
                message: realMessage
            })
        })
        hideLoading();

        console.log(gptText);
        return gptText;
    }
});


