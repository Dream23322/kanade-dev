package com.instrumentalist.krs.hacks;

public enum ModuleCategory {
    Combat,
    Movement,
    Player,
    Level,
    Exploit,
    Render,
    Dev;

    public static final ModuleCategory[] DISPLAY_ORDER = {
            Combat, Player, Movement, Render, Exploit, Level, Dev
    };

    public String label() {
        return switch (this) {
            case Render -> "Visual";
            case Exploit -> "Exploits";
            case Level -> "World";
            default -> name();
        };
    }
}
