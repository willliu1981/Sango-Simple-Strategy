package idv.kuan.studio.sango.domain.rule;

import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;

/** 玩家、AI、Command 與預覽共用的內政可執行性檢查。 */
public final class DomesticActionRules {
    public static final int PACIFY_MAX_PUBLIC_ORDER = 90;

    private DomesticActionRules() {
    }

    public static DomesticActionFailureReason evaluate(GameState gameState, String cityId, DomesticActionType actionType) {
        return evaluate(gameState, cityId, actionType, CampaignBalance.DEFAULT_RECRUIT_AMOUNT);
    }

    public static DomesticActionFailureReason evaluate(
        GameState gameState, String cityId, DomesticActionType actionType, int recruitmentAmount
    ) {
        GameStateValidator.validate(gameState);
        return evaluateForFaction(gameState, gameState.playerFactionId, cityId, actionType,
            recruitmentAmount, OfficerCommandProfile.DEFAULT);
    }

    public static DomesticActionFailureReason evaluateForFaction(
        GameState gameState, String factionId, String cityId, DomesticActionType actionType,
        int recruitmentAmount, OfficerCommandProfile officer
    ) {
        if (actionType == null || officer == null) {
            throw new IllegalArgumentException("內政命令與執行能力不可為 null。");
        }
        FactionState factionState = gameState.requireFactionState(factionId);
        if (!gameState.isGameplayActive() || !factionState.active) {
            return DomesticActionFailureReason.PLAYER_ELIMINATED;
        }
        CityState cityState = gameState.requireCityState(cityId);
        if (!factionId.equals(cityState.ownerFactionId)) {
            return DomesticActionFailureReason.CITY_NOT_OWNED;
        }
        if (FactionActionPointRules.remaining(gameState, factionId) < actionType.getActionPointCost()) {
            return DomesticActionFailureReason.NO_ACTION_POINTS;
        }
        int goldCost = actionType.getGoldCost();
        int foodCost = actionType.getFoodCost();
        if (actionType == DomesticActionType.RECRUIT) {
            if (recruitmentAmount < 1 || recruitmentAmount > CampaignBalance.MAXIMUM_RECRUITMENT) {
                return DomesticActionFailureReason.INVALID_RECRUIT_AMOUNT;
            }
            if ((long) cityState.population - recruitmentAmount < CampaignBalance.RECRUIT_POPULATION_RESERVE) {
                return DomesticActionFailureReason.INSUFFICIENT_POPULATION;
            }
            if (recruitmentAmount > RecruitmentRules.populationLimit(cityState.population, officer)
                || (long) cityState.troops + recruitmentAmount > Integer.MAX_VALUE) {
                return DomesticActionFailureReason.RECRUIT_LIMIT_EXCEEDED;
            }
            goldCost = RecruitmentRules.goldCost(recruitmentAmount);
            foodCost = RecruitmentRules.foodCost(recruitmentAmount);
        }
        if (factionState.gold < goldCost) {
            return DomesticActionFailureReason.INSUFFICIENT_GOLD;
        }
        if (factionState.food < foodCost) {
            return DomesticActionFailureReason.INSUFFICIENT_FOOD;
        }
        if (actionType == DomesticActionType.TRAIN && cityState.troops == 0) {
            return DomesticActionFailureReason.NO_TROOPS;
        }
        if (wouldExceedMaximum(cityState, actionType, officer)) {
            return DomesticActionFailureReason.VALUE_AT_MAXIMUM;
        }
        return DomesticActionFailureReason.NONE;
    }

    public static int pacifyGain(int publicOrder) {
        if (publicOrder < 50) {
            return 6;
        }
        if (publicOrder < 70) {
            return 4;
        }
        if (publicOrder < 85) {
            return 2;
        }
        if (publicOrder < PACIFY_MAX_PUBLIC_ORDER) {
            return 1;
        }
        return 0;
    }

    private static boolean wouldExceedMaximum(CityState cityState, DomesticActionType actionType, OfficerCommandProfile officer) {
        if (actionType.getAgricultureGain() > 0 && cityState.agriculture >= 100) {
            return true;
        }
        if (actionType.getCommerceGain() > 0 && cityState.commerce >= 100) {
            return true;
        }
        if (actionType.getWaterControlGain() > 0 && cityState.waterControl >= 100) {
            return true;
        }
        if (actionType.getDefenseGain() > 0 && cityState.defense >= 100) {
            return true;
        }
        if (actionType == DomesticActionType.PACIFY && cityState.publicOrder >= PACIFY_MAX_PUBLIC_ORDER) {
            return true;
        }
        if (actionType == DomesticActionType.TRAIN) {
            TroopQualityRules.TrainingProjection projection = TroopQualityRules.projectTraining(cityState, officer);
            return projection.trainingScaled() == TroopQualityRules.training(cityState)
                && projection.moraleScaled() == TroopQualityRules.morale(cityState);
        }
        return false;
    }
}
