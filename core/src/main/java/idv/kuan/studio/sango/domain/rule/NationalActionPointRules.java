package idv.kuan.studio.sango.domain.rule;

import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;

/**
 * 全城民心與每月行動力的純計算規則。不修改 GameState，也不依賴 UI 或 Graphics Context。
 * 每座玩家領地等權重；每 15 點平均民心增加 1 點行動力，範圍為 3 至 9。
 */
public final class NationalActionPointRules {
    public static final int MINIMUM_ACTION_POINTS = 3;
    public static final int MAXIMUM_ACTION_POINTS = 9;
    public static final int PUBLIC_ORDER_PER_BONUS_POINT = 15;

    private NationalActionPointRules() {
    }

    public static PublicOrderSummary summarizePlayer(GameState gameState) {
        if (gameState == null || gameState.playerFactionId == null || gameState.cityStates == null) {
            throw new IllegalArgumentException("全城民心計算需要完整的玩家與城池狀態。");
        }
        int cityCount = 0;
        long totalPublicOrder = 0;
        for (CityState cityState : gameState.cityStates) {
            if (cityState == null) {
                throw new IllegalArgumentException("城池狀態不可為 null。");
            }
            if (!gameState.playerFactionId.equals(cityState.ownerFactionId)) {
                continue;
            }
            if (cityState.publicOrder < 0 || cityState.publicOrder > 100) {
                throw new IllegalArgumentException("城池民心必須介於 0 到 100。");
            }
            cityCount += 1;
            totalPublicOrder += cityState.publicOrder;
        }
        return new PublicOrderSummary(cityCount, totalPublicOrder);
    }

    public static int calculateMonthlyActionPoints(GameState gameState) {
        if (gameState == null) {
            throw new IllegalArgumentException("GameState 不可為 null。");
        }
        if (gameState.gameplayStatus != GameplayStatus.ACTIVE) {
            return 0;
        }
        return summarizePlayer(gameState).monthlyActionPoints();
    }

    /**
     * 平均值以十分位向下顯示，避免 89.99 被顯示成 90.0，卻尚未達到 9 點門檻。
     * 額度直接以總和與城數計算，不先截斷平均值，也不受浮點四捨五入影響。
     */
    public record PublicOrderSummary(int cityCount, long totalPublicOrder) {
        public PublicOrderSummary {
            if (cityCount < 0 || totalPublicOrder < 0 || totalPublicOrder > cityCount * 100L) {
                throw new IllegalArgumentException("全城民心摘要超出合法範圍。");
            }
        }

        public int averagePublicOrderTenths() {
            if (cityCount == 0) {
                return 0;
            }
            return (int) (totalPublicOrder * 10L / cityCount);
        }

        public int monthlyActionPoints() {
            if (cityCount == 0) {
                return 0;
            }
            long bonusPoints = totalPublicOrder / (cityCount * (long) PUBLIC_ORDER_PER_BONUS_POINT);
            return (int) Math.min(MAXIMUM_ACTION_POINTS, MINIMUM_ACTION_POINTS + bonusPoints);
        }
    }
}
