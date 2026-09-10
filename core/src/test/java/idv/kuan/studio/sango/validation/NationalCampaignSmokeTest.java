package idv.kuan.studio.sango.validation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

import idv.kuan.studio.sango.application.command.EndTurnCommand;
import idv.kuan.studio.sango.application.command.ExecuteDomesticActionCommand;
import idv.kuan.studio.sango.application.command.LaunchExpeditionCommand;
import idv.kuan.studio.sango.application.command.MarkBattleReportReadCommand;
import idv.kuan.studio.sango.application.command.NewGameCommand;
import idv.kuan.studio.sango.application.request.NewGameRequest;
import idv.kuan.studio.sango.application.result.DomesticActionResult;
import idv.kuan.studio.sango.application.result.StrategicActionResult;
import idv.kuan.studio.sango.application.result.TurnEvent;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.application.result.TurnResolutionResult;
import idv.kuan.studio.sango.domain.definition.CityConnectionDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.BattleOutcome;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.ScenarioObjectiveStatus;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.DefensePolicy;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;
import idv.kuan.studio.sango.domain.rule.NationalActionPointRules;
import idv.kuan.studio.sango.domain.rule.MilitaryRules;
import idv.kuan.studio.sango.domain.service.BattleResolutionService;
import idv.kuan.studio.sango.domain.service.EnemyTurnService;
import idv.kuan.studio.sango.domain.service.TurnResolutionService;
import idv.kuan.studio.sango.repository.definition.AssetJsonGameDefinitionRepository;
import idv.kuan.studio.sango.repository.save.GameStateMigrator;
import idv.kuan.studio.sango.repository.save.LocalJsonSaveGameRepository;
import idv.kuan.studio.sango.repository.save.SaveGameDocument;
import idv.kuan.studio.sango.repository.save.SaveSlotInspection;

/**
 * 全國劇本、士氣、無抵抗佔領與真實舊 schema 序列化檔的無圖形回歸測試。
 * 測試只使用暫存存檔，不讀寫玩家的 .sango/save。
 */
public final class NationalCampaignSmokeTest {
    private static final String[] PLAYABLE_FACTIONS = {
        "cao_cao", "liu_bei", "sun_ce", "yuan_shao", "liu_biao", "ma_teng"
    };
    private static int assertions;
    private final AssetJsonGameDefinitionRepository definitions;
    private final LocalJsonSaveGameRepository saves;
    private final NewGameCommand newGameCommand;
    private final ExecuteDomesticActionCommand domesticCommand;
    private final LaunchExpeditionCommand launchCommand;
    private final EndTurnCommand endTurnCommand;
    private final TurnResolutionService turnService;

    private NationalCampaignSmokeTest(Path assetsPath, FileHandle saveDirectory) {
        definitions = new AssetJsonGameDefinitionRepository(
            new FileHandle(assetsPath.resolve("data/scenarios/scenarios.json").toFile()),
            new FileHandle(assetsPath.resolve("data/factions/factions.json").toFile()),
            new FileHandle(assetsPath.resolve("data/cities/cities.json").toFile()),
            new FileHandle(assetsPath.resolve("data/maps/maps.json").toFile())
        );
        saves = new LocalJsonSaveGameRepository(saveDirectory);
        newGameCommand = new NewGameCommand(definitions, saves);
        domesticCommand = new ExecuteDomesticActionCommand();
        launchCommand = new LaunchExpeditionCommand(definitions);
        turnService = new TurnResolutionService(definitions);
        endTurnCommand = new EndTurnCommand(saves, turnService);
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("需要 assets 目錄路徑。");
        }
        FileHandle temporaryDirectory = new FileHandle(Files.createTempDirectory("sango-050-test-").toFile());
        try {
            NationalCampaignSmokeTest tests = new NationalCampaignSmokeTest(
                Path.of(arguments[0]), temporaryDirectory.child("campaign")
            );
            tests.validateWorldAndStarts();
            tests.validateMoraleCommands();
            tests.validateMilitaryFormulas();
            tests.validateUnopposedOccupationMatrix();
            tests.validateBattleMoraleAndMerging();
            tests.validateMultifactionAiAndElimination();
            tests.validateLegacySaveMigration(temporaryDirectory.child("legacy"));
            tests.validateLongRunningCampaigns();
            System.out.println("Sango national campaign regression: PASS; assertions=" + assertions
                + "; unopposed combinations=18; simulated months=288");
        } finally {
            temporaryDirectory.deleteDirectory();
        }
    }

    private GameState newGame(String factionId) {
        return newGameCommand.execute(1, new NewGameRequest("warlords_china", factionId));
    }

    private void validateWorldAndStarts() {
        StrategicMapDefinition world = definitions.requireMap("china_42");
        check(world.nodes.length == 42, "全國地圖應有 42 城");
        check(world.connections.length == 68, "全國地圖應有 68 條道路");
        check(definitions.findFactionsForScenario("warlords_china").size() == 6, "六個可選勢力");
        Map<String, String> firstOwnership = new HashMap<>();
        for (String factionId : PLAYABLE_FACTIONS) {
            GameState gameState = newGame(factionId);
            GameStateValidator.validate(gameState);
            check(gameState.cityStates.length == 42, "新局建立全部城市狀態");
            check(gameState.factionStates.length == 8, "六勢力加黃巾與中立");
            check(gameState.findCitiesOwnedBy(factionId).size() == 2, "每個可玩勢力起始兩城");
            check(gameState.actionPointsRemaining == NationalActionPointRules.calculateMonthlyActionPoints(gameState), "新局依全城民心計算行動力");
            check(!gameState.ownsCity(factionId, gameState.victoryTargetCityId), "劇本目標不是己方城");
            check(world.findConnection(gameState.requirePlayerFactionState().capitalCityId,
                gameState.victoryTargetCityId) != null, "初始劇本目標與首都相鄰");
            for (CityState cityState : gameState.cityStates) {
                String originalOwner = firstOwnership.putIfAbsent(cityState.cityId, cityState.ownerFactionId);
                check(originalOwner == null || originalOwner.equals(cityState.ownerFactionId),
                    "選擇不同玩家不應改變世界初始所有權");
                check(cityState.morale == definitions.requireCity(cityState.cityId).initialMorale,
                    "新局士氣取自 Definition");
            }
            check(saves.load(1).cityStates.length == 42, "全國新局可讀回");
        }
        Set<String> reachableCities = new HashSet<>();
        reachableCities.add(world.nodes[0].cityId);
        for (int i = 0; i < world.nodes.length; i++) {
            for (CityConnectionDefinition connection : world.connections) {
                check(connection.travelMonths == 1, "道路行軍時間一個月");
                if (reachableCities.contains(connection.fromCityId) || reachableCities.contains(connection.toCityId)) {
                    reachableCities.add(connection.fromCityId);
                    reachableCities.add(connection.toCityId);
                }
            }
        }
        check(reachableCities.size() == 42, "全部城市互相可達");
        check(definitions.requireMap("prototype_central_region").nodes.length == 6,
            "舊六城 Definition 必須保留供存檔使用");
    }

    private void validateMoraleCommands() {
        GameState originalState = newGame("cao_cao");
        originalState.requireCapitalCityState().morale = 3;
        originalState.requireCapitalCityState().training = 3;
        int previousTroops = originalState.requireCapitalCityState().troops;
        DomesticActionResult recruitment = domesticCommand.execute(1, originalState, "chenliu", DomesticActionType.RECRUIT);
        check(recruitment.isSuccessful(), "徵兵成功");
        GameState nextState = recruitment.getGameState();
        check(nextState.requireCapitalCityState().morale > 3, "新兵士氣高於舊兵時，按兵數加權提升");
        check(nextState.requireCapitalCityState().training > 3, "新兵訓練高於舊兵時，按兵數加權提升");
        check(nextState.requireCapitalCityState().troops == previousTroops + 200, "徵兵增加 200 人");
        check(originalState.requireCapitalCityState().morale == 3, "徵兵使用 copy-on-write");
        check(originalState.requireCapitalCityState().training == 3, "原狀態訓練不變");
        check(originalState.requireCapitalCityState().troops == previousTroops, "原狀態兵力不變");
        nextState.requireCapitalCityState().training = 100;
        nextState.requireCapitalCityState().trainingFraction = 0;
        int moraleBeforeTraining = nextState.requireCapitalCityState().morale;
        DomesticActionResult training = domesticCommand.execute(1, nextState, "chenliu", DomesticActionType.TRAIN);
        check(training.isSuccessful(), "訓練滿值但士氣不足時仍可訓練");
        nextState = training.getGameState();
        check(nextState.requireCapitalCityState().training == 100, "訓練上限 100");
        check(nextState.requireCapitalCityState().morale == moraleBeforeTraining + 5, "覆蓋全部兵力時士氣增加 5");
        nextState.requireCapitalCityState().morale = 98;
        nextState.requireCapitalCityState().moraleFraction = 0;
        nextState = domesticCommand.execute(1, nextState, "chenliu", DomesticActionType.TRAIN).getGameState();
        check(nextState.requireCapitalCityState().morale == 100, "士氣上限 100");
        nextState.actionPointsRemaining = 3;
        check(!domesticCommand.execute(1, nextState, "chenliu", DomesticActionType.TRAIN).isSuccessful(),
            "訓練與士氣皆滿時不浪費資源");
        nextState.requireCapitalCityState().morale = 0;
        nextState.requireCapitalCityState().moraleFraction = 0;
        StrategicActionResult expedition = launchCommand.execute(1, nextState, "chenliu", "runan", BattleTactic.FEINT);
        check(expedition.isSuccessful(), "零士氣仍能出征");
        check(expedition.getGameState().armyStates[0].morale == 0, "出征不可將零士氣重設為 50");
        check(saves.load(1).armyStates.length == 0, "出征後尚未存檔，讀取可回到操作前");
    }

    private void validateMilitaryFormulas() {
        ArmyState armyState = army("cao_cao", "chenliu", "runan", 1000, 50, 0);
        check(MilitaryRules.calculateAttackerStrength(armyState) == 1125, "士氣 0 = 基準 0.75 倍");
        armyState.morale = 50;
        check(MilitaryRules.calculateAttackerStrength(armyState) == 1500, "士氣 50 = 基準 1 倍");
        armyState.morale = 100;
        check(MilitaryRules.calculateAttackerStrength(armyState) == 1875, "士氣 100 = 基準 1.25 倍");
        armyState.tactic = BattleTactic.ASSAULT;
        check(MilitaryRules.calculateAttackerStrength(armyState) == 1875,
            "戰術不再直接改變基礎戰力");
        check(MilitaryRules.applyMatchupPercent(
            MilitaryRules.calculateAttackerStrength(armyState),
            MilitaryRules.attackerMatchupPercent(BattleTactic.ASSAULT,
                DefensePolicy.HOLD)) == 2062,
            "強攻克制固守時才取得 10% 戰力加成");
        CityState cityState = new CityState();
        cityState.troops = 1000;
        cityState.training = 50;
        cityState.morale = 50;
        cityState.defense = 100;
        check(MilitaryRules.calculateDefenderStrength(cityState) == 2250, "城防 100 = 1.5 倍");
        cityState.defense = 41;
        check(MilitaryRules.calculateDefenderStrength(cityState) == 1800, "城防先做整數除法");
        cityState.defense = 42;
        check(MilitaryRules.calculateDefenderStrength(cityState) == 1815, "下一個偶數城防產生加成");
        cityState.troops = 0;
        check(MilitaryRules.calculateDefenderStrength(cityState) == 0, "空城守軍戰力為零");
        cityState.troops = Integer.MAX_VALUE;
        cityState.training = 100;
        cityState.morale = 100;
        cityState.defense = 100;
        check(MilitaryRules.calculateDefenderStrength(cityState) == Integer.MAX_VALUE,
            "大兵力中間計算不溢位");
        check(MilitaryRules.weightedQuality(400, 80, 600, 40) == 56, "部隊合併採加權士氣");
    }

    private void validateUnopposedOccupationMatrix() {
        for (ScenarioObjectiveStatus objectiveStatus : ScenarioObjectiveStatus.values()) {
            for (BattleTactic tactic : BattleTactic.values()) {
                for (String targetCityId : new String[] {"runan", "luoyang"}) {
                    GameState gameState = newGame("cao_cao");
                    gameState.scenarioObjectiveStatus = objectiveStatus;
                    gameState.enemyAttackCountdown = 99;
                    gameState.requireCapitalCityState().troops = 1800;
                    gameState.requireCapitalCityState().morale = 71;
                    CityState targetCity = gameState.requireCityState(targetCityId);
                    targetCity.troops = 0;
                    targetCity.defense = 80;
                    targetCity.publicOrder = 60;
                    StrategicActionResult expedition = launchCommand.execute(1, gameState, "chenliu", targetCityId, tactic);
                    check(expedition.isSuccessful(), "任務各狀態皆可進攻空城");
                    TurnResolutionResult resolution = endTurnCommand.execute(1, expedition.getGameState());
                    gameState = resolution.getGameState();
                    verifyReportReferences(resolution.getReport());
                    BattleReport battleReport = gameState.battleReports[0];
                    check(battleReport.outcome == BattleOutcome.UNOPPOSED_OCCUPATION, "空城明確標示無抵抗佔領");
                    check(battleReport.attackerLosses == 0 && battleReport.defenderLosses == 0, "空城零戰鬥傷亡");
                    check(battleReport.attackerSurvivors == expedition.getDispatchedTroops(), "進城兵力無憑空增減");
                    targetCity = gameState.requireCityState(targetCityId);
                    check(targetCity.ownerFactionId.equals("cao_cao"), "空城所有權切換");
                    check(targetCity.troops == expedition.getDispatchedTroops(), "攻軍轉為守軍");
                    check(targetCity.morale == 71 && battleReport.attackerMorale == 71, "士氣快照與駐軍士氣一致");
                    check(targetCity.defense == 75 && targetCity.publicOrder == 55, "無抵抗佔領只損耗 5 城防與民心");
                    check(gameState.gameplayStatus == GameplayStatus.ACTIVE && gameState.actionPointsRemaining == NationalActionPointRules.calculateMonthlyActionPoints(gameState),
                        "目標結果不限制自由征戰且下月回滿行動力");
                    if (objectiveStatus != ScenarioObjectiveStatus.IN_PROGRESS) {
                        check(gameState.scenarioObjectiveStatus == objectiveStatus, "目標結論不被後續佔領覆寫");
                    }
                    GameState markedState = new MarkBattleReportReadCommand().execute(1, gameState, battleReport.battleId);
                    check(markedState.battleReports[0].read && !saves.load(1).battleReports[0].read,
                        "戰報已讀尚未明確存檔，讀取可回到原狀態");
                    check(saves.load(1).requireCityState(targetCityId).morale == 71, "佔領城市存檔士氣保留");
                }
            }
        }
    }

    private void validateBattleMoraleAndMerging() {
        for (int morale : new int[] {0, 100}) {
            GameState gameState = newGame("cao_cao");
            CityState targetCity = gameState.requireCityState("runan");
            targetCity.troops = 1000;
            targetCity.training = 50;
            targetCity.morale = 50;
            targetCity.defense = 0;
            new BattleResolutionService().resolveArrival(gameState,
                army("cao_cao", "chenliu", "runan", 1000, 50, morale), definitions.requireMap(gameState.mapId),
                new TurnResolutionReport(190, 1));
            check(gameState.battleReports[0].cityCaptured == (morale == 100), "士氣差異確實改變戰鬥勝負");
            check(gameState.battleReports[0].outcome != BattleOutcome.UNOPPOSED_OCCUPATION,
                "城防為零不代表無守軍");
            GameStateValidator.validate(gameState);
        }
        GameState gameState = newGame("cao_cao");
        CityState friendlyCity = gameState.requireCityState("xuchang");
        friendlyCity.troops = 400;
        friendlyCity.morale = 80;
        friendlyCity.training = 70;
        new BattleResolutionService().resolveArrival(gameState,
            army("cao_cao", "chenliu", "xuchang", 600, 20, 40), definitions.requireMap(gameState.mapId),
            new TurnResolutionReport(190, 1));
        check(friendlyCity.troops == 1000 && friendlyCity.morale == 56 && friendlyCity.training == 40,
            "增援以兵力加權，不能洗出滿士氣或訓練");
        GameStateValidator.validate(gameState);
    }

    private void validateMultifactionAiAndElimination() {
        GameState gameState = newGame("cao_cao");
        gameState.enemyAttackCountdown = 0;
        new EnemyTurnService().execute(gameState, definitions.requireMap(gameState.mapId), new TurnResolutionReport(190, 1));
        Set<String> movingFactions = new HashSet<>();
        for (ArmyState armyState : gameState.armyStates) {
            movingFactions.add(armyState.factionId);
            check(!armyState.factionId.equals("cao_cao") && !armyState.factionId.equals("neutral"),
                "AI 不代替玩家下指令，中立不主動出兵");
        }
        check(movingFactions.size() == 6, "五個未選勢力與黃巾均可行動");
        GameStateValidator.validate(gameState);

        gameState = newGame("cao_cao");
        for (CityState cityState : gameState.findCitiesOwnedBy("yellow_turban")) {
            cityState.ownerFactionId = "neutral";
        }
        gameState.requireFactionState("yellow_turban").active = false;
        gameState.requireFactionState("yellow_turban").capitalCityId = "";
        gameState.enemyAttackCountdown = 0;
        new EnemyTurnService().execute(gameState, definitions.requireMap(gameState.mapId), new TurnResolutionReport(190, 1));
        Set<String> survivingAiFactions = new HashSet<>();
        for (ArmyState armyState : gameState.armyStates) {
            survivingAiFactions.add(armyState.factionId);
        }
        check(survivingAiFactions.size() == 5 && gameState.armyStates.length >= 5,
            "黃巾滅亡後其他 AI 仍可各自出兵，且不受單一野戰軍上限限制");
        GameStateValidator.validate(gameState);

        gameState = newGame("cao_cao");
        ArmyState marchingPlayerArmy = army("cao_cao", "chenliu", "runan", 500, 50, 60);
        marchingPlayerArmy.armyId = gameState.allocateArmyId();
        gameState.addArmy(marchingPlayerArmy);
        BattleResolutionService battles = new BattleResolutionService();
        gameState.requireCityState("chenliu").troops = 0;
        battles.resolveArrival(gameState, army("yellow_turban", "runan", "chenliu", 600, 50, 60),
            definitions.requireMap(gameState.mapId), new TurnResolutionReport(190, 1));
        check(gameState.gameplayStatus == GameplayStatus.ACTIVE, "首都失守但有城仍能繼續");
        check(gameState.requirePlayerFactionState().capitalCityId.equals("xuchang"), "自動遷都到許昌");
        check(gameState.hasArmyForFaction("cao_cao"), "尚存勢力的野戰軍不可消失");
        gameState.requireCityState("xuchang").troops = 0;
        battles.resolveArrival(gameState, army("yellow_turban", "runan", "xuchang", 600, 50, 60),
            definitions.requireMap(gameState.mapId), new TurnResolutionReport(190, 1));
        check(gameState.gameplayStatus == GameplayStatus.ELIMINATED && gameState.actionPointsRemaining == 0,
            "全部城池失去才停止操作");
        check(!gameState.hasArmyForFaction("cao_cao"), "已滅亡勢力的在途軍隊解散");
        GameStateValidator.validate(gameState);
        GameState eliminatedState = gameState;
        check(!launchCommand.execute(1, eliminatedState, "chenliu", "runan", BattleTactic.FEINT).isSuccessful(),
            "滅亡狀態拒絕出征");
        expectRuntimeFailure(() -> turnService.resolve(eliminatedState), "滅亡狀態拒絕推進月份");
    }

    private void validateLegacySaveMigration(FileHandle legacyDirectory) throws IOException {
        byte[] fixtureBytes;
        try (InputStream fixtureStream = NationalCampaignSmokeTest.class.getResourceAsStream("/save/schema3-campaign.json")) {
            if (fixtureStream == null) {
                throw new IOException("缺少實際舊序列化格式的 schema 3 測試存檔。");
            }
            fixtureBytes = fixtureStream.readAllBytes();
        }
        legacyDirectory.mkdirs();
        legacyDirectory.child("slot-01.json").writeBytes(fixtureBytes, false);
        Json json = new Json();
        SaveGameDocument legacyDocument = json.fromJson(SaveGameDocument.class, new String(fixtureBytes, StandardCharsets.UTF_8));
        check(legacyDocument.gameState.schemaVersion == 3, "fixture 確實是舊 schema 3");
        check(!new String(fixtureBytes, StandardCharsets.UTF_8).contains("moraleRecorded"), "舊 fixture 沒有新版戰報欄位");
        LocalJsonSaveGameRepository legacySaves = new LocalJsonSaveGameRepository(legacyDirectory);
        GameState migratedState = legacySaves.load(1);
        check(migratedState.schemaVersion == idv.kuan.studio.sango.SangoVersion.GAME_STATE_SCHEMA_VERSION,
            "schema 3 遷移至目前版本");
        check(migratedState.cityStates.length == 6 && migratedState.mapId.equals("prototype_central_region"),
            "舊戰局保留六城，不憑空加入新領地");
        check(migratedState.requireCityState("chenliu").morale == 50, "舊城低民心士氣初始化為 50");
        check(migratedState.requireCityState("pingyuan").morale == 80, "舊城高民心繼承作為初始士氣");
        check(migratedState.armyStates[0].morale == 0, "舊野戰軍合法零士氣必須保留");
        check(migratedState.battleReports[0].read && !migratedState.battleReports[0].moraleRecorded,
            "舊已讀戰報保持已讀且不捏造歷史士氣");
        check(migratedState.battleReports[0].battleId.equals(legacyDocument.gameState.battleReports[0].battleId),
            "遷移不改戰報 ID");
        check(migratedState.scenarioObjectiveStatus == ScenarioObjectiveStatus.FAILED
            && migratedState.gameplayStatus == GameplayStatus.ACTIVE && migratedState.actionPointsRemaining == 2,
            "舊自由征戰狀態與剩餘行動力保留");
        check(Arrays.equals(fixtureBytes, legacyDirectory.child("slot-01.json").readBytes()),
            "唯讀載入不先改寫舊存檔");
        GameStateMigrator migrator = new GameStateMigrator();
        migrator.migrate(legacyDocument.gameState);
        check(legacyDocument.gameState.schemaVersion == 3 && legacyDocument.gameState.requireCityState("chenliu").morale == 0,
            "遷移不得修改來源物件");
        migratedState.requireCityState("chenliu").morale = 0;
        legacySaves.save(1, migratedState);
        check(legacySaves.load(1).requireCityState("chenliu").morale == 0,
            "schema 5 重新載入不將士氣零值補回 50");
        check(Arrays.equals(fixtureBytes, legacyDirectory.child("slot-01.backup.json").readBytes()),
            "升級首次保存保留原始 schema 3 backup");
        legacyDirectory.child("slot-01.json").writeString("broken", false, "UTF-8");
        check(legacySaves.inspect(1).hasRecoveryCandidate(), "primary 損壞時識別舊版 backup");
        check(legacySaves.load(1).requireCityState("chenliu").morale == 50,
            "backup 復原也必須執行 migration");
        GameState unsupportedState = migratedState.copy();
        unsupportedState.schemaVersion = 1;
        expectRuntimeFailure(() -> migrator.migrate(unsupportedState), "schema 1 必須明確拒絕");
        unsupportedState.schemaVersion = 99;
        expectRuntimeFailure(() -> migrator.migrate(unsupportedState), "未來 schema 必須明確拒絕");
    }

    private void validateLongRunningCampaigns() {
        for (String factionId : PLAYABLE_FACTIONS) {
            GameState gameState = newGame(factionId);
            // 固定高防守與軍糧，讓這項結構壓力測試完整跑 48 月；不把它當平衡測試。
            for (CityState cityState : gameState.findCitiesOwnedBy(factionId)) {
                cityState.troops = 10000;
                cityState.training = 100;
                cityState.morale = 100;
                cityState.defense = 100;
            }
            gameState.requirePlayerFactionState().food = 1000000;
            for (int i = 0; i < 48; i++) {
                TurnResolutionResult result = turnService.resolve(gameState);
                gameState = result.getGameState();
                verifyReportReferences(result.getReport());
                GameStateValidator.validate(gameState);
                check(gameState.gameplayStatus == GameplayStatus.ACTIVE, "長回合壓力測試玩家仍可操作");
                check(gameState.actionPointsRemaining == NationalActionPointRules.calculateMonthlyActionPoints(gameState), "每月重置行動力");
                check(gameState.cityStates.length == 42, "回合過程不得丟失城市");
            }
            check(gameState.elapsedMonths == 48 && gameState.currentYear == 194, "完整推進四年");
            check(gameState.scenarioObjectiveStatus == ScenarioObjectiveStatus.FAILED,
                "十二月期限失敗之後仍繼續到 48 月");
            saves.save(1, gameState);
            check(saves.load(1).elapsedMonths == 48, "長期戰局可保存並讀回");
        }
    }

    private void verifyReportReferences(TurnResolutionReport report) {
        for (TurnEvent event : report.getEvents()) {
            if (event.getCityId() != null && !event.getCityId().isBlank()) {
                definitions.requireCity(event.getCityId());
            }
            if (event.getOtherCityId() != null && !event.getOtherCityId().isBlank()) {
                definitions.requireCity(event.getOtherCityId());
            }
            if (event.getFactionId() != null && !event.getFactionId().isBlank()) {
                definitions.requireFaction(event.getFactionId());
            }
            assertions++;
        }
    }

    private static ArmyState army(String factionId, String originCityId, String targetCityId,
        int troops, int training, int morale) {
        ArmyState armyState = new ArmyState();
        armyState.armyId = "test-army";
        armyState.factionId = factionId;
        armyState.originCityId = originCityId;
        armyState.targetCityId = targetCityId;
        armyState.troops = troops;
        armyState.training = training;
        armyState.morale = morale;
        armyState.tactic = BattleTactic.FEINT;
        armyState.remainingTravelMonths = 1;
        armyState.totalTravelMonths = 1;
        armyState.initialTroops = troops;
        return armyState;
    }

    private static void expectRuntimeFailure(Runnable operation, String description) {
        boolean failed = false;
        try {
            operation.run();
        } catch (RuntimeException expected) {
            failed = true;
        }
        check(failed, description);
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

}
