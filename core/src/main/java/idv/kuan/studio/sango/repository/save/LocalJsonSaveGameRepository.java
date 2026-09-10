package idv.kuan.studio.sango.repository.save;

import java.util.Locale;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import idv.kuan.studio.sango.SangoVersion;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.repository.SaveGameRepository;

/**
 * 使用 LibGDX FileHandle 的本機 JSON 存檔。
 *
 * 寫入流程採 temp -> validate -> backup -> replace，並可在主要檔案損壞時
 * 從完整的 temp 或 backup 載入，避免單次中斷直接毀掉唯一存檔。
 */
public final class LocalJsonSaveGameRepository implements SaveGameRepository {
    private static final String DESKTOP_SAVE_DIRECTORY = ".sango/save";
    private static final String MOBILE_SAVE_DIRECTORY = "save";

    private final FileHandle saveDirectory;
    private final Json json;
    private final GameStateMigrator gameStateMigrator;

    public LocalJsonSaveGameRepository() {
        this(resolveDefaultSaveDirectory());
    }

    public LocalJsonSaveGameRepository(FileHandle saveDirectory) {
        if (saveDirectory == null) {
            throw new IllegalArgumentException("saveDirectory 不可為 null。");
        }
        this.saveDirectory = saveDirectory;
        this.json = createJson();
        this.gameStateMigrator = new GameStateMigrator();
    }

    @Override
    public SaveSlotInspection inspect(SaveTarget target) {
        validateTarget(target);

        CandidateRead primaryRead = readCandidate(primaryFile(target), target);
        if (primaryRead.isValid()) {
            return SaveSlotInspection.available(
                false,
                SaveSlotMetadata.fromDocument(target.slotNumber(), primaryRead.document())
            );
        }

        CandidateRead temporaryRead = readCandidate(temporaryFile(target), target);
        if (temporaryRead.isValid()) {
            return SaveSlotInspection.available(
                true,
                SaveSlotMetadata.fromDocument(target.slotNumber(), temporaryRead.document())
            );
        }

        CandidateRead backupRead = readCandidate(backupFile(target), target);
        if (backupRead.isValid()) {
            return SaveSlotInspection.available(
                true,
                SaveSlotMetadata.fromDocument(target.slotNumber(), backupRead.document())
            );
        }

        if (!primaryRead.exists() && !temporaryRead.exists() && !backupRead.exists()) {
            return SaveSlotInspection.empty();
        }

        return SaveSlotInspection.corrupt(
            joinDiagnostics(primaryRead, temporaryRead, backupRead)
        );
    }

    @Override
    public GameState load(SaveTarget target) {
        validateTarget(target);

        CandidateRead primaryRead = readCandidate(primaryFile(target), target);
        if (primaryRead.isValid()) {
            return primaryRead.document().gameState.copy();
        }

        CandidateRead temporaryRead = readCandidate(temporaryFile(target), target);
        if (temporaryRead.isValid()) {
            return temporaryRead.document().gameState.copy();
        }

        CandidateRead backupRead = readCandidate(backupFile(target), target);
        if (backupRead.isValid()) {
            return backupRead.document().gameState.copy();
        }

        throw new SaveGameException(
            "無法讀取存檔槽 " + target.slotNumber() + "（" + target.kind() + "）："
                + joinDiagnostics(primaryRead, temporaryRead, backupRead)
        );
    }

    @Override
    public void save(SaveTarget target, GameState gameState) {
        validateTarget(target);
        GameStateValidator.validate(gameState);
        ensureSaveDirectory();

        FileHandle primaryFile = primaryFile(target);
        FileHandle temporaryFile = temporaryFile(target);
        FileHandle backupFile = backupFile(target);
        FileHandle corruptFile = corruptFile(target);

        SaveGameDocument saveGameDocument = new SaveGameDocument();
        saveGameDocument.schemaVersion = SangoVersion.SAVE_DOCUMENT_SCHEMA_VERSION;
        saveGameDocument.gameVersion = SangoVersion.GAME_VERSION;
        saveGameDocument.savedAtEpochMillis = System.currentTimeMillis();
        saveGameDocument.saveKind = target.kind();
        saveGameDocument.originSlotNumber = target.slotNumber();
        saveGameDocument.campaignInstanceId = gameState.campaignInstanceId;
        saveGameDocument.gameState = gameState.copy();

        try {
            deleteIfExists(temporaryFile);
            temporaryFile.writeString(json.prettyPrint(saveGameDocument), false, "UTF-8");
            readRequired(temporaryFile, target);

            CandidateRead existingPrimary = readCandidate(primaryFile, target);
            if (existingPrimary.isValid()) {
                deleteIfExists(backupFile);
                primaryFile.copyTo(backupFile);
                readRequired(backupFile, target);
            } else if (primaryFile.exists()) {
                deleteIfExists(corruptFile);
                primaryFile.copyTo(corruptFile);
            }

            deleteIfExists(primaryFile);
            temporaryFile.moveTo(primaryFile);
            readRequired(primaryFile, target);
        } catch (RuntimeException exception) {
            restoreBackupWhenPrimaryMissing(primaryFile, backupFile, target);
            throw new SaveGameException(
                "寫入存檔槽 " + target.slotNumber() + "（" + target.kind() + "）失敗。",
                exception
            );
        } finally {
            deleteIfExists(temporaryFile);
        }
    }

    @Override
    public void delete(SaveTarget target) {
        validateTarget(target);
        deleteIfExists(primaryFile(target));
        deleteIfExists(temporaryFile(target));
        deleteIfExists(backupFile(target));
        deleteIfExists(corruptFile(target));
    }

    public FileHandle getSaveDirectory() {
        return saveDirectory;
    }

    private static FileHandle resolveDefaultSaveDirectory() {
        if (Gdx.app == null || Gdx.files == null) {
            throw new IllegalStateException("LibGDX 尚未初始化，無法決定存檔目錄。");
        }

        Application.ApplicationType applicationType = Gdx.app.getType();
        if (applicationType == Application.ApplicationType.Desktop
            || applicationType == Application.ApplicationType.HeadlessDesktop) {
            return Gdx.files.external(DESKTOP_SAVE_DIRECTORY);
        }
        return Gdx.files.local(MOBILE_SAVE_DIRECTORY);
    }

    private Json createJson() {
        Json configuredJson = new Json();
        configuredJson.setIgnoreUnknownFields(false);
        configuredJson.setUsePrototypes(false);
        configuredJson.setOutputType(JsonWriter.OutputType.json);
        return configuredJson;
    }

    private void ensureSaveDirectory() {
        if (!saveDirectory.exists()) {
            saveDirectory.mkdirs();
        }
        if (!saveDirectory.exists() || !saveDirectory.isDirectory()) {
            throw new SaveGameException("無法建立存檔目錄：" + saveDirectory.path());
        }
    }

    private CandidateRead readCandidate(FileHandle candidateFile, SaveTarget target) {
        if (!candidateFile.exists()) {
            return CandidateRead.missing(candidateFile.path());
        }

        try {
            SaveGameDocument saveGameDocument = json.fromJson(
                SaveGameDocument.class,
                candidateFile
            );
            validateDocument(saveGameDocument, target);
            return CandidateRead.valid(candidateFile.path(), saveGameDocument);
        } catch (RuntimeException exception) {
            return CandidateRead.invalid(candidateFile.path(), exception.getMessage());
        }
    }

    private SaveGameDocument readRequired(FileHandle candidateFile, SaveTarget target) {
        CandidateRead candidateRead = readCandidate(candidateFile, target);
        if (!candidateRead.isValid()) {
            throw new SaveGameException(
                "存檔驗證失敗：" + candidateRead.path() + "；" + candidateRead.diagnostic()
            );
        }
        return candidateRead.document();
    }

    private void validateDocument(SaveGameDocument saveGameDocument, SaveTarget target) {
        if (saveGameDocument == null) {
            throw new IllegalArgumentException("SaveGameDocument 不可為 null。");
        }
        if (saveGameDocument.schemaVersion < 1
            || saveGameDocument.schemaVersion > SangoVersion.SAVE_DOCUMENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                "不支援的 SaveGame schemaVersion：" + saveGameDocument.schemaVersion
            );
        }
        if (saveGameDocument.schemaVersion == 1) {
            if (target.kind() != SaveKind.MANUAL) {
                throw new IllegalArgumentException("舊版存檔只能作為手動存檔載入。");
            }
            saveGameDocument.saveKind = SaveKind.MANUAL;
            saveGameDocument.originSlotNumber = target.slotNumber();
        } else if (saveGameDocument.saveKind != target.kind()
            || saveGameDocument.originSlotNumber != target.slotNumber()) {
            throw new IllegalArgumentException("存檔種類或來源槽位與檔名不符。");
        }
        if (saveGameDocument.gameVersion == null || saveGameDocument.gameVersion.trim().isEmpty()) {
            throw new IllegalArgumentException("SaveGame.gameVersion 不可為空。");
        }
        saveGameDocument.gameState = gameStateMigrator.migrate(saveGameDocument.gameState);
        if (saveGameDocument.campaignInstanceId == null
            || saveGameDocument.campaignInstanceId.isBlank()) {
            saveGameDocument.campaignInstanceId = saveGameDocument.gameState.campaignInstanceId;
        }
        if (!saveGameDocument.campaignInstanceId.equals(
            saveGameDocument.gameState.campaignInstanceId)) {
            throw new IllegalArgumentException("存檔戰局識別不一致。");
        }
        GameStateValidator.validate(saveGameDocument.gameState);
    }

    private void restoreBackupWhenPrimaryMissing(
        FileHandle primaryFile,
        FileHandle backupFile,
        SaveTarget target
    ) {
        if (primaryFile.exists() || !backupFile.exists()) {
            return;
        }
        try {
            CandidateRead backupRead = readCandidate(backupFile, target);
            if (backupRead.isValid()) {
                backupFile.copyTo(primaryFile);
            }
        } catch (RuntimeException exception) {
            logError("無法由備份還原主要存檔。", exception);
        }
    }

    private String joinDiagnostics(CandidateRead... candidateReads) {
        StringBuilder diagnosticBuilder = new StringBuilder();
        for (CandidateRead candidateRead : candidateReads) {
            if (!candidateRead.exists()) {
                continue;
            }
            if (diagnosticBuilder.length() > 0) {
                diagnosticBuilder.append(" | ");
            }
            diagnosticBuilder
                .append(candidateRead.path())
                .append(": ")
                .append(candidateRead.diagnostic());
        }
        if (diagnosticBuilder.length() == 0) {
            return "沒有可用的存檔候選檔案。";
        }
        return diagnosticBuilder.toString();
    }

    private void validateSlotNumber(int slotNumber) {
        if (slotNumber < 1 || slotNumber > 99) {
            throw new IllegalArgumentException("slotNumber 必須介於 1 到 99。");
        }
    }

    private void validateTarget(SaveTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("target 不可為 null。");
        }
        validateSlotNumber(target.slotNumber());
    }

    private FileHandle primaryFile(SaveTarget target) {
        return saveDirectory.child(filePrefix(target) + ".json");
    }

    private FileHandle temporaryFile(SaveTarget target) {
        return saveDirectory.child(filePrefix(target) + ".tmp");
    }

    private FileHandle backupFile(SaveTarget target) {
        return saveDirectory.child(filePrefix(target) + ".backup.json");
    }

    private FileHandle corruptFile(SaveTarget target) {
        return saveDirectory.child(filePrefix(target) + ".corrupt.json");
    }

    private String filePrefix(SaveTarget target) {
        String suffix = target.kind() == SaveKind.AUTO ? ".autosave" : "";
        return String.format(Locale.ROOT, "slot-%02d%s", target.slotNumber(), suffix);
    }

    private void deleteIfExists(FileHandle fileHandle) {
        if (fileHandle.exists() && !fileHandle.delete()) {
            throw new SaveGameException("無法刪除檔案：" + fileHandle.path());
        }
    }

    private void logError(String message, Throwable throwable) {
        if (Gdx.app != null) {
            Gdx.app.error("SaveGame", message, throwable);
        }
    }

    private static final class CandidateRead {
        private final String path;
        private final boolean exists;
        private final SaveGameDocument document;
        private final String diagnostic;

        private CandidateRead(
            String path,
            boolean exists,
            SaveGameDocument document,
            String diagnostic
        ) {
            this.path = path;
            this.exists = exists;
            this.document = document;
            this.diagnostic = diagnostic;
        }

        private static CandidateRead missing(String path) {
            return new CandidateRead(path, false, null, "檔案不存在");
        }

        private static CandidateRead valid(String path, SaveGameDocument document) {
            return new CandidateRead(path, true, document, "OK");
        }

        private static CandidateRead invalid(String path, String diagnostic) {
            String safeDiagnostic = diagnostic == null ? "未知格式錯誤" : diagnostic;
            return new CandidateRead(path, true, null, safeDiagnostic);
        }

        private String path() {
            return path;
        }

        private boolean exists() {
            return exists;
        }

        private SaveGameDocument document() {
            return document;
        }

        private String diagnostic() {
            return diagnostic;
        }

        private boolean isValid() {
            return document != null;
        }
    }
}
