package idv.kuan.studio.sango.ui.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.GameState;

/** Report views reference the same persisted battles, including their read state. */
public final class BattleReportCatalog {
    public static final int VISIBLE_COMPLETED_MONTHS = 6;
    private BattleReportCatalog() { }

    public static List<BattleReport> world(GameState state) {
        if (state == null || state.battleReports == null) return List.of();
        int completedTurn = state.currentTurn - 1;
        int oldestVisibleTurn = Math.max(1, completedTurn - VISIBLE_COMPLETED_MONTHS + 1);
        if (completedTurn < 1) return List.of();
        List<BattleReport> reports = new ArrayList<>();
        for (BattleReport report : state.battleReports) {
            if (report != null && report.resolvedTurn >= oldestVisibleTurn
                && report.resolvedTurn <= completedTurn) reports.add(report);
        }
        reports.sort(Comparator
            .comparingInt((BattleReport report) -> report.resolvedTurn).reversed()
            .thenComparing(report -> !report.involvesFaction(state.playerFactionId))
            .thenComparing(report -> report.battleId, Comparator.reverseOrder()));
        return reports;
    }

    public static BattleReport latest(GameState state) {
        List<BattleReport> reports = world(state);
        return reports.isEmpty() ? null : reports.get(0);
    }

    public static BattleReport latestForCity(GameState state, String cityId) {
        BattleReport latest = null;
        if (state == null || state.battleReports == null || state.currentTurn <= 1) return null;
        int completedTurn = state.currentTurn - 1;
        int oldestVisibleTurn = Math.max(1, completedTurn - VISIBLE_COMPLETED_MONTHS + 1);
        for (BattleReport report : state.battleReports) {
            if (report != null && report.resolvedTurn >= oldestVisibleTurn
                && report.resolvedTurn <= completedTurn
                && touchesCity(report, cityId) && isNewer(report, latest)) {
                latest = report;
            }
        }
        return latest;
    }

    public static List<BattleReport> city(GameState state, String cityId) {
        if (state == null || state.battleReports == null || state.currentTurn <= 1) return List.of();
        int completedTurn = state.currentTurn - 1;
        int oldestVisibleTurn = Math.max(1, completedTurn - VISIBLE_COMPLETED_MONTHS + 1);
        List<BattleReport> reports = new ArrayList<>();
        for (BattleReport report : state.battleReports) {
            if (report != null && report.resolvedTurn >= oldestVisibleTurn
                && report.resolvedTurn <= completedTurn && touchesCity(report, cityId)) {
                reports.add(report);
            }
        }
        reports.sort(Comparator.comparingInt((BattleReport report) -> report.resolvedTurn)
            .reversed().thenComparing(report -> report.battleId, Comparator.reverseOrder()));
        return reports;
    }

    public static boolean isLatestCompletedMonth(GameState state, BattleReport report) {
        return state != null && report != null && report.resolvedTurn == state.currentTurn - 1;
    }

    public static List<BattleReport> latestCities(GameState state) {
        java.util.Map<String, BattleReport> latest = new java.util.LinkedHashMap<>();
        int completedTurn = state == null ? -1 : state.currentTurn - 1;
        if (completedTurn >= 1 && state.battleReports != null) {
            for (BattleReport report : state.battleReports) {
                if (report != null && report.resolvedTurn == completedTurn) {
                    putIfNewer(latest, report.targetCityId, report);
                    if (report.routeEncounter) putIfNewer(latest, report.originCityId, report);
                }
            }
        }
        java.util.Map<String, BattleReport> unique = new java.util.LinkedHashMap<>();
        for (BattleReport report : latest.values()) {
            unique.putIfAbsent(report.battleId, report);
        }
        return new ArrayList<>(unique.values());
    }

    private static void putIfNewer(java.util.Map<String, BattleReport> latest,
        String cityId, BattleReport report) {
        if (cityId != null && isNewer(report, latest.get(cityId))) {
            latest.put(cityId, report);
        }
    }

    private static boolean isNewer(BattleReport candidate, BattleReport current) {
        return candidate != null && (current == null || candidate.resolvedTurn > current.resolvedTurn
            || (candidate.resolvedTurn == current.resolvedTurn
                && candidate.battleId.compareTo(current.battleId) > 0));
    }

    private static boolean touchesCity(BattleReport report, String cityId) {
        return cityId.equals(report.targetCityId)
            || report.routeEncounter && cityId.equals(report.originCityId);
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
