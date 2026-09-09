package idv.kuan.studio.sango.domain.service;

import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.NationalActionPointRules;

/**
 * 僅依可見城池歸屬與有效偵察快照推估他勢力概況；絕不讀取目標勢力的國庫或額度真值。
 */
public final class FactionIntelligenceEstimateService {
    private static final int UNKNOWN_CITY_PUBLIC_ORDER_LOW = 35;
    private static final int UNKNOWN_CITY_PUBLIC_ORDER_HIGH = 80;
    private static final int UNKNOWN_CITY_COMMERCE = 45;
    private static final int UNKNOWN_CITY_AGRICULTURE = 45;
    private static final int UNKNOWN_CITY_POPULATION = 50_000;

    private final CityIntelligenceService cityIntelligenceService = new CityIntelligenceService();

    public FactionEstimate estimate(GameState gameState, String observerFactionId, String targetFactionId) {
        if (gameState == null || observerFactionId == null || targetFactionId == null) {
            throw new IllegalArgumentException("勢力情報估算需要戰局、觀察方與目標勢力。");
        }
        int cityCount = 0;
        int exactCityCount = 0;
        int troopsLower = 0;
        int troopsUpper = 0;
        int quarterlyTax = 0;
        int harvest = 0;
        long publicOrderLower = 0;
        long publicOrderUpper = 0;

        for (CityState cityState : gameState.cityStates) {
            if (!targetFactionId.equals(cityState.ownerFactionId)) {
                continue;
            }
            cityCount += 1;
            KnownCityView knownCity = cityIntelligenceService.knownView(
                gameState, observerFactionId, cityState.cityId);
            boolean exact = knownCity.exact() && targetFactionId.equals(knownCity.ownerFactionId());
            int troops = exact ? knownCity.troops() : knownCity.troops();
            int variationPercent = exact ? 10 : 25;
            troopsLower += Math.max(0, troops * (100 - variationPercent) / 100);
            troopsUpper += Math.max(100, (troops * (100 + variationPercent) + 99) / 100);

            int population = exact ? knownCity.population() : UNKNOWN_CITY_POPULATION;
            int commerce = exact ? knownCity.commerce() : UNKNOWN_CITY_COMMERCE;
            int agriculture = exact ? knownCity.agriculture() : UNKNOWN_CITY_AGRICULTURE;
            quarterlyTax += 80 + commerce * 6 + population / 2_000;
            harvest += 500 + agriculture * 20 + population / 100;
            if (exact) {
                exactCityCount += 1;
                publicOrderLower += knownCity.publicOrder();
                publicOrderUpper += knownCity.publicOrder();
            } else {
                publicOrderLower += UNKNOWN_CITY_PUBLIC_ORDER_LOW;
                publicOrderUpper += UNKNOWN_CITY_PUBLIC_ORDER_HIGH;
            }
        }
        return new FactionEstimate(
            cityCount,
            exactCityCount,
            troopsLower,
            troopsUpper,
            quarterlyTax,
            quarterlyTax * 4,
            Math.max(0, harvest - (troopsUpper + 19) / 20),
            harvest * 3,
            actionPoints(cityCount, publicOrderLower),
            actionPoints(cityCount, publicOrderUpper)
        );
    }

    private int actionPoints(int cityCount, long totalPublicOrder) {
        return new NationalActionPointRules.PublicOrderSummary(cityCount, totalPublicOrder)
            .monthlyActionPoints();
    }

    public record FactionEstimate(
        int cityCount,
        int exactCityCount,
        int troopsLower,
        int troopsUpper,
        int goldLower,
        int goldUpper,
        int foodLower,
        int foodUpper,
        int actionPointsLower,
        int actionPointsUpper
    ) {
        public boolean allCitiesExact() {
            return cityCount > 0 && cityCount == exactCityCount;
        }
    }
}
