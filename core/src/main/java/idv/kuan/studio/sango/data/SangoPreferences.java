package idv.kuan.studio.sango.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/**
 * 第一階段只保存 Lobby 與流程驗證所需的輕量設定。
 * 正式 SaveGame 建立後，戰局資料應移出 Preferences。
 */
public final class SangoPreferences {
    private static final String PREFERENCES_NAME = "sango-local-settings";
    private static final String KEY_PROTOTYPE_CAMPAIGN_EXISTS = "prototypeCampaignExists";
    private static final String KEY_MUSIC_ENABLED = "musicEnabled";
    private static final String KEY_SOUND_ENABLED = "soundEnabled";

    private SangoPreferences() {
    }

    public static boolean hasPrototypeCampaign() {
        return preferences().getBoolean(KEY_PROTOTYPE_CAMPAIGN_EXISTS, false);
    }

    public static void setPrototypeCampaignExists(boolean exists) {
        Preferences preferences = preferences();
        preferences.putBoolean(KEY_PROTOTYPE_CAMPAIGN_EXISTS, exists);
        preferences.flush();
    }

    public static boolean isMusicEnabled() {
        return preferences().getBoolean(KEY_MUSIC_ENABLED, true);
    }

    public static void setMusicEnabled(boolean enabled) {
        Preferences preferences = preferences();
        preferences.putBoolean(KEY_MUSIC_ENABLED, enabled);
        preferences.flush();
    }

    public static boolean isSoundEnabled() {
        return preferences().getBoolean(KEY_SOUND_ENABLED, true);
    }

    public static void setSoundEnabled(boolean enabled) {
        Preferences preferences = preferences();
        preferences.putBoolean(KEY_SOUND_ENABLED, enabled);
        preferences.flush();
    }

    private static Preferences preferences() {
        return Gdx.app.getPreferences(PREFERENCES_NAME);
    }
}
