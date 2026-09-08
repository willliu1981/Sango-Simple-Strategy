package idv.kuan.studio.sango.domain.rule;

import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;

/** 本城民心及全勢力等權平均民心的共同入口；不以人口加權。 */
public final class PublicOrderRules {
    private PublicOrderRules() {
    }

    /** 回傳百分之一點：0..10000，避免把 79.99 過早捨入為 80。 */
    public static int effectiveOrderHundredths(GameState gameState, CityState cityState) {
        if (gameState.neutralFactionId.equals(cityState.ownerFactionId)) {
            // 中立城沒有共同君主，人口吸引力只參考該城民心。
            return cityState.publicOrder * 100;
        }
        NationalActionPointRules.PublicOrderSummary summary =
            NationalActionPointRules.summarizeFaction(gameState, cityState.ownerFactionId);
        if (summary.cityCount() == 0) {
            throw new IllegalArgumentException("城池所屬勢力沒有領地。");
        }
        int localWeight = CampaignBalance.LOCAL_PUBLIC_ORDER_PERCENT;
        return (int) (((long) localWeight * cityState.publicOrder * summary.cityCount()
            + (100L - localWeight) * summary.totalPublicOrder()) / summary.cityCount());
    }

    public static int recruitMorale(GameState gameState, CityState cityState) {
        int moraleRange = CampaignBalance.MAXIMUM_RECRUIT_MORALE
            - CampaignBalance.MINIMUM_RECRUIT_MORALE;
        return CampaignBalance.MINIMUM_RECRUIT_MORALE
            + effectiveOrderHundredths(gameState, cityState) * moraleRange / 10_000;
    }
}
