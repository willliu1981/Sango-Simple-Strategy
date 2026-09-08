package idv.kuan.studio.sango.domain.rule;

/** 城池遭受攻擊時採用的持久防守方針。 */
public enum DefensePolicy {
    BALANCED(100, 100, 100),
    AGGRESSIVE(90, 125, 120),
    HOLD(115, 75, 80);

    private final int strengthPercent;
    private final int attackerCasualtyPercent;
    private final int defenderCasualtyPercent;

    DefensePolicy(int strengthPercent, int attackerCasualtyPercent,
        int defenderCasualtyPercent) {
        this.strengthPercent = strengthPercent;
        this.attackerCasualtyPercent = attackerCasualtyPercent;
        this.defenderCasualtyPercent = defenderCasualtyPercent;
    }

    public int getStrengthPercent() {
        return strengthPercent;
    }

    public int getAttackerCasualtyPercent() {
        return attackerCasualtyPercent;
    }

    public int getDefenderCasualtyPercent() {
        return defenderCasualtyPercent;
    }
}
