package idv.kuan.studio.sango.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.files.FileHandle;

import idv.kuan.studio.sango.application.command.EndTurnCommand;
import idv.kuan.studio.sango.application.command.ExecuteDomesticActionCommand;
import idv.kuan.studio.sango.application.command.LaunchExpeditionCommand;
import idv.kuan.studio.sango.application.command.NewGameCommand;
import idv.kuan.studio.sango.application.command.ScoutCityCommand;
import idv.kuan.studio.sango.application.request.NewGameRequest;
import idv.kuan.studio.sango.application.result.DomesticActionResult;
import idv.kuan.studio.sango.application.result.StrategicActionResult;
import idv.kuan.studio.sango.application.result.TurnEvent;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionResult;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.CampaignStatus;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.DomesticActionFailureReason;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;
import idv.kuan.studio.sango.domain.rule.SeasonalEconomyRules;
import idv.kuan.studio.sango.domain.service.TurnResolutionService;
import idv.kuan.studio.sango.repository.definition.AssetJsonGameDefinitionRepository;
import idv.kuan.studio.sango.repository.save.LocalJsonSaveGameRepository;
import idv.kuan.studio.sango.repository.save.SaveSlotInspection;
import idv.kuan.studio.sango.repository.save.SaveSlotState;

/**
 * 不依賴 Graphics Context 的 0.3.0 Vertical Slice smoke test。
 */
public final class VerticalSliceSmokeTest {
    private static final int SAVE_SLOT = 1;
    private static final String SCENARIO_ID = "prototype_warlords";
    private static final String PLAYER_FACTION_ID = "cao_cao";
    private static final String PLAYER_CAPITAL_ID = "chenliu";
    private static final String VICTORY_TARGET_ID = "runan";

    private VerticalSliceSmokeTest() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("需要 assets 目錄的絕對路徑。");
        }

        Path assetsPath = Path.of(arguments[0]);
        Path temporaryRootPath = Files.createTempDirectory("sango-030-test-");
        FileHandle temporaryRootDirectory = new FileHandle(temporaryRootPath.toFile());

        try {
            AssetJsonGameDefinitionRepository definitionRepository = createDefinitionRepository(
                assetsPath
            );
            validateDefinitions(definitionRepository);
            validateDelayedEconomyAndSeasons(
                definitionRepository,
                temporaryRootDirectory.child("economy")
            );
            validateScoutingAndBattleLoop(
                definitionRepository,
                temporaryRootDirectory.child("battle")
            );
            validateCapitalDefeat(
                definitionRepository,
                temporaryRootDirectory.child("defeat")
            );
            validateTurnLimit(
                definitionRepository,
                temporaryRootDirectory.child("timeout")
            );
            validateSaveRecovery(
                definitionRepository,
                temporaryRootDirectory.child("recovery")
            );

            System.out.println("Sango 0.3.0 strategic vertical slice smoke test: PASS");
        } finally {
            temporaryRootDirectory.deleteDirectory();
        }
    }

    private static AssetJsonGameDefinitionRepository createDefinitionRepository(
        Path assetsPath
    ) {
        return new AssetJsonGameDefinitionRepository(
            new FileHandle(assetsPath.resolve("data/scenarios/scenarios.json").toFile()),
            new FileHandle(assetsPath.resolve("data/factions/factions.json").toFile()),
            new FileHandle(assetsPath.resolve("data/cities/cities.json").toFile()),
            new FileHandle(assetsPath.resolve("data/maps/maps.json").toFile())
        );
    }

    private static void validateDefinitions(
        AssetJsonGameDefinitionRepository definitionRepository
    ) {
        StrategicMapDefinition mapDefinition = definitionRepository.requireMap(
            "prototype_central_region"
        );
        assertEquals(6, mapDefinition.nodes.length, "地圖城池節點數");
        assertEquals(6, mapDefinition.connections.length, "地圖道路數");
        assertNotNull(
            mapDefinition.findConnection(PLAYER_CAPITAL_ID, VICTORY_TARGET_ID),
            "玩家主城與勝利目標必須相鄰"
        );
        assertEquals(
            3,
            definitionRepository.findFactionsForScenario(SCENARIO_ID).size(),
            "可選勢力數"
        );
    }

    private static void validateDelayedEconomyAndSeasons(
        AssetJsonGameDefinitionRepository definitionRepository,
        FileHandle saveDirectory
    ) {
        CommandSet commands = new CommandSet(definitionRepository, saveDirectory);
        GameState gameState = commands.newGame();
        CityState capitalCityState = gameState.requireCityState(PLAYER_CAPITAL_ID);

        assertEquals(1200, gameState.requirePlayerFactionState().gold, "新局金");
        assertEquals(2200, gameState.requirePlayerFactionState().food, "新局糧");
        assertEquals(40, capitalCityState.agriculture, "新局農業");
        assertEquals(42, capitalCityState.commerce, "新局商業");
        assertEquals(35, capitalCityState.waterControl, "新局治水");

        gameState = requireDomesticSuccess(
            commands.domesticActionCommand.execute(
                SAVE_SLOT,
                gameState,
                PLAYER_CAPITAL_ID,
                DomesticActionType.DEVELOP_AGRICULTURE
            )
        );
        assertEquals(1150, gameState.requirePlayerFactionState().gold, "開墾後金");
        assertEquals(2200, gameState.requirePlayerFactionState().food, "開墾不可立即增加糧");
        assertEquals(45, gameState.requireCityState(PLAYER_CAPITAL_ID).agriculture, "開墾後農業");

        gameState = requireDomesticSuccess(
            commands.domesticActionCommand.execute(
                SAVE_SLOT,
                gameState,
                PLAYER_CAPITAL_ID,
                DomesticActionType.DEVELOP_COMMERCE
            )
        );
        assertEquals(1100, gameState.requirePlayerFactionState().gold, "商業開發需支付金");
        assertEquals(47, gameState.requireCityState(PLAYER_CAPITAL_ID).commerce, "商業開發後商業");

        gameState = requireDomesticSuccess(
            commands.domesticActionCommand.execute(
                SAVE_SLOT,
                gameState,
                PLAYER_CAPITAL_ID,
                DomesticActionType.IMPROVE_WATER_CONTROL
            )
        );
        assertEquals(1020, gameState.requirePlayerFactionState().gold, "治水後金");
        assertEquals(40, gameState.requireCityState(PLAYER_CAPITAL_ID).waterControl, "治水後能力");
        assertEquals(0, gameState.actionPointsRemaining, "三次命令後行動力");

        DomesticActionResult rejectedAction = commands.domesticActionCommand.execute(
            SAVE_SLOT,
            gameState,
            PLAYER_CAPITAL_ID,
            DomesticActionType.TRAIN
        );
        assertTrue(!rejectedAction.isSuccessful(), "無行動力時必須拒絕命令");
        assertEquals(
            DomesticActionFailureReason.NO_ACTION_POINTS,
            rejectedAction.getFailureReason(),
            "無行動力拒絕原因"
        );

        gameState.enemyAttackCountdown = 99;
        TurnResolutionResult januaryResult = commands.endTurnCommand.execute(
            SAVE_SLOT,
            gameState
        );
        gameState = januaryResult.getGameState();
        assertEquals(2, gameState.currentMonth, "一月結算後月份");
        assertEquals(2140, gameState.requirePlayerFactionState().food, "一月軍糧支出");
        assertEquals(1020, gameState.requirePlayerFactionState().gold, "一月不可取得商業收入");
        assertContainsEvent(januaryResult, TurnEventType.MILITARY_UPKEEP, "一月軍糧事件");

        gameState = commands.endTurnCommand.execute(SAVE_SLOT, gameState).getGameState();
        assertEquals(3, gameState.currentMonth, "二月結算後月份");
        assertEquals(2080, gameState.requirePlayerFactionState().food, "二月軍糧支出");

        TurnResolutionResult marchResult = commands.endTurnCommand.execute(SAVE_SLOT, gameState);
        gameState = marchResult.getGameState();
        assertEquals(4, gameState.currentMonth, "三月結算後月份");
        assertEquals(1413, gameState.requirePlayerFactionState().gold, "三月季末商稅");
        assertEquals(2020, gameState.requirePlayerFactionState().food, "三月仍只有軍糧支出");
        assertEventPrimaryValue(
            marchResult,
            TurnEventType.QUARTERLY_TAX,
            393,
            "三月季末商稅事件"
        );

        gameState = commands.endTurnCommand.execute(SAVE_SLOT, gameState).getGameState();
        gameState = commands.endTurnCommand.execute(SAVE_SLOT, gameState).getGameState();
        TurnResolutionResult juneResult = commands.endTurnCommand.execute(SAVE_SLOT, gameState);
        gameState = juneResult.getGameState();
        CityState floodedCapitalState = gameState.requireCityState(PLAYER_CAPITAL_ID);
        assertEquals(7, gameState.currentMonth, "六月結算後月份");
        assertContainsEvent(juneResult, TurnEventType.FLOOD_OCCURRED, "六月洪災事件");
        assertEquals(42, floodedCapitalState.agriculture, "洪災降低農業");
        assertEquals(75, floodedCapitalState.harvestModifierPercent, "洪災降低秋收倍率");
        assertEquals(61800, floodedCapitalState.population, "洪災人口損失");
        assertEquals(1805, gameState.requirePlayerFactionState().gold, "六月季末商稅");

        gameState = commands.endTurnCommand.execute(SAVE_SLOT, gameState).getGameState();
        gameState = commands.endTurnCommand.execute(SAVE_SLOT, gameState).getGameState();
        TurnResolutionResult septemberResult = commands.endTurnCommand.execute(
            SAVE_SLOT,
            gameState
        );
        gameState = septemberResult.getGameState();
        assertEquals(10, gameState.currentMonth, "九月結算後月份");
        assertEquals(3128, gameState.requirePlayerFactionState().food, "九月秋收後糧");
        assertEquals(2197, gameState.requirePlayerFactionState().gold, "九月季末商稅後金");
        assertEventPrimaryValue(
            septemberResult,
            TurnEventType.HARVEST,
            1468,
            "九月秋收事件"
        );
        assertEquals(
            100,
            gameState.requireCityState(PLAYER_CAPITAL_ID).harvestModifierPercent,
            "秋收後重設收成倍率"
        );
    }

    private static void validateScoutingAndBattleLoop(
        AssetJsonGameDefinitionRepository definitionRepository,
        FileHandle saveDirectory
    ) {
        CommandSet commands = new CommandSet(definitionRepository, saveDirectory);
        GameState gameState = commands.newGame();

        StrategicActionResult scoutResult = commands.scoutCityCommand.execute(
            SAVE_SLOT,
            gameState,
            PLAYER_CAPITAL_ID,
            VICTORY_TARGET_ID
        );
        assertTrue(scoutResult.isSuccessful(), "相鄰敵城偵察應成功");
        gameState = scoutResult.getGameState();
        assertEquals(2, gameState.actionPointsRemaining, "偵察消耗行動力");
        assertEquals(1180, gameState.requirePlayerFactionState().gold, "偵察消耗金");
        assertEquals(
            4,
            gameState.requireCityState(VICTORY_TARGET_ID).scoutedUntilTurn,
            "偵察情報期限"
        );

        StrategicActionResult firstExpedition = commands.launchExpeditionCommand.execute(
            SAVE_SLOT,
            gameState,
            PLAYER_CAPITAL_ID,
            VICTORY_TARGET_ID,
            BattleTactic.BALANCED
        );
        assertTrue(firstExpedition.isSuccessful(), "第一次出征應成功建立軍隊");
        assertEquals(800, firstExpedition.getDispatchedTroops(), "第一次派出兵力");
        gameState = firstExpedition.getGameState();
        assertEquals(400, gameState.requireCityState(PLAYER_CAPITAL_ID).troops, "出征後留守兵力");
        assertEquals(1, gameState.armyStates.length, "出征後行軍部隊數");

        TurnResolutionResult firstBattleResult = commands.endTurnCommand.execute(
            SAVE_SLOT,
            gameState
        );
        gameState = firstBattleResult.getGameState();
        assertContainsEvent(
            firstBattleResult,
            TurnEventType.BATTLE_DEFENDER_WON,
            "第一次進攻應由守軍獲勝"
        );
        assertEquals(CampaignStatus.IN_PROGRESS, gameState.campaignStatus, "首戰失敗後仍可繼續");
        assertEquals(680, gameState.requireCityState(PLAYER_CAPITAL_ID).troops, "敗軍生還者返回主城");
        assertEquals(523, gameState.requireCityState(VICTORY_TARGET_ID).troops, "首戰後敵城守軍");

        gameState = requireDomesticSuccess(
            commands.domesticActionCommand.execute(
                SAVE_SLOT,
                gameState,
                PLAYER_CAPITAL_ID,
                DomesticActionType.RECRUIT
            )
        );
        gameState = requireDomesticSuccess(
            commands.domesticActionCommand.execute(
                SAVE_SLOT,
                gameState,
                PLAYER_CAPITAL_ID,
                DomesticActionType.RECRUIT
            )
        );
        StrategicActionResult secondExpedition = commands.launchExpeditionCommand.execute(
            SAVE_SLOT,
            gameState,
            PLAYER_CAPITAL_ID,
            VICTORY_TARGET_ID,
            BattleTactic.BALANCED
        );
        assertTrue(secondExpedition.isSuccessful(), "整備後第二次出征應成功");
        assertEquals(600, secondExpedition.getDispatchedTroops(), "第二次派出兵力");

        TurnResolutionResult victoryResult = commands.endTurnCommand.execute(
            SAVE_SLOT,
            secondExpedition.getGameState()
        );
        gameState = victoryResult.getGameState();
        assertEquals(CampaignStatus.VICTORY, gameState.campaignStatus, "攻下目標城後勝利");
        assertEquals(
            PLAYER_FACTION_ID,
            gameState.requireCityState(VICTORY_TARGET_ID).ownerFactionId,
            "目標城控制權"
        );
        assertContainsEvent(victoryResult, TurnEventType.CAMPAIGN_VICTORY, "戰役勝利事件");
        assertEquals(0, gameState.actionPointsRemaining, "戰役結束後不可再下命令");
    }

    private static void validateCapitalDefeat(
        AssetJsonGameDefinitionRepository definitionRepository,
        FileHandle saveDirectory
    ) {
        CommandSet commands = new CommandSet(definitionRepository, saveDirectory);
        GameState gameState = commands.newGame();
        gameState.enemyAttackCountdown = 4;

        gameState = commands.endTurnCommand.execute(SAVE_SLOT, gameState).getGameState();
        gameState = commands.endTurnCommand.execute(SAVE_SLOT, gameState).getGameState();
        gameState = commands.endTurnCommand.execute(SAVE_SLOT, gameState).getGameState();
        TurnResolutionResult aprilResult = commands.endTurnCommand.execute(SAVE_SLOT, gameState);
        gameState = aprilResult.getGameState();
        assertContainsEvent(aprilResult, TurnEventType.ENEMY_MARCHING, "四月敵軍出征");
        assertEquals(1, gameState.armyStates.length, "敵軍行軍部隊數");

        gameState.requireCityState(PLAYER_CAPITAL_ID).troops = 1;
        gameState.requireCityState("guangling").ownerFactionId = PLAYER_FACTION_ID;

        TurnResolutionResult defeatResult = commands.endTurnCommand.execute(
            SAVE_SLOT,
            gameState
        );
        gameState = defeatResult.getGameState();
        assertEquals(CampaignStatus.DEFEAT, gameState.campaignStatus, "主城失守後敗北");
        assertContainsEvent(
            defeatResult,
            TurnEventType.CAMPAIGN_DEFEAT_CAPITAL,
            "主城失守事件"
        );
        assertTrue(gameState.requirePlayerFactionState().active, "仍有其他城時勢力可保持 active");
        assertEquals("guangling", gameState.requirePlayerFactionState().capitalCityId, "敗北後替代主城");
        assertEquals(0, gameState.actionPointsRemaining, "敗北後行動力歸零");
    }

    private static void validateTurnLimit(
        AssetJsonGameDefinitionRepository definitionRepository,
        FileHandle saveDirectory
    ) {
        CommandSet commands = new CommandSet(definitionRepository, saveDirectory);
        GameState gameState = commands.newGame();
        gameState.turnLimitMonths = 1;
        gameState.enemyAttackCountdown = 99;

        TurnResolutionResult timeoutResult = commands.endTurnCommand.execute(
            SAVE_SLOT,
            gameState
        );
        gameState = timeoutResult.getGameState();
        assertEquals(CampaignStatus.DEFEAT, gameState.campaignStatus, "超過期限後敗北");
        assertContainsEvent(
            timeoutResult,
            TurnEventType.CAMPAIGN_DEFEAT_TIMEOUT,
            "期限敗北事件"
        );
        assertEquals(0, gameState.actionPointsRemaining, "期限敗北後行動力歸零");
    }

    private static void validateSaveRecovery(
        AssetJsonGameDefinitionRepository definitionRepository,
        FileHandle saveDirectory
    ) {
        CommandSet commands = new CommandSet(definitionRepository, saveDirectory);
        GameState gameState = commands.newGame();
        gameState = requireDomesticSuccess(
            commands.domesticActionCommand.execute(
                SAVE_SLOT,
                gameState,
                PLAYER_CAPITAL_ID,
                DomesticActionType.DEVELOP_AGRICULTURE
            )
        );
        assertEquals(45, gameState.requireCityState(PLAYER_CAPITAL_ID).agriculture, "主要存檔農業");

        GameState loadedState = commands.saveGameRepository.load(SAVE_SLOT);
        loadedState.requirePlayerFactionState().gold = 1;
        assertEquals(
            1150,
            commands.saveGameRepository.load(SAVE_SLOT).requirePlayerFactionState().gold,
            "讀取結果不可回寫 Repository"
        );

        FileHandle primarySave = saveDirectory.child("slot-01.json");
        primarySave.writeString("{ broken", false, "UTF-8");
        SaveSlotInspection recoveryInspection = commands.saveGameRepository.inspect(SAVE_SLOT);
        assertEquals(SaveSlotState.AVAILABLE, recoveryInspection.getState(), "備份可用狀態");
        assertTrue(recoveryInspection.hasRecoveryCandidate(), "應標示使用復原候選檔");
        GameState recoveredState = commands.saveGameRepository.load(SAVE_SLOT);
        assertEquals(40, recoveredState.requireCityState(PLAYER_CAPITAL_ID).agriculture, "備份應為前一版本");
        assertEquals(1200, recoveredState.requirePlayerFactionState().gold, "備份金");

        commands.saveGameRepository.delete(SAVE_SLOT);
        assertEquals(
            SaveSlotState.EMPTY,
            commands.saveGameRepository.inspect(SAVE_SLOT).getState(),
            "刪除後狀態"
        );
    }

    private static GameState requireDomesticSuccess(DomesticActionResult actionResult) {
        if (!actionResult.isSuccessful()) {
            throw new AssertionError("命令應成功，但失敗原因為：" + actionResult.getFailureReason());
        }
        return actionResult.getGameState();
    }

    private static void assertContainsEvent(
        TurnResolutionResult resolutionResult,
        TurnEventType expectedType,
        String message
    ) {
        for (TurnEvent turnEvent : resolutionResult.getReport().getEvents()) {
            if (turnEvent.getType() == expectedType) {
                return;
            }
        }
        throw new AssertionError(message + "，缺少事件：" + expectedType);
    }

    private static void assertEventPrimaryValue(
        TurnResolutionResult resolutionResult,
        TurnEventType expectedType,
        int expectedValue,
        String message
    ) {
        for (TurnEvent turnEvent : resolutionResult.getReport().getEvents()) {
            if (turnEvent.getType() == expectedType) {
                assertEquals(expectedValue, turnEvent.getPrimaryValue(), message);
                return;
            }
        }
        throw new AssertionError(message + "，缺少事件：" + expectedType);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertNotNull(Object value, String message) {
        if (value == null) {
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

    private static final class CommandSet {
        private final LocalJsonSaveGameRepository saveGameRepository;
        private final NewGameCommand newGameCommand;
        private final ExecuteDomesticActionCommand domesticActionCommand;
        private final ScoutCityCommand scoutCityCommand;
        private final LaunchExpeditionCommand launchExpeditionCommand;
        private final EndTurnCommand endTurnCommand;

        private CommandSet(
            AssetJsonGameDefinitionRepository definitionRepository,
            FileHandle saveDirectory
        ) {
            saveGameRepository = new LocalJsonSaveGameRepository(saveDirectory);
            newGameCommand = new NewGameCommand(definitionRepository, saveGameRepository);
            domesticActionCommand = new ExecuteDomesticActionCommand(saveGameRepository);
            scoutCityCommand = new ScoutCityCommand(definitionRepository, saveGameRepository);
            launchExpeditionCommand = new LaunchExpeditionCommand(
                definitionRepository,
                saveGameRepository
            );
            endTurnCommand = new EndTurnCommand(
                saveGameRepository,
                new TurnResolutionService(definitionRepository)
            );
        }

        private GameState newGame() {
            return newGameCommand.execute(
                SAVE_SLOT,
                new NewGameRequest(SCENARIO_ID, PLAYER_FACTION_ID)
            );
        }
    }
}
