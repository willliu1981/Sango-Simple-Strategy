package idv.kuan.studio.sango.domain.rule;

/**
 * 第一版簡化戰鬥可選擇的三種戰術。
 */
public enum BattleTactic {
    BALANCED(100, 100),
    ASSAULT(120, 125),
    CAUTIOUS(85, 75);

    private final int strengthPercent;
    private final int casualtyPercent;

    BattleTactic(int strengthPercent, int casualtyPercent) {
        this.strengthPercent = strengthPercent;
        this.casualtyPercent = casualtyPercent;
    }

    public int getStrengthPercent() {
        return strengthPercent;
    }

    public int getCasualtyPercent() {
        return casualtyPercent;
    }
}
