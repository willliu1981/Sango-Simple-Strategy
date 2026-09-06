package idv.kuan.studio.sango.application.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 某一月份結束時產生的事件集合。
 */
public final class TurnResolutionReport {
    private final int resolvedYear;
    private final int resolvedMonth;
    private final List<TurnEvent> events = new ArrayList<>();

    public TurnResolutionReport(int resolvedYear, int resolvedMonth) {
        this.resolvedYear = resolvedYear;
        this.resolvedMonth = resolvedMonth;
    }

    public void add(TurnEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    public int getResolvedYear() {
        return resolvedYear;
    }

    public int getResolvedMonth() {
        return resolvedMonth;
    }

    public List<TurnEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }
}
