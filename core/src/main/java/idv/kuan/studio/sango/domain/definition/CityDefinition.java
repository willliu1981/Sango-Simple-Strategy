package idv.kuan.studio.sango.domain.definition;

/**
 * 城池固定資料。初始值只在建立新局時使用。
 */
public final class CityDefinition {
    public String id;
    public String nameKey;
    public int initialPopulation;
    /** 人口成長容量；不回頭覆寫舊存檔人口。 */
    public int populationCapacity;
    public int initialAgriculture;
    public int initialCommerce;
    public int initialWaterControl;
    public int initialDefense;
    public int initialTroops;
    public int initialPublicOrder;
    public int initialTraining;
    public int initialMorale;

    public CityDefinition() {
    }
}
