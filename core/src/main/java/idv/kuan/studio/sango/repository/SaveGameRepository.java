package idv.kuan.studio.sango.repository;

import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.repository.save.SaveSlotInspection;
import idv.kuan.studio.sango.repository.save.SaveTarget;

/**
 * 戰局存檔介面。UI 目前提供三個槽位，Repository 仍保留可擴充的槽位參數。
 */
public interface SaveGameRepository {
    SaveSlotInspection inspect(SaveTarget target);

    default SaveSlotInspection inspect(int slotNumber) {
        return inspect(SaveTarget.manual(slotNumber));
    }

    default SaveSlotInspection inspectNewest(int slotNumber) {
        SaveSlotInspection manual = inspect(SaveTarget.manual(slotNumber));
        SaveSlotInspection auto = inspect(SaveTarget.auto(slotNumber));
        if (!manual.isAvailable()) return auto;
        if (!auto.isAvailable()) return manual;
        return auto.getMetadata().getSavedAtEpochMillis()
            > manual.getMetadata().getSavedAtEpochMillis() ? auto : manual;
    }

    GameState load(SaveTarget target);

    default GameState load(int slotNumber) {
        return load(SaveTarget.manual(slotNumber));
    }

    default GameState loadNewest(int slotNumber) {
        SaveSlotInspection newest = inspectNewest(slotNumber);
        if (!newest.isAvailable()) {
            throw new IllegalStateException("槽位沒有可讀取的存檔：" + slotNumber);
        }
        return load(new SaveTarget(slotNumber, newest.getMetadata().getSaveKind()));
    }

    void save(SaveTarget target, GameState gameState);

    default void save(int slotNumber, GameState gameState) {
        save(SaveTarget.manual(slotNumber), gameState);
    }

    void delete(SaveTarget target);

    default void delete(int slotNumber) {
        delete(SaveTarget.manual(slotNumber));
        delete(SaveTarget.auto(slotNumber));
    }
}
