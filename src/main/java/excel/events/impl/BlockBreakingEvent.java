package excel.events.impl;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import excel.events.api.events.Event;

public record BlockBreakingEvent(BlockPos blockPos, Direction direction) implements Event {}
