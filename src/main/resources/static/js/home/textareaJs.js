document.addEventListener('DOMContentLoaded', () => {

    /*textarea 내용 전송*/
    let textarea = document.querySelector('.fake-input');
    let searchBtn = document.querySelector('.search-real');
    searchBtn.onclick = (event) => {
        sendContents();
    }
    textarea.addEventListener("keydown", (event) => {
        event.key = "Enter";
        if(event.keyCode === 13) {
            event.preventDefault();
            sendContents();
        }
    })
});

function sendContents() {

    let textarea = document.querySelector('.fake-input');
    let realContent= document.querySelector('.realContent');
 let realBoxFont= document.querySelector('.realBoxFont');
    let gptContents = textarea.value
    console.log(textarea.value);

    try {

/*db에 대화내용 저장*/
            $.ajax({
                method: 'POST',
                url: '/gptApi/gptContents',
                data: JSON.stringify({gptContents: gptContents}),
                contentType: 'application/json',
                success: function (response) {
                    console.log(response);
                    textarea.value = "";
                    realContent.style.alignItems = "flex-end";
                    realBoxFont.textContent="";

                 /*gpt 대화 누적을 위한 */
                    $.ajax({
                        method: 'GET',
                        url: '/gptApi/gptContents',
                        success: function (response) {
                            console.log(response);
                        }
                    })
                }
            })

    } catch (error) {

    }
}