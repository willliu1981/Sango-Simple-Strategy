package idv.kuan.studio.sango.validation;

import java.util.List;

import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.ui.support.BattleReportCatalog;

/** Checks report membership independently of rendering or a live save file. */
public final class ReportCatalogSmokeTest {
    public static void main(String[] args) {
        GameState state = new GameState();
        state.playerFactionId = "player";
        BattleReport other = battle("other", "enemy-a", "enemy-b", 12);
        BattleReport defense = battle("defense", "enemy-a", "player", 9);
        BattleReport attack = battle("attack", "player", "enemy-b", 11);
        state.battleReports = new BattleReport[] { other, defense, attack };
        List<BattleReport> world = BattleReportCatalog.world(state);
        require(world.equals(List.of(other)), "World must show the latest battle month");
        require(state.battleReports[0] == other, "Sorting must not reorder saved history");
        other.read = true;
        require(BattleReportCatalog.world(state).get(0).read, "Read state must be shared, not copied or removed");
        defense.targetCityId = "city-a";
        attack.targetCityId = "city-a";
        other.targetCityId = "city-b";
        require(BattleReportCatalog.city(state, "city-a").equals(List.of(attack)), "City must show its latest battle");
        require(BattleReportCatalog.city(state, "missing").isEmpty(), "City without battles must be empty");
        require(BattleReportCatalog.latestCities(state).size() == 2, "Old city battles must not inflate alerts");
        BattleReport sameTurn = battle("same-turn", "player", "enemy-b", 12);
        state.battleReports = new BattleReport[] {other, defense, attack, sameTurn};
        require(BattleReportCatalog.world(state).equals(List.of(sameTurn, other)),
            "World must retain every battle from the latest turn, with player battles first");
        require(BattleReportCatalog.latestForCity(state, "city-a") == attack,
            "City reports must resolve to that city's latest battle");
        state.battleReports = new BattleReport[] {other, defense, attack};

        TurnResolutionReport month = new TurnResolutionReport(190, 1);
        month.addBattleReportId("other");
        month.addBattleReportId("defense");
        month.addBattleReportId("missing");
        month.addBattleReportId("attack");
        month.addBattleReportId("attack");
        require(BattleReportCatalog.playerMonth(state, month).equals(List.of(defense, attack)),
            "Faction month must exclude unrelated, missing and duplicate battles, including already-read ones");
        require(BattleReportCatalog.playerMonth(state, null).isEmpty(), "No month means no faction battles");
        state.battleReports = null;
        require(BattleReportCatalog.world(state).isEmpty(), "Empty saved history must be supported");
        System.out.println("ReportCatalogSmokeTest passed");
    }

    private static BattleReport battle(String id, String attacker, String defender, int turn) {
        BattleReport report = new BattleReport();
        report.battleId = id;
        report.attackerFactionId = attacker;
        report.defenderFactionId = defender;
        report.resolvedTurn = turn;
        return report;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
