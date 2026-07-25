package rich.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import rich.modules.module.category.ModuleCategory;
import rich.modules.module.ModuleStructure;
import rich.modules.module.setting.implement.*;
import rich.screens.clickgui.impl.theme.ClickGuiStyle;
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
                    "test",
                    "TargetHud",
//                    "CoolDowns",
//                    "Inventory",
                    "Info",
                    "Notifications",
                    "Keystrokes",
                    "Durability",
                    "Radar",
                    "TabGui",
                    "CoordsHud",
                    "FriendsListHud")

            .selected("Watermark",
                    "HotKeys",
                    "Potions",
                    "Staff",
                    "TargetHud",
//                    "CoolDowns",
//                    "Inventory",
                    "Info",
                    "Notifications",
                    "Keystrokes",
                    "Durability",
                    "Radar",
                    "TabGui",
                    "CoordsHud",
                    "FriendsListHud");

    public BooleanSetting showBps = new BooleanSetting("Show BPS", "Показывать блоки в секунду")
            .setValue(true)
            .visible(() -> interfaceSettings.isSelected("Info"));

    public BooleanSetting showTps = new BooleanSetting("Show TPS", "Показывать TPS в Watermark")
            .setValue(true)
            .visible(() -> interfaceSettings.isSelected("Watermark"));

    public Hud() {
        super("Hud", ModuleCategory.RENDER);
        settings(language, menuStyle, interfaceSettings, showBps, showTps);
        applyLanguage();
    }

    public void applyLanguage() {
        if (language.isSelected("English")) {
            Lang.init("en");
        } else {
            Lang.init("ru");
        }
    }

    public ClickGuiStyle getStyle() {
        int idx = menuStyle.isSelected("Модерн") ? 1 : menuStyle.isSelected("Минимал") ? 2 : 0;
        return ClickGuiStyle.fromIndex(idx);
    }
}