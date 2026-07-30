package excel.mixin;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import excel.Initialization;
import excel.client.draggables.Drag;


@Mixin(Screen.class)
public abstract class ScreenDragMixin extends Screen {

    protected ScreenDragMixin(Text title) {
        super(title);
    }

    private boolean richIsDragExcluded() {
        String name = this.getClass().getName().toLowerCase();
        if (name.contains("clickgui")) return true;
        if (name.contains("loading")) return true;
        if (name.contains("progress")) return true;
        if (name.contains("connecting")) return true;
        if (name.contains("terrain")) return true;
        return false;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (richIsDragExcluded()) return;
        Drag.onDraw(context, mouseX, mouseY, deltaTicks);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (richIsDragExcluded()) return;

        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        int button = click.button();

        if (Initialization.getInstance() != null && Initialization.getInstance().getManager() != null
                && Initialization.getInstance().getManager().getHudManager() != null) {
            if (Initialization.getInstance().getManager().getHudManager().mouseClicked(mouseX, mouseY, button)) {
                cir.setReturnValue(true);
                return;
            }
        }

        Drag.onMouseClick(click);
        if (Drag.isDragging()) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (!richIsDragExcluded()) {
            Drag.onMouseRelease(click);
        }
        return super.mouseReleased(click);
    }

    @Override
    public void removed() {
        Drag.resetDragging();
        super.removed();
    }

    @Override
    public void close() {
        Drag.resetDragging();
        super.close();
    }
}