package excel.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import excel.events.api.events.Event;

@Getter
@AllArgsConstructor
public class RotationUpdateEvent implements Event {
    byte type;
}
