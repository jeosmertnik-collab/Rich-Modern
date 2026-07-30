package excel.util.repository.way;

import lombok.Getter;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import excel.IMinecraft;
import excel.events.api.EventHandler;
import excel.events.api.EventManager;
import excel.events.impl.DeathScreenEvent;
import excel.events.impl.DrawEvent;
import excel.events.impl.WorldRenderEvent;
import excel.util.ColorUtil;
import excel.util.config.impl.way.WayConfig;
import excel.util.math.Projection;
import excel.util.render.Render2D;
import excel.util.render.Render3D;
import excel.util.render.font.Font;
import excel.util.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
public class WayRepository implements IMinecraft {
    private static WayRepository instance;
    private final List<Way> wayList = new ArrayList<>();
    private int deathCounter = 0;

    public WayRepository() {
        instance = this;
    }

    public static WayRepository getInstance() {
        if (instance == null) {
            instance = new WayRepository();
        }
        return instance;
    }

    public void init() {
        EventManager.register(this);
        WayConfig.getInstance().load();
    }

    public boolean isEmpty() {
        return wayList.isEmpty();
    }

    public void addWay(String name, BlockPos pos, String server) {
        wayList.add(new Way(name, pos, server));
    }

    public void addWayAndSave(String name, BlockPos pos, String server) {
        addWay(name, pos, server);
        WayConfig.getInstance().save();
    }

    public boolean hasWay(String name) {
        return wayList.stream().anyMatch(way -> way.name().equalsIgnoreCase(name));
    }

    public Optional<Way> getWay(String name) {
        return wayList.stream()
                .filter(way -> way.name().equalsIgnoreCase(name))
                .findFirst();
    }

    public void deleteWay(String name) {
        wayList.removeIf(way -> way.name().equalsIgnoreCase(name));
    }

    public void deleteWayAndSave(String name) {
        deleteWay(name);
        WayConfig.getInstance().save();
    }

    public void clearList() {
        wayList.clear();
    }

    public void clearListAndSave() {
        clearList();
        WayConfig.getInstance().save();
    }

    public int size() {
        return wayList.size();
    }

    public List<String> getWayNames() {
        return wayList.stream().map(Way::name).collect(Collectors.toList());
    }

    public List<String> getWayNamesForServer(String server) {
        return wayList.stream()
                .filter(way -> way.server().equalsIgnoreCase(server))
                .map(Way::name)
                .collect(Collectors.toList());
    }

    public void setWays(List<Way> ways) {
        wayList.clear();
        wayList.addAll(ways);
    }

    public String getCurrentServer() {
        if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getServerInfo() == null) {
            return "";
        }
        return mc.getNetworkHandler().getServerInfo().address;
    }

    private boolean isInFrontOfCamera(Vec3d worldPos) {
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return false;

        Vec3d cameraPos = camera.getCameraPos();
        Vec3d toPoint = worldPos.subtract(cameraPos);

        float yaw = camera.getYaw();
        float pitch = camera.getPitch();

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double lookY = -Math.sin(pitchRad);
        double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);

        Vec3d lookDir = new Vec3d(lookX, lookY, lookZ);

        return lookDir.dotProduct(toPoint) > 0;
    }

    @EventHandler
    public void onRender2D(DrawEvent event) {
        if (isEmpty() || mc.player == null || mc.world == null) return;
        if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getServerInfo() == null) return;

        String currentServer = getCurrentServer();

        for (Way way : wayList) {
            if (!way.server().equalsIgnoreCase(currentServer)) continue;

            Vec3d wayVec = way.pos().toCenterPos();

            if (!isInFrontOfCamera(wayVec)) continue;

            Vec3d screenPos = Projection.worldSpaceToScreenSpace(wayVec);

            if (screenPos.z <= 0) continue;

            double distance = mc.player.getEntityPos().distanceTo(wayVec);
            String text = way.name() + " - " + String.format("%.1f", distance) + "m";

            Font font = Fonts.BOLD;
            float fontSize = 6f;
            float textWidth = font.getWidth(text, fontSize);
            float textHeight = font.getHeight(fontSize);
            float padding = 3f;

            float x = (float) screenPos.x - textWidth / 2;
            float y = (float) screenPos.y - textHeight / 2;

            Render2D.rect(
                    x - padding,
                    y - padding + 0.5f,
                    textWidth + padding * 2,
                    textHeight + padding * 2,
                    0xE0131315,
                    2f
            );


            font.drawCentered(text, (float) screenPos.x, y + 1f, fontSize, 0xFFFFFFFF);
        }
    }

    @EventHandler
    public void onDeath(DeathScreenEvent event) {
        if (mc.player == null || mc.world == null) return;
        String server = getCurrentServer();
        if (server.isEmpty()) return;
        deathCounter++;
        BlockPos pos = mc.player.getBlockPos();
        String name = "☠ Death #" + deathCounter;
        addWayAndSave(name, pos, server);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent event) {
        if (isEmpty() || mc.player == null || mc.world == null) return;
        if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getServerInfo() == null) return;

        String currentServer = getCurrentServer();
        int markerColor = ColorUtil.getColor(255, 80, 80, 180);

        for (Way way : wayList) {
            if (!way.server().equalsIgnoreCase(currentServer)) continue;

            BlockPos pos = way.pos();
            double dist = mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist > 4096) continue;

            boolean isDeath = way.name().startsWith("☠");
            int color = isDeath ? ColorUtil.getColor(255, 60, 60, 160) : markerColor;

            Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            Render3D.drawBox(box, color, 1);

            Vec3d top = Vec3d.of(pos).add(0.5, 3, 0.5);
            Vec3d bottom = Vec3d.of(pos).add(0.5, 1, 0.5);
            Render3D.drawLine(top, bottom, color, 1.5f, false);
        }
    }
}