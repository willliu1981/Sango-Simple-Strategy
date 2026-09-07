package idv.kuan.studio.sango.domain.rule;

import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;

/** 每年十二月底結算一次；不在讀檔、切城或顯示預估時修改人口。 */
public final class PopulationRules {
    private PopulationRules() {
    }

    public static int annualRate(int effectiveOrderHundredths) {
        if (effectiveOrderHundredths < 0 || effectiveOrderHundredths > 10_000) {
            throw new IllegalArgumentException("有效民心超出合法範圍。");
        }
        if (effectiveOrderHundredths < 2_000) {
            return -4;
        }
        if (effectiveOrderHundredths < 4_000) {
            return -2;
        }
        if (effectiveOrderHundredths < 6_000) {
            return 0;
        }
        if (effectiveOrderHundredths < 8_000) {
            return 2;
        }
        return 4;
    }

    public static Projection project(GameState gameState, CityState cityState, int populationCapacity) {
        if (populationCapacity < CampaignBalance.POPULATION_FLOOR) {
            throw new IllegalArgumentException("人口容量低於人口保護值。");
        }
        int effectiveOrder = PublicOrderRules.effectiveOrderHundredths(gameState, cityState);
        int rate = annualRate(effectiveOrder);
        long rawDelta = (long) cityState.population * rate / 100;
        long delta = Math.max(-CampaignBalance.MAXIMUM_ANNUAL_POPULATION_LOSS,
            Math.min(CampaignBalance.MAXIMUM_ANNUAL_POPULATION_GAIN, rawDelta));
        if (delta > 0) {
            // 舊存檔若已超過新容量，只停止成長，不在升級或年度結算時強制裁切。
            delta = Math.min(delta, Math.max(0L, (long) populationCapacity - cityState.population));
        } else if (delta < 0) {
            delta = Math.max(delta, -Math.max(0L, (long) cityState.population - CampaignBalance.POPULATION_FLOOR));
        }
        return new Projection(effectiveOrder, rate, (int) delta, populationCapacity);
    }

    public record Projection(int effectiveOrderHundredths, int annualRatePercent, int delta, int capacity) {
    }
}
