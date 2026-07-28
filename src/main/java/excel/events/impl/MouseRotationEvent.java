package excel.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import excel.events.api.events.callables.EventCancellable;

@Getter
@Setter
@AllArgsConstructor
public class MouseRotationEvent extends EventCancellable {
    float cursorDeltaX, cursorDeltaY;
}
