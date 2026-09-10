package idv.kuan.studio.sango.domain.service;

import java.util.Set;

import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.definition.CityConnectionDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.BattleOutcome;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.CityIntelligenceSnapshot;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.DefensePolicy;
import idv.kuan.studio.sango.domain.rule.PostEncounterOrder;
import idv.kuan.studio.sango.repository.save.GameStateMigrator;

public final class PlanningAndRoadEncounterTest {
    public static void main(String[] args) {
        PlanningAndRoadEncounterTest test = new PlanningAndRoadEncounterTest();
        test.scoutingReadsMonthStartSnapshotAndNeverStoresPolicy();
        test.hostileArmiesMeetOnRoadAndLoserReturnsWithoutPretendingToOccupyCity();
        test.drawReturnsBothAndAutoUsesSurvivalAndMoraleThresholds();
        test.returningArmyDisbandsWhenItsOriginHasFallen();
        test.roadEncounterRequiresOppositeRoutesThatCrossThisMonth();
        test.eachArmyCanFightOnlyOncePerMonth();
        test.schemaElevenMigrationCreatesDeepSnapshotAndDropsPolicyIntel();
        test.aiHabitUsesOwnReportsAtFiftyAndSeventyPercentBoundaries();
        test.aiHabitIgnoresRoadReportsAndStopsAtDifferentPolicy();
        test.aiTacticCannotReadCurrentHiddenPolicy();
        System.out.println("PlanningAndRoadEncounterTest passed");
    }

    void scoutingReadsMonthStartSnapshotAndNeverStoresPolicy() {
        GameState state = new GameState();
        state.currentTurn = 5;
        state.currentYear = 1;
        state.currentMonth = 5;
        state.factionStates = new FactionState[] {faction("a"), faction("b")};
        state.cityStates = new CityState[] {city("city-a", "a", 2_000, DefensePolicy.ASSAULT)};
        state.turnStartCityStates = new CityState[] {city("city-a", "a", 1_200, DefensePolicy.HOLD)};

        CityIntelligenceSnapshot snapshot = new CityIntelligenceService()
            .observe(state, "b", "city-a");

        assertEquals(1_200, snapshot.troops);
        assertEquals(2_000, state.requireCityState("city-a").troops);
        assertNull(snapshot.defensePolicy);
        assertEquals(1_200, new CityIntelligenceService().knownView(state, "b", "city-a").troops());
    }

    void hostileArmiesMeetOnRoadAndLoserReturnsWithoutPretendingToOccupyCity() {
        GameState state = encounterState(
            army("army-a", "a", "city-a", "city-b", 2_000,
                BattleTactic.ASSAULT, PostEncounterOrder.CONTINUE),
            army("army-b", "b", "city-b", "city-a", 600,
                BattleTactic.HOLD, PostEncounterOrder.AUTO));

        new RoadEncounterResolutionService().resolve(state, directMap(),
            new TurnResolutionReport(1, 1));

        assertEquals(1, state.battleReports.length);
        BattleReport battle = state.battleReports[0];
        assertTrue(battle.routeEncounter);
        assertEquals(BattleOutcome.ATTACKER_VICTORY, battle.outcome);
        assertTrue(battle.attackerContinued);
        ArmyState loser = state.armyStates[1];
        assertTrue(loser.returningFromRoad);
        assertNull(loser.retreatCurrentCityId());
        assertEquals("city-b", loser.retreatDestinationCityId());
    }

    void drawReturnsBothAndAutoUsesSurvivalAndMoraleThresholds() {
        GameState state = encounterState(
            army("army-a", "a", "city-a", "city-b", 1_000,
                BattleTactic.FEINT, PostEncounterOrder.CONTINUE),
            army("army-b", "b", "city-b", "city-a", 1_000,
                BattleTactic.FEINT, PostEncounterOrder.CONTINUE));
        RoadEncounterResolutionService service = new RoadEncounterResolutionService();
        service.resolve(state, directMap(), new TurnResolutionReport(1, 1));
        assertEquals(BattleOutcome.DRAW, state.battleReports[0].outcome);
        assertNull(state.battleReports[0].winnerFactionId);
        assertTrue(state.armyStates[0].returningFromRoad);
        assertTrue(state.armyStates[1].returningFromRoad);

        ArmyState auto = army("auto", "a", "city-a", "city-b", 650,
            BattleTactic.HOLD, PostEncounterOrder.AUTO);
        auto.initialTroops = 1_000;
        auto.morale = 39;
        assertFalse(service.shouldContinue(auto));
        auto.morale = 40;
        assertTrue(service.shouldContinue(auto));
    }

    void returningArmyDisbandsWhenItsOriginHasFallen() {
        ArmyState returning = army("returning", "a", "city-a", "city-b", 700,
            BattleTactic.FEINT, PostEncounterOrder.RETURN);
        returning.returningFromRoad = true;
        GameState state = encounterState(returning);
        state.cityStates = new CityState[] {city("city-a", "b", 1_000, DefensePolicy.HOLD)};
        TurnResolutionReport report = new TurnResolutionReport(1, 1);

        new RetreatResolutionService().resolve(state, directMap(), Set.of("returning"), report);

        assertEquals(0, state.armyStates.length);
        assertTrue(report.getEvents().stream().anyMatch(event ->
            event.getType() == TurnEventType.ARMY_RETREAT_DISBANDED
                && event.getCityId() == null
                && "city-a".equals(event.getOtherCityId())
                && event.getPrimaryValue() == 700));
        assertEquals(1_000, state.requireCityState("city-a").troops);
    }

    void roadEncounterRequiresOppositeRoutesThatCrossThisMonth() {
        ArmyState farA = army("far-a", "a", "city-a", "city-b", 1_000,
            BattleTactic.ASSAULT, PostEncounterOrder.CONTINUE);
        ArmyState farB = army("far-b", "b", "city-b", "city-a", 1_000,
            BattleTactic.HOLD, PostEncounterOrder.CONTINUE);
        farA.totalTravelMonths = farA.remainingTravelMonths = 4;
        farB.totalTravelMonths = farB.remainingTravelMonths = 4;
        GameState notCrossing = encounterState(farA, farB);
        new RoadEncounterResolutionService().resolve(notCrossing, directMap(),
            new TurnResolutionReport(1, 1));
        assertEquals(0, notCrossing.battleReports.length);
        assertFalse(farA.isRetreating());
        assertFalse(farB.isRetreating());

        ArmyState sameA = army("same-a", "a", "city-a", "city-b", 1_000,
            BattleTactic.ASSAULT, PostEncounterOrder.CONTINUE);
        ArmyState sameB = army("same-b", "b", "city-a", "city-b", 1_000,
            BattleTactic.HOLD, PostEncounterOrder.CONTINUE);
        GameState sameDirection = encounterState(sameA, sameB);
        new RoadEncounterResolutionService().resolve(sameDirection, directMap(),
            new TurnResolutionReport(1, 1));
        assertEquals(0, sameDirection.battleReports.length);
    }

    void eachArmyCanFightOnlyOncePerMonth() {
        ArmyState first = army("army-a", "a", "city-a", "city-b", 2_000,
            BattleTactic.ASSAULT, PostEncounterOrder.CONTINUE);
        ArmyState opponent = army("army-b", "b", "city-b", "city-a", 600,
            BattleTactic.HOLD, PostEncounterOrder.AUTO);
        ArmyState secondOpponent = army("army-c", "b", "city-b", "city-a", 600,
            BattleTactic.HOLD, PostEncounterOrder.AUTO);
        GameState state = encounterState(first, opponent, secondOpponent);

        new RoadEncounterResolutionService().resolve(state, directMap(),
            new TurnResolutionReport(1, 1));

        assertEquals(1, state.battleReports.length);
        assertFalse(secondOpponent.isRetreating());
        assertEquals(600, secondOpponent.troops);
    }

    void schemaElevenMigrationCreatesDeepSnapshotAndDropsPolicyIntel() {
        GameState legacy = new GameState();
        legacy.schemaVersion = 11;
        legacy.cityStates = new CityState[] {city("city-a", "a", 1_500, DefensePolicy.ASSAULT)};
        FactionState observer = faction("b");
        CityIntelligenceSnapshot intel = new CityIntelligenceSnapshot();
        intel.defensePolicy = DefensePolicy.HOLD;
        observer.cityIntelligence = new CityIntelligenceSnapshot[] {intel};
        legacy.factionStates = new FactionState[] {observer};
        legacy.armyStates = new ArmyState[] {army("army-a", "a", "city-a", "city-b", 900,
            BattleTactic.FEINT, null)};

        GameState migrated = new GameStateMigrator().migrate(legacy);

        assertEquals(12, migrated.schemaVersion);
        assertNotSame(migrated.cityStates[0], migrated.turnStartCityStates[0]);
        assertNull(migrated.factionStates[0].cityIntelligence[0].defensePolicy);
        assertEquals(PostEncounterOrder.AUTO, migrated.armyStates[0].postEncounterOrder);
    }

    void aiHabitUsesOwnReportsAtFiftyAndSeventyPercentBoundaries() {
        GameState state = new GameState();
        state.battleReports = new BattleReport[] {
            observedDefense("old-1", "ai", "player", DefensePolicy.HOLD),
            observedDefense("old-2", "ai", "player", DefensePolicy.HOLD),
            observedDefense("old-3", "ai", "player", DefensePolicy.HOLD)
        };
        KnownCityView target = new KnownCityView("city-b", "player", false,
            0, 0, 0, false, 0, 1_000, 0, 0, 0, 0, 0, 0, 0, 0);
        EnemyTurnService service = new EnemyTurnService();
        int selectedTurn = -1;
        for (int turn = 1; turn < 10_000; turn++) {
            int habitRoll = Math.floorMod(("ai|habit|city-a|city-b|" + turn).hashCode(), 100);
            int fallback = Math.floorMod(("ai|city-a|city-b|" + turn).hashCode(),
                BattleTactic.activeValues().length);
            if (habitRoll >= 50 && habitRoll < 70
                && BattleTactic.activeValues()[fallback] != BattleTactic.ASSAULT) {
                selectedTurn = turn;
                break;
            }
        }
        assertTrue(selectedTurn > 0);
        state.currentTurn = selectedTurn;
        assertEquals(BattleTactic.ASSAULT,
            service.chooseAttackTactic(state, "ai", "city-a", "city-b", target));
        state.battleReports = new BattleReport[] {state.battleReports[0], state.battleReports[1]};
        assertFalse(service.chooseAttackTactic(state, "ai", "city-a", "city-b", target)
            == BattleTactic.ASSAULT);
    }

    void aiHabitIgnoresRoadReportsAndStopsAtDifferentPolicy() {
        KnownCityView target = knownEnemyCity();
        EnemyTurnService service = new EnemyTurnService();
        GameState roadIgnored = new GameState();
        BattleReport old = observedDefense("old", "ai", "player", DefensePolicy.HOLD);
        BattleReport recent = observedDefense("recent", "ai", "player", DefensePolicy.HOLD);
        BattleReport road = observedDefense("road", "ai", "player", DefensePolicy.FEINT);
        road.routeEncounter = true;
        roadIgnored.battleReports = new BattleReport[] {old, recent, road};
        int habitTurn = findTurn(0, 50, BattleTactic.ASSAULT, false);
        roadIgnored.currentTurn = habitTurn;
        assertEquals(BattleTactic.ASSAULT,
            service.chooseAttackTactic(roadIgnored, "ai", "city-a", "city-b", target));

        GameState interrupted = new GameState();
        interrupted.battleReports = new BattleReport[] {
            observedDefense("older", "ai", "player", DefensePolicy.HOLD),
            observedDefense("middle", "ai", "player", DefensePolicy.HOLD),
            observedDefense("newest", "ai", "player", DefensePolicy.FEINT)
        };
        int fallbackTurn = findTurn(0, 100, BattleTactic.HOLD, true);
        interrupted.currentTurn = fallbackTurn;
        BattleTactic expected = fallbackTactic(fallbackTurn);
        assertEquals(expected,
            service.chooseAttackTactic(interrupted, "ai", "city-a", "city-b", target));
    }

    void aiTacticCannotReadCurrentHiddenPolicy() {
        EnemyTurnService service = new EnemyTurnService();
        KnownCityView target = knownEnemyCity();
        GameState assault = new GameState();
        assault.currentTurn = 17;
        assault.battleReports = new BattleReport[0];
        assault.cityStates = new CityState[] {
            city("city-b", "player", 1_000, DefensePolicy.ASSAULT)
        };
        GameState hold = assault.copy();
        hold.requireCityState("city-b").defensePolicy = DefensePolicy.HOLD;
        assertEquals(
            service.chooseAttackTactic(assault, "ai", "city-a", "city-b", target),
            service.chooseAttackTactic(hold, "ai", "city-a", "city-b", target));
    }

    private KnownCityView knownEnemyCity() {
        return new KnownCityView("city-b", "player", false,
            0, 0, 0, false, 0, 1_000, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private int findTurn(int minimumHabitRoll, int maximumHabitRoll,
        BattleTactic excludedFallback, boolean requireDifferentFromHoldCounter) {
        for (int turn = 1; turn < 10_000; turn++) {
            int roll = Math.floorMod(("ai|habit|city-a|city-b|" + turn).hashCode(), 100);
            BattleTactic fallback = fallbackTactic(turn);
            if (roll >= minimumHabitRoll && roll < maximumHabitRoll
                && fallback != excludedFallback
                && (!requireDifferentFromHoldCounter || fallback != BattleTactic.ASSAULT)) {
                return turn;
            }
        }
        throw new AssertionError("No deterministic AI turn found");
    }

    private BattleTactic fallbackTactic(int turn) {
        BattleTactic[] values = BattleTactic.activeValues();
        return values[Math.floorMod(("ai|city-a|city-b|" + turn).hashCode(), values.length)];
    }

    private BattleReport observedDefense(String id, String attacker, String defender,
        DefensePolicy policy) {
        BattleReport report = new BattleReport();
        report.battleId = id;
        report.attackerFactionId = attacker;
        report.defenderFactionId = defender;
        report.defenderPolicyRecorded = true;
        report.defenderPolicy = policy;
        return report;
    }

    private GameState encounterState(ArmyState... armies) {
        GameState state = new GameState();
        state.currentTurn = 1;
        state.currentYear = 1;
        state.currentMonth = 1;
        state.nextBattleSequence = 1;
        state.armyStates = armies;
        state.battleReports = new BattleReport[0];
        return state;
    }

    private StrategicMapDefinition directMap() {
        CityConnectionDefinition connection = new CityConnectionDefinition();
        connection.fromCityId = "city-a";
        connection.toCityId = "city-b";
        connection.travelMonths = 2;
        StrategicMapDefinition map = new StrategicMapDefinition();
        map.connections = new CityConnectionDefinition[] {connection};
        return map;
    }

    private ArmyState army(String id, String factionId, String origin, String target,
        int troops, BattleTactic tactic, PostEncounterOrder order) {
        ArmyState army = new ArmyState();
        army.armyId = id;
        army.expeditionGroupId = id;
        army.factionId = factionId;
        army.originCityId = origin;
        army.targetCityId = target;
        army.remainingTravelMonths = 2;
        army.totalTravelMonths = 2;
        army.initialTroops = troops;
        army.troops = troops;
        army.training = 60;
        army.morale = 70;
        army.tactic = tactic;
        army.postEncounterOrder = order;
        return army;
    }

    private FactionState faction(String id) {
        FactionState faction = new FactionState();
        faction.factionId = id;
        faction.cityIntelligence = new CityIntelligenceSnapshot[0];
        return faction;
    }

    private CityState city(String id, String owner, int troops, DefensePolicy policy) {
        CityState city = new CityState();
        city.cityId = id;
        city.ownerFactionId = owner;
        city.troops = troops;
        city.population = 10_000;
        city.agriculture = 50;
        city.commerce = 50;
        city.waterControl = 50;
        city.defense = 50;
        city.training = 50;
        city.morale = 50;
        city.publicOrder = 50;
        city.defensePolicy = policy;
        return city;
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }

    private static void assertNull(Object value) {
        if (value != null) throw new AssertionError("Expected null but was " + value);
    }

    private static void assertNotSame(Object left, Object right) {
        if (left == right) throw new AssertionError("Expected different instances");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }
}
