package idv.kuan.studio.sango.domain.rule;

/** 城池遭受攻擊時採用的持久防守方針。舊常數只保留給既有 JSON 解碼。 */
public enum DefensePolicy {
    ASSAULT,
    FEINT,
    HOLD,
    /** @deprecated schema 10 的均衡防守，載入進行中城池時轉為 FEINT。 */
    @Deprecated
    BALANCED,
    /** @deprecated schema 10 的積極迎戰，載入進行中城池時轉為 ASSAULT。 */
    @Deprecated
    AGGRESSIVE;

    private static final DefensePolicy[] ACTIVE_VALUES = {ASSAULT, FEINT, HOLD};

    public boolean isActive() {
        return this == ASSAULT || this == FEINT || this == HOLD;
    }

    public DefensePolicy normalized() {
        return switch (this) {
            case BALANCED -> FEINT;
            case AGGRESSIVE -> ASSAULT;
            default -> this;
        };
    }

    public boolean defeats(BattleTactic battleTactic) {
        DefensePolicy defender = normalized();
        BattleTactic attacker = battleTactic.normalized();
        return defender == ASSAULT && attacker == BattleTactic.HOLD
            || defender == HOLD && attacker == BattleTactic.FEINT
            || defender == FEINT && attacker == BattleTactic.ASSAULT;
    }

    public static DefensePolicy[] activeValues() {
        return ACTIVE_VALUES.clone();
    }
}
