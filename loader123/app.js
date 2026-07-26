const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const https = require('https');
const http = require('http');
const { spawn } = require('child_process');

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

function findGameRoot() {
    log('findGameRoot called');
    log('PORTABLE_EXECUTABLE_DIR=' + (process.env.PORTABLE_EXECUTABLE_DIR || 'UNSET'));
    log('app.getPath(exe)=' + app.getPath('exe'));
    log('cwd=' + process.cwd());

    const saved = loadSavedGameRoot();
    if (saved) {
        log('Saved game root: ' + saved);
        try {
            if (fs.existsSync(path.join(saved, 'gradlew.bat'))) {
                log('Saved root has gradlew.bat, using it');
                return saved;
            }
            log('Saved root missing gradlew.bat, searching...');
        } catch (e) {}
    }

    const home = process.env.USERPROFILE || process.env.HOME || '';
    const desktop = home ? path.join(home, 'Desktop') : '';
    const appData = process.env.APPDATA || '';

    const searchDirs = [
        process.env.PORTABLE_EXECUTABLE_DIR || '',
        path.dirname(app.getPath('exe')),
        process.cwd(),
        desktop,
        desktop ? path.join(desktop, 'Rich-Modern') : '',
        desktop ? path.join(desktop, 'Rich Modern') : '',
        'C:\\Users\\Oxeo\\Desktop\\Rich-Modern',
        'C:\\Users\\Oxeo\\Desktop\\Rich Modern',
        appData ? path.join(appData, '..', 'Desktop', 'Rich-Modern') : '',
        appData ? path.join(appData, '..', 'Desktop', 'Rich Modern') : '',
    ];

    for (const startDir of searchDirs) {
        if (!startDir) continue;
        let dir = startDir;
        for (let i = 0; i < 10; i++) {
            if (!dir || dir.length < 3) break;
            try {
                if (fs.existsSync(path.join(dir, 'gradlew.bat'))) {
                    log('Found gradlew.bat at: ' + dir);
                    saveGameRoot(dir);
                    return dir;
                }
            } catch (e) {}
            const parent = path.dirname(dir);
            if (parent === dir) break;
            dir = parent;
        }
    }
    log('findGameRoot: NOT FOUND');
    return null;
}

const GAME_ROOT_FILE = path.join(app.getPath('userData'), 'gameroot.json');

function loadSavedGameRoot() {
    try {
        if (fs.existsSync(GAME_ROOT_FILE)) {
            const data = JSON.parse(fs.readFileSync(GAME_ROOT_FILE, 'utf8'));
            if (data.path && fs.existsSync(data.path)) return data.path;
        }
    } catch (e) {}
    return null;
}

function saveGameRoot(rootPath) {
    try {
        const dir = path.dirname(GAME_ROOT_FILE);
        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
        fs.writeFileSync(GAME_ROOT_FILE, JSON.stringify({ path: rootPath }, null, 2), 'utf8');
    } catch (e) {}
}

const MC_PATH_FILE = path.join(app.getPath('userData'), 'mcpath.json');

function loadCustomMcPath() {
    try {
        if (fs.existsSync(MC_PATH_FILE)) {
            const data = JSON.parse(fs.readFileSync(MC_PATH_FILE, 'utf8'));
            if (data.path && fs.existsSync(data.path)) return data.path;
        }
    } catch (e) {}
    return null;
}

function saveCustomMcPath(mcPath) {
    try {
        const dir = path.dirname(MC_PATH_FILE);
        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
        fs.writeFileSync(MC_PATH_FILE, JSON.stringify({ path: mcPath }, null, 2), 'utf8');
    } catch (e) {}
}

function findMinecraftDir() {
    const custom = loadCustomMcPath();
    if (custom) return custom;

    const appData = process.env.APPDATA;
    const home = process.env.USERPROFILE || process.env.HOME || '';
    const localAppData = process.env.LOCALAPPDATA || '';

    const candidates = [
        appData ? path.join(appData, '.minecraft') : null,
        home ? path.join(home, '.minecraft') : null,
        localAppData ? path.join(localAppData, '.minecraft') : null,
        appData ? path.join(appData, 'TLauncher', '.minecraft') : null,
        appData ? path.join(appData, 'HMCL', '.minecraft') : null,
        home ? path.join(home, 'AppData', 'Roaming', '.minecraft') : null,
        home ? path.join(home, 'AppData', 'Local', 'Programs', '.minecraft') : null,
        'C:\\Program Files\\.minecraft',
        'C:\\Program Files (x86)\\.minecraft',
        'D:\\.minecraft',
        'E:\\.minecraft',
        'D:\\Minecraft\\.minecraft',
        'E:\\Minecraft\\.minecraft',
    ];

    for (const c of candidates) {
        if (c && fs.existsSync(c)) return c;
    }
    return null;
}

function findModsDir() {
    const mcDir = findMinecraftDir();
    if (!mcDir) return null;
    const modsDir = path.join(mcDir, 'mods');
    if (!fs.existsSync(modsDir)) fs.mkdirSync(modsDir, { recursive: true });
    return modsDir;
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
    return findGameRoot();
});

ipcMain.handle('game:getGameDataDir', () => {
    return getGameDataDir();
});

ipcMain.handle('game:selectMinecraftDir', async () => {
    const result = await dialog.showOpenDialog(win, {
        title: 'Выберите папку .minecraft',
        properties: ['openDirectory'],
        defaultPath: process.env.APPDATA || '',
    });
    if (result.canceled || result.filePaths.length === 0) return null;
    const selected = result.filePaths[0];
    const hasVersions = fs.existsSync(path.join(selected, 'versions'));
    const hasMods = fs.existsSync(path.join(selected, 'mods'));
    if (hasVersions || hasMods || fs.existsSync(path.join(selected, 'fabric-api*'))) {
        saveCustomMcPath(selected);
        return selected;
    }
    saveCustomMcPath(selected);
    return selected;
});

ipcMain.handle('game:getMinecraftDir', () => {
    return findMinecraftDir();
});

ipcMain.handle('game:resetMinecraftPath', () => {
    try { fs.unlinkSync(MC_PATH_FILE); } catch (e) {}
    return findMinecraftDir();
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

ipcMain.on('game:launch', (event, { nickname, ram }) => {
    const logFile = path.join(app.getPath('userData'), 'launch.log');
    const log = (msg) => { const line = `[${new Date().toISOString()}] ${msg}\n`; fs.appendFileSync(logFile, line); };
    log('=== LAUNCH START ===');
    log('exe: ' + app.getPath('exe'));
    log('cwd: ' + process.cwd());
    log('PORTABLE_EXECUTABLE_DIR: ' + (process.env.PORTABLE_EXECUTABLE_DIR || 'NOT SET'));
    log('USERPROFILE: ' + (process.env.USERPROFILE || 'NOT SET'));
    log('APPDATA: ' + (process.env.APPDATA || 'NOT SET'));

    const root = findGameRoot();
    log('findGameRoot: ' + root);

    const mcDir = findMinecraftDir();
    log('findMinecraftDir: ' + mcDir);

    const nickFile = (() => {
        const targetDir = root ? root : (mcDir || path.join(app.getPath('userData')));
        const nf = path.join(targetDir, 'Rich', 'configs', 'lastnick.txt');
        try {
            const nd = path.dirname(nf);
            if (!fs.existsSync(nd)) fs.mkdirSync(nd, { recursive: true });
            fs.writeFileSync(nf, nickname, 'utf8');
        } catch (e) {}
        return nf;
    })();
    log('nickFile: ' + nickFile);

    if (root) {
        launchViaGradlew(event, root, nickname, ram, log);
    } else if (mcDir) {
        launchViaMinecraft(event, mcDir, nickname, ram, log);
    } else {
        log('ERROR: Neither gradlew.bat nor .minecraft found');
        event.reply('game:launch-status', { status: 'error', message: 'Minecraft not found. Press "Select Folder" to choose .minecraft path manually.' });
    }
});

function launchViaGradlew(event, root, nickname, ram, log) {
    const env = Object.assign({}, process.env);
    if (ram) {
        env.GRADLE_OPTS = `-Xmx${ram}M`;
        env.JAVA_OPTS = `-Xmx${ram}M -Xms${Math.min(parseInt(ram), 512)}M`;
        log('RAM: ' + ram);
    }

    const localJre = path.join(root, 'jre', 'bin', 'java.exe');
    const tempJre = path.join(process.env.TEMP || '', 'jdk21', 'jdk-21.0.2', 'bin', 'java.exe');

    if (fs.existsSync(localJre)) {
        env.JAVA_HOME = path.join(root, 'jre');
        log('Java: local jre');
    } else if (fs.existsSync(tempJre)) {
        env.JAVA_HOME = path.join(process.env.TEMP || '', 'jdk21', 'jdk-21.0.2');
        log('Java: temp jdk21');
    } else {
        log('Java: using system PATH');
    }

    event.reply('game:launch-status', { status: 'launching', message: 'Launching game...' });

    const game = spawn('cmd.exe', ['/c', 'gradlew.bat', '--no-daemon', 'runClient'], {
        cwd: root,
        env: env,
        detached: true,
        stdio: ['ignore', 'pipe', 'pipe'],
        windowsHide: true
    });

    log('spawn returned, pid: ' + game.pid);

    let stderr = '';
    let stdout = '';
    game.stdout.on('data', d => { stdout += d.toString(); });
    game.stderr.on('data', d => { stderr += d.toString(); });

    game.on('error', (err) => {
        log('SPAWN ERROR: ' + err.message);
        event.reply('game:launch-status', { status: 'error', message: 'Spawn error: ' + err.message });
    });

    game.on('close', (code) => {
        log('process closed, code=' + code);
        if (win) { win.show(); }
        if (code !== 0) {
            event.reply('game:launch-status', { status: 'error', message: 'Exit ' + code + ': ' + stderr.slice(-300) });
        } else {
            event.reply('game:launch-status', { status: 'closed', message: 'Game closed' });
        }
    });

    game.unref();
    event.reply('game:launch-status', { status: 'started', message: 'Game started' });
    setTimeout(() => { if (win) { win.hide(); } }, 3000);
}

function launchViaMinecraft(event, mcDir, nickname, ram, log) {
    log('Launching via .minecraft directory');

    const gameDataDir = getGameDataDir();
    const jarName = 'rich-1.0.01.jar';
    const sourceJar = path.join(gameDataDir, jarName);
    const modsDir = path.join(mcDir, 'mods');
    const targetJar = path.join(modsDir, jarName);

    if (!fs.existsSync(modsDir)) {
        try { fs.mkdirSync(modsDir, { recursive: true }); } catch (e) {}
    }

    if (fs.existsSync(sourceJar)) {
        try {
            if (!fs.existsSync(targetJar) || fs.statSync(sourceJar).mtimeMs > fs.statSync(targetJar).mtimeMs) {
                fs.copyFileSync(sourceJar, targetJar);
                log('Copied JAR to mods folder');
            }
        } catch (e) {
            log('Copy error: ' + e.message);
        }
    }

    const versionsDir = path.join(mcDir, 'versions');
    let fabricJar = null;
    let fabricVersion = null;

    if (fs.existsSync(versionsDir)) {
        const versions = fs.readdirSync(versionsDir).filter(v => v.toLowerCase().includes('fabric'));
        if (versions.length > 0) {
            fabricVersion = versions.sort().reverse()[0];
            const versionDir = path.join(versionsDir, fabricVersion);
            const jsonFiles = fs.readdirSync(versionDir).filter(f => f.endsWith('.json'));
            if (jsonFiles.length > 0) {
                fabricJar = path.join(versionDir, fabricVersion + '.jar');
            }
        }
    }

    let javaExe = 'javaw';
    const localJre = path.join(mcDir, '..', 'jre', 'bin', 'javaw.exe');
    const gameJre = path.join(mcDir, 'jre', 'bin', 'javaw.exe');

    if (fs.existsSync(localJre)) javaExe = localJre;
    else if (fs.existsSync(gameJre)) javaExe = gameJre;

    event.reply('game:launch-status', { status: 'launching', message: 'Launching via Minecraft...' });

    if (fabricVersion) {
        log('Found Fabric version: ' + fabricVersion);
        const versionDir = path.join(versionsDir, fabricVersion);
        const jsonPath = path.join(versionDir, fabricVersion + '.json');

        try {
            const versionJson = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));
            const mainClass = versionJson.mainClass || 'net.fabricmc.loader.impl.launch.knot.KnotClient';
            const classpath = (versionJson.classpath || []).map(p => {
                const libPath = path.join(mcDir, 'libraries', ...p.split('/'));
                return libPath;
            });

            const gameJar = path.join(versionDir, fabricVersion + '.jar');
            if (!fs.existsSync(gameJar)) {
                log('Game jar not found: ' + gameJar);
                event.reply('game:launch-status', { status: 'error', message: 'Fabric version jar not found. Run Minecraft with Fabric first.' });
                return;
            }

            const cpStr = [...classpath, gameJar].join(';');

            const env = Object.assign({}, process.env);
            const jvmArgs = [
                `-Xmx${ram || 2048}M`,
                `-Xms${Math.min(parseInt(ram || 2048), 512)}M`,
                `-Djava.library.path=${path.join(mcDir, 'versions', fabricVersion, 'natives')}`,
                `-cp`, cpStr,
                mainClass,
                '--username', nickname,
                '--version', fabricVersion,
                '--gameDir', mcDir,
                '--assetsDir', path.join(mcDir, 'assets'),
            ];

            log('Java: ' + javaExe);
            log('MainClass: ' + mainClass);

            const game = spawn(javaExe, jvmArgs, {
                cwd: mcDir,
                env: env,
                detached: true,
                stdio: ['ignore', 'pipe', 'pipe'],
                windowsHide: true
            });

            let stderr = '';
            game.stderr.on('data', d => { stderr += d.toString(); });

            game.on('error', (err) => {
                log('SPAWN ERROR: ' + err.message);
                event.reply('game:launch-status', { status: 'error', message: 'Java error: ' + err.message });
            });

            game.on('close', (code) => {
                log('process closed, code=' + code);
                if (win) { win.show(); }
                if (code !== 0) {
                    event.reply('game:launch-status', { status: 'error', message: 'Exit ' + code + ': ' + stderr.slice(-300) });
                } else {
                    event.reply('game:launch-status', { status: 'closed', message: 'Game closed' });
                }
            });

            game.unref();
            event.reply('game:launch-status', { status: 'started', message: 'Game started' });
            setTimeout(() => { if (win) { win.hide(); } }, 3000);
        } catch (e) {
            log('Failed to parse Fabric version json: ' + e.message);
            event.reply('game:launch-status', { status: 'error', message: 'Failed to read Fabric version: ' + e.message });
        }
    } else {
        log('No Fabric version found');
        event.reply('game:launch-status', { status: 'error', message: 'Fabric not found. Install Fabric for Minecraft 1.21.11 first.' });
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
