package rich.screens.hud;

import net.minecraft.client.gui.DrawContext;
import rich.client.draggables.AbstractHudElement;
import rich.modules.impl.render.Hud;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

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

        boolean showBps = Hud.getInstance() != null && Hud.getInstance().showBps.isValue();

        String serverAddress = "";
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getServerInfo() != null) {
            serverAddress = mc.getNetworkHandler().getServerInfo().address;
            if (serverAddress.length() > 24) serverAddress = serverAddress.substring(0, 24) + "...";
        }
        boolean showServer = !serverAddress.isEmpty();

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

        float xTextWidth = Fonts.BOLD.getWidth(xText, 6);
        float yTextWidth = Fonts.BOLD.getWidth(yText, 6);
        float zTextWidth = Fonts.BOLD.getWidth(zText, 6);

        float xValueWidth = Fonts.BOLD.getWidth(xValue, 6);
        float yValueWidth = Fonts.BOLD.getWidth(yValue, 6);
        float zValueWidth = Fonts.BOLD.getWidth(zValue, 6);

        float bpsValueWidth = Fonts.BOLD.getWidth(bpsValue, 6);
        float bpsTextWidth = Fonts.BOLD.getWidth(bpsText, 6);

        float coordsWidth = 10 + 12 + xTextWidth + 2 + xValueWidth + 8 + 8 +
                yTextWidth + 2 + yValueWidth + 8 + 8 +
                zTextWidth + 2 + zValueWidth;

        float bpsWidth = 10 + 12 + 12 + bpsValueWidth + 2 + bpsTextWidth + 5;

        float serverWidth = 0;
        if (showServer) {
            float serverTextWidth = Fonts.BOLD.getWidth(serverAddress, 6);
            serverWidth = 10 + 12 + 12 + serverTextWidth + 5;
        }

        setX((int) x);
        setY((int) y);

        float totalContentWidth = coordsWidth;
        if (showBps) totalContentWidth += bpsWidth + 4;
        if (showServer) totalContentWidth += serverWidth + 4;
        setWidth((int) (totalContentWidth + 24));
        setHeight(22);

        Render2D.gradientRect(x + 12, y + 3, coordsWidth, 20,
                new int[]{
                        new Color(52, 52, 52, 255).getRGB(),
                        new Color(22, 22, 22, 255).getRGB(),
                        new Color(52, 52, 52, 255).getRGB(),
                        new Color(22, 22, 22, 255).getRGB()
                },
                5);

        Render2D.outline(x + 12, y + 3, coordsWidth, 20, 0.35f, new Color(90, 90, 90, 255).getRGB(), 5);

        float textY = y + 7;
        float textX = x + 12;

        Fonts.ICONSTYPETHO.draw("n", textX + 5, textY + 0.5f, 11, new Color(255, 255, 255, 255).getRGB());

        float offsetX = textX + 22;

        Fonts.BOLD.draw(xText, offsetX, textY + 3, 6, new Color(155, 155, 155, 255).getRGB());
        offsetX += xTextWidth + 2;

        Fonts.BOLD.draw(xValue, offsetX, textY + 3, 6, new Color(255, 255, 255, 255).getRGB());
        offsetX += xValueWidth;

        Fonts.TEST.draw("»", offsetX + 4, textY + 1.5f, 8, new Color(155, 155, 155, 255).getRGB());
        offsetX += 12;

        Fonts.BOLD.draw(yText, offsetX, textY + 3, 6, new Color(155, 155, 155, 255).getRGB());
        offsetX += yTextWidth + 2;

        Fonts.BOLD.draw(yValue, offsetX, textY + 3, 6, new Color(255, 255, 255, 255).getRGB());
        offsetX += yValueWidth;

        Fonts.TEST.draw("»", offsetX + 4, textY + 1.5f, 8, new Color(155, 155, 155, 255).getRGB());
        offsetX += 12;

        Fonts.BOLD.draw(zText, offsetX, textY + 3, 6, new Color(155, 155, 155, 255).getRGB());
        offsetX += zTextWidth + 2;

        Fonts.BOLD.draw(zValue, offsetX, textY + 3, 6, new Color(255, 255, 255, 255).getRGB());

        if (showBps) {
            float bpsBoxX = x + 12 + coordsWidth + 4;

            Render2D.gradientRect(bpsBoxX, y + 3, bpsWidth, 20,
                    new int[]{
                            new Color(52, 52, 52, 255).getRGB(),
                            new Color(22, 22, 22, 255).getRGB(),
                            new Color(52, 52, 52, 255).getRGB(),
                            new Color(22, 22, 22, 255).getRGB()
                    },
                    5);

            Render2D.outline(bpsBoxX, y + 3, bpsWidth, 20, 0.35f, new Color(90, 90, 90, 255).getRGB(), 5);

            Fonts.ICONSTYPETHO.draw("l", bpsBoxX + 5, textY + 0.5f, 11, new Color(255, 255, 255, 255).getRGB());

            float bpsOffsetX = bpsBoxX + 20;

            Fonts.TEST.draw("»", bpsOffsetX, textY + 1.5f, 8, new Color(155, 155, 155, 255).getRGB());
            bpsOffsetX += 10;

            Fonts.BOLD.draw(bpsValue, bpsOffsetX, textY + 3, 6, new Color(255, 255, 255, 255).getRGB());
            bpsOffsetX += bpsValueWidth + 2;

            Fonts.BOLD.draw(bpsText, bpsOffsetX, textY + 3, 6, new Color(155, 155, 155, 255).getRGB());
        }

        if (showServer) {
            float serverBoxX = x + 12 + coordsWidth + 4;
            if (showBps) serverBoxX += bpsWidth + 4;

            float serverTextWidth = Fonts.BOLD.getWidth(serverAddress, 6);

            Render2D.gradientRect(serverBoxX, y + 3, serverWidth, 20,
                    new int[]{
                            new Color(52, 52, 52, 255).getRGB(),
                            new Color(22, 22, 22, 255).getRGB(),
                            new Color(52, 52, 52, 255).getRGB(),
                            new Color(22, 22, 22, 255).getRGB()
                    },
                    5);

            Render2D.outline(serverBoxX, y + 3, serverWidth, 20, 0.35f, new Color(90, 90, 90, 255).getRGB(), 5);

            Fonts.ICONSTYPETHO.draw("r", serverBoxX + 5, textY + 0.5f, 11, new Color(255, 255, 255, 255).getRGB());

            float serverOffsetX = serverBoxX + 20;

            Fonts.TEST.draw("»", serverOffsetX, textY + 1.5f, 8, new Color(155, 155, 155, 255).getRGB());
            serverOffsetX += 10;

            Fonts.BOLD.draw(serverAddress, serverOffsetX, textY + 3, 6, new Color(255, 255, 255, 255).getRGB());
        }
    }
}