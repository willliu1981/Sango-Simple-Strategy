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
    public int training;
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
        copiedState.training = training;
        copiedState.harvestModifierPercent = harvestModifierPercent;
        copiedState.scoutedUntilTurn = scoutedUntilTurn;
        return copiedState;
    }
}
