package idv.kuan.studio.sango.domain.model;

/**
 * 可序列化的戰局狀態。存檔只保存 ID 與可變資料，不嵌入 Definition。
 */
public final class GameState {
    public int schemaVersion;
    public String scenarioId;
    public String playerFactionId;
    public int currentTurn;
    public int currentYear;
    public int currentMonth;
    public int actionPointsRemaining;
    public int actionPointsPerTurn;
    public String lastActionCode;
    public FactionState[] factionStates;
    public CityState[] cityStates;

    public GameState() {
    }

    public GameState copy() {
        GameState copiedState = new GameState();
        copiedState.schemaVersion = schemaVersion;
        copiedState.scenarioId = scenarioId;
        copiedState.playerFactionId = playerFactionId;
        copiedState.currentTurn = currentTurn;
        copiedState.currentYear = currentYear;
        copiedState.currentMonth = currentMonth;
        copiedState.actionPointsRemaining = actionPointsRemaining;
        copiedState.actionPointsPerTurn = actionPointsPerTurn;
        copiedState.lastActionCode = lastActionCode;
        copiedState.factionStates = copyFactionStates(factionStates);
        copiedState.cityStates = copyCityStates(cityStates);
        return copiedState;
    }

    public FactionState requirePlayerFactionState() {
        if (factionStates != null) {
            for (FactionState factionState : factionStates) {
                if (factionState != null && playerFactionId.equals(factionState.factionId)) {
                    return factionState;
                }
            }
        }
        throw new IllegalStateException("找不到玩家勢力狀態：" + playerFactionId);
    }

    public CityState requireCapitalCityState() {
        FactionState playerFactionState = requirePlayerFactionState();
        if (cityStates != null) {
            for (CityState cityState : cityStates) {
                if (cityState != null && playerFactionState.capitalCityId.equals(cityState.cityId)) {
                    return cityState;
                }
            }
        }
        throw new IllegalStateException("找不到玩家主城狀態：" + playerFactionState.capitalCityId);
    }

    private FactionState[] copyFactionStates(FactionState[] sourceStates) {
        if (sourceStates == null) {
            return null;
        }

        FactionState[] copiedStates = new FactionState[sourceStates.length];
        for (int i = 0; i < sourceStates.length; i++) {
            copiedStates[i] = sourceStates[i] == null ? null : sourceStates[i].copy();
        }
        return copiedStates;
    }

    private CityState[] copyCityStates(CityState[] sourceStates) {
        if (sourceStates == null) {
            return null;
        }

        CityState[] copiedStates = new CityState[sourceStates.length];
        for (int i = 0; i < sourceStates.length; i++) {
            copiedStates[i] = sourceStates[i] == null ? null : sourceStates[i].copy();
        }
        return copiedStates;
    }
}
