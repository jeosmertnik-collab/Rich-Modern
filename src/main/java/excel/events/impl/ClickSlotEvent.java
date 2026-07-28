package excel.events.impl;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.minecraft.screen.slot.SlotActionType;
import excel.events.api.events.callables.EventCancellable;

@Getter @Setter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClickSlotEvent extends EventCancellable {
    int windowId, slotId, button;
    SlotActionType actionType;
}
