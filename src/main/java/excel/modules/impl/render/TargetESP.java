package excel.modules.impl.render;

import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import excel.IMinecraft;
import excel.events.api.EventHandler;
import excel.events.impl.WorldRenderEvent;
import excel.modules.impl.combat.Aura;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.ColorSetting;
import excel.modules.module.setting.implement.SelectSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.animations.Animation;
import excel.util.animations.Direction;
import excel.util.animations.OutBack;
import excel.util.render.Render3D;
import excel.util.render.сliemtpipeline.ClientPipelines;
import excel.util.render.font.Fonts;
import excel.util.timer.StopWatch;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class TargetESP extends ModuleStructure implements IMinecraft {

    private static TargetESP instance;

    public static TargetESP getInstance() {
        return instance;
    }

    Animation espAnim = new OutBack().setMs(300).setValue(1);

    SelectSetting mode = new SelectSetting("Режим", "Тип TargetESP")
            .value("Rhomb", "Ghost", "Chain", "Crystals", "Circle",
                    "Marker", "Spirits", "Orbits")
            .selected("Rhomb");

    SliderSettings crystalRotationSpeed = new SliderSettings("Скорость вращения кристаллов",
            "Скорость вращения кристаллов")
            .range(0.1f, 2.0f)
            .visible(() -> mode.isSelected("Crystals"));

    ColorSetting color1 = new ColorSetting("Цвет 1", "Первый цвет градиента")
            .setColor(new java.awt.Color(255, 101, 57, 255).getRGB());

    ColorSetting color2 = new ColorSetting("Цвет 2", "Второй цвет градиента")
            .setColor(new java.awt.Color(255, 50, 150, 255).getRGB());

    ColorSetting color3 = new ColorSetting("Цвет 3", "Третий цвет")
            .setColor(new java.awt.Color(150, 50, 255, 255).getRGB())
            .visible(() -> mode.isSelected("Ghost") || mode.isSelected("Spirits"));

    private long timestamp4;
    private long timestamp5;
    private float value23;
    private float animationNurik;
    private long currentTime;

    private static final int ORBIT_PARTICLE_COUNT = 3;
    private static final float ORBIT_BASE_RADIUS = 0.4f;
    private static final float ORBIT_SPEED = 15.0f;
    private static final int ORBIT_TRAIL_LENGTH = 40;
    private static final float[] SCALE_CACHE = new float[101];
    static {
        for (int k = 0; k <= 100; k++) {
            SCALE_CACHE[k] = Math.max(0.28f * (k / 100f), 0.15f);
        }
    }

    private Vec3d[] orbitPositions = new Vec3d[ORBIT_PARTICLE_COUNT];
    private Vec3d[] orbitMotions = new Vec3d[ORBIT_PARTICLE_COUNT];
    private List<Vec3d>[] orbitTrails = new List[ORBIT_PARTICLE_COUNT];
    private float movingAngle = 0;
    private long lastOrbitTime = 0;
    private Animation orbitShrinkAnim = new OutBack().setMs(300).setValue(0);
    private float crystalMoving = 0;

    private LivingEntity lastTarget = null;
    private float hurtProgress = 0;
    private float rotationAngle = 0;
    private long lastFrameTime = System.currentTimeMillis();
    private static final float TARGET_FPS = 60f;
    private static final float TARGET_FRAME_TIME = 1000f / TARGET_FPS;

    public TargetESP() {
        super("TargetEsp", "Подсветка текущей цели", ModuleCategory.RENDER);
        instance = this;

        crystalRotationSpeed.setValue(0.5f);
        settings(mode, crystalRotationSpeed, color1, color2, color3);

        for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
            orbitTrails[i] = new ArrayList<>();
            orbitMotions[i] = Vec3d.ZERO;
        }
        timestamp4 = System.currentTimeMillis();
        timestamp5 = System.nanoTime();
        currentTime = System.currentTimeMillis();
    }

    private float getDeltaTime() {
        long now = System.currentTimeMillis();
        float deltaMs = now - lastFrameTime;
        lastFrameTime = now;
        deltaMs = Math.max(1f, Math.min(deltaMs, 100f));
        return deltaMs / TARGET_FRAME_TIME;
    }

    @EventHandler
    public void onRender3D(WorldRenderEvent e) {
        LivingEntity target = null;
        if (Aura.getInstance() != null && Aura.getInstance().isState()) {
            target = Aura.target;
        }

        if (target == null) {
            lastTarget = null;
            espAnim.setDirection(Direction.BACKWARDS);
            orbitShrinkAnim.setDirection(Direction.BACKWARDS);
            Render3D.resetCircleSmoothing();
            for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
                orbitPositions[i] = null;
                orbitMotions[i] = Vec3d.ZERO;
                orbitTrails[i].clear();
            }
            return;
        }

        espAnim.setDirection(Direction.FORWARDS);
        float alpha = espAnim.getOutput().floatValue();
        if (alpha <= 0.01f) return;

        if (lastTarget != target) {
            for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
                orbitPositions[i] = null;
                orbitMotions[i] = Vec3d.ZERO;
                orbitTrails[i].clear();
            }
        }
        lastTarget = target;

        float deltaTime = getDeltaTime();

        float hurtDecay = 0.1f * deltaTime;
        hurtProgress = target.hurtTime > 0 ? (float) target.hurtTime / 10f : Math.max(0, hurtProgress - hurtDecay);

        Render3D.updateTargetEsp(deltaTime);

        String currentMode = mode.getSelected();

        if ("Circle".equals(currentMode)) {
            renderCircle(e.getStack(), target, alpha);
            return;
        }

        MatrixStack stack = e.getStack();
        VertexConsumerProvider.Immediate provider = mc.getBufferBuilders().getEntityVertexConsumers();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        float partialTicks = e.getPartialTicks();
        Vec3d targetPos = target.getLerpedPos(partialTicks);

        stack.push();
        stack.translate(targetPos.x - camPos.x, targetPos.y - camPos.y, targetPos.z - camPos.z);

        switch (currentMode) {
            case "Rhomb" -> renderRhomb(stack, provider, target, alpha);
            case "Ghost" -> renderGhost(stack, provider, target, alpha);
            case "Chain" -> renderChain(stack, provider, target, alpha, deltaTime);
            case "Crystals" -> {
                rotationAngle += crystalRotationSpeed.getValue() * deltaTime;
                rotationAngle = rotationAngle % 360;
                renderCrystals(stack, provider, target, alpha, deltaTime);
            }
            case "Marker" -> renderMarker(stack, target, alpha, e);
            case "Spirits" -> renderSpirits(stack, provider, target, alpha, e);
            case "Orbits" -> renderGhostOrbits(stack, provider, target, alpha, e);
        }

        provider.draw();
        stack.pop();
    }

    private void renderCircle(MatrixStack stack, LivingEntity target, float alpha) {
        int baseColor1 = color1.getColor();
        int baseColor2 = color2.getColor();
        if (hurtProgress > 0) {
            baseColor1 = lerpColor(baseColor1, 0xFFFF0000, hurtProgress);
            baseColor2 = lerpColor(baseColor2, 0xFFFF0000, hurtProgress);
        }
        Render3D.drawCircle(stack, target, alpha, hurtProgress, baseColor1, baseColor2);
    }

    private void renderChain(MatrixStack stack, VertexConsumerProvider provider, LivingEntity target, float alpha, float deltaTime) {
        VertexConsumer consumer = provider
                .getBuffer(ClientPipelines.CHAIN_ESP.apply(net.minecraft.util.Identifier.of("excel", "images/world/chain.png")));

        float animValue = (System.currentTimeMillis() % 360000) / 1000f * 60f;
        float gradusX = (float) (20 * Math.min(1 + Math.sin(Math.toRadians(animValue)), 1));
        float gradusZ = (float) (20 * (Math.min(1 + Math.sin(Math.toRadians(animValue)), 2) - 1));
        float width = target.getWidth() * 3;

        int linksStep = 18;
        int totalAngle = 360 * 2;
        float chainSizeVal = 8;
        float down = 1.5f;
        float chainScale = 0.5f;

        int alphaVal = MathHelper.clamp((int) (alpha * 128), 0, 128);
        int baseColor1 = color1.getColor();
        int baseColor2 = color2.getColor();
        if (hurtProgress > 0) {
            baseColor1 = lerpColor(baseColor1, 0xFFFF0000, hurtProgress);
            baseColor2 = lerpColor(baseColor2, 0xFFFF0000, hurtProgress);
        }
        int c1 = withAlpha(baseColor1, alphaVal);
        int c2 = withAlpha(baseColor2, alphaVal);
        float rotationValue = (System.currentTimeMillis() % 720000) / 1000f * 30f;

        for (int chain = 0; chain < 2; chain++) {
            float val = 1.2f - 0.5f * (chain == 0 ? 1.0f : 0.9f);
            stack.push();
            stack.translate(0, target.getHeight() / 2.0f, 0);
            stack.scale(chainScale, chainScale, chainScale);
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(chain == 0 ? gradusX : -gradusX));
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(chain == 0 ? gradusZ : -gradusZ));

            float x = 0, y = -0.5f, z = 0;
            Matrix4f matrix = stack.peek().getPositionMatrix();
            int modif = linksStep / 2;
            for (int i = 0; i < totalAngle; i += modif) {
                float offsetX = (chain == 0 ? gradusX : -gradusX) / 100F;
                float offsetZ = (chain == 0 ? -gradusZ : gradusZ) / 100F;
                float prevSin = (float) (x + offsetX + Math.sin(Math.toRadians(i - modif + rotationValue)) * width * val);
                float prevCos = (float) (z + offsetZ + Math.cos(Math.toRadians(i - modif + rotationValue)) * width * val);
                float sin = (float) (x + offsetX + Math.sin(Math.toRadians(i + rotationValue)) * width * val);
                float cos = (float) (z + offsetZ + Math.cos(Math.toRadians(i + rotationValue)) * width * val);
                float u0 = 1f / 360f * (float) (i - modif) * chainSizeVal;
                float u1 = 1f / 360f * (float) i * chainSizeVal;
                consumer.vertex(matrix, prevSin, y, prevCos).texture(u0, 0).color(c1);
                consumer.vertex(matrix, sin, y, cos).texture(u1, 0).color(c1);
                consumer.vertex(matrix, sin, y + down, cos).texture(u1, 0.99f).color(c2);
                consumer.vertex(matrix, prevSin, y + down, prevCos).texture(u0, 0.99f).color(c2);
            }
            stack.pop();
        }
    }

    private void renderRhomb(MatrixStack stack, VertexConsumerProvider provider, LivingEntity target, float alpha) {
        VertexConsumer consumer = provider
                .getBuffer(ClientPipelines.ROMB_ESP.apply(net.minecraft.util.Identifier.of("excel", "images/world/cube.png")));
        Quaternionf camRot = mc.gameRenderer.getCamera().getRotation();
        stack.translate(0, target.getHeight() / 2f, 0);
        stack.multiply(camRot);
        float timeRotation = (System.currentTimeMillis() % 6283) / 1000f;
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(timeRotation) * 360));
        float size = 0.5f;
        stack.scale(size, size, 1);
        int c1 = withAlpha(color1.getColor(), (int) (255 * alpha));
        int c2 = withAlpha(color2.getColor(), (int) (255 * alpha));
        Vector3f[] quad = {
                new Vector3f(-1, -1, 0), new Vector3f(-1, 1, 0),
                new Vector3f(1, 1, 0), new Vector3f(1, -1, 0)
        };
        var m = stack.peek();
        consumer.vertex(m, quad[0].x, quad[0].y, 0).texture(0, 0).color(c2);
        consumer.vertex(m, quad[1].x, quad[1].y, 0).texture(0, 1).color(c1);
        consumer.vertex(m, quad[2].x, quad[2].y, 0).texture(1, 1).color(c2);
        consumer.vertex(m, quad[3].x, quad[3].y, 0).texture(1, 0).color(c1);
    }

    private void renderGhost(MatrixStack stack, VertexConsumerProvider consumers, LivingEntity target, float alpha) {
        VertexConsumer consumer = consumers
                .getBuffer(ClientPipelines.GHOSTS_ESP.apply(net.minecraft.util.Identifier.of("excel", "images/particle/ghost-glow.png")));
        stack.translate(0, target.getHeight() * 0.5f, 0);
        double radius = 0.7f;
        float particleSize = 0.5f;
        long elapsed = System.currentTimeMillis();
        long timeMs = (long) ((float) (elapsed - timestamp4) / 2.0F);
        long nanoTime = System.nanoTime();
        float deltaTime = (float) (nanoTime - timestamp5) / 2000000.0F;
        timestamp5 = nanoTime;
        if (hurtProgress > 0) value23 += hurtProgress * deltaTime;

        for (int layer = 0; layer < 3; layer++) {
            for (int i = 0; i < 14; i++) {
                stack.push();
                float progress = (float) i / 13.0F;
                float size = (0.5F * (1.0F - progress) + 0.5F * progress) * alpha;
                double angle = (double) (0.2F * ((float) timeMs + value23 - (float) i * 7.0F) / 15.0F);
                boolean firstHalf = progress < 0.5F;
                float wave = firstHalf ? progress * 2.0F : (1.0F - progress) * 2.0F;
                double amplitude = Math.sin((double) wave * Math.PI) * 2.0;
                Random random = new Random((long) i * 12345L);
                double offsetX = (random.nextDouble() - 0.5) * amplitude;
                double offsetY = (random.nextDouble() - 0.5) * amplitude;
                double offsetZ = (random.nextDouble() - 0.5) * amplitude;

                switch (layer) {
                    case 0 -> stack.translate(Math.cos(angle) * radius + offsetX, offsetY, Math.sin(angle) * radius + offsetZ);
                    case 1 -> stack.translate(-Math.sin(angle) * radius + offsetX, offsetY, Math.cos(angle) * radius + offsetZ);
                    case 2 -> stack.translate(-Math.cos(angle) * radius + offsetX, offsetY, -Math.sin(angle) * radius + offsetZ);
                }

                float pSize = size * 0.5F;
                int ci = switch (layer) { case 0 -> color1.getColor(); case 1 -> color2.getColor(); default -> color3.getColor(); };
                int color = withAlpha(ci, (int) (alpha * 200));

                stack.multiply(mc.gameRenderer.getCamera().getRotation());
                Matrix4f matrix = stack.peek().getPositionMatrix();
                consumer.vertex(matrix, -pSize, -pSize, 0).texture(0, 0).color(color);
                consumer.vertex(matrix, pSize, -pSize, 0).texture(1, 0).color(color);
                consumer.vertex(matrix, pSize, pSize, 0).texture(1, 1).color(color);
                consumer.vertex(matrix, -pSize, pSize, 0).texture(0, 1).color(color);
                stack.pop();
            }
        }
    }

    private static boolean blendSetup = false;
    private void renderMarker(MatrixStack stack, LivingEntity target, float alpha, WorldRenderEvent e) {
        stack.translate(0, target.getHeight() + 0.5f, 0);
        Camera camera = mc.gameRenderer.getCamera();
        stack.multiply(camera.getRotation());

        float scale = -0.15F * alpha;
        stack.scale(scale, scale, scale);

        float size = 12.0F;
        int textAlpha = (int) (alpha * 255);
        int color = color1.getColor();

        Fonts.BOLD.drawCentered("E", 0, -size / 2f, 14f, withAlpha(color, textAlpha));
        Fonts.REGULARNEW.drawCentered(target.getName().getString(), 0, size / 2f + 2, 5f, withAlpha(0xFFFFFF, textAlpha));
    }

    private void renderSpirits(MatrixStack stack, VertexConsumerProvider provider, LivingEntity target, float alpha, WorldRenderEvent e) {
        Camera camera = mc.gameRenderer.getCamera();
        long timeMs = (long) ((float) (System.currentTimeMillis() - timestamp4) / 2.0F);
        float hurtTime = target.hurtTime > 0 ? ((float) target.hurtTime - e.getPartialTicks()) / 10.0F : 0.0F;
        long nanoTime = System.nanoTime();
        float deltaTime = (float) (nanoTime - timestamp5) / 2000000.0F;
        timestamp5 = nanoTime;
        value23 += hurtTime * deltaTime;

        stack.translate(0, target.getHeight() / 2f, 0);
        stack.scale(1.5F, 1.5F, 1.5F);

        VertexConsumer consumer = provider.getBuffer(ClientPipelines.GHOSTS_ESP.apply(net.minecraft.util.Identifier.of("excel", "images/particle/glow.png")));

        float animValue = -0.15F * alpha + 0.65F;
        for (int layer = 0; layer < 3; layer++) {
            for (int i = 0; i < 14; i++) {
                stack.push();
                float progress = (float) i / 13.0F;
                float size = (0.55F * (1.0F - progress) + 0.2F * progress) * alpha;
                double angle = (double) (0.2F * ((float) timeMs + value23 - (float) i * 7.0F) / 15.0F);
                boolean firstHalf = progress < 0.5F;
                float wave = firstHalf ? progress * 2.0F : (1.0F - progress) * 2.0F;
                double amplitude = Math.sin((double) wave * Math.PI) * 2.0;
                Random random = new Random((long) i * 12345L);
                double offsetX = (random.nextDouble() - 0.5) * amplitude;
                double offsetY = (random.nextDouble() - 0.5) * amplitude;
                double offsetZ = (random.nextDouble() - 0.5) * amplitude;
                double posX = -Math.sin(angle) * animValue;
                double posZ = -Math.cos(angle) * animValue;

                switch (layer) {
                    case 0 -> stack.translate(posX + offsetX, posZ + offsetY, -posZ + offsetZ);
                    case 1 -> stack.translate(-posX + offsetX, posX + offsetY, -posZ + offsetZ);
                    case 2 -> stack.translate(-posX + offsetX, -posX + offsetY, posZ + offsetZ);
                }

                float particleSize = size * 0.5F;
                int ci = switch (layer) { case 0 -> color1.getColor(); case 1 -> color2.getColor(); default -> color3.getColor(); };
                int color = withAlpha(ci, (int) (alpha * 200));

                stack.multiply(camera.getRotation());
                Matrix4f matrix = stack.peek().getPositionMatrix();
                consumer.vertex(matrix, -particleSize, -particleSize, 0).texture(0, 0).color(color);
                consumer.vertex(matrix, particleSize, -particleSize, 0).texture(1, 0).color(color);
                consumer.vertex(matrix, particleSize, particleSize, 0).texture(1, 1).color(color);
                consumer.vertex(matrix, -particleSize, particleSize, 0).texture(0, 1).color(color);
                stack.pop();
            }
        }
    }

    private void renderGhostOrbits(MatrixStack stack, VertexConsumerProvider provider, LivingEntity target, float alpha, WorldRenderEvent e) {
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        float partialTicks = e.getPartialTicks();
        Vec3d targetCenter = target.getLerpedPos(partialTicks).add(0, target.getHeight() / 2.0, 0);

        long now = System.currentTimeMillis();
        if (lastOrbitTime == 0) lastOrbitTime = now;
        float dtMs = now - lastOrbitTime;
        lastOrbitTime = now;

        movingAngle += (20.0f * dtMs / 16.667f) * (ORBIT_SPEED / 55.0f);

        boolean isHurt = target.hurtTime > 7;
        orbitShrinkAnim.setDirection(isHurt ? Direction.FORWARDS : Direction.BACKWARDS);
        float shrinkValue = orbitShrinkAnim.getOutput().floatValue();

        VertexConsumer consumer = provider.getBuffer(ClientPipelines.GHOSTS_ESP.apply(net.minecraft.util.Identifier.of("excel", "images/particle/glow.png")));

        int baseColor = color1.getColor();
        if (hurtProgress > 0) baseColor = lerpColor(baseColor, 0xFFFF0000, hurtProgress);

        for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
            float angleOffset = i * 360f / ORBIT_PARTICLE_COUNT;
            float currentAngle = movingAngle + angleOffset;
            double radian = Math.toRadians(currentAngle);

            float orbitRadius = ORBIT_BASE_RADIUS - shrinkValue * ORBIT_BASE_RADIUS;
            float ox = (float) Math.sin(radian) * orbitRadius;
            float oz = (float) Math.cos(radian) * orbitRadius;
            double oy = 0.3 * Math.sin(Math.toRadians(movingAngle / (i + 1.0f)));

            Vec3d targetGhostPos = targetCenter.add(ox, oy, oz);
            if (orbitPositions[i] == null || orbitPositions[i].distanceTo(targetGhostPos) > 10) {
                orbitPositions[i] = targetGhostPos;
                orbitMotions[i] = Vec3d.ZERO;
            }

            float fpsFactor = 500 / Math.max(mc.getCurrentFps(), 10);
            float mul = 0.1f * fpsFactor;
            Vec3d diff = targetGhostPos.subtract(orbitPositions[i]);
            orbitMotions[i] = diff.multiply(mul, mul, mul);
            orbitPositions[i] = orbitPositions[i].add(orbitMotions[i]);

            if (orbitTrails[i].isEmpty() || orbitTrails[i].get(0).distanceTo(orbitPositions[i]) > 0.01) {
                orbitTrails[i].add(0, orbitPositions[i]);
                while (orbitTrails[i].size() > ORBIT_TRAIL_LENGTH)
                    orbitTrails[i].remove(orbitTrails[i].size() - 1);
            }

            for (int j = 0; j < orbitTrails[i].size(); j++) {
                Vec3d p = orbitTrails[i].get(j);
                float offset = 1.0f - (float) j / ORBIT_TRAIL_LENGTH;
                stack.push();
                stack.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
                stack.multiply(camera.getRotation());
                Matrix4f matrix = stack.peek().getPositionMatrix();
                float opacity = (float) Math.pow(offset, 1.8) * alpha * 0.7f;
                int color = withAlpha(baseColor, (int) (opacity * 255));
                float scale = SCALE_CACHE[Math.min((int) (offset * 100), 100)] * 0.8f;
                consumer.vertex(matrix, -scale, scale, 0).texture(0f, 1f).color(color);
                consumer.vertex(matrix, scale, scale, 0).texture(1f, 1f).color(color);
                consumer.vertex(matrix, scale, -scale, 0).texture(1f, 0f).color(color);
                consumer.vertex(matrix, -scale, -scale, 0).texture(0f, 0f).color(color);
                stack.pop();
            }

            if (!orbitTrails[i].isEmpty()) {
                Vec3d head = orbitTrails[i].get(0);
                stack.push();
                stack.translate(head.x - camPos.x, head.y - camPos.y, head.z - camPos.z);
                stack.multiply(camera.getRotation());
                Matrix4f matrix = stack.peek().getPositionMatrix();
                float headScale = 0.35f * alpha;
                int headColor = withAlpha(baseColor, (int) (120 * alpha));
                consumer.vertex(matrix, -headScale, headScale, 0).texture(0f, 1f).color(headColor);
                consumer.vertex(matrix, headScale, headScale, 0).texture(1f, 1f).color(headColor);
                consumer.vertex(matrix, headScale, -headScale, 0).texture(1f, 0f).color(headColor);
                consumer.vertex(matrix, -headScale, -headScale, 0).texture(0f, 0f).color(headColor);
                stack.pop();
            }
        }
    }

    private void renderCrystals(MatrixStack stack, VertexConsumerProvider provider, LivingEntity target, float alpha, float deltaTime) {
        crystalMoving += 1.0f;
        float entityHeight = target.getHeight();
        float entityWidth = target.getWidth();
        float width = entityWidth * 1.5f;

        int baseColor = color1.getColor();
        if (hurtProgress > 0) baseColor = lerpColor(baseColor, 0xFFFF0000, hurtProgress);

        int crystalAlpha = Math.min(255, (int) (alpha * 255));
        int cr = (baseColor >> 16) & 0xFF;
        int cg = (baseColor >> 8) & 0xFF;
        int cb = baseColor & 0xFF;

        int cTop = (crystalAlpha << 24) | (Math.min(255, cr + 60) << 16) | (Math.min(255, cg + 60) << 8) | Math.min(255, cb + 60);
        int cSide1 = (crystalAlpha << 24) | (Math.min(255, cr + 30) << 16) | (Math.min(255, cg + 30) << 8) | Math.min(255, cb + 30);
        int cSide2 = (crystalAlpha << 24) | (cr << 16) | (cg << 8) | cb;
        int cBot = (crystalAlpha << 24) | (Math.max(0, cr - 30) << 16) | (Math.max(0, cg - 30) << 8) | Math.max(0, cb - 30);

        float cw = 0.075f;
        float ch = 0.20f;

        VertexConsumer crystalConsumer = provider.getBuffer(ClientPipelines.CRYSTAL_FILLED);
        VertexConsumer glowConsumer = provider.getBuffer(ClientPipelines.CRYSTAL_GLOW);

        Camera camera = mc.gameRenderer.getCamera();

        for (int i = 0; i < 360; i += 19) {
            float val = 1.2f - 0.5f * alpha;
            float angleDeg = i + crystalMoving * 0.3f;
            float angleRad = (float) Math.toRadians(angleDeg);
            float sin = (float) (Math.sin(angleRad) * width * val);
            float cos = (float) (Math.cos(angleRad) * width * val);
            float heightPrc = ((i / 20.0f) * 0.6180339f) % 1.0f;
            float crystalY = entityHeight * heightPrc;

            stack.push();
            stack.translate(sin, crystalY, cos);
            Vector3f dir = new Vector3f(-sin, 0, -cos).normalize();
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir);
            stack.multiply(rotation);
            Matrix4f matrix = stack.peek().getPositionMatrix();

            float[] ex = {cw, 0, -cw, 0};
            float[] ez = {0, cw, 0, -cw};

            for (int j = 0; j < 4; j++) {
                int next = (j + 1) % 4;
                int fc = (j % 2 == 0) ? cTop : cSide1;
                crystalConsumer.vertex(matrix, 0, ch, 0).color(fc);
                crystalConsumer.vertex(matrix, ex[j], 0, ez[j]).color(fc);
                crystalConsumer.vertex(matrix, ex[next], 0, ez[next]).color(fc);
            }
            for (int j = 0; j < 4; j++) {
                int next = (j + 1) % 4;
                int fc = (j % 2 == 0) ? cBot : cSide2;
                crystalConsumer.vertex(matrix, 0, -ch, 0).color(fc);
                crystalConsumer.vertex(matrix, ex[next], 0, ez[next]).color(fc);
                crystalConsumer.vertex(matrix, ex[j], 0, ez[j]).color(fc);
            }
            stack.pop();

            stack.push();
            stack.translate(sin, crystalY, cos);
            stack.multiply(camera.getRotation());
            Matrix4f gmatrix = stack.peek().getPositionMatrix();
            float glowSize = 0.15f * alpha;
            int glowColor = withAlpha(baseColor, (int) (alpha * 100));
            glowConsumer.vertex(gmatrix, -glowSize, glowSize, 0).texture(0f, 1f).color(glowColor);
            glowConsumer.vertex(gmatrix, glowSize, glowSize, 0).texture(1f, 1f).color(glowColor);
            glowConsumer.vertex(gmatrix, glowSize, -glowSize, 0).texture(1f, 0f).color(glowColor);
            glowConsumer.vertex(gmatrix, -glowSize, -glowSize, 0).texture(0f, 0f).color(glowColor);
            stack.pop();
        }
    }

    private int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16) | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }

    private int withAlpha(int color, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
