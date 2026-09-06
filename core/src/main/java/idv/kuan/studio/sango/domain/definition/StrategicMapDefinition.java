package idv.kuan.studio.sango.domain.definition;

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
}
