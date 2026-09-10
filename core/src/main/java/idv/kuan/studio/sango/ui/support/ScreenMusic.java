package idv.kuan.studio.sango.ui.support;

import idv.kuan.studio.sango.audio.MusicTrack;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.id.ScreenId;

/** 依返回位置辨別主選單或戰局音樂，不因記憶體中殘留戰局而在 Lobby 播錯歌。 */
public final class ScreenMusic {
    private ScreenMusic() {
    }

    public static MusicTrack resolve(ScreenId screenId) {
        if (isVictoryReportContext(screenId)) {
            return MusicTrack.VICTORY;
        }
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

    private static boolean isVictoryReportContext(ScreenId screenId) {
        boolean viewingMonthReport = screenId == ScreenId.MONTH_REPORT;
        boolean viewingBattleFromMonthReport = screenId == ScreenId.BATTLE_REPORT
            && SangoServices.session().getBattleReportReturnScreen() == ScreenId.MONTH_REPORT;
        if (!viewingMonthReport && !viewingBattleFromMonthReport) {
            return false;
        }
        TurnResolutionReport report = SangoServices.session().getLastTurnReport();
        return report != null && report.getEvents().stream()
            .anyMatch(event -> event.getType() == TurnEventType.CAMPAIGN_VICTORY);
    }

    public static void play(ScreenId screenId) {
        SangoServices.audio().playMusic(resolve(screenId));
    }
}
