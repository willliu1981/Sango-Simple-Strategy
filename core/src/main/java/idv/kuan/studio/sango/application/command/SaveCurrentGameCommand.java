package idv.kuan.studio.sango.application.command;

import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.repository.SaveGameRepository;
import idv.kuan.studio.sango.repository.save.SaveTarget;
import idv.kuan.studio.sango.repository.save.SaveSlotInspection;
import idv.kuan.studio.sango.repository.save.SaveSlotState;

/**
 * 手動保存目前戰局。
 */
public final class SaveCurrentGameCommand {
    private final SaveGameRepository saveGameRepository;

    public SaveCurrentGameCommand(SaveGameRepository saveGameRepository) {
        this.saveGameRepository = saveGameRepository;
    }

    public void execute(int slotNumber, GameState gameState) {
        GameStateValidator.validate(gameState);
        SaveTarget autoTarget = SaveTarget.auto(slotNumber);
        SaveSlotInspection autoInspection = saveGameRepository.inspect(autoTarget);
        if (autoInspection.getState() != SaveSlotState.EMPTY
            && (!autoInspection.isAvailable()
                || !gameState.campaignInstanceId.equals(
                    autoInspection.getMetadata().getCampaignInstanceId()))) {
            saveGameRepository.delete(autoTarget);
        }
        saveGameRepository.save(SaveTarget.manual(slotNumber), gameState);
    }

    /** 返回大廳或離開時保存進度，但不覆寫玩家的手動存檔。 */
    public void executeAuto(int slotNumber, GameState gameState) {
        GameStateValidator.validate(gameState);
        saveGameRepository.save(SaveTarget.auto(slotNumber), gameState);
    }
}
