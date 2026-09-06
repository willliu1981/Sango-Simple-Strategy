package idv.kuan.studio.sango.runtime;

import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.rule.BattleTactic;

/**
 * 保存目前記憶體中的戰局與不需寫入存檔的 UI 選取狀態。
 */
public final class GameSession {
    private GameState currentState;
    private String selectedCityId;
    private BattleTactic selectedBattleTactic = BattleTactic.BALANCED;

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
        GameStateValidator.validate(gameState);
        currentState = gameState;
        if (selectedCityId == null || gameState.findCityState(selectedCityId) == null) {
            selectedCityId = gameState.requirePlayerFactionState().active
                ? gameState.requirePlayerFactionState().capitalCityId
                : gameState.victoryTargetCityId;
        }
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
    }

    public BattleTactic getSelectedBattleTactic() {
        return selectedBattleTactic;
    }

    public void setSelectedBattleTactic(BattleTactic selectedBattleTactic) {
        if (selectedBattleTactic == null) {
            throw new IllegalArgumentException("selectedBattleTactic 不可為 null。");
        }
        this.selectedBattleTactic = selectedBattleTactic;
    }

    public void clear() {
        currentState = null;
        selectedCityId = null;
        selectedBattleTactic = BattleTactic.BALANCED;
    }
}
