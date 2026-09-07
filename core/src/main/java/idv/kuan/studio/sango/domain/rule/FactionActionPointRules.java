package idv.kuan.studio.sango.domain.rule;

import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;

/**
 * 玩家與 AI 共用的額度發放及扣點入口。
 * 玩家繼續使用 GameState 既有快照欄位；FactionState 的 ai 欄位只供非玩家勢力。
 */
public final class FactionActionPointRules {
    private FactionActionPointRules() {
    }

    public static int remaining(GameState gameState, String factionId) {
        if (gameState.playerFactionId.equals(factionId)) {
            return gameState.actionPointsRemaining;
        }
        return gameState.requireFactionState(factionId).aiActionPointsRemaining;
    }

    public static int capacity(GameState gameState, String factionId) {
        if (gameState.playerFactionId.equals(factionId)) {
            return gameState.actionPointsPerTurn;
        }
        return gameState.requireFactionState(factionId).aiActionPointsPerTurn;
    }

    public static void spend(GameState gameState, String factionId, int amount) {
        if (amount < 1 || remaining(gameState, factionId) < amount) {
            throw new IllegalArgumentException("行動力不足或扣點數量無效。");
        }
        if (gameState.playerFactionId.equals(factionId)) {
            gameState.actionPointsRemaining -= amount;
        } else {
            gameState.requireFactionState(factionId).aiActionPointsRemaining -= amount;
        }
    }

    public static void refreshAll(GameState gameState, boolean firstMonth) {
        for (FactionState factionState : gameState.factionStates) {
            int allowance = calculateAllowance(gameState, factionState, firstMonth);
            if (gameState.playerFactionId.equals(factionState.factionId)) {
                // 保留滅亡前的月額度快照，但剩餘為零。
                if (allowance > 0) {
                    gameState.actionPointsPerTurn = allowance;
                }
                gameState.actionPointsRemaining = allowance;
            } else {
                factionState.aiActionPointsPerTurn = allowance;
                factionState.aiActionPointsRemaining = allowance;
            }
        }
    }

    public static void initializeMigratedAi(GameState gameState) {
        for (FactionState factionState : gameState.factionStates) {
            if (!gameState.playerFactionId.equals(factionState.factionId)) {
                int allowance = calculateAllowance(gameState, factionState, false);
                factionState.aiActionPointsPerTurn = allowance;
                factionState.aiActionPointsRemaining = allowance;
            }
        }
    }

    private static int calculateAllowance(
        GameState gameState, FactionState factionState, boolean firstMonth
    ) {
        if (!factionState.active || gameState.neutralFactionId.equals(factionState.factionId)) {
            return 0;
        }
        NationalActionPointRules.PublicOrderSummary summary =
            NationalActionPointRules.summarizeFaction(gameState, factionState.factionId);
        if (summary.cityCount() == 0) {
            return 0;
        }
        return firstMonth ? NationalActionPointRules.MINIMUM_ACTION_POINTS : summary.monthlyActionPoints();
    }
}
