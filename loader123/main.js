const { ipcRenderer } = require('electron');

document.addEventListener('DOMContentLoaded', () => {
    const win = document.getElementById('launcherWindow');
    const bg = document.getElementById('bgCanvas');
    if (bg) {
        bg.onload = () => { bg.classList.add('loaded'); };
        if (bg.complete) bg.classList.add('loaded');
    }
    requestAnimationFrame(() => {
        if (win) win.style.opacity = '1';
    });

    let currentLanguage = localStorage.getItem('launcher_lang') || 'ru';
    let currentUser = null;
    let isRegisterMode = false;
    let pendingUpdate = null;

    const langSelectorBtn = document.getElementById('langSelectorBtn');
    const langDropdownMenu = document.getElementById('langDropdownMenu');
    const currentLangText = document.getElementById('currentLangText');
    const currentLangFlag = document.getElementById('currentLangFlag');
    const langOptions = document.querySelectorAll('.lang-option');

    const authScreen = document.getElementById('authScreen');
    const authCard = document.getElementById('authCard');
    const loginUser = document.getElementById('loginUser');
    const loginPassword = document.getElementById('loginPassword');
    const pwdToggle = document.getElementById('pwdToggle');
    const eyeIcon = document.getElementById('eyeIcon');
    const loginGroup = document.getElementById('loginGroup');
    const pswdGroup = document.getElementById('pwdGroup');
    const errorHint = document.getElementById('errorHint');
    const authTitle = document.getElementById('authTitle');
    const authSubtitle = document.getElementById('authSubtitle');
    const authSubmitBtn = document.getElementById('authSubmitBtn');
    const authSwitchBtn = document.getElementById('authSwitchBtn');
        const confirmGroup = document.getElementById('confirmGroup');
        const emailGroup = document.getElementById('emailGroup');
        const loginEmail = document.getElementById('loginEmail');
    const loginConfirm = document.getElementById('loginConfirm');
    const rememberCheck = document.getElementById('rememberCheck');

    const navItems = document.querySelectorAll('.nav-item');
    const screens = document.querySelectorAll('.screen');
    const screenMain = document.getElementById('screen-main');
    const clientCard = document.getElementById('clientCard');
    const clientDetailScreen = document.getElementById('clientDetailScreen');
    const backToCardsBtn = document.getElementById('backToCardsBtn');
    const launchGameBtn = document.getElementById('launchGameBtn');
    const launchStatus = document.getElementById('launchStatus');

    const winMinimize = document.getElementById('windowMinimize');
    const winClose = document.getElementById('windowClose');
    const logoutBtn = document.getElementById('logoutBtn');
    const uploadAvatarBtn = document.getElementById('uploadAvatarBtn');
    const userAvatar = document.getElementById('userAvatar');
    const ramSlider = document.getElementById('ramSlider');
    const ramValue = document.getElementById('ramValue');

    const profileLoginEl = document.getElementById('profileLoginText');
    const profileUidEl = document.getElementById('profileUidText');

    if (winMinimize) winMinimize.addEventListener('click', () => ipcRenderer.send('window-minimize'));
    if (winClose) winClose.addEventListener('click', () => ipcRenderer.send('window-close'));

    if (uploadAvatarBtn) uploadAvatarBtn.addEventListener('click', () => ipcRenderer.send('open-avatar-dialog'));
    ipcRenderer.on('selected-avatar', (event, filePath) => {
        if (userAvatar) userAvatar.style.backgroundImage = `url('file://${filePath.replace(/\\/g, '/')}')`;
    });

    async function loadTranslations(lang) {
        try {
            const response = await fetch(`./locales/${lang}.json`);
            if (!response.ok) throw new Error('Locale load failed');
            return await response.json();
        } catch (err) {
            console.error('Locale error:', err);
            return {};
        }
    }

    function applyTranslations(t) {
        document.querySelectorAll('[data-i18n]').forEach(el => {
            const key = el.getAttribute('data-i18n');
            if (t[key]) {
                if (el.querySelector('svg')) {
                    el.innerHTML = el.querySelector('svg').outerHTML + ' ' + t[key];
                } else {
                    el.textContent = t[key];
                }
            }
        });
        document.querySelectorAll('[data-i18n-placeholder]').forEach(input => {
            const key = input.getAttribute('data-i18n-placeholder');
            if (t[key]) input.placeholder = t[key];
        });
    }

    async function changeLanguage(lang) {
        const t = await loadTranslations(lang);
        applyTranslations(t);
        localStorage.setItem('launcher_lang', lang);
        currentLanguage = lang;
        if (currentLangText) currentLangText.textContent = lang === 'ru' ? 'Русский' : 'English';
        if (currentLangFlag) currentLangFlag.textContent = lang === 'ru' ? '🇷🇺' : '🇺🇸';
        updateAuthModeTexts(t);
    }

    function updateAuthModeTexts(t) {
        if (!t) return;
        if (isRegisterMode) {
            if (authTitle) authTitle.textContent = t.register_title || 'Registration';
            if (authSubtitle) authSubtitle.textContent = t.register_subtitle || 'Create an account';
            if (authSubmitBtn) authSubmitBtn.textContent = t.btn_register || 'REGISTER';
            if (authSwitchBtn) authSwitchBtn.textContent = t.btn_to_login || 'Already have an account? Login';
            if (confirmGroup) confirmGroup.style.display = 'block';
            if (emailGroup) emailGroup.style.display = 'block';
        } else {
            if (authTitle) authTitle.textContent = t.auth_title || 'Authorization';
            if (authSubtitle) authSubtitle.textContent = t.auth_subtitle || 'Enter your credentials';
            if (authSubmitBtn) authSubmitBtn.textContent = t.btn_login || 'LOGIN';
            if (authSwitchBtn) authSwitchBtn.textContent = t.btn_to_register || 'No account? Register';
            if (confirmGroup) confirmGroup.style.display = 'none';
            if (emailGroup) emailGroup.style.display = 'none';
        }
        if (errorHint) errorHint.style.display = 'none';
    }

    changeLanguage(currentLanguage);

    // Auto-login
    const savedUser = localStorage.getItem('launcher_remember');
    const accounts = loadAccounts();
    if (savedUser) {
        const found = accounts.find(a => a.user === savedUser);
        if (found) {
            loginAs(found.user, found.pass || '');
        } else if (accounts.length > 0) {
            loginAs(accounts[0].user, accounts[0].pass || '');
        }
    } else if (accounts.length > 0) {
        loginAs(accounts[0].user, accounts[0].pass || '');
    }

    if (langSelectorBtn && langDropdownMenu) {
        langSelectorBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            langDropdownMenu.style.display = langDropdownMenu.style.display === 'block' ? 'none' : 'block';
        });
        document.addEventListener('click', () => { if (langDropdownMenu) langDropdownMenu.style.display = 'none'; });
    }

    langOptions.forEach(option => {
        option.addEventListener('click', (e) => {
            e.stopPropagation();
            const selectedLang = option.getAttribute('data-lang');
            if (selectedLang !== currentLanguage) changeLanguage(selectedLang);
            if (langDropdownMenu) langDropdownMenu.style.display = 'none';
        });
    });

    if (authSwitchBtn) {
        authSwitchBtn.addEventListener('click', async () => {
            isRegisterMode = !isRegisterMode;
            const t = await loadTranslations(currentLanguage);
            updateAuthModeTexts(t);
            if (loginUser) loginUser.value = '';
            if (loginPassword) loginPassword.value = '';
            if (loginConfirm) loginConfirm.value = '';
        });
    }

    if (authSubmitBtn) {
        authSubmitBtn.addEventListener('click', async () => {
            const username = loginUser.value.trim();
            const password = loginPassword.value;
            const t = await loadTranslations(currentLanguage);

            if (username.length < 3) {
                showError(t.error_short || 'Minimum 3 characters');
                return;
            }

            if (isRegisterMode) {
                const confirm = loginConfirm.value;
                const email = loginEmail ? loginEmail.value.trim() : '';
                if (password.length < 3) {
                    showError(t.error_short || 'Minimum 3 characters');
                    return;
                }
                if (password === username) {
                    showError(t.error_same || 'Username and password cannot be the same');
                    return;
                }
                if (password !== confirm) {
                    showError(t.error_passwords || 'Passwords do not match');
                    return;
                }
                if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
                    showError('Invalid email format');
                    return;
                }
                const result = await ipcRenderer.invoke('auth:register', { login: username, password, email });
                if (!result.success) {
                    showError(t.error_exists || 'Account already exists');
                    return;
                }
                loginAs(username, password);
            } else {
                if (!password) {
                    showError(t.error_auth || 'Invalid username or password.');
                    return;
                }
                const result = await ipcRenderer.invoke('auth:login', { login: username, password });
                if (!result.success) {
                    showError(t.error_auth || 'Invalid username or password.');
                    return;
                }
                loginAs(result.user.login, password);
            }
        });
    }

    function showError(msg) {
        if (errorHint) { errorHint.textContent = msg; errorHint.style.display = 'block'; }
        if (loginGroup) { loginGroup.classList.add('error'); setTimeout(() => loginGroup.classList.remove('error'), 400); }
        if (pswdGroup) { pswdGroup.classList.add('error'); setTimeout(() => pswdGroup.classList.remove('error'), 400); }
    }

    // --- MULTI-ACCOUNT MANAGEMENT ---
    function loadAccounts() {
        try {
            const raw = localStorage.getItem('launcher_accounts');
            if (raw) return JSON.parse(raw);
        } catch (e) {}
        const old = localStorage.getItem('launcher_credentials');
        if (old) {
            try {
                const parsed = JSON.parse(old);
                return [parsed];
            } catch (e2) {}
        }
        const oldUser = localStorage.getItem('launcher_remember');
        if (oldUser) return [{ user: oldUser, pass: '' }];
        return [];
    }

    function saveAccounts(accounts) {
        localStorage.setItem('launcher_accounts', JSON.stringify(accounts));
    }

    function saveCurrentCredentials(username, password) {
        let accounts = loadAccounts();
        const idx = accounts.findIndex(a => a.user === username);
        if (idx >= 0) {
            if (password) accounts[idx].pass = password;
        } else {
            accounts.push({ user: username, pass: password || '' });
        }
        saveAccounts(accounts);
    }

    function removeAccount(username) {
        let accounts = loadAccounts();
        accounts = accounts.filter(a => a.user !== username);
        saveAccounts(accounts);
        renderAccountList();
    }

    function switchToAccount(username) {
        const accounts = loadAccounts();
        const found = accounts.find(a => a.user === username);
        if (found) {
            currentUser = found.user;
            localStorage.setItem('launcher_remember', found.user);
            if (profileLoginEl) profileLoginEl.textContent = currentUser || '';
            if (profileUidEl) profileUidEl.textContent = currentUser || '-';
            renderAccountList();
            populateAccountSelect();
        }
    }

    function populateAccountSelect() {
        const select = document.getElementById('launchAccountSelect');
        if (!select) return;
        const accounts = loadAccounts();
        const current = select.value || currentUser;
        select.innerHTML = '';
        accounts.forEach(acc => {
            const opt = document.createElement('option');
            opt.value = acc.user;
            opt.textContent = acc.user;
            if (acc.user === current) opt.selected = true;
            select.appendChild(opt);
        });
        select.onchange = () => {
            if (select.value) switchToAccount(select.value);
        };
    }

    function renderAccountList() {
        const container = document.getElementById('accountList');
        if (!container) return;
        const accounts = loadAccounts();
        container.innerHTML = '';
        if (accounts.length === 0) {
            container.innerHTML = '<div style="font-size:13px;color:var(--text-muted);padding:12px 0;" data-i18n="accounts_empty">Нет сохранённых аккаунтов</div>';
            return;
        }
        accounts.forEach(acc => {
            const row = document.createElement('div');
            row.style.cssText = 'display:flex;align-items:center;justify-content:space-between;padding:10px 14px;border-radius:10px;background:rgba(255,255,255,0.02);border:1px solid var(--border-color);margin-bottom:6px;transition:all 0.2s;cursor:pointer;';
            if (acc.user === currentUser) {
                row.style.borderColor = 'rgba(100,150,255,0.3)';
                row.style.background = 'rgba(100,150,255,0.06)';
            }
            row.innerHTML = `
                <div style="display:flex;align-items:center;gap:10px;">
                    <div style="width:30px;height:30px;border-radius:50%;background:linear-gradient(135deg,#6366f1,#8b5cf6);display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:700;color:#fff;">${acc.user.charAt(0).toUpperCase()}</div>
                    <div>
                        <div style="font-weight:600;font-size:13px;">${acc.user}</div>
                        <div style="font-size:11px;color:var(--text-muted);">${acc.pass ? '••••••••' : 'без пароля'}</div>
                    </div>
                </div>
                <div style="display:flex;gap:6px;">
                    ${acc.user !== currentUser ? `<button class="acc-switch-btn" style="background:rgba(100,150,255,0.1);border:none;color:#6496FF;padding:6px 12px;border-radius:6px;font-size:11px;font-weight:600;cursor:pointer;">Войти</button>` : '<span style="font-size:11px;color:#22c55e;font-weight:600;">Активен</span>'}
                    <button class="acc-remove-btn" style="background:rgba(255,74,74,0.1);border:none;color:#ff4a4a;padding:6px 10px;border-radius:6px;font-size:11px;cursor:pointer;">✕</button>
                </div>
            `;
            const switchBtn = row.querySelector('.acc-switch-btn');
            if (switchBtn) switchBtn.addEventListener('click', (e) => { e.stopPropagation(); switchToAccount(acc.user); });
            const removeBtn = row.querySelector('.acc-remove-btn');
            if (removeBtn) removeBtn.addEventListener('click', (e) => { e.stopPropagation(); removeAccount(acc.user); });
            container.appendChild(row);
        });
    }

    function loginAs(username, password) {
        currentUser = username;
        saveCurrentCredentials(username, password);
        localStorage.setItem('launcher_remember', username);
        if (loginGroup) loginGroup.classList.remove('error');
        if (pswdGroup) pswdGroup.classList.remove('error');
        if (errorHint) errorHint.style.display = 'none';

        if (authCard) authCard.style.transform = 'scale(0.95) translateY(-20px)';
        if (authScreen) {
            authScreen.style.opacity = '0';
            authScreen.style.pointerEvents = 'none';
            setTimeout(() => {
                authScreen.style.display = 'none';
                showMainScreen();
            }, 500);
        }
    }

    function showMainScreen() {
        screens.forEach(s => { s.style.display = 'none'; s.classList.remove('active'); });
        if (screenMain) { screenMain.style.display = 'block'; setTimeout(() => screenMain.classList.add('active'), 10); }
        navItems.forEach(nav => nav.classList.remove('active'));
        if (navItems.length > 0) navItems[0].classList.add('active');
        if (profileLoginEl) profileLoginEl.textContent = currentUser || '';
        if (profileUidEl) profileUidEl.textContent = currentUser || '-';
        renderAccountList();
        populateAccountSelect();
    }

    if (pwdToggle) {
        let isPasswordHidden = true;
        pwdToggle.addEventListener('click', () => {
            isPasswordHidden = !isPasswordHidden;
            loginPassword.type = isPasswordHidden ? 'password' : 'text';
            if (eyeIcon) {
                eyeIcon.innerHTML = isPasswordHidden
                    ? '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle>'
                    : '<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line>';
            }
        });
    }

    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            currentUser = null;
            localStorage.removeItem('launcher_remember');
            if (loginUser) loginUser.value = '';
            if (loginPassword) loginPassword.value = '';
            if (rememberCheck) rememberCheck.checked = false;
            if (authCard) authCard.style.transform = 'scale(1) translateY(0)';
            if (authScreen) { authScreen.style.display = 'flex'; authScreen.style.pointerEvents = 'all'; setTimeout(() => authScreen.style.opacity = '1', 10); }
        });
    }

    const syncBtn = document.getElementById('syncAccountsBtn');
    const syncStatus = document.getElementById('syncStatus');
    if (syncBtn) {
        syncBtn.addEventListener('click', async () => {
            syncBtn.disabled = true;
            syncBtn.textContent = 'Синхронизация...';
            const result = await ipcRenderer.invoke('auth:sync-remote');
            if (syncStatus) syncStatus.textContent = result.success
                ? (result.merged > 0 ? 'Синхронизировано: ' + result.merged + ' аккаунтов' : 'Аккаунты актуальны')
                : 'Ошибка: ' + (result.error || 'неизвестная');
            syncBtn.disabled = false;
            syncBtn.textContent = 'СИНХРОНИЗИРОВАТЬ';
        });
    }

    if (clientCard) {
        clientCard.addEventListener('click', async () => {
            if (screenMain) { screenMain.style.display = 'none'; screenMain.classList.remove('active'); }
            if (clientDetailScreen) { clientDetailScreen.style.display = 'flex'; setTimeout(() => clientDetailScreen.classList.add('active'), 10); }
            await updateLaunchState();
        });
    }

    const subRequiredBlock = document.getElementById('subRequiredBlock');
    const subExpiredBlock = document.getElementById('subExpiredBlock');
    const subActiveBlock = document.getElementById('subActiveBlock');
    const subExpiredCountdown = document.getElementById('subExpiredCountdown');
    const subActivePlan = document.getElementById('subActivePlan');
    const subActiveCountdown = document.getElementById('subActiveCountdown');
    const PLAN_LABELS_LAUNCH = { stable: 'Stable', beta: 'Beta', alpha: 'Alpha' };

    async function updateLaunchState() {
        const t = await loadTranslations(currentLanguage);
        const license = await ipcRenderer.invoke('license:get');

        if (subRequiredBlock) subRequiredBlock.style.display = 'none';
        if (subExpiredBlock) subExpiredBlock.style.display = 'none';
        if (subActiveBlock) subActiveBlock.style.display = 'none';

        if (!license) {
            if (subRequiredBlock) subRequiredBlock.style.display = '';
            if (launchGameBtn) { launchGameBtn.disabled = true; launchGameBtn.style.opacity = '0.4'; launchGameBtn.style.pointerEvents = 'none'; }
            if (launchStatus) { launchStatus.style.display = 'block'; launchStatus.textContent = ''; launchStatus.style.color = ''; }
            return;
        }

        const now = Date.now();
        if (now > license.expiresAt) {
            if (subExpiredBlock) subExpiredBlock.style.display = '';
            if (launchGameBtn) { launchGameBtn.disabled = true; launchGameBtn.style.opacity = '0.4'; launchGameBtn.style.pointerEvents = 'none'; }
            updateExpiredCountdown(license.expiresAt, t);
            return;
        }

        if (subActiveBlock) subActiveBlock.style.display = '';
        if (launchGameBtn) { launchGameBtn.disabled = false; launchGameBtn.style.opacity = '1'; launchGameBtn.style.pointerEvents = 'all'; }
        if (launchStatus) { launchStatus.style.display = 'none'; launchStatus.textContent = ''; }
        updateActiveCountdown(license, t);
    }

    function updateExpiredCountdown(expiresAt, t) {
        if (!subExpiredCountdown) return;
        function update() {
            const diff = Date.now() - expiresAt;
            const days = Math.floor(diff / 86400000);
            const hours = Math.floor((diff % 86400000) / 3600000);
            const mins = Math.floor((diff % 3600000) / 60000);
            const secs = Math.floor((diff % 60000) / 1000);
            subExpiredCountdown.textContent = `${days}д ${hours}ч ${mins}м ${secs}с назад`;
        }
        update();
        if (subExpiredCountdown._timer) clearInterval(subExpiredCountdown._timer);
        subExpiredCountdown._timer = setInterval(update, 1000);
    }

    function updateActiveCountdown(license, t) {
        if (!subActivePlan || !subActiveCountdown) return;
        subActivePlan.textContent = PLAN_LABELS_LAUNCH[license.plan] || license.plan;
        function update() {
            const diff = license.expiresAt - Date.now();
            if (diff <= 0) { clearInterval(subActiveCountdown._timer); updateLaunchState(); return; }
            const days = Math.floor(diff / 86400000);
            const hours = Math.floor((diff % 86400000) / 3600000);
            const mins = Math.floor((diff % 3600000) / 60000);
            const secs = Math.floor((diff % 60000) / 1000);
            subActiveCountdown.textContent = `${days}д ${hours}ч ${mins}м ${secs}с`;
        }
        update();
        if (subActiveCountdown._timer) clearInterval(subActiveCountdown._timer);
        subActiveCountdown._timer = setInterval(update, 1000);
    }

    if (backToCardsBtn) {
        backToCardsBtn.addEventListener('click', () => {
            if (clientDetailScreen) { clientDetailScreen.style.display = 'none'; clientDetailScreen.classList.remove('active'); }
            if (screenMain) { screenMain.style.display = 'block'; setTimeout(() => screenMain.classList.add('active'), 10); }
        });
    }

    if (launchGameBtn) {
        launchGameBtn.addEventListener('click', async () => {
            const t = await loadTranslations(currentLanguage);

            const license = await ipcRenderer.invoke('license:get');
            if (!license || Date.now() > license.expiresAt) {
                return;
            }

            const accountSelect = document.getElementById('launchAccountSelect');
            const nickname = (accountSelect && accountSelect.value) || currentUser || 'Player';
            const ram = ramSlider ? ramSlider.value : '2048';
            const quickJoinServer = document.getElementById('quickJoinServer');
            const server = quickJoinServer ? quickJoinServer.value : '';

            launchGameBtn.disabled = true;
            launchGameBtn.style.opacity = '0.5';
            launchGameBtn.style.pointerEvents = 'none';
            if (launchStatus) launchStatus.style.display = 'block';
            if (selectMinecraftBtn) {
                selectMinecraftBtn.style.display = 'none';
                selectMinecraftBtn.querySelector('[data-i18n]').textContent = t.btn_select_mc_folder || 'ВЫБРАТЬ ПАПКУ .minecraft';
                selectMinecraftBtn.onclick = null;
            }

            ipcRenderer.send('game:launch', { nickname, ram, server: server || undefined });
        });
    }

    const selectMinecraftBtn = document.getElementById('selectMinecraftBtn');
    if (selectMinecraftBtn) {
        selectMinecraftBtn.addEventListener('click', async () => {
            const selectedPath = await ipcRenderer.invoke('game:selectMinecraftDir');
            if (selectedPath) {
                selectMinecraftBtn.style.display = 'none';
                if (launchStatus) {
                    launchStatus.textContent = 'Minecraft found: ' + selectedPath;
                    launchStatus.style.color = '#22c55e';
                    setTimeout(() => { launchStatus.style.display = 'none'; launchStatus.style.color = ''; }, 3000);
                }
            }
        });
    }

    ipcRenderer.on('game:launch-status', async (event, data) => {
        const t = await loadTranslations(currentLanguage);
        const launchProgress = document.getElementById('launchProgress');
        const launchProgressFill = document.getElementById('launchProgressFill');
        const launchProgressText = document.getElementById('launchProgressText');
        const launchProgressStep = document.getElementById('launchProgressStep');

        if (data.status === 'error') {
            if (launchProgress) launchProgress.style.display = 'none';
            if (launchStatus) { launchStatus.textContent = data.message || t.error_unknown || 'Unknown error'; launchStatus.style.display = 'block'; launchStatus.style.color = '#ef4444'; }
            launchGameBtn.disabled = false;
            launchGameBtn.style.opacity = '1';
            launchGameBtn.style.pointerEvents = 'all';
            launchGameBtn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg> ${t.btn_launch || 'ЗАПУСТИТЬ'}`;
            playErrorSound();
            if (data.message && data.message.includes('Java')) {
                if (selectMinecraftBtn) {
                    selectMinecraftBtn.style.display = 'block';
                    selectMinecraftBtn.querySelector('[data-i18n]').textContent = 'СКАЧАТЬ JAVA 21';
                    selectMinecraftBtn.onclick = () => {
                        require('electron').shell.openExternal('https://adoptium.net/temurin/releases/?version=21');
                    };
                }
            }
        } else if (data.status === 'building') {
            if (selectMinecraftBtn) selectMinecraftBtn.style.display = 'none';
            if (launchProgress) launchProgress.style.display = 'block';
            if (launchProgressText) launchProgressText.textContent = t.launch_preparing || 'Подготовка...';
            if (launchProgressStep) launchProgressStep.textContent = t.launch_build || 'Сборка';
            launchGameBtn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" style="animation: spin 1s linear infinite;"><circle cx="12" cy="12" r="10"></circle></svg> ${t.btn_launching || 'ЗАПУСК...'}`;
            playLaunchSound();
        } else if (data.status === 'started') {
            if (launchProgress) launchProgress.style.display = 'none';
            launchGameBtn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><circle cx="12" cy="12" r="10"></circle><polyline points="8 12 11 15 16 9"></polyline></svg> ${t.status_started || 'ИГРА ЗАПУЩЕНА'}`;
            playSuccessSound();
            updateOwnPresence();
        } else if (data.status === 'closed') {
            if (launchProgress) launchProgress.style.display = 'none';
            launchGameBtn.disabled = false;
            launchGameBtn.style.opacity = '1';
            launchGameBtn.style.pointerEvents = 'all';
            launchGameBtn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg> ${t.btn_launch || 'ЗАПУСТИТЬ'}`;
            if (launchStatus) launchStatus.style.display = 'none';
            removeOwnPresence();
            updateLaunchState();
        }
    });

    ipcRenderer.on('game:launch-progress', async (event, data) => {
        const launchProgress = document.getElementById('launchProgress');
        const launchProgressFill = document.getElementById('launchProgressFill');
        const launchProgressText = document.getElementById('launchProgressText');
        const launchProgressStep = document.getElementById('launchProgressStep');

        if (launchProgress) launchProgress.style.display = 'block';

        if (launchProgressText) launchProgressText.textContent = data.message || 'Загрузка...';
        if (data.taskNumber) {
            if (launchProgressStep) launchProgressStep.textContent = '#' + data.taskNumber;
            const pct = Math.min(95, Math.round((data.taskNumber / 40) * 100));
            if (launchProgressFill) launchProgressFill.style.width = pct + '%';
        }
        if (data.percent >= 0 && launchProgressFill) {
            launchProgressFill.style.width = data.percent + '%';
        }
    });

    navItems.forEach(item => {
        item.addEventListener('click', () => {
            navItems.forEach(nav => nav.classList.remove('active'));
            item.classList.add('active');
            const targetScreen = item.getAttribute('data-target');
            if (clientDetailScreen) { clientDetailScreen.style.display = 'none'; clientDetailScreen.classList.remove('active'); }
            screens.forEach(screen => {
                screen.style.display = 'none';
                screen.classList.remove('active');
                if (screen.id === targetScreen) { screen.style.display = 'block'; setTimeout(() => screen.classList.add('active'), 5); }
            });
        });
    });

    if (ramSlider) {
        ramSlider.addEventListener('input', (e) => { if (ramValue) ramValue.textContent = e.target.value; });
    }

    // --- COSMETICS (removed) ---

    // --- ONLINE UPDATE SYSTEM ---
    const updateBanner = document.getElementById('updateBanner');
    const updateVersionText = document.getElementById('updateVersionText');
    const updateChangelog = document.getElementById('updateChangelog');
    const btnUpdate = document.getElementById('btnUpdate');
    const btnSkipUpdate = document.getElementById('btnSkipUpdate');
    const updateProgress = document.getElementById('updateProgress');
    const updateProgressFill = document.getElementById('updateProgressFill');
    const updateProgressText = document.getElementById('updateProgressText');
    const updateProgressSize = document.getElementById('updateProgressSize');

    function formatBytes(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }

    async function checkForUpdates() {
        try {
            const result = await ipcRenderer.invoke('update:check');
            if (result.error || !result.hasUpdate) return;

            pendingUpdate = result;

            if (updateVersionText) updateVersionText.textContent = 'v' + result.version;
            if (updateChangelog) {
                const changelog = result.changelog[currentLanguage] || result.changelog.ru || result.changelog.en || '';
                updateChangelog.textContent = changelog;
            }

            if (updateBanner) {
                updateBanner.style.display = 'block';
                updateBanner.classList.remove('error', 'success');
            }
            playNotificationSound();

            if (btnUpdate) {
                btnUpdate.disabled = false;
                btnUpdate.style.display = '';
            }
            if (btnSkipUpdate) btnSkipUpdate.style.display = '';
            if (updateProgress) updateProgress.style.display = 'none';
        } catch (e) {
            console.log('Update check failed:', e);
        }
    }

    if (btnSkipUpdate) {
        btnSkipUpdate.addEventListener('click', () => {
            if (updateBanner) {
                updateBanner.style.opacity = '0';
                updateBanner.style.transform = 'translateY(-10px)';
                updateBanner.style.transition = 'all 0.3s ease';
                setTimeout(() => { updateBanner.style.display = 'none'; }, 300);
            }
            pendingUpdate = null;
        });
    }

    if (btnUpdate) {
        btnUpdate.addEventListener('click', async () => {
            if (!pendingUpdate || !pendingUpdate.downloadUrl) return;

            const t = await loadTranslations(currentLanguage);

            btnUpdate.disabled = true;
            btnUpdate.style.display = 'none';
            if (btnSkipUpdate) btnSkipUpdate.style.display = 'none';
            if (updateProgress) updateProgress.style.display = 'block';

            if (updateProgressText) updateProgressText.textContent = t.update_downloading || 'Загрузка...';

            const jarName = 'rich-' + pendingUpdate.clientVersion + '.jar';
            let targetPath;

            const root = await ipcRenderer.invoke('game:findRoot');
            if (root) {
                targetPath = require('path').join(root, 'mods', jarName);
            } else {
                const gameDataDir = await ipcRenderer.invoke('game:getGameDataDir');
                targetPath = require('path').join(gameDataDir, jarName);
            }

            ipcRenderer.send('update:download', {
                url: pendingUpdate.downloadUrl,
                targetPath: targetPath,
                _updateVersion: pendingUpdate.version,
                _updateClientVersion: pendingUpdate.clientVersion
            });
        });
    }

    ipcRenderer.on('update:download-status', async (event, data) => {
        const t = await loadTranslations(currentLanguage);

        if (data.status === 'downloading') {
            if (data.percent >= 0) {
                if (updateProgressFill) updateProgressFill.style.width = data.percent + '%';
                if (updateProgressText) updateProgressText.textContent = data.percent + '%';
            } else {
                if (updateProgressText) updateProgressText.textContent = t.update_downloading || 'Загрузка...';
            }
            if (updateProgressSize && data.totalBytes > 0) {
                updateProgressSize.textContent = formatBytes(data.receivedBytes) + ' / ' + formatBytes(data.totalBytes);
            } else if (updateProgressSize && data.receivedBytes > 0) {
                updateProgressSize.textContent = formatBytes(data.receivedBytes);
            }
        } else if (data.status === 'done') {
            if (updateProgressFill) updateProgressFill.style.width = '100%';
            if (updateProgressText) updateProgressText.textContent = t.update_complete || 'Готово!';
            if (updateProgressSize) updateProgressSize.textContent = '';
            if (updateBanner) {
                updateBanner.classList.add('success');
                updateBanner.classList.remove('error');
            }
            if (btnUpdate) {
                btnUpdate.disabled = false;
                btnUpdate.style.display = '';
                btnUpdate.textContent = t.update_restart || 'ЗАПУСТИТЬ ЗАНОВО';
                btnUpdate.onclick = () => { ipcRenderer.send('window-close'); };
            }
            if (btnSkipUpdate) btnSkipUpdate.style.display = 'none';
            if (updateProgress) {
                setTimeout(() => { updateProgress.style.display = 'none'; }, 1000);
            }

            ipcRenderer.invoke('update:setVersion', {
                version: pendingUpdate.version,
                clientVersion: pendingUpdate.clientVersion
            });
        } else if (data.status === 'error') {
            if (updateBanner) updateBanner.classList.add('error');
            if (updateProgressText) updateProgressText.textContent = (t.update_error || 'Ошибка: ') + data.message;
            if (updateProgressFill) updateProgressFill.style.width = '0%';
            if (btnUpdate) {
                btnUpdate.disabled = false;
                btnUpdate.style.display = '';
                btnUpdate.textContent = t.btn_retry || 'ПОВТОРИТЬ';
            }
            if (btnSkipUpdate) btnSkipUpdate.style.display = '';
        }
    });

    checkForUpdates();

    // === SUBSCRIPTION SYSTEM ===
    const licenseKeyInput = document.getElementById('licenseKeyInput');
    const activateKeyBtn = document.getElementById('activateKeyBtn');
    const removeKeyBtn = document.getElementById('removeKeyBtn');
    const licenseError = document.getElementById('licenseError');
    const licenseSuccess = document.getElementById('licenseSuccess');
    const subStatusDot = document.getElementById('subStatusDot');
    const subStatusText = document.getElementById('subStatusText');
    const subStatusDetail = document.getElementById('subStatusDetail');
    const subPlanInfo = document.getElementById('subPlanInfo');
    const subPlanName = document.getElementById('subPlanName');
    const subExpiresAt = document.getElementById('subExpiresAt');
    const subKeyDisplay = document.getElementById('subKeyDisplay');

    const PLAN_LABELS = { stable: 'Stable', beta: 'Beta', alpha: 'Alpha' };

    async function loadSubscriptionStatus() {
        const license = await ipcRenderer.invoke('license:get');
        if (!license) {
            if (subStatusDot) subStatusDot.style.background = '#ff4a4a';
            if (subStatusText) subStatusText.textContent = currentLanguage === 'ru' ? 'Нет подписки' : 'No subscription';
            if (subStatusDetail) subStatusDetail.textContent = '';
            if (subPlanInfo) subPlanInfo.style.display = 'none';
            if (removeKeyBtn) removeKeyBtn.style.display = 'none';
            return;
        }

        const now = Date.now();
        const expired = now > license.expiresAt;

        if (expired) {
            if (subStatusDot) subStatusDot.style.background = '#ff4a4a';
            if (subStatusText) subStatusText.textContent = currentLanguage === 'ru' ? 'Подписка истекла' : 'Subscription expired';
            if (subStatusDetail) subStatusDetail.textContent = '';
            if (subPlanInfo) subPlanInfo.style.display = 'none';
            if (removeKeyBtn) removeKeyBtn.style.display = '';
            return;
        }

        const daysLeft = Math.ceil((license.expiresAt - now) / 86400000);
        if (subStatusDot) subStatusDot.style.background = '#22c55e';
        if (subStatusText) subStatusText.textContent = PLAN_LABELS[license.plan] || license.plan;
        if (subStatusDetail) {
            subStatusDetail.textContent = currentLanguage === 'ru'
                ? `Осталось ${daysLeft} дн.`
                : `${daysLeft} days left`;
        }

        if (subPlanInfo) {
            subPlanInfo.style.display = '';
            if (subPlanName) subPlanName.textContent = PLAN_LABELS[license.plan] || license.plan;
            if (subExpiresAt) {
                const d = new Date(license.expiresAt);
                subExpiresAt.textContent = d.toLocaleDateString(currentLanguage === 'ru' ? 'ru-RU' : 'en-US');
            }
            if (subKeyDisplay) subKeyDisplay.textContent = license.key;
        }
        if (removeKeyBtn) removeKeyBtn.style.display = '';
    }

    if (activateKeyBtn) {
        activateKeyBtn.addEventListener('click', async () => {
            const key = (licenseKeyInput ? licenseKeyInput.value.trim().toUpperCase() : '');
            if (!key || key.length < 14 || !key.startsWith('RM-')) {
                if (licenseError) {
                    licenseError.textContent = currentLanguage === 'ru' ? 'Неверный формат ключа' : 'Invalid key format';
                    licenseError.style.display = 'block';
                }
                if (licenseSuccess) licenseSuccess.style.display = 'none';
                return;
            }

            activateKeyBtn.disabled = true;
            activateKeyBtn.textContent = currentLanguage === 'ru' ? 'ПРОВЕРКА...' : 'CHECKING...';
            if (licenseError) licenseError.style.display = 'none';

            const result = await ipcRenderer.invoke('license:activate', { key });

            activateKeyBtn.disabled = false;
            activateKeyBtn.textContent = currentLanguage === 'ru' ? 'АКТИВИРОВАТЬ' : 'ACTIVATE';

            if (result.success) {
                if (licenseSuccess) {
                    licenseSuccess.textContent = currentLanguage === 'ru'
                        ? `Подписка ${PLAN_LABELS[result.plan] || result.plan} активирована!`
                        : `${PLAN_LABELS[result.plan] || result.plan} subscription activated!`;
                    licenseSuccess.style.display = 'block';
                }
                if (licenseError) licenseError.style.display = 'none';
                if (licenseKeyInput) licenseKeyInput.value = '';
                playSuccessSound();
                loadSubscriptionStatus();
                updateLaunchState();
            } else {
                if (licenseError) {
                    licenseError.textContent = result.error || (currentLanguage === 'ru' ? 'Ошибка активации' : 'Activation failed');
                    licenseError.style.display = 'block';
                }
                if (licenseSuccess) licenseSuccess.style.display = 'none';
            }
        });
    }

    if (removeKeyBtn) {
        removeKeyBtn.addEventListener('click', async () => {
            await ipcRenderer.invoke('license:remove');
            loadSubscriptionStatus();
            updateLaunchState();
        });
    }

    if (subKeyDisplay) {
        subKeyDisplay.addEventListener('click', () => {
            const key = subKeyDisplay.textContent;
            if (key && key !== '-') {
                navigator.clipboard.writeText(key).then(() => {
                    subKeyDisplay.style.color = '#22c55e';
                    setTimeout(() => { subKeyDisplay.style.color = '#a78bfa'; }, 1000);
                });
            }
        });
    }

    // --- SERVER MONITOR ---
    const refreshServersBtn = document.getElementById('refreshServersBtn');
    const serverList = document.getElementById('serverList');

    const SERVERS = [
        { name: 'FunTime', host: 'play.funtime.ru', port: 25565 },
        { name: 'Hypixel', host: 'mc.hypixel.net', port: 25565 },
        { name: 'Minecraft Central', host: 'mccentral.org', port: 25565 },
        { name: 'CubeCraft', host: 'cubecraft.net', port: 25565 },
        { name: '2b2t', host: '2b2t.org', port: 25565 },
        { name: 'Mindustry', host: 'mindustry.ga', port: 25565 },
    ];

    function pingServer(host, port, timeout) {
        return new Promise((resolve) => {
            const net = require('net');
            const start = Date.now();
            const socket = new net.Socket();
            socket.setTimeout(timeout || 5000);
            socket.on('connect', () => {
                const latency = Date.now() - start;
                socket.destroy();
                resolve({ status: 'online', latency });
            });
            socket.on('error', () => { socket.destroy(); resolve({ status: 'offline', latency: null }); });
            socket.on('timeout', () => { socket.destroy(); resolve({ status: 'offline', latency: null }); });
            socket.connect(port, host);
        });
    }

    async function refreshServers() {
        if (!serverList) return;
        serverList.innerHTML = '';
        for (const srv of SERVERS) {
            const card = document.createElement('div');
            card.style.cssText = 'display:flex;justify-content:space-between;align-items:center;padding:14px 18px;background:rgba(14,15,20,0.4);border:1px solid var(--border-color);border-radius:12px;';
            card.innerHTML = `
                <div>
                    <div style="font-weight:600;font-size:14px;">${srv.name}</div>
                    <div style="font-size:12px;color:var(--text-muted);margin-top:2px;">${srv.host}:${srv.port}</div>
                </div>
                <div id="status-${srv.host.replace(/[^a-z0-9]/gi, '_')}" style="font-size:11px;font-weight:600;letter-spacing:0.5px;color:var(--text-muted);">Checking...</div>
            `;
            serverList.appendChild(card);
        }
        const t = await loadTranslations(currentLanguage);
        for (const srv of SERVERS) {
            const result = await pingServer(srv.host, srv.port, 3000);
            const el = document.getElementById(`status-${srv.host.replace(/[^a-z0-9]/gi, '_')}`);
            if (el) {
                if (result.status === 'online') {
                    el.textContent = (t.monitor_online || 'ONLINE') + ` (${result.latency}ms)`;
                    el.style.color = '#22c55e';
                } else {
                    el.textContent = t.monitor_offline || 'OFFLINE';
                    el.style.color = '#ff4a4a';
                }
            }
        }
    }

    if (refreshServersBtn) {
        refreshServersBtn.addEventListener('click', refreshServers);
    }

    // --- RESOURCE PACKS ---
    const rpSearch = document.getElementById('rpSearch');
    const rpSearchBtn = document.getElementById('rpSearchBtn');
    const rpGrid = document.getElementById('rpGrid');

    const PACK_CATALOG = [
        { name: 'Faithful 32x', url: 'https://mediafilez.com/download/8AFD4/f32', desc: 'Classic pixel-perfect textures at 32x resolution.', author: 'Faithful Team' },
        { name: 'Sphax PureBDcraft 64x', url: 'https://mediafilez.com/download/saGAr/Sp64', desc: 'Comic-style smooth textures, 64x resolution.', author: 'Sphax' },
        { name: 'ModernArch 128x', url: 'https://mediafilez.com/download/5MrL2/mA128', desc: 'Realistic modern architecture style textures.', author: 'ModernArch' },
        { name: 'Stay True', url: 'https://mediafilez.com/download/FTr0T/StTr', desc: 'Faithful enhancement with vibrant colors.', author: 'Stay True Team' },
    ];

    async function renderResourcePacks(filter) {
        if (!rpGrid) return;
        const result = await ipcRenderer.invoke('resourcepacks:list');
        const installed = result.packs || [];
        const t = await loadTranslations(currentLanguage);

        rpGrid.innerHTML = '';
        const filtered = PACK_CATALOG.filter(p =>
            !filter || p.name.toLowerCase().includes(filter.toLowerCase())
        );

        if (filtered.length === 0) {
            rpGrid.innerHTML = '<div class="card" style="grid-column:1/-1;text-align:center;padding:40px;color:var(--text-muted);">' + (t.rp_search_placeholder || 'No packs found') + '</div>';
            return;
        }

        for (const pack of filtered) {
            const isInstalled = installed.some(p => p.startsWith(pack.name));
            const card = document.createElement('div');
            card.className = 'card';
            card.style.cssText = 'display:flex;flex-direction:column;';

            const statusText = isInstalled ? (t.rp_installed || 'INSTALLED') : (t.rp_install || 'INSTALL');

            card.innerHTML = `
                <div style="display:flex;align-items:center;gap:14px;margin-bottom:16px;">
                    <div style="width:44px;height:44px;border-radius:10px;background:linear-gradient(135deg,#6366f1,#8b5cf6);display:flex;align-items:center;justify-content:center;font-size:20px;font-weight:700;color:#fff;flex-shrink:0;">${pack.name[0]}</div>
                    <div style="flex:1;min-width:0;">
                        <div style="font-weight:600;font-size:14px;">${pack.name}</div>
                        <div style="font-size:11px;color:var(--text-muted);margin-top:2px;">${pack.author}</div>
                    </div>
                </div>
                <div style="font-size:12px;color:#a2a4b0;line-height:1.5;margin-bottom:16px;flex:1;">${pack.desc}</div>
                <button class="btn-monochrome-sub rp-install-btn" style="width:100%;text-align:center;padding:12px;${isInstalled ? 'opacity:0.5;' : ''}" data-url="${pack.url}" data-name="${pack.name}" ${isInstalled ? 'disabled' : ''}>${statusText}</button>
            `;
            rpGrid.appendChild(card);
        }

        document.querySelectorAll('.rp-install-btn:not([disabled])').forEach(btn => {
            btn.addEventListener('click', async () => {
                const url = btn.dataset.url;
                const name = btn.dataset.name + '.zip';
                const t2 = await loadTranslations(currentLanguage);
                btn.textContent = t2.rp_downloading || 'DOWNLOADING...';
                btn.disabled = true;
                const res = await ipcRenderer.invoke('resourcepacks:install', { url, name });
                if (res.success) {
                    btn.textContent = t2.rp_installed || 'INSTALLED';
                    btn.style.opacity = '0.5';
                } else {
                    btn.textContent = (t2.rp_install || 'INSTALL') + ' ✕';
                    btn.disabled = false;
                }
            });
        });
    }

    if (rpSearchBtn && rpSearch) {
        rpSearchBtn.addEventListener('click', () => renderResourcePacks(rpSearch.value));
        rpSearch.addEventListener('keyup', (e) => { if (e.key === 'Enter') renderResourcePacks(rpSearch.value); });
    }

    // --- QUICK JOIN ---
    document.querySelectorAll('.quick-join-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const server = btn.getAttribute('data-server');
            const serverInput = document.getElementById('quickJoinServer');
            if (serverInput) serverInput.value = server;
            document.querySelectorAll('.quick-join-btn').forEach(b => {
                b.style.borderColor = 'var(--border-color)';
                b.style.background = '';
            });
            btn.style.borderColor = 'rgba(255,255,255,0.3)';
            btn.style.background = 'rgba(255,255,255,0.04)';
            const launchBtn = document.getElementById('launchGameBtn');
            if (launchBtn) {
                const span = launchBtn.querySelector('span');
                if (span) span.textContent = (currentLanguage === 'ru' ? 'ЗАПУСТИТЬ НА ' : 'LAUNCH ON ') + server;
            }
        });
    });

    // --- THEME SYSTEM ---
    let currentTheme = localStorage.getItem('launcher_theme') || 'dark';
    const themeAccentInput = document.getElementById('themeAccent');
    const themePresets = document.querySelectorAll('.theme-preset');

    function applyTheme(accent) {
        if (!accent) return;
        document.documentElement.style.setProperty('--accent-white', accent);
        document.documentElement.style.setProperty('--accent-white-hover', accent + 'cc');
        localStorage.setItem('launcher_accent', accent);
    }

    const savedAccent = localStorage.getItem('launcher_accent');
    if (savedAccent) applyTheme(savedAccent);

    if (themeAccentInput) {
        themeAccentInput.addEventListener('input', (e) => applyTheme(e.target.value));
    }

    themePresets.forEach(btn => {
        btn.addEventListener('click', () => {
            const color = btn.getAttribute('data-color');
            if (color) {
                applyTheme(color);
                if (themeAccentInput) themeAccentInput.value = color;
            }
        });
    });

    // Reset accent to default
    const themeResetBtn = document.getElementById('themeResetBtn');
    if (themeResetBtn) {
        themeResetBtn.addEventListener('click', () => {
            applyTheme('#ffffff');
            if (themeAccentInput) themeAccentInput.value = '#ffffff';
            document.querySelectorAll('.theme-preset').forEach(b => b.classList.remove('active'));
        });
    }

    // --- SOUND SYSTEM ---
    let soundEnabled = localStorage.getItem('launcher_sound') !== 'false';
    let audioCtx = null;

    function getAudioCtx() {
        if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        return audioCtx;
    }

    function playTone(freq, duration, type, volume) {
        if (!soundEnabled) return;
        try {
            const ctx = getAudioCtx();
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            osc.type = type || 'sine';
            osc.frequency.value = freq;
            gain.gain.value = volume || 0.08;
            gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + duration);
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.start();
            osc.stop(ctx.currentTime + duration);
        } catch (e) { /* audio not available */ }
    }

    function playLaunchSound() {
        playTone(523, 0.1, 'sine');
        setTimeout(() => playTone(659, 0.1, 'sine'), 100);
        setTimeout(() => playTone(784, 0.15, 'sine'), 200);
    }

    function playErrorSound() {
        playTone(200, 0.3, 'sawtooth', 0.05);
        setTimeout(() => playTone(180, 0.4, 'sawtooth', 0.04), 150);
    }

    function playSuccessSound() {
        playTone(523, 0.1, 'sine');
        setTimeout(() => playTone(659, 0.12, 'sine'), 100);
        setTimeout(() => playTone(784, 0.15, 'sine'), 200);
        setTimeout(() => playTone(1047, 0.25, 'sine'), 350);
    }

    function playNotificationSound() {
        playTone(880, 0.08, 'sine');
        setTimeout(() => playTone(1109, 0.08, 'sine'), 80);
    }

    // Sound toggle
    const soundToggle = document.getElementById('soundToggle');
    const soundToggleKnob = document.getElementById('soundToggleKnob');
    const soundToggleLabel = document.getElementById('soundToggleLabel');

    function updateSoundUI() {
        if (!soundToggle) return;
        soundToggle.checked = soundEnabled;
        if (soundToggleKnob) {
            soundToggleKnob.style.left = soundEnabled ? '22px' : '2px';
            soundToggleKnob.style.background = soundEnabled ? '#22c55e' : '#626475';
        }
        if (soundToggleLabel) soundToggleLabel.textContent = soundEnabled ? 'Вкл' : 'Выкл';
    }

    if (soundToggle) {
        soundToggle.addEventListener('change', () => {
            soundEnabled = soundToggle.checked;
            localStorage.setItem('launcher_sound', soundEnabled ? 'true' : 'false');
            updateSoundUI();
            if (soundEnabled) playNotificationSound();
        });
    }
    updateSoundUI();
    // --- END SOUND SYSTEM ---

    // --- NOTIFICATION TOAST SYSTEM ---
    function showToast(icon, title, text, duration) {
        const container = document.getElementById('toastContainer');
        if (!container) return;
        const toast = document.createElement('div');
        toast.className = 'toast';
        toast.innerHTML = '<div class="toast-icon">' + icon + '</div><div class="toast-content"><div class="toast-title">' + title + '</div><div class="toast-text">' + text + '</div></div>';
        container.appendChild(toast);
        setTimeout(() => { toast.style.animation = 'toastOut 0.3s ease forwards'; setTimeout(() => toast.remove(), 300); }, duration || 4000);
        playNotificationSound();
    }

    // Check subscription expiry on launch
    async function checkSubExpiryNotification() {
        const license = await ipcRenderer.invoke('license:get');
        if (!license) return;
        const daysLeft = Math.ceil((license.expiresAt - Date.now()) / 86400000);
        if (daysLeft <= 3 && daysLeft > 0) {
            showToast('⏳', 'Подписка истекает', 'Осталось ' + daysLeft + ' ' + (daysLeft === 1 ? 'день' : daysLeft < 5 ? 'дня' : 'дней') + '. Продли на сайте.', 6000);
        }
    }

    // Friend online notifications (checked in friends section)
    let prevFriendStatus = {};

    function checkFriendNotifications(friends) {
        const now = Date.now();
        friends.forEach(f => {
            const url = getPresenceURL() + '/api/presence/' + encodeURIComponent(f.username);
            const controller = new AbortController();
            const timer = setTimeout(() => controller.abort(), 2000);
            fetch(url, { signal: controller.signal }).then(r => r.json()).then(data => {
                clearTimeout(timer);
                const wasOnline = prevFriendStatus[f.username];
                const isOnline = data && data.status === 'online' && (now - data.lastSeen) < 120000;
                if (isOnline && !wasOnline && f.username !== currentUser) {
                    showToast('🟢', 'Друг в игре', f.username + ' зашёл' + (data.game ? ' на ' + data.game : '') + '!');
                }
                prevFriendStatus[f.username] = isOnline;
            }).catch(() => { clearTimeout(timer); });
        });
    }

    // --- KEY GENERATOR (admin) ---
    function showKeyGenPanel() {
        const panel = document.getElementById('keyGenPanel');
        if (panel) panel.style.display = '';
    }

    // Click subscription title 5 times to show generator
    let genClicks = 0;
    const subTitle = document.querySelector('#subStatusCard .card-title');
    if (subTitle) {
        subTitle.addEventListener('dblclick', () => {
            genClicks++;
            if (genClicks >= 2) {
                showKeyGenPanel();
                genClicks = 0;
            }
        });
    }

    const genKeyBtn = document.getElementById('genKeyBtn');
    const genKeyOutput = document.getElementById('genKeyOutput');
    const genKeyDisplay = document.getElementById('genKeyDisplay');

    if (genKeyBtn) {
        genKeyBtn.addEventListener('click', async () => {
            const plan = document.getElementById('genPlanSelect')?.value || 'beta';
            const days = document.getElementById('genDaysSelect')?.value || '30';
            genKeyBtn.disabled = true;
            genKeyBtn.textContent = 'ГЕНЕРАЦИЯ...';
            const result = await ipcRenderer.invoke('license:generate', { plan, days });
            genKeyBtn.disabled = false;
            genKeyBtn.textContent = 'СГЕНЕРИРОВАТЬ';
            if (result && result.key) {
                if (genKeyDisplay) genKeyDisplay.textContent = result.key;
                if (genKeyOutput) genKeyOutput.style.display = '';
            }
        });
    }

    window.copyGeneratedKey = function() {
        const el = document.getElementById('genKeyDisplay');
        if (!el) return;
        const key = el.textContent;
        if (key && key !== 'RM-XXXX-XXXX-XXXX') {
            navigator.clipboard.writeText(key).then(() => {
                el.style.color = '#22c55e';
                el.textContent = '✓ Скопировано!';
                setTimeout(() => { el.style.color = '#a78bfa'; el.textContent = key; }, 1500);
            });
        }
    };

    // --- FRIENDS SYSTEM ---
    function loadFriends() {
        try {
            const raw = localStorage.getItem('launcher_friends');
            if (raw) return JSON.parse(raw);
        } catch (e) {}
        return [];
    }

    function saveFriends(friends) {
        localStorage.setItem('launcher_friends', JSON.stringify(friends));
    }

    const friendAddInput = document.getElementById('friendAddInput');
    const friendAddBtn = document.getElementById('friendAddBtn');
    const friendList = document.getElementById('friendList');

    function getPresenceURL() {
        return localStorage.getItem('launcher_presence_url') || 'http://localhost:3000';
    }

    async function checkFriendOnline(username) {
        try {
            const url = getPresenceURL() + '/api/presence/' + encodeURIComponent(username);
            const controller = new AbortController();
            const timer = setTimeout(() => controller.abort(), 3000);
            const resp = await fetch(url, { signal: controller.signal });
            clearTimeout(timer);
            if (!resp.ok) return null;
            return await resp.json();
        } catch (e) {
            return null;
        }
    }

    async function updateOwnPresence() {
        const user = currentUser;
        if (!user) return;
        try {
            const url = getPresenceURL() + '/api/presence/' + encodeURIComponent(user);
            const controller = new AbortController();
            const timer = setTimeout(() => controller.abort(), 3000);
            await fetch(url, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ status: 'online', user }),
                signal: controller.signal
            });
            clearTimeout(timer);
        } catch (e) { /* presence server not available */ }
    }

    function removeOwnPresence() {
        const user = currentUser;
        if (!user) return;
        try {
            const controller = new AbortController();
            setTimeout(() => controller.abort(), 2000);
            fetch(getPresenceURL() + '/api/presence/' + encodeURIComponent(user), { method: 'DELETE', signal: controller.signal }).catch(() => {});
        } catch (e) {}
    }

    function renderFriendList() {
        if (!friendList) return;
        const friends = loadFriends();
        friendList.innerHTML = '';
        if (friends.length === 0) {
            friendList.innerHTML = '<div style="padding:24px;text-align:center;color:var(--text-muted);font-size:13px;" data-i18n="friends_empty">Список друзей пуст. Добавь друга по нику.</div>';
            return;
        }
        friends.forEach(f => {
            const row = document.createElement('div');
            row.style.cssText = 'display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-radius:12px;background:var(--bg-card);border:1px solid var(--border-color);backdrop-filter:blur(16px);';
            row.innerHTML = `
                <div style="display:flex;align-items:center;gap:14px;">
                    <div style="width:36px;height:36px;border-radius:50%;background:linear-gradient(135deg,#6366f1,#8b5cf6);display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:700;color:#fff;flex-shrink:0;">${f.username.charAt(0).toUpperCase()}</div>
                    <div>
                        <div style="font-weight:600;font-size:14px;">${f.username}</div>
                        <div style="font-size:11px;color:var(--text-muted);display:flex;align-items:center;gap:6px;">
                            <span class="friend-status-dot" style="width:7px;height:7px;border-radius:50%;background:#626475;display:inline-block;"></span>
                            <span class="friend-status-text">...</span>
                        </div>
                    </div>
                </div>
                <button class="friend-remove-btn" style="background:rgba(255,74,74,0.1);border:none;color:#ff4a4a;padding:6px 10px;border-radius:6px;font-size:11px;cursor:pointer;transition:all 0.2s;">✕</button>
            `;
            const removeBtn = row.querySelector('.friend-remove-btn');
            removeBtn.addEventListener('click', () => {
                let friends = loadFriends();
                friends = friends.filter(x => x.username !== f.username);
                saveFriends(friends);
                renderFriendList();
                playNotificationSound();
            });
            friendList.appendChild(row);
            checkFriendOnline(f.username).then(data => {
                const dot = row.querySelector('.friend-status-dot');
                const text = row.querySelector('.friend-status-text');
                if (data && data.status === 'online' && (Date.now() - data.lastSeen) < 120000) {
                    if (dot) dot.style.background = '#22c55e';
                    if (text) text.textContent = data.game || 'В игре';
                    if (text) text.style.color = '#22c55e';
                } else {
                    if (dot) dot.style.background = '#626475';
                    if (text) text.textContent = 'Оффлайн';
                    if (text) text.style.color = '#626475';
                }
            });
        });
    }

    if (friendAddBtn && friendAddInput) {
        friendAddBtn.addEventListener('click', () => {
            const name = friendAddInput.value.trim().toLowerCase();
            if (!name || name.length < 3) return;
            const friends = loadFriends();
            if (friends.find(f => f.username === name)) return;
            friends.push({ username: name, addedAt: Date.now() });
            saveFriends(friends);
            friendAddInput.value = '';
            renderFriendList();
            playSuccessSound();
        });
        friendAddInput.addEventListener('keyup', (e) => {
            if (e.key === 'Enter') friendAddBtn.click();
        });
    }

    // Auto-refresh friends presence every 15 seconds
    let friendsRefreshTimer = null;
    function startFriendsRefresh() {
        if (friendsRefreshTimer) clearInterval(friendsRefreshTimer);
        friendsRefreshTimer = setInterval(() => {
            const friends = loadFriends();
            renderFriendList();
            checkFriendNotifications(friends);
        }, 15000);
    }
    startFriendsRefresh();

    // Initial notification check
    setTimeout(() => {
        const friends = loadFriends();
        if (friends.length > 0) checkFriendNotifications(friends);
    }, 5000);

    // Update own presence on game launch (hooks into game:launch-status via IPC)
    // The presence update is handled alongside the UI updates in the existing game:launch-status handler

    // Also update presence when launcher opens
    updateOwnPresence();

    // Clean up presence on window close
    window.addEventListener('beforeunload', () => {
        removeOwnPresence();
    });

    // Check sub expiry notification
    checkSubExpiryNotification();

    // --- DAILY BONUS SYSTEM ---
    const dailyBonusCard = document.getElementById('dailyBonusCard');
    const dailyBonusTitle = document.getElementById('dailyBonusTitle');
    const dailyBonusDesc = document.getElementById('dailyBonusDesc');
    const dailyBonusStreak = document.getElementById('dailyBonusStreak');
    const dailyClaimBtn = document.getElementById('dailyClaimBtn');

    const BONUS_AMOUNTS = [0, 50, 75, 100, 125, 150, 200, 250, 300, 400];

    function getDailyState() {
        try { return JSON.parse(localStorage.getItem('launcher_daily')) || {}; } catch (e) { return {}; }
    }

    function saveDailyState(s) {
        localStorage.setItem('launcher_daily', JSON.stringify(s));
    }

    function updateDailyBonusUI() {
        if (!dailyBonusCard || !dailyClaimBtn) return;
        const state = getDailyState();
        const now = new Date();
        const today = now.toDateString();

        const lastDate = state.lastClaimDate || '';
        const yesterday = new Date(now);
        yesterday.setDate(yesterday.getDate() - 1);
        const yesterdayStr = yesterday.toDateString();

        if (lastDate !== today && lastDate !== yesterdayStr) {
            state.streak = 0;
            saveDailyState(state);
        }

        if (state.lastClaimDate === today) {
            dailyClaimBtn.disabled = true;
            dailyClaimBtn.textContent = '📅 ПОЛУЧЕНО';
            dailyBonusTitle.textContent = 'Ежедневный бонус';
            const claimed = state.lastAmount || 0;
            dailyBonusDesc.textContent = 'Сегодня получено ' + claimed + ' монет';
        } else {
            dailyClaimBtn.disabled = false;
            const nextStreak = (state.streak || 0) + 1;
            const amount = BONUS_AMOUNTS[Math.min(nextStreak, BONUS_AMOUNTS.length - 1)];
            dailyClaimBtn.textContent = 'ЗАБРАТЬ ' + amount;
            dailyBonusTitle.textContent = 'Ежедневный бонус';
            dailyBonusDesc.textContent = 'Забери ' + amount + ' монет за вход!';
        }

        const streak = state.lastClaimDate === today ? (state.streak || 0) : (state.streak || 0);
        dailyBonusStreak.textContent = '🔥 Дней подряд: ' + streak;

        if (dailyBonusCard.style.display === 'none' || !dailyBonusCard.style.display) {
            dailyBonusCard.style.display = 'flex';
        }
    }

    if (dailyClaimBtn) {
        dailyClaimBtn.addEventListener('click', () => {
            const state = getDailyState();
            const now = new Date();
            const today = now.toDateString();
            if (state.lastClaimDate === today) return;

            const yesterday = new Date(now);
            yesterday.setDate(yesterday.getDate() - 1);

            if (state.lastClaimDate === yesterday.toDateString()) {
                state.streak = (state.streak || 0) + 1;
            } else {
                state.streak = 1;
            }

            const amount = BONUS_AMOUNTS[Math.min(state.streak, BONUS_AMOUNTS.length - 1)];
            state.coins = (state.coins || 0) + amount;
            state.lastClaimDate = today;
            state.lastAmount = amount;
            saveDailyState(state);
            updateDailyBonusUI();
            showToast('🎁', 'Бонус получен!', '+' + amount + ' монет (стрик: ' + state.streak + ' дней)', 3000);
        });
    }

    updateDailyBonusUI();

    loadSubscriptionStatus();
    updateLaunchState();
});
