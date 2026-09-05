package idv.kuan.studio.sango.application.command;

import idv.kuan.studio.sango.application.result.DomesticActionResult;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.rule.DomesticActionFailureReason;
import idv.kuan.studio.sango.domain.rule.DomesticActionRules;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;
import idv.kuan.studio.sango.repository.SaveGameRepository;

/**
 * 以 copy-on-write 方式執行內政命令；只有保存成功才回傳新狀態。
 */
public final class ExecuteDomesticActionCommand {
    private final SaveGameRepository saveGameRepository;

    public ExecuteDomesticActionCommand(SaveGameRepository saveGameRepository) {
        this.saveGameRepository = saveGameRepository;
    }

    public DomesticActionResult execute(
        int slotNumber,
        GameState currentState,
        DomesticActionType actionType
    ) {
        DomesticActionFailureReason failureReason = DomesticActionRules.evaluate(
            currentState,
            actionType
        );
        if (failureReason != DomesticActionFailureReason.NONE) {
            return DomesticActionResult.failure(actionType, failureReason);
        }

        GameState nextState = currentState.copy();
        applyAction(nextState, actionType);
        GameStateValidator.validate(nextState);
        saveGameRepository.save(slotNumber, nextState);
        return DomesticActionResult.success(actionType, nextState);
    }

    private void applyAction(GameState gameState, DomesticActionType actionType) {
        FactionState factionState = gameState.requirePlayerFactionState();
        CityState cityState = gameState.requireCapitalCityState();

        gameState.actionPointsRemaining -= actionType.getActionPointCost();
        gameState.lastActionCode = actionType.name();

        factionState.gold -= actionType.getGoldCost();
        factionState.food -= actionType.getFoodCost();
        factionState.gold += actionType.getGoldGain();
        factionState.food += actionType.getFoodGain();

        cityState.troops += actionType.getTroopGain();
        cityState.population += actionType.getPopulationDelta();
        cityState.agriculture = clampToPercentage(
            cityState.agriculture + actionType.getAgricultureGain()
        );
        cityState.commerce = clampToPercentage(
            cityState.commerce + actionType.getCommerceGain()
        );
        cityState.training = clampToPercentage(
            cityState.training + actionType.getTrainingGain()
        );
    }

    private int clampToPercentage(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
