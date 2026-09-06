package idv.kuan.studio.sango.repository.save;

import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.ScenarioObjectiveStatus;

/**
 * 存讀檔畫面所需的輕量槽位摘要。
 */
public final class SaveSlotMetadata {
    private final int slotNumber;
    private final long savedAtEpochMillis;
    private final String gameVersion;
    private final String scenarioId;
    private final String playerFactionId;
    private final String capitalCityId;
    private final int currentYear;
    private final int currentMonth;
    private final int currentTurn;
    private final int ownedCityCount;
    private final ScenarioObjectiveStatus objectiveStatus;

    private SaveSlotMetadata(
        int slotNumber,
        long savedAtEpochMillis,
        String gameVersion,
        String scenarioId,
        String playerFactionId,
        String capitalCityId,
        int currentYear,
        int currentMonth,
        int currentTurn,
        int ownedCityCount,
        ScenarioObjectiveStatus objectiveStatus
    ) {
        this.slotNumber = slotNumber;
        this.savedAtEpochMillis = savedAtEpochMillis;
        this.gameVersion = gameVersion;
        this.scenarioId = scenarioId;
        this.playerFactionId = playerFactionId;
        this.capitalCityId = capitalCityId;
        this.currentYear = currentYear;
        this.currentMonth = currentMonth;
        this.currentTurn = currentTurn;
        this.ownedCityCount = ownedCityCount;
        this.objectiveStatus = objectiveStatus;
    }

    public static SaveSlotMetadata fromDocument(
        int slotNumber,
        SaveGameDocument saveGameDocument
    ) {
        GameState gameState = saveGameDocument.gameState;
        String capitalCityId = gameState.requirePlayerFactionState().active
            ? gameState.requirePlayerFactionState().capitalCityId
            : "";
        return new SaveSlotMetadata(
            slotNumber,
            saveGameDocument.savedAtEpochMillis,
            saveGameDocument.gameVersion,
            gameState.scenarioId,
            gameState.playerFactionId,
            capitalCityId,
            gameState.currentYear,
            gameState.currentMonth,
            gameState.currentTurn,
            gameState.findCitiesOwnedBy(gameState.playerFactionId).size(),
            gameState.scenarioObjectiveStatus
        );
    }

    public int getSlotNumber() {
        return slotNumber;
    }

    public long getSavedAtEpochMillis() {
        return savedAtEpochMillis;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public String getPlayerFactionId() {
        return playerFactionId;
    }

    public String getCapitalCityId() {
        return capitalCityId;
    }

    public int getCurrentYear() {
        return currentYear;
    }

    public int getCurrentMonth() {
        return currentMonth;
    }

    public int getCurrentTurn() {
        return currentTurn;
    }

    public int getOwnedCityCount() {
        return ownedCityCount;
    }

    public ScenarioObjectiveStatus getObjectiveStatus() {
        return objectiveStatus;
    }
}
