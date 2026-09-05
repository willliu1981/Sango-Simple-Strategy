package idv.kuan.studio.sango.runtime;

import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;

/**
 * 保存目前記憶體中的戰局。所有變更仍必須先經 Command 並完成磁碟保存。
 */
public final class GameSession {
    private GameState currentState;

    public boolean hasCurrentState() {
        return currentState != null;
    }

    public GameState requireCurrentState() {
        if (currentState == null) {
            throw new IllegalStateException("目前沒有已載入的戰局。");
        }
        return currentState;
    }

    public void setCurrentState(GameState gameState) {
        GameStateValidator.validate(gameState);
        currentState = gameState;
    }

    public void clear() {
        currentState = null;
    }
}
