document.addEventListener("DOMContentLoaded", function () {
    const tplToastAlert = document.getElementById("tpl-toast-alert");
    const toastAlert = tplToastAlert.querySelector(".toast-alert");
    const bellButton = document.querySelector(".sideBell");
    const body = document.body;
    // 알림 버튼 클릭 시 모달 알람 표시
    bellButton.addEventListener("click", function () {
        tplToastAlert.classList.add("show"); // show 클래스 추가
        showToastAlert("알림이 도착했습니다!");

    });

    function showToastAlert(message) {
        const toastAlert = tplToastAlert.content.cloneNode(true);
        const toastAlertText = toastAlert.querySelector(".toast-alert-text");

        toastAlertText.textContent = message;

        document.body.appendChild(toastAlert);


    }
    tplToastAlert.addEventListener("click", function (event) {
        // 배경을 클릭했을 때 (모달 내용이 아닌 경우)
        if (event.target !== toastAlert) {
            tplToastAlert.classList.remove("show");
        }
        // 모달 내용을 클릭했을 때
        else if (toastAlert.contains(event.target)) {
            // 아무것도 안 함 (모달 유지)
        }
    });


});