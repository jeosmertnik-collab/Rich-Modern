package excel.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.player.PlayerEntity;
import excel.events.api.EventHandler;
import excel.events.impl.EntityColorEvent;
import excel.events.impl.TickEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.BooleanSetting;
import excel.modules.module.setting.implement.ColorSetting;
import excel.modules.module.setting.implement.SelectSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.Instance;
import excel.util.repository.friend.FriendUtils;

import java.util.ArrayList;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Chams extends ModuleStructure {

    public static Chams getInstance() {
        return Instance.get(Chams.class);
    }

    List<PlayerEntity> targets = new ArrayList<>();

    SelectSetting mode = new SelectSetting("Режим", "Режим отображения")
            .value("Through Walls", "Colored", "Textured").selected("Through Walls");

    BooleanSetting showOnSelf = new BooleanSetting("На себе", "Показывать на себе").setValue(false);
    BooleanSetting friends = new BooleanSetting("Друзья", "Показывать друзей").setValue(true);
    ColorSetting wallColor = new ColorSetting("Цвет за стеной", "Цвет модели за стеной").value(0xFFFF0000);
    ColorSetting visibleColor = new ColorSetting("Цвет на виду", "Цвет модели на виду").value(0xFF00FF00);
    SliderSettings wallAlpha = new SliderSettings("Прозрачность", "Прозрачность за стеной").setValue(0.7f).range(0.1f, 1.0f);
    SliderSettings visibleAlpha = new SliderSettings("Прозрачность (видим.)", "Прозрачность на виду").setValue(1.0f).range(0.1f, 1.0f);
    BooleanSetting showInvisibles = new BooleanSetting("Невидимые", "Показывать невидимых игроков").setValue(true);

    public Chams() {
        super("Chams", "Модели игроков сквозь стены", ModuleCategory.RENDER);
        settings(mode, showOnSelf, friends, wallColor, visibleColor, wallAlpha, visibleAlpha, showInvisibles);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        targets.clear();
        if (mc.world == null || mc.player == null) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player && !showOnSelf.isValue()) continue;
            if (player == mc.player) continue;
            if (!friends.isValue() && FriendUtils.isFriend(player)) continue;
            targets.add(player);
        }
    }

    @EventHandler
    public void onEntityColor(EntityColorEvent e) {
        if (!isState()) return;
        e.cancel();
    }
}
