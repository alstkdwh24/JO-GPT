let buttonBlack = document.querySelector(".buttonBlack");
let buttonWhite = document.querySelector(".buttonWhite");
let modelLogin = document.querySelector(".modelLogin")
let logoutButton = document.querySelector("#logoutButton");
logoutButton.onclick = function (event) {
    console.log("logout")

    //로그아웃 요청
    $.ajax({
        type: 'GET',
        url: 'http://localhost:8086/login/logout',
        xhrFields: { withCredentials: true }, // 쿠키 포함 확인
        success: function (response) {
            localStorage.removeItem('ACCESS_TOKEN');
            localStorage.removeItem("showChat");
            console.log("로그아웃 성공:", response);
            console.log("로그아웃 완료 후 페이지 새로고침");
            document.getElementById('authButtons').style.display = 'flex';
            document.getElementById('userProfile').style.display = 'none';
            // 확실한 초기화를 원한다면 약간의 지연 후 새로고침

            // 2. 세션 데이터 삭제 요청

            // 앱이 꺼지지 않으므로,  데이터 삭제 시간을 고려해
            // 0.5 초 뒤에 페이지를 새로고침하여 로그아웃 상태를 UI에 반영합니다.
            setTimeout(() => { location.reload(); }, 500);

        },
        error: function (error) {
            console.error('Error fetching myInfo:', error);
        }
    })
}
if (buttonBlack) {
    buttonBlack.onclick = function (event) {
        event.preventDefault();
        console.log("회원가입 모달 오픈");
        modelLogin.style.display = "flex";
    }
}

if (buttonWhite) {
    buttonWhite.onclick = function (event) {
        event.preventDefault();
        console.log("로그인 모달 오픈");
        modelLogin.style.display = "flex";
    }
}

if (modelLogin) {
    modelLogin.onclick = function (event) {
        if (event.target === modelLogin) {
            event.preventDefault();
            console.log("모달 화면 닫음");
            modelLogin.style.display = "none";
        }
    }
}

// URL 파라미터에 error가 있으면 모달을 자동으로 연다
window.onload = function () {
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('error')) {
        if (modelLogin) {
            modelLogin.style.display = "flex";
        }
    }
}
