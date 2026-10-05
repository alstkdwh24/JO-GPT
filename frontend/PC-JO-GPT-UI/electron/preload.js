const { contextBridge, ipcRenderer } = require('electron');

// React에서 window.electronAPI 로 접근 가능하게 노출
// contextBridge: 보안상 렌더러에서 Node.js 직접 접근 차단하고 필요한 것만 노출
contextBridge.exposeInMainWorld('electronAPI', {
    showNotification: (title, body) => {
        ipcRenderer.send('show-notification', { title, body });
    },
    isElectron: true,
});
