// Обработка формы авторизации
const loginForm = document.getElementById('login-form');
if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const loginInput = document.getElementById('login-input').value;
        const passwordInput = document.getElementById('password-input').value;

        // Отправляем данные в главный процесс через мост preload.js
        const response = await window.api.login({ login: loginInput, password: passwordInput });

        if (response.success) {
            console.log('Авторизован как:', response.user.login);
            // Переключаем табы или обновляем данные профиля на UI
            updateProfileUI(response.user);
        } else {
            alert(response.error);
        }
    });
}

// Функция динамического обновления данных профиля (то, что ты просил изменить)
function updateProfileUI(user) {
    const uidElement = document.getElementById('profile-uid');
    const loginElement = document.getElementById('profile-login');
    const titleElement = document.getElementById('app-title');

    if (uidElement) uidElement.innerText = user.uid;       // Установит 15
    if (loginElement) loginElement.innerText = user.login; // Установит Admin
    if (titleElement) titleElement.innerText = "NptProxy"; // Меняет заголовок лаунчера
}

// Слайдер оперативной памяти (как на 3-м скрине)
const ramSlider = document.getElementById('ram-slider');
const ramValue = document.getElementById('ram-value');

if (ramSlider && ramValue) {
    ramSlider.addEventListener('input', (e) => {
        ramValue.innerText = e.target.value; // Динамически обновляет МБ при перетаскивании
    });
}