package idv.kuan.studio.sango.runtime;

import idv.kuan.studio.sango.application.command.EndTurnCommand;
import idv.kuan.studio.sango.application.command.ExecuteDomesticActionCommand;
import idv.kuan.studio.sango.application.command.LaunchExpeditionCommand;
import idv.kuan.studio.sango.application.command.MarkBattleReportReadCommand;
import idv.kuan.studio.sango.application.command.NewGameCommand;
import idv.kuan.studio.sango.application.command.SaveCurrentGameCommand;
import idv.kuan.studio.sango.application.command.ScoutCityCommand;
import idv.kuan.studio.sango.audio.SangoAudioService;
import idv.kuan.studio.sango.domain.service.TurnResolutionService;
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
    public static final int SAVE_SLOT_COUNT = 3;
    public static final String DEFAULT_SCENARIO_ID = "prototype_warlords";

    private static boolean initialized;
    private static GameDefinitionRepository definitionRepository;
    private static SaveGameRepository saveGameRepository;
    private static GameSession gameSession;
    private static SangoAudioService audioService;
    private static NewGameCommand newGameCommand;
    private static ExecuteDomesticActionCommand domesticActionCommand;
    private static ScoutCityCommand scoutCityCommand;
    private static LaunchExpeditionCommand launchExpeditionCommand;
    private static EndTurnCommand endTurnCommand;
    private static SaveCurrentGameCommand saveCurrentGameCommand;
    private static MarkBattleReportReadCommand markBattleReportReadCommand;

    private SangoServices() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        definitionRepository = new AssetJsonGameDefinitionRepository();
        saveGameRepository = new LocalJsonSaveGameRepository();
        gameSession = new GameSession();
        audioService = new SangoAudioService();
        TurnResolutionService turnResolutionService = new TurnResolutionService(
            definitionRepository
        );
        newGameCommand = new NewGameCommand(definitionRepository, saveGameRepository);
        domesticActionCommand = new ExecuteDomesticActionCommand(saveGameRepository);
        scoutCityCommand = new ScoutCityCommand(definitionRepository, saveGameRepository);
        launchExpeditionCommand = new LaunchExpeditionCommand(
            definitionRepository,
            saveGameRepository
        );
        endTurnCommand = new EndTurnCommand(saveGameRepository, turnResolutionService);
        saveCurrentGameCommand = new SaveCurrentGameCommand(saveGameRepository);
        markBattleReportReadCommand = new MarkBattleReportReadCommand(saveGameRepository);
        initialized = true;
    }

    public static synchronized void dispose() {
        if (!initialized) {
            return;
        }
        audioService.dispose();
        initialized = false;
        definitionRepository = null;
        saveGameRepository = null;
        gameSession = null;
        audioService = null;
        newGameCommand = null;
        domesticActionCommand = null;
        scoutCityCommand = null;
        launchExpeditionCommand = null;
        endTurnCommand = null;
        saveCurrentGameCommand = null;
        markBattleReportReadCommand = null;
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

    public static SangoAudioService audio() {
        requireInitialized();
        return audioService;
    }

    public static NewGameCommand newGameCommand() {
        requireInitialized();
        return newGameCommand;
    }

    public static ExecuteDomesticActionCommand domesticActionCommand() {
        requireInitialized();
        return domesticActionCommand;
    }

    public static ScoutCityCommand scoutCityCommand() {
        requireInitialized();
        return scoutCityCommand;
    }

    public static LaunchExpeditionCommand launchExpeditionCommand() {
        requireInitialized();
        return launchExpeditionCommand;
    }

    public static EndTurnCommand endTurnCommand() {
        requireInitialized();
        return endTurnCommand;
    }

    public static SaveCurrentGameCommand saveCurrentGameCommand() {
        requireInitialized();
        return saveCurrentGameCommand;
    }

    public static MarkBattleReportReadCommand markBattleReportReadCommand() {
        requireInitialized();
        return markBattleReportReadCommand;
    }

    private static void requireInitialized() {
        if (!initialized) {
            throw new IllegalStateException("SangoServices 尚未初始化。");
        }
    }
}
