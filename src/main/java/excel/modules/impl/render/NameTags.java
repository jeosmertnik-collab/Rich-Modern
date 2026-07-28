package excel.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4d;
import excel.events.api.EventHandler;
import excel.events.impl.DrawEvent;
import excel.events.impl.TickEvent;
import excel.events.impl.WorldRenderEvent;
import excel.modules.impl.combat.AntiBot;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.BooleanSetting;
import excel.modules.module.setting.implement.ColorSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.Instance;
import excel.util.math.Projection;
import excel.util.network.Network;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;
import excel.util.repository.friend.FriendUtils;

import java.util.ArrayList;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NameTags extends ModuleStructure {

    public static NameTags getInstance() {
        return Instance.get(NameTags.class);
    }

    List<PlayerEntity> players = new ArrayList<>();

    BooleanSetting health = new BooleanSetting("Здоровье", "Показывать здоровье").setValue(true);
    BooleanSetting distance = new BooleanSetting("Расстояние", "Показывать расстояние").setValue(true);
    BooleanSetting armor = new BooleanSetting("Броня", "Показывать броню").setValue(true);
    BooleanSetting ping = new BooleanSetting("Пинг", "Показывать пинг").setValue(true);
    BooleanSetting background = new BooleanSetting("Фон", "Тёмный фон").setValue(true);
    SliderSettings scale = new SliderSettings("Масштаб", "Масштаб текста").setValue(1.0f).range(0.5f, 3.0f);
    ColorSetting nameColor = new ColorSetting("Цвет имени", "Цвет имени игрока").value(0xFFFFFFFF);
    ColorSetting friendColor = new ColorSetting("Цвет друга", "Цвет имени друга").value(0xFF00FF00);
    ColorSetting healthColorHigh = new ColorSetting("Цвет HP (высш.)", "Цвет HP при высоком здоровье").value(0xFF00FF00);
    ColorSetting healthColorMid = new ColorSetting("Цвет HP (сред.)", "Цвет HP при среднем здоровье").value(0xFFFFFF00);
    ColorSetting healthColorLow = new ColorSetting("Цвет HP (низш.)", "Цвет HP при низком здоровье").value(0xFFFF0000);

    public NameTags() {
        super("NameTags", "Улучшенные теги имён", ModuleCategory.RENDER);
        settings(health, distance, armor, ping, background, scale, nameColor, friendColor, healthColorHigh, healthColorMid, healthColorLow);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        players.clear();
        if (mc.world != null) {
            mc.world.getPlayers().stream()
                    .filter(p -> p != mc.player)
                    .filter(p -> p.getCustomName() == null || !p.getCustomName().getString().startsWith("Ghost_"))
                    .filter(p -> !(AntiBot.getInstance() != null && AntiBot.getInstance().isState() && AntiBot.getInstance().isBot(p)))
                    .forEach(players::add);
        }
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        DrawContext context = e.getDrawContext();
        float tickDelta = e.getPartialTicks();
        float textSize = 5.5f * scale.getValue();

        for (PlayerEntity player : players) {
            if (player == null) continue;

            Vector4d vec = Projection.getVector4D(player, tickDelta);
            if (Projection.cantSee(vec)) continue;

            float dist = (float) mc.gameRenderer.getCamera().getCameraPos().distanceTo(player.getBoundingBox().getCenter());
            if (dist < 1) continue;

            double centerX = Projection.centerX(vec);
            double startY = vec.y - 2;

            drawNameTag(context, player, centerX, startY, textSize, dist);
        }
    }

    private void drawNameTag(DrawContext context, PlayerEntity player, double centerX, double startY, float size, float dist) {
        boolean isFriend = FriendUtils.isFriend(player);
        String name = player.getName().getString();

        StringBuilder sb = new StringBuilder();
        sb.append(name);

        if (ping.isValue()) {
            int pingVal = mc.getNetworkHandler() != null ?
                    mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) != null ?
                            mc.getNetworkHandler().getPlayerListEntry(player.getUuid()).getLatency() : 0 : 0;
            sb.append(" [").append(pingVal).append("ms]");
        }

        if (distance.isValue()) {
            sb.append(" [").append(Math.round(dist)).append("m]");
        }

        String displayText = sb.toString();
        float textWidth = Fonts.TEST.getWidth(displayText, size);
        float textHeight = Fonts.TEST.getHeight(size);

        float posX = (float) centerX - textWidth / 2;
        float posY = (float) startY - textHeight;

        if (background.isValue()) {
            Render2D.rect(posX - 4, posY - 2, textWidth + 8, textHeight + 4, 0x90000000, 3f);
        }

        int nameCol = isFriend ? friendColor.getColorNoAlpha() : nameColor.getColorNoAlpha();
        Fonts.TEST.draw(displayText, posX, posY, size, nameCol);

        if (health.isValue()) {
            float hp = player.getHealth() + player.getAbsorptionAmount();
            float maxHp = player.getMaxHealth();
            String hpStr = String.format("%.1f", hp);
            float hpWidth = Fonts.TEST.getWidth(hpStr, size * 0.85f);

            int hpColor;
            float hpRatio = hp / maxHp;
            if (hpRatio > 0.6f) {
                hpColor = healthColorHigh.getColorNoAlpha();
            } else if (hpRatio > 0.3f) {
                hpColor = healthColorMid.getColorNoAlpha();
            } else {
                hpColor = healthColorLow.getColorNoAlpha();
            }

            Fonts.TEST.draw(hpStr, posX - hpWidth - 4, posY + 1, size * 0.85f, hpColor);
        }

        if (armor.isValue()) {
            float armorX = posX + textWidth + 4;
            int armorPts = player.getArmor();
            if (armorPts > 0) {
                String armorStr = "⛨" + armorPts;
                Fonts.TEST.draw(armorStr, armorX, posY + 1, size * 0.85f, 0xFFAAAAAA);
            }
        }
    }
}
