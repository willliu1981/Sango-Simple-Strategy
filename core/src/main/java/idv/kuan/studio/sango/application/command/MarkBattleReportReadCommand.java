package idv.kuan.studio.sango.application.command;

import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.repository.SaveGameRepository;

/**
 * 將指定戰報標記為已讀並保存。
 */
public final class MarkBattleReportReadCommand {
    private final SaveGameRepository saveGameRepository;

    public MarkBattleReportReadCommand(SaveGameRepository saveGameRepository) {
        this.saveGameRepository = saveGameRepository;
    }

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
            saveGameRepository.save(slotNumber, nextState);
        }
        return nextState;
    }
}
