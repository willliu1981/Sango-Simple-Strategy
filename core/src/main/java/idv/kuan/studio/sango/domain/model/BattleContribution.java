package idv.kuan.studio.sango.domain.model;

import idv.kuan.studio.sango.domain.rule.BattleTactic;

/** 聯合戰鬥中單一來源軍隊的兵力、傷亡與戰力快照。 */
public final class BattleContribution {
    public String armyId;
    public String factionId;
    public String originCityId;
    public int troopsBefore;
    public int losses;
    public int survivors;
    public int training;
    public int morale;
    /** schema 11 起保存各來源軍實際採用的戰術。 */
    public BattleTactic attackerTactic;
    /** 不含相剋加成的基礎戰力；舊戰報預設為 0。 */
    public int baseStrength;
    /** 套用相剋後的有效戰力；舊戰報保留當時既有的 strength 值。 */
    public int strength;

    public BattleContribution() {
    }

    public BattleContribution copy() {
        BattleContribution copiedContribution = new BattleContribution();
        copiedContribution.armyId = armyId;
        copiedContribution.factionId = factionId;
        copiedContribution.originCityId = originCityId;
        copiedContribution.troopsBefore = troopsBefore;
        copiedContribution.losses = losses;
        copiedContribution.survivors = survivors;
        copiedContribution.training = training;
        copiedContribution.morale = morale;
        copiedContribution.attackerTactic = attackerTactic;
        copiedContribution.baseStrength = baseStrength;
        copiedContribution.strength = strength;
        return copiedContribution;
    }
}
