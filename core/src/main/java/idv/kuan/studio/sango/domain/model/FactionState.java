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
    /** 只供非玩家勢力使用；玩家額度沿用 GameState 的既有快照欄位。 */
    public int aiActionPointsPerTurn;
    public int aiActionPointsRemaining;

    public FactionState() {
    }

    public FactionState copy() {
        FactionState copiedState = new FactionState();
        copiedState.factionId = factionId;
        copiedState.capitalCityId = capitalCityId;
        copiedState.gold = gold;
        copiedState.food = food;
        copiedState.active = active;
        copiedState.aiActionPointsPerTurn = aiActionPointsPerTurn;
        copiedState.aiActionPointsRemaining = aiActionPointsRemaining;
        return copiedState;
    }
}
