package excel.screens.hud;

import net.minecraft.client.gui.DrawContext;
import excel.client.draggables.AbstractHudElement;
import excel.modules.impl.render.Hud;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;

import java.awt.*;

public class Info extends AbstractHudElement {

    private double lastX = 0;
    private double lastZ = 0;
    private double currentBps = 0;
    private double displayBps = 0;
    private double targetBps = 0;
    private long lastUpdateTime = 0;

    private static final double BPS_SMOOTHING = 0.05;
    private static final double DISPLAY_SMOOTHING = 0.03;

    private int accentR = 100, accentG = 150, accentB = 255;
    private void updateAccent() {
        int c = Hud.getInstance().getAccentRGB();
        accentR = (c >> 16) & 0xFF;
        accentG = (c >> 8) & 0xFF;
        accentB = c & 0xFF;
    }

    public Info() {
        super("Info", 10, 0, 200, 24, false);
        startAnimation();
    }

    @Override
    public void tick() {
    }

    private double roundToStep(double value, double step) {
        return Math.round(value / step) * step;
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;
        if (mc.player == null) return;
        updateAccent();

        boolean showBps = Hud.getInstance() != null && Hud.getInstance().showBps.isValue();

        long currentTime = System.currentTimeMillis();
        double deltaTime = (currentTime - lastUpdateTime) / 1000.0;

        if (lastUpdateTime > 0 && deltaTime > 0) {
            double dx = mc.player.getX() - lastX;
            double dz = mc.player.getZ() - lastZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double instantBps = distance / deltaTime;

            currentBps = currentBps + (instantBps - currentBps) * BPS_SMOOTHING;
            targetBps = roundToStep(currentBps, 0.50);
        }

        displayBps = displayBps + (targetBps - displayBps) * DISPLAY_SMOOTHING;

        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
        lastUpdateTime = currentTime;

        float x = -5;
        float y = 28;

        int playerX = (int) mc.player.getX();
        int playerY = (int) mc.player.getY();
        int playerZ = (int) mc.player.getZ();

        String xText = "x";
        String yText = "y";
        String zText = "z";

        String xValue = String.valueOf(playerX);
        String yValue = String.valueOf(playerY);
        String zValue = String.valueOf(playerZ);

        double roundedDisplayBps = roundToStep(displayBps, 0.50);
        String bpsValue = String.format("%.2f", roundedDisplayBps);
        String bpsText = "b/s";

        float xTextWidth = Fonts.SFPRO_REGULAR.getWidth(xText, 6);
        float yTextWidth = Fonts.SFPRO_REGULAR.getWidth(yText, 6);
        float zTextWidth = Fonts.SFPRO_REGULAR.getWidth(zText, 6);

        float xValueWidth = Fonts.SFPRO_REGULAR.getWidth(xValue, 6);
        float yValueWidth = Fonts.SFPRO_REGULAR.getWidth(yValue, 6);
        float zValueWidth = Fonts.SFPRO_REGULAR.getWidth(zValue, 6);

        float bpsValueWidth = Fonts.SFPRO_REGULAR.getWidth(bpsValue, 6);
        float bpsTextWidth = Fonts.SFPRO_REGULAR.getWidth(bpsText, 6);

        float coordsWidth = 10 + 12 + xTextWidth + 2 + xValueWidth + 8 + 8 +
                yTextWidth + 2 + yValueWidth + 8 + 8 +
                zTextWidth + 2 + zValueWidth;

        float bpsWidth = 10 + 12 + 12 + bpsValueWidth + 2 + bpsTextWidth + 5;

        setX((int) x);
        setY((int) y);

        if (showBps) {
            setWidth((int) (coordsWidth + bpsWidth + 30));
        } else {
            setWidth((int) (coordsWidth + 24));
        }
        setHeight(22);

        Render2D.gradientRect(x + 12, y + 3, coordsWidth, 20,
                new int[]{
                        new Color(25, 30, 40, 120).getRGB(),
                        new Color(15, 20, 30, 120).getRGB(),
                        new Color(25, 30, 40, 120).getRGB(),
                        new Color(15, 20, 30, 120).getRGB()
                },
                4);

        Render2D.glowOutline(x + 12, y + 3, coordsWidth, 20, 1.0f,
                new Color(accentR, accentG, accentB, 100).getRGB(), 4, 1.0f, 3.0f);

        float textY = y + 7;
        float textX = x + 12;

        Fonts.CATEGORY_ICONS.draw("n", textX + 5, textY + 2f, 9, new Color(180, 190, 210, 255).getRGB());

        float offsetX = textX + 22;

        Fonts.SFPRO_REGULAR.draw(xText, offsetX, textY + 3, 6, new Color(180, 190, 210, 255).getRGB());
        offsetX += xTextWidth + 2;

        Fonts.SFPRO_REGULAR.draw(xValue, offsetX, textY + 3, 6, new Color(220, 230, 255, 255).getRGB());
        offsetX += xValueWidth;

        Fonts.TEST.draw("»", offsetX + 4, textY + 1.5f, 8, new Color(accentR, accentG, accentB, 255).getRGB());
        offsetX += 12;

        Fonts.SFPRO_REGULAR.draw(yText, offsetX, textY + 3, 6, new Color(180, 190, 210, 255).getRGB());
        offsetX += yTextWidth + 2;

        Fonts.SFPRO_REGULAR.draw(yValue, offsetX, textY + 3, 6, new Color(220, 230, 255, 255).getRGB());
        offsetX += yValueWidth;

        Fonts.TEST.draw("»", offsetX + 4, textY + 1.5f, 8, new Color(accentR, accentG, accentB, 255).getRGB());
        offsetX += 12;

        Fonts.SFPRO_REGULAR.draw(zText, offsetX, textY + 3, 6, new Color(180, 190, 210, 255).getRGB());
        offsetX += zTextWidth + 2;

        Fonts.SFPRO_REGULAR.draw(zValue, offsetX, textY + 3, 6, new Color(220, 230, 255, 255).getRGB());

        if (showBps) {
            float bpsBoxX = x + 12 + coordsWidth + 4;

            Render2D.gradientRect(bpsBoxX, y + 3, bpsWidth, 20,
                    new int[]{
                            new Color(25, 30, 40, 120).getRGB(),
                            new Color(15, 20, 30, 120).getRGB(),
                            new Color(25, 30, 40, 120).getRGB(),
                            new Color(15, 20, 30, 120).getRGB()
                    },
                    4);

            Render2D.glowOutline(bpsBoxX, y + 3, bpsWidth, 20, 1.0f,
                    new Color(accentR, accentG, accentB, 100).getRGB(), 4, 1.0f, 3.0f);

            Fonts.CATEGORY_ICONS.draw("b", bpsBoxX + 5, textY + 2f, 9, new Color(180, 190, 210, 255).getRGB());

            float bpsOffsetX = bpsBoxX + 20;

            Fonts.TEST.draw("»", bpsOffsetX, textY + 1.5f, 8, new Color(accentR, accentG, accentB, 255).getRGB());
            bpsOffsetX += 10;

            Fonts.SFPRO_REGULAR.draw(bpsValue, bpsOffsetX, textY + 3, 6, new Color(220, 230, 255, 255).getRGB());
            bpsOffsetX += bpsValueWidth + 2;

            Fonts.SFPRO_REGULAR.draw(bpsText, bpsOffsetX, textY + 3, 6, new Color(180, 190, 210, 255).getRGB());
        }
    }
}
