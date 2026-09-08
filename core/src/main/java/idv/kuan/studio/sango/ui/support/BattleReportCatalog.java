package idv.kuan.studio.sango.ui.support;

import java.util.ArrayList;
import java.util.List;

import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.GameState;

/** Report views reference the same persisted battles, including their read state. */
public final class BattleReportCatalog {
    private BattleReportCatalog() { }

    public static List<BattleReport> world(GameState state) {
        BattleReport latest = null;
        if (state.battleReports != null) {
            for (BattleReport report : state.battleReports) {
                if (report != null && (latest == null || report.resolvedTurn >= latest.resolvedTurn)) {
                    latest = report;
                }
            }
        }
        return latest == null ? List.of() : List.of(latest);
    }

    public static List<BattleReport> city(GameState state, String cityId) {
        BattleReport latest = state.findLatestBattleReportForCity(cityId);
        return latest == null ? List.of() : List.of(latest);
    }

    public static List<BattleReport> latestCities(GameState state) {
        java.util.Map<String, BattleReport> latest = new java.util.LinkedHashMap<>();
        if (state.battleReports != null) {
            for (BattleReport report : state.battleReports) {
                if (report != null) latest.put(report.targetCityId, report);
            }
        }
        return new ArrayList<>(latest.values());
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
