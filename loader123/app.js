const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const https = require('https');
const http = require('http');
const { spawn } = require('child_process');

let win;

const USERS_FILE = path.join(app.getPath('userData'), 'users.json');
const VERSION_URL = 'https://raw.githubusercontent.com/jeosmertnik-collab/Rich-Modern/main/version.json';
const LOCAL_VERSION_FILE = path.join(app.getPath('userData'), 'version.json');

function loadUsers() {
    try {
        if (fs.existsSync(USERS_FILE)) {
            return JSON.parse(fs.readFileSync(USERS_FILE, 'utf8'));
        }
    } catch (e) {}
    return {};
}

function saveUsers(users) {
    try {
        const dir = path.dirname(USERS_FILE);
        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
        fs.writeFileSync(USERS_FILE, JSON.stringify(users, null, 2), 'utf8');
    } catch (e) {}
}

function findGameRoot() {
    const exeDir = path.dirname(app.getPath('exe'));
    const possiblePaths = [
        path.join(exeDir, '..', '..', '..'),
        exeDir,
        process.cwd(),
        path.join('C:', 'Users', process.env.USERNAME || 'Oxeo', 'Desktop', 'Rich-Modern'),
    ];
    for (const p of possiblePaths) {
        if (fs.existsSync(path.join(p, 'gradlew.bat'))) return p;
    }
    return null;
}

function createWindow() {
    win = new BrowserWindow({
        width: 960,
        height: 600,
        resizable: false,
        frame: false,
        webPreferences: {
            nodeIntegration: true,
            contextIsolation: false
        }
    });
    win.loadFile('index.html');
}

ipcMain.on('window-minimize', () => { if (win) win.minimize(); });
ipcMain.on('window-close', () => { if (win) win.close(); });

ipcMain.on('open-avatar-dialog', (event) => {
    dialog.showOpenDialog(win, {
        title: 'Выберите аватарку',
        defaultPath: app.getPath('pictures'),
        buttonLabel: 'Открыть',
        filters: [{ name: 'Изображения', extensions: ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'] }],
        properties: ['openFile']
    }).then(result => {
        if (!result.canceled && result.filePaths.length > 0) {
            event.reply('selected-avatar', result.filePaths[0]);
        }
    }).catch(err => console.log(err));
});

ipcMain.handle('auth:register', (event, { login, password }) => {
    const users = loadUsers();
    if (users[login]) return { success: false, error: 'User already exists' };
    users[login] = { password, registeredAt: Date.now() };
    saveUsers(users);
    return { success: true };
});

ipcMain.handle('auth:login', (event, { login, password }) => {
    const users = loadUsers();
    if (!users[login]) return { success: false, error: 'User not found' };
    if (users[login].password !== password) return { success: false, error: 'Wrong password' };
    return { success: true, user: { login } };
});

ipcMain.handle('game:findRoot', () => {
    return findGameRoot();
});

function loadLocalVersion() {
    try {
        if (fs.existsSync(LOCAL_VERSION_FILE)) {
            return JSON.parse(fs.readFileSync(LOCAL_VERSION_FILE, 'utf8'));
        }
    } catch (e) {}
    return { version: '0.0.0', clientVersion: '0.0.0' };
}

function saveLocalVersion(data) {
    try {
        const dir = path.dirname(LOCAL_VERSION_FILE);
        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
        fs.writeFileSync(LOCAL_VERSION_FILE, JSON.stringify(data, null, 2), 'utf8');
    } catch (e) {}
}

function fetchUrl(url) {
    return new Promise((resolve, reject) => {
        const client = url.startsWith('https') ? https : http;
        client.get(url, { timeout: 10000 }, (res) => {
            if (res.statusCode === 301 || res.statusCode === 302) {
                return fetchUrl(res.headers.location).then(resolve).catch(reject);
            }
            if (res.statusCode !== 200) {
                reject(new Error('HTTP ' + res.statusCode));
                return;
            }
            let data = '';
            res.on('data', chunk => { data += chunk; });
            res.on('end', () => resolve(data));
            res.on('error', reject);
        }).on('error', reject);
    });
}

ipcMain.handle('update:check', async () => {
    try {
        const remoteJson = await fetchUrl(VERSION_URL);
        const remote = JSON.parse(remoteJson);
        const local = loadLocalVersion();

        const remoteVersion = remote.version || '0.0.0';
        const localVersion = local.version || '0.0.0';

        const remoteParts = remoteVersion.split('.').map(Number);
        const localParts = localVersion.split('.').map(Number);

        let hasUpdate = false;
        for (let i = 0; i < 3; i++) {
            const r = remoteParts[i] || 0;
            const l = localParts[i] || 0;
            if (r > l) { hasUpdate = true; break; }
            if (r < l) break;
        }

        return {
            hasUpdate,
            version: remoteVersion,
            clientVersion: remote.clientVersion || '',
            downloadUrl: remote.downloadUrl || '',
            launcherUrl: remote.launcherUrl || '',
            changelog: remote.changelog || {},
            required: remote.required || false,
            releaseDate: remote.releaseDate || ''
        };
    } catch (e) {
        return { hasUpdate: false, error: e.message };
    }
});

ipcMain.on('update:download', (event, { url, targetPath }) => {
    const dir = path.dirname(targetPath);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });

    const tempPath = targetPath + '.downloading';

    function doDownload(downloadUrl) {
        const client = downloadUrl.startsWith('https') ? https : http;
        client.get(downloadUrl, { timeout: 60000 }, (res) => {
            if (res.statusCode === 301 || res.statusCode === 302) {
                doDownload(res.headers.location);
                return;
            }
            if (res.statusCode !== 200) {
                event.reply('update:download-status', { status: 'error', message: 'HTTP ' + res.statusCode });
                return;
            }

            const totalBytes = parseInt(res.headers['content-length'], 10) || 0;
            let receivedBytes = 0;

            const fileStream = fs.createWriteStream(tempPath);

            res.on('data', (chunk) => {
                receivedBytes += chunk.length;
                fileStream.write(chunk);
                if (totalBytes > 0) {
                    const percent = Math.round((receivedBytes / totalBytes) * 100);
                    event.reply('update:download-status', {
                        status: 'downloading',
                        percent,
                        receivedBytes,
                        totalBytes
                    });
                } else {
                    event.reply('update:download-status', {
                        status: 'downloading',
                        percent: -1,
                        receivedBytes,
                        totalBytes: 0
                    });
                }
            });

            res.on('end', () => {
                fileStream.end(() => {
                    try {
                        if (fs.existsSync(targetPath)) fs.unlinkSync(targetPath);
                        fs.renameSync(tempPath, targetPath);

                        const local = loadLocalVersion();
                        local.version = event._updateVersion || local.version;
                        local.clientVersion = event._updateClientVersion || local.clientVersion;
                        local.lastUpdated = Date.now();
                        saveLocalVersion(local);

                        event.reply('update:download-status', { status: 'done', message: 'Update downloaded' });
                    } catch (e) {
                        event.reply('update:download-status', { status: 'error', message: e.message });
                    }
                });
            });

            res.on('error', (e) => {
                fileStream.end();
                try { if (fs.existsSync(tempPath)) fs.unlinkSync(tempPath); } catch (ex) {}
                event.reply('update:download-status', { status: 'error', message: e.message });
            });
        }).on('error', (e) => {
            event.reply('update:download-status', { status: 'error', message: e.message });
        });
    }

    doDownload(url);
});

ipcMain.handle('update:setVersion', (event, { version, clientVersion }) => {
    const local = loadLocalVersion();
    if (version) local.version = version;
    if (clientVersion) local.clientVersion = clientVersion;
    local.lastUpdated = Date.now();
    saveLocalVersion(local);
    return true;
});

ipcMain.handle('update:getLocalVersion', () => {
    return loadLocalVersion();
});

ipcMain.on('game:launch', (event, { nickname, ram }) => {
    const root = findGameRoot();
    if (!root) {
        event.reply('game:launch-status', { status: 'error', message: 'Game root not found (gradlew.bat)' });
        return;
    }

    const nickFile = path.join(root, 'Rich', 'configs', 'lastnick.txt');
    try {
        const nickDir = path.dirname(nickFile);
        if (!fs.existsSync(nickDir)) fs.mkdirSync(nickDir, { recursive: true });
        fs.writeFileSync(nickFile, nickname, 'utf8');
    } catch (e) {}

    event.reply('game:launch-status', { status: 'building', message: 'Building client...' });

    const jarPath = path.join(root, 'build', 'libs', 'rich-1.0.01.jar');
    const modsDir = path.join(root, 'run', 'mods');

    function copyJarAndLaunch() {
        try {
            const destJar = path.join(modsDir, 'rich-1.0.01.jar');
            if (!fs.existsSync(modsDir)) fs.mkdirSync(modsDir, { recursive: true });
            if (fs.existsSync(jarPath)) {
                fs.copyFileSync(jarPath, destJar);
            }
        } catch (e) {}

        event.reply('game:launch-status', { status: 'launching', message: 'Launching game...' });

        const gradlewArgs = ['--no-daemon', 'runClient'];
        const env = Object.assign({}, process.env);
        if (ram) {
            env.GRADLE_OPTS = `-Xmx${ram}M`;
            env.JAVA_OPTS = `-Xmx${ram}M -Xms${Math.min(parseInt(ram), 512)}M`;
        }

        const game = spawn('cmd.exe', ['/c', 'gradlew.bat', ...gradlewArgs], {
            cwd: root,
            env: env,
            windowsHide: false,
            stdio: ['ignore', 'pipe', 'pipe']
        });

        let gameStderr = '';
        let gameStdout = '';
        game.stderr.on('data', d => { gameStderr += d.toString(); });
        game.stdout.on('data', d => { gameStdout += d.toString(); });

        event.reply('game:launch-status', { status: 'started', message: 'Game started' });

        setTimeout(() => {
            if (win) { win.hide(); }
        }, 3000);

        let hasError = false;
        game.stderr.on('data', (d) => {
            const text = d.toString();
            if (text.includes('Exception') || text.includes('Error') || text.includes('error:')) {
                hasError = true;
                event.reply('game:launch-status', { status: 'error', message: 'Java: ' + text.slice(0, 300) });
            }
        });

        game.on('close', (code) => {
            if (win) { win.show(); }
            if (code !== 0 && !hasError) {
                const errOutput = (gameStderr + gameStdout).slice(-500);
                event.reply('game:launch-status', { status: 'error', message: 'Exit code ' + code + ': ' + errOutput });
            } else {
                event.reply('game:launch-status', { status: 'closed', message: 'Game closed' });
            }
        });
    }

    if (fs.existsSync(jarPath)) {
        copyJarAndLaunch();
    } else {
        const build = spawn('cmd.exe', ['/c', 'gradlew.bat', 'build', '--no-daemon'], {
            cwd: root,
            windowsHide: true,
            stdio: ['ignore', 'pipe', 'pipe']
        });

        let buildOutput = '';
        build.stdout.on('data', d => { buildOutput += d.toString(); });
        build.stderr.on('data', d => { buildOutput += d.toString(); });

        build.on('close', (code) => {
            if (code !== 0) {
                event.reply('game:launch-status', { status: 'error', message: 'Build failed (code ' + code + '): ' + buildOutput.slice(-500) });
                return;
            }
            copyJarAndLaunch();
        });
    }
});

app.whenReady().then(() => {
    createWindow();
    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
});
