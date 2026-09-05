package idv.kuan.studio.sango.runtime;

import idv.kuan.studio.sango.application.command.EndTurnCommand;
import idv.kuan.studio.sango.application.command.ExecuteDomesticActionCommand;
import idv.kuan.studio.sango.application.command.NewGameCommand;
import idv.kuan.studio.sango.application.command.SaveCurrentGameCommand;
import idv.kuan.studio.sango.repository.GameDefinitionRepository;
import idv.kuan.studio.sango.repository.SaveGameRepository;
import idv.kuan.studio.sango.repository.definition.AssetJsonGameDefinitionRepository;
import idv.kuan.studio.sango.repository.save.LocalJsonSaveGameRepository;

/**
 * SimpleUI 的 Screen factory 使用無參數建構式，因此以單一 Runtime 容器提供依賴。
 * Domain 與 Application 類別本身仍只依賴介面，可在測試中替換 Repository。
 */
public final class SangoServices {
    public static final int DEFAULT_SAVE_SLOT = 1;
    public static final String DEFAULT_SCENARIO_ID = "prototype_warlords";

    private static boolean initialized;
    private static GameDefinitionRepository definitionRepository;
    private static SaveGameRepository saveGameRepository;
    private static GameSession gameSession;
    private static NewGameCommand newGameCommand;
    private static ExecuteDomesticActionCommand domesticActionCommand;
    private static EndTurnCommand endTurnCommand;
    private static SaveCurrentGameCommand saveCurrentGameCommand;

    private SangoServices() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        definitionRepository = new AssetJsonGameDefinitionRepository();
        saveGameRepository = new LocalJsonSaveGameRepository();
        gameSession = new GameSession();
        newGameCommand = new NewGameCommand(definitionRepository, saveGameRepository);
        domesticActionCommand = new ExecuteDomesticActionCommand(saveGameRepository);
        endTurnCommand = new EndTurnCommand(saveGameRepository);
        saveCurrentGameCommand = new SaveCurrentGameCommand(saveGameRepository);
        initialized = true;
    }

    public static GameDefinitionRepository definitions() {
        requireInitialized();
        return definitionRepository;
    }

    public static SaveGameRepository saveGames() {
        requireInitialized();
        return saveGameRepository;
    }

    public static GameSession session() {
        requireInitialized();
        return gameSession;
    }

    public static NewGameCommand newGameCommand() {
        requireInitialized();
        return newGameCommand;
    }

    public static ExecuteDomesticActionCommand domesticActionCommand() {
        requireInitialized();
        return domesticActionCommand;
    }

    public static EndTurnCommand endTurnCommand() {
        requireInitialized();
        return endTurnCommand;
    }

    public static SaveCurrentGameCommand saveCurrentGameCommand() {
        requireInitialized();
        return saveCurrentGameCommand;
    }

    private static void requireInitialized() {
        if (!initialized) {
            throw new IllegalStateException("SangoServices 尚未初始化。");
        }
    }
}
