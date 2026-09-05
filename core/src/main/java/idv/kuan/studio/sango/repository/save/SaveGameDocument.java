package idv.kuan.studio.sango.repository.save;

import idv.kuan.studio.sango.domain.model.GameState;

/**
 * 寫入磁碟的存檔外層格式。
 */
public final class SaveGameDocument {
    public int schemaVersion;
    public String gameVersion;
    public long savedAtEpochMillis;
    public GameState gameState;

    public SaveGameDocument() {
    }
}
