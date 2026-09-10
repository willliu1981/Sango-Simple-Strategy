package idv.kuan.studio.sango.repository.save;

/** 一個槽位內的具名存檔目標。 */
public record SaveTarget(int slotNumber, SaveKind kind) {
    public SaveTarget {
        if (slotNumber < 1 || slotNumber > 99) {
            throw new IllegalArgumentException("slotNumber 必須介於 1 到 99。");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind 不可為 null。");
        }
    }

    public static SaveTarget manual(int slotNumber) {
        return new SaveTarget(slotNumber, SaveKind.MANUAL);
    }

    public static SaveTarget auto(int slotNumber) {
        return new SaveTarget(slotNumber, SaveKind.AUTO);
    }
}
