package idv.kuan.studio.sango.domain.rule;

import idv.kuan.studio.sango.domain.model.CampaignStatus;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;

/**
 * 內政命令的可執行性判斷。Command 與 UI 共用，避免兩邊規則不一致。
 */
public final class DomesticActionRules {
    private DomesticActionRules() {
    }

    public static DomesticActionFailureReason evaluate(
        GameState gameState,
        String cityId,
        DomesticActionType actionType
    ) {
        GameStateValidator.validate(gameState);
        if (cityId == null || cityId.trim().isEmpty()) {
            throw new IllegalArgumentException("cityId 不可為空。");
        }
        if (actionType == null) {
            throw new IllegalArgumentException("actionType 不可為 null。");
        }
        if (gameState.campaignStatus != CampaignStatus.IN_PROGRESS) {
            return DomesticActionFailureReason.CAMPAIGN_FINISHED;
        }

        CityState cityState = gameState.requireCityState(cityId);
        if (!gameState.playerFactionId.equals(cityState.ownerFactionId)) {
            return DomesticActionFailureReason.CITY_NOT_OWNED;
        }
        if (gameState.actionPointsRemaining < actionType.getActionPointCost()) {
            return DomesticActionFailureReason.NO_ACTION_POINTS;
        }

        FactionState factionState = gameState.requirePlayerFactionState();
        if (factionState.gold < actionType.getGoldCost()) {
            return DomesticActionFailureReason.INSUFFICIENT_GOLD;
        }
        if (factionState.food < actionType.getFoodCost()) {
            return DomesticActionFailureReason.INSUFFICIENT_FOOD;
        }
        if (cityState.population + actionType.getPopulationDelta() < 1000) {
            return DomesticActionFailureReason.INSUFFICIENT_POPULATION;
        }
        if (wouldExceedMaximum(cityState, actionType)) {
            return DomesticActionFailureReason.VALUE_AT_MAXIMUM;
        }
        return DomesticActionFailureReason.NONE;
    }

    private static boolean wouldExceedMaximum(
        CityState cityState,
        DomesticActionType actionType
    ) {
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
        return actionType.getTrainingGain() > 0 && cityState.training >= 100;
    }
}
