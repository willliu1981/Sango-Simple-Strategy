package idv.kuan.studio.sango.domain.rule;

import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;

/** 單次徵兵曲線、成本、人口保留量及預覽的純計算。 */
public final class RecruitmentRules {
    private RecruitmentRules() {
    }

    public static int populationLimit(int population, OfficerCommandProfile officer) {
        if (population <= CampaignBalance.RECRUIT_POPULATION_RESERVE) {
            return 0;
        }
        long squaredFactor = (long) CampaignBalance.RECRUITMENT_SQRT_FACTOR
            * CampaignBalance.RECRUITMENT_SQRT_FACTOR;
        long radicand = population * squaredFactor;
        long root = (long) Math.sqrt(radicand);
        while ((root + 1) * (root + 1) <= radicand) {
            root += 1;
        }
        while (root * root > radicand) {
            root -= 1;
        }
        long officerLimit = root * officer.recruitmentCapacityPercent() / 100;
        return (int) Math.min(CampaignBalance.MAXIMUM_RECRUITMENT,
            Math.min(officerLimit, population - CampaignBalance.RECRUIT_POPULATION_RESERVE));
    }

    public static int maximumRecruitable(CityState cityState, FactionState factionState, OfficerCommandProfile officer) {
        long maximum = populationLimit(cityState.population, officer);
        maximum = Math.min(maximum, affordableCount(factionState.gold, CampaignBalance.RECRUIT_GOLD_NUMERATOR));
        maximum = Math.min(maximum, affordableCount(factionState.food, CampaignBalance.RECRUIT_FOOD_NUMERATOR));
        maximum = Math.min(maximum, (long) Integer.MAX_VALUE - cityState.troops);
        return (int) Math.max(0, maximum);
    }

    /** AI 預設只正常徵兵，不進入會降低民心的強徵區間。 */
    public static int maximumVoluntaryRecruitable(
        CityState cityState, FactionState factionState, OfficerCommandProfile officer
    ) {
        return Math.min(maximumRecruitable(cityState, factionState, officer),
            Math.max(0, cityState.population - CampaignBalance.RECRUIT_SAFE_POPULATION_RESERVE));
    }

    public static boolean isForcedRecruitment(int population, int troopCount) {
        return troopCount > 0
            && population - troopCount < CampaignBalance.RECRUIT_SAFE_POPULATION_RESERVE;
    }

    public static int goldCost(int troopCount) {
        return calculateCost(troopCount, CampaignBalance.RECRUIT_GOLD_NUMERATOR);
    }

    public static int foodCost(int troopCount) {
        return calculateCost(troopCount, CampaignBalance.RECRUIT_FOOD_NUMERATOR);
    }

    private static long affordableCount(int resource, int numerator) {
        return (long) resource * CampaignBalance.RECRUIT_COST_DENOMINATOR / numerator;
    }

    private static int calculateCost(int troopCount, int numerator) {
        if (troopCount < 0 || troopCount > CampaignBalance.MAXIMUM_RECRUITMENT) {
            throw new IllegalArgumentException("單次徵兵數必須介於 0 到 "
                + CampaignBalance.MAXIMUM_RECRUITMENT + "。");
        }
        return (int) (((long) troopCount * numerator + CampaignBalance.RECRUIT_COST_DENOMINATOR - 1)
            / CampaignBalance.RECRUIT_COST_DENOMINATOR);
    }

    public static Quote quote(GameState gameState, CityState cityState, int troopCount, OfficerCommandProfile officer) {
        FactionState factionState = gameState.requireFactionState(cityState.ownerFactionId);
        int startingMorale = PublicOrderRules.recruitMorale(gameState, cityState);
        int resultingTraining = TroopQualityRules.training(cityState);
        int resultingMorale = TroopQualityRules.morale(cityState);
        if (troopCount > 0) {
            resultingTraining = TroopQualityRules.weightedAverage(cityState.troops, resultingTraining,
                troopCount, officer.recruitTraining() * TroopQualityRules.SCALE);
            resultingMorale = TroopQualityRules.weightedAverage(cityState.troops, resultingMorale,
                troopCount, startingMorale * TroopQualityRules.SCALE);
        }
        boolean forced = isForcedRecruitment(cityState.population, troopCount);
        int publicOrderLoss = forced
            ? Math.min(cityState.publicOrder, CampaignBalance.FORCED_RECRUIT_PUBLIC_ORDER_LOSS) : 0;
        return new Quote(troopCount, maximumRecruitable(cityState, factionState, officer),
            goldCost(troopCount), foodCost(troopCount), officer.recruitTraining(), startingMorale,
            resultingTraining, resultingMorale, forced, publicOrderLoss,
            cityState.publicOrder - publicOrderLoss);
    }

    public record Quote(
        int troopCount, int maximum, int goldCost, int foodCost,
        int recruitTraining, int recruitMorale, int resultingTraining, int resultingMorale,
        boolean forced, int publicOrderLoss, int resultingPublicOrder
    ) {
    }
}
