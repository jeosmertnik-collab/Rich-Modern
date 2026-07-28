package excel.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.CrossbowItem;
import net.minecraft.util.Hand;
import excel.events.api.EventHandler;
import excel.events.impl.HandOffsetEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.SliderSettings;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ViewModel extends ModuleStructure {

    SliderSettings mainHandXSetting = new SliderSettings("Основная рука X", "Настройка значения X для основной руки")
            .setValue(0.0F).range(-1.0F, 1.0F);

    SliderSettings mainHandYSetting = new SliderSettings("Основная рука Y", "Настройка значения Y для основной руки")
            .setValue(0.0F).range(-1.0F, 1.0F);

    SliderSettings mainHandZSetting = new SliderSettings("Основная рука Z", "Настройка значения Z для основной руки")
            .setValue(0.0F).range(-2.5F, 2.5F);

    SliderSettings offHandXSetting = new SliderSettings("Второстепенная рука X", "Настройка значения X для второстепенной руки")
            .setValue(0.0F).range(-1.0F, 1.0F);

    SliderSettings offHandYSetting = new SliderSettings("Второстепенная рука Y", "Настройка значения Y для второстепенной руки")
            .setValue(0.0F).range(-1.0F, 1.0F);

    SliderSettings offHandZSetting = new SliderSettings("Второстепенная рука Z", "Настройка значения Z для второстепенной руки")
            .setValue(0.0F).range(-2.5F, 2.5F);

    SliderSettings mainHandScale = new SliderSettings("Основная рука размер", "Масштаб основной руки")
            .setValue(1.0F).range(0.1F, 3.0F);

    SliderSettings offHandScale = new SliderSettings("Второстепенная рука размер", "Масштаб второстепенной руки")
            .setValue(1.0F).range(0.1F, 3.0F);

    SliderSettings mainHandRotateX = new SliderSettings("Основная рука вращение X", "Вращение основной руки по X")
            .setValue(0.0F).range(-180.0F, 180.0F);

    SliderSettings mainHandRotateY = new SliderSettings("Основная рука вращение Y", "Вращение основной руки по Y")
            .setValue(0.0F).range(-180.0F, 180.0F);

    SliderSettings mainHandRotateZ = new SliderSettings("Основная рука вращение Z", "Вращение основной руки по Z")
            .setValue(0.0F).range(-180.0F, 180.0F);

    SliderSettings offHandRotateX = new SliderSettings("Второстепенная рука вращение X", "Вращение второстепенной руки по X")
            .setValue(0.0F).range(-180.0F, 180.0F);

    SliderSettings offHandRotateY = new SliderSettings("Второстепенная рука вращение Y", "Вращение второстепенной руки по Y")
            .setValue(0.0F).range(-180.0F, 180.0F);

    SliderSettings offHandRotateZ = new SliderSettings("Второстепенная рука вращение Z", "Вращение второстепенной руки по Z")
            .setValue(0.0F).range(-180.0F, 180.0F);

    public ViewModel() {
        super("ViewModel", "Изменение модели оружия в руке", ModuleCategory.RENDER);
        settings(mainHandXSetting, mainHandYSetting, mainHandZSetting,
                mainHandScale, mainHandRotateX, mainHandRotateY, mainHandRotateZ,
                offHandXSetting, offHandYSetting, offHandZSetting,
                offHandScale, offHandRotateX, offHandRotateY, offHandRotateZ);
    }

    @EventHandler
    public void onHandOffset(HandOffsetEvent e) {
        Hand hand = e.getHand();
        if (hand.equals(Hand.MAIN_HAND) && e.getStack().getItem() instanceof CrossbowItem) return;

        MatrixStack matrix = e.getMatrices();

        if (hand.equals(Hand.MAIN_HAND)) {
            matrix.translate(mainHandXSetting.getValue(), mainHandYSetting.getValue(), mainHandZSetting.getValue());
            float scale = mainHandScale.getValue();
            matrix.scale(scale, scale, scale);
            if (mainHandRotateX.getValue() != 0) matrix.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(mainHandRotateX.getValue()));
            if (mainHandRotateY.getValue() != 0) matrix.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(mainHandRotateY.getValue()));
            if (mainHandRotateZ.getValue() != 0) matrix.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(mainHandRotateZ.getValue()));
        } else {
            matrix.translate(offHandXSetting.getValue(), offHandYSetting.getValue(), offHandZSetting.getValue());
            float scale = offHandScale.getValue();
            matrix.scale(scale, scale, scale);
            if (offHandRotateX.getValue() != 0) matrix.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(offHandRotateX.getValue()));
            if (offHandRotateY.getValue() != 0) matrix.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(offHandRotateY.getValue()));
            if (offHandRotateZ.getValue() != 0) matrix.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(offHandRotateZ.getValue()));
        }
    }
}