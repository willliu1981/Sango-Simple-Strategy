package idv.kuan.studio.sango.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.badlogic.gdx.files.FileHandle;

import idv.kuan.studio.sango.application.command.NewGameCommand;
import idv.kuan.studio.sango.application.request.NewGameRequest;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.service.BattleResolutionService;
import idv.kuan.studio.sango.domain.service.RetreatResolutionService;
import idv.kuan.studio.sango.repository.definition.AssetJsonGameDefinitionRepository;
import idv.kuan.studio.sango.repository.save.GameStateMigrator;
import idv.kuan.studio.sango.repository.save.LocalJsonSaveGameRepository;

/** 退卻不即時回城、路線失效與 schema 8 升級的無圖形驗證。 */
public final class RetreatExpeditionSmokeTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path assets = Path.of(args[0]);
        FileHandle temp = new FileHandle(Files.createTempDirectory("sango-retreat-test-").toFile());
        try {
            AssetJsonGameDefinitionRepository definitions = definitions(assets);
            GameState state = new NewGameCommand(definitions, new LocalJsonSaveGameRepository(temp))
                .execute(1, new NewGameRequest("warlords_china", "cao_cao"));
            StrategicMapDefinition map = definitions.requireMap(state.mapId);
            CityState origin = state.requireCityState("chenliu");
            CityState target = state.requireCityState("runan");
            origin.ownerFactionId = "cao_cao";
            origin.troops = 2_000;
            target.ownerFactionId = "yellow_turban";
            target.troops = 100_000;
            target.defense = 100;

            ArmyState army = army(state, "chenliu", "runan", 800);
            state.addArmy(army);
            new BattleResolutionService().resolveArrival(state, army, map, new TurnResolutionReport(190, 1));
            check(army.isRetreating() && state.hasArmyForFaction("cao_cao"), "戰敗生還者改為在途退卻");
            check("runan".equals(army.retreatCurrentCityId()) && "chenliu".equals(army.retreatDestinationCityId()),
                "退卻以原戰場開始並優先回原城");
            new RetreatResolutionService().resolve(state, map, Set.of(), new TurnResolutionReport(190, 2));
            check(state.hasArmyForFaction("cao_cao"), "新敗軍當月不得移動或合併");
            new RetreatResolutionService().resolve(state, map, Set.of(army.armyId), new TurnResolutionReport(190, 3));
            check(!state.hasArmyForFaction("cao_cao") && origin.troops > 2_000,
                "下月抵達安全城後才併入守軍");

            state.addArmy(army(state, "chenliu", "runan", 400));
            GameState legacy = state.copy();
            legacy.schemaVersion = 8;
            GameState migrated = new GameStateMigrator().migrate(legacy);
            check(migrated.schemaVersion == 9, "schema 8 升級到 9");
            for (ArmyState migratedArmy : migrated.armyStates) {
                check(migratedArmy.retreatRouteCityIds == null && migratedArmy.retreatRouteIndex == 0,
                    "schema 8 軍隊取得空白退卻欄位");
            }
            GameStateValidator.validate(migrated);
            System.out.println("Sango retreat/expedition regression: PASS; checks=" + checks);
        } finally {
            temp.deleteDirectory();
        }
    }

    private static AssetJsonGameDefinitionRepository definitions(Path assets) {
        return new AssetJsonGameDefinitionRepository(
            new FileHandle(assets.resolve("data/scenarios/scenarios.json").toFile()),
            new FileHandle(assets.resolve("data/factions/factions.json").toFile()),
            new FileHandle(assets.resolve("data/cities/cities.json").toFile()),
            new FileHandle(assets.resolve("data/maps/maps.json").toFile()));
    }

    private static ArmyState army(GameState state, String origin, String target, int troops) {
        ArmyState army = new ArmyState();
        army.armyId = state.allocateArmyId();
        army.expeditionGroupId = army.armyId;
        army.factionId = "cao_cao";
        army.originCityId = origin;
        army.targetCityId = target;
        army.remainingTravelMonths = 1;
        army.troops = troops;
        army.training = 20;
        army.morale = 20;
        army.tactic = BattleTactic.BALANCED;
        return army;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
