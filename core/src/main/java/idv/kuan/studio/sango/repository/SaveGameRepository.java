package idv.kuan.studio.sango.repository;

import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.repository.save.SaveSlotInspection;

/**
 * 戰局存檔介面。UI 目前提供三個槽位，Repository 仍保留可擴充的槽位參數。
 */
public interface SaveGameRepository {
    SaveSlotInspection inspect(int slotNumber);

    GameState load(int slotNumber);

    void save(int slotNumber, GameState gameState);

    void delete(int slotNumber);
}
