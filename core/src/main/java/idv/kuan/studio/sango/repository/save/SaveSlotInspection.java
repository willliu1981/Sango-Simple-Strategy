package idv.kuan.studio.sango.repository.save;

/**
 * Lobby 用來決定是否開放「繼續遊戲」的輕量檢查結果。
 */
public final class SaveSlotInspection {
    private final SaveSlotState state;
    private final boolean recoveryCandidate;
    private final String diagnosticMessage;

    private SaveSlotInspection(
        SaveSlotState state,
        boolean recoveryCandidate,
        String diagnosticMessage
    ) {
        this.state = state;
        this.recoveryCandidate = recoveryCandidate;
        this.diagnosticMessage = diagnosticMessage;
    }

    public static SaveSlotInspection empty() {
        return new SaveSlotInspection(SaveSlotState.EMPTY, false, "");
    }

    public static SaveSlotInspection available(boolean recoveryCandidate) {
        return new SaveSlotInspection(SaveSlotState.AVAILABLE, recoveryCandidate, "");
    }

    public static SaveSlotInspection corrupt(String diagnosticMessage) {
        return new SaveSlotInspection(
            SaveSlotState.CORRUPT,
            false,
            diagnosticMessage == null ? "" : diagnosticMessage
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
}
