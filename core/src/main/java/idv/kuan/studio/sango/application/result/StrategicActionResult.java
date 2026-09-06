package idv.kuan.studio.sango.application.result;

import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.StrategicActionFailureReason;

/**
 * 偵察或出征命令的共用結果。
 */
public final class StrategicActionResult {
    private final boolean successful;
    private final String actionCode;
    private final StrategicActionFailureReason failureReason;
    private final GameState gameState;
    private final int dispatchedTroops;

    private StrategicActionResult(
        boolean successful,
        String actionCode,
        StrategicActionFailureReason failureReason,
        GameState gameState,
        int dispatchedTroops
    ) {
        this.successful = successful;
        this.actionCode = actionCode;
        this.failureReason = failureReason;
        this.gameState = gameState;
        this.dispatchedTroops = dispatchedTroops;
    }

    public static StrategicActionResult success(
        String actionCode,
        GameState gameState,
        int dispatchedTroops
    ) {
        return new StrategicActionResult(
            true,
            actionCode,
            StrategicActionFailureReason.NONE,
            gameState,
            dispatchedTroops
        );
    }

    public static StrategicActionResult failure(
        String actionCode,
        StrategicActionFailureReason failureReason
    ) {
        return new StrategicActionResult(false, actionCode, failureReason, null, 0);
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getActionCode() {
        return actionCode;
    }

    public StrategicActionFailureReason getFailureReason() {
        return failureReason;
    }

    public GameState getGameState() {
        return gameState;
    }

    public int getDispatchedTroops() {
        return dispatchedTroops;
    }
}
