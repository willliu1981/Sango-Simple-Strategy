package idv.kuan.studio.sango.ui.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

import idv.kuan.studio.sango.domain.model.CityState;

/** 運兵來源的畫面排序；不改變出征候選或命令規則。 */
public enum TransferOriginOrder {
    SHORTEST_TRAVEL,
    MOST_DISPATCH;

    public List<CityState> sort(
        List<CityState> candidates,
        ToIntFunction<CityState> travelMonths,
        ToIntFunction<CityState> dispatchTroops
    ) {
        List<CityState> sorted = new ArrayList<>(candidates);
        Comparator<CityState> comparator = this == SHORTEST_TRAVEL
            ? Comparator.comparingInt(travelMonths)
                .thenComparing(city -> city.cityId)
            : Comparator.comparingInt(dispatchTroops).reversed()
                .thenComparingInt(travelMonths)
                .thenComparing(city -> city.cityId);
        sorted.sort(comparator);
        return sorted;
    }

    public TransferOriginOrder next() {
        return this == SHORTEST_TRAVEL ? MOST_DISPATCH : SHORTEST_TRAVEL;
    }
}
