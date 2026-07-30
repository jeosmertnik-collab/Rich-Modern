package excel.modules.impl.misc;

import excel.events.api.EventHandler;
import excel.events.impl.ChatEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.*;
import excel.util.Instance;
import excel.util.chat.ChatWebSocket;

public class ChatMod extends ModuleStructure {

    private static ChatMod instance;

    public static ChatMod getInstance() {
        return instance;
    }

    public BooleanSetting localOnly = new BooleanSetting("Local only", "Только локальный чат (без Telegram)")
            .setValue(false);

    public ChatMod() {
        super("ChatMod", "Внутриигровой чат между клиентами", ModuleCategory.MISC);
        settings(localOnly);
        instance = this;
    }

    @Override
    public void activate() {
        ChatWebSocket ws = ChatWebSocket.getInstance();
        ws.connect(mc.getSession().getUsername(), msg -> {});
    }

    @Override
    public void deactivate() {
        ChatWebSocket.getInstance().disconnect();
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        String msg = event.getMessage();
        if (msg.startsWith(".")) {
            event.setCancelled(true);
            String text = msg.substring(1).trim();
            if (!text.isEmpty()) {
                ChatWebSocket.getInstance().sendMessage(text);
            }
        }
    }
}