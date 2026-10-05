const {contextBridge, ipcRenderer, shell} = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
    // 외부 브라우저로 링크 열기
    openExternal: (url) => shell.openExternal(url),

    // 기존 함수들
    sendMessage: (message) => ipcRenderer.send('message', message),
    onUpdate: (callback) => ipcRenderer.on('update', (event, ...args) => callback(...args)),

    // 인증 성공 리스너 (필요 시)
    onAuthSuccess: (callback) => {
        //'auth-success' 이벤트에 등록된 모든 리스너(콜백 함수)를 제가하는 코드 그러니까 리스너가 중복 작동하는 것을 제거해준다.
        ipcRenderer.removeAllListeners('auth-success');
        // 새 리스너를 등록해준다. 이래서 항상 1개의 리스너만 유지한다. 그래서 'auth-success' 이벤트가 발생할 때마다 콜백이 중복 실행되는 것을 방지한다. 위에 것이 그리고 아래 것은 새로 생성한다.
        ipcRenderer.on('auth-success', (event, token) => callback(token));
    },
    // [추가] 세션 삭제를 위한 API 등록
    clearSession: () => ipcRenderer.send('clear-session'),

    // ==========================================
    // [추가해야 할 부분] 자동 로그인 관련 API
    // ==========================================
    saveToken: (token) => ipcRenderer.invoke('save-token', token),
    getToken: () => ipcRenderer.invoke('get-token'),
    onSessionCleared: (callback) => ipcRenderer.on('session-cleared', (event, value) => callback(value))
});