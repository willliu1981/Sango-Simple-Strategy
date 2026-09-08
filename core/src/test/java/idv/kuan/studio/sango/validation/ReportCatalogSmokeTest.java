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
        require(world.equals(List.of(attack, defense, other)), "Player attack/defense must lead newer third-party battles");
        require(state.battleReports[0] == other, "Sorting must not reorder saved history");
        attack.read = true;
        require(BattleReportCatalog.world(state).get(0).read, "Read state must be shared, not copied or removed");

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
