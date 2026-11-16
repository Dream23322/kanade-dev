package xyz.breadloaf.imguimc.theme;

import xyz.breadloaf.imguimc.interfaces.Theme;

public class ImGuiDarkTheme implements Theme {
    @Override
    public void preRender() {
        KanadeImGuiTheme.applyDark();
    }

    @Override
    public void postRender() {

    }
}
