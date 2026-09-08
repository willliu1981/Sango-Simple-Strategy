package idv.kuan.studio.sango.audio;

/**
 * 短促遊戲音效。所有音檔皆為專案內程序合成的原創 Prototype 素材。
 */
public enum SoundEffect {
    UI_CLICK("audio/sfx/ui_click.ogg"),
    CONFIRM("audio/sfx/confirm.ogg"),
    CANCEL("audio/sfx/cancel.ogg"),
    COMMAND_SUCCESS("audio/sfx/command_success.ogg"),
    COMMAND_ERROR("audio/sfx/command_error.ogg"),
    END_MONTH("audio/sfx/end_month.ogg"),
    SAVE_COMPLETE("audio/sfx/save_complete.ogg"),
    BATTLE_ALERT("audio/sfx/battle_alert.ogg"),
    BATTLE_IMPACT("audio/sfx/battle_impact.ogg"),
    CITY_CAPTURED("audio/sfx/city_captured.ogg"),
    OBJECTIVE_SUCCESS("audio/sfx/objective_success.ogg"),
    OBJECTIVE_FAILED("audio/sfx/objective_failed.ogg");

    private final String assetPath;

    SoundEffect(String assetPath) {
        this.assetPath = assetPath;
    }

    public String getAssetPath() {
        return assetPath;
    }
}
