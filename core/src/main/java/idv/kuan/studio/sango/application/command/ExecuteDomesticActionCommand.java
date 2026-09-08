package idv.kuan.studio.sango.application.command;

import idv.kuan.studio.sango.application.result.DomesticActionResult;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.rule.CampaignBalance;
import idv.kuan.studio.sango.domain.rule.DomesticActionFailureReason;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;
import idv.kuan.studio.sango.domain.rule.OfficerCommandProfile;
import idv.kuan.studio.sango.domain.service.DomesticActionService;

/** 在工作副本上執行內政；實際存檔由明確的存檔時機負責。 */
public final class ExecuteDomesticActionCommand {
    private final DomesticActionService domesticActionService = new DomesticActionService();

    /** 保留既有呼叫入口；徵兵 UI 必須使用帶數量的版本。 */
    public DomesticActionResult execute(int slotNumber, GameState currentState, String cityId, DomesticActionType actionType) {
        return execute(slotNumber, currentState, cityId, actionType, CampaignBalance.DEFAULT_RECRUIT_AMOUNT);
    }

    public DomesticActionResult execute(
        int slotNumber, GameState currentState, String cityId,
        DomesticActionType actionType, int recruitmentAmount
    ) {
        GameStateValidator.validate(currentState);
        GameState nextState = currentState.copy();
        DomesticActionFailureReason failure = domesticActionService.apply(nextState,
            currentState.playerFactionId, cityId, actionType, recruitmentAmount, OfficerCommandProfile.DEFAULT);
        if (failure != DomesticActionFailureReason.NONE) {
            return DomesticActionResult.failure(actionType, failure);
        }
        nextState.lastActionCode = actionType.name();
        GameStateValidator.validate(nextState);
        return DomesticActionResult.success(actionType, nextState);
    }
}
