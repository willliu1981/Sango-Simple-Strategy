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
    public int troops;
    public int publicOrder;
    public int training;

    public CityState() {
    }

    public CityState copy() {
        CityState copiedState = new CityState();
        copiedState.cityId = cityId;
        copiedState.ownerFactionId = ownerFactionId;
        copiedState.population = population;
        copiedState.agriculture = agriculture;
        copiedState.commerce = commerce;
        copiedState.troops = troops;
        copiedState.publicOrder = publicOrder;
        copiedState.training = training;
        return copiedState;
    }
}
