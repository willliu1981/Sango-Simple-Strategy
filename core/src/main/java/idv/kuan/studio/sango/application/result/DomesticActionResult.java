package idv.kuan.studio.sango.application.result;

import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.DomesticActionFailureReason;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;

/**
 * 內政命令執行結果。成功時包含已完成保存的新狀態。
 */
public final class DomesticActionResult {
    private final boolean successful;
    private final DomesticActionType actionType;
    private final DomesticActionFailureReason failureReason;
    private final GameState gameState;

    private DomesticActionResult(
        boolean successful,
        DomesticActionType actionType,
        DomesticActionFailureReason failureReason,
        GameState gameState
    ) {
        this.successful = successful;
        this.actionType = actionType;
        this.failureReason = failureReason;
        this.gameState = gameState;
    }

    public static DomesticActionResult success(
        DomesticActionType actionType,
        GameState gameState
    ) {
        return new DomesticActionResult(
            true,
            actionType,
            DomesticActionFailureReason.NONE,
            gameState
        );
    }

    public static DomesticActionResult failure(
        DomesticActionType actionType,
        DomesticActionFailureReason failureReason
    ) {
        return new DomesticActionResult(false, actionType, failureReason, null);
    }

    public boolean isSuccessful() {
        return successful;
    }

    public DomesticActionType getActionType() {
        return actionType;
    }

    public DomesticActionFailureReason getFailureReason() {
        return failureReason;
    }

    public GameState getGameState() {
        return gameState;
    }
}
