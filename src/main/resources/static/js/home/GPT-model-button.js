let buttonBlack= document.querySelector(".buttonBlack");
let buttonWhite= document.querySelector(".buttonWhite");
let modelLogin= document.querySelector(".modelLogin")
let logoutButton= document.querySelector("#logoutButton");
logoutButton.onclick=function (event) {
    console.log("logout")

    //로그아웃 요청
    $.ajax({
        type: 'GET',
        url: '/login/logout',
        success: function (response) {

            console.log("로그아웃 성공:", response);
            document.getElementById('authButtons').style.display = 'flex';
            document.getElementById('userProfile').style.display = 'none';

        },
        error: function (error) {
            console.error('Error fetching myInfo:', error);
        }
    })
}
if (buttonBlack) {
    buttonBlack.onclick=function (event) {
        event.preventDefault();
        console.log("회원가입 모달 오픈");
        modelLogin.style.display = "flex";
    }
}

if (buttonWhite) {
    buttonWhite.onclick=function (event) {
        event.preventDefault();
        console.log("로그인 모달 오픈");
        modelLogin.style.display = "flex";
    }
}

if (modelLogin) {
    modelLogin.onclick=function (event){
        if(event.target === modelLogin) {
            event.preventDefault();
            console.log("모달 화면 닫음");
            modelLogin.style.display = "none";
        }
    }
}

// URL 파라미터에 error가 있으면 모달을 자동으로 연다
window.onload = function() {
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('error')) {
        if (modelLogin) {
            modelLogin.style.display = "flex";
        }
    }
}
