package idv.kuan.studio.sango.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import idv.kuan.studio.sango.SangoVersion;

import idv.kuan.studio.sango.application.command.EndTurnCommand;
import idv.kuan.studio.sango.application.command.ExecuteDomesticActionCommand;
import idv.kuan.studio.sango.application.command.LaunchExpeditionCommand;
import idv.kuan.studio.sango.application.command.MarkBattleReportReadCommand;
import idv.kuan.studio.sango.application.command.NewGameCommand;
import idv.kuan.studio.sango.application.command.ScoutCityCommand;
import idv.kuan.studio.sango.application.request.NewGameRequest;
import idv.kuan.studio.sango.application.result.DomesticActionResult;
import idv.kuan.studio.sango.application.result.StrategicActionResult;
import idv.kuan.studio.sango.application.result.TurnEvent;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionResult;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.CampaignStatus;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.ScenarioObjectiveStatus;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.DomesticActionFailureReason;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;
import idv.kuan.studio.sango.domain.rule.NationalActionPointRules;
import idv.kuan.studio.sango.domain.rule.SeasonalEconomyRules;
import idv.kuan.studio.sango.domain.service.TurnResolutionService;
import idv.kuan.studio.sango.repository.definition.AssetJsonGameDefinitionRepository;
import idv.kuan.studio.sango.repository.save.LocalJsonSaveGameRepository;
import idv.kuan.studio.sango.repository.save.SaveGameDocument;
import idv.kuan.studio.sango.repository.save.SaveSlotInspection;
import idv.kuan.studio.sango.repository.save.SaveSlotMetadata;
import idv.kuan.studio.sango.repository.save.SaveSlotState;

/**
 * 不依賴 Graphics Context 的舊六城劇本、戰報、自由征戰與存檔回歸測試。
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
        Path temporaryRootPath = Files.createTempDirectory("sango-040-test-");
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
            validateCapitalRelocationAndFreePlay(
                definitionRepository,
                temporaryRootDirectory.child("capital-relocation")
            );
            validateTurnLimitFreePlay(
                definitionRepository,
                temporaryRootDirectory.child("timeout")
            );
            validatePlayerElimination(
                definitionRepository,
                temporaryRootDirectory.child("elimination")
            );
            validateSaveRecoveryAndSlots(
                definitionRepository,
                temporaryRootDirectory.child("recovery")
            );
            validateSchemaTwoMigration(
                definitionRepository,
                temporaryRootDirectory.child("migration")
            );
            validateAudioAssets(assetsPath);

            System.out.println("Sango legacy campaign regression (schema 5): PASS");
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
        assertEquals(gameState.actionPointsPerTurn - 3, gameState.actionPointsRemaining, "三次命令消耗三點行動力");

        // 獨立建立零行動力情境，避免把舊版固定三點當成新的民心規則。
        gameState.actionPointsRemaining = 0;

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
        assertEquals(gameState.actionPointsPerTurn - 1, gameState.actionPointsRemaining, "偵察消耗行動力");
        assertEquals(1180, gameState.requirePlayerFactionState().gold, "偵察消耗金");
        assertEquals(
            gameState.currentTurn + 2,
            gameState.requirePlayerFactionState().cityIntelligence[0].validThroughTurn,
            "偵察情報包含當月共三個月"
        );

        StrategicActionResult firstExpedition = commands.launchExpeditionCommand.execute(
            SAVE_SLOT,
            gameState,
            PLAYER_CAPITAL_ID,
            VICTORY_TARGET_ID,
            BattleTactic.FEINT
        );
        assertTrue(firstExpedition.isSuccessful(), "第一次出征應成功建立軍隊");
        assertEquals(800, firstExpedition.getDispatchedTroops(), "第一次派出兵力");
        gameState = firstExpedition.getGameState();
        assertEquals(400, gameState.requireCityState(PLAYER_CAPITAL_ID).troops, "出征後留守兵力");
        assertEquals(1, gameState.armyStates.length, "出征後行軍部隊數");

        // 隔離戰鬥與戰報回歸；AI 內政及扣點由獨立測試涵蓋。
        for (var factionState : gameState.factionStates) {
            factionState.aiActionPointsRemaining = 0;
        }
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
        assertEquals(
            ScenarioObjectiveStatus.IN_PROGRESS,
            gameState.scenarioObjectiveStatus,
            "首戰失敗後劇本目標仍進行中"
        );
        assertEquals(GameplayStatus.ACTIVE, gameState.gameplayStatus, "首戰失敗後仍可操作");
        assertEquals(1, firstBattleResult.getReport().getBattleReportIds().size(), "首戰戰報 ID 數");
        assertEquals(1, gameState.countUnreadBattleReports(), "首戰產生未讀戰報");
        BattleReport firstBattleReport = gameState.battleReports[0];
        assertEquals(VICTORY_TARGET_ID, firstBattleReport.targetCityId, "首戰戰報城池");
        assertTrue(!firstBattleReport.read, "新戰報預設未讀");
        MarkBattleReportReadCommand markBattleReportReadCommand = new MarkBattleReportReadCommand();
        gameState = markBattleReportReadCommand.execute(
            SAVE_SLOT,
            gameState,
            firstBattleReport.battleId
        );
        assertTrue(gameState.requireBattleReport(firstBattleReport.battleId).read, "戰報可標記已讀");
        assertEquals(0, gameState.countUnreadBattleReports(), "已讀後不再計入未讀數");
        GameState reloadedReportState = commands.saveGameRepository.load(SAVE_SLOT);
        assertTrue(
            !reloadedReportState.requireBattleReport(firstBattleReport.battleId).read,
            "戰報已讀尚未明確存檔，讀取可回到原狀態"
        );
        assertEquals(400, gameState.requireCityState(PLAYER_CAPITAL_ID).troops, "新敗軍當月尚未返回主城");
        assertEquals(240, gameState.armyStates[0].troops,
            "誘敵受固守克制後，敗軍生還者保留於退卻部隊");
        assertEquals(551, gameState.requireCityState(VICTORY_TARGET_ID).troops,
            "首戰後固守敵城守軍");

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
        // 退卻軍仍在途；補足本案例所需兵力，維持第二次強攻勝利的測試目的。
        gameState.requireCityState(PLAYER_CAPITAL_ID).troops += 200;
        StrategicActionResult secondExpedition = commands.launchExpeditionCommand.execute(
            SAVE_SLOT,
            gameState,
            PLAYER_CAPITAL_ID,
            VICTORY_TARGET_ID,
            BattleTactic.ASSAULT
        );
        assertTrue(secondExpedition.isSuccessful(), "徵兵降低訓練後改採強攻，第二次出征應成功");
        assertEquals(600, secondExpedition.getDispatchedTroops(), "第二次派出兵力");

        for (var factionState : secondExpedition.getGameState().factionStates) {
            factionState.aiActionPointsRemaining = 0;
        }
        TurnResolutionResult victoryResult = commands.endTurnCommand.execute(
            SAVE_SLOT,
            secondExpedition.getGameState()
        );
        gameState = victoryResult.getGameState();
        assertTurnEventCityReferencesValid(
            definitionRepository,
            victoryResult,
            "勝利回合事件城池引用"
        );
        assertEquals(
            ScenarioObjectiveStatus.ACHIEVED,
            gameState.scenarioObjectiveStatus,
            "攻下目標城後完成劇本目標"
        );
        assertEquals(GameplayStatus.ACTIVE, gameState.gameplayStatus, "達成目標後仍可自由征戰");
        assertEquals(
            PLAYER_FACTION_ID,
            gameState.requireCityState(VICTORY_TARGET_ID).ownerFactionId,
            "目標城控制權"
        );
        assertContainsEvent(victoryResult, TurnEventType.CAMPAIGN_VICTORY, "戰役勝利事件");
        assertEquals(NationalActionPointRules.calculateMonthlyActionPoints(gameState), gameState.actionPointsRemaining, "達成目標後恢復下月行動力");
        DomesticActionResult freePlayAction = commands.domesticActionCommand.execute(
            SAVE_SLOT,
            gameState,
            PLAYER_CAPITAL_ID,
            DomesticActionType.TRAIN
        );
        assertTrue(freePlayAction.isSuccessful(), "達成劇本目標後仍可下達內政命令");
    }

    private static void validateCapitalRelocationAndFreePlay(
        AssetJsonGameDefinitionRepository definitionRepository,
        FileHandle saveDirectory
    ) {
        CommandSet commands = new CommandSet(definitionRepository, saveDirectory);
        GameState gameState = commands.newGame();
        gameState.enemyAttackCountdown = 4;
        // 多勢力 AI 會挑選較弱前線；本案例明確將主城設為目標以驗證遷都。
        gameState.requireCityState(PLAYER_CAPITAL_ID).troops = 1;

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
        assertTurnEventCityReferencesValid(
            definitionRepository,
            defeatResult,
            "遷都敗北回合事件城池引用"
        );
        assertEquals(
            ScenarioObjectiveStatus.FAILED,
            gameState.scenarioObjectiveStatus,
            "原首都失守後劇本目標失敗"
        );
        assertEquals(GameplayStatus.ACTIVE, gameState.gameplayStatus, "仍有城池時遊戲繼續");
        assertContainsEvent(
            defeatResult,
            TurnEventType.CAMPAIGN_DEFEAT_CAPITAL,
            "主城失守事件"
        );
        assertTrue(gameState.requirePlayerFactionState().active, "仍有其他城時勢力可保持 active");
        assertEquals("guangling", gameState.requirePlayerFactionState().capitalCityId, "敗北後替代主城");
        assertEquals(NationalActionPointRules.calculateMonthlyActionPoints(gameState), gameState.actionPointsRemaining, "遷都後恢復下月行動力");
        TurnResolutionResult continuedResult = commands.endTurnCommand.execute(SAVE_SLOT, gameState);
        assertEquals(7, continuedResult.getGameState().currentMonth, "遷都後仍可繼續推進月份");
    }

    private static void validateTurnLimitFreePlay(
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
        assertEquals(
            ScenarioObjectiveStatus.FAILED,
            gameState.scenarioObjectiveStatus,
            "超過期限後劇本目標失敗"
        );
        assertEquals(GameplayStatus.ACTIVE, gameState.gameplayStatus, "期限失敗後仍可自由征戰");
        assertContainsEvent(
            timeoutResult,
            TurnEventType.CAMPAIGN_DEFEAT_TIMEOUT,
            "期限敗北事件"
        );
        assertEquals(NationalActionPointRules.calculateMonthlyActionPoints(gameState), gameState.actionPointsRemaining, "期限失敗後仍恢復行動力");
        TurnResolutionResult continuedResult = commands.endTurnCommand.execute(SAVE_SLOT, gameState);
        assertEquals(3, continuedResult.getGameState().currentMonth, "期限失敗後仍可繼續月份");
    }

    private static void validatePlayerElimination(
        AssetJsonGameDefinitionRepository definitionRepository,
        FileHandle saveDirectory
    ) {
        CommandSet commands = new CommandSet(definitionRepository, saveDirectory);
        GameState gameState = commands.newGame();
        gameState.enemyAttackCountdown = 1;
        gameState.requireCityState(PLAYER_CAPITAL_ID).troops = 1;

        TurnResolutionResult marchResult = commands.endTurnCommand.execute(
            SAVE_SLOT,
            gameState
        );
        gameState = marchResult.getGameState();
        assertContainsEvent(marchResult, TurnEventType.ENEMY_MARCHING, "敵軍應建立行軍部隊");
        assertEquals(1, gameState.armyStates.length, "敵軍行軍部隊數");

        TurnResolutionResult eliminationResult = commands.endTurnCommand.execute(
            SAVE_SLOT,
            gameState
        );
        gameState = eliminationResult.getGameState();
        assertTurnEventCityReferencesValid(
            definitionRepository,
            eliminationResult,
            "玩家滅亡回合事件城池引用"
        );
        assertContainsEvent(
            eliminationResult,
            TurnEventType.PLAYER_ELIMINATED,
            "失去最後一座城後必須產生滅亡事件"
        );
        assertEquals(
            ScenarioObjectiveStatus.FAILED,
            gameState.scenarioObjectiveStatus,
            "玩家滅亡後劇本目標失敗"
        );
        assertEquals(
            GameplayStatus.ELIMINATED,
            gameState.gameplayStatus,
            "失去全部城池後遊戲狀態"
        );
        assertTrue(!gameState.requirePlayerFactionState().active, "玩家勢力必須失效");
        assertEquals(0, gameState.findCitiesOwnedBy(PLAYER_FACTION_ID).size(), "玩家城池數");
        assertEquals(0, gameState.actionPointsRemaining, "玩家滅亡後行動力");
        assertEquals(1, gameState.countUnreadBattleReports(), "滅亡戰鬥仍須留下未讀戰報");

        boolean rejected = false;
        try {
            commands.endTurnCommand.execute(SAVE_SLOT, gameState);
        } catch (IllegalStateException expectedException) {
            rejected = true;
        }
        assertTrue(rejected, "玩家勢力滅亡後不可繼續推進月份");
    }

    @SuppressWarnings("deprecation")
    private static void validateSchemaTwoMigration(
        AssetJsonGameDefinitionRepository definitionRepository,
        FileHandle saveDirectory
    ) {
        CommandSet commands = new CommandSet(definitionRepository, saveDirectory);
        GameState legacyState = commands.newGame().copy();
        legacyState.schemaVersion = SangoVersion.LEGACY_GAME_STATE_SCHEMA_VERSION;
        legacyState.campaignStatus = CampaignStatus.DEFEAT;
        legacyState.scenarioObjectiveStatus = null;
        legacyState.gameplayStatus = null;
        legacyState.nextBattleSequence = 0;
        legacyState.battleReports = null;
        legacyState.actionPointsRemaining = 0;

        SaveGameDocument legacyDocument = new SaveGameDocument();
        legacyDocument.schemaVersion = SangoVersion.SAVE_DOCUMENT_SCHEMA_VERSION;
        legacyDocument.gameVersion = "0.3.0";
        legacyDocument.savedAtEpochMillis = 1_725_552_000_000L;
        legacyDocument.gameState = legacyState;

        saveDirectory.mkdirs();
        Json json = new Json();
        json.setIgnoreUnknownFields(false);
        json.setUsePrototypes(false);
        json.setOutputType(JsonWriter.OutputType.json);
        saveDirectory.child("slot-01.json").writeString(
            json.prettyPrint(legacyDocument),
            false,
            "UTF-8"
        );

        LocalJsonSaveGameRepository migrationRepository = new LocalJsonSaveGameRepository(
            saveDirectory
        );
        SaveSlotInspection inspection = migrationRepository.inspect(SAVE_SLOT);
        assertEquals(SaveSlotState.AVAILABLE, inspection.getState(), "舊存檔遷移後槽位狀態");
        assertNotNull(inspection.getMetadata(), "遷移後槽位摘要");
        assertEquals(
            ScenarioObjectiveStatus.FAILED,
            inspection.getMetadata().getObjectiveStatus(),
            "遷移後槽位目標狀態"
        );

        GameState migratedState = migrationRepository.load(SAVE_SLOT);
        assertEquals(
            SangoVersion.GAME_STATE_SCHEMA_VERSION,
            migratedState.schemaVersion,
            "遷移後 GameState schema"
        );
        assertEquals(null, migratedState.campaignStatus, "遷移後舊欄位應清空");
        assertEquals(
            ScenarioObjectiveStatus.FAILED,
            migratedState.scenarioObjectiveStatus,
            "遷移後目標狀態"
        );
        assertEquals(GameplayStatus.ACTIVE, migratedState.gameplayStatus, "遷移後可繼續遊玩");
        assertEquals(
            migratedState.actionPointsPerTurn,
            migratedState.actionPointsRemaining,
            "舊敗北存檔遷移後恢復行動力"
        );
        assertEquals(1, migratedState.nextBattleSequence, "遷移後戰報序號");
        assertEquals(0, migratedState.battleReports.length, "遷移後戰報陣列");

        migrationRepository.save(SAVE_SLOT, migratedState);
        GameState reloadedState = migrationRepository.load(SAVE_SLOT);
        assertEquals(
            SangoVersion.GAME_STATE_SCHEMA_VERSION,
            reloadedState.schemaVersion,
            "遷移狀態重新保存後 schema"
        );
        assertEquals(
            ScenarioObjectiveStatus.FAILED,
            reloadedState.scenarioObjectiveStatus,
            "遷移狀態重新保存後目標狀態"
        );
    }

    private static void validateAudioAssets(Path assetsPath) throws IOException {
        String[] audioAssetPaths = {
            "audio/music/lobby_theme.mp3",
            "audio/music/strategy_theme.mp3",
            "audio/sfx/ui_click.ogg",
            "audio/sfx/confirm.ogg",
            "audio/sfx/cancel.ogg",
            "audio/sfx/command_success.ogg",
            "audio/sfx/command_error.ogg",
            "audio/sfx/end_month.ogg",
            "audio/sfx/save_complete.ogg",
            "audio/sfx/battle_alert.ogg",
            "audio/sfx/battle_impact.ogg",
            "audio/sfx/city_captured.ogg",
            "audio/sfx/objective_success.ogg",
            "audio/sfx/objective_failed.ogg"
        };
        for (String audioAssetPath : audioAssetPaths) {
            Path audioPath = assetsPath.resolve(audioAssetPath);
            assertTrue(Files.isRegularFile(audioPath), "缺少音訊資產：" + audioAssetPath);
            assertTrue(
                Files.size(audioPath) > 256L,
                "音訊資產內容過小或無效：" + audioAssetPath
            );
        }
    }

    private static void validateSaveRecoveryAndSlots(
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

        commands.saveGameRepository.save(2, gameState);
        commands.saveGameRepository.save(3, gameState);
        SaveSlotInspection slotTwoInspection = commands.saveGameRepository.inspect(2);
        SaveSlotInspection slotThreeInspection = commands.saveGameRepository.inspect(3);
        assertEquals(SaveSlotState.AVAILABLE, slotTwoInspection.getState(), "存檔槽 2 狀態");
        assertEquals(SaveSlotState.AVAILABLE, slotThreeInspection.getState(), "存檔槽 3 狀態");
        SaveSlotMetadata slotTwoMetadata = slotTwoInspection.getMetadata();
        assertNotNull(slotTwoMetadata, "存檔槽 2 摘要");
        assertEquals(2, slotTwoMetadata.getSlotNumber(), "槽位摘要編號");
        assertEquals(PLAYER_FACTION_ID, slotTwoMetadata.getPlayerFactionId(), "槽位摘要勢力");
        assertEquals(PLAYER_CAPITAL_ID, slotTwoMetadata.getCapitalCityId(), "槽位摘要首都");
        assertEquals(1, slotTwoMetadata.getOwnedCityCount(), "槽位摘要領地數");
        assertEquals(gameState.currentTurn, slotTwoMetadata.getCurrentTurn(), "槽位摘要回合");
        assertEquals(
            ScenarioObjectiveStatus.IN_PROGRESS,
            slotTwoMetadata.getObjectiveStatus(),
            "槽位摘要目標狀態"
        );

        commands.saveGameRepository.delete(2);
        assertEquals(
            SaveSlotState.EMPTY,
            commands.saveGameRepository.inspect(2).getState(),
            "刪除槽位 2 後狀態"
        );
        assertEquals(
            SaveSlotState.AVAILABLE,
            commands.saveGameRepository.inspect(3).getState(),
            "刪除槽位 2 不可影響槽位 3"
        );

        // 第二次寫入 slot 1，讓初始新局成為可驗證的備份。
        commands.saveGameRepository.save(SAVE_SLOT, gameState);
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
        assertNotNull(recoveryInspection.getMetadata(), "復原候選仍應提供槽位摘要");
        GameState recoveredState = commands.saveGameRepository.load(SAVE_SLOT);
        assertEquals(40, recoveredState.requireCityState(PLAYER_CAPITAL_ID).agriculture, "備份應為前一版本");
        assertEquals(1200, recoveredState.requirePlayerFactionState().gold, "備份金");

        commands.saveGameRepository.delete(SAVE_SLOT);
        commands.saveGameRepository.delete(3);
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

    private static void assertTurnEventCityReferencesValid(
        AssetJsonGameDefinitionRepository definitionRepository,
        TurnResolutionResult resolutionResult,
        String message
    ) {
        for (TurnEvent turnEvent : resolutionResult.getReport().getEvents()) {
            assertOptionalCityReference(
                definitionRepository,
                turnEvent.getCityId(),
                message + " / cityId / " + turnEvent.getType()
            );
            assertOptionalCityReference(
                definitionRepository,
                turnEvent.getOtherCityId(),
                message + " / otherCityId / " + turnEvent.getType()
            );
        }
    }

    private static void assertOptionalCityReference(
        AssetJsonGameDefinitionRepository definitionRepository,
        String cityId,
        String message
    ) {
        if (cityId == null || cityId.isEmpty()) {
            return;
        }
        try {
            definitionRepository.requireCity(cityId);
        } catch (IllegalArgumentException invalidCityReferenceException) {
            throw new AssertionError(
                message + "，不是有效的城池 ID：" + cityId,
                invalidCityReferenceException
            );
        }
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
            domesticActionCommand = new ExecuteDomesticActionCommand();
            scoutCityCommand = new ScoutCityCommand(definitionRepository);
            launchExpeditionCommand = new LaunchExpeditionCommand(definitionRepository);
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
