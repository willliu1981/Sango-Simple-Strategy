package idv.kuan.studio.sango.domain.rule;

/**
 * 0.6.0 初版平衡參數；調整數值不必更改 UI、Command 或存檔格式。
 * 城市容量另由 cities.json 定義。新兵成本保留每 200 兵需 100 金、100 糧的比例。
 */
public final class CampaignBalance {
    public static final int POPULATION_FLOOR = 1_000;
    public static final int RECRUIT_POPULATION_RESERVE = 2_000;
    public static final int MAXIMUM_RECRUITMENT = 10_000;
    public static final int RECRUITMENT_SQRT_FACTOR = 10;
    public static final int RECRUIT_COST_DENOMINATOR = 2;
    public static final int RECRUIT_GOLD_NUMERATOR = 1;
    public static final int RECRUIT_FOOD_NUMERATOR = 1;
    public static final int DEFAULT_RECRUIT_AMOUNT = 200;
    public static final int LOCAL_PUBLIC_ORDER_PERCENT = 60;
    public static final int MINIMUM_RECRUIT_MORALE = 30;
    public static final int MAXIMUM_RECRUIT_MORALE = 70;
    public static final int MAXIMUM_ANNUAL_POPULATION_GAIN = 5_000;
    public static final int MAXIMUM_ANNUAL_POPULATION_LOSS = 3_000;

    private CampaignBalance() {
    }
}
