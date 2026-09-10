package idv.kuan.studio.sango.runtime;

import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.ui.id.ScreenId;

/**
 * 保存目前記憶體中的戰局與不需寫入存檔的 UI 選取狀態。
 */
public final class GameSession {
    private GameState currentState;
    private int currentSaveSlot;
    private String selectedCityId;
    private TurnResolutionReport lastTurnReport;
    private String selectedBattleReportId;
    private ScreenId battleReportReturnScreen = ScreenId.STRATEGIC_MAP;
    private ScreenId monthReportReturnScreen = ScreenId.STRATEGIC_MAP;
    private boolean monthReportWorldView;
    private ScreenId settingsReturnScreen = ScreenId.STRATEGIC_MAP;
    private ScreenId saveLoadReturnScreen = ScreenId.LOBBY;
    private SaveLoadMode saveLoadMode = SaveLoadMode.LOAD;
    private ScreenId musicPlayerReturnScreen = ScreenId.LOBBY;
    private boolean strategicMapFocusRequested;

    public boolean hasCurrentState() {
        return currentState != null;
    }

    public GameState requireCurrentState() {
        if (currentState == null) {
            throw new IllegalStateException("目前沒有已載入的戰局。");
        }
        return currentState;
    }

    public void setCurrentState(GameState gameState) {
        int slotNumber = currentSaveSlot > 0 ? currentSaveSlot : 1;
        setCurrentState(slotNumber, gameState);
    }

    public void setCurrentState(int slotNumber, GameState gameState) {
        if (slotNumber < 1 || slotNumber > SangoServices.SAVE_SLOT_COUNT) {
            throw new IllegalArgumentException("slotNumber 必須介於 1 到 3。");
        }
        GameStateValidator.validate(gameState);
        boolean changedSaveSlot = currentState == null || currentSaveSlot != slotNumber;
        currentSaveSlot = slotNumber;
        currentState = gameState;
        selectedCityId = gameState.findCityState(gameState.strategicMapFocusedCityId) != null
            ? gameState.strategicMapFocusedCityId : resolveDefaultSelectedCity(gameState);
        gameState.strategicMapFocusedCityId = selectedCityId;
        if (changedSaveSlot) {
            lastTurnReport = null;
            selectedBattleReportId = null;
            requestStrategicMapFocus();
        }
    }

    public int getCurrentSaveSlot() {
        if (currentSaveSlot < 1) {
            throw new IllegalStateException("目前沒有對應的存檔槽。");
        }
        return currentSaveSlot;
    }

    public String getSelectedCityId() {
        return selectedCityId;
    }

    public void setSelectedCityId(String selectedCityId) {
        if (selectedCityId == null || selectedCityId.trim().isEmpty()) {
            throw new IllegalArgumentException("selectedCityId 不可為空。");
        }
        if (currentState != null && currentState.findCityState(selectedCityId) == null) {
            throw new IllegalArgumentException("選取了戰局中不存在的城池：" + selectedCityId);
        }
        this.selectedCityId = selectedCityId;
        if (currentState != null) {
            currentState.strategicMapFocusedCityId = selectedCityId;
        }
    }

    public TurnResolutionReport getLastTurnReport() {
        return lastTurnReport;
    }

    public void setLastTurnReport(TurnResolutionReport lastTurnReport) {
        this.lastTurnReport = lastTurnReport;
    }

    public String getSelectedBattleReportId() {
        return selectedBattleReportId;
    }

    public void openBattleReport(String battleReportId, ScreenId returnScreen) {
        if (battleReportId == null || battleReportId.trim().isEmpty()) {
            throw new IllegalArgumentException("battleReportId 不可為空。");
        }
        if (returnScreen == null) {
            throw new IllegalArgumentException("returnScreen 不可為 null。");
        }
        selectedBattleReportId = battleReportId;
        battleReportReturnScreen = returnScreen;
    }

    public ScreenId getBattleReportReturnScreen() {
        return battleReportReturnScreen;
    }

    public void openMonthReport(TurnResolutionReport turnResolutionReport, ScreenId returnScreen) {
        if (turnResolutionReport == null || returnScreen == null) {
            throw new IllegalArgumentException("turnResolutionReport 與 returnScreen 不可為 null。");
        }
        lastTurnReport = turnResolutionReport;
        monthReportReturnScreen = returnScreen;
        monthReportWorldView = false;
    }

    public ScreenId getMonthReportReturnScreen() {
        return monthReportReturnScreen;
    }

    public boolean isMonthReportWorldView() {
        return monthReportWorldView;
    }

    public void setMonthReportWorldView(boolean monthReportWorldView) {
        this.monthReportWorldView = monthReportWorldView;
    }

    public void openSettings(ScreenId returnScreen) {
        if (returnScreen == null) {
            throw new IllegalArgumentException("returnScreen 不可為 null。");
        }
        settingsReturnScreen = returnScreen;
    }

    public ScreenId getSettingsReturnScreen() {
        return settingsReturnScreen;
    }

    public void openSaveLoad(SaveLoadMode mode, ScreenId returnScreen) {
        if (mode == null || returnScreen == null) {
            throw new IllegalArgumentException("mode 與 returnScreen 不可為 null。");
        }
        saveLoadMode = mode;
        saveLoadReturnScreen = returnScreen;
    }

    public SaveLoadMode getSaveLoadMode() {
        return saveLoadMode;
    }

    public ScreenId getSaveLoadReturnScreen() {
        return saveLoadReturnScreen;
    }

    public void openMusicPlayer(ScreenId returnScreen) {
        if (returnScreen != ScreenId.LOBBY && returnScreen != ScreenId.LOBBY_SETTINGS
            && returnScreen != ScreenId.SETTINGS) {
            throw new IllegalArgumentException("音樂鑑賞只能由主選單或設定畫面開啟。");
        }
        musicPlayerReturnScreen = returnScreen;
    }

    public ScreenId getMusicPlayerReturnScreen() {
        return musicPlayerReturnScreen;
    }

    public void requestStrategicMapFocus() {
        strategicMapFocusRequested = true;
    }

    public boolean consumeStrategicMapFocusRequest() {
        boolean requested = strategicMapFocusRequested;
        strategicMapFocusRequested = false;
        return requested;
    }

    public void clear() {
        currentState = null;
        currentSaveSlot = 0;
        selectedCityId = null;
        lastTurnReport = null;
        selectedBattleReportId = null;
        battleReportReturnScreen = ScreenId.STRATEGIC_MAP;
        monthReportReturnScreen = ScreenId.STRATEGIC_MAP;
        monthReportWorldView = false;
        settingsReturnScreen = ScreenId.STRATEGIC_MAP;
        saveLoadReturnScreen = ScreenId.LOBBY;
        saveLoadMode = SaveLoadMode.LOAD;
        musicPlayerReturnScreen = ScreenId.LOBBY;
        strategicMapFocusRequested = false;
    }

    private String resolveDefaultSelectedCity(GameState gameState) {
        if (gameState.requirePlayerFactionState().active) {
            return gameState.requirePlayerFactionState().capitalCityId;
        }
        if (gameState.cityStates != null) {
            for (CityState cityState : gameState.cityStates) {
                if (cityState != null) {
                    return cityState.cityId;
                }
            }
        }
        return gameState.victoryTargetCityId;
    }
}
