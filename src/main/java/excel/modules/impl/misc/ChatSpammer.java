package excel.modules.impl.misc;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import excel.events.api.EventHandler;
import excel.events.impl.TickEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.SliderSettings;
import excel.modules.module.setting.implement.TextSetting;
import excel.util.timer.StopWatch;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatSpammer extends ModuleStructure {

    final TextSetting message1 = new TextSetting("Сообщение 1", "Первое сообщение для спама");
    final TextSetting message2 = new TextSetting("Сообщение 2", "Второе сообщение (если пусто — не отправляется)");
    final TextSetting message3 = new TextSetting("Сообщение 3", "Третье сообщение (если пусто — не отправляется)");
    final TextSetting message4 = new TextSetting("Сообщение 4", "Четвёртое сообщение (если пусто — не отправляется)");
    final TextSetting message5 = new TextSetting("Сообщение 5", "Пятое сообщение (если пусто — не отправляется)");

    final SliderSettings delay = new SliderSettings("Задержка (сек)", "Задержка между сообщениями")
            .range(1f, 30f).setValue(3f);

    final StopWatch timer = new StopWatch();
    int currentIndex = 0;

    public ChatSpammer() {
        super("ChatSpammer", "Автоматический спам в чат", ModuleCategory.MISC);
        settings(message1, message2, message3, message4, message5, delay);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.getNetworkHandler() == null) return;

        if (!timer.finished((long) (delay.getValue() * 1000))) return;

        String msg = getNextMessage();
        if (msg == null || msg.isEmpty()) return;

        mc.getNetworkHandler().sendChatMessage(msg);
        timer.reset();
    }

    private String getNextMessage() {
        String[] messages = {
                message1.getText(),
                message2.getText(),
                message3.getText(),
                message4.getText(),
                message5.getText()
        };

        for (int i = 0; i < messages.length; i++) {
            int idx = (currentIndex + i) % messages.length;
            if (messages[idx] != null && !messages[idx].trim().isEmpty()) {
                currentIndex = (idx + 1) % messages.length;
                return messages[idx].trim();
            }
        }
        return null;
    }
}
