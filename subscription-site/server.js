const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = 3000;
const DB_FILE = path.join(__dirname, 'licenses.json');
const SECRET = 'rich-modern-secret-2026';

function loadDB() {
    if (!fs.existsSync(DB_FILE)) return {};
    return JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
}

function saveDB(db) {
    fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2));
}

function generateLicenseKey(plan, days, email, nick) {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let seed = 0;
    const str = plan + days + email + nick + SECRET;
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

function createLicense(plan, days, email, nick) {
    const key = generateLicenseKey(plan, days, email, nick);
    const db = loadDB();
    db[key] = {
        plan,
        days,
        email,
        nick,
        createdAt: Date.now(),
        expiresAt: Date.now() + days * 86400000,
        hwid: null
    };
    saveDB(db);
    return key;
}

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
    console.log(`Rich Modern license API running on port ${PORT}`);
});
