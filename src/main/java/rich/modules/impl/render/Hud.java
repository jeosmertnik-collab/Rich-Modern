package rich.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import rich.modules.module.category.ModuleCategory;
import rich.modules.module.ModuleStructure;
import rich.modules.module.setting.implement.*;

import rich.util.Instance;
import rich.util.lang.Lang;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Hud extends ModuleStructure {
    public static Hud getInstance() {
        return Instance.get(Hud.class);
    }

    public SelectSetting language = new SelectSetting("Язык", "Язык интерфейса / Interface language")
            .value("Русский", "English")
            .selected("Русский");

    public SelectSetting themePreset = new SelectSetting("Пресет темы", "Внешний вид ClickGUI")
            .value("Тёмная", "Светлая", "Синяя", "Кастомная")
            .selected("Тёмная");

    public ColorSetting customBgColor = new ColorSetting("Цвет фона", "Цвет фона при кастомной теме")
            .value(0xFF1A1A1A)
            .visible(() -> themePreset.isSelected("Кастомная"));

    public ColorSetting customPanelColor = new ColorSetting("Цвет панелей", "Цвет панелей при кастомной теме")
            .value(0xFF262626)
            .visible(() -> themePreset.isSelected("Кастомная"));

    public ColorSetting customOutlineColor = new ColorSetting("Цвет обводки", "Цвет обводки при кастомной теме")
            .value(0xFF373737)
            .visible(() -> themePreset.isSelected("Кастомная"));

    public BooleanSetting customBlur = new BooleanSetting("Размытие", "Включить blur фон при кастомной теме")
            .setValue(true)
            .visible(() -> themePreset.isSelected("Кастомная"));

    public SliderSettings guiScale = new SliderSettings("Масштаб GUI", "Масштаб интерфейса ClickGUI")
            .range(0.5F, 2.0F).setValue(1.0F);

    public SliderSettings fontSize = new SliderSettings("Размер шрифта", "Размер шрифта в ClickGUI")
            .range(8F, 18F).setValue(12F);

    public SelectSetting menuStyle = new SelectSetting("Стиль меню", "Визуальный стиль ClickGUI")
            .value("Классический", "Модерн", "Минимал")
            .selected("Классический");

    public MultiSelectSetting interfaceSettings = new MultiSelectSetting("Элементы", "Настройка элементов интерфейса")
            .value("Watermark",
                    "HotKeys",
                    "Potions",
                    "Staff",
                    "TargetHud",
                    "Info",
                    "Notifications",
                    "Keystrokes",
                    "Durability",
                    "Radar",
                    "FriendsListHud",
                    "ThirdPersonHud",
                    "Inventory")

            .selected("Watermark",
                    "HotKeys",
                    "Potions",
                    "Staff",
                    "TargetHud",
                    "Info",
                    "Notifications",
                    "Keystrokes",
                    "Durability",
                    "Radar",
                    "FriendsListHud",
                    "ThirdPersonHud",
                    "Inventory");

    public BooleanSetting showBps = new BooleanSetting("Show BPS", "Показывать блоки в секунду")
            .setValue(true)
            .visible(() -> interfaceSettings.isSelected("Info"));

    public BooleanSetting showTps = new BooleanSetting("Show TPS", "Показывать TPS в Watermark")
            .setValue(true)
            .visible(() -> interfaceSettings.isSelected("Watermark"));

    public ColorSetting accentColor = new ColorSetting("Цвет акцента", "Основной цвет интерфейса")
            .value(0xFF6C5CE7)
            .presets(0xFF6C5CE7, 0xFF7C3AED, 0xFF22C55E, 0xFFE74C3C, 0xFF3498DB, 0xFFF39C12, 0xFFE91E63, 0xFF00BCD4);

    public Hud() {
        super("Hud", "Настройка интерфейса", ModuleCategory.RENDER);
        settings(language, themePreset, customBgColor, customPanelColor, customOutlineColor, customBlur, guiScale, fontSize, menuStyle, interfaceSettings, showBps, showTps, accentColor);
        accentColor.onChange(() -> {});
        applyLanguage();
    }

    public void applyLanguage() {
        if (language.isSelected("English")) {
            Lang.init("en");
        } else {
            Lang.init("ru");
        }
    }

    public int getStyleIndex() {
        return menuStyle.isSelected("Модерн") ? 1 : menuStyle.isSelected("Минимал") ? 2 : 0;
    }

    public int getAccentRGB() {
        return accentColor.getColor() & 0x00FFFFFF;
    }

    public int getAccentARGB() {
        return accentColor.getColor();
    }

    public float getGuiScale() {
        return guiScale.getValue();
    }

    public float getFontSize() {
        return fontSize.getValue();
    }

    public String getThemePreset() {
        return themePreset.getSelected();
    }
}