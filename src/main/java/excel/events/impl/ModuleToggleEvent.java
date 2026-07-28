package excel.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import excel.events.api.events.Event;
import excel.modules.module.ModuleStructure;

@Getter
@AllArgsConstructor
public class ModuleToggleEvent implements Event {
    private final ModuleStructure module;
    private final boolean enabled;
}