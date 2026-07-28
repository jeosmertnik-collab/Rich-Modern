package excel.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
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
import excel.modules.module.setting.implement.ColorSetting;
import excel.modules.module.setting.implement.SelectSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.render.сliemtpipeline.ClientPipelines;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class BackSword extends ModuleStructure implements IMinecraft {

    private static BackSword instance;

    private final SelectSetting target = new SelectSetting("Target", "Who can see the back sword.")
            .value("Self", "All Players", "Self and Others").selected("Self");

    private final BooleanSetting glow = new BooleanSetting("Glow", "Enable glow effect.").setValue(true);

    private final SliderSettings glowLevel = new SliderSettings("Glow Level", "Glow intensity.")
            .setValue(50f).range(0f, 100f);

    private final SliderSettings fillAlpha = new SliderSettings("Fill Alpha", "Fill transparency.")
            .setValue(20f).range(0f, 100f);

    private final SliderSettings outlineAlpha = new SliderSettings("Outline Alpha", "Outline transparency.")
            .setValue(85f).range(0f, 100f);

    private final SliderSettings posY = new SliderSettings("Offset Y", "Vertical position on the back.")
            .setValue(0.9f).range(-0.5f, 1.5f);

    private final SliderSettings posZ = new SliderSettings("Offset Z", "How far behind the player.")
            .setValue(0.3f).range(0f, 0.8f);

    private final ColorSetting color = new ColorSetting("Color", "Sword color.").value(0xFF6366F1);

    private float selfBodyYaw;
    private boolean selfBodyYawInitialized;
    private final Map<UUID, Float> prevBodyYaws = new HashMap<>();

    public BackSword() {
        super("BackSword", "Renders a 3D sword on the player's back.", ModuleCategory.RENDER);
        instance = this;
        settings(target, glow, glowLevel, fillAlpha, outlineAlpha, posY, posZ, color);
        glowLevel.visible(() -> glow.isValue());
    }

    public static BackSword getInstance() {
        return instance;
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent event) {
        if (mc.world == null || mc.player == null || mc.gameRenderer == null) return;

        float tickDelta = event.getPartialTicks();
        MatrixStack stack = event.getStack();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        VertexConsumerProvider.Immediate provider = mc.getBufferBuilders().getEntityVertexConsumers();

        boolean firstPerson = mc.options.getPerspective().isFirstPerson();

        if (!firstPerson && mc.player.isAlive()) {
            boolean showSelf = target.isSelected("Self") || target.isSelected("Self and Others");
            if (showSelf) {
                renderSword(stack, provider, mc.player, tickDelta, camera);
            }
        }

        if (target.isSelected("All Players") || target.isSelected("Self and Others")) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player) continue;
                if (!player.isAlive()) continue;
                renderSword(stack, provider, player, tickDelta, camera);
            }
        }

        provider.draw();
    }

    private void renderSword(MatrixStack stack, VertexConsumerProvider.Immediate provider,
                             PlayerEntity player, float tickDelta, Vec3d camera) {
        double x = MathHelper.lerp(tickDelta, player.lastX, player.getX()) - camera.x;
        double y = MathHelper.lerp(tickDelta, player.lastY, player.getY()) - camera.y;
        double z = MathHelper.lerp(tickDelta, player.lastZ, player.getZ()) - camera.z;

        float bodyYaw = resolveBodyYaw(player, tickDelta);

        int baseColor = color.getColor();
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        int fillA = (int) (fillAlpha.getValue() / 100f * 255f);
        int outA = (int) (outlineAlpha.getValue() / 100f * 255f);

        stack.push();
        stack.translate(x, y, z);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));
        stack.translate(0f, posY.getValue(), posZ.getValue());
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-15f));
        stack.scale(1.15f, 1.15f, 1.15f);

        Matrix4f matrix = stack.peek().getPositionMatrix();

        VertexConsumer fill = provider.getBuffer(ClientPipelines.BACKSWORD_FILL);
        drawBox(fill, matrix, -0.042f, -0.48f, -0.024f, 0.042f, -0.42f, 0.024f, r, g, b, fillA);
        drawBox(fill, matrix, -0.032f, -0.42f, -0.018f, 0.032f, 0.0f, 0.018f, r, g, b, fillA);
        drawBox(fill, matrix, -0.085f, 0.0f, -0.034f, 0.085f, 0.05f, 0.034f, r, g, b, fillA);
        drawBox(fill, matrix, -0.022f, 0.05f, -0.010f, 0.022f, 0.96f, 0.010f, r, g, b, fillA);
        drawBox(fill, matrix, -0.012f, 0.96f, -0.007f, 0.012f, 1.07f, 0.007f, r, g, b, fillA);

        int br = Math.min(r + 40, 255);
        int bg = Math.min(g + 40, 255);
        int bb = Math.min(b + 40, 255);
        float e = 0.004f;
        drawBoxEdge(fill, matrix, -0.042f, -0.48f, -0.024f, 0.042f, -0.42f, 0.024f, br, bg, bb, outA, e);
        drawBoxEdge(fill, matrix, -0.032f, -0.42f, -0.018f, 0.032f, 0.0f, 0.018f, br, bg, bb, outA, e);
        drawBoxEdge(fill, matrix, -0.085f, 0.0f, -0.034f, 0.085f, 0.05f, 0.034f, br, bg, bb, outA, e);
        drawBoxEdge(fill, matrix, -0.022f, 0.05f, -0.010f, 0.022f, 0.96f, 0.010f, br, bg, bb, outA, e);
        drawBoxEdge(fill, matrix, -0.012f, 0.96f, -0.007f, 0.012f, 1.07f, 0.007f, br, bg, bb, outA, e);

        if (glow.isValue()) {
            VertexConsumer glowCon = provider.getBuffer(ClientPipelines.BACKSWORD_GLOW);
            float glowBase = glowLevel.getValue() / 100f;
            float pulse = (float) (Math.sin(System.currentTimeMillis() / 700.0) * 0.12 + 0.88);
            glowBase *= pulse;
            int layers = 12;
            float maxEx = 0.045f;
            for (int i = 0; i < layers; i++) {
                float t = (float) i / (layers - 1);
                float falloff = (1f - t) * (1f - t);
                int ga = (int) (glowBase * falloff * 180f);
                if (ga <= 1) continue;
                float ex = t * maxEx;
                drawBox(glowCon, matrix, -0.042f - ex, -0.48f - ex, -0.024f - ex, 0.042f + ex, -0.42f + ex, 0.024f + ex, r, g, b, ga);
                drawBox(glowCon, matrix, -0.032f - ex, -0.42f - ex, -0.018f - ex, 0.032f + ex, 0.0f + ex, 0.018f + ex, r, g, b, ga);
                drawBox(glowCon, matrix, -0.085f - ex, 0.0f - ex, -0.034f - ex, 0.085f + ex, 0.05f + ex, 0.034f + ex, r, g, b, ga);
                drawBox(glowCon, matrix, -0.022f - ex, 0.05f - ex, -0.010f - ex, 0.022f + ex, 0.96f + ex, 0.010f + ex, r, g, b, ga);
                drawBox(glowCon, matrix, -0.012f - ex, 0.96f - ex, -0.007f - ex, 0.012f + ex, 1.07f + ex, 0.007f + ex, r, g, b, ga);
            }
        }

        stack.pop();
    }

    private static int argb(int r, int g, int b, int a) {
        return (MathHelper.clamp(a, 0, 255) << 24) | (MathHelper.clamp(r, 0, 255) << 16) | (MathHelper.clamp(g, 0, 255) << 8) | MathHelper.clamp(b, 0, 255);
    }

    private static void drawBox(VertexConsumer consumer, Matrix4f matrix,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                int r, int g, int b, int a) {
        if (a <= 0) return;
        int color = argb(r, g, b, a);
        consumer.vertex(matrix, x0, y0, z0).color(color);
        consumer.vertex(matrix, x1, y0, z0).color(color);
        consumer.vertex(matrix, x1, y0, z1).color(color);
        consumer.vertex(matrix, x0, y0, z1).color(color);

        consumer.vertex(matrix, x0, y1, z0).color(color);
        consumer.vertex(matrix, x0, y1, z1).color(color);
        consumer.vertex(matrix, x1, y1, z1).color(color);
        consumer.vertex(matrix, x1, y1, z0).color(color);

        consumer.vertex(matrix, x0, y0, z0).color(color);
        consumer.vertex(matrix, x0, y1, z0).color(color);
        consumer.vertex(matrix, x1, y1, z0).color(color);
        consumer.vertex(matrix, x1, y0, z0).color(color);

        consumer.vertex(matrix, x0, y0, z1).color(color);
        consumer.vertex(matrix, x1, y0, z1).color(color);
        consumer.vertex(matrix, x1, y1, z1).color(color);
        consumer.vertex(matrix, x0, y1, z1).color(color);

        consumer.vertex(matrix, x0, y0, z0).color(color);
        consumer.vertex(matrix, x0, y0, z1).color(color);
        consumer.vertex(matrix, x0, y1, z1).color(color);
        consumer.vertex(matrix, x0, y1, z0).color(color);

        consumer.vertex(matrix, x1, y0, z0).color(color);
        consumer.vertex(matrix, x1, y1, z0).color(color);
        consumer.vertex(matrix, x1, y1, z1).color(color);
        consumer.vertex(matrix, x1, y0, z1).color(color);
    }

    private static void drawBoxEdge(VertexConsumer consumer, Matrix4f matrix,
                                    float x0, float y0, float z0, float x1, float y1, float z1,
                                    int r, int g, int b, int a, float edge) {
        drawBox(consumer, matrix, x0 - edge, y0 - edge, z0 - edge, x1 + edge, y0 + edge, z0 + edge, r, g, b, a);
        drawBox(consumer, matrix, x0 - edge, y0 - edge, z1 - edge, x1 + edge, y0 + edge, z1 + edge, r, g, b, a);
        drawBox(consumer, matrix, x0 - edge, y0 - edge, z0 - edge, x0 + edge, y0 + edge, z1 + edge, r, g, b, a);
        drawBox(consumer, matrix, x1 - edge, y0 - edge, z0 - edge, x1 + edge, y0 + edge, z1 + edge, r, g, b, a);

        drawBox(consumer, matrix, x0 - edge, y1 - edge, z0 - edge, x1 + edge, y1 + edge, z0 + edge, r, g, b, a);
        drawBox(consumer, matrix, x0 - edge, y1 - edge, z1 - edge, x1 + edge, y1 + edge, z1 + edge, r, g, b, a);
        drawBox(consumer, matrix, x0 - edge, y1 - edge, z0 - edge, x0 + edge, y1 + edge, z1 + edge, r, g, b, a);
        drawBox(consumer, matrix, x1 - edge, y1 - edge, z0 - edge, x1 + edge, y1 + edge, z1 + edge, r, g, b, a);

        drawBox(consumer, matrix, x0 - edge, y0 - edge, z0 - edge, x0 + edge, y1 + edge, z0 + edge, r, g, b, a);
        drawBox(consumer, matrix, x1 - edge, y0 - edge, z0 - edge, x1 + edge, y1 + edge, z0 + edge, r, g, b, a);
        drawBox(consumer, matrix, x0 - edge, y0 - edge, z1 - edge, x0 + edge, y1 + edge, z1 + edge, r, g, b, a);
        drawBox(consumer, matrix, x1 - edge, y0 - edge, z1 - edge, x1 + edge, y1 + edge, z1 + edge, r, g, b, a);
    }

    private float resolveBodyYaw(PlayerEntity player, float tickDelta) {
        float currentYaw = player.bodyYaw;

        if (player != mc.player) {
            UUID id = player.getUuid();
            float prev = prevBodyYaws.getOrDefault(id, currentYaw);
            float interpolated = MathHelper.lerpAngleDegrees(tickDelta, prev, currentYaw);
            prevBodyYaws.put(id, currentYaw);
            return interpolated;
        }

        if (!selfBodyYawInitialized || player.age < 2) {
            selfBodyYaw = currentYaw;
            selfBodyYawInitialized = true;
            return selfBodyYaw;
        }
        selfBodyYaw = approachDegrees(selfBodyYaw, currentYaw, 14f);
        return selfBodyYaw;
    }

    private static float approachDegrees(float current, float target, float maxDelta) {
        float delta = MathHelper.wrapDegrees(target - current);
        delta = MathHelper.clamp(delta, -maxDelta, maxDelta);
        return current + delta;
    }

    @Override
    public void deactivate() {
        selfBodyYawInitialized = false;
        super.deactivate();
    }
}
