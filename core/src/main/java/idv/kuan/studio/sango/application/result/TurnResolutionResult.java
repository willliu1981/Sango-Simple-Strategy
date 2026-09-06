package idv.kuan.studio.sango.application.result;

import idv.kuan.studio.sango.domain.model.GameState;

/**
 * 結束月份後，包含已保存的新狀態與結算事件。
 */
public final class TurnResolutionResult {
    private final GameState gameState;
    private final TurnResolutionReport report;

    public TurnResolutionResult(GameState gameState, TurnResolutionReport report) {
        if (gameState == null || report == null) {
            throw new IllegalArgumentException("gameState 與 report 不可為 null。");
        }
        this.gameState = gameState;
        this.report = report;
    }

    public GameState getGameState() {
        return gameState;
    }

    public TurnResolutionReport getReport() {
        return report;
    }
}
