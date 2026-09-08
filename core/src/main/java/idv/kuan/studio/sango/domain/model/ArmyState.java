package idv.kuan.studio.sango.domain.model;

import idv.kuan.studio.sango.domain.rule.BattleTactic;

/**
 * 離開城池後、正在道路上行軍的軍隊。
 */
public final class ArmyState {
    public String armyId;
    /** 同一次聯合出征共用的群組 ID；舊式外部單軍可為 null。 */
    public String expeditionGroupId;
    public String factionId;
    public String originCityId;
    public String targetCityId;
    /** 退卻路徑；第一個節點固定為原戰場，null 表示一般行軍。 */
    public String[] retreatRouteCityIds;
    /** 退卻軍目前所在節點於 retreatRouteCityIds 的索引。 */
    public int retreatRouteIndex;
    public int remainingTravelMonths;
    public int troops;
    public int training;
    public int morale;
    /** 百萬分之一點；與整數素質一起保存，不因讀檔而丟失。 */
    public int trainingFraction;
    public int moraleFraction;
    public BattleTactic tactic;

    public ArmyState() {
    }

    public ArmyState copy() {
        ArmyState copiedState = new ArmyState();
        copiedState.armyId = armyId;
        copiedState.expeditionGroupId = expeditionGroupId;
        copiedState.factionId = factionId;
        copiedState.originCityId = originCityId;
        copiedState.targetCityId = targetCityId;
        copiedState.retreatRouteCityIds = retreatRouteCityIds == null
            ? null : retreatRouteCityIds.clone();
        copiedState.retreatRouteIndex = retreatRouteIndex;
        copiedState.remainingTravelMonths = remainingTravelMonths;
        copiedState.troops = troops;
        copiedState.training = training;
        copiedState.morale = morale;
        copiedState.trainingFraction = trainingFraction;
        copiedState.moraleFraction = moraleFraction;
        copiedState.tactic = tactic;
        return copiedState;
    }

    public boolean isRetreating() {
        return retreatRouteCityIds != null;
    }

    public String retreatDestinationCityId() {
        return isRetreating() && retreatRouteCityIds.length > 0
            ? retreatRouteCityIds[retreatRouteCityIds.length - 1] : null;
    }

    public String retreatCurrentCityId() {
        return isRetreating() && retreatRouteIndex >= 0
            && retreatRouteIndex < retreatRouteCityIds.length
            ? retreatRouteCityIds[retreatRouteIndex] : null;
    }
}
