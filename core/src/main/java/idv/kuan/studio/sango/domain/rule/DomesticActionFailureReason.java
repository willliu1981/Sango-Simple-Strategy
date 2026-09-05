package idv.kuan.studio.sango.domain.rule;

/**
 * 內政命令不可執行時的原因。UI 再將它轉成對應語系文字。
 */
public enum DomesticActionFailureReason {
    NONE,
    NO_ACTION_POINTS,
    INSUFFICIENT_GOLD,
    INSUFFICIENT_FOOD,
    INSUFFICIENT_POPULATION
}
