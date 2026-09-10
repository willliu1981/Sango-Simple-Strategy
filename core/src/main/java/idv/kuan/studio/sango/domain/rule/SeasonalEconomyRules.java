package idv.kuan.studio.sango.domain.rule;

import idv.kuan.studio.sango.domain.model.CityState;

/**
 * 季節收入、軍糧與洪災的集中公式。
 */
public final class SeasonalEconomyRules {
    public static final int FLOOD_RESOLUTION_MONTH = 6;
    public static final int HARVEST_MONTH = 9;

    private SeasonalEconomyRules() {
    }

    public static boolean isQuarterEnd(int month) {
        return month == 3 || month == 6 || month == 9 || month == 12;
    }

    public static int calculateQuarterlyTax(CityState cityState) {
        return 80 + cityState.commerce * 6 + cityState.population / 200;
    }

    public static int calculateBaseHarvest(CityState cityState) {
        return 500 + cityState.agriculture * 20 + cityState.population / 10;
    }

    public static int calculateHarvest(CityState cityState) {
        return calculateBaseHarvest(cityState) * cityState.harvestModifierPercent / 100;
    }

    public static int calculateMilitaryFoodUpkeep(int troopCount) {
        if (troopCount <= 0) {
            return 0;
        }
        return (troopCount + 19) / 20;
    }

    public static int calculateFloodRiskPercent(CityState cityState) {
        return clamp(65 - cityState.waterControl, 5, 60);
    }

    public static int calculateFloodHarvestLossPercent(CityState cityState) {
        return clamp(45 - cityState.waterControl / 2, 10, 40);
    }

    public static int calculateEstimatedHarvest(CityState cityState) {
        return calculateBaseHarvest(cityState) * cityState.harvestModifierPercent / 100;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
