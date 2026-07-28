package excel.screens.hud;

import net.minecraft.client.gui.DrawContext;
import excel.Initialization;
import excel.client.draggables.AbstractHudElement;
import excel.modules.impl.misc.ServerHelper;
import excel.modules.impl.render.Hud;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;
import excel.util.string.KeyHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ServerAssistBinds extends AbstractHudElement {

    private final List<BindEntry> entries = new ArrayList<>();
    private int mouseX, mouseY;

    public ServerAssistBinds() {
        super("ServerAssistBinds", 4, 160, 150, 20, true);
    }

    @Override
    public void tick() {
        entries.clear();
        var manager = Initialization.getInstance().getManager();
        if (manager == null) return;

        var modules = manager.getModuleProvider().getModuleStructures();
        for (var module : modules) {
            if (module instanceof ServerHelper sh && module.isState()) {
                for (var bind : sh.getKeyBindings()) {
                    int key = bind.setting().getKey();
                    if (key != -1 && bind.setting().getVisible().get()) {
                        String keyName = KeyHelper.getKeyName(key);
                        entries.add(new BindEntry(bind.setting().getName(), bind.setting().getDescription(), keyName));
                    }
                }
            }
        }
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (entries.isEmpty()) return;
        float aF = alpha / 255f;

        if (mc.getWindow() != null) {
            mouseX = (int) mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth();
            mouseY = (int) mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight();
        }

        Hud hud = Hud.getInstance();
        int accent = hud != null ? hud.getAccentRGB() : 0x6C5CE7;
        int accentR = (accent >> 16) & 0xFF;
        int accentG = (accent >> 8) & 0xFF;
        int accentB = accent & 0xFF;

        float x = getX();
        float y = getY();
        float lineH = 11f;
        float pad = 6f;
        float headerH = 16f;
        float maxW = 0;

        for (var e : entries) {
            String text = e.keyName + " -> " + e.displayName;
            float w = Fonts.SFPRO_REGULAR.getWidth(text, 6) + pad * 2;
            if (w > maxW) maxW = w;
        }
        float headerW = Fonts.SFPRO_REGULAR.getWidth("Server Assist", 7) + pad * 2;
        if (headerW > maxW) maxW = headerW;

        float totalH = headerH + entries.size() * lineH + 4;
        setWidth((int) maxW);
        setHeight((int) totalH);

        int bg = new Color(20, 25, 35, (int) (200 * aF)).getRGB();
        Render2D.gradientRect(x, y, maxW, totalH, new int[]{bg, bg}, 4);

        Render2D.gradientRect(x, y, maxW, headerH, new int[]{
                new Color(accentR, accentG, accentB, (int) (80 * aF)).getRGB(),
                new Color(accentR, accentG, accentB, (int) (40 * aF)).getRGB()
        }, 4);
        Fonts.SFPRO_REGULAR.draw("Server Assist", x + pad, y + 4, 7,
                new Color(accentR, accentG, accentB, (int) (255 * aF)).getRGB());

        float oy = y + headerH + 2;
        for (var e : entries) {
            boolean hovered = mouseX >= x + pad && mouseX <= x + maxW - pad
                    && mouseY >= oy && mouseY < oy + lineH;

            if (hovered) {
                Render2D.gradientRect(x + 2, oy, maxW - 4, lineH,
                        new int[]{new Color(255, 255, 255, (int) (10 * aF)).getRGB()}, 2);
            }

            String text = "\u00a77" + e.keyName + " \u00a78-> \u00a7f" + e.displayName;
            Fonts.SFPRO_REGULAR.draw(text, x + pad, oy + 1.5f, 6,
                    new Color(220, 230, 255, (int) (255 * aF)).getRGB());

            if (hovered && !e.description.isEmpty()) {
                drawTooltip(context, e.description, mouseX + 10, mouseY - 10, aF);
            }

            oy += lineH;
        }
    }

    private void drawTooltip(DrawContext context, String text, float tx, float ty, float aF) {
        float tw = Fonts.SFPRO_REGULAR.getWidth(text, 6) + 8;
        float th = 16;
        int bg = new Color(10, 15, 25, (int) (220 * aF)).getRGB();
        Render2D.gradientRect(tx, ty, tw, th, new int[]{bg, bg}, 4);
        Fonts.SFPRO_REGULAR.draw(text, tx + 4, ty + 4, 6,
                new Color(200, 200, 220, (int) (255 * aF)).getRGB());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    private record BindEntry(String displayName, String description, String keyName) {}
}
