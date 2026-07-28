const path = require('path');
const fs = require('fs');
const https = require('https');
const http = require('http');
const { execSync } = require('child_process');
const crypto = require('crypto');

const MC_VERSION = '1.21.11';
const FABRIC_LOADER_VERSION = '0.18.4';
const FABRIC_VERSION_ID = MC_VERSION + '-rich';
const MODRINTH_FABRIC_API_ID = 'P7dR8mSH';
const JAVA_MIN_VERSION = 21;

const VERSION_MANIFEST_URL = 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json';
const FABRIC_META_URL = `https://meta.fabricmc.net/v2/versions/loader/${MC_VERSION}/${FABRIC_LOADER_VERSION}`;
const MODRINTH_API = 'https://api.modrinth.com/v2';
const MOD_RELEASE_URL = 'https://api.github.com/repos/jeosmertnik-collab/Rich-Modern/releases/latest';
const LAUNCHER_VERSION = require(path.join(__dirname, '..', 'version.json')).launcherVersion || '1.0.0';

const OS_NAME = { win32: 'windows', darwin: 'osx', linux: 'linux' }[process.platform] || 'windows';
const SEP = process.platform === 'win32' ? ';' : ':';

function downloadFile(url, dest, timeout) {
    return new Promise((resolve, reject) => {
        const client = url.startsWith('https') ? https : http;
        const req = client.get(url, { timeout: timeout || 120000 }, (res) => {
            if (res.statusCode === 301 || res.statusCode === 302) {
                return downloadFile(res.headers.location, dest, timeout).then(resolve).catch(reject);
            }
            if (res.statusCode !== 200) return reject(new Error('HTTP ' + res.statusCode + ' for ' + url));
            const ws = fs.createWriteStream(dest);
            res.pipe(ws);
            ws.on('finish', () => { ws.close(); resolve(); });
            ws.on('error', reject);
        });
        req.on('error', reject);
        req.on('timeout', () => { req.destroy(); reject(new Error('timeout')); });
    });
}

function fetchJson(url) {
    return new Promise((resolve, reject) => {
        const client = url.startsWith('https') ? https : http;
        const req = client.get(url, { timeout: 60000 }, (res) => {
            if (res.statusCode === 301 || res.statusCode === 302) {
                return fetchJson(res.headers.location).then(resolve).catch(reject);
            }
            if (res.statusCode !== 200) return reject(new Error('HTTP ' + res.statusCode + ' for ' + url));
            let data = '';
            res.on('data', (c) => data += c);
            res.on('end', () => { try { resolve(JSON.parse(data)); } catch (e) { reject(e); } });
        });
        req.on('error', reject);
        req.on('timeout', () => { req.destroy(); reject(new Error('timeout')); });
    });
}

function shouldIncludeLib(lib) {
    if (!lib.rules || lib.rules.length === 0) return true;
    let result = false;
    for (const rule of lib.rules) {
        let matches = true;
        if (rule.os) matches = rule.os.name === OS_NAME;
        if (rule.features) matches = false;
        if (matches) result = rule.action === 'allow';
    }
    return result;
}

function offlineUUID(username) {
    const hash = crypto.createHash('md5').update('OfflinePlayer:' + username).digest();
    hash[6] = (hash[6] & 0x0f) | 0x30;
    hash[8] = (hash[8] & 0x3f) | 0x80;
    return hash.toString('hex').replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/, '$1-$2-$3-$4-$5');
}

function extractZip(zipPath, destDir) {
    const lower = zipPath.toLowerCase();
    if (lower.endsWith('.tar.gz') || lower.endsWith('.tgz') || lower.endsWith('.tar')) {
        execSync(`tar -xf "${zipPath}" -C "${destDir}"`, {
            encoding: 'utf8', timeout: 120000, stdio: 'ignore'
        });
        return;
    }
    try {
        execSync(`powershell -Command "Expand-Archive -Path '${zipPath}' -DestinationPath '${destDir}' -Force"`, {
            encoding: 'utf8', timeout: 120000, stdio: 'ignore'
        });
    } catch (e) {
        execSync(`tar -xf "${zipPath}" -C "${destDir}"`, {
            encoding: 'utf8', timeout: 120000, stdio: 'ignore'
        });
    }
}

function ensureDir(dir) {
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

function fileExists(p) {
    try { return fs.existsSync(p) && fs.statSync(p).size > 0; } catch (e) { return false; }
}

class MinecraftLauncher {
    constructor(gameDir, event, log) {
        this.gameDir = gameDir;
        this.event = event;
        this.log = log;
        this.libsDir = path.join(gameDir, 'libraries');
        this.versionsDir = path.join(gameDir, 'versions');
        this.assetsDir = path.join(gameDir, 'assets');
        this.modsDir = path.join(gameDir, 'mods');
        this.nativesDir = path.join(gameDir, 'natives');
        ensureDir(this.libsDir);
        ensureDir(this.versionsDir);
        ensureDir(this.modsDir);
        ensureDir(this.nativesDir);
    }

    status(msg) {
        this.log(msg);
        this.event.reply('game:launch-status', { status: 'building', message: msg });
    }

    progress(msg) {
        this.event.reply('game:launch-progress', { status: 'downloading', message: msg });
    }

    error(msg) {
        this.log('ERROR: ' + msg);
        this.event.reply('game:launch-status', { status: 'error', message: msg });
    }

    async downloadWithRetry(url, dest, desc, retries) {
        retries = retries || 3;
        for (let i = 0; i < retries; i++) {
            try {
                this.progress('Download: ' + (desc || path.basename(dest)));
                await downloadFile(url, dest, 180000);
                return;
            } catch (e) {
                if (i === retries - 1) throw new Error('Failed to download ' + (desc || url) + ': ' + e.message);
                await new Promise(r => setTimeout(r, 2000 * (i + 1)));
            }
        }
    }

    async findJava() {
        const localJreDir = path.join(this.gameDir, 'jre');

        // Search recursively in bundled jre/ directory
        function findJavaw(dir) {
            try {
                for (const e of fs.readdirSync(dir)) {
                    const p = path.join(dir, e);
                    if (fs.statSync(p).isDirectory()) {
                        const r = findJavaw(p);
                        if (r) return r;
                    } else if (e === 'javaw.exe') {
                        return p;
                    }
                }
            } catch (e) {}
            return null;
        }

        if (fs.existsSync(localJreDir)) {
            const found = findJavaw(localJreDir);
            if (found) {
                try {
                    const out = execSync(`"${found}" -version`, { encoding: 'utf8', timeout: 10000, stdio: ['ignore', 'pipe', 'pipe'] });
                    const match = out.match(/version\s+"(\d+)/);
                    if (match && parseInt(match[1]) >= JAVA_MIN_VERSION) {
                        this.log('Found bundled JRE: ' + found);
                        return found;
                    }
                } catch (e) {}
            }
        }

        this.status('Поиск Java ' + JAVA_MIN_VERSION + '...');
        const candidates = [];

        const javaHome = process.env.JAVA_HOME;
        if (javaHome) {
            candidates.push(path.join(javaHome, 'bin', 'javaw.exe'));
            candidates.push(path.join(javaHome, 'bin', 'java.exe'));
        }

        candidates.push('javaw');
        candidates.push('java');

        const commonPaths = [
            'C:\\Program Files\\Java',
            'C:\\Program Files\\Eclipse Adoptium',
            'C:\\Program Files\\Microsoft',
            'C:\\Program Files\\Zulu',
            process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, 'Programs', 'Java') : null
        ].filter(Boolean);

        for (const base of commonPaths) {
            try {
                const entries = fs.readdirSync(base);
                for (const entry of entries) {
                    if (entry.toLowerCase().includes('jdk') || entry.toLowerCase().includes('jre')) {
                        candidates.push(path.join(base, entry, 'bin', 'javaw.exe'));
                        candidates.push(path.join(base, entry, 'bin', 'java.exe'));
                    }
                }
            } catch (e) {}
        }

        for (const javaExe of candidates) {
            try {
                const out = execSync(`"${javaExe}" -version`, { encoding: 'utf8', timeout: 10000, stdio: ['ignore', 'pipe', 'pipe'] });
                const match = out.match(/version\s+"(\d+)/);
                if (match && parseInt(match[1]) >= JAVA_MIN_VERSION) {
                    this.log('Found Java ' + match[1] + ': ' + javaExe);
                    return javaExe;
                }
            } catch (e) {}
        }

        this.status('Java 21 не найдена, скачиваю...');
        try {
            return await this.downloadJava(localJreDir, localJavaw);
        } catch (e) {
            throw new Error('Java 21 не найдена и скачать не удалось. Установите вручную: https://adoptium.net/temurin/releases/?version=21');
        }
    }

    async downloadJava(jreDir, javaExe) {
        const zipPath = path.join(this.gameDir, 'jre-temp.zip');
        const tempDir = path.join(this.gameDir, 'jre-temp-dir');
        try {
            const url = 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk';
            this.status('Скачивание Java 21 (JRE)...');
            await this.downloadWithRetry(url, zipPath, 'Java 21 JRE');

            this.status('Установка Java 21...');
            if (fs.existsSync(tempDir)) fs.rmSync(tempDir, { recursive: true, force: true });
            ensureDir(tempDir);
            extractZip(zipPath, tempDir);

            function findJavaw(dir) {
                try {
                    for (const e of fs.readdirSync(dir)) {
                        const p = path.join(dir, e);
                        if (fs.statSync(p).isDirectory()) {
                            const r = findJavaw(p);
                            if (r) return r;
                        } else if (e === 'javaw.exe') {
                            return p;
                        }
                    }
                } catch (e) {}
                return null;
            }

            const found = findJavaw(tempDir);
            if (!found) throw new Error('javaw.exe not found in extracted JRE');

            const innerRoot = path.dirname(path.dirname(found));
            if (fs.existsSync(jreDir)) fs.rmSync(jreDir, { recursive: true, force: true });
            fs.cpSync(innerRoot, jreDir, { recursive: true });

            try { fs.unlinkSync(zipPath); } catch (e) {}
            try { fs.rmSync(tempDir, { recursive: true, force: true }); } catch (e) {}

            const jreExe = path.join(jreDir, 'bin', 'javaw.exe');
            if (fileExists(jreExe)) {
                const out = execSync(`"${jreExe}" -version`, { encoding: 'utf8', timeout: 10000, stdio: ['ignore', 'pipe', 'pipe'] });
                const match = out.match(/version\s+"(\d+)/);
                if (match && parseInt(match[1]) >= JAVA_MIN_VERSION) {
                    this.log('Downloaded JRE OK: ' + jreExe);
                    return jreExe;
                }
            }

            const anyFound = findJavaw(jreDir);
            if (anyFound) return anyFound;

            throw new Error('Java 21 JRE extracted but not found at expected path');
        } catch (e) {
            try { fs.unlinkSync(zipPath); } catch (err) {}
            try { if (fs.existsSync(tempDir)) fs.rmSync(tempDir, { recursive: true, force: true }); } catch (err) {}
            try { if (fs.existsSync(jreDir)) fs.rmSync(jreDir, { recursive: true, force: true }); } catch (err) {}
            throw e;
        }
    }

    async downloadVersionJson() {
        this.status('Получение информации о Minecraft ' + MC_VERSION + '...');
        const versionDir = path.join(this.versionsDir, MC_VERSION);
        const versionJsonPath = path.join(versionDir, MC_VERSION + '.json');
        const clientJarPath = path.join(versionDir, MC_VERSION + '.jar');

        if (fileExists(versionJsonPath)) {
            this.log('Version JSON already exists');
            return JSON.parse(fs.readFileSync(versionJsonPath, 'utf8'));
        }

        const manifest = await fetchJson(VERSION_MANIFEST_URL);
        const versionEntry = manifest.versions.find(v => v.id === MC_VERSION);
        if (!versionEntry) throw new Error('Version ' + MC_VERSION + ' not found in manifest');

        const versionJson = await fetchJson(versionEntry.url);
        ensureDir(versionDir);
        fs.writeFileSync(versionJsonPath, JSON.stringify(versionJson, null, 2), 'utf8');
        return versionJson;
    }

    async downloadClientJar(versionJson) {
        const versionDir = path.join(this.versionsDir, MC_VERSION);
        const clientJarPath = path.join(versionDir, MC_VERSION + '.jar');

        if (fileExists(clientJarPath)) {
            this.log('Client JAR already exists');
            return;
        }

        this.status('Скачивание клиентского JAR Minecraft...');
        const client = versionJson.downloads && versionJson.downloads.client;
        if (!client || !client.url) throw new Error('Client JAR URL not found in version JSON');

        ensureDir(versionDir);
        await this.downloadWithRetry(client.url, clientJarPath, MC_VERSION + '.jar');
    }

    async downloadLibraries(versionJson) {
        this.status('Скачивание библиотек...');
        const libraries = versionJson.libraries || [];
        let count = 0;
        const total = libraries.length;
        const classpath = [];
        const nativeJars = [];

        for (const lib of libraries) {
            count++;
            if (!shouldIncludeLib(lib)) continue;

            if (lib.downloads && lib.downloads.artifact) {
                const art = lib.downloads.artifact;
                const dest = path.join(this.libsDir, art.path);
                if (!fileExists(dest)) {
                    ensureDir(path.dirname(dest));
                    await this.downloadWithRetry(art.url, dest, lib.name);
                }
                classpath.push(dest);
            }

            if (lib.natives && lib.natives[OS_NAME]) {
                const classifier = lib.natives[OS_NAME];
                if (lib.downloads && lib.downloads.classifiers && lib.downloads.classifiers[classifier]) {
                    const cls = lib.downloads.classifiers[classifier];
                    const dest = path.join(this.libsDir, cls.path);
                    if (!fileExists(dest)) {
                        ensureDir(path.dirname(dest));
                        await this.downloadWithRetry(cls.url, dest, lib.name + '-natives');
                    }
                    nativeJars.push(dest);
                }
            }

            if (count % 10 === 0 || count === total) {
                this.progress('Библиотеки: ' + count + '/' + total);
            }
        }

        if (nativeJars.length > 0) {
            this.status('Извлечение нативных библиотек...');
            for (const nj of nativeJars) {
                try {
                    extractZip(nj, this.nativesDir);
                } catch (e) {
                    this.log('Failed to extract natives from ' + nj + ': ' + e.message);
                }
            }
        }

        return classpath;
    }

    async downloadFabricLoader() {
        this.status('Скачивание Fabric Loader ' + FABRIC_LOADER_VERSION + '...');

        const meta = await fetchJson(FABRIC_META_URL);
        const entry = meta[0];
        if (!entry) throw new Error('Fabric loader metadata not found');

        const fabricLibs = entry.launcherMeta && entry.launcherMeta.libraries;
        if (!fabricLibs) throw new Error('Fabric loader libraries not found in metadata');

        const fabricClasspath = [];

        for (const lib of fabricLibs) {
            let url = null;
            let libPath = null;

            if (lib.client && lib.client.url) {
                url = lib.client.url;
                libPath = lib.client.path;
            } else if (lib.client && lib.client.path && lib.url) {
                url = lib.url + lib.client.path;
                libPath = lib.client.path;
            } else if (lib.url && lib.name) {
                const mavenPath = lib.name.replace(/:/g, '/') + '.jar';
                url = lib.url + mavenPath;
                libPath = mavenPath;
            }

            if (!url || !libPath) {
                this.log('Skipping library without URL: ' + lib.name);
                continue;
            }

            const dest = path.join(this.libsDir, libPath);
            if (!fileExists(dest)) {
                ensureDir(path.dirname(dest));
                await this.downloadWithRetry(url, dest, lib.name);
            }
            fabricClasspath.push(dest);
        }

        return fabricClasspath;
    }

    async createFabricVersionJson() {
        this.status('Создание конфигурации Fabric...');
        const fabricDir = path.join(this.versionsDir, FABRIC_VERSION_ID);
        const fabricJsonPath = path.join(fabricDir, FABRIC_VERSION_ID + '.json');

        if (fileExists(fabricJsonPath)) return;

        const meta = await fetchJson(FABRIC_META_URL);
        const entry = meta[0];
        const fabricLibs = (entry.launcherMeta && entry.launcherMeta.libraries) || [];

        const libraries = fabricLibs.map(lib => {
            const obj = { name: lib.name };
            if (lib.url) obj.url = lib.url;
            return obj;
        });

        const fabricJson = {
            id: FABRIC_VERSION_ID,
            inheritsFrom: MC_VERSION,
            mainClass: 'net.fabricmc.loader.impl.launch.knot.KnotClient',
            arguments: {
                jvm: [
                    `-fabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient`,
                    `-fabric.dli.env=client`
                ],
                game: []
            },
            libraries: libraries
        };

        ensureDir(fabricDir);
        fs.writeFileSync(fabricJsonPath, JSON.stringify(fabricJson, null, 2), 'utf8');
    }

    async downloadAssets(versionJson) {
        this.status('Скачивание ассетов...');
        const assetIndex = versionJson.assetIndex;
        if (!assetIndex || !assetIndex.url) {
            this.log('No asset index found, skipping assets');
            return;
        }

        const indexDir = path.join(this.assetsDir, 'indexes');
        const objectsDir = path.join(this.assetsDir, 'objects');
        ensureDir(indexDir);
        ensureDir(objectsDir);

        const indexFile = path.join(indexDir, assetIndex.id + '.json');
        if (!fileExists(indexFile)) {
            await this.downloadWithRetry(assetIndex.url, indexFile, 'asset-index-' + assetIndex.id + '.json');
        }

        let indexData;
        try {
            indexData = JSON.parse(fs.readFileSync(indexFile, 'utf8'));
        } catch (e) {
            this.log('Failed to parse asset index: ' + e.message);
            return;
        }

        const objects = indexData.objects || {};
        const entries = Object.entries(objects);
        let downloaded = 0;
        const total = entries.length;

        if (total === 0) {
            this.log('No assets to download');
            return;
        }

        this.status('Скачивание ассетов (0/' + total + ')...');

        for (const [name, obj] of entries) {
            const hash = obj.hash;
            const prefix = hash.substring(0, 2);
            const assetDir = path.join(objectsDir, prefix);
            const assetPath = path.join(assetDir, hash);

            if (!fileExists(assetPath)) {
                ensureDir(assetDir);
                const url = `https://resources.download.minecraft.net/${prefix}/${hash}`;
                try {
                    await this.downloadWithRetry(url, assetPath, name);
                } catch (e) {
                    this.log('Failed to download asset ' + name + ': ' + e.message);
                }
            }

            downloaded++;
            if (downloaded % 50 === 0 || downloaded === total) {
                this.progress('Ассеты: ' + downloaded + '/' + total);
            }
        }
    }

    async downloadFabricApi() {
        this.status('Скачивание Fabric API...');
        const dest = path.join(this.modsDir, 'fabric-api.jar');
        if (fileExists(dest)) {
            this.log('Fabric API already exists');
            return;
        }

        const versionsUrl = `${MODRINTH_API}/project/${MODRINTH_FABRIC_API_ID}/version?game_versions=%5B%22${MC_VERSION}%22%5D&loaders=%5B%22fabric%22%5D`;
        let versions;
        try {
            versions = await fetchJson(versionsUrl);
        } catch (e) {
            throw new Error('Failed to fetch Fabric API info from Modrinth: ' + e.message);
        }

        if (!versions || versions.length === 0) {
            throw new Error('No Fabric API version found for ' + MC_VERSION);
        }

        const version = versions[0];
        const file = version.files && version.files[0];
        if (!file || !file.url) throw new Error('Fabric API download URL not found');

        await this.downloadWithRetry(file.url, dest, 'Fabric API');

        if (file.filename && file.filename !== 'fabric-api.jar') {
            const realDest = path.join(this.modsDir, file.filename);
            try {
                fs.renameSync(dest, realDest);
                this.log('Renamed Fabric API to ' + file.filename);
            } catch (e) {
                try {
                    fs.copyFileSync(dest, realDest);
                    fs.unlinkSync(dest);
                } catch (e2) {}
            }
        }
    }

    async downloadModJar() {
        this.status('Скачивание Rich Modern мода...');
        const dest = path.join(this.modsDir, 'rich.jar');
        if (fileExists(dest)) {
            this.log('Mod jar already exists');
            return;
        }

        let release;
        try {
            release = await fetchJson(MOD_RELEASE_URL);
        } catch (e) {
            throw new Error('Failed to fetch latest release from GitHub: ' + e.message);
        }

        const jarAsset = release.assets && release.assets.find(a => a.name && a.name.endsWith('.jar') && a.name.startsWith('rich'));
        if (!jarAsset) throw new Error('Rich Modern JAR not found in latest release');

        await this.downloadWithRetry(jarAsset.browser_download_url, dest, jarAsset.name);
    }

    async launch(nickname, ram) {
        try {
            const javaPath = await this.findJava();
            const versionJson = await this.downloadVersionJson();
            await this.downloadClientJar(versionJson);
            const mcClasspath = await this.downloadLibraries(versionJson);
            const fabricClasspath = await this.downloadFabricLoader();
            await this.createFabricVersionJson();
            await this.downloadAssets(versionJson);
            await this.downloadFabricApi();
            await this.downloadModJar();

            this.status('Запуск игры...');

            const clientJar = path.join(this.versionsDir, MC_VERSION, MC_VERSION + '.jar');
            const allClasspath = [clientJar, ...fabricClasspath].join(SEP);

            const uuid = offlineUUID(nickname);
            const assetsIndex = versionJson.assetIndex ? versionJson.assetIndex.id : 'legacy';
            const assetsDir = path.join(this.assetsDir);

            const jvmArgs = [];
            if (ram) jvmArgs.push(`-Xmx${ram}M`, `-Xms${Math.min(parseInt(ram), 512)}M`);
            jvmArgs.push(
                '-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient',
                '-Dfabric.dli.env=client',
                '-Djava.library.path=' + this.nativesDir,
                '-Dminecraft.launcher.brand=' + LAUNCHER_VERSION,
                '-Dminecraft.launcher.version=1.0',
                '-cp', allClasspath
            );

            const gameArgs = [
                '--username', nickname,
                '--version', FABRIC_VERSION_ID,
                '--gameDir', this.gameDir,
                '--assetsDir', assetsDir,
                '--assetIndex', assetsIndex,
                '--uuid', uuid,
                '--accessToken', '0',
                '--userType', 'offline',
                '--versionType', LAUNCHER_VERSION
            ];

            this.log('java: ' + javaPath);
            this.log('classpath entries: ' + (mcClasspath.length + fabricClasspath.length));
            this.log('gameDir: ' + this.gameDir);

            const game = require('child_process').spawn(javaPath, [...jvmArgs, 'net.fabricmc.loader.impl.launch.knot.KnotClient', ...gameArgs], {
                cwd: this.gameDir,
                detached: true,
                stdio: ['ignore', 'pipe', 'pipe'],
                windowsHide: true
            });

            this.log('spawned, pid=' + game.pid);

            let stderr = '';
            game.stderr.on('data', d => { stderr += d.toString(); });
            game.stdout.on('data', d => {
                const text = d.toString();
                if (text.includes('[Fabric]') || text.includes('fabric')) {
                    this.log('[MC] ' + text.trim().substring(0, 200));
                }
            });

            game.on('error', (err) => {
                this.error('Failed to start game: ' + err.message);
            });

            game.on('close', (code) => {
                this.log('Game closed with code ' + code);
                if (code !== 0 && code !== null) {
                    const lastErr = stderr.slice(-500);
                    this.error('Game exited with code ' + code + (lastErr ? '\n' + lastErr : ''));
                } else {
                    this.event.reply('game:launch-status', { status: 'started', message: 'Game started' });
                    setTimeout(() => { try { require('electron').app.quit(); } catch (e) {} }, 3000);
                }
            });

            game.unref();
        } catch (e) {
            this.error(e.message);
        }
    }
}

module.exports = MinecraftLauncher;
