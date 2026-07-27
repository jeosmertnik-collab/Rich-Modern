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
        settings(language, menuStyle, interfaceSettings, showBps, showTps, accentColor);
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
}