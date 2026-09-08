package idv.kuan.studio.sango.validation;

import java.util.List;
import java.util.Map;

import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.ui.support.TransferOriginOrder;

/** 驗證運兵視窗排序，不觸及出征規則或 Graphics Context。 */
public final class DispatchOriginOrderingSmokeTest {
    private static int checks;

    private DispatchOriginOrderingSmokeTest() {
    }

    public static void main(String[] arguments) {
        CityState pingyuan = city("pingyuan");
        CityState xiaopei = city("xiaopei");
        CityState tied = city("beihai");
        List<CityState> candidates = List.of(xiaopei, pingyuan, tied);
        Map<String, Integer> travel = Map.of("pingyuan", 1, "xiaopei", 2, "beihai", 1);
        Map<String, Integer> dispatch = Map.of("pingyuan", 800, "xiaopei", 1800, "beihai", 800);

        List<CityState> shortest = TransferOriginOrder.SHORTEST_TRAVEL.sort(
            candidates, city -> travel.get(city.cityId), city -> dispatch.get(city.cityId)
        );
        check(ids(shortest).equals(List.of("beihai", "pingyuan", "xiaopei")),
            "路程最短由短到長，平手依 cityId 穩定排序");

        List<CityState> mostDispatch = TransferOriginOrder.MOST_DISPATCH.sort(
            candidates, city -> travel.get(city.cityId), city -> dispatch.get(city.cityId)
        );
        check(ids(mostDispatch).equals(List.of("xiaopei", "beihai", "pingyuan")),
            "可派兵最多由多到少，平手再依路程與 cityId 排序");
        check(TransferOriginOrder.SHORTEST_TRAVEL.next() == TransferOriginOrder.MOST_DISPATCH
            && TransferOriginOrder.MOST_DISPATCH.next() == TransferOriginOrder.SHORTEST_TRAVEL,
            "排序按鈕在兩種模式間循環");
        System.out.println("Sango dispatch origin ordering: PASS; checks=" + checks);
    }

    private static CityState city(String cityId) {
        CityState city = new CityState();
        city.cityId = cityId;
        return city;
    }

    private static List<String> ids(List<CityState> cities) {
        return cities.stream().map(city -> city.cityId).toList();
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
