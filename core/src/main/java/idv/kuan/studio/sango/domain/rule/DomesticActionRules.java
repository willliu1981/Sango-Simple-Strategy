package idv.kuan.studio.sango.domain.rule;

import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;

/**
 * 內政命令的可執行性判斷。Command 與 UI 共用，避免兩邊規則不一致。
 */
public final class DomesticActionRules {
    private DomesticActionRules() {
    }

    public static DomesticActionFailureReason evaluate(
        GameState gameState,
        DomesticActionType actionType
    ) {
        GameStateValidator.validate(gameState);
        if (actionType == null) {
            throw new IllegalArgumentException("actionType 不可為 null。");
        }

        if (gameState.actionPointsRemaining < actionType.getActionPointCost()) {
            return DomesticActionFailureReason.NO_ACTION_POINTS;
        }

        FactionState factionState = gameState.requirePlayerFactionState();
        if (factionState.gold < actionType.getGoldCost()) {
            return DomesticActionFailureReason.INSUFFICIENT_GOLD;
        }
        if (factionState.food < actionType.getFoodCost()) {
            return DomesticActionFailureReason.INSUFFICIENT_FOOD;
        }

        CityState cityState = gameState.requireCapitalCityState();
        if (cityState.population + actionType.getPopulationDelta() < 0) {
            return DomesticActionFailureReason.INSUFFICIENT_POPULATION;
        }

        return DomesticActionFailureReason.NONE;
    }
}
