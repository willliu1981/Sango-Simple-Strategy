package idv.kuan.studio.sango.domain.model;

import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.PostEncounterOrder;

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
    /** 從道路接戰位置直接折返原出發城；此時不宣稱部隊位於任一城池。 */
    public boolean returningFromRoad;
    /** 退卻軍目前所在節點於 retreatRouteCityIds 的索引。 */
    public int retreatRouteIndex;
    public int remainingTravelMonths;
    /** 出發時的道路總行程，用於判斷相向部隊是否在本月交會。 */
    public int totalTravelMonths;
    /** 出發時兵力，用於接戰後自動判斷存活比例。 */
    public int initialTroops;
    public int troops;
    public int training;
    public int morale;
    /** 百萬分之一點；與整數素質一起保存，不因讀檔而丟失。 */
    public int trainingFraction;
    public int moraleFraction;
    public BattleTactic tactic = BattleTactic.HOLD;
    public PostEncounterOrder postEncounterOrder = PostEncounterOrder.AUTO;

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
        copiedState.returningFromRoad = returningFromRoad;
        copiedState.retreatRouteIndex = retreatRouteIndex;
        copiedState.remainingTravelMonths = remainingTravelMonths;
        copiedState.totalTravelMonths = totalTravelMonths;
        copiedState.initialTroops = initialTroops;
        copiedState.troops = troops;
        copiedState.training = training;
        copiedState.morale = morale;
        copiedState.trainingFraction = trainingFraction;
        copiedState.moraleFraction = moraleFraction;
        copiedState.tactic = tactic;
        copiedState.postEncounterOrder = postEncounterOrder;
        return copiedState;
    }

    public boolean isRetreating() {
        return retreatRouteCityIds != null || returningFromRoad;
    }

    public String retreatDestinationCityId() {
        if (returningFromRoad) {
            return originCityId;
        }
        return isRetreating() && retreatRouteCityIds.length > 0
            ? retreatRouteCityIds[retreatRouteCityIds.length - 1] : null;
    }

    public String retreatCurrentCityId() {
        if (returningFromRoad) {
            return null;
        }
        return isRetreating() && retreatRouteIndex >= 0
            && retreatRouteIndex < retreatRouteCityIds.length
            ? retreatRouteCityIds[retreatRouteIndex] : null;
    }
}
