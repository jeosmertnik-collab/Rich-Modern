package excel.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.ModuleStructure;
import excel.modules.module.setting.implement.*;
import excel.util.Instance;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Hud extends ModuleStructure {
    public static Hud getInstance() {
        return Instance.get(Hud.class);
    }

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
                    "Inventory",
                    "MusicBar",
                    "ServerAssistBinds",
                    "ChatOverlay")

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
                    "Inventory",
                    "MusicBar",
                    "ServerAssistBinds",
                    "ChatOverlay");

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
        super("Hud", ModuleCategory.RENDER);
        settings(interfaceSettings, showBps, showTps, accentColor);
    }

    public int getAccentRGB() {
        return accentColor.getColor() & 0x00FFFFFF;
    }

    public int getAccentARGB() {
        return accentColor.getColor();
    }
}