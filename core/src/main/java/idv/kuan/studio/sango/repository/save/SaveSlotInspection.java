package idv.kuan.studio.sango.repository.save;

/**
 * Lobby 與存讀檔畫面使用的槽位檢查結果。
 */
public final class SaveSlotInspection {
    private final SaveSlotState state;
    private final boolean recoveryCandidate;
    private final String diagnosticMessage;
    private final SaveSlotMetadata metadata;

    private SaveSlotInspection(
        SaveSlotState state,
        boolean recoveryCandidate,
        String diagnosticMessage,
        SaveSlotMetadata metadata
    ) {
        this.state = state;
        this.recoveryCandidate = recoveryCandidate;
        this.diagnosticMessage = diagnosticMessage;
        this.metadata = metadata;
    }

    public static SaveSlotInspection empty() {
        return new SaveSlotInspection(SaveSlotState.EMPTY, false, "", null);
    }

    public static SaveSlotInspection available(
        boolean recoveryCandidate,
        SaveSlotMetadata metadata
    ) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata 不可為 null。");
        }
        return new SaveSlotInspection(
            SaveSlotState.AVAILABLE,
            recoveryCandidate,
            "",
            metadata
        );
    }

    public static SaveSlotInspection corrupt(String diagnosticMessage) {
        return new SaveSlotInspection(
            SaveSlotState.CORRUPT,
            false,
            diagnosticMessage == null ? "" : diagnosticMessage,
            null
        );
    }

    public SaveSlotState getState() {
        return state;
    }

    public boolean isAvailable() {
        return state == SaveSlotState.AVAILABLE;
    }

    public boolean hasRecoveryCandidate() {
        return recoveryCandidate;
    }

    public String getDiagnosticMessage() {
        return diagnosticMessage;
    }

    public SaveSlotMetadata getMetadata() {
        return metadata;
    }
}
