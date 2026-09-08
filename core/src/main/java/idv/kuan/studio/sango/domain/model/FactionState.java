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
    /** 此勢力自行取得的城池情報；不同勢力不共用。 */
    public CityIntelligenceSnapshot[] cityIntelligence = new CityIntelligenceSnapshot[0];

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
        if (cityIntelligence != null) {
            copiedState.cityIntelligence = new CityIntelligenceSnapshot[cityIntelligence.length];
            for (int i = 0; i < cityIntelligence.length; i++) {
                copiedState.cityIntelligence[i] = cityIntelligence[i] == null
                    ? null : cityIntelligence[i].copy();
            }
        }
        return copiedState;
    }
}
