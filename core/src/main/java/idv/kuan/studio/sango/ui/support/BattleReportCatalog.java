package idv.kuan.studio.sango.ui.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.GameState;

/** Report views reference the same persisted battles, including their read state. */
public final class BattleReportCatalog {
    private BattleReportCatalog() { }

    public static List<BattleReport> world(GameState state) {
        List<BattleReport> reports = new ArrayList<>();
        if (state.battleReports != null) {
            for (BattleReport report : state.battleReports) {
                if (report != null) reports.add(report);
            }
        }
        reports.sort(Comparator
            .comparing((BattleReport report) -> !report.involvesFaction(state.playerFactionId))
            .thenComparing(Comparator.comparingInt((BattleReport report) -> report.resolvedTurn).reversed())
            .thenComparing(report -> report.battleId));
        return reports;
    }

    public static List<BattleReport> playerMonth(GameState state, TurnResolutionReport month) {
        List<BattleReport> reports = new ArrayList<>();
        if (month == null) return reports;
        for (String id : month.getBattleReportIds()) {
            BattleReport report = state.findBattleReport(id);
            if (report != null && report.involvesFaction(state.playerFactionId)
                && reports.stream().noneMatch(existing -> existing.battleId.equals(id))) {
                reports.add(report);
            }
        }
        return reports;
    }
}
