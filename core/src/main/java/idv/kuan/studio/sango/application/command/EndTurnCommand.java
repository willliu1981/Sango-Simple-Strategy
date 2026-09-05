package idv.kuan.studio.sango.application.command;

import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.repository.SaveGameRepository;

/**
 * 推進一個月份並恢復行動力。第一版尚未加入 AI 或被動資源結算。
 */
public final class EndTurnCommand {
    private final SaveGameRepository saveGameRepository;

    public EndTurnCommand(SaveGameRepository saveGameRepository) {
        this.saveGameRepository = saveGameRepository;
    }

    public GameState execute(int slotNumber, GameState currentState) {
        GameStateValidator.validate(currentState);

        GameState nextState = currentState.copy();
        nextState.currentTurn += 1;
        nextState.currentMonth += 1;
        if (nextState.currentMonth > 12) {
            nextState.currentMonth = 1;
            nextState.currentYear += 1;
        }
        nextState.actionPointsRemaining = nextState.actionPointsPerTurn;
        nextState.lastActionCode = "END_TURN";

        GameStateValidator.validate(nextState);
        saveGameRepository.save(slotNumber, nextState);
        return nextState;
    }
}
