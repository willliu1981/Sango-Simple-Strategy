package idv.kuan.studio.sango.domain.rule;

/** 出征方採用的戰術。舊常數只保留給既有 JSON 解碼。 */
public enum BattleTactic {
    ASSAULT,
    FEINT,
    HOLD,
    /** @deprecated schema 10 以前的穩健戰術，載入進行中軍隊時轉為 FEINT。 */
    @Deprecated
    BALANCED,
    /** @deprecated schema 10 以前的保守戰術，載入進行中軍隊時轉為 HOLD。 */
    @Deprecated
    CAUTIOUS;

    private static final BattleTactic[] ACTIVE_VALUES = {ASSAULT, FEINT, HOLD};

    public boolean isActive() {
        return this == ASSAULT || this == FEINT || this == HOLD;
    }

    public BattleTactic normalized() {
        return switch (this) {
            case BALANCED -> FEINT;
            case CAUTIOUS -> HOLD;
            default -> this;
        };
    }

    public boolean defeats(DefensePolicy defensePolicy) {
        BattleTactic attacker = normalized();
        DefensePolicy defender = defensePolicy.normalized();
        return attacker == ASSAULT && defender == DefensePolicy.HOLD
            || attacker == HOLD && defender == DefensePolicy.FEINT
            || attacker == FEINT && defender == DefensePolicy.ASSAULT;
    }

    public static BattleTactic counterTo(DefensePolicy defensePolicy) {
        return switch (defensePolicy.normalized()) {
            case ASSAULT -> FEINT;
            case FEINT -> HOLD;
            case HOLD -> ASSAULT;
            default -> throw new IllegalStateException("防守方針正規化失敗。");
        };
    }

    public static BattleTactic[] activeValues() {
        return ACTIVE_VALUES.clone();
    }
}
