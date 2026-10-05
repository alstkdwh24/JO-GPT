// 메인 프로세스로 메시지 전송
window.electronAPI.sendMessage('Hello from renderer!')

// 메인 프로세스로부터 메시지 수신
window.electronAPI.onUpdate((data) => {
    console.log(data)
})
