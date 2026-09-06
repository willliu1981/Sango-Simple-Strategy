package idv.kuan.studio.sango.domain.rule;

/**
 * 偵察與出征無法執行時的原因。
 */
public enum StrategicActionFailureReason {
    NONE,
    PLAYER_ELIMINATED,
    NO_ACTION_POINTS,
    ORIGIN_NOT_OWNED,
    TARGET_ALREADY_OWNED,
    TARGET_NOT_CONNECTED,
    INSUFFICIENT_GOLD,
    INSUFFICIENT_FOOD,
    INSUFFICIENT_TROOPS,
    ARMY_ALREADY_ACTIVE
}
