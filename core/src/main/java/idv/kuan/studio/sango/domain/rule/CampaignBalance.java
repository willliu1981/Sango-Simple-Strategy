package idv.kuan.studio.sango.domain.rule;

/**
 * 0.6.0 初版平衡參數；調整數值不必更改 UI、Command 或存檔格式。
 * 城市容量另由 cities.json 定義。新兵成本保留每 2 兵需 1 金、1 糧的比例。
 */
public final class CampaignBalance {
    public static final int POPULATION_FLOOR = 100;
    /** 低於此人口仍可強徵，但會降低本城民心。 */
    public static final int RECRUIT_SAFE_POPULATION_RESERVE = 200;
    /** 強徵也不可突破的生存人口底線。 */
    public static final int RECRUIT_POPULATION_RESERVE = POPULATION_FLOOR;
    public static final int FORCED_RECRUIT_PUBLIC_ORDER_LOSS = 5;
    public static final int MAXIMUM_RECRUITMENT = 1_000;
    public static final int RECRUITMENT_SQRT_FACTOR = 10;
    public static final int RECRUIT_COST_DENOMINATOR = 2;
    public static final int RECRUIT_GOLD_NUMERATOR = 1;
    public static final int RECRUIT_FOOD_NUMERATOR = 1;
    public static final int DEFAULT_RECRUIT_AMOUNT = 100;
    public static final int LOCAL_PUBLIC_ORDER_PERCENT = 60;
    public static final int MINIMUM_RECRUIT_MORALE = 30;
    public static final int MAXIMUM_RECRUIT_MORALE = 70;
    public static final int MAXIMUM_ANNUAL_POPULATION_GAIN = 500;
    public static final int MAXIMUM_ANNUAL_POPULATION_LOSS = 300;
    public static final int FLOOD_AGRICULTURE_LOSS = 3;
    public static final int FLOOD_PUBLIC_ORDER_LOSS = 5;
    public static final int FLOOD_POPULATION_LOSS = 20;

    private CampaignBalance() {
    }
}
