const { app, BrowserWindow, Menu, globalShortcut, ipcMain, session, safeStorage } = require('electron');
const path = require('path');
const { jwtDecode } = require('jwt-decode');
const electronReload = require("electron-reload");
const fs = require('fs');
const TOKEN_FILE_PATH = path.join(app.getPath('userData'), 'auth-token.dat');
const config = require('./js/config/config.js');
//프로토콜 이름
const CUSTOM_SCHEME = config.CUSTOM_SCHEME;
let pendingToken = null; // 전달되지 못한 토큰 임시 보관
let mainWindow;


//Dev tools 켜기
const isDev = app.isPackaged;
if (!isDev) {
    require('electron-reload')(__dirname, {
        // node_modules 폴더 내부에 있는 electron 실행 파일을 지정하여, 
        // 메인 프로세스 코드가 수정되었을 때 앱을 완전히 재시작하도록 설정합니다.
        electron: path.join(__dirname, 'node_modules', '.bin', 'electron')
    });
}


if (!app.requestSingleInstanceLock()) {//Electron앱이 중복실행 되는 것을 방지하고, 하나의 인스턴스만 뜨도록 보장하는 역할
    app.quit(); //엡을 닫는다.
    process.exit(0); //프로세스 종료
} else {
    app.on('second-instance', (event, commandLine) => {
        //Electron 앱이 이미 실행중일때 사용자가 앱을 한번 더 실행하려고 하거나 외부 링크(딥링크)를 통해 앱을 호출하려고 할때 발생하는 이벤트 처림
        console.log("second-instance{}", event);
        if (mainWindow) {
            if (mainWindow.isMinimized())
                // 창이 최소화되어 있다면 원래 크기로 복구합니다.
                mainWindow.restore();
            //창을 최상단으로 가져와 사용자에게 보여줍니다.
            mainWindow.focus();
            // Windows에서 전달된 URL 확인 (소셜 로그인 토큰 처리)
            const url = commandLine.find((arg) => arg.startsWith(CUSTOM_SCHEME + '://'));
            console.log("second-instance{}", url);

            if (url) handleCustomProtocol(url);
        }
    });
    app.whenReady().then(() => {
        createWindow(); // 1. 준비가 되면 창을 만든다.

        // 이 앱을 'jo-gpt://'프로토콜의 기본 처리기로 등록한다.
        if (process.defaultApp) {
            if (process.argv.length >= 2) {
                const appPath = path.resolve('.');
                // app.setAsDefaultProtocolClient(CUSTOM_SCHEME, process.execPath, [appPath]);
            }
        } else {
            // app.setAsDefaultProtocolClient(CUSTOM_SCHEME);
        }

        // 3. 앱이 꺼져 있을 때 링크로 실행되었다면(Cold Start), 그 정보를 처리한다. 앱을 강제로 깨워서 실행시킨다.
        const startUrl = process.argv.find(arg => arg.startsWith(CUSTOM_SCHEME + '://'));
        if (startUrl) {
            // 창이 로딩될 시간을 주기 위해 약간의 지연 후 실행
            setTimeout(() => handleCustomProtocol(startUrl), 100);
        }
    });

}

function handleCustomProtocol(url) {
    console.log("good:", url);

    // [수정] getAllWindows 대신 전역 변수 mainWindow 사용
    if (!mainWindow) {
        console.log(">>> mainWindow 없음, 토큰 보류");
        pendingToken = url;  // 나중에 창이 생기면 처리
        return;
    }

    try {
        const urlObj = new URL(url.replace('jo-gpt://', 'http://localhost:8086/', 'http://agentcloudllm.me/')); // 프로토콜을 임시로 http로 바꿔서 URL 객체로 파싱
        const token = urlObj.searchParams.get('token');
        const error = urlObj.searchParams.get('error');

        // handleCustomProtocol 함수 내부 수정
        if (token) {
            if (mainWindow.webContents.isLoading()) {
                // 1. 아직 로딩 중이라면: 로딩 완료 후 전송
                mainWindow.webContents.once('did-finish-load', () => {
                    console.log("로딩 완료 후 토큰 전송");
                    mainWindow.webContents.send('auth-success', token);
                });
            } else {
                console.log("즉시 토큰 전송 시도 (약간의 지연 추가)");

                setTimeout(() => {
                    if (mainWindow && !mainWindow.isDestroyed()) {
                        mainWindow.webContents.send('auth-success', token);
                    } else {
                        console.error("mainWindow가 파괴되어 토큰 전송 불가");
                    }
                }, 100);
            }
        } else {
            mainWindow.loadFile('index.html');
        }
    } catch (e) {
        console.error("URL 파싱 에러:", e);
    }
}

function createWindow() {
    // [수정] const를 빼고 전역 변수 mainWindow에 할당
    mainWindow = new BrowserWindow({
        width: 1440,
        height: 1024,
        title: "JO-GPT PC Client",
        webPreferences: {
            // 렌더러 프로세스(브라우저 창 내의 JavaScript)가 Node.js의 API를 사용할수 있을지에 대한 여부를 설정
            nodeIntegration: false,
            // Electron의 API와 Preload 스크립트를 사용하여 보안 격리를 유지할지 여부를 설정
            contextIsolation: true,
            // 렌더러 프로세스를 Chromium 샌트박스 내에서 실행할지 여부를 결정합니다.
            sandbox: false,
            preload: path.join(__dirname, 'preload.js'), // 여기서 이미 연결됨!
            // [추가 옵션] 샌드박스 환경에서 간혹 발생하는 렌더링/통신 이슈 방지
            spellcheck: false, // 맞춤법 검사 비활성화 (샌드박스 환경에서 간혹 발생하는 렌더링/통신 이슈 방지)
            webviewTag: false, // 웹뷰 태그 사용 금지 (보안 강화)
            devTools: true // 개발자 도구 사용 허용 (기본값 true)
        }
    });

    mainWindow.webContents.on('will-navigate', (event, url) => {
        if (url.startsWith('jo-gpt://')) {
            event.preventDefault();
            handleCustomProtocol(url);
        }
    });

    mainWindow.webContents.setWindowOpenHandler(({ url }) => {
        if (url.startsWith('jo-gpt://')) {
            handleCustomProtocol(url);
            return { action: 'deny' };
        }// 2. http나 https로 시작하는 모든 외부 요청은 무조건 시스템 브라우저로 엽니다.
        // 이렇게 하면 앱 내부에 "Login with OAuth 2.0" 같은 창이 뜨는 것을 방지할 수 있습니다.
        if (url.startsWith('http:') || url.startsWith('https:')) {
            const { shell } = require('electron');
            shell.openExternal(url).then(r => console.log("External URL opened:", url)).catch(e => console.error("Failed to open external URL:", e));
            return { action: 'deny' }; // 앱 내에서 새 창이 열리는 것을 차단
        }

        return { action: 'deny' }; // 그 외의 모든 새 창 요청 차단

    });

    mainWindow.maximize();
    Menu.setApplicationMenu(null);
    mainWindow.loadFile('index.html');

    mainWindow.webContents.on('did-finish-load', () => {
        if (pendingToken) {
            handleCustomProtocol(pendingToken);
            pendingToken = null;
        }
    })
    // 수정안
    if (!app.isPackaged) {
        mainWindow.webContents.openDevTools();
    }
    // 단축키 등록 (창이 활성화된 상태에서만 동작하도록)
    registerShortcuts();
}

// 1. 토큰 암호화 및 저장
ipcMain.handle('save-token', (event, token) => {
    if (safeStorage.isEncryptionAvailable()) {
        try {
            const encrypted = safeStorage.encryptString(token);
            fs.writeFileSync(TOKEN_FILE_PATH, encrypted);
            return true;
        } catch (e) {
            console.error("토큰 저장 에러:", e);
            return false;
        }
    }
    return false;
});


// 2. 토큰 복호화 및 불러오기
ipcMain.handle('get-token', () => {
    if (fs.existsSync(TOKEN_FILE_PATH) && safeStorage.isEncryptionAvailable()) {
        try {
            const encrypted = fs.readFileSync(TOKEN_FILE_PATH);
            return safeStorage.decryptString(encrypted);
        } catch (e) {
            console.error("토큰 불러오기 에러:", e);
            return null;
        }
    }
    return null;
});

// 3. 로그아웃 시 기존 세션 및 저장된 토큰 파일까지 완전 삭제
ipcMain.on('clear-session', async (event) => {
    try {
        // 모든 쿠키 및 캐시 데이터 삭제
        await session.defaultSession.clearStorageData();
        await session.defaultSession.clearCache();

        // [추가] 로컬에 저장된 암호화 토큰 파일도 물리적으로 삭제
        if (fs.existsSync(TOKEN_FILE_PATH)) {
            fs.unlinkSync(TOKEN_FILE_PATH);
        }

        console.log("세션 데이터 및 자동 로그인 토큰이 완전히 삭제되었습니다.");

        // 앱을 끄지 않고, 처리가 끝났음을 화면(React)에 알림
        event.reply('session-cleared', true);

    } catch (e) {
        console.error("세션 삭제 중 에러 발생:", e);
    }
});

function registerShortcuts() {
    globalShortcut.unregisterAll(); // 중복 등록 방지

    /*앱 리로딩*/
    globalShortcut.register('F5', () => {
        if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.reload();
    });


    globalShortcut.register('CommandOrControl+A', () => {
        if (mainWindow) mainWindow.loadFile('index.html');
    });
    // 개발자 도구 열기
    if (!app.isPackaged) {
        globalShortcut.register('CommandOrControl+F12', () => {
            mainWindow.webContents.openDevTools();
        })
    }
}

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
});