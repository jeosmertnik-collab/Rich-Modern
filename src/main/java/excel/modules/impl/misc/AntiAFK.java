package excel.modules.impl.misc;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.math.MathHelper;
import excel.events.api.EventHandler;
import excel.events.impl.TickEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.SelectSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.timer.StopWatch;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AntiAFK extends ModuleStructure {

    final SliderSettings delay = new SliderSettings("Задержка (сек)", "Задержка между AFK-действиями")
            .range(5f, 120f).setValue(30f);

    final SelectSetting mode = new SelectSetting("Режим", "Тип AFK-действия")
            .value("Jump", "Rotate", "Walk", "Sneak").selected("Jump");

    final StopWatch timer = new StopWatch();
    long lastAction = 0;

    public AntiAFK() {
        super("AntiAFK", "Предотвращает кик за AFK", ModuleCategory.MISC);
        settings(delay, mode);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.getNetworkHandler() == null) return;

        if (!timer.finished((long) (delay.getValue() * 1000))) return;

        switch (mode.getSelected()) {
            case "Jump" -> {
                mc.player.jump();
            }
            case "Rotate" -> {
                float yaw = mc.player.getYaw() + 180f;
                if (yaw > 360f) yaw -= 360f;
                mc.player.setYaw(yaw);
                mc.player.setHeadYaw(yaw);
            }
            case "Walk" -> {
                mc.options.forwardKey.setPressed(true);
                mc.options.backKey.setPressed(false);
                mc.options.leftKey.setPressed(false);
                mc.options.rightKey.setPressed(false);
                lastAction = System.currentTimeMillis();
            }
            case "Sneak" -> {
                mc.options.sneakKey.setPressed(true);
                lastAction = System.currentTimeMillis();
            }
        }

        timer.reset();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        if (mc.player == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }
}
