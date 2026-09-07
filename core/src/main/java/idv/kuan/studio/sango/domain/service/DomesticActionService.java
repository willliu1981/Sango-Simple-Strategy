package idv.kuan.studio.sango.domain.service;

import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.DomesticActionFailureReason;
import idv.kuan.studio.sango.domain.rule.DomesticActionRules;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;
import idv.kuan.studio.sango.domain.rule.FactionActionPointRules;
import idv.kuan.studio.sango.domain.rule.OfficerCommandProfile;
import idv.kuan.studio.sango.domain.rule.RecruitmentRules;
import idv.kuan.studio.sango.domain.rule.TroopQualityRules;

/** 由 Application 的工作副本或月底 AI 呼叫，驗證完成後才套用一次命令。 */
public final class DomesticActionService {
    public DomesticActionFailureReason apply(
        GameState gameState, String factionId, String cityId, DomesticActionType actionType,
        int recruitmentAmount, OfficerCommandProfile officer
    ) {
        DomesticActionFailureReason failure = DomesticActionRules.evaluateForFaction(
            gameState, factionId, cityId, actionType, recruitmentAmount, officer);
        if (failure != DomesticActionFailureReason.NONE) {
            return failure;
        }
        CityState cityState = gameState.requireCityState(cityId);
        FactionState factionState = gameState.requireFactionState(factionId);
        int goldCost = actionType.getGoldCost();
        int foodCost = actionType.getFoodCost();
        if (actionType == DomesticActionType.RECRUIT) {
            RecruitmentRules.Quote quote = RecruitmentRules.quote(gameState, cityState, recruitmentAmount, officer);
            goldCost = quote.goldCost();
            foodCost = quote.foodCost();
            TroopQualityRules.set(cityState, quote.resultingTraining(), quote.resultingMorale());
            cityState.troops += recruitmentAmount;
            cityState.population -= recruitmentAmount;
        } else if (actionType == DomesticActionType.TRAIN) {
            TroopQualityRules.train(cityState, officer);
        } else {
            cityState.agriculture = Math.min(100, cityState.agriculture + actionType.getAgricultureGain());
            cityState.commerce = Math.min(100, cityState.commerce + actionType.getCommerceGain());
            cityState.waterControl = Math.min(100, cityState.waterControl + actionType.getWaterControlGain());
            cityState.defense = Math.min(100, cityState.defense + actionType.getDefenseGain());
        }
        factionState.gold -= goldCost;
        factionState.food -= foodCost;
        FactionActionPointRules.spend(gameState, factionId, actionType.getActionPointCost());
        return DomesticActionFailureReason.NONE;
    }
}
