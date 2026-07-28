const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const https = require('https');
const http = require('http');
const { spawn, execSync, exec } = require('child_process');
const MinecraftLauncher = require('./mc-launcher');

let win;
let g_licenseServer = null;
let g_vkHandlerSetup = false;
let g_vkExchangeCode = null;
let g_vkResolve = null;

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
const VERSION_URL = 'https://raw.githubusercontent.com/jeosmertnik-collab/Excel-Client/main/version.json';
const REMOTE_USERS_URL = 'https://raw.githubusercontent.com/jeosmertnik-collab/Excel-Client/main/users.json';
const REMOTE_USERS_API = 'https://api.github.com/repos/jeosmertnik-collab/Rich-Modern/contents/users.json';
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
'<div class="license-key" id="licenseKeyDisplay" onclick="copyLicense()">RM-XXXX-XXXX-XXXX-XXXX</div>' +
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
'async function processPayment(){const email=document.getElementById("emailInput").value.trim();if(!email||!email.includes("@")){alert("Введите корректный email");return}const btn=document.getElementById("payBtn");btn.disabled=true;btn.textContent="Обработка...";const cacheKey=selectedPlan+"_"+selectedDays+"_"+email;let key=localStorage.getItem("rm_key_"+cacheKey);if(!key){key=generateLicenseKey(selectedPlan,selectedDays,email);localStorage.setItem("rm_key_"+cacheKey,key)}await new Promise(r=>setTimeout(r,2000));document.getElementById("licenseKeyDisplay").textContent=key;document.getElementById("checkoutForm").style.display="none";document.getElementById("successContent").style.display="";btn.disabled=false;btn.textContent="Оплатить"}' +
'function generateLicenseKey(plan,days,email){const chars="ABCDEFGHJKLMNPQRSTUVWXYZ23456789",nonce=Date.now().toString(36)+Math.random().toString(36).substring(2,8);let seed=0;for(let i=0;i<(plan+days+email+nonce+"rich-modern-secret-2026").length;i++)seed=(seed<<5)-seed+(plan+days+email+nonce+"rich-modern-secret-2026").charCodeAt(i)|0;const rand=()=>{seed=(seed*1103515245+12345)&2147483647;return seed},seg=()=>{let s="";for(let i=0;i<4;i++)s+=chars[rand()%chars.length];return s},planCode={stable:"ST",beta:"BT",alpha:"AL"}[plan],daysCode=days.toString(16).toUpperCase().padStart(2,"0");return"RM-"+planCode+daysCode+"-"+seg()+"-"+seg()+"-"+seg()}' +
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
            } else if (req.url.startsWith('/vk_callback') && req.method === 'GET') {
                res.writeHead(200, { 'Content-Type': 'text/html' });
                res.end('<html><body><h2>VK авторизация успешна!</h2><p>Закройте вкладку.</p><script>window.close()</script></body></html>');
                const url = new URL(req.url, 'http://localhost:3000');
                const code = url.searchParams.get('code');
                if (code && g_vkExchangeCode) {
                    g_vkExchangeCode(code).then(token => {
                        if (token && g_vkResolve) { g_vkResolve(token); g_vkResolve = null; }
                    });
                }
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
    win.on('closed', () => {
        try { killExistingGameProcesses(log); } catch (e) {}
        app.quit();
    });
}

app.on('before-quit', () => {
    try { killExistingGameProcesses(log); } catch (e) {}
});

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

ipcMain.handle('vk:getToken', () => {
    const dirs = ['Excel', 'Rich'];
    for (const dir of dirs) {
        const tokenFile = path.join(app.getPath('userData'), '.minecraft', dir, 'configs', 'vk_token.txt');
        try {
            if (fs.existsSync(tokenFile)) return fs.readFileSync(tokenFile, 'utf8').trim();
        } catch (e) {}
    }
    return '';
});

ipcMain.handle('vk:removeToken', () => {
    const tokenFile = path.join(app.getPath('userData'), '.minecraft', 'Excel', 'configs', 'vk_token.txt');
    try {
        if (fs.existsSync(tokenFile)) fs.unlinkSync(tokenFile);
        // Also remove old path
        const oldFile = path.join(app.getPath('userData'), '.minecraft', 'Rich', 'configs', 'vk_token.txt');
        try { if (fs.existsSync(oldFile)) fs.unlinkSync(oldFile); } catch (e) {}
        log('VK token removed');
    } catch (e) {}
});

ipcMain.handle('vk:login', async () => {
    const VK_APP_ID = '2274003';
    const VK_APP_SECRET = 'hHbJjNqNJmTqiPvT';
    const scope = 'audio,offline';
    const redirectUri = 'http://localhost:3000/vk_callback';
    const authUrl = `https://oauth.vk.com/authorize?client_id=${VK_APP_ID}&display=page&redirect_uri=${redirectUri}&scope=${scope}&response_type=code&v=5.131&revoke=1`;

    function saveToken(token) {
        const tokenFile = path.join(app.getPath('userData'), '.minecraft', 'Excel', 'configs', 'vk_token.txt');
        try {
            const dir = path.dirname(tokenFile);
            if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
            fs.writeFileSync(tokenFile, token, 'utf8');
            log('VK token saved');
            return true;
        } catch (e) {
            log('VK token save error: ' + e.message);
            return false;
        }
    }

    async function exchangeCode(code) {
        try {
            const tokenUrl = `https://oauth.vk.com/access_token?client_id=${VK_APP_ID}&client_secret=${VK_APP_SECRET}&redirect_uri=${redirectUri}&code=${code}`;
            const resp = await fetch(tokenUrl);
            const data = await resp.json();
            if (data.access_token) {
                saveToken(data.access_token);
                return data.access_token;
            }
            log('VK exchange error: ' + JSON.stringify(data));
            return '';
        } catch (e) {
            log('VK exchange exception: ' + e.message);
            return '';
        }
    }

    // Set up globals so the permanent /vk_callback route can use them
    g_vkExchangeCode = exchangeCode;

    return new Promise((resolve) => {
        let resolved = false;
        const done = (token) => { if (!resolved) { resolved = true; g_vkResolve = null; resolve(token); } };
        g_vkResolve = done;

        // Open auth in BrowserWindow
        const authWin = new BrowserWindow({
            width: 800, height: 600,
            title: 'VK Login',
            webPreferences: { nodeIntegration: false, contextIsolation: true }
        });

        authWin.loadURL(authUrl).catch(err => {
            log('VK BrowserWindow failed: ' + err.message);
            const { shell } = require('electron');
            shell.openExternal(authUrl);
        });

        const checkUrl = async (url) => {
            if (url.includes('code=')) {
                try {
                    const code = new URL(url).searchParams.get('code');
                    if (code) { const t = await exchangeCode(code); if (t) done(t); }
                } catch (e) {}
            }
        };

        authWin.webContents.on('will-redirect', async (e, url) => checkUrl(url));
        authWin.webContents.on('will-navigate', async (e, url) => checkUrl(url));
        authWin.webContents.on('did-navigate', async (e, url) => checkUrl(url));

        const filter = { urls: [redirectUri + '*'] };
        authWin.webContents.session.webRequest.onBeforeRequest(async (details, callback) => {
            if (details.url.includes('code=')) {
                try {
                    const code = new URL(details.url).searchParams.get('code');
                    if (code) { const t = await exchangeCode(code); if (t) { done(t); authWin.close(); return callback({ cancel: true }); } }
                } catch (e) {}
            }
            callback({});
        });

        authWin.on('closed', () => setTimeout(() => done(''), 3000));
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
