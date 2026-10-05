const { app, BrowserWindow, Notification, ipcMain } = require('electron');
const path = require('path');

// 패키징 여부로 개발/배포 환경 구분
const isDev = !app.isPackaged;

let mainWindow = null;

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1280,
        height: 800,
        minWidth: 900,
        minHeight: 600,
        icon: path.join(__dirname, '../public/image/JO-GPT_login.png'),
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true,
            preload: path.join(__dirname, 'preload.js'),
        },
    });

    // 개발 중에는 Vite 서버, 배포 후엔 빌드 파일
    if (isDev) {
        mainWindow.loadURL('http://localhost:5173');
        mainWindow.webContents.openDevTools();
    } else {
        mainWindow.loadFile(path.join(__dirname, '../dist/index.html'));
    }

    mainWindow.on('closed', () => { mainWindow = null; });
}

app.whenReady().then(() => {
    createWindow();
    // macOS 독 클릭 시 창 복원
    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
});

// Windows/Linux는 창 모두 닫으면 앱 종료
app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
});

// 렌더러에서 요청한 데스크탑 알림 처리
ipcMain.on('show-notification', (_, { title, body }) => {
    if (Notification.isSupported()) {
        new Notification({ title, body }).show();
    }
});
