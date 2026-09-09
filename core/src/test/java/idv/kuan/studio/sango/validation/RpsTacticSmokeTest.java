package idv.kuan.studio.sango.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

import idv.kuan.studio.sango.SangoVersion;
import idv.kuan.studio.sango.application.command.NewGameCommand;
import idv.kuan.studio.sango.application.request.NewGameRequest;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.definition.CityConnectionDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.*;
import idv.kuan.studio.sango.domain.rule.*;
import idv.kuan.studio.sango.domain.service.*;
import idv.kuan.studio.sango.repository.definition.AssetJsonGameDefinitionRepository;
import idv.kuan.studio.sango.repository.save.*;

/** No graphics or player-save access: verifies the complete three-way combat contract. */
@SuppressWarnings("deprecation")
public final class RpsTacticSmokeTest {
    private static int checks;
    private static final BattleTactic[] ATTACK = {
        BattleTactic.ASSAULT, BattleTactic.FEINT, BattleTactic.HOLD
    };
    private static final DefensePolicy[] DEFENSE = {
        DefensePolicy.ASSAULT, DefensePolicy.FEINT, DefensePolicy.HOLD
    };
    // Independent specification: rows assault/feint/hold, columns assault/feint/hold.
    private static final int[][] ADVANTAGE = {{0, -1, 1}, {1, 0, -1}, {-1, 1, 0}};

    public static void main(String[] args) throws Exception {
        Path assets = Path.of(args[0]);
        FileHandle temp = new FileHandle(Files.createTempDirectory("sango-rps-test-").toFile());
        try {
            var definitions = new AssetJsonGameDefinitionRepository(
                new FileHandle(assets.resolve("data/scenarios/scenarios.json").toFile()),
                new FileHandle(assets.resolve("data/factions/factions.json").toFile()),
                new FileHandle(assets.resolve("data/cities/cities.json").toFile()),
                new FileHandle(assets.resolve("data/maps/maps.json").toFile()));
            var saves = new LocalJsonSaveGameRepository(temp);
            GameState initial = new NewGameCommand(definitions, saves).execute(
                1, new NewGameRequest("warlords_china", "cao_cao"));
            StrategicMapDefinition map = definitions.requireMap(initial.mapId);
            matrix(initial, map);
            mixed(initial, map);
            weightedMixed(initial, map);
            arrivals(initial, map);
            intelligence(initial, map);
            migration(initial, map, temp);
            System.out.println("Sango RPS tactics: PASS; checks=" + checks);
        } finally {
            temp.deleteDirectory();
        }
    }

    private static void matrix(GameState initial, StrategicMapDefinition map) {
        for (int a = 0; a < 3; a++) {
            for (int d = 0; d < 3; d++) {
                GameState state = initial.copy();
                String[] route = route(state, map, state.playerFactionId);
                CityState target = target(state, route[1], 1000, DEFENSE[d]);
                ArmyState army = army(state, route, "matrix", 1000, ATTACK[a]);
                int ap = ADVANTAGE[a][d] > 0 ? 110 : 100;
                int dp = ADVANTAGE[a][d] < 0 ? 110 : 100;
                check(MilitaryRules.calculateAttackerStrength(army) == 1000, "equal attacker base");
                check(MilitaryRules.calculateDefenderStrength(target) == 1000, "equal defender base");
                check(MilitaryRules.attackerMatchupPercent(ATTACK[a], DEFENSE[d]) == ap,
                    "independent attack matrix");
                check(MilitaryRules.defenderMatchupPercent(ATTACK[a], DEFENSE[d]) == dp,
                    "independent defense matrix");
                BattleReport report = resolve(state, map, List.of(army));
                GameStateValidator.validate(state);
                check(report.battleRulesVersion == 2, "new rules version recorded");
                check(report.attackerStrength == ap * 10L
                    && report.defenderBaseStrength == 1000
                    && report.defenderStrength == dp * 10
                    && report.defenderMatchupPercent == dp, "effective strength snapshots");
                check(report.cityCaptured == (ap >= dp), "equal bases follow matchup");
                check(report.attackerContributions[0].baseStrength == 1000
                    && report.attackerContributions[0].strength == ap * 10
                    && report.attackerContributions[0].attackerTactic == ATTACK[a],
                    "individual contribution snapshot");
                check(report.attackerLosses + report.attackerSurvivors == 1000
                    && report.defenderLosses + report.defenderSurvivors == 1000,
                    "troop conservation");
                BattleReport copy = report.copy();
                target.defensePolicy = DefensePolicy.FEINT;
                check(copy.defenderPolicy == DEFENSE[d]
                    && copy.attackerStrength == report.attackerStrength
                    && copy.defenderStrength == report.defenderStrength
                    && copy.defenderMatchupPercent == report.defenderMatchupPercent
                    && copy.battleRulesVersion == 2
                    && copy.attackerContributions[0].attackerTactic == ATTACK[a],
                    "report copy independent of later city changes");
            }
        }
        check(MilitaryRules.applyMatchupPercent(Integer.MAX_VALUE, 110) == Integer.MAX_VALUE,
            "matchup saturation does not overflow");
    }

    private static void mixed(GameState initial, StrategicMapDefinition map) {
        BattleReport first = mixedReport(initial, map, false);
        BattleReport reverse = mixedReport(initial, map, true);
        check(first.attackerStrength == 2100 && first.defenderBaseStrength == 3000
            && first.defenderMatchupPercent == 105 && first.defenderStrength == 3150,
            "mixed defender bonus uses countered base strength share");
        check(first.attackerStrength == reverse.attackerStrength
            && first.defenderStrength == reverse.defenderStrength
            && first.attackerLosses == reverse.attackerLosses
            && first.defenderLosses == reverse.defenderLosses
            && first.outcome == reverse.outcome, "mixed arrival order does not change battle");
        for (BattleContribution c : first.attackerContributions) {
            BattleContribution other = Arrays.stream(reverse.attackerContributions)
                .filter(x -> x.armyId.equals(c.armyId)).findFirst().orElseThrow();
            check(c.strength == other.strength && c.losses == other.losses
                && c.attackerTactic == other.attackerTactic, "per-army order independence");
        }
    }

    private static void weightedMixed(GameState initial, StrategicMapDefinition map) {
        GameState state = initial.copy();
        String[] route = route(state, map, state.playerFactionId);
        target(state, route[1], 3000, DefensePolicy.ASSAULT);
        ArmyState trained = army(state, route, "trained", 1000, BattleTactic.FEINT);
        trained.training = 100;
        ArmyState untrained = army(state, route, "untrained", 1000, BattleTactic.HOLD);
        BattleReport report = resolve(state, map, List.of(trained, untrained));
        check(report.attackerStrength == 3200 && report.defenderMatchupPercent == 103
            && report.defenderStrength == 3090,
            "mixed defense weighting uses base strength, not troop count or effective strength");
        check(report.cityCaptured, "modest matchup advantage preserves troop quality importance");
    }

    private static BattleReport mixedReport(GameState initial, StrategicMapDefinition map,
        boolean reverse) {
        GameState state = initial.copy();
        String[] route = route(state, map, state.playerFactionId);
        target(state, route[1], 3000, DefensePolicy.ASSAULT);
        ArmyState feint = army(state, route, "mixed-a", 1000, BattleTactic.FEINT);
        ArmyState hold = army(state, route, "mixed-b", 1000, BattleTactic.HOLD);
        return resolve(state, map, reverse ? List.of(hold, feint) : List.of(feint, hold));
    }

    private static void arrivals(GameState initial, StrategicMapDefinition map) {
        for (BattleTactic tactic : ATTACK) {
            GameState state = initial.copy();
            String[] route = route(state, map, state.playerFactionId);
            target(state, route[1], 0, DefensePolicy.HOLD);
            BattleReport report = resolve(state, map,
                List.of(army(state, route, "empty", 1000, tactic)));
            check(report.outcome == BattleOutcome.UNOPPOSED_OCCUPATION
                && report.battleRulesVersion == 2 && report.defenderMatchupPercent == 100
                && report.attackerLosses == 0 && report.defenderLosses == 0
                && report.attackerStrength == 1000 && report.defenderStrength == 0,
                "empty city no matchup bonus or casualties");
        }
        GameState state = initial.copy();
        String city = state.findCitiesOwnedBy(state.playerFactionId).get(0).cityId;
        CityState own = state.requireCityState(city);
        own.defensePolicy = DefensePolicy.FEINT;
        int troopsBefore = own.troops;
        new BattleResolutionService().resolveArrival(state,
            army(state, new String[] {city, city}, "transfer", 1000, BattleTactic.HOLD),
            map, new TurnResolutionReport(state.currentYear, state.currentMonth));
        check(own.troops == troopsBefore + 1000 && own.defensePolicy == DefensePolicy.FEINT
            && state.battleReports.length == 0, "friendly transfer does not battle or reset policy");
    }

    private static void intelligence(GameState initial, StrategicMapDefinition fullMap) {
        GameState base = initial.copy();
        String factionId = base.opponentFactionId;
        String[] route = route(base, fullMap, factionId);
        var map = new StrategicMapDefinition();
        map.connections = new CityConnectionDefinition[] {
            fullMap.findConnection(route[0], route[1])
        };
        for (FactionState faction : base.factionStates) {
            faction.aiActionPointsPerTurn = 0;
            faction.aiActionPointsRemaining = 0;
            faction.cityIntelligence = new CityIntelligenceSnapshot[0];
        }
        FactionState observer = base.requireFactionState(factionId);
        observer.aiActionPointsPerTurn = 1;
        observer.aiActionPointsRemaining = 1;
        observer.food = 10000;
        observer.gold = 10000;
        CityState origin = base.requireCityState(route[0]);
        origin.troops = 6000;
        origin.training = 60;
        origin.morale = 60;
        base.enemyAttackCountdown = 1;
        BattleTactic blindChoice = null;
        for (int d = 0; d < 3; d++) {
            GameState known = base.copy();
            known.requireCityState(route[1]).defensePolicy = DEFENSE[d];
            new CityIntelligenceService().observe(known, factionId, route[1]);
            // Deliberately change the truth after scouting; the observed policy must stay hidden.
            known.requireCityState(route[1]).defensePolicy = DEFENSE[(d + 1) % 3];
            BattleTactic choice = march(known, map, factionId).tactic;
            if (blindChoice == null) blindChoice = choice;
            check(choice == blindChoice, "AI cannot counter a defense policy hidden from scouting");
            check(known.requireFactionState(factionId).cityIntelligence[0].defensePolicy == DEFENSE[d],
                "AI decision does not refresh observation");
        }
        GameState unknownA = base.copy();
        GameState unknownB = base.copy();
        unknownA.requireCityState(route[1]).defensePolicy = DefensePolicy.ASSAULT;
        unknownB.requireCityState(route[1]).defensePolicy = DefensePolicy.HOLD;
        unknownB.requireCityState(route[1]).troops += 50000;
        check(march(unknownA, map, factionId).tactic == march(unknownB, map, factionId).tactic,
            "unknown live enemy values cannot change chosen tactic");

        GameState expiredA = base.copy();
        GameState expiredB = base.copy();
        expiredA.requireCityState(route[1]).defensePolicy = DefensePolicy.ASSAULT;
        expiredB.requireCityState(route[1]).defensePolicy = DefensePolicy.HOLD;
        new CityIntelligenceService().observe(expiredA, factionId, route[1]);
        new CityIntelligenceService().observe(expiredB, factionId, route[1]);
        expiredA.currentTurn += 3;
        expiredB.currentTurn += 3;
        check(march(expiredA, map, factionId).tactic == march(expiredB, map, factionId).tactic,
            "expired snapshot is not consulted");
    }

    private static ArmyState march(GameState state, StrategicMapDefinition map, String factionId) {
        new EnemyTurnService().execute(state, map,
            new TurnResolutionReport(state.currentYear, state.currentMonth));
        return Arrays.stream(state.armyStates).filter(a -> factionId.equals(a.factionId))
            .findFirst().orElseThrow(() -> new AssertionError("AI did not dispatch"));
    }

    private static void migration(GameState initial, StrategicMapDefinition map, FileHandle temp) {
        GameState legacy = initial.copy();
        String[] route = route(legacy, map, legacy.playerFactionId);
        target(legacy, route[1], 10000, DefensePolicy.HOLD);
        BattleReport oldReport = resolve(legacy, map,
            List.of(army(legacy, route, "historic", 1000, BattleTactic.HOLD)));
        oldReport.battleRulesVersion = 0;
        oldReport.attackerTactic = BattleTactic.CAUTIOUS;
        oldReport.defenderPolicy = DefensePolicy.AGGRESSIVE;
        oldReport.attackerStrength = 0;
        oldReport.defenderBaseStrength = 0;
        oldReport.defenderStrength = 0;
        oldReport.defenderMatchupPercent = 0;
        for (BattleContribution contribution : oldReport.attackerContributions) {
            contribution.attackerTactic = null;
            contribution.baseStrength = 0;
        }
        // Fresh pending armies exercise both old names independently of the historic report.
        legacy.armyStates = new ArmyState[] {
            army(legacy, route, "legacy-a", 1000, BattleTactic.BALANCED),
            army(legacy, route, "legacy-b", 1000, BattleTactic.CAUTIOUS)
        };
        legacy.requireCityState(route[0]).defensePolicy = DefensePolicy.BALANCED;
        legacy.requireCityState(route[1]).defensePolicy = DefensePolicy.AGGRESSIVE;
        var snapshot = new CityIntelligenceService().observe(legacy, legacy.playerFactionId, route[1]);
        int validThrough = snapshot.validThroughTurn;
        int observedTurn = snapshot.observedTurn;
        legacy.schemaVersion = 10;
        SaveGameDocument document = new SaveGameDocument();
        document.schemaVersion = SangoVersion.SAVE_DOCUMENT_SCHEMA_VERSION;
        document.gameVersion = "0.6.1";
        document.gameState = legacy;
        Json json = new Json();
        json.setUsePrototypes(false);
        json.setOutputType(JsonWriter.OutputType.json);
        JsonValue raw = new JsonReader().parse(json.toJson(document));
        for (JsonValue report : raw.get("gameState").get("battleReports")) {
            for (String name : new String[] {"battleRulesVersion", "attackerStrength",
                "defenderBaseStrength", "defenderStrength", "defenderMatchupPercent"}) report.remove(name);
            for (JsonValue c : report.get("attackerContributions")) {
                c.remove("attackerTactic");
                c.remove("baseStrength");
            }
        }
        String serialized = raw.toJson(JsonWriter.OutputType.json);
        check(serialized.contains("BALANCED") && serialized.contains("CAUTIOUS")
            && serialized.contains("AGGRESSIVE"), "fixture contains literal legacy enum names");
        FileHandle directory = temp.child("legacy");
        directory.mkdirs();
        FileHandle primary = directory.child("slot-01.json");
        primary.writeString(serialized, false, "UTF-8");
        var saves = new LocalJsonSaveGameRepository(directory);
        GameState migrated = saves.load(1);
        check(migrated.schemaVersion == SangoVersion.GAME_STATE_SCHEMA_VERSION
            && migrated.armyStates[0].tactic == BattleTactic.FEINT
            && migrated.armyStates[1].tactic == BattleTactic.HOLD, "old pending armies migrated");
        check(migrated.requireCityState(route[0]).defensePolicy == DefensePolicy.FEINT
            && migrated.requireCityState(route[1]).defensePolicy == DefensePolicy.ASSAULT,
            "old city policies migrated");
        CityIntelligenceSnapshot intel = migrated.requirePlayerFactionState().cityIntelligence[0];
        check(intel.defensePolicy == DefensePolicy.ASSAULT
            && intel.validThroughTurn == validThrough && intel.observedTurn == observedTurn,
            "snapshot policy mapped without changing observed time");
        BattleReport historic = migrated.battleReports[0];
        check(historic.battleRulesVersion == 0
            && historic.attackerTactic == BattleTactic.CAUTIOUS
            && historic.defenderPolicy == DefensePolicy.AGGRESSIVE
            && historic.attackerLosses == oldReport.attackerLosses
            && historic.outcome == oldReport.outcome, "historic names and result unchanged");
        check(primary.readString("UTF-8").equals(serialized), "load does not overwrite old save");
        saves.save(1, migrated);
        GameState roundTrip = saves.load(1);
        check(roundTrip.armyStates[0].tactic == BattleTactic.FEINT
            && roundTrip.battleReports[0].attackerTactic == BattleTactic.CAUTIOUS,
            "migrated state and legacy reports round-trip together");

        GameState current = initial.copy();
        String[] currentRoute = route(current, map, current.playerFactionId);
        target(current, currentRoute[1], 10000, DefensePolicy.ASSAULT);
        BattleReport report = resolve(current, map,
            List.of(army(current, currentRoute, "current", 1000, BattleTactic.FEINT)));
        saves.save(2, current);
        BattleReport loaded = saves.load(2).battleReports[0];
        check(loaded.battleRulesVersion == 2
            && loaded.attackerStrength == report.attackerStrength
            && loaded.defenderStrength == report.defenderStrength
            && loaded.attackerContributions[0].attackerTactic == BattleTactic.FEINT
            && loaded.attackerContributions[0].strength == report.attackerContributions[0].strength,
            "new battle snapshots survive JSON round-trip");
    }

    private static CityState target(GameState state, String cityId, int troops, DefensePolicy policy) {
        CityState city = state.requireCityState(cityId);
        city.troops = troops;
        city.training = 0;
        city.morale = 50;
        city.defense = 0;
        city.defensePolicy = policy;
        return city;
    }

    private static ArmyState army(GameState state, String[] route, String id, int troops,
        BattleTactic tactic) {
        ArmyState army = new ArmyState();
        army.armyId = id;
        army.expeditionGroupId = id;
        army.factionId = state.requireCityState(route[0]).ownerFactionId;
        army.originCityId = route[0];
        army.targetCityId = route[1];
        army.troops = troops;
        army.training = 0;
        army.morale = 50;
        army.tactic = tactic;
        army.remainingTravelMonths = 1;
        return army;
    }

    private static String[] route(GameState state, StrategicMapDefinition map, String faction) {
        for (CityConnectionDefinition edge : map.connections) {
            if (state.ownsCity(faction, edge.fromCityId) && !state.ownsCity(faction, edge.toCityId))
                return new String[] {edge.fromCityId, edge.toCityId};
            if (state.ownsCity(faction, edge.toCityId) && !state.ownsCity(faction, edge.fromCityId))
                return new String[] {edge.toCityId, edge.fromCityId};
        }
        throw new AssertionError("No foreign edge");
    }

    private static BattleReport resolve(GameState state, StrategicMapDefinition map,
        List<ArmyState> armies) {
        new BattleResolutionService().resolveArrival(state, armies, map,
            new TurnResolutionReport(state.currentYear, state.currentMonth));
        return state.battleReports[state.battleReports.length - 1];
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
