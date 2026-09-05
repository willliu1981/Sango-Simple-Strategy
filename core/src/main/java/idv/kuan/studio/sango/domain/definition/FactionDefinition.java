package idv.kuan.studio.sango.domain.definition;

/**
 * 勢力固定資料。戰局中會變動的數值應放在 FactionState。
 */
public final class FactionDefinition {
    public String id;
    public String nameKey;
    public String rulerNameKey;
    public String summaryKey;
    public String capitalCityId;
    public int initialGold;
    public int initialFood;

    public FactionDefinition() {
    }
}
