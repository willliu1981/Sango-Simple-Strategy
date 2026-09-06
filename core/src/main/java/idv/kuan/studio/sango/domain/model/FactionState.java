package idv.kuan.studio.sango.domain.model;

/**
 * 勢力在目前戰局中的可變狀態。
 */
public final class FactionState {
    public String factionId;
    public String capitalCityId;
    public int gold;
    public int food;
    public boolean active;

    public FactionState() {
    }

    public FactionState copy() {
        FactionState copiedState = new FactionState();
        copiedState.factionId = factionId;
        copiedState.capitalCityId = capitalCityId;
        copiedState.gold = gold;
        copiedState.food = food;
        copiedState.active = active;
        return copiedState;
    }
}
