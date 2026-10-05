document.addEventListener('DOMContentLoaded', () => {
    let userName = document.getElementById('userName');
fetchMyInfo();
    function fetchMyInfo() {
        console.log();
        $.ajax({
            method: 'GET',
            url: '/login/myInfo',
            // headers에 Authorization을 넣지 않아도 브라우저가 쿠키(ACCESS_TOKEN)를 자동으로 보냅니다.
            xhrFields: {
                withCredentials: true // 크로스 도메인 상황일 때 쿠키를 포함시키는 옵션
            },
            success: function(response) {
                console.log(response);
                //이걸 통해 데이터를 가져옴(jwt 키로 security 문을 열고 db에서 데이터를 가져옴)
                userName.textContent=response.name;
                
                // 로그인 상태면 UI 전환
                document.getElementById('authButtons').style.display = 'none';
                document.getElementById('userProfile').style.display = 'flex';
            },
            error: function(error) {
                console.error('Error fetching myInfo:', error);
                // 로그인 상태가 아니면 UI 유지
                document.getElementById('authButtons').style.display = 'flex';
                document.getElementById('userProfile').style.display = 'none';
            }
        })

    }


});


