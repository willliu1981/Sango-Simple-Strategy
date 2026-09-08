package idv.kuan.studio.sango.domain.definition;

/**
 * 戰略地圖兩座城池間的雙向道路。
 */
public final class CityConnectionDefinition {
    public String fromCityId;
    public String toCityId;
    public int travelMonths;

    public CityConnectionDefinition() {
    }

    public boolean connects(String firstCityId, String secondCityId) {
        return fromCityId.equals(firstCityId) && toCityId.equals(secondCityId)
            || fromCityId.equals(secondCityId) && toCityId.equals(firstCityId);
    }
}
