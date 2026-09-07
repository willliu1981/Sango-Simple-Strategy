package idv.kuan.studio.sango.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.badlogic.gdx.files.FileHandle;

import idv.kuan.studio.sango.application.command.ExecuteDomesticActionCommand;
import idv.kuan.studio.sango.application.command.LaunchExpeditionCommand;
import idv.kuan.studio.sango.application.command.NewGameCommand;
import idv.kuan.studio.sango.application.request.NewGameRequest;
import idv.kuan.studio.sango.application.result.DomesticActionResult;
import idv.kuan.studio.sango.application.result.StrategicActionResult;
import idv.kuan.studio.sango.application.result.TurnEvent;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionResult;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.ScenarioObjectiveStatus;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.DomesticActionFailureReason;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;
import idv.kuan.studio.sango.domain.rule.NationalActionPointRules;
import idv.kuan.studio.sango.domain.service.DeterministicEventRoller;
import idv.kuan.studio.sango.domain.service.TurnResolutionService;
import idv.kuan.studio.sango.repository.definition.AssetJsonGameDefinitionRepository;
import idv.kuan.studio.sango.repository.save.GameStateMigrator;
import idv.kuan.studio.sango.repository.save.LocalJsonSaveGameRepository;
import idv.kuan.studio.sango.ui.support.NationalOrderTextFormatter;

/**
 * 全城民心與 3–9 點行動力的無圖形回歸測試。僅使用臨時存檔，不碰玩家進度。
 */
public final class NationalActionPointSmokeTest {
    private static int checks;

    private NationalActionPointSmokeTest() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("需要 assets 目錄路徑。");
        }
        Path assetsPath = Path.of(arguments[0]).toAbsolutePath().normalize();
        FileHandle temporaryDirectory = new FileHandle(Files.createTempDirectory("sango-060-ap-").toFile());
        try {
            AssetJsonGameDefinitionRepository definitions = new AssetJsonGameDefinitionRepository(
                new FileHandle(assetsPath.resolve("data/scenarios/scenarios.json").toFile()),
                new FileHandle(assetsPath.resolve("data/factions/factions.json").toFile()),
                new FileHandle(assetsPath.resolve("data/cities/cities.json").toFile()),
                new FileHandle(assetsPath.resolve("data/maps/maps.json").toFile())
            );
            LocalJsonSaveGameRepository saves = new LocalJsonSaveGameRepository(temporaryDirectory);
            NewGameCommand newGameCommand = new NewGameCommand(definitions, saves);
            GameState initialState = newGameCommand.execute(1, new NewGameRequest("warlords_china", "cao_cao"));
            validateAggregation(initialState);
            validateThresholdCurve();
            validateMonthlySnapshot(initialState, definitions, saves);
            validateOccupation(initialState, definitions, saves);
            validateFloodBeforeRefresh(initialState, definitions);
            validateSaveCompatibility(initialState, definitions, saves);
            System.out.println("Sango national public order and 3-9 AP: PASS; checks=" + checks);
        } finally {
            temporaryDirectory.deleteDirectory();
        }
    }

    private static void validateThresholdCurve() {
        int[] thresholds = {200, 400, 650, 900, 1150, 1500};
        for (int i = 0; i <= 4200; i++) {
            int expected = 3;
            for (int threshold : thresholds) {
                if (i >= threshold) {
                    expected += 1;
                }
            }
            NationalActionPointRules.PublicOrderSummary summary =
                new NationalActionPointRules.PublicOrderSummary(42, i);
            check(summary.monthlyActionPoints() == expected, "全部 4201 個民心總和與階梯門檻");
        }
    }

    private static void validateAggregation(GameState initialState) {
        GameState gameState = initialState.copy();
        List<CityState> ownedCities = gameState.findCitiesOwnedBy(gameState.playerFactionId);
        check(ownedCities.size() == 2, "測試基準需有兩座領地");
        CityState firstCity = ownedCities.get(0);
        CityState secondCity = ownedCities.get(1);
        for (int i = 0; i <= 100; i++) {
            for (int j = 0; j <= 100; j++) {
                firstCity.publicOrder = i;
                secondCity.publicOrder = j;
                NationalActionPointRules.PublicOrderSummary summary = NationalActionPointRules.summarizePlayer(gameState);
                int expectedPoints = (i + j >= 200 ? 4 : 3);
                check(summary.cityCount() == 2 && summary.totalPublicOrder() == i + j, "只聚合玩家的全部領地");
                check(summary.monthlyActionPoints() == expectedPoints, "雙城 10201 組民心組合與精確門檻");
                check(summary.averagePublicOrderTenths() == (i + j) * 5, "平均值保留一位小數");
            }
        }
        firstCity.publicOrder = 89;
        secondCity.publicOrder = 90;
        check(NationalActionPointRules.calculateMonthlyActionPoints(gameState) == 3, "總和 179 仍為 3 點");
        firstCity.publicOrder = 90;
        check(NationalActionPointRules.calculateMonthlyActionPoints(gameState) == 3, "雙城各 90 只有 180 點總民心");
        firstCity.population = 1;
        secondCity.population = 999999;
        firstCity.publicOrder = 0;
        secondCity.publicOrder = 100;
        firstCity.morale = 100;
        secondCity.morale = 0;
        check(NationalActionPointRules.calculateMonthlyActionPoints(gameState) == 3, "總民心不受人口或士氣影響");
        for (CityState cityState : gameState.cityStates) {
            if (!gameState.playerFactionId.equals(cityState.ownerFactionId)) {
                cityState.publicOrder = 0;
            }
        }
        check(NationalActionPointRules.calculateMonthlyActionPoints(gameState) == 3, "敵方與中立民心不得影響玩家");
        check(NationalOrderTextFormatter.formatAverage(895).equals("89.5"), "UI 顯示一位小數");
        check(NationalOrderTextFormatter.formatAverage(1000).equals("100.0"), "UI 正確顯示上界");
        gameState.gameplayStatus = GameplayStatus.ELIMINATED;
        check(NationalActionPointRules.calculateMonthlyActionPoints(gameState) == 0, "滅亡後不可獲得最低三點");
        gameState.gameplayStatus = GameplayStatus.ACTIVE;
        firstCity.ownerFactionId = gameState.neutralFactionId;
        secondCity.ownerFactionId = gameState.neutralFactionId;
        check(NationalActionPointRules.calculateMonthlyActionPoints(gameState) == 0, "無領地時不除以零也不發點");
        check(NationalActionPointRules.summarizePlayer(gameState).averagePublicOrderTenths() == 0, "無領地摘要可顯示");
    }

    private static void validateMonthlySnapshot(
        GameState initialState,
        AssetJsonGameDefinitionRepository definitions,
        LocalJsonSaveGameRepository saves
    ) {
        ExecuteDomesticActionCommand domesticCommand = new ExecuteDomesticActionCommand(saves);
        TurnResolutionService turnService = new TurnResolutionService(definitions);
        for (ScenarioObjectiveStatus objectiveStatus : ScenarioObjectiveStatus.values()) {
            GameState gameState = initialState.copy();
            gameState.scenarioObjectiveStatus = objectiveStatus;
            gameState.enemyAttackCountdown = 99;
            gameState.actionPointsRemaining = 2;
            int previousCapacity = gameState.actionPointsPerTurn;
            for (CityState cityState : gameState.findCitiesOwnedBy(gameState.playerFactionId)) {
                cityState.publicOrder = 90;
                cityState.training = 50;
            }
            for (int i = 0; i < 20; i++) {
                check(NationalActionPointRules.calculateMonthlyActionPoints(gameState) == 3, "可重複查詢下月預估");
                check(gameState.actionPointsRemaining == 2 && gameState.actionPointsPerTurn == previousCapacity,
                    "預估不改寫當月額度或剩餘行動力");
            }
            DomesticActionResult commandResult = domesticCommand.execute(1, gameState, "chenliu", DomesticActionType.TRAIN);
            check(commandResult.isSuccessful(), "正常內政命令仍可執行");
            check(commandResult.getGameState().actionPointsRemaining == 1, "命令只消耗一點，不按新民心回補");
            check(gameState.actionPointsRemaining == 2, "命令維持 copy-on-write");
            TurnResolutionResult resolution = turnService.resolve(commandResult.getGameState());
            GameState nextState = resolution.getGameState();
            check(nextState.actionPointsPerTurn == 3 && nextState.actionPointsRemaining == 3,
                "各任務結果於下月都依民心發三點，不累加舊點數");
            check(nextState.currentMonth == gameState.currentMonth + 1, "月份仍正常前進一次");
            int refreshEventCount = 0;
            for (TurnEvent event : resolution.getReport().getEvents()) {
                if (event.getType() == TurnEventType.ACTION_POINTS_REFRESHED) {
                    refreshEventCount += 1;
                    check(event.getPrimaryValue() == 3 && event.getSecondaryValue() == 180,
                        "月報明確記錄發放額度與結算後民心總和");
                    check(event.getCityId() == null && event.getOtherCityId() == null,
                        "民心事件不把勢力 ID 塞進城池欄位");
                }
            }
            check(refreshEventCount == 1, "每次月底只產生一次行動力發放事件");
        }
        GameState zeroOrderState = initialState.copy();
        zeroOrderState.enemyAttackCountdown = 99;
        for (CityState cityState : zeroOrderState.findCitiesOwnedBy(zeroOrderState.playerFactionId)) {
            cityState.publicOrder = 0;
        }
        check(turnService.resolve(zeroOrderState).getGameState().actionPointsRemaining == 3, "零民心仍保留最低三點");
    }

    private static void validateOccupation(
        GameState initialState,
        AssetJsonGameDefinitionRepository definitions,
        LocalJsonSaveGameRepository saves
    ) {
        GameState gameState = initialState.copy();
        gameState.enemyAttackCountdown = 99;
        for (CityState cityState : gameState.findCitiesOwnedBy(gameState.playerFactionId)) {
            cityState.publicOrder = 90;
        }
        gameState.requireCityState("chenliu").troops = 1800;
        gameState.requireCityState("runan").troops = 0;
        gameState.requireCityState("runan").publicOrder = 5;
        StrategicActionResult launchResult = new LaunchExpeditionCommand(definitions, saves)
            .execute(1, gameState, "chenliu", "runan", BattleTactic.BALANCED);
        check(launchResult.isSuccessful(), "可向低民心空城出征");
        GameState nextState = new TurnResolutionService(definitions).resolve(launchResult.getGameState()).getGameState();
        NationalActionPointRules.PublicOrderSummary summary = NationalActionPointRules.summarizePlayer(nextState);
        check(summary.cityCount() == 3 && summary.totalPublicOrder() == 180,
            "新佔領空城民心扣五點後立即納入下月聚合");
        check(nextState.actionPointsPerTurn == 3 && nextState.actionPointsRemaining == 3,
            "先攻佔再計算總和，新增零民心城不會降低既有總民心");
    }

    private static void validateFloodBeforeRefresh(
        GameState initialState,
        AssetJsonGameDefinitionRepository definitions
    ) {
        GameState gameState = initialState.copy();
        gameState.currentMonth = 6;
        gameState.enemyAttackCountdown = 99;
        for (CityState cityState : gameState.findCitiesOwnedBy(gameState.playerFactionId)) {
            cityState.publicOrder = 100;
            cityState.waterControl = 0;
        }
        DeterministicEventRoller eventRoller = new DeterministicEventRoller();
        boolean foundFloodYear = false;
        for (int i = 190; i < 240; i++) {
            for (CityState cityState : gameState.findCitiesOwnedBy(gameState.playerFactionId)) {
                if (eventRoller.rollPercent(gameState.scenarioId, cityState.cityId, i, "FLOOD") < 60) {
                    gameState.currentYear = i;
                    foundFloodYear = true;
                    break;
                }
            }
            if (foundFloodYear) {
                break;
            }
        }
        check(foundFloodYear, "找到可重現洪災的固定年月");
        TurnResolutionResult result = new TurnResolutionService(definitions).resolve(gameState);
        GameState nextState = result.getGameState();
        check(NationalActionPointRules.summarizePlayer(nextState).averagePublicOrderTenths() < 1000,
            "洪災先降低民心");
        check(nextState.actionPointsPerTurn == 3 && nextState.actionPointsRemaining == 3,
            "下月以洪災後總民心計算三點，不誤發災前四點");
        check(gameState.actionPointsRemaining == initialState.actionPointsRemaining,
            "月底結算不修改輸入狀態");
    }

    private static void validateSaveCompatibility(
        GameState initialState,
        AssetJsonGameDefinitionRepository definitions,
        LocalJsonSaveGameRepository saves
    ) {
        GameState legacyState = initialState.copy();
        legacyState.actionPointsPerTurn = 3;
        legacyState.actionPointsRemaining = 2;
        legacyState.enemyAttackCountdown = 99;
        for (CityState cityState : legacyState.findCitiesOwnedBy(legacyState.playerFactionId)) {
            cityState.publicOrder = 90;
        }
        saves.save(1, legacyState);
        for (int i = 0; i < 5; i++) {
            GameState loadedState = saves.load(1);
            check(loadedState.schemaVersion == 5, "小數素質與 AI 額度使用 Schema 5");
            check(loadedState.actionPointsPerTurn == 3 && loadedState.actionPointsRemaining == 2,
                "舊 0.5.1 月中存檔保留 2/3，不在讀檔時回補");
            loadedState = new GameStateMigrator().migrate(loadedState);
            check(loadedState.actionPointsRemaining == 2, "現行 Schema normalize 冪等，不刷點");
            saves.save(1, loadedState);
        }
        GameState nextMonth = new TurnResolutionService(definitions).resolve(saves.load(1)).getGameState();
        check(nextMonth.actionPointsPerTurn == 3 && nextMonth.actionPointsRemaining == 3,
            "舊存檔下一月自動採新公式");
        nextMonth.actionPointsRemaining = 2;
        saves.save(2, nextMonth);
        GameState reloaded = saves.load(2);
        check(reloaded.actionPointsRemaining == 2 && reloaded.actionPointsPerTurn == 3,
            "新規則存檔保留已使用點數");
        reloaded.actionPointsRemaining = 0;
        saves.save(2, reloaded);
        DomesticActionResult rejected = new ExecuteDomesticActionCommand(saves)
            .execute(2, saves.load(2), "chenliu", DomesticActionType.TRAIN);
        check(!rejected.isSuccessful() && rejected.getFailureReason() == DomesticActionFailureReason.NO_ACTION_POINTS,
            "讀檔後零點仍拒絕操作，不因高民心補點");
        GameStateValidator.validate(saves.load(1));
        GameStateValidator.validate(saves.load(2));
    }

    private static void check(boolean condition, String description) {
        checks += 1;
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
