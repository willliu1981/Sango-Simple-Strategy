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
    public SaveSlotInspection inspect(int slotNumber) {
        validateSlotNumber(slotNumber);

        CandidateRead primaryRead = readCandidate(primaryFile(slotNumber));
        if (primaryRead.isValid()) {
            return SaveSlotInspection.available(
                false,
                SaveSlotMetadata.fromDocument(slotNumber, primaryRead.document())
            );
        }

        CandidateRead temporaryRead = readCandidate(temporaryFile(slotNumber));
        if (temporaryRead.isValid()) {
            return SaveSlotInspection.available(
                true,
                SaveSlotMetadata.fromDocument(slotNumber, temporaryRead.document())
            );
        }

        CandidateRead backupRead = readCandidate(backupFile(slotNumber));
        if (backupRead.isValid()) {
            return SaveSlotInspection.available(
                true,
                SaveSlotMetadata.fromDocument(slotNumber, backupRead.document())
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
    public GameState load(int slotNumber) {
        validateSlotNumber(slotNumber);

        CandidateRead primaryRead = readCandidate(primaryFile(slotNumber));
        if (primaryRead.isValid()) {
            return primaryRead.document().gameState.copy();
        }

        CandidateRead temporaryRead = readCandidate(temporaryFile(slotNumber));
        if (temporaryRead.isValid()) {
            return temporaryRead.document().gameState.copy();
        }

        CandidateRead backupRead = readCandidate(backupFile(slotNumber));
        if (backupRead.isValid()) {
            return backupRead.document().gameState.copy();
        }

        throw new SaveGameException(
            "無法讀取存檔槽 " + slotNumber + "："
                + joinDiagnostics(primaryRead, temporaryRead, backupRead)
        );
    }

    @Override
    public void save(int slotNumber, GameState gameState) {
        validateSlotNumber(slotNumber);
        GameStateValidator.validate(gameState);
        ensureSaveDirectory();

        FileHandle primaryFile = primaryFile(slotNumber);
        FileHandle temporaryFile = temporaryFile(slotNumber);
        FileHandle backupFile = backupFile(slotNumber);
        FileHandle corruptFile = corruptFile(slotNumber);

        SaveGameDocument saveGameDocument = new SaveGameDocument();
        saveGameDocument.schemaVersion = SangoVersion.SAVE_DOCUMENT_SCHEMA_VERSION;
        saveGameDocument.gameVersion = SangoVersion.GAME_VERSION;
        saveGameDocument.savedAtEpochMillis = System.currentTimeMillis();
        saveGameDocument.gameState = gameState.copy();

        try {
            deleteIfExists(temporaryFile);
            temporaryFile.writeString(json.prettyPrint(saveGameDocument), false, "UTF-8");
            readRequired(temporaryFile);

            CandidateRead existingPrimary = readCandidate(primaryFile);
            if (existingPrimary.isValid()) {
                deleteIfExists(backupFile);
                primaryFile.copyTo(backupFile);
                readRequired(backupFile);
            } else if (primaryFile.exists()) {
                deleteIfExists(corruptFile);
                primaryFile.copyTo(corruptFile);
            }

            deleteIfExists(primaryFile);
            temporaryFile.moveTo(primaryFile);
            readRequired(primaryFile);
        } catch (RuntimeException exception) {
            restoreBackupWhenPrimaryMissing(primaryFile, backupFile);
            throw new SaveGameException(
                "寫入存檔槽 " + slotNumber + " 失敗。",
                exception
            );
        } finally {
            deleteIfExists(temporaryFile);
        }
    }

    @Override
    public void delete(int slotNumber) {
        validateSlotNumber(slotNumber);
        deleteIfExists(primaryFile(slotNumber));
        deleteIfExists(temporaryFile(slotNumber));
        deleteIfExists(backupFile(slotNumber));
        deleteIfExists(corruptFile(slotNumber));
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

    private CandidateRead readCandidate(FileHandle candidateFile) {
        if (!candidateFile.exists()) {
            return CandidateRead.missing(candidateFile.path());
        }

        try {
            SaveGameDocument saveGameDocument = json.fromJson(
                SaveGameDocument.class,
                candidateFile
            );
            validateDocument(saveGameDocument);
            return CandidateRead.valid(candidateFile.path(), saveGameDocument);
        } catch (RuntimeException exception) {
            return CandidateRead.invalid(candidateFile.path(), exception.getMessage());
        }
    }

    private SaveGameDocument readRequired(FileHandle candidateFile) {
        CandidateRead candidateRead = readCandidate(candidateFile);
        if (!candidateRead.isValid()) {
            throw new SaveGameException(
                "存檔驗證失敗：" + candidateRead.path() + "；" + candidateRead.diagnostic()
            );
        }
        return candidateRead.document();
    }

    private void validateDocument(SaveGameDocument saveGameDocument) {
        if (saveGameDocument == null) {
            throw new IllegalArgumentException("SaveGameDocument 不可為 null。");
        }
        if (saveGameDocument.schemaVersion != SangoVersion.SAVE_DOCUMENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                "不支援的 SaveGame schemaVersion：" + saveGameDocument.schemaVersion
            );
        }
        if (saveGameDocument.gameVersion == null || saveGameDocument.gameVersion.trim().isEmpty()) {
            throw new IllegalArgumentException("SaveGame.gameVersion 不可為空。");
        }
        saveGameDocument.gameState = gameStateMigrator.migrate(saveGameDocument.gameState);
        GameStateValidator.validate(saveGameDocument.gameState);
    }

    private void restoreBackupWhenPrimaryMissing(
        FileHandle primaryFile,
        FileHandle backupFile
    ) {
        if (primaryFile.exists() || !backupFile.exists()) {
            return;
        }
        try {
            CandidateRead backupRead = readCandidate(backupFile);
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

    private FileHandle primaryFile(int slotNumber) {
        return saveDirectory.child(filePrefix(slotNumber) + ".json");
    }

    private FileHandle temporaryFile(int slotNumber) {
        return saveDirectory.child(filePrefix(slotNumber) + ".tmp");
    }

    private FileHandle backupFile(int slotNumber) {
        return saveDirectory.child(filePrefix(slotNumber) + ".backup.json");
    }

    private FileHandle corruptFile(int slotNumber) {
        return saveDirectory.child(filePrefix(slotNumber) + ".corrupt.json");
    }

    private String filePrefix(int slotNumber) {
        return String.format(Locale.ROOT, "slot-%02d", slotNumber);
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
