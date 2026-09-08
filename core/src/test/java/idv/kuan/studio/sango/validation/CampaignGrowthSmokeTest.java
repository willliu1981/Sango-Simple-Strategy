package idv.kuan.studio.sango.validation;

import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.files.FileHandle;

import idv.kuan.studio.sango.application.command.ExecuteDomesticActionCommand;
import idv.kuan.studio.sango.application.command.LaunchExpeditionCommand;
import idv.kuan.studio.sango.application.command.NewGameCommand;
import idv.kuan.studio.sango.application.request.NewGameRequest;
import idv.kuan.studio.sango.application.result.DomesticActionResult;
import idv.kuan.studio.sango.application.result.TurnEvent;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.DomesticActionFailureReason;
import idv.kuan.studio.sango.domain.rule.DomesticActionRules;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;
import idv.kuan.studio.sango.domain.rule.FactionActionPointRules;
import idv.kuan.studio.sango.domain.rule.OfficerCommandProfile;
import idv.kuan.studio.sango.domain.rule.PopulationRules;
import idv.kuan.studio.sango.domain.rule.PublicOrderRules;
import idv.kuan.studio.sango.domain.rule.RecruitmentRules;
import idv.kuan.studio.sango.domain.rule.TroopQualityRules;
import idv.kuan.studio.sango.domain.service.CityIntelligenceService;
import idv.kuan.studio.sango.domain.service.DomesticActionService;
import idv.kuan.studio.sango.domain.service.EnemyTurnService;
import idv.kuan.studio.sango.domain.service.TurnResolutionService;
import idv.kuan.studio.sango.repository.definition.AssetJsonGameDefinitionRepository;
import idv.kuan.studio.sango.repository.save.GameStateMigrator;
import idv.kuan.studio.sango.repository.save.LocalJsonSaveGameRepository;

/** 人口、徵兵、部分覆蓋訓練、小數持久化及 AI 同規則的無圖形測試。 */
public final class CampaignGrowthSmokeTest {
    private static int checks;
    private static final OfficerCommandProfile DEFAULT_OFFICER = OfficerCommandProfile.DEFAULT;

    private CampaignGrowthSmokeTest() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("需要 assets 目錄路徑。");
        }
        Path assets = Path.of(arguments[0]).toAbsolutePath();
        FileHandle temporaryDirectory = new FileHandle(Files.createTempDirectory("sango-growth-").toFile());
        try {
            AssetJsonGameDefinitionRepository definitions = new AssetJsonGameDefinitionRepository(
                new FileHandle(assets.resolve("data/scenarios/scenarios.json").toFile()),
                new FileHandle(assets.resolve("data/factions/factions.json").toFile()),
                new FileHandle(assets.resolve("data/cities/cities.json").toFile()),
                new FileHandle(assets.resolve("data/maps/maps.json").toFile()));
            LocalJsonSaveGameRepository saves = new LocalJsonSaveGameRepository(temporaryDirectory);
            GameState initial = new NewGameCommand(definitions, saves)
                .execute(1, new NewGameRequest("warlords_china", "cao_cao"));
            FileHandle legacyDirectory = temporaryDirectory.child("legacy-four");
            legacyDirectory.mkdirs();
            try (var fixture = CampaignGrowthSmokeTest.class.getResourceAsStream("/save/schema4-national-campaign.json")) {
                if (fixture == null) {
                    throw new IllegalStateException("缺少 Schema 4 原版存檔 fixture。");
                }
                legacyDirectory.child("slot-01.json").writeBytes(fixture.readAllBytes(), false);
            }
            LocalJsonSaveGameRepository legacySaves = new LocalJsonSaveGameRepository(legacyDirectory);
            GameState legacyLoaded = legacySaves.load(1);
            check(legacyLoaded.schemaVersion == idv.kuan.studio.sango.SangoVersion.GAME_STATE_SCHEMA_VERSION
                && legacyLoaded.cityStates.length == 42,
                "由未修改 0.5.2 寫出的真實 JSON 可經 Repository 遷移");
            check(legacyLoaded.actionPointsPerTurn == 9 && legacyLoaded.actionPointsRemaining == 2,
                "真實 Schema 4 JSON 遷移保留 2/9 快照");
            check(legacyLoaded.requireCapitalCityState().morale == 0,
                "真實舊 JSON 的零士氣不被 default 重設");
            legacySaves.save(1, legacyLoaded);
            check(legacySaves.load(1).schemaVersion == idv.kuan.studio.sango.SangoVersion.GAME_STATE_SCHEMA_VERSION,
                "真實舊檔遷移後可重新安全保存");
            testRecruitmentCurve();
            testWeightedQuality();
            testTrainingCoverage();
            testPacify(initial);
            testOrders(initial, saves);
            testPopulation(initial, definitions, saves);
            testPersistence(initial, definitions, saves);
            testAiParity(initial, definitions);
            System.out.println("Sango population/recruitment/training/AI regression: PASS; checks=" + checks);
        } finally {
            temporaryDirectory.deleteDirectory();
        }
    }

    private static void testRecruitmentCurve() {
        int[][] samples = {{10000, 1000}, {30000, 1732}, {50000, 2236}, {80000, 2828}, {100000, 3162}};
        for (int[] sample : samples) {
            check(RecruitmentRules.populationLimit(sample[0], DEFAULT_OFFICER) == sample[1], "人口根號曲線示例");
        }
        int previous = 0;
        for (int i = 0; i <= 1_050_000; i++) {
            int result = RecruitmentRules.populationLimit(i, DEFAULT_OFFICER);
            int expected = (int) Math.min(10000, Math.min(Math.floor(10d * Math.sqrt(i)), Math.max(0, i - 2000)));
            check(result == expected, "完整人口邊界與硬上限");
            check(result >= previous && result <= 10000 && result <= Math.max(0, i - 2000), "單調遞增且保留人口");
            previous = result;
        }
        check(RecruitmentRules.populationLimit(Integer.MAX_VALUE, DEFAULT_OFFICER) == 10000, "極大人口不溢位");
        OfficerCommandProfile stronger = new OfficerCommandProfile(120, 24000, 5, 5, 50);
        check(RecruitmentRules.populationLimit(10000, stronger) == 1200, "未來武將徵兵加成獨立生效");
        check(RecruitmentRules.populationLimit(2_001, stronger) == 1, "武將加成不能突破保留量");
        check(RecruitmentRules.populationLimit(1_000_000, stronger) == 10000, "武將加成仍受單次硬上限");
        for (int i = 0; i <= 10000; i++) {
            check(RecruitmentRules.goldCost(i) == (i + 1) / 2, "金費用按實際人數向上取整");
            check(RecruitmentRules.foodCost(i) == (i + 1) / 2, "糧費用按實際人數向上取整");
        }
        CityState city = new CityState();
        city.population = 100_000;
        FactionState faction = new FactionState();
        faction.gold = 51;
        faction.food = 50;
        check(RecruitmentRules.maximumRecruitable(city, faction, DEFAULT_OFFICER) == 100, "資源限制取較小值");
        city.troops = Integer.MAX_VALUE - 1;
        check(RecruitmentRules.maximumRecruitable(city, faction, DEFAULT_OFFICER) == 1, "總兵力不超出整數容量");
    }

    private static void testWeightedQuality() {
        int scale = TroopQualityRules.SCALE;
        check(TroopQualityRules.weightedAverage(1000, 100 * scale, 1000, 50 * scale) == 75 * scale,
            "千名精兵加千名新兵為 75");
        check(TroopQualityRules.weightedAverage(2000, 100 * scale, 1000, 50 * scale) == 83_333_333,
            "兩千精兵加千名新兵保留小數");
        check(TroopQualityRules.weightedAverage(0, 100 * scale, 1000, 50 * scale) == 50 * scale,
            "空城徵兵不混入空部隊殘留素質");
        check(TroopQualityRules.weightedAverage(Integer.MAX_VALUE, 100 * scale,
            Integer.MAX_VALUE, 100 * scale) == 100 * scale, "加權中間值使用 long");
        CityState city = new CityState();
        city.troops = 2000;
        TroopQualityRules.set(city, 100 * scale, 100 * scale);
        TroopQualityRules.merge(city, 1000, 50 * scale, 50 * scale);
        check(city.troops == 3000 && city.training == 84 && city.trainingFraction == 0,
            "操作後平均值無條件進位並取代原值");
    }

    private static void testTrainingCoverage() {
        int[][] cases = {{10000, 5_000_000}, {20000, 5_000_000}, {50000, 2_000_000},
            {100000, 1_000_000}, {2_000_000, 50_000}};
        for (int[] sample : cases) {
            CityState city = new CityState();
            city.troops = sample[0];
            TroopQualityRules.set(city, 50_000_000, 50_000_000);
            TroopQualityRules.TrainingProjection projection = TroopQualityRules.projectTraining(city, DEFAULT_OFFICER);
            check(projection.coveredTroops() == Math.min(city.troops, 20000), "訓練實際覆蓋人數");
            check(projection.trainingScaled() == 50_000_000 + sample[1], "訓練按覆蓋比例攤回");
            check(projection.moraleScaled() == 50_000_000 + sample[1], "士氣按覆蓋比例攤回");
            check(city.training == 50 && city.trainingFraction == 0, "預覽不得修改部隊");
        }
        CityState city = new CityState();
        city.troops = 100000;
        TroopQualityRules.set(city, 99_000_000, 100_000_000);
        TroopQualityRules.train(city, DEFAULT_OFFICER);
        check(TroopQualityRules.training(city) == 100_000_000,
            "操作後訓練小數無條件進位並取代原值");
        check(TroopQualityRules.morale(city) == 100_000_000, "已滿士氣不溢位");
        city.troops = 2_000_000;
        TroopQualityRules.set(city, 50_000_000, 50_000_000);
        for (int i = 0; i < 20; i++) {
            TroopQualityRules.train(city, DEFAULT_OFFICER);
        }
        check(city.training == 70 && city.trainingFraction == 0,
            "每次操作的小數結果皆無條件進位並取代原值");
        TroopQualityRules.set(city, 99_999_999, 100_000_000);
        TroopQualityRules.train(city, DEFAULT_OFFICER);
        check(city.training == 100 && city.trainingFraction == 0, "最後一個微小單位不永久卡住");
    }

    private static void testOrders(GameState initial, LocalJsonSaveGameRepository saves) {
        GameState state = initial.copy();
        GameState savedBeforeOrder = saves.load(1);
        CityState city = state.requireCapitalCityState();
        state.requirePlayerFactionState().gold = 100000;
        state.requirePlayerFactionState().food = 100000;
        city.population = 30000;
        city.troops = 1000;
        for (CityState owned : state.findCitiesOwnedBy(state.playerFactionId)) {
            owned.publicOrder = 50;
        }
        TroopQualityRules.set(city, 100_000_000, 100_000_000);
        ExecuteDomesticActionCommand command = new ExecuteDomesticActionCommand();
        DomesticActionResult result = command.execute(1, state, city.cityId, DomesticActionType.RECRUIT, 1000);
        check(result.isSuccessful(), "自選千人徵兵成功");
        CityState nextCity = result.getGameState().requireCapitalCityState();
        check(nextCity.troops == 2000 && nextCity.training == 75 && nextCity.morale == 75, "命令落地加權素質");
        check(nextCity.population == 29000, "徵多少扣多少人口");
        check(result.getGameState().requirePlayerFactionState().gold == 99500
            && result.getGameState().requirePlayerFactionState().food == 99500, "按人數扣金糧");
        check(result.getGameState().actionPointsRemaining == 2, "單次只扣一點 AP");
        check(city.troops == 1000 && city.population == 30000, "命令保留輸入狀態");
        check(saves.load(1).requireCapitalCityState().training
            == savedBeforeOrder.requireCapitalCityState().training,
            "內政後尚未明確存檔，讀取仍為操作前狀態");
        for (int amount : new int[] {-1, 0, 1733, 10001, Integer.MAX_VALUE}) {
            result = command.execute(1, state, city.cityId, DomesticActionType.RECRUIT, amount);
            check(!result.isSuccessful(), "非法或超過曲線人數拒絕");
            check(state.actionPointsRemaining == 3 && city.population == 30000, "拒絕不扣點或人口");
        }
        city.population = 2001;
        check(!command.execute(1, state, city.cityId, DomesticActionType.RECRUIT, 2).isSuccessful(), "徵完必須保留 2000");
        result = command.execute(1, state, city.cityId, DomesticActionType.RECRUIT, 1);
        check(result.isSuccessful() && result.getGameState().requireCapitalCityState().population == 2000,
            "2001 人可精確徵 1 人");
        city.population = 30000;
        city.troops = 0;
        check(command.execute(1, state, city.cityId, DomesticActionType.TRAIN).getFailureReason()
            == DomesticActionFailureReason.NO_TROOPS, "零兵拒絕訓練");
        city.troops = 1000;
        TroopQualityRules.set(city, 100_000_000, 100_000_000);
        check(command.execute(1, state, city.cityId, DomesticActionType.TRAIN).getFailureReason()
            == DomesticActionFailureReason.VALUE_AT_MAXIMUM, "雙滿不消耗訓練費用");
    }

    private static void testPacify(GameState initial) {
        check(DomesticActionType.PACIFY.getActionPointCost() == 1
            && DomesticActionType.PACIFY.getGoldCost() == 100
            && DomesticActionType.PACIFY.getFoodCost() == 50, "安民固定消耗 1 AP、100 金、50 糧");
        check(DomesticActionRules.pacifyGain(49) == 6 && DomesticActionRules.pacifyGain(50) == 4
            && DomesticActionRules.pacifyGain(69) == 4 && DomesticActionRules.pacifyGain(70) == 2
            && DomesticActionRules.pacifyGain(84) == 2 && DomesticActionRules.pacifyGain(85) == 1
            && DomesticActionRules.pacifyGain(94) == 1, "安民依民心門檻增加");
        GameState state = initial.copy();
        CityState city = state.requireCapitalCityState();
        FactionState faction = state.requirePlayerFactionState();
        city.publicOrder = 49;
        faction.gold = 1000;
        faction.food = 1000;
        DomesticActionService service = new DomesticActionService();
        check(service.apply(state, state.playerFactionId, city.cityId, DomesticActionType.PACIFY, 0, DEFAULT_OFFICER)
            == DomesticActionFailureReason.NONE, "安民透過共用內政服務成功");
        check(city.publicOrder == 55 && faction.gold == 900 && faction.food == 950
            && state.actionPointsRemaining == 2, "安民同步套用民心與全部成本");
        city.publicOrder = 95;
        check(DomesticActionRules.evaluate(state, city.cityId, DomesticActionType.PACIFY)
            == DomesticActionFailureReason.VALUE_AT_MAXIMUM, "民心 95 時拒絕安民");
    }

    private static void testPopulation(GameState initial, AssetJsonGameDefinitionRepository definitions,
        LocalJsonSaveGameRepository saves) {
        for (int i = 0; i <= 10000; i++) {
            int expected = i < 2000 ? -4 : i < 4000 ? -2 : i < 6000 ? 0 : i < 8000 ? 2 : 4;
            check(PopulationRules.annualRate(i) == expected, "有效民心百分之一點全部年度門檻");
        }
        GameState state = initial.copy();
        CityState city = state.requireCapitalCityState();
        for (CityState owned : state.findCitiesOwnedBy(state.playerFactionId)) {
            owned.publicOrder = 0;
        }
        city.publicOrder = 100;
        check(PublicOrderRules.effectiveOrderHundredths(state, city) == 8000, "本城 100 全國平均 50 得 80");
        check(PublicOrderRules.recruitMorale(state, city) == 62, "低全國民心拖累高民心徵兵城");
        city.publicOrder = 0;
        check(PublicOrderRules.recruitMorale(state, city) == 30, "零民心仍有新兵士氣底值");
        city.population = 1001;
        check(PopulationRules.project(state, city, 100000).delta() == -1, "年度流失不低於 1000");
        city.population = 800;
        check(PopulationRules.project(state, city, 100000).delta() == 0, "舊存檔低人口不憑空補到保護值");
        city.population = 200000;
        check(PopulationRules.project(state, city, 300000).delta() == -3000, "年度流失上限 3000");
        for (CityState owned : state.findCitiesOwnedBy(state.playerFactionId)) {
            owned.publicOrder = 100;
        }
        check(PublicOrderRules.recruitMorale(state, city) == 70, "高民心新兵士氣仍不等於 100");
        check(PopulationRules.project(state, city, 300000).delta() == 5000, "年度增加上限 5000");
        check(PopulationRules.project(state, city, 100000).delta() == 0, "超容量舊人口不裁切");
        city.population = 99000;
        check(PopulationRules.project(state, city, 100000).delta() == 1000, "增長不超過容量");
        state = initial.copy();
        state.currentMonth = 11;
        state.enemyAttackCountdown = 99;
        for (FactionState faction : state.factionStates) {
            faction.aiActionPointsRemaining = 0;
            faction.food = 100000;
        }
        for (CityState candidate : state.cityStates) {
            candidate.publicOrder = 100;
            candidate.population = 10000;
        }
        TurnResolutionService turns = new TurnResolutionService(definitions);
        GameState december = turns.resolve(state).getGameState();
        check(december.requireCapitalCityState().population == 10000, "十一月底不提前結算年度人口");
        for (FactionState faction : december.factionStates) {
            faction.aiActionPointsRemaining = 0;
        }
        GameState january = turns.resolve(december).getGameState();
        check(january.currentMonth == 1 && january.currentYear == state.currentYear + 1, "十二月底跨年一次");
        for (CityState candidate : january.cityStates) {
            check(candidate.population == 10400, "玩家 AI 中立各城皆於年底結算一次");
        }
        saves.save(2, january);
        GameState loaded = saves.load(2);
        check(loaded.requireCapitalCityState().population == 10400, "讀檔不重複年度增長");
        check(turns.resolve(loaded).getGameState().requireCapitalCityState().population == 10400, "一月底不重複增長");
    }

    private static void testPersistence(GameState initial, AssetJsonGameDefinitionRepository definitions,
        LocalJsonSaveGameRepository saves) {
        GameState state = initial.copy();
        state.actionPointsPerTurn = 9;
        state.actionPointsRemaining = 2;
        state.schemaVersion = 4;
        state.requireCapitalCityState().morale = 0;
        int initialPopulation = state.requireCapitalCityState().population;
        GameState migrated = new GameStateMigrator().migrate(state);
        check(migrated.schemaVersion == idv.kuan.studio.sango.SangoVersion.GAME_STATE_SCHEMA_VERSION
            && state.schemaVersion == 4, "Schema 4 遷移使用副本");
        check(migrated.actionPointsPerTurn == 9 && migrated.actionPointsRemaining == 2, "舊月中 2/9 不回補或重算");
        check(migrated.requireCapitalCityState().morale == 0
            && migrated.requireCapitalCityState().population == initialPopulation, "零士氣與人口不重設");
        CityState city = migrated.requireCapitalCityState();
        city.troops = 1800;
        TroopQualityRules.set(city, 75_123_456, 60_234_567);
        saves.save(2, migrated);
        GameState loaded = saves.load(2);
        check(TroopQualityRules.training(loaded.requireCapitalCityState()) == 76_000_000,
            "城市訓練操作後以整數存讀");
        check(TroopQualityRules.morale(loaded.requireCapitalCityState()) == 61_000_000,
            "城市士氣操作後以整數存讀");
        check(new GameStateMigrator().migrate(loaded).requireCapitalCityState().trainingFraction == 0,
            "目前 schema 重讀保留整數素質");
        GameState launched = new LaunchExpeditionCommand(definitions)
            .execute(2, loaded, city.cityId, "runan", BattleTactic.BALANCED).getGameState();
        check(launched.armyStates[0].trainingFraction == 0 && launched.armyStates[0].moraleFraction == 0,
            "出征軍保存原城整數素質");
        saves.save(2, launched);
        check(saves.load(2).armyStates[0].trainingFraction == 0, "野戰軍整數素質存讀");
        launched.requireCityState("runan").troops = 0;
        launched.enemyAttackCountdown = 99;
        for (FactionState faction : launched.factionStates) {
            faction.aiActionPointsRemaining = 0;
        }
        GameState occupied = new TurnResolutionService(definitions).resolve(launched).getGameState();
        check(occupied.requireCityState("runan").trainingFraction == 0, "佔領後守軍維持整數素質");
        GameStateValidator.validate(occupied);
    }

    private static void testAiParity(GameState initial, AssetJsonGameDefinitionRepository definitions) {
        GameState state = initial.copy();
        for (CityState city : state.cityStates) {
            city.publicOrder = 100;
        }
        FactionActionPointRules.refreshAll(state, true);
        for (FactionState faction : state.factionStates) {
            int expected = faction.active && !state.neutralFactionId.equals(faction.factionId) ? 3 : 0;
            check(FactionActionPointRules.remaining(state, faction.factionId) == expected, "高初始民心仍從 3 AP 起步");
        }
        FactionActionPointRules.refreshAll(state, false);
        for (FactionState faction : state.factionStates) {
            if (faction.active && !state.neutralFactionId.equals(faction.factionId)) {
                int cityCount = state.findCitiesOwnedBy(faction.factionId).size();
                check(FactionActionPointRules.remaining(state, faction.factionId) == (cityCount == 2 ? 4 : 3),
                    "玩家 AI 同民心門檻表");
            }
        }
        state = initial.copy();
        state.enemyAttackCountdown = 99;
        String aiFaction = "liu_bei";
        CityState aiCity = state.findCitiesOwnedBy(aiFaction).get(0);
        CityState playerCity = state.requireCapitalCityState();
        for (CityState city : state.cityStates) {
            city.publicOrder = 50;
        }
        for (CityState city : new CityState[] {aiCity, playerCity}) {
            city.population = 30000;
            city.troops = 1000;
            TroopQualityRules.set(city, 75_500_000, 80_000_000);
            FactionState faction = state.requireFactionState(city.ownerFactionId);
            faction.gold = 10000;
            faction.food = 10000;
        }
        DomesticActionService domestic = new DomesticActionService();
        check(domestic.apply(state, aiFaction, aiCity.cityId, DomesticActionType.RECRUIT, 1000, DEFAULT_OFFICER)
            == DomesticActionFailureReason.NONE, "AI 共用徵兵服務");
        check(domestic.apply(state, state.playerFactionId, playerCity.cityId, DomesticActionType.RECRUIT, 1000, DEFAULT_OFFICER)
            == DomesticActionFailureReason.NONE, "玩家共用徵兵服務");
        check(TroopQualityRules.training(aiCity) == TroopQualityRules.training(playerCity)
            && TroopQualityRules.morale(aiCity) == TroopQualityRules.morale(playerCity), "同條件素質相同");
        check(state.requireFactionState(aiFaction).gold == state.requirePlayerFactionState().gold
            && state.requireFactionState(aiFaction).food == state.requirePlayerFactionState().food,
            "玩家 AI 金糧成本相同");
        check(FactionActionPointRules.remaining(state, aiFaction) == state.actionPointsRemaining, "玩家 AI 均實際扣 AP");
        for (FactionState faction : state.factionStates) {
            faction.aiActionPointsRemaining = 0;
        }
        int priorTroops = aiCity.troops;
        int priorGold = state.requireFactionState(aiFaction).gold;
        EnemyTurnService enemy = new EnemyTurnService();
        enemy.execute(state, definitions.requireMap(state.mapId), new TurnResolutionReport(state.currentYear, state.currentMonth));
        check(aiCity.troops == priorTroops && state.requireFactionState(aiFaction).gold == priorGold, "AI 零 AP 不偷偷徵兵或訓練");
        CityIntelligenceService intelligence = new CityIntelligenceService();
        for (CityState city : state.cityStates) {
            if (!aiFaction.equals(city.ownerFactionId)) {
                intelligence.observe(state, aiFaction, city.cityId);
            }
        }
        FactionActionPointRules.refreshAll(state, false);
        TurnResolutionReport report = new TurnResolutionReport(state.currentYear, state.currentMonth);
        enemy.execute(state, definitions.requireMap(state.mapId), report);
        check(aiCity.publicOrder > 50, "AI 已有情報時透過共用內政流程安民");
        int actionReports = 0;
        for (TurnEvent event : report.getEvents()) {
            if (event.getType() == TurnEventType.AI_ACTIONS_USED) {
                actionReports += 1;
                check(event.getPrimaryValue() > 0 && event.getPrimaryValue() <= event.getSecondaryValue(), "AI 真實用量不超過額度");
            }
        }
        check(actionReports > 0, "測試確實執行 AI 命令而非空跑");
        GameStateValidator.validate(state);
    }

    private static void check(boolean condition, String description) {
        checks += 1;
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
