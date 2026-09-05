package idv.kuan.studio.sango.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.files.FileHandle;

import idv.kuan.studio.sango.application.command.EndTurnCommand;
import idv.kuan.studio.sango.application.command.ExecuteDomesticActionCommand;
import idv.kuan.studio.sango.application.command.NewGameCommand;
import idv.kuan.studio.sango.application.request.NewGameRequest;
import idv.kuan.studio.sango.application.result.DomesticActionResult;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.DomesticActionFailureReason;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;
import idv.kuan.studio.sango.repository.definition.AssetJsonGameDefinitionRepository;
import idv.kuan.studio.sango.repository.save.LocalJsonSaveGameRepository;
import idv.kuan.studio.sango.repository.save.SaveSlotInspection;
import idv.kuan.studio.sango.repository.save.SaveSlotState;

/**
 * 不依賴 Graphics Context 的 Vertical Slice smoke test。
 */
public final class VerticalSliceSmokeTest {
    private static final int SAVE_SLOT = 1;

    private VerticalSliceSmokeTest() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("需要 assets 目錄的絕對路徑。");
        }

        Path assetsPath = Path.of(arguments[0]);
        Path temporarySavePath = Files.createTempDirectory("sango-save-test-");
        FileHandle temporarySaveDirectory = new FileHandle(temporarySavePath.toFile());

        try {
            AssetJsonGameDefinitionRepository definitionRepository =
                new AssetJsonGameDefinitionRepository(
                    new FileHandle(assetsPath.resolve("data/scenarios/scenarios.json").toFile()),
                    new FileHandle(assetsPath.resolve("data/factions/factions.json").toFile()),
                    new FileHandle(assetsPath.resolve("data/cities/cities.json").toFile())
                );
            LocalJsonSaveGameRepository saveGameRepository =
                new LocalJsonSaveGameRepository(temporarySaveDirectory);

            NewGameCommand newGameCommand = new NewGameCommand(
                definitionRepository,
                saveGameRepository
            );
            ExecuteDomesticActionCommand domesticActionCommand =
                new ExecuteDomesticActionCommand(saveGameRepository);
            EndTurnCommand endTurnCommand = new EndTurnCommand(saveGameRepository);

            GameState gameState = newGameCommand.execute(
                SAVE_SLOT,
                new NewGameRequest("prototype_warlords", "cao_cao")
            );
            assertEquals(1, gameState.currentTurn, "新局回合");
            assertEquals(3, gameState.actionPointsRemaining, "新局行動力");
            assertEquals(1200, gameState.requirePlayerFactionState().gold, "新局金");
            assertEquals(2200, gameState.requirePlayerFactionState().food, "新局糧");

            gameState = requireSuccess(
                domesticActionCommand.execute(
                    SAVE_SLOT,
                    gameState,
                    DomesticActionType.DEVELOP_AGRICULTURE
                )
            );
            assertEquals(2, gameState.actionPointsRemaining, "開墾後行動力");
            assertEquals(1150, gameState.requirePlayerFactionState().gold, "開墾後金");
            assertEquals(2400, gameState.requirePlayerFactionState().food, "開墾後糧");
            assertEquals(45, gameState.requireCapitalCityState().agriculture, "開墾後農業");

            gameState = requireSuccess(
                domesticActionCommand.execute(
                    SAVE_SLOT,
                    gameState,
                    DomesticActionType.DEVELOP_COMMERCE
                )
            );
            assertEquals(1300, gameState.requirePlayerFactionState().gold, "商業後金");
            assertEquals(47, gameState.requireCapitalCityState().commerce, "商業後商業值");

            gameState = requireSuccess(
                domesticActionCommand.execute(
                    SAVE_SLOT,
                    gameState,
                    DomesticActionType.RECRUIT
                )
            );
            FactionState factionState = gameState.requirePlayerFactionState();
            CityState cityState = gameState.requireCapitalCityState();
            assertEquals(0, gameState.actionPointsRemaining, "三次命令後行動力");
            assertEquals(1200, factionState.gold, "徵兵後金");
            assertEquals(2300, factionState.food, "徵兵後糧");
            assertEquals(1400, cityState.troops, "徵兵後兵力");
            assertEquals(61800, cityState.population, "徵兵後人口");

            DomesticActionResult rejectedAction = domesticActionCommand.execute(
                SAVE_SLOT,
                gameState,
                DomesticActionType.TRAIN
            );
            assertTrue(!rejectedAction.isSuccessful(), "無行動力時必須拒絕命令");
            assertEquals(
                DomesticActionFailureReason.NO_ACTION_POINTS,
                rejectedAction.getFailureReason(),
                "拒絕原因"
            );

            gameState = endTurnCommand.execute(SAVE_SLOT, gameState);
            assertEquals(2, gameState.currentTurn, "結束回合後回合");
            assertEquals(2, gameState.currentMonth, "結束回合後月份");
            assertEquals(3, gameState.actionPointsRemaining, "結束回合後行動力");

            gameState = requireSuccess(
                domesticActionCommand.execute(
                    SAVE_SLOT,
                    gameState,
                    DomesticActionType.TRAIN
                )
            );
            assertEquals(2, gameState.actionPointsRemaining, "訓練後行動力");
            assertEquals(1150, gameState.requirePlayerFactionState().gold, "訓練後金");
            assertEquals(40, gameState.requireCapitalCityState().training, "訓練後訓練值");

            GameState loadedState = saveGameRepository.load(SAVE_SLOT);
            assertEquals(gameState.currentTurn, loadedState.currentTurn, "重新讀取回合");
            assertEquals(
                gameState.requirePlayerFactionState().gold,
                loadedState.requirePlayerFactionState().gold,
                "重新讀取金"
            );
            loadedState.requirePlayerFactionState().gold = 1;
            assertEquals(
                1150,
                saveGameRepository.load(SAVE_SLOT).requirePlayerFactionState().gold,
                "讀取結果不可回寫 Repository"
            );

            FileHandle primarySave = temporarySaveDirectory.child("slot-01.json");
            primarySave.writeString("{ broken", false, "UTF-8");
            SaveSlotInspection recoveryInspection = saveGameRepository.inspect(SAVE_SLOT);
            assertEquals(SaveSlotState.AVAILABLE, recoveryInspection.getState(), "備份可用狀態");
            assertTrue(recoveryInspection.hasRecoveryCandidate(), "應標示使用復原候選檔");
            GameState recoveredState = saveGameRepository.load(SAVE_SLOT);
            assertEquals(2, recoveredState.currentTurn, "備份回合");
            assertEquals(3, recoveredState.actionPointsRemaining, "備份行動力");
            assertEquals(1200, recoveredState.requirePlayerFactionState().gold, "備份金");
            assertEquals(35, recoveredState.requireCapitalCityState().training, "備份訓練值");

            saveGameRepository.delete(SAVE_SLOT);
            assertEquals(
                SaveSlotState.EMPTY,
                saveGameRepository.inspect(SAVE_SLOT).getState(),
                "刪除後狀態"
            );

            System.out.println("Sango Vertical Slice smoke test: PASS");
        } finally {
            temporarySaveDirectory.deleteDirectory();
        }
    }

    private static GameState requireSuccess(DomesticActionResult actionResult) {
        if (!actionResult.isSuccessful()) {
            throw new AssertionError("命令應成功，但失敗原因為：" + actionResult.getFailureReason());
        }
        return actionResult.getGameState();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(
                message + "，預期：" + expected + "，實際：" + actual
            );
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                message + "，預期：" + expected + "，實際：" + actual
            );
        }
    }
}
