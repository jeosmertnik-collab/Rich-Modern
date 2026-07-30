const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = 3000;
const DB_FILE = path.join(__dirname, 'licenses.json');
const SECRET = 'rich-modern-secret-2026';
// Optional: sync keys to launcher's DB so they work without manual import
// Set this to the full path of the launcher's licenses.json (e.g. %APPDATA%/excel-client-launcher/.minecraft/licenses.json)
const LAUNCHER_DB_FILE = process.env.LAUNCHER_DB_PATH || '';

function loadDB() {
    if (!fs.existsSync(DB_FILE)) return {};
    return JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
}

function saveDB(db) {
    fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2));
}

function generateRandomSegment() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    const buf = crypto.randomBytes(4);
    let s = '';
    for (let i = 0; i < 4; i++) {
        s += chars[buf[i] % chars.length];
    }
    return s;
}

function generateLicenseKey(plan, days, email, nick) {
    return `RM-${generateRandomSegment()}-${generateRandomSegment()}-${generateRandomSegment()}`;
}

function validateKey(key) {
    const db = loadDB();
    const entry = db[key];
    if (!entry) return { valid: false, error: 'Key not found' };

    const now = Date.now();
    if (now > entry.expiresAt) return { valid: false, error: 'Key expired' };

    return {
        valid: true,
        plan: entry.plan,
        daysTotal: entry.days,
        expiresAt: entry.expiresAt,
        email: entry.email,
        nick: entry.nick
    };
}

function saveLicenseEntry(key, plan, days, email, nick) {
    const entry = {
        plan,
        days,
        email,
        nick,
        createdAt: Date.now(),
        expiresAt: days === 9999 ? Date.now() + 3650 * 86400000 : Date.now() + days * 86400000,
        hwid: null
    };
    // Save to own DB
    const db = loadDB();
    db[key] = entry;
    saveDB(db);
    // Also save to launcher DB if configured
    if (LAUNCHER_DB_FILE) {
        try {
            const dir = path.dirname(LAUNCHER_DB_FILE);
            if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
            let ldb = {};
            if (fs.existsSync(LAUNCHER_DB_FILE)) {
                ldb = JSON.parse(fs.readFileSync(LAUNCHER_DB_FILE, 'utf8'));
            }
            ldb[key] = entry;
            fs.writeFileSync(LAUNCHER_DB_FILE, JSON.stringify(ldb, null, 2));
        } catch (e) {
            console.error('[license] Failed to write to launcher DB:', e.message);
        }
    }
    return entry;
}

function createLicense(plan, days, email, nick) {
    const key = generateLicenseKey(plan, days, email, nick);
    saveLicenseEntry(key, plan, days, email, nick);
    return key;
}

const MIME_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css',
    '.js': 'application/javascript',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.ico': 'image/x-icon',
};

function serveStatic(url, res) {
    let filePath = path.join(__dirname, url === '/' ? 'index.html' : url);
    const ext = path.extname(filePath);
    try {
        if (fs.statSync(filePath).isFile()) {
            res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
            res.end(fs.readFileSync(filePath));
            return true;
        }
    } catch (e) {}
    return false;
}

const server = http.createServer((req, res) => {
    if (req.method === 'GET' && !req.url.startsWith('/api/')) {
        if (serveStatic(req.url, res)) return;
        res.writeHead(404);
        res.end('Not found');
        return;
    }
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
                const result = validateKey(key);
                if (result.valid) {
                    const db = loadDB();
                    if (db[key].hwid && db[key].hwid !== hwid) {
                        res.writeHead(403);
                        res.end(JSON.stringify({ valid: false, error: 'Key bound to another device' }));
                        return;
                    }
                    if (hwid) db[key].hwid = hwid;
                    saveDB(db);
                    result.hwid = hwid;
                }
                res.writeHead(result.valid ? 200 : 403);
                res.end(JSON.stringify(result));
            } catch (e) {
                res.writeHead(400);
                res.end(JSON.stringify({ error: 'Invalid request' }));
            }
        });
    } else if (req.url === '/api/create' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            try {
                const { plan, days, email, nick } = JSON.parse(body);
                if (!plan || !days || !email || !nick) {
                    res.writeHead(400);
                    res.end(JSON.stringify({ error: 'Missing fields' }));
                    return;
                }
                const key = createLicense(plan, days, email, nick);
                res.writeHead(200);
                res.end(JSON.stringify({ key }));
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

server.listen(PORT, () => {
    console.log(`Excel Client license API running on port ${PORT}`);
});
