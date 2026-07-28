package excel.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import excel.IMinecraft;
import excel.events.api.EventHandler;
import excel.events.impl.WorldRenderEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.BooleanSetting;
import excel.util.repository.friend.FriendUtils;
import excel.modules.module.setting.implement.ColorSetting;
import excel.modules.module.setting.implement.MultiSelectSetting;
import excel.modules.module.setting.implement.SelectSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.render.Render3D;
import excel.util.render.сliemtpipeline.ClientPipelines;
import excel.util.render.wings.WingsShaderRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class Wings extends ModuleStructure implements IMinecraft {
    private static Wings instance;

    private static final float DEFAULT_SPREAD = 8.0f;
    private static final int DEFAULT_ALPHA = 220;

    private static final WingPoint[][] SHAPES = new WingPoint[8][];
    private static final int ANGELIC = 0, DRAGON = 1, BUTTERFLY = 2, PHOENIX = 3,
            CRYSTAL = 4, MECHANICAL = 5, FAIRY = 6, DEMON = 7;

    static {
        SHAPES[ANGELIC] = new WingPoint[]{
                new WingPoint(0.08f, 0.10f, 0.88f),
                new WingPoint(0.28f, 0.34f, 0.78f),
                new WingPoint(0.56f, 0.82f, 0.62f),
                new WingPoint(0.86f, 0.30f, 0.52f),
                new WingPoint(1.14f, 0.46f, 0.40f),
                new WingPoint(1.24f, 0.04f, 0.30f),
                new WingPoint(1.02f, -0.18f, 0.28f),
                new WingPoint(1.18f, -0.64f, 0.22f),
                new WingPoint(0.86f, -0.46f, 0.20f),
                new WingPoint(0.80f, -0.98f, 0.14f),
                new WingPoint(0.54f, -0.74f, 0.16f),
                new WingPoint(0.30f, -1.16f, 0.12f),
                new WingPoint(0.10f, -0.54f, 0.18f)
        };

        SHAPES[DRAGON] = new WingPoint[]{
                new WingPoint(0.10f, 0.12f, 0.90f),
                new WingPoint(0.22f, 0.40f, 0.80f),
                new WingPoint(0.48f, 0.72f, 0.65f),
                new WingPoint(0.80f, 0.60f, 0.55f),
                new WingPoint(1.10f, 0.70f, 0.42f),
                new WingPoint(1.30f, 0.30f, 0.35f),
                new WingPoint(1.20f, -0.10f, 0.30f),
                new WingPoint(1.05f, -0.50f, 0.25f),
                new WingPoint(0.70f, -0.35f, 0.22f),
                new WingPoint(0.50f, -0.70f, 0.18f),
                new WingPoint(0.20f, -0.50f, 0.15f),
                new WingPoint(0.05f, -0.30f, 0.20f)
        };

        SHAPES[BUTTERFLY] = new WingPoint[]{
                new WingPoint(0.12f, 0.15f, 0.92f),
                new WingPoint(0.30f, 0.50f, 0.85f),
                new WingPoint(0.50f, 0.90f, 0.70f),
                new WingPoint(0.70f, 0.80f, 0.60f),
                new WingPoint(0.85f, 0.55f, 0.50f),
                new WingPoint(0.75f, 0.20f, 0.45f),
                new WingPoint(0.55f, -0.15f, 0.40f),
                new WingPoint(0.40f, -0.60f, 0.30f),
                new WingPoint(0.25f, -0.85f, 0.20f),
                new WingPoint(0.12f, -0.65f, 0.25f),
                new WingPoint(0.06f, -0.35f, 0.30f)
        };

        SHAPES[PHOENIX] = new WingPoint[]{
                new WingPoint(0.10f, 0.14f, 0.90f),
                new WingPoint(0.25f, 0.45f, 0.82f),
                new WingPoint(0.52f, 0.78f, 0.68f),
                new WingPoint(0.82f, 0.50f, 0.55f),
                new WingPoint(1.15f, 0.55f, 0.42f),
                new WingPoint(1.28f, 0.15f, 0.32f),
                new WingPoint(1.20f, -0.25f, 0.28f),
                new WingPoint(1.10f, -0.55f, 0.24f),
                new WingPoint(1.25f, -0.85f, 0.18f),
                new WingPoint(0.90f, -0.65f, 0.16f),
                new WingPoint(0.60f, -0.90f, 0.14f),
                new WingPoint(0.30f, -0.70f, 0.12f),
                new WingPoint(0.08f, -0.40f, 0.16f)
        };

        SHAPES[CRYSTAL] = new WingPoint[]{
                new WingPoint(0.15f, 0.10f, 0.85f),
                new WingPoint(0.40f, 0.35f, 0.75f),
                new WingPoint(0.70f, 0.60f, 0.60f),
                new WingPoint(1.00f, 0.40f, 0.50f),
                new WingPoint(0.85f, 0.10f, 0.45f),
                new WingPoint(1.10f, -0.15f, 0.35f),
                new WingPoint(0.90f, -0.45f, 0.30f),
                new WingPoint(0.65f, -0.30f, 0.25f),
                new WingPoint(0.45f, -0.60f, 0.20f),
                new WingPoint(0.20f, -0.40f, 0.22f),
                new WingPoint(0.08f, -0.15f, 0.30f)
        };

        SHAPES[MECHANICAL] = new WingPoint[]{
                new WingPoint(0.08f, 0.08f, 0.90f),
                new WingPoint(0.20f, 0.25f, 0.82f),
                new WingPoint(0.45f, 0.40f, 0.70f),
                new WingPoint(0.70f, 0.35f, 0.58f),
                new WingPoint(0.95f, 0.25f, 0.48f),
                new WingPoint(0.90f, 0.00f, 0.40f),
                new WingPoint(1.10f, -0.15f, 0.32f),
                new WingPoint(0.80f, -0.30f, 0.28f),
                new WingPoint(0.55f, -0.20f, 0.25f),
                new WingPoint(0.40f, -0.45f, 0.20f),
                new WingPoint(0.15f, -0.30f, 0.22f),
                new WingPoint(0.05f, -0.10f, 0.28f)
        };

        SHAPES[FAIRY] = new WingPoint[]{
                new WingPoint(0.10f, 0.12f, 0.90f),
                new WingPoint(0.25f, 0.38f, 0.82f),
                new WingPoint(0.42f, 0.65f, 0.68f),
                new WingPoint(0.55f, 0.70f, 0.58f),
                new WingPoint(0.60f, 0.45f, 0.50f),
                new WingPoint(0.50f, 0.15f, 0.42f),
                new WingPoint(0.38f, -0.10f, 0.35f),
                new WingPoint(0.30f, -0.35f, 0.28f),
                new WingPoint(0.18f, -0.45f, 0.22f),
                new WingPoint(0.08f, -0.25f, 0.26f)
        };

        SHAPES[DEMON] = new WingPoint[]{
                new WingPoint(0.10f, 0.12f, 0.88f),
                new WingPoint(0.25f, 0.38f, 0.80f),
                new WingPoint(0.55f, 0.65f, 0.65f),
                new WingPoint(0.85f, 0.50f, 0.52f),
                new WingPoint(1.15f, 0.55f, 0.40f),
                new WingPoint(1.25f, 0.20f, 0.32f),
                new WingPoint(1.10f, -0.10f, 0.28f),
                new WingPoint(1.30f, -0.45f, 0.22f),
                new WingPoint(1.15f, -0.70f, 0.18f),
                new WingPoint(0.85f, -0.55f, 0.16f),
                new WingPoint(0.55f, -0.85f, 0.14f),
                new WingPoint(0.25f, -0.65f, 0.12f),
                new WingPoint(0.08f, -0.35f, 0.18f)
        };
    }

    private static final int[] RIBS_DEFAULT = {2, 4, 7, 9, 11};
    private static final int[] RIBS_SMALL = {2, 4, 6, 8};

    private final SelectSetting wingType = new SelectSetting("Wing Type", "Type of wings to render.")
            .value("Angelic", "Dragon", "Butterfly", "Phoenix", "Crystal", "Mechanical", "Fairy", "Demon")
            .selected("Angelic");

    private final SelectSetting fillType = new SelectSetting("Fill Type", "Wing fill type.")
            .value("Normal", "Shader")
            .selected("Normal");

    private final MultiSelectSetting targets = new MultiSelectSetting("Targets", "Who can see the wings.")
            .value("Self", "Friends", "Players")
            .selected("Self");

    private final SliderSettings wingScale = new SliderSettings("Scale", "Wing size.")
            .setValue(1.0f).range(0.3f, 3.0f);

    private final SliderSettings height = new SliderSettings("Height", "Wing height on the back.")
            .setValue(1.5f).range(0.8f, 2.5f);

    private final SliderSettings depthOffset = new SliderSettings("Depth", "How far wings stick out from back.")
            .setValue(0.15f).range(0.0f, 0.5f);

    private final BooleanSetting flapping = new BooleanSetting("Flapping", "Enable wing flapping animation.")
            .setValue(true);

    private final SliderSettings flapStrength = new SliderSettings("Flap Strength", "Strength of the flap bend.")
            .setValue(30.0f).range(5.0f, 60.0f);

    private final SliderSettings flapSpeed = new SliderSettings("Flap Speed", "Speed of flapping.")
            .setValue(3.0f).range(0.5f, 8.0f);

    private final BooleanSetting throughWalls = new BooleanSetting("Through Walls", "Render wings through walls.")
            .setValue(false);

    private final ColorSetting wingColor = new ColorSetting("Color", "Wing color.")
            .value(0xFFFFFFFF);

    private float selfBodyYaw;
    private boolean selfBodyYawInitialized;
    private final Map<UUID, Float> prevBodyYaws = new HashMap<>();
    private final Map<UUID, Integer> glidingTicks = new HashMap<>();

    public Wings() {
        super("Wings", "Renders wings on player backs.", ModuleCategory.RENDER);
        instance = this;
        settings(wingType, fillType, targets, wingScale, height, depthOffset,
                flapping, flapStrength, flapSpeed, throughWalls, wingColor);
        flapStrength.visible(() -> flapping.isValue());
        flapSpeed.visible(() -> flapping.isValue());
    }

    public static Wings getInstance() {
        return instance;
    }

    private WingPoint[] getCurrentShape() {
        return switch (wingType.getSelected()) {
            case "Dragon" -> SHAPES[DRAGON];
            case "Butterfly" -> SHAPES[BUTTERFLY];
            case "Phoenix" -> SHAPES[PHOENIX];
            case "Crystal" -> SHAPES[CRYSTAL];
            case "Mechanical" -> SHAPES[MECHANICAL];
            case "Fairy" -> SHAPES[FAIRY];
            case "Demon" -> SHAPES[DEMON];
            default -> SHAPES[ANGELIC];
        };
    }

    private int[] getCurrentRibIndices(WingPoint[] shape) {
        return shape.length <= 10 ? RIBS_SMALL : RIBS_DEFAULT;
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent event) {
        if (mc.world == null || mc.player == null || mc.gameRenderer == null) return;

        float tickDelta = event.getPartialTicks();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        MatrixStack stack = event.getStack();

        if (camera == null) return;

        if (targets.isSelected("Self") && !mc.options.getPerspective().isFirstPerson()
                && mc.player.isAlive() && !hasElytra(mc.player)) {
            try {
                renderWings(stack, mc.player, tickDelta, camera);
            } catch (Exception ignored) {
            }
        }

        if (targets.isSelected("Friends") || targets.isSelected("Players")) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player) continue;
                if (!player.isAlive() || hasElytra(player)) continue;
                if (!shouldRender(player)) continue;
                try {
                    renderWings(stack, player, tickDelta, camera);
                } catch (Exception ignored) {
                }
            }
        }

        VertexConsumerProvider.Immediate provider = mc.getBufferBuilders().getEntityVertexConsumers();
        provider.draw();
    }

    private void renderWings(MatrixStack stack, PlayerEntity player, float tickDelta, Vec3d camera) {
        VertexConsumerProvider.Immediate provider = mc.getBufferBuilders().getEntityVertexConsumers();

        double x = MathHelper.lerp(tickDelta, player.lastX, player.getX()) - camera.x;
        double y = MathHelper.lerp(tickDelta, player.lastY, player.getY()) - camera.y;
        double z = MathHelper.lerp(tickDelta, player.lastZ, player.getZ()) - camera.z;

        float bodyYaw = resolveBodyYaw(player, tickDelta);
        float move = MathHelper.clamp((float) player.getVelocity().horizontalLength() * 10f, 0f, 1f);

        WingPose pose = resolvePose(player, tickDelta);
        if (pose == null) return;

        float flap = 0f;
        if (flapping.isValue()) {
            float speedMul = flapSpeed.getValue() / 3.0f;
            float ampMul = flapStrength.getValue() / 30.0f;
            float effectiveSpeed = pose.flapSpeed * speedMul;
            float effectiveAmp = pose.flapAmplitude * ampMul;
            flap = (float) Math.sin((player.age + tickDelta) * effectiveSpeed) * effectiveAmp;
        }
        float open = (DEFAULT_SPREAD + flap + move * pose.motionSpreadBoost) * pose.openMultiplier;
        float ws = wingScale.getValue() * pose.scaleMultiplier;

        WingPoint[] shape = getCurrentShape();
        int[] ribIndices = getCurrentRibIndices(shape);

        int baseColor = wingColor.getColor();
        int glowColor = interpolateColor(baseColor, 0xFFFFFFFF, 0.28f);
        int coreColor = interpolateColor(baseColor, 0xFFFFFFFF, 0.55f);
        int outlineColor = baseColor;

        boolean depth = !throughWalls.isValue();

        stack.push();
        stack.translate(x, y, z);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));
        if (pose.preTranslateY != 0f || pose.preTranslateZ != 0f)
            stack.translate(0f, pose.preTranslateY, pose.preTranslateZ);
        if (pose.pitchRotation != 0f)
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.pitchRotation));
        if (pose.rollRotation != 0f)
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pose.rollRotation));
        stack.translate(0f, pose.anchorY, pose.anchorZ);
        stack.scale(ws, ws, ws);

        renderWingSide(stack, provider, -1f, open, baseColor, glowColor, coreColor, outlineColor, pose, shape, ribIndices, depth);
        renderWingSide(stack, provider, 1f, open, baseColor, glowColor, coreColor, outlineColor, pose, shape, ribIndices, depth);

        stack.pop();
    }

    private void renderWingSide(MatrixStack stack, VertexConsumerProvider.Immediate provider,
                                float side, float open, int baseColor, int glowColor, int coreColor, int outlineColor,
                                WingPose pose, WingPoint[] shape, int[] ribIndices, boolean depth) {
        stack.push();
        stack.translate(side * pose.sideOffset, pose.sideYOffset, pose.sideZOffset);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * open));
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * pose.sideRoll));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.sidePitch));

        var glowType = depth ? ClientPipelines.WINGS_GLOW_DEPTH : ClientPipelines.WINGS_GLOW;
        drawWingLayer(provider, stack, side, 1.22f, setAlpha(glowColor, (int) (DEFAULT_ALPHA * 0.22f)), setAlpha(glowColor, 0), glowType, shape);
        drawWingLayer(provider, stack, side, 0.84f, setAlpha(coreColor, (int) (DEFAULT_ALPHA * 0.26f)), setAlpha(coreColor, 0), glowType, shape);

        if (fillType.isSelected("Shader")) {
            Matrix4f modelView = new Matrix4f(stack.peek().getPositionMatrix());
            WingsShaderRenderer.begin();
            int rootColor = setAlpha(baseColor, DEFAULT_ALPHA);
            int edgeColor = setAlpha(baseColor, 10);
            for (int i = 0; i < shape.length; i++) {
                WingPoint cur = shape[i];
                WingPoint next = shape[(i + 1) % shape.length];
                WingsShaderRenderer.addVertex(0f, 0f, 0f, rootColor);
                WingsShaderRenderer.addVertex(side * cur.x, cur.y, 0f, applyPointAlpha(edgeColor, cur.alphaMul));
                WingsShaderRenderer.addVertex(side * next.x, next.y, 0f, applyPointAlpha(edgeColor, next.alphaMul));
            }
            WingsShaderRenderer.render(modelView, depth);
        } else {
            var baseType = depth ? ClientPipelines.WINGS_FILLED : ClientPipelines.WINGS_FILLED_NOTHROUGH;
            drawWingLayer(provider, stack, side, 1.0f, setAlpha(baseColor, DEFAULT_ALPHA), setAlpha(baseColor, 10), baseType, shape);
        }

        var outlineType = depth ? ClientPipelines.WINGS_OUTLINE_DEPTH : ClientPipelines.WINGS_OUTLINE;
        drawWingOutline(provider, stack, side, 1.0f, setAlpha(outlineColor, (int) (DEFAULT_ALPHA * 0.62f)), outlineType, shape);

        var ribsType = depth ? ClientPipelines.WINGS_RIBS_DEPTH : ClientPipelines.WINGS_RIBS;
        drawWingRibs(provider, stack, side, 0.96f, setAlpha(glowColor, (int) (DEFAULT_ALPHA * 0.20f)), ribsType, shape, ribIndices);

        stack.pop();
    }

    private void drawWingLayer(VertexConsumerProvider.Immediate provider, MatrixStack stack,
                               float side, float scale, int rootColor, int edgeColor, net.minecraft.client.render.RenderLayer renderType, WingPoint[] shape) {
        VertexConsumer consumer = provider.getBuffer(renderType);
        Matrix4f matrix = stack.peek().getPositionMatrix();
        for (int i = 0; i < shape.length; i++) {
            WingPoint cur = shape[i];
            WingPoint next = shape[(i + 1) % shape.length];
            consumer.vertex(matrix, 0f, 0f, 0f).color(rootColor);
            consumer.vertex(matrix, side * cur.x * scale, cur.y * scale, 0f).color(applyPointAlpha(edgeColor, cur.alphaMul));
            consumer.vertex(matrix, side * next.x * scale, next.y * scale, 0f).color(applyPointAlpha(edgeColor, next.alphaMul));
        }
    }

    private void drawWingOutline(VertexConsumerProvider.Immediate provider, MatrixStack stack,
                                 float side, float scale, int color, net.minecraft.client.render.RenderLayer renderType, WingPoint[] shape) {
        VertexConsumer consumer = provider.getBuffer(renderType);
        Matrix4f matrix = stack.peek().getPositionMatrix();
        for (WingPoint point : shape) {
            consumer.vertex(matrix, side * point.x * scale, point.y * scale, 0f).color(color);
        }
        consumer.vertex(matrix, side * shape[0].x * scale, shape[0].y * scale, 0f).color(color);
    }

    private void drawWingRibs(VertexConsumerProvider.Immediate provider, MatrixStack stack,
                              float side, float scale, int color, net.minecraft.client.render.RenderLayer renderType, WingPoint[] shape, int[] ribIndices) {
        VertexConsumer consumer = provider.getBuffer(renderType);
        Matrix4f matrix = stack.peek().getPositionMatrix();
        for (int idx : ribIndices) {
            if (idx >= shape.length) continue;
            WingPoint point = shape[idx];
            consumer.vertex(matrix, 0f, 0f, 0f).color(setAlpha(color, Math.max(8, (int) (alpha(color) * 0.75f))));
            consumer.vertex(matrix, side * point.x * scale, point.y * scale, 0f).color(applyPointAlpha(color, point.alphaMul));
        }
    }

    private int applyPointAlpha(int color, float multiplier) {
        return setAlpha(color, Math.max(0, Math.min(255, (int) (alpha(color) * multiplier))));
    }

    private static int setAlpha(int color, int a) {
        return (MathHelper.clamp(a, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static int alpha(int color) {
        return (color >> 24) & 0xFF;
    }

    private static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    private static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    private static int getColor(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int interpolateColor(int color1, int color2, float t) {
        int r1 = red(color1), g1 = green(color1), b1 = blue(color1), a1 = alpha(color1);
        int r2 = red(color2), g2 = green(color2), b2 = blue(color2), a2 = alpha(color2);
        return getColor(
                (int) (r1 + (r2 - r1) * t),
                (int) (g1 + (g2 - g1) * t),
                (int) (b1 + (b2 - b1) * t),
                (int) (a1 + (a2 - a1) * t)
        );
    }

    private boolean shouldRender(PlayerEntity player) {
        if (FriendUtils.isFriend(player)) return targets.isSelected("Friends");
        return targets.isSelected("Players");
    }

    private boolean hasElytra(PlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private float resolveBodyYaw(PlayerEntity player, float tickDelta) {
        float target = MathHelper.lerpAngleDegrees(tickDelta, prevBodyYaws.getOrDefault(player.getUuid(), player.bodyYaw), player.bodyYaw);
        if (player != mc.player) {
            prevBodyYaws.put(player.getUuid(), player.bodyYaw);
            return target;
        }
        if (!selfBodyYawInitialized || player.age < 2) {
            selfBodyYaw = target;
            selfBodyYawInitialized = true;
            prevBodyYaws.put(player.getUuid(), player.bodyYaw);
            return selfBodyYaw;
        }
        selfBodyYaw = approachDegrees(selfBodyYaw, target, 14f);
        prevBodyYaws.put(player.getUuid(), player.bodyYaw);
        return selfBodyYaw;
    }

    private static float approachDegrees(float current, float target, float maxDelta) {
        float delta = MathHelper.wrapDegrees(target - current);
        delta = MathHelper.clamp(delta, -maxDelta, maxDelta);
        return current + delta;
    }

    private WingPose resolvePose(PlayerEntity player, float tickDelta) {
        float pitch = MathHelper.lerp(tickDelta, player.lastPitch, player.getPitch());

        if (player.isGliding()) {
            int gliding = glidingTicks.computeIfAbsent(player.getUuid(), k -> 0);
            float flightTicks = gliding + tickDelta;
            float flightProgress = MathHelper.clamp(flightTicks * flightTicks / 100f, 0f, 1f);
            float pitchRotation = flightProgress * (-90f - pitch);
            glidingTicks.put(player.getUuid(), gliding + 1);
            return new WingPose(0.34f, 0.46f, 0f, 0f, pitchRotation, 0f,
                    0.76f, 0.92f, 0.10f, 0.58f, 0.05f, 0.06f, -5f, -2f, 0.13f);
        } else {
            glidingTicks.remove(player.getUuid());
        }

        if (player.isTouchingWater()) return null;

        if (player.isSneaking()) {
            return new WingPose(0f, 0f, 0.96f, 0.10f, 18f, 0f,
                    1f, 1f, 0.18f, 4.5f, 0.06f, 0.02f, -11f, -4f, 0.12f);
        }

        return new WingPose(0f, 0f, 1.38f, 0.10f, 0f, 0f,
                1f, 1f, 0.18f, 4.5f, 0.06f, 0.02f, -11f, -4f, 0.12f);
    }

    @Override
    public void deactivate() {
        selfBodyYawInitialized = false;
        prevBodyYaws.clear();
        glidingTicks.clear();
        super.deactivate();
    }

    private static final class WingPoint {
        final float x, y, alphaMul;

        WingPoint(float x, float y, float alphaMul) {
            this.x = x;
            this.y = y;
            this.alphaMul = alphaMul;
        }
    }

    private static final class WingPose {
        final float preTranslateY, preTranslateZ;
        final float anchorY, anchorZ;
        final float pitchRotation, rollRotation;
        final float openMultiplier, scaleMultiplier;
        final float motionSpreadBoost, flapAmplitude;
        final float sideOffset, sideYOffset, sideZOffset;
        final float sideRoll, sidePitch, flapSpeed;

        WingPose(float preTranslateY, float preTranslateZ, float anchorY, float anchorZ,
                 float pitchRotation, float rollRotation, float openMultiplier, float scaleMultiplier,
                 float motionSpreadBoost, float flapAmplitude, float sideOffset, float sideZOffset,
                 float sideRoll, float sidePitch, float flapSpeed) {
            this(preTranslateY, preTranslateZ, anchorY, anchorZ, pitchRotation, rollRotation,
                    openMultiplier, scaleMultiplier, motionSpreadBoost, flapAmplitude,
                    sideOffset, 0f, sideZOffset, sideRoll, sidePitch, flapSpeed);
        }

        WingPose(float preTranslateY, float preTranslateZ, float anchorY, float anchorZ,
                 float pitchRotation, float rollRotation, float openMultiplier, float scaleMultiplier,
                 float motionSpreadBoost, float flapAmplitude, float sideOffset, float sideYOffset,
                 float sideZOffset, float sideRoll, float sidePitch, float flapSpeed) {
            this.preTranslateY = preTranslateY;
            this.preTranslateZ = preTranslateZ;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.pitchRotation = pitchRotation;
            this.rollRotation = rollRotation;
            this.openMultiplier = openMultiplier;
            this.scaleMultiplier = scaleMultiplier;
            this.motionSpreadBoost = motionSpreadBoost;
            this.flapAmplitude = flapAmplitude;
            this.sideOffset = sideOffset;
            this.sideYOffset = sideYOffset;
            this.sideZOffset = sideZOffset;
            this.sideRoll = sideRoll;
            this.sidePitch = sidePitch;
            this.flapSpeed = flapSpeed;
        }
    }
}
