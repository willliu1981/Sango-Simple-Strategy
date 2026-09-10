package idv.kuan.studio.sango.domain.rule;

import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;

/** 全勢力民心總和決定下月額度；本城及全勢力平均值則另供人口與新兵士氣使用。 */
public final class NationalActionPointRules {
    public static final int MINIMUM_ACTION_POINTS = 3;
    public static final int MAXIMUM_ACTION_POINTS = 9;
    private static final int[] BONUS_THRESHOLDS = {200, 400, 650, 900, 1150, 1500};

    private NationalActionPointRules() {
    }

    public static PublicOrderSummary summarizePlayer(GameState gameState) {
        if (gameState == null) {
            throw new IllegalArgumentException("GameState 不可為 null。");
        }
        return summarizeFaction(gameState, gameState.playerFactionId);
    }

    public static PublicOrderSummary summarizeFaction(GameState gameState, String factionId) {
        if (gameState == null || factionId == null || gameState.cityStates == null) {
            throw new IllegalArgumentException("民心計算需要勢力與城池狀態。");
        }
        int cityCount = 0;
        long totalPublicOrder = 0;
        for (CityState cityState : gameState.cityStates) {
            if (cityState == null) {
                throw new IllegalArgumentException("城池狀態不可為 null。");
            }
            if (factionId.equals(cityState.ownerFactionId)) {
                if (cityState.publicOrder < 0 || cityState.publicOrder > 100) {
                    throw new IllegalArgumentException("民心必須介於 0 到 100。");
                }
                cityCount += 1;
                totalPublicOrder += cityState.publicOrder;
            }
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

    public static int nextThreshold(long totalPublicOrder) {
        for (int threshold : BONUS_THRESHOLDS) {
            if (totalPublicOrder < threshold) {
                return threshold;
            }
        }
        return -1;
    }

    public record PublicOrderSummary(int cityCount, long totalPublicOrder) {
        public PublicOrderSummary {
            if (cityCount < 0 || totalPublicOrder < 0 || totalPublicOrder > cityCount * 100L) {
                throw new IllegalArgumentException("民心摘要超出合法範圍。");
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
            int actionPoints = MINIMUM_ACTION_POINTS;
            for (int threshold : BONUS_THRESHOLDS) {
                if (totalPublicOrder < threshold) {
                    break;
                }
                actionPoints += 1;
            }
            return actionPoints;
        }

        public int nextActionPointThreshold() {
            return nextThreshold(totalPublicOrder);
        }
    }
}
