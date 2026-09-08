package idv.kuan.studio.sango.application.command;

import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;

/**
 * 將指定戰報在當前戰局標記為已讀。
 */
public final class MarkBattleReportReadCommand {
    public GameState execute(int slotNumber, GameState currentState, String battleReportId) {
        GameStateValidator.validate(currentState);
        if (battleReportId == null || battleReportId.trim().isEmpty()) {
            throw new IllegalArgumentException("battleReportId 不可為空。");
        }

        GameState nextState = currentState.copy();
        BattleReport battleReport = nextState.requireBattleReport(battleReportId);
        if (!battleReport.read) {
            battleReport.read = true;
            nextState.lastActionCode = "READ_BATTLE_REPORT";
            GameStateValidator.validate(nextState);
        }
        return nextState;
    }
}
