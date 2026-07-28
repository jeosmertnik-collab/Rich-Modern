package excel.util.lang;

import java.util.HashMap;
import java.util.Map;

public class Lang {
    private static Lang current;
    private static final Map<String, String> translations = new HashMap<>();

    static {
        // === Russian ===
        translations.put("ru.combat", "Бой");
        translations.put("ru.movement", "Движение");
        translations.put("ru.render", "Визуал");
        translations.put("ru.player", "Игрок");
        translations.put("ru.misc", "Разное");
        translations.put("ru.autobuy", "Автопокупка");

        translations.put("ru.search", "Поиск...");
        translations.put("ru.search_type", "Введите для поиска...");
        translations.put("ru.search_results", "Результаты поиска");
        translations.put("ru.results_for", "Результаты для");
        translations.put("ru.modules", "Модули");
        translations.put("ru.settings", "Настройки");
        translations.put("ru.no_settings", "Нет настроек");
        translations.put("ru.select_module", "Выберите модуль");
        translations.put("ru.main", "Основные");
        translations.put("ru.extra", "Дополнительно");

        translations.put("ru.language", "Язык");
        translations.put("ru.style", "Стиль меню");
        translations.put("ru.style_default", "Классический");
        translations.put("ru.style_modern", "Модерн");
        translations.put("ru.style_minimal", "Минимал");

        translations.put("ru.no_modules_found", "Модули не найдены");
        translations.put("ru.enabled", "Включено");
        translations.put("ru.disabled", "Выключено");
        translations.put("ru.bind", "Привязка");
        translations.put("ru.favorite", "Избранное");

        translations.put("ru.combat_desc", "Боевые модули");
        translations.put("ru.movement_desc", "Модули движения");
        translations.put("ru.render_desc", "Модули визуала");
        translations.put("ru.player_desc", "Модули игрока");
        translations.put("ru.misc_desc", "Разные модули");
        translations.put("ru.autobuy_desc", "Автопокупка");

        translations.put("ru.changelog", "Журнал изменений");
        translations.put("ru.ok", "OK");
        translations.put("ru.modules_count", "Модули");
        translations.put("ru.uid", "UID");
        translations.put("ru.no_settings_desc", "У этого модуля нет настроек");
        translations.put("ru.select_module_hint", "Выберите модуль");
        translations.put("ru.search_results_for", "Результаты для");

        translations.put("ru.hud_inventory", "Инвентарь");
        translations.put("ru.hud_online", "онлайн");
        translations.put("ru.hud_offline", "оффлайн");
        translations.put("ru.hud_active", "Активные:");
        translations.put("ru.hud_binds", "Привязки");

        // HUD elements
        translations.put("ru.hud_watermark", "Watermark");
        translations.put("ru.hud_hotkeys", "Горячие клавиши");
        translations.put("ru.hud_potions", "Зелья");
        translations.put("ru.hud_staff", "Персонал");
        translations.put("ru.hud_targethud", "Цель");
        translations.put("ru.hud_info", "Инфо");
        translations.put("ru.hud_notifications", "Уведомления");
        translations.put("ru.hud_keystrokes", "Клавиши");
        translations.put("ru.hud_durability", "Прочность");
        translations.put("ru.hud_radar", "Радар");
        translations.put("ru.hud_friends", "Друзья");
        translations.put("ru.hud_thirdperson", "Третье лицо");
        translations.put("ru.third_person_info", "Инфо");
        translations.put("ru.main_hand", "Основная:");
        translations.put("ru.off_hand", "Вторая:");
        translations.put("ru.ping", "Пинг:");
        translations.put("ru.health", "Здоровье:");
        translations.put("ru.armor", "Броня:");
        translations.put("ru.server", "Сервер:");
        translations.put("ru.empty", "Пусто");

        // Settings panel
        translations.put("ru.language_setting", "Язык");
        translations.put("ru.language_ru", "Русский");
        translations.put("ru.language_en", "English");
        translations.put("ru.style_setting", "Стиль меню");
        translations.put("ru.elements_setting", "Элементы");
        translations.put("ru.show_bps", "Показать BPS");
        translations.put("ru.show_tps", "Показать TPS");
        translations.put("ru.accent_color", "Цвет акцента");

        // === English ===
        translations.put("en.combat", "Combat");
        translations.put("en.movement", "Movement");
        translations.put("en.render", "Render");
        translations.put("en.player", "Player");
        translations.put("en.misc", "Misc");
        translations.put("en.autobuy", "AutoBuy");

        translations.put("en.search", "Search...");
        translations.put("en.search_type", "Type to search...");
        translations.put("en.search_results", "Search Results");
        translations.put("en.results_for", "Results for");
        translations.put("en.modules", "Modules");
        translations.put("en.settings", "Settings");
        translations.put("en.no_settings", "No settings");
        translations.put("en.select_module", "Select a module");
        translations.put("en.main", "Main");
        translations.put("en.extra", "Extra");

        translations.put("en.language", "Language");
        translations.put("en.style", "Menu Style");
        translations.put("en.style_default", "Classic");
        translations.put("en.style_modern", "Modern");
        translations.put("en.style_minimal", "Minimal");

        translations.put("en.no_modules_found", "No modules found");
        translations.put("en.enabled", "Enabled");
        translations.put("en.disabled", "Disabled");
        translations.put("en.bind", "Bind");
        translations.put("en.favorite", "Favorite");

        translations.put("en.combat_desc", "Combat modules");
        translations.put("en.movement_desc", "Movement modules");
        translations.put("en.render_desc", "Render modules");
        translations.put("en.player_desc", "Player modules");
        translations.put("en.misc_desc", "Misc modules");
        translations.put("en.autobuy_desc", "AutoBuy modules");

        translations.put("en.changelog", "Changelog");
        translations.put("en.ok", "OK");
        translations.put("en.modules_count", "Modules");
        translations.put("en.uid", "UID");
        translations.put("en.no_settings_desc", "This module doesn't have settings");
        translations.put("en.select_module_hint", "Select a module");
        translations.put("en.search_results_for", "Results for");

        translations.put("en.hud_inventory", "Inventory");
        translations.put("en.hud_online", "online");
        translations.put("en.hud_offline", "offline");
        translations.put("en.hud_active", "Active:");
        translations.put("en.hud_binds", "Binds");

        // HUD elements
        translations.put("en.hud_watermark", "Watermark");
        translations.put("en.hud_hotkeys", "Hotkeys");
        translations.put("en.hud_potions", "Potions");
        translations.put("en.hud_staff", "Staff");
        translations.put("en.hud_targethud", "TargetHud");
        translations.put("en.hud_info", "Info");
        translations.put("en.hud_notifications", "Notifications");
        translations.put("en.hud_keystrokes", "Keystrokes");
        translations.put("en.hud_durability", "Durability");
        translations.put("en.hud_radar", "Radar");
        translations.put("en.hud_friends", "Friends");
        translations.put("en.hud_thirdperson", "Third Person");
        translations.put("en.third_person_info", "Info");
        translations.put("en.main_hand", "Main:");
        translations.put("en.off_hand", "Off:");
        translations.put("en.ping", "Ping:");
        translations.put("en.health", "Health:");
        translations.put("en.armor", "Armor:");
        translations.put("en.server", "Server:");
        translations.put("en.empty", "Empty");

        // Settings panel
        translations.put("en.language_setting", "Language");
        translations.put("en.language_ru", "Русский");
        translations.put("en.language_en", "English");
        translations.put("en.style_setting", "Menu Style");
        translations.put("en.elements_setting", "Elements");
        translations.put("en.show_bps", "Show BPS");
        translations.put("en.show_tps", "Show TPS");
        translations.put("en.accent_color", "Accent Color");
    }

    public static void init(String lang) {
        if (!lang.equalsIgnoreCase("ru") && !lang.equalsIgnoreCase("en")) {
            lang = "ru";
        }
        current = new Lang(lang);
    }

    public static Lang get() {
        if (current == null) init("ru");
        return current;
    }

    private final String lang;

    private Lang(String lang) {
        this.lang = lang.toLowerCase();
    }

    public String get(String key) {
        return translations.getOrDefault(lang + "." + key, key);
    }

    public String getCategoryName(String name) {
        return switch (name.toLowerCase()) {
            case "combat" -> get("combat");
            case "movement" -> get("movement");
            case "render" -> get("render");
            case "player" -> get("player");
            case "misc" -> get("misc");
            case "autobuy" -> get("autobuy");
            default -> name;
        };
    }

    public String getLang() {
        return lang;
    }

    public boolean isRu() {
        return "ru".equals(lang);
    }
}
