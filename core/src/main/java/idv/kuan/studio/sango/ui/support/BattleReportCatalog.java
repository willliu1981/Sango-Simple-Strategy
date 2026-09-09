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
        if (state == null || state.battleReports == null) return List.of();
        int latestTurn = -1;
        for (BattleReport report : state.battleReports) {
            if (report != null) latestTurn = Math.max(latestTurn, report.resolvedTurn);
        }
        if (latestTurn < 0) return List.of();
        List<BattleReport> reports = new ArrayList<>();
        for (BattleReport report : state.battleReports) {
            if (report != null && report.resolvedTurn == latestTurn) reports.add(report);
        }
        reports.sort(Comparator
            .comparing((BattleReport report) -> !report.involvesFaction(state.playerFactionId))
            .thenComparing(report -> report.battleId, Comparator.reverseOrder()));
        return reports;
    }

    public static BattleReport latest(GameState state) {
        List<BattleReport> reports = world(state);
        return reports.isEmpty() ? null : reports.get(0);
    }

    public static BattleReport latestForCity(GameState state, String cityId) {
        BattleReport latest = null;
        if (state.battleReports == null) return null;
        for (BattleReport report : state.battleReports) {
            if (report != null && cityId.equals(report.targetCityId) && isNewer(report, latest)) {
                latest = report;
            }
        }
        return latest;
    }

    public static List<BattleReport> city(GameState state, String cityId) {
        BattleReport latest = latestForCity(state, cityId);
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

    private static boolean isNewer(BattleReport candidate, BattleReport current) {
        return candidate != null && (current == null || candidate.resolvedTurn > current.resolvedTurn
            || (candidate.resolvedTurn == current.resolvedTurn
                && candidate.battleId.compareTo(current.battleId) > 0));
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
