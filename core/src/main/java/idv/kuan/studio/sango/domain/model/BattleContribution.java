package idv.kuan.studio.sango.domain.model;

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
        copiedContribution.strength = strength;
        return copiedContribution;
    }
}
