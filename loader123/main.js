const { ipcRenderer } = require('electron');

document.addEventListener('DOMContentLoaded', () => {
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
        if (currentLangFlag) currentLangFlag.src = lang === 'ru' ? 'https://flagcdn.com/w20/ru.png' : 'https://flagcdn.com/w20/us.png';
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
        } else {
            if (authTitle) authTitle.textContent = t.auth_title || 'Authorization';
            if (authSubtitle) authSubtitle.textContent = t.auth_subtitle || 'Enter your credentials';
            if (authSubmitBtn) authSubmitBtn.textContent = t.btn_login || 'LOGIN';
            if (authSwitchBtn) authSwitchBtn.textContent = t.btn_to_register || 'No account? Register';
            if (confirmGroup) confirmGroup.style.display = 'none';
        }
        if (errorHint) errorHint.style.display = 'none';
    }

    changeLanguage(currentLanguage);

    // Auto-login if remembered
    const savedCredentials = localStorage.getItem('launcher_credentials');
    if (savedCredentials) {
        try {
            const { user, pass } = JSON.parse(savedCredentials);
            if (user) {
                if (loginUser) loginUser.value = user;
                if (loginPassword) loginPassword.value = pass || '';
                if (rememberCheck) rememberCheck.checked = true;
                loginAs(user, pass);
            }
        } catch (e) {
            // Fallback for old format (plain username string)
            const savedUser = localStorage.getItem('launcher_remember');
            if (savedUser) {
                if (loginUser) loginUser.value = savedUser;
                loginAs(savedUser);
            }
        }
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
                const result = await ipcRenderer.invoke('auth:register', { login: username, password });
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

    function loginAs(username, password) {
        currentUser = username;
        if (rememberCheck && rememberCheck.checked && password) {
            localStorage.setItem('launcher_credentials', JSON.stringify({ user: username, pass: password }));
        } else {
            localStorage.removeItem('launcher_credentials');
        }
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
            localStorage.removeItem('launcher_credentials');
            if (loginUser) loginUser.value = '';
            if (loginPassword) loginPassword.value = '';
            if (rememberCheck) rememberCheck.checked = false;
            if (authCard) authCard.style.transform = 'scale(1) translateY(0)';
            if (authScreen) { authScreen.style.display = 'flex'; authScreen.style.pointerEvents = 'all'; setTimeout(() => authScreen.style.opacity = '1', 10); }
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

            const nickname = currentUser || 'Player';
            const ram = ramSlider ? ramSlider.value : '2048';

            launchGameBtn.disabled = true;
            launchGameBtn.style.opacity = '0.5';
            launchGameBtn.style.pointerEvents = 'none';
            if (launchStatus) launchStatus.style.display = 'block';

            ipcRenderer.send('game:launch', { nickname, ram });
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
            launchGameBtn.disabled = false;
            launchGameBtn.style.opacity = '1';
            launchGameBtn.style.pointerEvents = 'all';
            launchGameBtn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg> ${t.btn_launch || 'ЗАПУСТИТЬ'}`;
            if (data.message && (data.message.includes('not found') || data.message.includes('not installed'))) {
                if (selectMinecraftBtn) selectMinecraftBtn.style.display = 'block';
            }
        } else if (data.status === 'building') {
            if (selectMinecraftBtn) selectMinecraftBtn.style.display = 'none';
            if (launchProgress) launchProgress.style.display = 'block';
            if (launchProgressText) launchProgressText.textContent = t.launch_preparing || 'Подготовка...';
            if (launchProgressStep) launchProgressStep.textContent = t.launch_build || 'Сборка';
            launchGameBtn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" style="animation: spin 1s linear infinite;"><circle cx="12" cy="12" r="10"></circle></svg> ${t.btn_launching || 'ЗАПУСК...'}`;
        } else if (data.status === 'started') {
            if (launchProgress) launchProgress.style.display = 'none';
            launchGameBtn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><circle cx="12" cy="12" r="10"></circle><polyline points="8 12 11 15 16 9"></polyline></svg> ${t.status_started || 'ИГРА ЗАПУЩЕНА'}`;
        } else if (data.status === 'closed') {
            if (launchProgress) launchProgress.style.display = 'none';
            launchGameBtn.disabled = false;
            launchGameBtn.style.opacity = '1';
            launchGameBtn.style.pointerEvents = 'all';
            launchGameBtn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg> ${t.btn_launch || 'ЗАПУСТИТЬ'}`;
            if (launchStatus) launchStatus.style.display = 'none';
            updateLaunchState();
        }
    });

    ipcRenderer.on('game:launch-progress', async (event, data) => {
        const launchProgress = document.getElementById('launchProgress');
        const launchProgressFill = document.getElementById('launchProgressFill');
        const launchProgressText = document.getElementById('launchProgressText');
        const launchProgressStep = document.getElementById('launchProgressStep');

        if (launchProgress) launchProgress.style.display = 'block';

        if (data.status === 'building') {
            if (launchProgressText) launchProgressText.textContent = data.message || 'Building...';
            if (launchProgressStep) launchProgressStep.textContent = '#' + (data.taskNumber || '');
            if (data.taskNumber) {
                const pct = Math.min(95, Math.round((data.taskNumber / 30) * 100));
                if (launchProgressFill) launchProgressFill.style.width = pct + '%';
            }
        } else if (data.status === 'downloading') {
            if (launchProgressText) launchProgressText.textContent = data.message || 'Downloading...';
            if (data.percent && launchProgressFill) {
                launchProgressFill.style.width = data.percent + '%';
            }
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
                targetPath = require('path').join(root, 'build', 'libs', jarName);
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
            const key = (licenseKeyInput ? licenseKeyInput.value.trim() : '').toUpperCase();
            if (!key || key.length < 18) {
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

    loadSubscriptionStatus();
    updateLaunchState();
});
