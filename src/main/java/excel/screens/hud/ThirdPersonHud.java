package excel.screens.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import excel.client.draggables.AbstractHudElement;
import excel.modules.impl.render.Hud;

import excel.util.animations.Direction;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;
import excel.util.render.item.ItemRender;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static excel.util.lang.Lang.get;

public class ThirdPersonHud extends AbstractHudElement {

    private float animatedWidth = 150;
    private float animatedHeight = 80;
    private long lastUpdateTime = System.currentTimeMillis();

    private static final float ITEM_SCALE = 0.55f;

    public ThirdPersonHud() {
        super("ThirdPersonHud", 10, 80, 150, 80, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        if (mc.player == null) {
            stopAnimation();
            return;
        }

        boolean isThirdPerson = mc.options.getPerspective() != Perspective.FIRST_PERSON;
        if (isThirdPerson) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;
        if (mc.player == null) return;
        if (mc.options.getPerspective() == Perspective.FIRST_PERSON) return;

        float alphaFactor = alpha / 255.0f;
        int bgAlpha = (int) (240 * alphaFactor);
        int textAlpha = (int) (255 * alphaFactor);
        int dimAlpha = (int) (160 * alphaFactor);
        int accentRGB = getAccentRGB();

        float x = getX();
        float y = getY();

        List<String[]> rows = new ArrayList<>();

        String mainHandName = mc.player.getMainHandStack().isEmpty()
                ? LangKey("empty")
                : mc.player.getMainHandStack().getName().getString();
        rows.add(new String[]{LangKey("main_hand"), mainHandName});

        String offHandName = mc.player.getOffHandStack().isEmpty()
                ? LangKey("empty")
                : mc.player.getOffHandStack().getName().getString();
        rows.add(new String[]{LangKey("off_hand"), offHandName});

        int ping = 0;
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null) {
            ping = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()).getLatency();
        }
        rows.add(new String[]{LangKey("ping"), ping + " ms"});

        String health = String.format("%.1f", mc.player.getHealth());
        rows.add(new String[]{LangKey("health"), health});

        int armor = mc.player.getArmor();
        rows.add(new String[]{LangKey("armor"), armor + ""});

        String server = "";
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getServerInfo() != null) {
            server = mc.getNetworkHandler().getServerInfo().address;
            if (server.length() > 24) server = server.substring(0, 24) + "...";
        }
        if (!server.isEmpty()) {
            rows.add(new String[]{LangKey("server"), server});
        }

        float maxLabelW = 0;
        float maxValueW = 0;
        for (String[] row : rows) {
            float lw = Fonts.SFPRO_REGULAR.getWidth(row[0], 5.5f);
            float vw = Fonts.SFPRO_REGULAR.getWidth(row[1], 5.5f);
            if (lw > maxLabelW) maxLabelW = lw;
            if (vw > maxValueW) maxValueW = vw;
        }

        float contentWidth = 16 + maxLabelW + 6 + maxValueW + 16;
        float rowHeight = 12f;
        float headerHeight = 18f;
        float totalHeight = headerHeight + 6 + rows.size() * rowHeight + 8;
        float minWidth = 120f;
        float totalWidth = Math.max(minWidth, contentWidth);

        animatedWidth = lerp(animatedWidth, totalWidth, 0.15f);
        animatedHeight = lerp(animatedHeight, totalHeight, 0.15f);
        setWidth((int) Math.ceil(animatedWidth));
        setHeight((int) Math.ceil(animatedHeight));

        Render2D.rect(x, y, getWidth(), getHeight(), (bgAlpha << 24) | 0x0D0D18, 6);
        Render2D.outline(x, y, getWidth(), getHeight(), 0.4f,
                ((int)(60 * alphaFactor) << 24) | accentRGB, 6);

        Render2D.rect(x, y, getWidth(), 2, ((int)(200 * alphaFactor) << 24) | accentRGB, 6);

        String title = mc.getSession().getUsername();
        Fonts.SFPRO_REGULAR.draw(title, x + 10, y + 5, 6.5f, (textAlpha << 24) | 0xFFFFFF);

        Render2D.rect(x + 10, y + headerHeight - 2, getWidth() - 20, 0.5f,
                ((int)(40 * alphaFactor) << 24) | 0x444460, 0);

        float contentY = y + headerHeight + 4;

        for (String[] row : rows) {
            Fonts.SFPRO_REGULAR.draw(row[0], x + 10, contentY, 5.5f, (dimAlpha << 24) | 0xA0A0B8);
            Fonts.SFPRO_REGULAR.draw(row[1], x + 10 + maxLabelW + 6, contentY, 5.5f, (textAlpha << 24) | 0xFFFFFF);

            if (row == rows.get(0) && !mc.player.getMainHandStack().isEmpty()) {
                float iconX = x + getWidth() - 10 - 18;
                float iconY = contentY - 3;
                if (ItemRender.needsContextRender(mc.player.getMainHandStack())) {
                    ItemRender.drawItemWithContext(context, mc.player.getMainHandStack(), iconX, iconY, ITEM_SCALE, alphaFactor);
                } else {
                    ItemRender.drawItem(mc.player.getMainHandStack(), iconX, iconY, ITEM_SCALE, alphaFactor);
                }
            }
            if (row == rows.get(1) && !mc.player.getOffHandStack().isEmpty()) {
                float iconX = x + getWidth() - 10 - 18;
                float iconY = contentY - 3;
                if (ItemRender.needsContextRender(mc.player.getOffHandStack())) {
                    ItemRender.drawItemWithContext(context, mc.player.getOffHandStack(), iconX, iconY, ITEM_SCALE, alphaFactor);
                } else {
                    ItemRender.drawItem(mc.player.getOffHandStack(), iconX, iconY, ITEM_SCALE, alphaFactor);
                }
            }

            contentY += rowHeight;
        }
    }

    private float lerp(float current, float target, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < 0.3f) return target;
        return current + diff * Math.min(speed, 1f);
    }

    private String LangKey(String key) {
        try {
            return get().get(key);
        } catch (Exception e) {
            return key;
        }
    }

    private int getAccentRGB() {
        if (Hud.getInstance() != null) {
            return Hud.getInstance().getAccentRGB();
        }
        return 0x6496FF;
    }
}
