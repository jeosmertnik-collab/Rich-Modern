package excel.screens.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import excel.client.draggables.AbstractHudElement;
import excel.util.animations.Direction;
import excel.util.lang.Lang;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;
import excel.util.render.shader.Scissor;
import excel.modules.impl.render.Hud;
import excel.util.repository.friend.FriendUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class FriendsListHud extends AbstractHudElement {

    private float animatedWidth = 80;
    private float animatedHeight = 23;
    private long lastUpdateTime = System.currentTimeMillis();

    private static final float ANIM_SPEED = 8.0f;
    private static final float ITEM_HEIGHT = 11;
    private static final float HEADER_HEIGHT = 18;

    private int accentR = 100, accentG = 150, accentB = 255;
    private void updateAccent() {
        int c = Hud.getInstance().getAccentRGB();
        accentR = (c >> 16) & 0xFF;
        accentG = (c >> 8) & 0xFF;
        accentB = c & 0xFF;
    }

    public FriendsListHud() {
        super("FriendsListHud", 300, 200, 80, 23, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        if (FriendUtils.getFriends().isEmpty() && !isChat(mc.currentScreen)) {
            stopAnimation();
        } else {
            startAnimation();
        }
    }

    private float lerp(float current, float target, float deltaTime) {
        float factor = (float) (1.0 - Math.pow(0.001, deltaTime * ANIM_SPEED));
        return current + (target - current) * factor;
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        updateAccent();
        if (alpha <= 0) return;

        float alphaFactor = alpha / 255.0f;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        deltaTime = Math.min(deltaTime, 0.1f);

        float x = getX();
        float y = getY();

        List<String> friendNames = new ArrayList<>(FriendUtils.getFriendNames());
        List<OnlineFriend> onlineFriends = new ArrayList<>();
        List<String> offlineFriends = new ArrayList<>();

        for (String name : friendNames) {
            boolean isOnline = false;
            if (mc.world != null) {
                for (PlayerEntity player : mc.world.getPlayers()) {
                    if (player.getName().getString().equalsIgnoreCase(name)) {
                        isOnline = true;
                        onlineFriends.add(new OnlineFriend(name, player));
                        break;
                    }
                }
            }
            if (!isOnline) {
                offlineFriends.add(name);
            }
        }

        boolean showExample = friendNames.isEmpty() && isChat(mc.currentScreen);
        int totalItems = showExample ? 1 : Math.min(onlineFriends.size() + offlineFriends.size(), 12);

        float targetWidth = 80;
        float targetHeight = HEADER_HEIGHT + totalItems * ITEM_HEIGHT + 8;

        if (showExample) {
            targetHeight = HEADER_HEIGHT + ITEM_HEIGHT + 8;
            float nameWidth = Fonts.SFPRO_REGULAR.getWidth("ExampleFriend", 6);
            targetWidth = Math.max(nameWidth + 40, targetWidth);
        } else {
            for (OnlineFriend of : onlineFriends) {
                float nameWidth = Fonts.SFPRO_REGULAR.getWidth(of.name, 6);
                targetWidth = Math.max(nameWidth + 40, targetWidth);
            }
            for (String name : offlineFriends) {
                float nameWidth = Fonts.SFPRO_REGULAR.getWidth(name, 6);
                targetWidth = Math.max(nameWidth + 40, targetWidth);
            }
        }

        animatedWidth = lerp(animatedWidth, targetWidth, deltaTime);
        animatedHeight = lerp(animatedHeight, targetHeight, deltaTime);

        if (Math.abs(animatedWidth - targetWidth) < 0.3f) animatedWidth = targetWidth;
        if (Math.abs(animatedHeight - targetHeight) < 0.3f) animatedHeight = targetHeight;

        setWidth((int) Math.ceil(animatedWidth));
        setHeight((int) Math.ceil(animatedHeight));

        int bgAlpha = (int) (255 * alphaFactor);

        Render2D.gradientRect(x, y, getWidth(), getHeight(),
                new int[]{
                        new Color(25, 30, 40, bgAlpha).getRGB(),
                        new Color(15, 20, 30, bgAlpha).getRGB(),
                        new Color(25, 30, 40, bgAlpha).getRGB(),
                        new Color(15, 20, 30, bgAlpha).getRGB()
                }, 4);

        Render2D.glowOutline(x, y, getWidth(), getHeight(), 1.0f,
                new Color(accentR, accentG, accentB, (int)(bgAlpha * 0.4f)).getRGB(), 4, 1.0f, 3.0f);

        Fonts.SFPRO_REGULAR.draw(Lang.get().get("hud_friends"), x + 8, y + 5.5f, 6,
                new Color(220, 230, 255, bgAlpha).getRGB());

        String countText = String.valueOf(friendNames.size());
        float countWidth = Fonts.SFPRO_REGULAR.getWidth(countText, 6);
        Render2D.gradientRect(x + getWidth() - countWidth - 12, y + 5, countWidth + 8, 10,
                new int[]{
                        new Color(35, 40, 50, bgAlpha).getRGB(),
                        new Color(25, 30, 40, bgAlpha).getRGB(),
                        new Color(35, 40, 50, bgAlpha).getRGB(),
                        new Color(25, 30, 40, bgAlpha).getRGB()
                }, 3);
        Fonts.SFPRO_REGULAR.draw(countText, x + getWidth() - countWidth - 8, y + 6, 6,
                new Color(180, 190, 210, bgAlpha).getRGB());

        Scissor.enable(x, y, getWidth(), getHeight(), 2);

        float itemY = y + HEADER_HEIGHT + 2;

        if (showExample) {
            drawFriendItem(x + 8, itemY, "ExampleFriend", true, 1f, bgAlpha);
            itemY += ITEM_HEIGHT;
        } else {
            for (OnlineFriend of : onlineFriends) {
                if (itemY + ITEM_HEIGHT > y + getHeight()) break;
                drawFriendItem(x + 8, itemY, of.name, true, 1f, bgAlpha);
                itemY += ITEM_HEIGHT;
            }
            for (String name : offlineFriends) {
                if (itemY + ITEM_HEIGHT > y + getHeight()) break;
                drawFriendItem(x + 8, itemY, name, false, 1f, bgAlpha);
                itemY += ITEM_HEIGHT;
            }
        }

        Scissor.disable();
    }

    private void drawFriendItem(float x, float y, String name, boolean online, float anim, int bgAlpha) {
        int textAlpha = (int) (255 * anim * (bgAlpha / 255.0f));

        Render2D.rect(x + 3, y + 3, 3, 3,
                online ? new Color(80, 200, 80, textAlpha).getRGB() : new Color(180, 190, 210, textAlpha).getRGB(), 1.5f);

        int textColor = online ?
                new Color(220, 230, 255, textAlpha).getRGB() :
                new Color(130, 140, 160, textAlpha).getRGB();
        Fonts.SFPRO_REGULAR.draw(name, x + 10, y + 1, 6, textColor);

        if (online) {
            String statusText = Lang.get().get("hud_online");
            float statusWidth = Fonts.SFPRO_REGULAR.getWidth(statusText, 4.5f);
            Fonts.SFPRO_REGULAR.draw(statusText, x + getWidth() - statusWidth - 8, y + 2, 4.5f,
                    new Color(80, 200, 80, (int)(textAlpha * 0.6f)).getRGB());
        } else {
            String statusText = Lang.get().get("hud_offline");
            float statusWidth = Fonts.SFPRO_REGULAR.getWidth(statusText, 4.5f);
            Fonts.SFPRO_REGULAR.draw(statusText, x + getWidth() - statusWidth - 8, y + 2, 4.5f,
                    new Color(180, 190, 210, (int)(textAlpha * 0.6f)).getRGB());
        }
    }

    private static class OnlineFriend {
        String name;
        PlayerEntity entity;

        OnlineFriend(String name, PlayerEntity entity) {
            this.name = name;
            this.entity = entity;
        }
    }
}
