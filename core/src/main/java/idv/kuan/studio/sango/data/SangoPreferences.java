package idv.kuan.studio.sango.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/**
 * 只保存非戰局型的本機介面偏好。
 * 正式 GameState 一律交由 SaveGameRepository 管理。
 */
public final class SangoPreferences {
    private static final String PREFERENCES_NAME = "sango-local-settings";
    private static final String KEY_MUSIC_ENABLED = "musicEnabled";
    private static final String KEY_SOUND_ENABLED = "soundEnabled";

    private SangoPreferences() {
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
