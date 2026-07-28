const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const https = require('https');
const http = require('http');
const { spawn, execSync } = require('child_process');
const MinecraftLauncher = require('./mc-launcher');

let win;

const LOG_FILE = path.join(app.getPath('userData'), 'launch.log');

function log(msg) {
    const line = `[${new Date().toISOString()}] ${msg}\n`;
    try { fs.appendFileSync(LOG_FILE, line); } catch (e) {}
    console.log(line.trim());
}

const USERS_FILE = path.join(app.getPath('userData'), 'users.json');
const LICENSE_FILE = path.join(app.getPath('userData'), 'license.json');
const LICENSE_DB_FILE = path.join(app.getPath('userData'), 'licenses.json');
const VERSION_URL = 'https://raw.githubusercontent.com/jeosmertnik-collab/Rich-Modern/main/version.json';
const LICENSE_API_URL = 'http://localhost:3000/api/validate';
const LOCAL_VERSION_FILE = path.join(app.getPath('userData'), 'version.json');
const LICENSE_SECRET = 'rich-modern-secret-2026';

function generateLicenseKey(plan, days, email, nick) {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let seed = 0;
    const str = plan + days + email + nick + LICENSE_SECRET;
    for (let i = 0; i < str.length; i++) {
        seed = ((seed << 5) - seed + str.charCodeAt(i)) | 0;
    }
    function rand() {
        seed = (seed * 1103515245 + 12345) & 0x7fffffff;
        return seed;
    }
    function segment() {
        let s = '';
        for (let i = 0; i < 4; i++) s += chars[rand() % chars.length];
        return s;
    }
    const planCode = { stable: 'ST', beta: 'BT', alpha: 'AL' }[plan];
    const daysCode = days.toString(16).toUpperCase().padStart(2, '0');
    return `RM-${planCode}${daysCode}-${segment()}-${segment()}-${segment()}`;
}

function validateKeyLocal(key, hwid) {
    if (!key || key.length < 22) return { valid: false, error: 'Invalid key format' };

    const parts = key.split('-');
    if (parts.length !== 5 || parts[0] !== 'RM') return { valid: false, error: 'Invalid key format' };

    const planMap = { ST: 'stable', BT: 'beta', AL: 'alpha' };
    const planPart = parts[1].substring(0, 2);
    const daysHex = parts[1].substring(2, 4);
    const plan = planMap[planPart];
    if (!plan) return { valid: false, error: 'Unknown plan' };

    const days = parseInt(daysHex, 16);
    if (isNaN(days) || days <= 0) return { valid: false, error: 'Invalid duration' };

    const db = loadLicenseDB();
    let entry = db[key];

    if (!entry) {
        entry = { plan, days, createdAt: Date.now(), expiresAt: Date.now() + days * 86400000, hwid: null };
        db[key] = entry;
        saveLicenseDB(db);
    }

    if (entry.hwid && entry.hwid !== hwid) {
        return { valid: false, error: 'Key bound to another device' };
    }

    const now = Date.now();
    if (now > entry.expiresAt) return { valid: false, error: 'Key expired' };

    if (hwid) { entry.hwid = hwid; saveLicenseDB(db); }

    return { valid: true, plan: entry.plan, daysTotal: entry.days, expiresAt: entry.expiresAt };
}

function loadLicenseDB() {
    try { if (fs.existsSync(LICENSE_DB_FILE)) return JSON.parse(fs.readFileSync(LICENSE_DB_FILE, 'utf8')); } catch (e) {}
    return {};
}

function saveLicenseDB(db) {
    try { const dir = path.dirname(LICENSE_DB_FILE); if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true }); fs.writeFileSync(LICENSE_DB_FILE, JSON.stringify(db, null, 2)); } catch (e) {}
}

function startLicenseServer() {
    try {
        const server = http.createServer((req, res) => {
            res.setHeader('Content-Type', 'application/json');
            res.setHeader('Access-Control-Allow-Origin', '*');
            res.setHeader('Access-Control-Allow-Methods', 'POST, GET, OPTIONS');
            res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
            if (req.method === 'OPTIONS') { res.writeHead(200); res.end(); return; }

            if (req.url === '/api/validate' && req.method === 'POST') {
                let body = '';
                req.on('data', chunk => body += chunk);
                req.on('end', () => {
                    try {
                        const { key, hwid } = JSON.parse(body);
                        const result = validateKeyLocal(key, hwid);
                        res.writeHead(result.valid ? 200 : 403);
                        res.end(JSON.stringify(result));
                    } catch (e) {
                        res.writeHead(400);
                        res.end(JSON.stringify({ error: 'Invalid request' }));
                    }
                });
            } else if (req.url === '/api/plans' && req.method === 'GET') {
                res.writeHead(200);
                res.end(JSON.stringify({
                    stable: { name: 'Stable', prices: { 7: 1.49, 30: 2.99, 90: 7.65, 180: 13.46, 365: 21.53 } },
                    beta:   { name: 'Beta',   prices: { 7: 2.99, 30: 5.99, 90: 15.27, 180: 26.96, 365: 43.13 } },
                    alpha:  { name: 'Alpha',  prices: { 7: 4.99, 30: 9.99, 90: 25.48, 180: 44.96, 365: 71.93 } }
                }));
            } else {
                res.writeHead(404);
                res.end(JSON.stringify({ error: 'Not found' }));
            }
        });

        server.listen(3000, '127.0.0.1', () => {
            console.log('License server started on port 3000');
        });

        server.on('error', (err) => {
            console.log('License server port 3000 busy, retrying...');
            setTimeout(() => { try { server.listen(3000, '127.0.0.1'); } catch (e) {} }, 5000);
        });
    } catch (e) {
        console.log('Failed to start license server:', e.message);
    }
}

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

function loadLicense() {
    try {
        if (fs.existsSync(LICENSE_FILE)) {
            return JSON.parse(fs.readFileSync(LICENSE_FILE, 'utf8'));
        }
    } catch (e) {}
    return null;
}

function saveLicense(license) {
    try {
        const dir = path.dirname(LICENSE_FILE);
        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
        fs.writeFileSync(LICENSE_FILE, JSON.stringify(license, null, 2), 'utf8');
    } catch (e) {}
}

function getHardwareId() {
    const os = require('os');
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.mac && iface.mac !== '00:00:00:00:00:00') {
                return iface.mac.replace(/:/g, '').toUpperCase();
            }
        }
    }
    return 'UNKNOWN';
}

function getGameDataDir() {
    const dir = path.join(app.getPath('userData'), 'game');
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    return dir;
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
    return path.join(app.getPath('userData'), '.minecraft');
});

ipcMain.handle('game:getGameDataDir', () => {
    return getGameDataDir();
});

ipcMain.handle('game:selectMinecraftDir', async () => {
    const result = await dialog.showOpenDialog(win, {
        title: 'Выберите папку .minecraft',
        properties: ['openDirectory'],
        defaultPath: process.env.PORTABLE_EXECUTABLE_DIR || process.env.USERPROFILE || '',
    });
    if (result.canceled || result.filePaths.length === 0) return null;
    return result.filePaths[0];
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

// === SUBSCRIPTION HANDLERS ===

ipcMain.handle('license:activate', async (event, { key }) => {
    const hwid = getHardwareId();
    const result = validateKeyLocal(key, hwid);
    if (result.valid) {
        saveLicense({ key, plan: result.plan, expiresAt: result.expiresAt, activatedAt: Date.now(), hwid });
        return { success: true, plan: result.plan, expiresAt: result.expiresAt };
    }
    return { success: false, error: result.error };
});

ipcMain.handle('license:get', () => {
    return loadLicense();
});

ipcMain.handle('license:remove', () => {
    try { fs.unlinkSync(LICENSE_FILE); } catch (e) {}
    return true;
});

ipcMain.on('game:launch', async (event, { nickname, ram }) => {
    log('=== LAUNCH START (Direct) ===');

    killExistingGameProcesses(log);

    const gameDir = path.join(app.getPath('userData'), '.minecraft');
    const launcher = new MinecraftLauncher(gameDir, event, log);
    await launcher.launch(nickname, ram);
});

function killExistingGameProcesses(log) {
    try {
        const out = execSync('tasklist /FI "IMAGENAME eq java.exe" /FO CSV /NH', { encoding: 'utf8', timeout: 5000 });
        const lines = out.split('\n').filter(l => l.includes('java.exe'));
        for (const line of lines) {
            const match = line.match(/"java\.exe","(\d+)"/);
            if (match) {
                const pid = parseInt(match[1]);
                try {
                    execSync(`taskkill /PID ${pid} /F`, { encoding: 'utf8', timeout: 5000 });
                    log('killed old java process PID=' + pid);
                } catch (e) {}
            }
        }
        if (lines.length === 0) log('no existing java processes found');
    } catch (e) {
        log('tasklist failed: ' + e.message);
    }
}



function downloadFile(url, dest) {
    return new Promise((resolve, reject) => {
        const client = url.startsWith('https') ? https : http;
        client.get(url, { timeout: 120000 }, (res) => {
            if (res.statusCode === 301 || res.statusCode === 302) {
                return downloadFile(res.headers.location, dest).then(resolve).catch(reject);
            }
            if (res.statusCode !== 200) return reject(new Error('HTTP ' + res.statusCode));
            const fileStream = fs.createWriteStream(dest);
            res.pipe(fileStream);
            fileStream.on('finish', () => { fileStream.close(); resolve(); });
            fileStream.on('error', reject);
        }).on('error', reject);
    });
}

function selfUpdate() {
    return new Promise(async (resolve) => {
        try {
            const remoteJson = await fetchUrl(VERSION_URL);
            const remote = JSON.parse(remoteJson);
            const local = loadLocalVersion();
            const remoteLauncherVer = remote.launcherVersion || remote.version || '0.0.0';
            const localLauncherVer = local.launcherVersion || local.version || '0.0.0';

            const rParts = remoteLauncherVer.split('.').map(Number);
            const lParts = localLauncherVer.split('.').map(Number);
            let needUpdate = false;
            for (let i = 0; i < 3; i++) {
                const r = rParts[i] || 0;
                const l = lParts[i] || 0;
                if (r > l) { needUpdate = true; break; }
                if (r < l) break;
            }

            if (!needUpdate || !remote.launcherUrl) return resolve(false);

            const originalDir = process.env.PORTABLE_EXECUTABLE_DIR || '';
            const downloadedPath = path.join(app.getPath('temp'), 'RichModern-update.exe');

            await downloadFile(remote.launcherUrl, downloadedPath);

            if (originalDir && fs.existsSync(path.join(originalDir, 'RichModern.exe'))) {
                try {
                    fs.copyFileSync(downloadedPath, path.join(originalDir, 'RichModern.exe'));
                    fs.unlinkSync(downloadedPath);
                } catch (e) {}
            }

            local.launcherVersion = remoteLauncherVer;
            saveLocalVersion(local);

            app.quit();
            resolve(true);
        } catch (e) {
            console.log('Self-update check failed:', e.message);
            resolve(false);
        }
    });
}

ipcMain.handle('vk:getToken', () => {
    const tokenFile = path.join(app.getPath('userData'), '.minecraft', 'Rich', 'configs', 'vk_token.txt');
    try {
        if (fs.existsSync(tokenFile)) {
            return fs.readFileSync(tokenFile, 'utf8').trim();
        }
    } catch (e) {}
    return '';
});

ipcMain.handle('vk:removeToken', () => {
    const tokenFile = path.join(app.getPath('userData'), '.minecraft', 'Rich', 'configs', 'vk_token.txt');
    try {
        if (fs.existsSync(tokenFile)) fs.unlinkSync(tokenFile);
        log('VK token removed');
    } catch (e) {}
});

ipcMain.handle('vk:login', async () => {
    const VK_APP_ID = '3140623';
    const redirectUri = 'https://oauth.vk.com/blank.html';
    const scope = 'audio,offline';
    const authUrl = `https://oauth.vk.com/authorize?client_id=${VK_APP_ID}&display=page&redirect_uri=${redirectUri}&scope=${scope}&response_type=token&v=5.131&revoke=1`;

    return new Promise((resolve) => {
        const authWin = new BrowserWindow({
            width: 800, height: 600,
            title: 'VK Login',
            webPreferences: { nodeIntegration: false, contextIsolation: true }
        });

        authWin.loadURL(authUrl);

        authWin.webContents.on('will-redirect', (event, url) => {
            if (url.startsWith(redirectUri) && url.includes('access_token=')) {
                const fragment = url.split('#')[1] || '';
                const params = new URLSearchParams(fragment);
                const token = params.get('access_token');
                if (token) {
                    const tokenFile = path.join(app.getPath('userData'), '.minecraft', 'Rich', 'configs', 'vk_token.txt');
                    try {
                        const dir = path.dirname(tokenFile);
                        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
                        fs.writeFileSync(tokenFile, token, 'utf8');
                        log('VK token saved to ' + tokenFile);
                        resolve(token);
                        authWin.close();
                        return;
                    } catch (e) {
                        log('VK token save error: ' + e.message);
                    }
                }
                resolve('');
                authWin.close();
            }
        });

        authWin.on('closed', () => resolve(''));
    });
});

app.whenReady().then(() => {
    startLicenseServer();
    createWindow();
    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
});
