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
    private static final String KEY_MUSIC_VOLUME = "musicVolume";
    private static final String KEY_SOUND_VOLUME = "soundVolume";
    private static final String KEY_LAST_USED_SAVE_SLOT = "lastUsedSaveSlot";
    private static final float DEFAULT_MUSIC_VOLUME = 0.55f;
    private static final float DEFAULT_SOUND_VOLUME = 0.75f;

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

    public static float getMusicVolume() {
        return clampVolume(preferences().getFloat(KEY_MUSIC_VOLUME, DEFAULT_MUSIC_VOLUME));
    }

    public static void setMusicVolume(float volume) {
        Preferences preferences = preferences();
        preferences.putFloat(KEY_MUSIC_VOLUME, clampVolume(volume));
        preferences.flush();
    }

    public static float getSoundVolume() {
        return clampVolume(preferences().getFloat(KEY_SOUND_VOLUME, DEFAULT_SOUND_VOLUME));
    }

    public static void setSoundVolume(float volume) {
        Preferences preferences = preferences();
        preferences.putFloat(KEY_SOUND_VOLUME, clampVolume(volume));
        preferences.flush();
    }

    public static int getLastUsedSaveSlot() {
        int slotNumber = preferences().getInteger(KEY_LAST_USED_SAVE_SLOT, 1);
        return Math.max(1, Math.min(3, slotNumber));
    }

    public static void setLastUsedSaveSlot(int slotNumber) {
        if (slotNumber < 1 || slotNumber > 3) {
            throw new IllegalArgumentException("slotNumber 必須介於 1 到 3。");
        }
        Preferences preferences = preferences();
        preferences.putInteger(KEY_LAST_USED_SAVE_SLOT, slotNumber);
        preferences.flush();
    }

    private static float clampVolume(float volume) {
        return Math.max(0f, Math.min(1f, volume));
    }

    private static Preferences preferences() {
        if (Gdx.app == null) {
            throw new IllegalStateException("LibGDX 尚未初始化，無法存取偏好設定。");
        }
        return Gdx.app.getPreferences(PREFERENCES_NAME);
    }
}
