package idv.kuan.studio.sango.validation;

import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.files.FileHandle;

import idv.kuan.studio.sango.SangoVersion;
import idv.kuan.studio.sango.application.command.NewGameCommand;
import idv.kuan.studio.sango.application.command.ScoutCityCommand;
import idv.kuan.studio.sango.application.command.SetDefensePolicyCommand;
import idv.kuan.studio.sango.application.request.NewGameRequest;
import idv.kuan.studio.sango.application.result.StrategicActionResult;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.definition.CityConnectionDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.CityIntelligenceSnapshot;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.DefensePolicy;
import idv.kuan.studio.sango.domain.rule.MilitaryRules;
import idv.kuan.studio.sango.domain.service.BattleResolutionService;
import idv.kuan.studio.sango.domain.service.CityIntelligenceService;
import idv.kuan.studio.sango.domain.service.EnemyTurnService;
import idv.kuan.studio.sango.domain.service.KnownCityView;
import idv.kuan.studio.sango.repository.definition.AssetJsonGameDefinitionRepository;
import idv.kuan.studio.sango.repository.save.GameStateMigrator;
import idv.kuan.studio.sango.repository.save.LocalJsonSaveGameRepository;
import idv.kuan.studio.sango.ui.support.DispatchPanelRules;

/** 防守方針、情報隔離與 schema 10 的無圖形回歸。 */
public final class DefenseIntelligenceSmokeTest {
    private static int checks;

    private DefenseIntelligenceSmokeTest() {
    }

    public static void main(String[] arguments) throws Exception {
        Path assets = Path.of(arguments[0]).toAbsolutePath().normalize();
        FileHandle temporary = new FileHandle(Files.createTempDirectory("sango-intel-").toFile());
        try {
            AssetJsonGameDefinitionRepository definitions = new AssetJsonGameDefinitionRepository(
                new FileHandle(assets.resolve("data/scenarios/scenarios.json").toFile()),
                new FileHandle(assets.resolve("data/factions/factions.json").toFile()),
                new FileHandle(assets.resolve("data/cities/cities.json").toFile()),
                new FileHandle(assets.resolve("data/maps/maps.json").toFile())
            );
            GameState initial = new NewGameCommand(definitions,
                new LocalJsonSaveGameRepository(temporary)).execute(
                    1, new NewGameRequest("warlords_china", "cao_cao"));
            validateScouting(initial, definitions);
            validateAiScouting(initial, definitions);
            validateDefense(initial, definitions);
            validateMigration(initial);
            validateUi(assets);
            System.out.println("Sango defense and intelligence: PASS; checks=" + checks);
        } finally {
            temporary.deleteDirectory();
        }
    }

    private static void validateAiScouting(GameState initial,
        AssetJsonGameDefinitionRepository definitions) {
        GameState state = initial.copy();
        state.enemyAttackCountdown = 2;
        for (var faction : state.factionStates) {
            if (!state.playerFactionId.equals(faction.factionId)) {
                faction.aiActionPointsPerTurn = 0;
                faction.aiActionPointsRemaining = 0;
            }
        }
        var observer = state.requireOpponentFactionState();
        observer.aiActionPointsPerTurn = 1;
        observer.aiActionPointsRemaining = 1;
        int goldBefore = observer.gold;
        int playerIntelBefore = state.requirePlayerFactionState().cityIntelligence.length;
        new EnemyTurnService().execute(state, definitions.requireMap(state.mapId),
            new TurnResolutionReport(state.currentYear, state.currentMonth));
        check(observer.gold == goldBefore - ScoutCityCommand.GOLD_COST
            && observer.aiActionPointsRemaining == 0,
            "AI 偵察同樣扣除二十金與一點行動力");
        check(observer.cityIntelligence.length == 1
            && state.requirePlayerFactionState().cityIntelligence.length == playerIntelBefore,
            "AI 偵察結果只寫入自己的情報");
    }

    private static void validateScouting(GameState initial,
        AssetJsonGameDefinitionRepository definitions) {
        GameState state = initial.copy();
        StrategicMapDefinition map = definitions.requireMap(state.mapId);
        CityConnectionDefinition edge = findForeignEdge(state, map, state.playerFactionId);
        String originId = state.ownsCity(state.playerFactionId, edge.fromCityId)
            ? edge.fromCityId : edge.toCityId;
        String targetId = originId.equals(edge.fromCityId) ? edge.toCityId : edge.fromCityId;
        CityState target = state.requireCityState(targetId);
        int observedTroops = target.troops;
        StrategicActionResult result = new ScoutCityCommand(definitions).execute(
            1, state, originId, targetId);
        check(result.isSuccessful(), "玩家可依共同規則偵察");
        GameState scouted = result.getGameState();
        CityIntelligenceService intelligence = new CityIntelligenceService();
        CityIntelligenceSnapshot snapshot = intelligence.findSnapshot(
            scouted, scouted.playerFactionId, targetId);
        check(snapshot != null && snapshot.validThroughTurn == scouted.currentTurn + 2,
            "三個月情報包含偵察當月");
        check(scouted.requireFactionState(target.ownerFactionId).cityIntelligence.length == 0,
            "各勢力情報彼此隔離");
        scouted.requireCityState(targetId).troops += 777;
        check(intelligence.knownView(scouted, scouted.playerFactionId, targetId).troops()
            == observedTroops, "情報快照不隨真值變動");
        scouted.currentTurn += 3;
        check(!intelligence.knownView(scouted, scouted.playerFactionId, targetId).exact(),
            "進入第四個月後情報失效");

        GameState unknownA = initial.copy();
        GameState unknownB = initial.copy();
        unknownB.requireCityState(targetId).troops += 50_000;
        KnownCityView viewA = intelligence.knownView(unknownA,
            unknownA.opponentFactionId, targetId);
        KnownCityView viewB = intelligence.knownView(unknownB,
            unknownB.opponentFactionId, targetId);
        check(viewA.troops() == viewB.troops() && !viewA.exact() && !viewB.exact(),
            "AI 未偵察時的決策資料不受城池真值影響");
    }

    private static void validateDefense(GameState initial,
        AssetJsonGameDefinitionRepository definitions) {
        GameState state = initial.copy();
        CityState own = state.findCitiesOwnedBy(state.playerFactionId).get(0);
        state = new SetDefensePolicyCommand().execute(
            state, state.playerFactionId, own.cityId, DefensePolicy.HOLD);
        check(state.requireCityState(own.cityId).defensePolicy == DefensePolicy.HOLD,
            "玩家防守方針持久寫入城池");

        CityState strengthCity = state.requireCityState(own.cityId).copy();
        strengthCity.troops = 2_000;
        strengthCity.defensePolicy = DefensePolicy.BALANCED;
        int balanced = MilitaryRules.calculateDefenderStrength(strengthCity);
        strengthCity.defensePolicy = DefensePolicy.AGGRESSIVE;
        int aggressive = MilitaryRules.calculateDefenderStrength(strengthCity);
        strengthCity.defensePolicy = DefensePolicy.HOLD;
        int hold = MilitaryRules.calculateDefenderStrength(strengthCity);
        check(aggressive == balanced * 90 / 100 && hold == balanced * 115 / 100,
            "防守方針套用守城戰力倍率");
        check(DefensePolicy.AGGRESSIVE.getAttackerCasualtyPercent() == 125
            && DefensePolicy.AGGRESSIVE.getDefenderCasualtyPercent() == 120
            && DefensePolicy.HOLD.getAttackerCasualtyPercent() == 75
            && DefensePolicy.HOLD.getDefenderCasualtyPercent() == 80,
            "防守方針傷亡倍率集中且正確");

        StrategicMapDefinition map = definitions.requireMap(state.mapId);
        CityConnectionDefinition edge = findForeignEdge(state, map, state.playerFactionId);
        String originId = state.ownsCity(state.playerFactionId, edge.fromCityId)
            ? edge.fromCityId : edge.toCityId;
        String targetId = originId.equals(edge.fromCityId) ? edge.toCityId : edge.fromCityId;
        CityState target = state.requireCityState(targetId);
        target.troops = 1;
        target.defensePolicy = DefensePolicy.AGGRESSIVE;
        ArmyState army = new ArmyState();
        army.armyId = "test-army";
        army.expeditionGroupId = army.armyId;
        army.factionId = state.playerFactionId;
        army.originCityId = originId;
        army.targetCityId = targetId;
        army.remainingTravelMonths = 1;
        army.troops = 10_000;
        army.training = 100;
        army.morale = 100;
        army.tactic = BattleTactic.BALANCED;
        TurnResolutionReport report = new TurnResolutionReport(state.currentYear, state.currentMonth);
        new BattleResolutionService().resolveArrival(state, army, map, report);
        BattleReport battle = state.battleReports[state.battleReports.length - 1];
        check(battle.defenderPolicyRecorded
            && battle.defenderPolicy == DefensePolicy.AGGRESSIVE,
            "戰報保存交戰當下守方方針");
        check(state.requireCityState(targetId).defensePolicy == DefensePolicy.BALANCED,
            "城池易主後防守方針重設均衡");
    }

    private static void validateMigration(GameState initial) {
        GameState legacy = initial.copy();
        legacy.schemaVersion = 9;
        CityState target = null;
        for (CityState city : legacy.cityStates) {
            city.defensePolicy = null;
            if (!legacy.playerFactionId.equals(city.ownerFactionId) && target == null) {
                target = city;
            }
        }
        for (var faction : legacy.factionStates) {
            faction.cityIntelligence = null;
        }
        int legacyValidThrough = legacy.currentTurn + 1;
        target.scoutedUntilTurn = legacyValidThrough;
        GameState migrated = new GameStateMigrator().migrate(legacy);
        check(migrated.schemaVersion == SangoVersion.GAME_STATE_SCHEMA_VERSION,
            "schema 9 可遷移為 schema 10");
        check(migrated.requireCityState(target.cityId).defensePolicy == DefensePolicy.BALANCED,
            "舊城池補上均衡方針");
        CityIntelligenceSnapshot snapshot = new CityIntelligenceService().findSnapshot(
            migrated, migrated.playerFactionId, target.cityId);
        check(snapshot != null && snapshot.validThroughTurn == legacyValidThrough
            && !snapshot.observationDateRecorded
            && migrated.requireCityState(target.cityId).scoutedUntilTurn == 0,
            "舊玩家偵察保留原到期日且不偽造偵察年月");
    }

    private static void validateUi(Path assets) throws Exception {
        String xml = Files.readString(assets.resolve("ui/strategic_map.xml"));
        String i18n = Files.readString(assets.resolve("i18n/ui_zh_Hant.xml"));
        check(xml.contains("defense_policy_panel")
            && xml.contains("dispatch_tactic_panel"), "地圖具有防守與派兵戰術元件");
        check(DispatchPanelRules.showsTacticSelection(false)
            && !DispatchPanelRules.showsTacticSelection(true),
            "出征顯示戰術而運兵隱藏戰術");
        check(i18n.contains("map_intel_scout_date_format")
            && i18n.contains("map_status_defense_policy_updated"),
            "情報年月與防守方針文字已註冊");
    }

    private static CityConnectionDefinition findForeignEdge(GameState state,
        StrategicMapDefinition map, String factionId) {
        for (CityConnectionDefinition edge : map.connections) {
            if (state.ownsCity(factionId, edge.fromCityId)
                && !state.ownsCity(factionId, edge.toCityId)
                || state.ownsCity(factionId, edge.toCityId)
                && !state.ownsCity(factionId, edge.fromCityId)) {
                return edge;
            }
        }
        throw new AssertionError("測試劇本沒有跨勢力道路");
    }

    private static void check(boolean condition, String description) {
        checks += 1;
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
