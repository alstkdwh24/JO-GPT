
const menuBar = document.querySelector('.menuBar');
const sideBar = document.querySelector('.sideBar');
const activeBar = document.querySelector('.active');
const menuActive = document.querySelector('.menuActive');
const contents = document.querySelector('.content');
const realContent = document.querySelector('.realContent');
const topBar = document.querySelector('.topBar');
const sideBarTop = document.querySelector('.sideBar-top');
const sideBarImg = document.querySelectorAll(".sideBarImg");
const sideButton = document.querySelector('.sideButton');
const sideBarBottom = document.querySelector('.sideBar-bottom');

document.addEventListener('DOMContentLoaded', () => {
    menuBar.addEventListener('click', () => {
        menuBar.classList.toggle('menuActive');
        menuBar.classList.toggle('menuBar');
        sideBar.classList.toggle('active');
        sideBar.classList.toggle('sideBar');
        contents.classList.toggle('content');
        contents.classList.toggle('contentActive');
        realContent.classList.toggle('realContent');
        realContent.classList.toggle('realContentActive');
        topBar.classList.toggle('topBar');
        topBar.classList.toggle('topBarActive');
        sideBarTop.classList.toggle('sideBarTopActive');
        sideBarTop.classList.toggle('sideBar-top');
        sideButton.classList.toggle('sideButtonActive');
        sideButton.classList.toggle('sideButton');
        sideBarBottom.classList.toggle('sideBar-bottomActive');
        sideBarBottom.classList.toggle('sideBar-bottom');


        sideBarImg.forEach(img => {
            // 1. 단일 요소인 사이드바 버튼의 상태는 반복문 외부에서 단 한 번만 토글합니다.
            if (sideButton) {
                sideButton.classList.toggle('sideButtonActive');
                sideButton.classList.toggle('sideButton');
            }
            // 2. 다수의 요소인 사이드바 아이콘(sideBarImg)들은 반복문을 돌며 각자의 상태만 토글합니다.
            if (sideBarImg && sideBarImg.length > 0) {
                sideBarImg.forEach(img => {
                    // 여기서 img는 개별 <i> 또는 <img> 요소입니다.
                    img.classList.toggle('sideBarImgActive');
                    img.classList.toggle('sideBarImg');

                    // 주의: 이곳에 img.sideButton 과 같은 코드를 넣으면 안 됩니다.
                });
            }



        });






        console.log('Menu bar clicked');
    });

});