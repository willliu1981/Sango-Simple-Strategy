package idv.kuan.studio.sango.application.command;

import idv.kuan.studio.sango.application.result.TurnResolutionResult;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.service.TurnResolutionService;
import idv.kuan.studio.sango.repository.SaveGameRepository;
import idv.kuan.studio.sango.repository.save.SaveTarget;

/**
 * 完成某月份結算，且只有保存成功才回傳新狀態與報告。
 */
public final class EndTurnCommand {
    private final SaveGameRepository saveGameRepository;
    private final TurnResolutionService turnResolutionService;

    public EndTurnCommand(
        SaveGameRepository saveGameRepository,
        TurnResolutionService turnResolutionService
    ) {
        this.saveGameRepository = saveGameRepository;
        this.turnResolutionService = turnResolutionService;
    }

    public TurnResolutionResult execute(int slotNumber, GameState currentState) {
        GameStateValidator.validate(currentState);
        TurnResolutionResult resolutionResult = turnResolutionService.resolve(currentState);
        saveGameRepository.save(SaveTarget.auto(slotNumber), resolutionResult.getGameState());
        return resolutionResult;
    }
}
