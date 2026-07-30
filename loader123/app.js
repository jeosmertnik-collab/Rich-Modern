const { app, BrowserWindow, ipcMain, dialog, net, Tray, Menu, nativeImage } = require('electron');
const path = require('path');
const fs = require('fs');
const http = require('http');
const { spawn, execSync, exec } = require('child_process');
const MinecraftLauncher = require('./mc-launcher');

let win;
let tray;
let g_licenseServer = null;


process.on('uncaughtException', (err) => {
    try {
        const line = `[${new Date().toISOString()}] UNCAUGHT: ${err.stack || err.message}\n`;
        require('fs').appendFileSync(path.join(app.getPath('userData'), 'crash.log'), line);
    } catch (e) {}
    console.error('UNCAUGHT EXCEPTION:', err);
});

process.on('unhandledRejection', (reason) => {
    try {
        const line = `[${new Date().toISOString()}] UNHANDLED REJECTION: ${reason instanceof Error ? reason.stack : reason}\n`;
        require('fs').appendFileSync(path.join(app.getPath('userData'), 'crash.log'), line);
    } catch (e) {}
    console.error('UNHANDLED REJECTION:', reason);
});

const LOG_FILE = path.join(app.getPath('userData'), 'launch.log');

function log(msg) {
    const line = `[${new Date().toISOString()}] ${msg}\n`;
    try { fs.appendFileSync(LOG_FILE, line); } catch (e) {}
    console.log(line.trim());
}

const USERS_FILE = path.join(app.getPath('userData'), '.minecraft', 'users.json');
const LICENSE_FILE = path.join(app.getPath('userData'), '.minecraft', 'license.json');
const LICENSE_DB_FILE = path.join(app.getPath('userData'), '.minecraft', 'licenses.json');
const VERSION_URL = 'https://raw.githubusercontent.com/jeosmertnik-collab/Rich-Modern/main/version.json';
const REMOTE_USERS_URL = 'https://raw.githubusercontent.com/jeosmertnik-collab/Rich-Modern/main/users.json';
const REMOTE_USERS_API = 'https://api.github.com/repos/jeosmertnik-collab/Rich-Modern/contents/users.json';
const LICENSE_API_URL = 'http://localhost:3000/api/validate';
const LOCAL_VERSION_FILE = path.join(app.getPath('userData'), 'version.json');
const LICENSE_SECRET = 'rich-modern-secret-2026';
const NOTIF_DB_FILE = path.join(app.getPath('userData'), '.minecraft', 'notifications.json');
const CAPES_DIR = path.join(__dirname, 'capes');
const ADMIN_PASSWORD_HASH_FILE = path.join(app.getPath('userData'), '.admin_hash');

function generateRandomSegment() {
    const crypto = require('crypto');
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    const buf = crypto.randomBytes(4);
    let s = '';
    for (let i = 0; i < 4; i++) {
        s += chars[buf[i] % chars.length];
    }
    return s;
}

function generateLicenseKey(plan, days, email, nick) {
    const crypto = require('crypto');
    const key = `RM-${generateRandomSegment()}-${generateRandomSegment()}-${generateRandomSegment()}`;

    const db = loadLicenseDB();
    if (db[key]) return generateLicenseKey(plan, days, email, nick);

    const expiresAt = days === 9999 ? Date.now() + 3650 * 86400000 : Date.now() + days * 86400000;
    db[key] = { plan, days, createdAt: Date.now(), expiresAt, hwid: null, email: email || '', nick: nick || '' };
    saveLicenseDB(db);

    return { key, plan, expiresAt };
}

async function validateKeyRemote(key, hwid) {
    const cfg = loadBotConfig();
    const apiUrl = (cfg && cfg.subscriptionApiUrl) || '';
    if (!apiUrl) return null;
    try {
        const url = apiUrl.replace(/\/+$/, '') + '/api/validate';
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 10000);
        const res = await net.fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ key, hwid }),
            signal: controller.signal
        });
        clearTimeout(timeout);
        if (res.ok) return await res.json();
    } catch (e) {}
    return null;
}

function validateKeyLocal(key, hwid) {
    if (!key || key.length < 17) return { valid: false, error: 'Invalid key format' };

    const parts = key.split('-');
    if (parts[0] !== 'RM' || parts.length < 4 || parts.length > 5) return { valid: false, error: 'Invalid key format' };
    for (let i = 1; i < parts.length; i++) {
        if (parts[i].length !== 4) return { valid: false, error: 'Invalid key format' };
    }

    const db = loadLicenseDB();
    const entry = db[key];

    if (!entry) return { valid: false, error: 'Key not found' };

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
        const SUB_PAGE = '<!DOCTYPE html>' +
'<html lang="ru">' +
'<head>' +
'<meta charset="UTF-8">' +
'<meta name="viewport" content="width=device-width,initial-scale=1.0">' +
'<title>Excel Client — Подписки</title>' +
'<style>' +
'*{margin:0;padding:0;box-sizing:border-box}' +
'body{font-family:Arial,sans-serif;background:#0a0a0f;color:#e4e4e7;min-height:100vh}' +
'.container{max-width:1200px;margin:0 auto;padding:40px 20px}' +
'header{text-align:center;margin-bottom:60px}' +
'.logo{font-size:32px;font-weight:800;background:linear-gradient(135deg,#6366f1,#a78bfa,#c084fc);-webkit-background-clip:text;-webkit-text-fill-color:transparent;margin-bottom:12px}' +
'header p{color:#71717a;font-size:16px}' +
'.duration-selector{display:flex;justify-content:center;gap:8px;margin-bottom:50px;flex-wrap:wrap}' +
'.duration-btn{padding:10px 20px;border:1px solid #27272a;background:#18181b;color:#a1a1aa;border-radius:10px;cursor:pointer;font-size:14px;font-weight:500;transition:all .2s}' +
'.duration-btn:hover{border-color:#6366f1;color:#e4e4e7}' +
'.duration-btn.active{background:#6366f1;border-color:#6366f1;color:#fff}' +
'.duration-btn .badge{font-size:10px;background:#22c55e;color:#fff;padding:2px 6px;border-radius:6px;margin-left:4px;font-weight:600}' +
'.plans{display:grid;grid-template-columns:repeat(3,1fr);gap:24px;margin-bottom:60px}' +
'@media(max-width:900px){.plans{grid-template-columns:1fr;max-width:400px;margin:0 auto}}' +
'.plan-card{background:#18181b;border:1px solid #27272a;border-radius:20px;padding:36px 28px;position:relative;transition:transform .3s,border-color .3s}' +
'.plan-card:hover{transform:translateY(-4px)}' +
'.plan-card.featured{border-color:#6366f1;box-shadow:0 0 40px rgba(99,102,241,0.1)}' +
'.plan-card.featured::before{content:"ПОПУЛЯРНЫЙ";position:absolute;top:-12px;left:50%;transform:translateX(-50%);background:linear-gradient(135deg,#6366f1,#8b5cf6);color:#fff;padding:4px 16px;border-radius:20px;font-size:11px;font-weight:700;letter-spacing:1px}' +
'.plan-icon{width:48px;height:48px;border-radius:14px;display:flex;align-items:center;justify-content:center;font-size:22px;margin-bottom:20px}' +
'.plan-card:nth-child(1) .plan-icon{background:rgba(234,179,8,0.15)}' +
'.plan-card:nth-child(2) .plan-icon{background:rgba(99,102,241,0.15)}' +
'.plan-card:nth-child(3) .plan-icon{background:rgba(34,197,94,0.15)}' +
'.plan-name{font-size:22px;font-weight:700;margin-bottom:6px}' +
'.plan-desc{color:#71717a;font-size:14px;margin-bottom:24px}' +
'.plan-price{font-size:40px;font-weight:800;margin-bottom:4px}' +
'.plan-price .currency{font-size:20px;font-weight:600;color:#a1a1aa}' +
'.plan-price .period{font-size:16px;font-weight:500;color:#52525b}' +
'.plan-features{list-style:none;margin:24px 0;padding:0}' +
'.plan-features li{padding:8px 0;font-size:14px;color:#a1a1aa;display:flex;align-items:center;gap:10px}' +
'.plan-features li::before{content:"✓";color:#22c55e;font-weight:700;font-size:14px}' +
'.plan-features li.disabled{color:#3f3f46}' +
'.plan-features li.disabled::before{content:"✕";color:#3f3f46}' +
'.buy-btn{width:100%;padding:14px;border:none;border-radius:12px;font-size:15px;font-weight:600;cursor:pointer;transition:all .2s}' +
'.plan-card:nth-child(1) .buy-btn{background:linear-gradient(135deg,#eab308,#ca8a04);color:#000}' +
'.plan-card:nth-child(2) .buy-btn{background:linear-gradient(135deg,#6366f1,#8b5cf6);color:#fff}' +
'.plan-card:nth-child(3) .buy-btn{background:linear-gradient(135deg,#22c55e,#16a34a);color:#fff}' +
'.buy-btn:hover{transform:scale(1.02);filter:brightness(1.1)}' +
'.buy-btn:disabled{opacity:.5;cursor:not-allowed;transform:none}' +
'.modal-overlay{display:none;position:fixed;inset:0;background:rgba(0,0,0,0.7);backdrop-filter:blur(8px);z-index:1000;align-items:center;justify-content:center}' +
'.modal-overlay.active{display:flex}' +
'.modal{background:#18181b;border:1px solid #27272a;border-radius:20px;padding:40px;width:90%;max-width:480px;position:relative}' +
'.modal-close{position:absolute;top:16px;right:16px;background:none;border:none;color:#71717a;font-size:24px;cursor:pointer}' +
'.modal h2{font-size:22px;margin-bottom:8px}' +
'.modal .subtitle{color:#71717a;font-size:14px;margin-bottom:28px}' +
'.form-group{margin-bottom:18px}' +
'.form-group label{display:block;font-size:13px;font-weight:600;color:#a1a1aa;margin-bottom:6px}' +
'.form-group input{width:100%;padding:12px 16px;background:#0a0a0f;border:1px solid #27272a;border-radius:10px;color:#e4e4e7;font-size:14px}' +
'.form-group input:focus{outline:none;border-color:#6366f1}' +
'.form-group input::placeholder{color:#3f3f46}' +
'.payment-methods{display:flex;gap:10px;margin-bottom:20px}' +
'.payment-method{flex:1;padding:12px;border:1px solid #27272a;border-radius:10px;background:#0a0a0f;cursor:pointer;text-align:center;font-size:13px;font-weight:500;transition:all .2s}' +
'.payment-method:hover,.payment-method.active{border-color:#6366f1;background:rgba(99,102,241,0.1)}' +
'.pay-btn{width:100%;padding:14px;background:linear-gradient(135deg,#6366f1,#8b5cf6);color:#fff;border:none;border-radius:12px;font-size:15px;font-weight:600;cursor:pointer}' +
'.pay-btn:hover{filter:brightness(1.1)}' +
'.pay-btn:disabled{opacity:.5;cursor:not-allowed}' +
'.divider{height:1px;background:#27272a;margin:20px 0}' +
'.order-summary{background:#0a0a0f;border-radius:12px;padding:16px;margin-bottom:20px}' +
'.order-row{display:flex;justify-content:space-between;font-size:14px;padding:4px 0}' +
'.order-row.total{font-weight:700;font-size:16px;margin-top:8px;padding-top:8px;border-top:1px solid #27272a}' +
'.success-content{text-align:center}' +
'.success-icon{width:64px;height:64px;background:rgba(34,197,94,0.15);border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:32px;margin:0 auto 20px}' +
'.license-key{background:#0a0a0f;border:1px solid #27272a;border-radius:10px;padding:14px;font-family:monospace;font-size:14px;color:#a78bfa;text-align:center;margin:16px 0;word-break:break-all;cursor:pointer}' +
'.license-key:hover{border-color:#6366f1}' +
'.copy-hint{font-size:12px;color:#52525b}' +
'footer{text-align:center;padding:40px 20px;color:#3f3f46;font-size:13px}' +
'</style>' +
'</head><body>' +
'<div class="container">' +
'<header><div class="logo">EXCEL CLIENT</div><p>Выбери подписку и получи доступ к эксклюзивным функциям</p></header>' +
'<div class="duration-selector">' +
'<button class="duration-btn" data-days="1">1 день</button>' +
'<button class="duration-btn" data-days="7">7 дней</button>' +
'<button class="duration-btn active" data-days="30">30 дней</button>' +
'<button class="duration-btn" data-days="90">90 дней <span class="badge">-15%</span></button>' +
'<button class="duration-btn" data-days="9999">Навсегда <span class="badge">-60%</span></button>' +
'</div>' +
'<div class="plans">' +
'<div class="plan-card"><div class="plan-icon">⚡</div><div class="plan-name">Stable</div><div class="plan-desc">Стабильная версия для всех</div>' +
'<div class="plan-price"><span class="price-value" data-base="2.99">2.99</span><span class="currency">₽</span><span class="period">/мес</span></div>' +
'<ul class="plan-features"><li>Все базовые модули</li><li>ClickGUI + HUD</li><li>Stable обновления</li><li>Discord поддержка</li><li class="disabled">Alpha/Beta функции</li><li class="disabled">Ранний доступ</li></ul>' +
'<button class="buy-btn" onclick="openCheckout(\'stable\')">Купить</button></div>' +
'<div class="plan-card featured"><div class="plan-icon">🚀</div><div class="plan-name">Beta</div><div class="plan-desc">Тестирование новых функций</div>' +
'<div class="plan-price"><span class="price-value" data-base="5.99">5.99</span><span class="currency">₽</span><span class="period">/мес</span></div>' +
'<ul class="plan-features"><li>Все функции Stable</li><li>Beta модули</li><li>Ранний доступ к обновлениям</li><li>Приоритетная поддержка</li><li>Экспериментальные функции</li><li class="disabled">Alpha функции</li></ul>' +
'<button class="buy-btn" onclick="openCheckout(\'beta\')">Купить</button></div>' +
'<div class="plan-card"><div class="plan-icon">👑</div><div class="plan-name">Alpha</div><div class="plan-desc">Максимум возможностей</div>' +
'<div class="plan-price"><span class="price-value" data-base="9.99">9.99</span><span class="currency">₽</span><span class="period">/мес</span></div>' +
'<ul class="plan-features"><li>Все функции Beta</li><li>Alpha эксклюзив</li><li>Персональные настройки</li><li>Личный Discord канал</li><li>Голосование за фичи</li><li>Полный доступ ко всему</li></ul>' +
'<button class="buy-btn" onclick="openCheckout(\'alpha\')">Купить</button></div>' +
'</div></div>' +
'<div class="modal-overlay" id="checkoutModal">' +
'<div class="modal"><button class="modal-close" onclick="closeModal()">&times;</button>' +
'<div id="checkoutForm">' +
'<h2>Оформление подписки</h2><p class="subtitle" id="checkoutSubtitle">Beta — 30 дней</p>' +
'<div class="order-summary">' +
'<div class="order-row"><span>Подписка</span><span id="orderPlan">Beta</span></div>' +
'<div class="order-row"><span>Период</span><span id="orderPeriod">30 дней</span></div>' +
'<div class="order-row" id="promoRow" style="display:none;color:#22c55e;"><span>Скидка</span><span id="orderDiscount">-0</span></div>' +
'<div class="order-row total"><span>Итого</span><span id="orderTotal">5.99₽</span></div>' +
'</div>' +
'<div class="form-group"><label>Email</label><input type="email" id="emailInput" placeholder="your@email.com"></div>' +
'<div class="form-group"><label>Промокод</label>' +
'<div style="display:flex;gap:8px;"><input type="text" id="promoInput" placeholder="Введите промокод" style="flex:1;">' +
'<button onclick="applyPromo()" style="padding:12px 16px;background:#6366f1;color:white;border:none;border-radius:10px;font-weight:600;cursor:pointer;white-space:nowrap;">Применить</button></div>' +
'<div id="promoMessage" style="font-size:12px;margin-top:6px;display:none;"></div></div>' +
'<div class="payment-methods">' +
'<div class="payment-method active" data-method="card" onclick="selectPayment(this)">💳 Карта</div>' +
'<div class="payment-method" data-method="crypto" onclick="selectPayment(this)">₿ Крипто</div>' +
'<div class="payment-method" data-method="qiwi" onclick="selectPayment(this)">🅴 QIWI</div>' +
'</div>' +
'<button class="pay-btn" id="payBtn" onclick="processPayment()">Оплатить <span id="payAmount">5.99₽</span></button>' +
'</div>' +
'<div id="successContent" style="display:none;">' +
'<div class="success-content"><div class="success-icon">✓</div><h2>Оплата прошла!</h2>' +
'<p class="subtitle">Твой лицензионный ключ</p>' +
'<div class="license-key" id="licenseKeyDisplay" onclick="copyLicense()">RM-XXXX-XXXX-XXXX</div>' +
'<p class="copy-hint">Нажми на ключ чтобы скопировать</p>' +
'<p style="margin-top:20px;font-size:13px;color:#71717a">Вставь этот ключ в лаунчер: <strong>Настройки → Подписка → Активировать</strong></p>' +
'</div></div></div></div>' +
'<footer>Excel Client &copy; 2026 — Ключ действителен на 1 устройстве.</footer>' +
'<script>' +
'const PRICES={stable:{1:49,7:149,30:299,90:649,9999:999},beta:{1:79,7:249,30:449,90:949,9999:1499},alpha:{1:129,7:349,30:599,90:1299,9999:1999}};' +
'const PROMO_CODES={ostopov:{discount:.5,label:"50% скидка"}};' +
'const PLAN_NAMES={stable:"Stable",beta:"Beta",alpha:"Alpha"};' +
'let selectedDays=30,selectedPlan="beta",selectedPayment="card",promoDiscount=0,promoApplied=false;' +
'document.querySelectorAll(".duration-btn").forEach(b=>{b.addEventListener("click",()=>{document.querySelectorAll(".duration-btn").forEach(x=>x.classList.remove("active"));b.classList.add("active");selectedDays=parseInt(b.dataset.days);updatePrices()})});' +
'function selectPayment(el){document.querySelectorAll(".payment-method").forEach(m=>m.classList.remove("active"));el.classList.add("active");selectedPayment=el.dataset.method}' +
'function applyPromo(){const c=document.getElementById("promoInput").value.trim().toLowerCase(),m=document.getElementById("promoMessage");if(c===""&&!promoApplied)return;if(promoApplied){promoDiscount=0;promoApplied=false;m.style.display="none";document.getElementById("promoInput").value="";updatePrices();return}if(PROMO_CODES[c]){promoDiscount=PROMO_CODES[c].discount;promoApplied=true;m.style.display="block";m.style.color="#22c55e";m.textContent="Применено: "+PROMO_CODES[c].label}else{m.style.display="block";m.style.color="#ef4444";m.textContent="Промокод не найден"}updatePrices()}' +
'function updatePrices(){document.querySelectorAll(".price-value").forEach((e,i)=>{let p=PRICES[["stable","beta","alpha"][i]][selectedDays];if(promoApplied)p*=(1-promoDiscount);e.textContent=Math.round(p)});updateCheckout()}' +
'function updateCheckout(){const p=Math.round(PRICES[selectedPlan][selectedDays]*(promoApplied?1-promoDiscount:1));document.getElementById("orderTotal").textContent=p+"₽";document.getElementById("payAmount").textContent=p+"₽";if(promoApplied&&promoDiscount>0){const full=PRICES[selectedPlan][selectedDays];const disc=Math.round(full*promoDiscount);document.getElementById("promoRow").style.display="";document.getElementById("orderDiscount").textContent="-"+disc+"₽"}else{document.getElementById("promoRow").style.display="none"}}' +
'function openCheckout(plan){selectedPlan=plan;updateCheckout();document.getElementById("checkoutSubtitle").textContent=PLAN_NAMES[plan]+" — "+getPeriodText(selectedDays);document.getElementById("orderPlan").textContent=PLAN_NAMES[plan];document.getElementById("orderPeriod").textContent=getPeriodText(selectedDays);document.getElementById("checkoutForm").style.display="";document.getElementById("successContent").style.display="none";document.getElementById("checkoutModal").classList.add("active")}' +
'function getPeriodText(d){return d===9999?"навсегда":d===1?"1 день":d+" дней"}' +
'function closeModal(){document.getElementById("checkoutModal").classList.remove("active")}' +
'async function processPayment(){const email=document.getElementById("emailInput").value.trim();if(!email||!email.includes("@")){alert("Введите корректный email");return}const btn=document.getElementById("payBtn");btn.disabled=true;btn.textContent="Обработка...";try{const r=await fetch("/api/generate",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({plan:selectedPlan,days:selectedDays,email})});const d=await r.json();if(!d.key){alert("Ошибка генерации");btn.disabled=false;btn.textContent="Оплатить";return}await new Promise(t=>setTimeout(t,2000));document.getElementById("licenseKeyDisplay").textContent=d.key;document.getElementById("checkoutForm").style.display="none";document.getElementById("successContent").style.display="";btn.disabled=false;btn.textContent="Оплатить"}catch(e){alert("Ошибка соединения");btn.disabled=false;btn.textContent="Оплатить"}}' +
'function copyLicense(){navigator.clipboard.writeText(document.getElementById("licenseKeyDisplay").textContent).then(()=>{const e=document.getElementById("licenseKeyDisplay");e.style.borderColor="#22c55e";setTimeout(()=>e.style.borderColor="",1000)})}' +
'updatePrices()' +
'</script></body></html>';

        const server = http.createServer((req, res) => {
            if (req.method === 'GET' && req.url === '/') {
                res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
                res.end(SUB_PAGE);
                return;
            }
            if (req.url === '/favicon.ico') {
                res.writeHead(204);
                res.end();
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
            } else if (req.url === '/api/generate' && req.method === 'POST') {
                let body = '';
                req.on('data', chunk => body += chunk);
                req.on('end', () => {
                    try {
                        const { plan, days, email } = JSON.parse(body);
                        const result = generateLicenseKey(plan, parseInt(days), email || '');
                        res.writeHead(200, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ key: result.key }));
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
                        const result = generateLicenseKey(plan, parseInt(days), email || '', nick || '');
                        res.writeHead(200, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ key: result.key }));
                    } catch (e) {
                        res.writeHead(400);
                        res.end(JSON.stringify({ error: 'Invalid request' }));
                    }
                });
            // --- Admin panel static ---
            } else if (req.url === '/admin' || req.url === '/admin/' || req.url.startsWith('/admin/')) {
                const filePath = req.url === '/admin' || req.url === '/admin/' ? '/index.html' : req.url.replace('/admin', '');
                const localPath = path.join(__dirname, 'admin', filePath);
                try {
                    if (fs.existsSync(localPath) && !fs.statSync(localPath).isDirectory()) {
                        const ext = path.extname(localPath).toLowerCase();
                        const mime = { '.html': 'text/html; charset=utf-8', '.js': 'application/javascript', '.css': 'text/css', '.png': 'image/png', '.svg': 'image/svg+xml', '.ico': 'image/x-icon' };
                        res.writeHead(200, { 'Content-Type': mime[ext] || 'application/octet-stream' });
                        res.end(fs.readFileSync(localPath));
                    } else {
                        res.writeHead(404); res.end('Not found');
                    }
                } catch (e) { res.writeHead(404); res.end('Not found'); }
            // --- Cape file serving (public) ---
            } else if (req.url.startsWith('/api/capes/') && req.method === 'GET') {
                const username = decodeURIComponent(req.url.split('/').pop());
                const assigns = loadCapeAssignments();
                const file = assigns[username.toLowerCase()];
                if (file) {
                    const capePath = path.join(CAPES_DIR, file);
                    try {
                        if (fs.existsSync(capePath) && !fs.statSync(capePath).isDirectory()) {
                            res.writeHead(200, { 'Content-Type': 'image/png' });
                            res.end(fs.readFileSync(capePath));
                            return;
                        }
                    } catch (e) {}
                }
                res.writeHead(404); res.end(JSON.stringify({ error: 'No cape' }));
            // --- Admin API ---
            } else if (req.url.startsWith('/api/admin') && req.method === 'POST') {
                let body = '';
                req.on('data', chunk => body += chunk);
                req.on('end', () => {
                    try {
                        const data = JSON.parse(body);
                        const adminPass = getAdminPassword();
                        const subUrl = req.url.replace('/api/admin', '') || '/';
                        const token = data.token || req.headers['x-admin-token'] || '';

                        if (subUrl === '/login') {
                            const ok = data.password === adminPass;
                            if (ok) {
                                res.writeHead(200); res.end(JSON.stringify({ token: adminHash(adminPass), ok: true }));
                            } else {
                                res.writeHead(403); res.end(JSON.stringify({ error: 'Invalid password' }));
                            }
                            return;
                        }
                        if (!adminCheckToken(token, adminPass)) {
                            res.writeHead(403); res.end(JSON.stringify({ error: 'Unauthorized' }));
                            return;
                        }
                        if (subUrl === '/check') {
                            res.writeHead(200); res.end(JSON.stringify({ ok: true })); return;
                        }
                        if (subUrl === '/ban') {
                            const db = loadLicenseDB();
                            if (db[data.key]) { db[data.key].banned = true; saveLicenseDB(db); res.writeHead(200); res.end(JSON.stringify({ ok: true })); }
                            else { res.writeHead(404); res.end(JSON.stringify({ error: 'Key not found' })); }
                            return;
                        }
                        if (subUrl === '/unban') {
                            const db = loadLicenseDB();
                            if (db[data.key]) { delete db[data.key].banned; saveLicenseDB(db); res.writeHead(200); res.end(JSON.stringify({ ok: true })); }
                            else { res.writeHead(404); res.end(JSON.stringify({ error: 'Key not found' })); }
                            return;
                        }
                        if (subUrl === '/extend') {
                            const db = loadLicenseDB();
                            if (db[data.key]) { db[data.key].expiresAt += (data.days || 30) * 86400000; saveLicenseDB(db); res.writeHead(200); res.end(JSON.stringify({ ok: true })); }
                            else { res.writeHead(404); res.end(JSON.stringify({ error: 'Key not found' })); }
                            return;
                        }
                        if (subUrl === '/genkey') {
                            const result = generateLicenseKey(data.plan || 'beta', parseInt(data.days) || 30, data.email || '', data.nick || '');
                            res.writeHead(200); res.end(JSON.stringify({ key: result.key, plan: result.plan, expiresAt: result.expiresAt }));
                            return;
                        }
                        if (subUrl === '/notify') {
                            addNotification(data.title || 'Новое уведомление', data.body || '', data.type || 'info');
                            res.writeHead(200); res.end(JSON.stringify({ ok: true }));
                            return;
                        }
                        if (subUrl === '/cape/upload') {
                            ensureCapesDir();
                            const name = data.name || 'cape_' + Date.now();
                            const fileName = name.replace(/[^a-zA-Z0-9_-]/g, '_') + '.png';
                            const filePath = path.join(CAPES_DIR, fileName);
                            try {
                                const buf = Buffer.from(data.data, 'base64');
                                fs.writeFileSync(filePath, buf);
                                res.writeHead(200); res.end(JSON.stringify({ ok: true, file: fileName }));
                            } catch (e) { res.writeHead(500); res.end(JSON.stringify({ error: e.message })); }
                            return;
                        }
                        if (subUrl === '/cape/assign') {
                            const assigns = loadCapeAssignments();
                            const user = (data.user || '').toLowerCase();
                            if (!user || !data.file) { res.writeHead(400); res.end(JSON.stringify({ error: 'user and file required' })); return; }
                            assigns[user] = data.file;
                            saveCapeAssignments(assigns);
                            res.writeHead(200); res.end(JSON.stringify({ ok: true }));
                            return;
                        }
                        res.writeHead(404); res.end(JSON.stringify({ error: 'Unknown admin route' }));
                    } catch (e) { res.writeHead(400); res.end(JSON.stringify({ error: 'Invalid request' })); }
                });
            // --- Admin API GET ---
            } else if (req.url.startsWith('/api/admin') && req.method === 'GET') {
                const token = req.headers['x-admin-token'] || '';
                const adminPass = getAdminPassword();
                if (!adminCheckToken(token, adminPass)) {
                    res.writeHead(403); res.end(JSON.stringify({ error: 'Unauthorized' })); return;
                }
                const subUrl = req.url.replace('/api/admin', '') || '/';
                if (subUrl === '/stats') {
                    const db = loadLicenseDB();
                    const keys = Object.values(db);
                    const now = Date.now();
                    const linksPath = path.join(app.getPath('userData'), 'bot_links.json');
                    let linked = 0;
                    try { if (fs.existsSync(linksPath)) linked = Object.keys(JSON.parse(fs.readFileSync(linksPath, 'utf8'))).length; } catch (e) {}
                    res.writeHead(200); res.end(JSON.stringify({
                        total: keys.length,
                        active: keys.filter(k => !k.banned && now < k.expiresAt).length,
                        expired: keys.filter(k => now > k.expiresAt).length,
                        banned: keys.filter(k => k.banned).length,
                        linked
                    })); return;
                }
                if (subUrl === '/users') {
                    const db = loadLicenseDB();
                    const users = Object.entries(db).map(([key, data]) => ({ key, ...data }));
                    res.writeHead(200); res.end(JSON.stringify({ users })); return;
                }
                if (subUrl === '/notifications') {
                    const list = loadNotifDB();
                    res.writeHead(200); res.end(JSON.stringify({ list })); return;
                }
                if (subUrl === '/capes') {
                    ensureCapesDir();
                    let files = [];
                    try { files = fs.readdirSync(CAPES_DIR).filter(f => f.endsWith('.png') && f !== '_assignments.json'); } catch (e) {}
                    const list = files.map(f => ({ name: f.replace('.png', ''), file: f }));
                    res.writeHead(200); res.end(JSON.stringify({ list })); return;
                }
                if (subUrl.startsWith('/cape/file/')) {
                    const fileName = decodeURIComponent(subUrl.split('/').pop());
                    const filePath = path.join(CAPES_DIR, fileName);
                    try {
                        if (fs.existsSync(filePath) && !fs.statSync(filePath).isDirectory()) {
                            res.writeHead(200, { 'Content-Type': 'image/png' }); res.end(fs.readFileSync(filePath)); return;
                        }
                    } catch (e) {}
                    res.writeHead(404); res.end(JSON.stringify({ error: 'Not found' })); return;
                }
                res.writeHead(404); res.end(JSON.stringify({ error: 'Unknown admin route' }));
            } else {
                res.writeHead(404);
                res.end(JSON.stringify({ error: 'Not found' }));
            }
        });
        g_licenseServer = server;
        
        server.on('error', (err) => {
            if (err.code === 'EADDRINUSE') {
                console.log('Port 3000 busy, killing old process...');
                try {
                    exec('netstat -ano | findstr :3000', (e, stdout) => {
                        if (stdout) {
                            const lines = stdout.trim().split('\n');
                            for (const line of lines) {
                                const parts = line.trim().split(/\s+/);
                                if (parts.length >= 5 && parts[1] && parts[1].indexOf(':3000') !== -1) {
                                    const pid = parts[4];
                                    if (pid && pid !== '0') {
                                        try { exec('taskkill /PID ' + pid + ' /F'); } catch (ex) {}
                                    }
                                }
                            }
                        }
                        setTimeout(() => {
                            try { server.listen(3000, '127.0.0.1'); } catch (ex) {}
                        }, 1000);
                    });
                } catch (ex) {
                    console.log('Could not kill port 3000 process:', ex.message);
                }
            }
        });

        server.listen(3000, '127.0.0.1', () => {
            console.log('License server started on port 3000');
        });
        server.unref();
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

// --- Notification DB ---
function loadNotifDB() {
    try { if (fs.existsSync(NOTIF_DB_FILE)) return JSON.parse(fs.readFileSync(NOTIF_DB_FILE, 'utf8')); } catch (e) {}
    return [];
}
function saveNotifDB(db) {
    try { const dir = path.dirname(NOTIF_DB_FILE); if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true }); fs.writeFileSync(NOTIF_DB_FILE, JSON.stringify(db, null, 2)); } catch (e) {}
}
function addNotification(title, body, type) {
    const db = loadNotifDB();
    db.push({ title, body, type: type || 'info', time: Date.now() });
    saveNotifDB(db);
}
// --- Cape helpers ---
function ensureCapesDir() { try { if (!fs.existsSync(CAPES_DIR)) fs.mkdirSync(CAPES_DIR, { recursive: true }); } catch (e) {} }
function loadCapeAssignments() {
    const p = path.join(CAPES_DIR, '_assignments.json');
    try { if (fs.existsSync(p)) return JSON.parse(fs.readFileSync(p, 'utf8')); } catch (e) {}
    return {};
}
function saveCapeAssignments(a) {
    try { ensureCapesDir(); fs.writeFileSync(path.join(CAPES_DIR, '_assignments.json'), JSON.stringify(a, null, 2)); } catch (e) {}
}
// --- Admin auth ---
function getAdminPassword() {
    // First check cached bot config
    const cfg = loadBotConfig();
    if (cfg && cfg.adminPassword) return cfg.adminPassword;
    // If not cached, read directly from dev config (user might have old userData copy)
    try {
        const devPath = path.join(__dirname, 'bot.config.json');
        if (fs.existsSync(devPath)) {
            const devCfg = JSON.parse(fs.readFileSync(devPath, 'utf8'));
            if (devCfg && devCfg.adminPassword) return devCfg.adminPassword;
        }
    } catch (e) {}
    return 'admin123';
}
function adminHash(pass) {
    const crypto = require('crypto');
    return crypto.createHash('sha256').update(pass + LICENSE_SECRET).digest('hex');
}
function adminCheckToken(token, pass) {
    if (!token) return false;
    const expected = adminHash(pass);
    return token === expected;
}

function getHardwareId() {
    const os = require('os');
    const crypto = require('crypto');
    let mac = 'UNKNOWN';
    try {
        const interfaces = os.networkInterfaces();
        for (const name of Object.keys(interfaces)) {
            for (const iface of interfaces[name]) {
                if (iface.mac && iface.mac !== '00:00:00:00:00:00') {
                    mac = iface.mac.replace(/:/g, '').toUpperCase();
                    break;
                }
            }
            if (mac !== 'UNKNOWN') break;
        }
    } catch (e) {}
    const hostname = os.hostname();
    const parts = [mac, hostname];
    try {
        if (process.env.USERNAME) parts.push(process.env.USERNAME);
        if (process.env.COMPUTERNAME) parts.push(process.env.COMPUTERNAME);
    } catch (e) {}
    return crypto.createHash('sha256').update(parts.join(':')).digest('hex').substring(0, 16).toUpperCase();
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
    win.on('close', () => {
        app.isQuitting = true;
        try { if (tray) { tray.destroy(); tray = null; } } catch (e) {}
        app.exit(0);
    });
}

function createTray() {
    try {
        const iconPath = path.join(__dirname, 'excel.ico');
        let trayIcon;
        if (fs.existsSync(iconPath)) {
            trayIcon = nativeImage.createFromPath(iconPath);
        } else {
            trayIcon = nativeImage.createEmpty();
        }
        tray = new Tray(trayIcon);
        tray.setToolTip('Excel Client');
        const menu = Menu.buildFromTemplate([
            { label: 'Открыть лаунчер', click: () => { if (win) { win.show(); win.focus(); } else createWindow(); } },
            { type: 'separator' },
            { label: 'Выход', click: () => { app.isQuitting = true; app.quit(); } }
        ]);
        tray.setContextMenu(menu);
        tray.on('double-click', () => { if (win) { win.show(); win.focus(); } else createWindow(); });
    } catch (e) { console.error('[tray] error:', e.message); }
}

app.on('before-quit', () => {
    try { killExistingGameProcesses(log); } catch (e) {}
});

ipcMain.on('window-minimize', () => { if (win) win.minimize(); });
ipcMain.on('window-close', () => { if (win) win.hide(); });

ipcMain.handle('autostart:set', async (event, enable) => {
    try {
        app.setLoginItemSettings({ openAtLogin: enable });
    } catch (e) {
        console.error('[autostart] error:', e.message);
    }
});

ipcMain.handle('autostart:get', async () => {
    try {
        return app.getLoginItemSettings().openAtLogin;
    } catch (e) { return false; }
});

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

ipcMain.handle('auth:register', (event, { login, password, email }) => {
    const users = loadUsers();
    if (users[login]) return { success: false, error: 'User already exists' };
    users[login] = { password, email: email || '', registeredAt: Date.now() };
    saveUsers(users);
    log('Registered: ' + login + (email ? ' <' + email + '>' : ''));
    return { success: true };
});

ipcMain.handle('auth:login', (event, { login, password }) => {
    const users = loadUsers();
    if (!users[login]) return { success: false, error: 'User not found' };
    if (users[login].password !== password) return { success: false, error: 'Wrong password' };
    return { success: true, user: { login } };
});

ipcMain.handle('auth:sync-remote', async () => {
    try {
        let resp;
        try {
            resp = await fetchUrl(REMOTE_USERS_URL);
        } catch (e) {
            return { success: true, merged: 0, total: 0, note: 'Remote file not found' };
        }
        const remote = JSON.parse(resp);
        if (!remote || typeof remote !== 'object') return { success: true, merged: 0, total: 0 };
        const local = loadUsers();
        let merged = 0;
        for (const [login, data] of Object.entries(remote)) {
            if (!local[login]) {
                local[login] = data;
                merged++;
            }
        }
        if (merged > 0) saveUsers(local);
        return { success: true, merged, total: Object.keys(remote).length };
    } catch (e) {
        return { success: false, error: e.message };
    }
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
    return { version: '0.0.0', clientVersion: '0.0.0', launcherVersion: '0.0.0' };
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

        const remoteLauncherVer = remote.launcherVersion || '0.0.0';
        const localLauncherVer = local.launcherVersion || '0.0.0';

        const rlParts = remoteLauncherVer.split('.').map(Number);
        const llParts = localLauncherVer.split('.').map(Number);

        let hasLauncherUpdate = false;
        for (let i = 0; i < 3; i++) {
            const r = rlParts[i] || 0;
            const l = llParts[i] || 0;
            if (r > l) { hasLauncherUpdate = true; break; }
            if (r < l) break;
        }

        return {
            hasUpdate,
            hasLauncherUpdate,
            version: remoteVersion,
            launcherVersion: remoteLauncherVer,
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

ipcMain.handle('update:setVersion', (event, { version, clientVersion, launcherVersion }) => {
    const local = loadLocalVersion();
    if (version) local.version = version;
    if (clientVersion) local.clientVersion = clientVersion;
    if (launcherVersion) local.launcherVersion = launcherVersion;
    local.lastUpdated = Date.now();
    saveLocalVersion(local);
    return true;
});

ipcMain.handle('update:getLocalVersion', () => {
    return loadLocalVersion();
});

// --- Launcher self-update ---
function downloadFileWithProgress(url, dest, onProgress) {
    return new Promise((resolve, reject) => {
        const client = url.startsWith('https') ? https : http;
        client.get(url, { timeout: 120000 }, (res) => {
            if (res.statusCode === 301 || res.statusCode === 302) {
                return downloadFileWithProgress(res.headers.location, dest, onProgress).then(resolve).catch(reject);
            }
            if (res.statusCode !== 200) return reject(new Error('HTTP ' + res.statusCode));

            const totalBytes = parseInt(res.headers['content-length'], 10) || 0;
            let receivedBytes = 0;
            const fileStream = fs.createWriteStream(dest);

            res.on('data', (chunk) => {
                receivedBytes += chunk.length;
                fileStream.write(chunk);
                if (onProgress) onProgress(receivedBytes, totalBytes);
            });

            res.on('end', () => {
                fileStream.end(() => {
                    fileStream.close();
                    resolve();
                });
            });

            res.on('error', (e) => {
                fileStream.close();
                reject(e);
            });
        }).on('error', reject);
    });
}

ipcMain.on('update:launcher-download', async (event) => {
    try {
        const remoteJson = await fetchUrl(VERSION_URL);
        const remote = JSON.parse(remoteJson);

        if (!remote.launcherUrl) {
            event.reply('update:download-status', { status: 'error', message: 'Нет ссылки для скачивания' });
            return;
        }

        const tempPath = path.join(app.getPath('temp'), 'ExcelClient-update.exe');

        await downloadFileWithProgress(remote.launcherUrl, tempPath, (received, total) => {
            const percent = total > 0 ? Math.round((received / total) * 100) : -1;
            event.reply('update:download-status', {
                status: 'downloading',
                percent,
                receivedBytes: received,
                totalBytes: total
            });
        });

        const originalDir = process.env.PORTABLE_EXECUTABLE_DIR || '';
        if (originalDir && fs.existsSync(path.join(originalDir, 'ExcelClient.exe'))) {
            try {
                fs.copyFileSync(tempPath, path.join(originalDir, 'ExcelClient.exe'));
                fs.unlinkSync(tempPath);
            } catch (e) {
                event.reply('update:download-status', { status: 'error', message: 'Не удалось заменить файл: ' + e.message });
                return;
            }
        }

        const local = loadLocalVersion();
        local.launcherVersion = remote.launcherVersion;
        saveLocalVersion(local);

        event.reply('update:download-status', { status: 'done', message: 'Launcher updated' });

        setTimeout(() => { app.quit(); }, 1500);
    } catch (e) {
        event.reply('update:download-status', { status: 'error', message: e.message });
    }
});

// === SUBSCRIPTION HANDLERS ===

ipcMain.handle('license:activate', async (event, { key, username }) => {
    const hwid = getHardwareId();
    let result = validateKeyLocal(key, hwid);

    // If key not found locally, try remote validation against subscription site API
    if (!result.valid && result.error === 'Key not found') {
        const remote = await validateKeyRemote(key, hwid);
        if (remote && remote.valid) {
            const db = loadLicenseDB();
            db[key] = {
                plan: remote.plan,
                days: remote.daysTotal || 30,
                email: remote.email || '',
                nick: remote.nick || username || '',
                createdAt: Date.now(),
                expiresAt: remote.expiresAt,
                hwid
            };
            saveLicenseDB(db);
            result = { valid: true, plan: remote.plan, daysTotal: remote.daysTotal, expiresAt: remote.expiresAt };
        }
    }

    if (result.valid) {
        const db = loadLicenseDB();
        if (db[key]) {
            if (username) db[key].nick = username;
        } else {
            db[key] = { plan: result.plan, days: result.daysTotal || 30, createdAt: Date.now(), expiresAt: result.expiresAt, hwid, email: '', nick: username || '' };
        }
        saveLicenseDB(db);
        saveLicense({ key, plan: result.plan, expiresAt: result.expiresAt, activatedAt: Date.now(), hwid });
        if (username) {
            const linksPath = path.join(app.getPath('userData'), 'bot_links.json');
            try {
                if (fs.existsSync(linksPath)) {
                    const links = JSON.parse(fs.readFileSync(linksPath, 'utf8'));
                    const target = Object.values(links).find(c => c);
                    if (target) botSend(target, '🟢 Активация: `' + username + '`\nКлюч: `' + key + '`\nПлан: ' + result.plan);
                }
            } catch (e) {}
        }
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

ipcMain.handle('license:generate', async (event, { plan, days }) => {
    const result = generateLicenseKey(plan, parseInt(days));
    return result;
});

ipcMain.handle('resourcepacks:install', async (event, { url, name }) => {
    try {
        const rpDir = path.join(app.getPath('userData'), '.minecraft', 'resourcepacks');
        if (!fs.existsSync(rpDir)) fs.mkdirSync(rpDir, { recursive: true });
        const dest = path.join(rpDir, name);
        await downloadFile(url, dest);
        return { success: true };
    } catch (e) {
        return { success: false, error: e.message };
    }
});

ipcMain.handle('resourcepacks:list', () => {
    try {
        const rpDir = path.join(app.getPath('userData'), '.minecraft', 'resourcepacks');
        if (!fs.existsSync(rpDir)) return { packs: [] };
        const files = fs.readdirSync(rpDir).filter(f => f.endsWith('.zip'));
        return { packs: files };
    } catch (e) {
        return { packs: [] };
    }
});

ipcMain.handle('resourcepacks:remove', async (event, { name }) => {
    try {
        const rpDir = path.join(app.getPath('userData'), '.minecraft', 'resourcepacks');
        const filePath = path.join(rpDir, name);
        if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
        return { success: true };
    } catch (e) {
        return { success: false, error: e.message };
    }
});

ipcMain.on('game:launch', async (event, { nickname, ram, server }) => {
    log('=== LAUNCH START (Direct) ===' + (server ? ' server=' + server : ''));

    killExistingGameProcesses(log);

    const gameDir = path.join(app.getPath('userData'), '.minecraft');
    const launcher = new MinecraftLauncher(gameDir, event, log);

    try {
        await launcher.launch(nickname, ram, server);
    } catch (e) {
        log('Launch error: ' + e.message);
    }

    // Destroy tray and close window so launcher disappears from taskbar/taskmgr
    try { if (tray) { tray.destroy(); tray = null; } } catch (e) {}
    try { if (win) { win.destroy(); win = null; } } catch (e) {}

    app.exit(0);
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
            const downloadedPath = path.join(app.getPath('temp'), 'ExcelClient-update.exe');

            await downloadFile(remote.launcherUrl, downloadedPath);

            if (originalDir && fs.existsSync(path.join(originalDir, 'ExcelClient.exe'))) {
                try {
                    fs.copyFileSync(downloadedPath, path.join(originalDir, 'ExcelClient.exe'));
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



// === INLINE TELEGRAM BOT ===
let botEnabled = false;
let botInterval = null;
let botOffset = 0;
let _botCfg = null;

function loadBotConfig() {
    if (_botCfg) return _botCfg;
    const cfgPath = path.join(app.getPath('userData'), 'bot.config.json');
    const devPath = path.join(__dirname, 'bot.config.json');
    // Try userData copy first, but always merge adminPassword from dev config
    try {
        if (fs.existsSync(cfgPath)) {
            _botCfg = JSON.parse(fs.readFileSync(cfgPath, 'utf8'));
            // Merge adminPassword from dev config to ensure it's always up-to-date
            try {
                if (fs.existsSync(devPath)) {
                    const devCfg = JSON.parse(fs.readFileSync(devPath, 'utf8'));
                    if (devCfg.adminPassword) {
                        _botCfg.adminPassword = devCfg.adminPassword;
                        fs.writeFileSync(cfgPath, JSON.stringify(_botCfg, null, 2));
                    }
                }
            } catch (e) {}
            return _botCfg;
        }
    } catch (e) { console.error('[bot] load config error:', e.message); }
    try {
        if (fs.existsSync(devPath)) {
            const cfg = JSON.parse(fs.readFileSync(devPath, 'utf8'));
            fs.mkdirSync(path.dirname(cfgPath), { recursive: true });
            fs.writeFileSync(cfgPath, JSON.stringify(cfg, null, 2));
            _botCfg = cfg;
            return _botCfg;
        }
    } catch (e) { console.error('[bot] load dev config error:', e.message); }
    return null;
}
function clearBotConfigCache() { _botCfg = null; }

async function botApi(method, body) {
    try {
        const cfg = loadBotConfig();
        if (!cfg || !cfg.token) return { ok: false };
        const data = body ? JSON.stringify(body) : '';
        const url = 'https://api.telegram.org/bot' + cfg.token + '/' + method;
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 15000);
        const res = await net.fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: data || undefined,
            signal: controller.signal
        });
        clearTimeout(timeout);
        const txt = await res.text();
        return JSON.parse(txt);
    } catch (e) {
        console.error('[bot] API error:', e.message);
        return { ok: false };
    }
}

function botSend(chatId, text) {
    return botApi('sendMessage', { chat_id: chatId, text, parse_mode: 'Markdown' });
}

function botGenKey() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    return 'RM-' + Array.from({ length: 3 }, () =>
        Array.from({ length: 4 }, () => chars[crypto.randomInt(chars.length)]).join('')
    ).join('-');
}

async function botHandle(cmd, chatId, username) {
    const parts = cmd.split(' ');
    const c = parts[0].toLowerCase();

    const cfg = loadBotConfig();
    const admins = (cfg && cfg.admins) || [];
    const isAdmin = admins.includes(username) || admins.includes(String(chatId));

    const linksPath = path.join(app.getPath('userData'), 'bot_links.json');
    function loadLinks() { try { if (fs.existsSync(linksPath)) return JSON.parse(fs.readFileSync(linksPath, 'utf8')); } catch (e) {} return {}; }
    function saveLinks(l) { try { fs.writeFileSync(linksPath, JSON.stringify(l, null, 2)); } catch (e) {} }

    // Read licenses from the same DB as the launcher
    const licenses = loadLicenseDB();

    const links = loadLinks();
    const linkedUser = Object.keys(links).find(k => links[k] === chatId);

    if (c === '/start') {
        const arg = parts.slice(1).join(' ');
        if (arg) {
            links[arg.toLowerCase()] = chatId;
            saveLinks(links);
            return botSend(chatId, '✅ Аккаунт `' + arg + '` привязан!');
        }
        return botSend(chatId,
            '*Excel Client Bot*\n\n'
            + 'Привяжи аккаунт: `/start твой_логин`\n\n'
            + '*/key* — получить ключ\n'
            + '*/status* — статус подписки\n'

            + (isAdmin ? '_Админ:_ `/genkey`, `/ban`, `/unban`, `/stats`, `/broadcast`, `/notify`\n' : '')
        );
    }

    if (c === '/key') {
        if (!linkedUser) return botSend(chatId, '❌ Привяжи аккаунт: `/start твой_логин`');
        let userKeys = Object.entries(licenses).filter(([k, v]) =>
            (v.email || '').toLowerCase() === linkedUser.toLowerCase() ||
            (v.nick || '').toLowerCase() === linkedUser.toLowerCase() ||
            k.toLowerCase().includes(linkedUser.toLowerCase())
        );
        if (userKeys.length === 0) {
            const local = loadLicense();
            if (local && local.key) {
                if (licenses[local.key]) {
                    licenses[local.key].nick = linkedUser;
                    saveLicenseDB(licenses);
                    userKeys = [[local.key, licenses[local.key]]];
                } else {
                    userKeys = [[local.key, {
                        plan: local.plan || 'beta',
                        expiresAt: local.expiresAt || 0,
                        days: local.days || 30,
                        hwid: local.hwid || null,
                        email: '',
                        nick: linkedUser
                    }]];
                    if (userKeys[0][1].expiresAt > Date.now()) {
                        licenses[local.key] = userKeys[0][1];
                        saveLicenseDB(licenses);
                    }
                }
            }
        }
        if (userKeys.length === 0) return botSend(chatId, '❌ Нет активных ключей.');
        const active = userKeys.find(([k, v]) => Date.now() < v.expiresAt);
        if (!active) return botSend(chatId, '❌ Все ключи истекли.');
        return botSend(chatId, '🔑 `' + active[0] + '`\nПлан: ' + active[1].plan + '\nДо: ' + new Date(active[1].expiresAt).toLocaleDateString('ru-RU'));
    }

    if (c === '/status') {
        if (!linkedUser) return botSend(chatId, '❌ Привяжи аккаунт: `/start твой_логин`');
        let userKeys = Object.entries(licenses).filter(([k, v]) =>
            (v.email || '').toLowerCase() === linkedUser.toLowerCase() ||
            (v.nick || '').toLowerCase() === linkedUser.toLowerCase() ||
            k.toLowerCase().includes(linkedUser.toLowerCase())
        );
        if (userKeys.length === 0) {
            const local = loadLicense();
            if (local && local.key) {
                if (licenses[local.key]) {
                    licenses[local.key].nick = linkedUser;
                    saveLicenseDB(licenses);
                    userKeys = [[local.key, licenses[local.key]]];
                } else {
                    userKeys = [[local.key, {
                        plan: local.plan || 'beta',
                        expiresAt: local.expiresAt || 0,
                        days: local.days || 30,
                        hwid: local.hwid || null,
                        email: '',
                        nick: linkedUser
                    }]];
                    if (userKeys[0][1].expiresAt > Date.now()) {
                        licenses[local.key] = userKeys[0][1];
                        saveLicenseDB(licenses);
                    }
                }
            }
        }
        if (userKeys.length === 0) return botSend(chatId, '📭 Нет ключей.');
        let msg = '*Твои ключи:*\n';
        userKeys.forEach(([key, data]) => {
            const ok = Date.now() < data.expiresAt;
            msg += '\n' + (ok ? '✅' : '❌') + ' `' + key + '` (' + data.plan + ') до ' + new Date(data.expiresAt).toLocaleDateString('ru-RU');
        });
        return botSend(chatId, msg);
    }

    if (!isAdmin) return;

    if (c === '/genkey') {
        const plan = parts[1] || 'beta';
        const days = parseInt(parts[2]) || 30;
        if (!['stable', 'beta', 'alpha'].includes(plan)) return botSend(chatId, '❌ План: stable/beta/alpha');
        const key = botGenKey();
        const expiresAt = days === 9999 ? Date.now() + 3650 * 86400000 : Date.now() + days * 86400000;
        licenses[key] = { plan, days, createdAt: Date.now(), expiresAt, hwid: null, email: parts[3] || '', nick: parts[4] || '' };
        saveLicenseDB(licenses);
        return botSend(chatId, '✅ Ключ создан:\n`' + key + '`\nПлан: ' + plan + '\nДней: ' + days + (parts[4] ? '\nНик: ' + parts[4] : ''));
    }

    if (c === '/ban') {
        const target = parts.slice(1).join(' ');
        if (!target) return botSend(chatId, '❌ Укажи ключ или логин.');
        if (licenses[target]) {
            licenses[target].banned = true;
            saveLicenseDB(licenses);
            return botSend(chatId, '🔨 Ключ `' + target + '` забанен.');
        }
        return botSend(chatId, '🔨 `' + target + '` добавлен в бан-лист (ключ не найден в БД).');
    }

    if (c === '/unban') {
        const target = parts.slice(1).join(' ');
        if (!target) return botSend(chatId, '❌ Укажи ключ или логин.');
        if (licenses[target]) {
            delete licenses[target].banned;
            saveLicenseDB(licenses);
            return botSend(chatId, '✅ `' + target + '` разбанен.');
        }
        return botSend(chatId, '✅ `' + target + '` разбанен (не был в БД).');
    }

    if (c === '/stats') {
        const keys = Object.values(licenses);
        const total = keys.length;
        const active = keys.filter(k => !k.banned && Date.now() < k.expiresAt).length;
        const expired = keys.filter(k => Date.now() > k.expiresAt).length;
        const banned = keys.filter(k => k.banned).length;
        const byPlan = {};
        keys.forEach(k => { byPlan[k.plan] = (byPlan[k.plan] || 0) + 1; });
        let msg = '*📊 Статистика*\nВсего: ' + total + ' | ✅ ' + active + ' | ❌ ' + expired + ' | 🔨 ' + banned + '\n\n*По планам:*\n';
        Object.entries(byPlan).forEach(([p, c]) => { msg += p + ': ' + c + '\n'; });
        const lPath = path.join(app.getPath('userData'), 'bot_links.json');
        let linkCount = 0;
        try { if (fs.existsSync(lPath)) linkCount = Object.keys(JSON.parse(fs.readFileSync(lPath, 'utf8'))).length; } catch (e) {}
        msg += '\n👥 Привязано: ' + linkCount;
        return botSend(chatId, msg);
    }

    if (c === '/broadcast' && isAdmin) {
        const text = parts.slice(1).join(' ');
        if (!text) return botSend(chatId, '❌ Укажи текст: `/broadcast Сообщение всем`');
        const links = loadLinks();
        const targets = Object.values(links);
        if (targets.length === 0) return botSend(chatId, '📭 Нет привязанных аккаунтов.');
        let sent = 0;
        for (const cid of targets) {
            try { await botSend(cid, '📢 *Важно:* ' + text); sent++; } catch (e) {}
        }
        return botSend(chatId, '✅ Разослано ' + sent + '/' + targets.length + ' пользователям.');
    }

    if (c === '/notify' && isAdmin) {
        const text = parts.slice(1).join(' ');
        if (!text) return botSend(chatId, '❌ Укажи текст: `/notify Сообщение`');
        addNotification('📢 Уведомление', text, 'info');
        const links = loadLinks();
        const targets = Object.values(links);
        let sent = 0;
        for (const cid of targets) {
            try { await botSend(cid, '📢 *' + text + '*'); sent++; } catch (e) {}
        }
        return botSend(chatId, '✅ Оповещение сохранено и разослано ' + sent + '/' + targets.length + ' пользователям.');
    }
}

function botPoll() {
    const startTime = Date.now();
    botApi('getUpdates', { offset: botOffset, timeout: 10 }).then(res => {
        if (!res.ok) {
            console.error('[bot] API returned error:', JSON.stringify(res).slice(0, 200));
            return;
        }
        if (!res.result || res.result.length === 0) return;
        for (const upd of res.result) {
            botOffset = upd.update_id + 1;
            const msg = upd.message;
            if (!msg || !msg.text) continue;
            const chatId = msg.chat.id;
            const username = msg.from.username || msg.from.first_name || 'unknown';
            console.log('[bot] <<', msg.text, 'from', username, 'chat', chatId);
            // Capture the current botHandle for the promise chain
            const handleFn = botHandle;
            handleFn(msg.text, chatId, username).catch(e => {
                console.error('[bot] cmd error:', e.message);
                botSend(chatId, '⚠️ ' + e.message);
            });
        }
    }).catch(e => {
        console.error('[bot] poll error:', e.message);
    });
}

function startBot() {
    if (botInterval) return;
    const cfg = loadBotConfig();
    if (!cfg || !cfg.token) { console.log('[bot] no token, disabled'); return; }
    botEnabled = true;
    botInterval = setInterval(botPoll, 1500);
    botPoll();
    console.log('[bot] inline polling started');
}

function stopBot() {
    botEnabled = false;
    if (botInterval) { clearInterval(botInterval); botInterval = null; }
    console.log('[bot] inline polling stopped');
}

ipcMain.handle('bot:toggle', async (event, enable) => {
    if (enable) startBot();
    else stopBot();
    return true;
});

ipcMain.handle('bot:status', () => botInterval !== null);

// --- CHAT WebSocket Server ---
let g_chatWss = null;
let g_chatInstanceId = require('crypto').randomBytes(4).toString('hex');
let g_recentChatIds = new Set();

function startChatServer() {
    try {
        const { WebSocketServer, WebSocket } = require('ws');
        g_chatWss = new WebSocketServer({ port: 4000 });
        console.log('[chat] WS server on port 4000');

        g_chatWss.on('connection', (ws) => {
            let username = null;
            console.log('[chat] client connected');

            ws.on('message', (raw) => {
                try {
                    const msg = JSON.parse(raw.toString());
                    if (msg.type === 'auth') {
                        username = msg.username;
                        const sys = { type: 'system', text: username + ' присоединился к чату', time: Date.now() };
                        broadcastChat(sys, ws);
                        return;
                    }
                    if (msg.type === 'message' && username) {
                        const payload = { type: 'message', username, text: msg.text, time: Date.now(), id: g_chatInstanceId + '_' + Date.now() };
                        broadcastChat(payload, null);
                    }
                } catch (e) {}
            });

            ws.on('close', () => {
                if (username) {
                    const sys = { type: 'system', text: username + ' покинул чат', time: Date.now() };
                    broadcastChat(sys, null);
                }
            });
        });

        g_chatWss.on('error', (e) => {
            console.log('[chat] WS error:', e.message);
        });

    } catch (e) {
        console.log('[chat] failed to start:', e.message);
    }
}

function broadcastChat(msg, exclude) {
    if (!g_chatWss) return;
    const str = JSON.stringify(msg);
    g_chatWss.clients.forEach(client => {
        if (client !== exclude && client.readyState === 1) {
            try { client.send(str); } catch (e) {}
        }
    });
}

function stopChatServer() {
    if (g_chatWss) {
        g_chatWss.close();
        g_chatWss = null;
        console.log('[chat] server stopped');
    }
}

// Wrap botHandle to intercept /chat messages for cross-instance relay
const _origBotHandle = botHandle;
botHandle = async function(cmd, chatId, username) {
    const parts = cmd.split(' ');
    if (parts[0] === '/chat' && parts.length >= 3) {
        try {
            const rest = parts.slice(1).join(' ');
            const payload = JSON.parse(rest);
            if (payload.id && payload.id.startsWith(g_chatInstanceId)) return;
            broadcastChat(payload, null);
        } catch (e) {
            const relayUser = parts[1];
            const relayText = parts.slice(2).join(' ');
            if (relayUser && relayText) {
                broadcastChat({ type: 'message', username: relayUser, text: relayText, time: Date.now() }, null);
            }
        }
        return;
    }
    if (_origBotHandle) return _origBotHandle(cmd, chatId, username);
};

app.whenReady().then(() => {
    startLicenseServer();
    startChatServer();
    createWindow();
    createTray();
    startBot();
    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
});

app.on('before-quit', () => {
    stopBot();
    stopChatServer();
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
});
