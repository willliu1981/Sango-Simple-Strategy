package idv.kuan.studio.sango.domain.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import idv.kuan.studio.sango.domain.definition.CityConnectionDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;

/** 只沿同勢力城市道路尋找敗軍最近的安全退卻城市。 */
public final class RetreatRoutePlanner {
    public RoutePlan findRoute(GameState gameState, StrategicMapDefinition mapDefinition,
        String factionId, String startCityId, String originalOriginCityId) {
        Map<String, RouteNode> bestByCity = new HashMap<>();
        PriorityQueue<RouteNode> pending = new PriorityQueue<>(Comparator
            .comparingInt((RouteNode node) -> node.travelMonths)
            .thenComparing(node -> node.pathKey));
        RouteNode start = new RouteNode(startCityId, 0, List.of(startCityId));
        bestByCity.put(startCityId, start);
        pending.add(start);

        while (!pending.isEmpty()) {
            RouteNode current = pending.remove();
            if (bestByCity.get(current.cityId) != current) {
                continue;
            }
            if (mapDefinition.connections == null) {
                continue;
            }
            for (CityConnectionDefinition connection : mapDefinition.connections) {
                if (connection == null || connection.travelMonths <= 0) {
                    continue;
                }
                String nextCityId = connection.fromCityId.equals(current.cityId)
                    ? connection.toCityId
                    : connection.toCityId.equals(current.cityId) ? connection.fromCityId : null;
                if (nextCityId == null || !gameState.ownsCity(factionId, nextCityId)) {
                    continue;
                }
                int nextMonths = Math.addExact(current.travelMonths, connection.travelMonths);
                List<String> nextPath = new ArrayList<>(current.path);
                nextPath.add(nextCityId);
                RouteNode candidate = new RouteNode(nextCityId, nextMonths, nextPath);
                RouteNode previous = bestByCity.get(nextCityId);
                if (previous == null || candidate.travelMonths < previous.travelMonths
                    || candidate.travelMonths == previous.travelMonths
                    && candidate.pathKey.compareTo(previous.pathKey) < 0) {
                    bestByCity.put(nextCityId, candidate);
                    pending.add(candidate);
                }
            }
        }

        RouteNode selected = null;
        for (CityState cityState : gameState.findCitiesOwnedBy(factionId)) {
            RouteNode candidate = bestByCity.get(cityState.cityId);
            if (candidate == null || candidate.cityId.equals(startCityId)) {
                continue;
            }
            if (selected == null || compareDestinations(gameState, candidate, selected,
                originalOriginCityId) < 0) {
                selected = candidate;
            }
        }
        return selected == null ? null
            : new RoutePlan(selected.path.toArray(new String[0]), selected.travelMonths);
    }

    public boolean isRouteValid(GameState gameState, StrategicMapDefinition mapDefinition,
        ArmyState armyState) {
        if (armyState.returningFromRoad) {
            return armyState.remainingTravelMonths > 0
                && gameState.ownsCity(armyState.factionId, armyState.originCityId);
        }
        if (!armyState.isRetreating() || armyState.retreatRouteCityIds.length < 2
            || armyState.retreatRouteIndex < 0
            || armyState.retreatRouteIndex >= armyState.retreatRouteCityIds.length - 1
            || !armyState.targetCityId.equals(armyState.retreatRouteCityIds[0])
            || armyState.remainingTravelMonths <= 0) {
            return false;
        }
        for (int index = 0; index < armyState.retreatRouteCityIds.length - 1; index++) {
            CityConnectionDefinition connection = mapDefinition.findConnection(
                armyState.retreatRouteCityIds[index], armyState.retreatRouteCityIds[index + 1]);
            if (connection == null || connection.travelMonths <= 0) {
                return false;
            }
            if (index == armyState.retreatRouteIndex
                && armyState.remainingTravelMonths > connection.travelMonths) {
                return false;
            }
        }
        if (armyState.retreatRouteIndex > 0
            && !gameState.ownsCity(armyState.factionId,
                armyState.retreatRouteCityIds[armyState.retreatRouteIndex])) {
            return false;
        }
        for (int index = armyState.retreatRouteIndex + 1;
            index < armyState.retreatRouteCityIds.length; index++) {
            if (!gameState.ownsCity(armyState.factionId, armyState.retreatRouteCityIds[index])) {
                return false;
            }
        }
        return true;
    }

    private int compareDestinations(GameState gameState, RouteNode first, RouteNode second,
        String originalOriginCityId) {
        int compared = Integer.compare(first.travelMonths, second.travelMonths);
        if (compared != 0) {
            return compared;
        }
        compared = Boolean.compare(!first.cityId.equals(originalOriginCityId),
            !second.cityId.equals(originalOriginCityId));
        if (compared != 0) {
            return compared;
        }
        compared = Integer.compare(gameState.requireCityState(second.cityId).defense,
            gameState.requireCityState(first.cityId).defense);
        return compared != 0 ? compared : first.cityId.compareTo(second.cityId);
    }

    public record RoutePlan(String[] cityIds, int totalTravelMonths) {
        public RoutePlan {
            cityIds = cityIds.clone();
        }

        @Override
        public String[] cityIds() {
            return cityIds.clone();
        }
    }

    private static final class RouteNode {
        private final String cityId;
        private final int travelMonths;
        private final List<String> path;
        private final String pathKey;

        private RouteNode(String cityId, int travelMonths, List<String> path) {
            this.cityId = cityId;
            this.travelMonths = travelMonths;
            this.path = path;
            this.pathKey = String.join("\u0000", path);
        }
    }
}
