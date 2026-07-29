package excel.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import excel.util.config.impl.cosmetics.CosmeticsManager;
import excel.util.render.сliemtpipeline.ClientPipelines;

public class MaskFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public MaskFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrixStack, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.options.getPerspective().isFirstPerson()) return;

        CosmeticsManager cm = CosmeticsManager.getInstance();
        if (!cm.isMaskEnabled()) return;
        String maskType = cm.getSelectedMask();

        if (mc.player.getId() != state.id) return;

        matrixStack.push();
        this.getContextModel().head.applyTransform(matrixStack);
        matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f));
        matrixStack.translate(0, -0.1f, -0.25f);

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        renderMaskShape(matrixStack, immediate, maskType);
        immediate.draw();

        matrixStack.pop();
    }

    private void renderMaskShape(MatrixStack stack, VertexConsumerProvider provider, String maskType) {
        VertexConsumer consumer = provider.getBuffer(ClientPipelines.WINGS_FILLED_NOTHROUGH);
        Matrix4f matrix = stack.peek().getPositionMatrix();

        int maskColor = getMaskColor(maskType);
        int alpha = 200;

        float halfW = 0.15f;
        float halfH = 0.18f;
        float depth = 0.02f;

        consumer.vertex(matrix, -halfW, -halfH, depth).color((maskColor & 0x00FFFFFF) | (alpha << 24));
        consumer.vertex(matrix, halfW, -halfH, depth).color((maskColor & 0x00FFFFFF) | (alpha << 24));
        consumer.vertex(matrix, halfW, halfH, depth).color((maskColor & 0x00FFFFFF) | (alpha << 24));
        consumer.vertex(matrix, -halfW, halfH, depth).color((maskColor & 0x00FFFFFF) | (alpha << 24));

        if ("skull".equals(maskType) || "oni".equals(maskType)) {
            int eyeColor = maskType.equals("skull") ? 0x000000 : 0xFFFF4400;
            float eyeW = 0.04f;
            float eyeY = -0.04f;
            consumer.vertex(matrix, -0.08f, eyeY - eyeW, depth + 0.005f).color((eyeColor & 0x00FFFFFF) | (255 << 24));
            consumer.vertex(matrix, -0.02f, eyeY - eyeW, depth + 0.005f).color((eyeColor & 0x00FFFFFF) | (255 << 24));
            consumer.vertex(matrix, -0.02f, eyeY + eyeW, depth + 0.005f).color((eyeColor & 0x00FFFFFF) | (255 << 24));
            consumer.vertex(matrix, -0.08f, eyeY + eyeW, depth + 0.005f).color((eyeColor & 0x00FFFFFF) | (255 << 24));

            consumer.vertex(matrix, 0.02f, eyeY - eyeW, depth + 0.005f).color((eyeColor & 0x00FFFFFF) | (255 << 24));
            consumer.vertex(matrix, 0.08f, eyeY - eyeW, depth + 0.005f).color((eyeColor & 0x00FFFFFF) | (255 << 24));
            consumer.vertex(matrix, 0.08f, eyeY + eyeW, depth + 0.005f).color((eyeColor & 0x00FFFFFF) | (255 << 24));
            consumer.vertex(matrix, 0.02f, eyeY + eyeW, depth + 0.005f).color((eyeColor & 0x00FFFFFF) | (255 << 24));
        }

        if ("cat".equals(maskType)) {
            float earW = 0.05f;
            float earH = 0.06f;
            consumer.vertex(matrix, -0.12f, -0.2f, depth).color((maskColor & 0x00FFFFFF) | (alpha << 24));
            consumer.vertex(matrix, -0.04f, -0.2f, depth).color((maskColor & 0x00FFFFFF) | (alpha << 24));
            consumer.vertex(matrix, -0.08f, -0.28f, depth).color((maskColor & 0x00FFFFFF) | (alpha << 24));

            consumer.vertex(matrix, 0.04f, -0.2f, depth).color((maskColor & 0x00FFFFFF) | (alpha << 24));
            consumer.vertex(matrix, 0.12f, -0.2f, depth).color((maskColor & 0x00FFFFFF) | (alpha << 24));
            consumer.vertex(matrix, 0.08f, -0.28f, depth).color((maskColor & 0x00FFFFFF) | (alpha << 24));
        }

        if ("fox".equals(maskType)) {
            float earW = 0.05f;
            float earH = 0.07f;
            consumer.vertex(matrix, -0.13f, -0.2f, depth).color((0xFFCC8800));
            consumer.vertex(matrix, -0.05f, -0.2f, depth).color((0xFFCC8800));
            consumer.vertex(matrix, -0.09f, -0.30f, depth).color((0xFFCC8800));

            consumer.vertex(matrix, 0.05f, -0.2f, depth).color((0xFFCC8800));
            consumer.vertex(matrix, 0.13f, -0.2f, depth).color((0xFFCC8800));
            consumer.vertex(matrix, 0.09f, -0.30f, depth).color((0xFFCC8800));

            int white = 0xFFFFFFFF;
            consumer.vertex(matrix, -0.10f, -0.18f, depth + 0.003f).color(white);
            consumer.vertex(matrix, -0.03f, -0.18f, depth + 0.003f).color(white);
            consumer.vertex(matrix, -0.065f, -0.26f, depth + 0.003f).color(white);

            consumer.vertex(matrix, 0.03f, -0.18f, depth + 0.003f).color(white);
            consumer.vertex(matrix, 0.10f, -0.18f, depth + 0.003f).color(white);
            consumer.vertex(matrix, 0.065f, -0.26f, depth + 0.003f).color(white);
        }
    }

    private int getMaskColor(String maskType) {
        return switch (maskType) {
            case "clown" -> 0xFFE8E8E8;
            case "ninja" -> 0xFF1a1a2e;
            case "skull" -> 0xFFE0E0E0;
            case "cat" -> 0xFFFFA500;
            case "fox" -> 0xFFCC6600;
            case "gas" -> 0xFF2a2a3a;
            case "bandit" -> 0xFF0a0a0a;
            case "oni" -> 0xFFCC2200;
            default -> 0xFF444466;
        };
    }
}
