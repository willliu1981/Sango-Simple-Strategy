package idv.kuan.studio.sango.application.command;

import idv.kuan.studio.sango.application.result.DomesticActionResult;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.rule.CampaignBalance;
import idv.kuan.studio.sango.domain.rule.DomesticActionFailureReason;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;
import idv.kuan.studio.sango.domain.rule.OfficerCommandProfile;
import idv.kuan.studio.sango.domain.service.DomesticActionService;
import idv.kuan.studio.sango.repository.SaveGameRepository;

/** 保存成功才交付工作副本；取消、非法數量及保存失敗均不扣原狀態資源。 */
public final class ExecuteDomesticActionCommand {
    private final SaveGameRepository saveGameRepository;
    private final DomesticActionService domesticActionService = new DomesticActionService();

    public ExecuteDomesticActionCommand(SaveGameRepository saveGameRepository) {
        this.saveGameRepository = saveGameRepository;
    }

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
        saveGameRepository.save(slotNumber, nextState);
        return DomesticActionResult.success(actionType, nextState);
    }
}
