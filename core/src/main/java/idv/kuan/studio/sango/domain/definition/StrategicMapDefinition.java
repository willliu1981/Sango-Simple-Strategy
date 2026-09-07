package idv.kuan.studio.sango.domain.definition;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 小型區域戰略地圖的固定節點與道路資料。
 */
public final class StrategicMapDefinition {
    public String id;
    public String nameKey;
    public String backgroundAssetPath;
    public MapCityNodeDefinition[] nodes;
    public CityConnectionDefinition[] connections;

    public StrategicMapDefinition() {
    }

    public MapCityNodeDefinition requireNode(String cityId) {
        if (nodes != null) {
            for (MapCityNodeDefinition nodeDefinition : nodes) {
                if (nodeDefinition != null && cityId.equals(nodeDefinition.cityId)) {
                    return nodeDefinition;
                }
            }
        }
        throw new IllegalArgumentException("地圖中找不到城池節點：" + cityId);
    }

    public CityConnectionDefinition findConnection(String firstCityId, String secondCityId) {
        if (connections != null) {
            for (CityConnectionDefinition connectionDefinition : connections) {
                if (connectionDefinition != null
                    && connectionDefinition.connects(firstCityId, secondCityId)) {
                    return connectionDefinition;
                }
            }
        }
        return null;
    }

    /** 依道路網路計算兩城最短行程；未連通時回傳 -1。 */
    public int shortestTravelMonths(String originCityId, String targetCityId) {
        if (originCityId.equals(targetCityId)) {
            return 0;
        }
        Map<String, Integer> distances = new HashMap<>();
        PriorityQueue<RouteStep> pending = new PriorityQueue<>();
        distances.put(originCityId, 0);
        pending.add(new RouteStep(originCityId, 0));
        while (!pending.isEmpty()) {
            RouteStep current = pending.remove();
            if (current.travelMonths != distances.getOrDefault(current.cityId, -1)) {
                continue;
            }
            if (targetCityId.equals(current.cityId)) {
                return current.travelMonths;
            }
            for (CityConnectionDefinition connection : connections) {
                String nextCityId = connection.fromCityId.equals(current.cityId) ? connection.toCityId
                    : connection.toCityId.equals(current.cityId) ? connection.fromCityId : null;
                if (nextCityId == null) {
                    continue;
                }
                int nextDistance = current.travelMonths + connection.travelMonths;
                if (nextDistance < distances.getOrDefault(nextCityId, Integer.MAX_VALUE)) {
                    distances.put(nextCityId, nextDistance);
                    pending.add(new RouteStep(nextCityId, nextDistance));
                }
            }
        }
        return -1;
    }

    private static final class RouteStep implements Comparable<RouteStep> {
        private final String cityId;
        private final int travelMonths;

        private RouteStep(String cityId, int travelMonths) {
            this.cityId = cityId;
            this.travelMonths = travelMonths;
        }

        @Override
        public int compareTo(RouteStep other) {
            return Integer.compare(travelMonths, other.travelMonths);
        }
    }
}
