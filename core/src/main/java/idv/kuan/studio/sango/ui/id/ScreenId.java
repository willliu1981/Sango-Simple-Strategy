package idv.kuan.studio.sango.ui.id;

import idv.kuan.studio.libgdx.simpleui.screen.ScreenIdentifier;

/**
 * 集中管理 SimpleUI 畫面識別碼，避免散落字串。
 */
public enum ScreenId implements ScreenIdentifier {
    LOBBY("lobby"),
    NEW_GAME("new_game"),
    STRATEGIC_MAP("strategic_map"),
    CITY("city"),
    MONTH_REPORT("month_report"),
    BATTLE_REPORT("battle_report"),
    SAVE_LOAD("save_load"),
    SETTINGS("settings");

    private final String value;

    ScreenId(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}
