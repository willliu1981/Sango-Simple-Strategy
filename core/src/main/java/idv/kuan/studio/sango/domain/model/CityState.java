package idv.kuan.studio.sango.domain.model;

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
        copiedState.scoutedUntilTurn = scoutedUntilTurn;
        return copiedState;
    }
}
