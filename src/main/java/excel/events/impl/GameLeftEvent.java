package excel.events.impl;

import excel.events.api.events.Event;

public class GameLeftEvent implements Event {
    private static final GameLeftEvent INSTANCE = new GameLeftEvent();

    public static GameLeftEvent get() {
        return INSTANCE;
    }
}