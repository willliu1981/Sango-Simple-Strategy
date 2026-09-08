package idv.kuan.studio.sango.domain.model;

import idv.kuan.studio.sango.domain.rule.DefensePolicy;

/**
 * 城池在目前戰局中的可變狀態。
 */
public final class CityState {
    public String cityId;
    public String ownerFactionId;
    public int population;
    public int agriculture;
    public int commerce;
    public int waterControl;
    public int defense;
    public int troops;
    public int publicOrder;
    /** 民心自然恢復的連續合格月底數，上限為三個月。 */
    public int publicOrderRecoveryStreakMonths;
    public int training;
    public int morale;
    /** 百萬分之一點；與整數素質一起保存，不因讀檔而丟失。 */
    public int trainingFraction;
    public int moraleFraction;
    public int harvestModifierPercent;
    /** 遭受攻擊時採用的方針；易主時重設為均衡。 */
    public DefensePolicy defensePolicy = DefensePolicy.BALANCED;
    /** @deprecated schema 10 改由各勢力的情報快照保存，僅供舊 JSON 遷移。 */
    @Deprecated
    public int scoutedUntilTurn;

    public CityState() {
    }

    public CityState copy() {
        CityState copiedState = new CityState();
        copiedState.cityId = cityId;
        copiedState.ownerFactionId = ownerFactionId;
        copiedState.population = population;
        copiedState.agriculture = agriculture;
        copiedState.commerce = commerce;
        copiedState.waterControl = waterControl;
        copiedState.defense = defense;
        copiedState.troops = troops;
        copiedState.publicOrder = publicOrder;
        copiedState.publicOrderRecoveryStreakMonths = publicOrderRecoveryStreakMonths;
        copiedState.training = training;
        copiedState.morale = morale;
        copiedState.trainingFraction = trainingFraction;
        copiedState.moraleFraction = moraleFraction;
        copiedState.harvestModifierPercent = harvestModifierPercent;
        copiedState.defensePolicy = defensePolicy;
        copiedState.scoutedUntilTurn = scoutedUntilTurn;
        return copiedState;
    }
}
