package idv.kuan.studio.sango.domain.rule;

/**
 * 內政命令只進行投資或即時軍事整備；農業與商業收益延後結算。
 */
public enum DomesticActionType {
    DEVELOP_AGRICULTURE(1, 50, 0, 0, 5, 0, 0, 0, 0, 0),
    DEVELOP_COMMERCE(1, 50, 0, 0, 0, 5, 0, 0, 0, 0),
    IMPROVE_WATER_CONTROL(1, 80, 0, 0, 0, 0, 5, 0, 0, 0),
    PACIFY(1, 100, 50, 0, 0, 0, 0, 0, 0, 0),
    RECRUIT(1, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    TRAIN(1, 50, 0, 0, 0, 0, 0, 0, 5, 0),
    FORTIFY(1, 100, 0, 0, 0, 0, 0, 5, 0, 0);

    private final int actionPointCost;
    private final int goldCost;
    private final int foodCost;
    private final int troopGain;
    private final int agricultureGain;
    private final int commerceGain;
    private final int waterControlGain;
    private final int defenseGain;
    private final int trainingGain;
    private final int populationDelta;

    DomesticActionType(
        int actionPointCost,
        int goldCost,
        int foodCost,
        int troopGain,
        int agricultureGain,
        int commerceGain,
        int waterControlGain,
        int defenseGain,
        int trainingGain,
        int populationDelta
    ) {
        this.actionPointCost = actionPointCost;
        this.goldCost = goldCost;
        this.foodCost = foodCost;
        this.troopGain = troopGain;
        this.agricultureGain = agricultureGain;
        this.commerceGain = commerceGain;
        this.waterControlGain = waterControlGain;
        this.defenseGain = defenseGain;
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

    public int getTroopGain() {
        return troopGain;
    }

    public int getAgricultureGain() {
        return agricultureGain;
    }

    public int getCommerceGain() {
        return commerceGain;
    }

    public int getWaterControlGain() {
        return waterControlGain;
    }

    public int getDefenseGain() {
        return defenseGain;
    }

    public int getTrainingGain() {
        return trainingGain;
    }

    public int getMoraleDelta() {
        return switch (this) {
            case RECRUIT -> 0;
            case TRAIN -> 5;
            default -> 0;
        };
    }

    public int getPopulationDelta() {
        return populationDelta;
    }
}
