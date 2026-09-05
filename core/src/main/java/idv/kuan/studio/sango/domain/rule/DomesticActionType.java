package idv.kuan.studio.sango.domain.rule;

/**
 * 第一個 Vertical Slice 可執行的四種內政命令與其固定規則。
 */
public enum DomesticActionType {
    DEVELOP_AGRICULTURE(
        1,
        50,
        0,
        0,
        200,
        0,
        5,
        0,
        0,
        0
    ),
    DEVELOP_COMMERCE(
        1,
        0,
        0,
        150,
        0,
        0,
        0,
        5,
        0,
        0
    ),
    RECRUIT(
        1,
        100,
        100,
        0,
        0,
        200,
        0,
        0,
        0,
        -200
    ),
    TRAIN(
        1,
        50,
        0,
        0,
        0,
        0,
        0,
        0,
        5,
        0
    );

    private final int actionPointCost;
    private final int goldCost;
    private final int foodCost;
    private final int goldGain;
    private final int foodGain;
    private final int troopGain;
    private final int agricultureGain;
    private final int commerceGain;
    private final int trainingGain;
    private final int populationDelta;

    DomesticActionType(
        int actionPointCost,
        int goldCost,
        int foodCost,
        int goldGain,
        int foodGain,
        int troopGain,
        int agricultureGain,
        int commerceGain,
        int trainingGain,
        int populationDelta
    ) {
        this.actionPointCost = actionPointCost;
        this.goldCost = goldCost;
        this.foodCost = foodCost;
        this.goldGain = goldGain;
        this.foodGain = foodGain;
        this.troopGain = troopGain;
        this.agricultureGain = agricultureGain;
        this.commerceGain = commerceGain;
        this.trainingGain = trainingGain;
        this.populationDelta = populationDelta;
    }

    public int getActionPointCost() {
        return actionPointCost;
    }

    public int getGoldCost() {
        return goldCost;
    }

    public int getFoodCost() {
        return foodCost;
    }

    public int getGoldGain() {
        return goldGain;
    }

    public int getFoodGain() {
        return foodGain;
    }

    public int getTroopGain() {
        return troopGain;
    }

    public int getAgricultureGain() {
        return agricultureGain;
    }

    public int getCommerceGain() {
        return commerceGain;
    }

    public int getTrainingGain() {
        return trainingGain;
    }

    public int getPopulationDelta() {
        return populationDelta;
    }
}
