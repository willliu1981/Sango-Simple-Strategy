package idv.kuan.studio.sango.application.command;

import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.repository.SaveGameRepository;

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
        saveGameRepository.save(slotNumber, gameState);
    }
}
