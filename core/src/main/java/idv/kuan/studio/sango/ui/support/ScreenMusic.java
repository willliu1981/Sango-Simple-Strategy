package idv.kuan.studio.sango.ui.support;

import idv.kuan.studio.sango.audio.MusicTrack;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.id.ScreenId;

/** 依返回位置辨別主選單或戰局音樂，不因記憶體中殘留戰局而在 Lobby 播錯歌。 */
public final class ScreenMusic {
    private ScreenMusic() {
    }

    public static MusicTrack resolve(ScreenId screenId) {
        ScreenId context = screenId;
        if (context == ScreenId.SAVE_LOAD) {
            context = SangoServices.session().getSaveLoadReturnScreen();
        }
        if (context == ScreenId.SETTINGS) {
            context = SangoServices.session().getSettingsReturnScreen();
        }
        if (context == ScreenId.LOBBY || context == ScreenId.LOBBY_SETTINGS || context == ScreenId.NEW_GAME
            || !SangoServices.session().hasCurrentState()) {
            return MusicTrack.LOBBY;
        }
        return MusicTrack.forMonth(SangoServices.session().requireCurrentState().currentMonth);
    }

    public static void play(ScreenId screenId) {
        SangoServices.audio().playMusic(resolve(screenId));
    }
}
