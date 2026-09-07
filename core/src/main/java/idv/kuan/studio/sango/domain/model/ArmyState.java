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
        copiedState.remainingTravelMonths = remainingTravelMonths;
        copiedState.troops = troops;
        copiedState.training = training;
        copiedState.morale = morale;
        copiedState.trainingFraction = trainingFraction;
        copiedState.moraleFraction = moraleFraction;
        copiedState.tactic = tactic;
        return copiedState;
    }
}
