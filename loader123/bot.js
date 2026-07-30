const https = require('https');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// --- CONFIG ---
const CONFIG_DIR = process.env.BOT_CONFIG_DIR || __dirname;
const CONFIG_FILE = path.join(CONFIG_DIR, 'bot.config.json');
let config = {};
try { config = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf8')); } catch (e) {}

const TOKEN = process.env.BOT_TOKEN || config.token || '';
const ADMINS = config.admins || [];
const DATA_DIR = process.env.LAUNCHER_DATA_DIR || config.dataDir || (process.env.APPDATA ? path.join(process.env.APPDATA, 'excel-client-launcher') : path.join(__dirname, 'data'));

if (!TOKEN) { console.error('No BOT_TOKEN set. Create bot.config.json with {"token":"..."} or set env.'); process.exit(1); }

const API = 'https://api.telegram.org/bot' + TOKEN;

const LINKS_FILE = path.join(DATA_DIR, 'bot_links.json');
const LICENSES_FILE = path.join(DATA_DIR, '.minecraft', 'licenses.json');
const BANNED_FILE = path.join(DATA_DIR, 'banned.json');

function loadJSON(p, def) { try { if (fs.existsSync(p)) return JSON.parse(fs.readFileSync(p, 'utf8')); } catch (e) {} return def || {}; }
function saveJSON(p, d) { try { const dir = path.dirname(p); if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true }); fs.writeFileSync(p, JSON.stringify(d, null, 2)); } catch (e) {} }

function apiCall(method, body) {
    return new Promise((resolve, reject) => {
        const data = body ? JSON.stringify(body) : '';
        const req = https.request(API + '/' + method, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) }
        }, (res) => {
            let r = '';
            res.on('data', c => r += c);
            res.on('end', () => { try { resolve(JSON.parse(r)); } catch (e) { resolve({ ok: false }); } });
        });
        req.on('error', reject);
        req.write(data);
        req.end();
    });
}

function sendMsg(chatId, text, extra) {
    return apiCall('sendMessage', { chat_id: chatId, text, parse_mode: 'Markdown', ...extra });
}

function generateCode() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    return 'RM-' + Array.from({ length: 3 }, () =>
        Array.from({ length: 4 }, () => chars[crypto.randomInt(chars.length)]).join('')
    ).join('-');
}

// --- COMMANDS ---
async function handleCommand(chatId, text, userId, username) {
    const links = loadJSON(LINKS_FILE);
    const linkedUser = Object.keys(links).find(k => links[k] === chatId);

    const parts = text.split(' ');
    const cmd = parts[0].toLowerCase();

    if (cmd === '/start') {
        const args = parts.slice(1).join(' ');
        if (args) {
            links[args.toLowerCase()] = chatId;
            saveJSON(LINKS_FILE, links);
            return sendMsg(chatId, '✅ Аккаунт `' + args + '` привязан к этому чату!');
        }
        return sendMsg(chatId,
            '*Excel Client Bot*\n\n'
            + 'Привяжи аккаунт: `/start твой_логин`\n\n'
            + '*/key* — получить ключ\n'
            + '*/status* — статус подписки\n'

            + '_Админ-команды:_ `/genkey`, `/ban`, `/unban`, `/stats`'
        );
    }

    if (cmd === '/key') {
        if (!linkedUser) return sendMsg(chatId, '❌ Сначала привяжи аккаунт: `/start твой_логин`');

        const licenses = loadJSON(LICENSES_FILE);
        const userLicenses = Object.entries(licenses).filter(([k, v]) => v.email === linkedUser || k.includes(linkedUser));

        if (userLicenses.length === 0) {
            return sendMsg(chatId, '❌ У тебя нет активных ключей. Купи на сайте.');
        }

        const active = userLicenses.find(([k, v]) => Date.now() < v.expiresAt);
        if (active) {
            const [key, data] = active;
            const d = new Date(data.expiresAt);
            return sendMsg(chatId,
                '🔑 *Твой ключ*\n'
                + '`' + key + '`\n'
                + 'План: ' + data.plan + '\n'
                + 'Истекает: ' + d.toLocaleDateString('ru-RU')
            );
        }

        return sendMsg(chatId, '❌ Все твои ключи истекли.');
    }

    if (cmd === '/status') {
        if (!linkedUser) return sendMsg(chatId, '❌ Сначала привяжи аккаунт: `/start твой_логин`');

        const licenses = loadJSON(LICENSES_FILE);
        const userLicenses = Object.entries(licenses).filter(([k, v]) => v.email === linkedUser || k.includes(linkedUser));

        if (userLicenses.length === 0) {
            return sendMsg(chatId, '📭 Нет ключей.');
        }

        let msg = '*Твои ключи:*\n';
        userLicenses.forEach(([key, data]) => {
            const expired = Date.now() > data.expiresAt;
            const d = new Date(data.expiresAt);
            msg += '\n' + (expired ? '❌' : '✅') + ' `' + key + '`\n';
            msg += '   План: ' + data.plan + '\n';
            msg += '   До: ' + d.toLocaleDateString('ru-RU');
            if (expired) msg += ' _(истёк)_';
            msg += '\n';
        });

        return sendMsg(chatId, msg);
    }

    // --- ADMIN COMMANDS ---
    const isAdmin = ADMINS.includes(username) || ADMINS.includes(chatId.toString());
    if (!isAdmin) return sendMsg(chatId, '❌ Неизвестная команда.');

    if (cmd === '/genkey') {
        const plan = parts[1] || 'beta';
        const days = parseInt(parts[2]) || 30;
        if (!['stable', 'beta', 'alpha'].includes(plan)) return sendMsg(chatId, '❌ План: stable/beta/alpha');

        const key = generateCode();
        const licenses = loadJSON(LICENSES_FILE);
        const expiresAt = days === 9999 ? Date.now() + 3650 * 86400000 : Date.now() + days * 86400000;
        licenses[key] = { plan, days, createdAt: Date.now(), expiresAt, hwid: null, email: parts[3] || '' };
        saveJSON(LICENSES_FILE, licenses);
        return sendMsg(chatId, '✅ Ключ создан:\n`' + key + '`\nПлан: ' + plan + '\nДней: ' + days);
    }

    if (cmd === '/ban') {
        const target = parts.slice(1).join(' ');
        if (!target) return sendMsg(chatId, '❌ Укажи ключ или логин.');

        let banned = loadJSON(BANNED_FILE);
        banned[target] = { by: username, at: Date.now() };
        saveJSON(BANNED_FILE, banned);

        // Also revoke license if it's a key
        const licenses = loadJSON(LICENSES_FILE);
        if (licenses[target]) {
            licenses[target].banned = true;
            licenses[target].bannedBy = username;
            licenses[target].bannedAt = Date.now();
            saveJSON(LICENSES_FILE, licenses);
            return sendMsg(chatId, '🔨 Ключ `' + target + '` забанен.');
        }

        return sendMsg(chatId, '🔨 `' + target + '` добавлен в бан-лист.');
    }

    if (cmd === '/unban') {
        const target = parts.slice(1).join(' ');
        if (!target) return sendMsg(chatId, '❌ Укажи ключ или логин.');

        let banned = loadJSON(BANNED_FILE);
        delete banned[target];
        saveJSON(BANNED_FILE, banned);

        const licenses = loadJSON(LICENSES_FILE);
        if (licenses[target]) {
            delete licenses[target].banned;
            delete licenses[target].bannedBy;
            delete licenses[target].bannedAt;
            saveJSON(LICENSES_FILE, licenses);
        }

        return sendMsg(chatId, '✅ `' + target + '` разбанен.');
    }

    if (cmd === '/stats') {
        const licenses = loadJSON(LICENSES_FILE);
        const keys = Object.values(licenses);
        const total = keys.length;
        const active = keys.filter(k => !k.banned && Date.now() < k.expiresAt).length;
        const expired = keys.filter(k => Date.now() > k.expiresAt).length;
        const banned = keys.filter(k => k.banned).length;
        const byPlan = {};
        keys.forEach(k => { byPlan[k.plan] = (byPlan[k.plan] || 0) + 1; });

        let msg = '*📊 Статистика лицензий*\n';
        msg += 'Всего: ' + total + '\n';
        msg += '✅ Активных: ' + active + '\n';
        msg += '❌ Истекло: ' + expired + '\n';
        msg += '🔨 Забанено: ' + banned + '\n';
        msg += '\n*По планам:*\n';
        Object.entries(byPlan).forEach(([p, c]) => { msg += p + ': ' + c + '\n'; });

        const links = loadJSON(LINKS_FILE);
        msg += '\n👥 Привязано аккаунтов: ' + Object.keys(links).length;

        return sendMsg(chatId, msg);
    }

    return sendMsg(chatId, '❌ Неизвестная команда.');
}

// --- MAIN LOOP (polling) ---
let offset = 0;
console.log('🤖 Bot started. Data dir:', DATA_DIR);

async function poll() {
    try {
        const res = await apiCall('getUpdates', { offset, timeout: 30 });
        if (!res.ok || !res.result) return;

        for (const upd of res.result) {
            offset = upd.update_id + 1;
            const msg = upd.message;
            if (!msg || !msg.text) continue;
            const chatId = msg.chat.id;
            const userId = msg.from.id;
            const username = msg.from.username || msg.from.first_name || 'unknown';

            console.log('<<', msg.text, 'from', username);

            try {
                await handleCommand(chatId, msg.text, userId, username);
            } catch (e) {
                console.error('Command error:', e.message);
                await sendMsg(chatId, '⚠️ Ошибка: ' + e.message);
            }
        }
    } catch (e) {
        console.error('Poll error:', e.message);
    }
}

setInterval(poll, 1000);
poll();

// Graceful shutdown
process.on('SIGINT', () => { console.log('\nBot stopped.'); process.exit(0); });
process.on('SIGTERM', () => process.exit(0));
