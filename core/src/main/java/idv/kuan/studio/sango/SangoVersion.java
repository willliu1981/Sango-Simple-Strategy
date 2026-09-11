package idv.kuan.studio.sango;

/**
 * 集中管理程式與存檔格式版本。
 */
public final class SangoVersion {
    public static final String GAME_VERSION = "0.6.2";
    public static final int SAVE_DOCUMENT_SCHEMA_VERSION = 2;
    public static final int GAME_STATE_SCHEMA_VERSION = 14;
    public static final int LEGACY_GAME_STATE_SCHEMA_VERSION = 2;

    private SangoVersion() {
    }
}
