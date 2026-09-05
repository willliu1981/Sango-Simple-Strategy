package idv.kuan.studio.sango.repository;

import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.repository.save.SaveSlotInspection;

/**
 * 戰局存檔介面。第一版 UI 使用 slot 1，但介面保留槽位參數。
 */
public interface SaveGameRepository {
    SaveSlotInspection inspect(int slotNumber);

    GameState load(int slotNumber);

    void save(int slotNumber, GameState gameState);

    void delete(int slotNumber);
}
