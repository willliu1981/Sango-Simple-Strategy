package idv.kuan.studio.sango.ui.flow;

import idv.kuan.studio.sango.application.result.TurnResolutionResult;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.runtime.SangoServices;

/**
 * 地圖與內政畫面共用的月底結算入口。
 */
public final class MonthEndFlowController {
    public TurnResolutionResult endCurrentMonth() {
        GameState currentState = SangoServices.session().requireCurrentState();
        TurnResolutionResult resolutionResult = SangoServices.endTurnCommand().execute(
            SangoServices.session().getCurrentSaveSlot(),
            currentState
        );
        SangoServices.session().setCurrentState(
            SangoServices.session().getCurrentSaveSlot(),
            resolutionResult.getGameState()
        );
        SangoServices.session().setLastTurnReport(resolutionResult.getReport());
        return resolutionResult;
    }
}
