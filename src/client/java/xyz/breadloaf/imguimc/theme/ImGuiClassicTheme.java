package xyz.breadloaf.imguimc.theme;

import xyz.breadloaf.imguimc.interfaces.Theme;

public class ImGuiClassicTheme implements Theme {
    @Override
    public void preRender() {
        KanadeImGuiTheme.applyClassic();
    }

    @Override
    public void postRender() {

    }
}
