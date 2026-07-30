package excel.screens.hud;

import net.minecraft.client.gui.DrawContext;
import excel.client.draggables.AbstractHudElement;
import excel.modules.impl.render.Hud;
import excel.util.chat.ChatWebSocket;
import excel.util.chat.ChatWebSocket.ChatMessage;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ChatOverlay extends AbstractHudElement {

    private static ChatOverlay instance;
    private int accentR = 100, accentG = 150, accentB = 255;

    public static ChatOverlay getInstance() {
        return instance;
    }

    public ChatOverlay() {
        super("ChatOverlay", 200, 10, 280, 200, true);
        instance = this;
        startAnimation();
    }

    private void updateAccent() {
        int c = Hud.getInstance().getAccentRGB();
        accentR = (c >> 16) & 0xFF;
        accentG = (c >> 8) & 0xFF;
        accentB = c & 0xFF;
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;
        updateAccent();

        ChatWebSocket ws = ChatWebSocket.getInstance();
        List<ChatMessage> messages = ws.getMessages();

        int visibleCount = Math.min(messages.size(), 12);
        int msgHeight = 18;
        int headerHeight = 18;
        int totalHeight = headerHeight + visibleCount * msgHeight + 8;

        setWidth(280);
        setHeight(totalHeight);

        float x = getX();
        float y = getY();

        int bgAlpha = (int)(130 * alpha / 255f);
        Render2D.gradientRect(x, y, 280, totalHeight,
                new int[]{
                        new Color(25, 30, 40, bgAlpha).getRGB(),
                        new Color(15, 20, 30, bgAlpha).getRGB(),
                        new Color(25, 30, 40, bgAlpha).getRGB(),
                        new Color(15, 20, 30, bgAlpha).getRGB()
                }, 6);

        Render2D.glowOutline(x, y, 280, totalHeight, 1.0f,
                new Color(accentR, accentG, accentB, (int)(bgAlpha * 0.8f)).getRGB(), 6, 1.0f, 3.0f);

        // Header
        String title = ws.isConnected() ? "Чат" : "Чат (откл)";
        Color headerColor = ws.isConnected() ? new Color(220, 230, 255, alpha) : new Color(180, 180, 180, alpha);
        Fonts.BOLD.draw(title, x + 8, y + 4, 8, headerColor.getRGB());

        // Status indicator
        int statusColor = ws.isConnected()
                ? new Color(34, 197, 94, alpha).getRGB()
                : new Color(239, 68, 68, alpha).getRGB();
        Render2D.gradientRect(x + 265, y + 6, 8, 8,
                new int[]{statusColor, statusColor, statusColor, statusColor}, 4);

        // Messages
        int startIdx = Math.max(0, messages.size() - visibleCount);
        for (int i = 0; i < visibleCount; i++) {
            ChatMessage msg = messages.get(startIdx + i);
            float msgY = y + headerHeight + 4 + i * msgHeight;

            if ("system".equals(msg.getUsername())) {
                Fonts.SFPRO_REGULAR.draw(msg.getText(), x + 10, msgY + 3, 6,
                        new Color(180, 190, 210, alpha).getRGB());
            } else {
                String timeStr = new SimpleDateFormat("HH:mm").format(new Date(msg.getTime()));
                Fonts.SFPRO_REGULAR.draw(timeStr, x + 10, msgY + 3, 5,
                        new Color(150, 160, 180, alpha).getRGB());

                Fonts.BOLD.draw(msg.getUsername(), x + 48, msgY + 2, 6,
                        new Color(accentR, accentG, accentB, alpha).getRGB());

                Fonts.SFPRO_REGULAR.draw(msg.getText(), x + 48, msgY + 10, 5.5f,
                        new Color(220, 230, 255, alpha).getRGB());
            }
        }

        // Hint
        if (messages.isEmpty()) {
            Fonts.SFPRO_REGULAR.draw("Напиши .сообщение в чат", x + 10, y + headerHeight + 8, 6,
                    new Color(150, 160, 180, (int)(alpha * 0.6f)).getRGB());
        }
    }

    @Override
    public void tick() {
    }
}