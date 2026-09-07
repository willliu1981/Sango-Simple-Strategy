package idv.kuan.studio.sango.domain.rule;

import idv.kuan.studio.sango.domain.model.CityState;

/** 玩家與 AI 共用的現行出征規格，與徵兵／訓練容量分離。 */
public final class ExpeditionRules {
    public static final int ACTION_POINT_COST = 1;
    public static final int FOOD_COST = 100;
    public static final int MINIMUM_GARRISON = 400;
    public static final int MINIMUM_EXPEDITION = 400;
    public static final int MAXIMUM_EXPEDITION = 1_000;

    private ExpeditionRules() {
    }

    public static int calculateDispatchTroops(CityState cityState) {
        int availableTroops = cityState.troops - MINIMUM_GARRISON;
        return Math.max(0, Math.min(MAXIMUM_EXPEDITION, availableTroops) / 100 * 100);
    }
}
